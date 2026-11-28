package com.example.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.VoltBrowserApp
import com.example.data.AppDatabase
import com.example.data.BrowserRepository
import com.example.data.DownloadStatus
import com.example.data.VoltPreferences
import com.example.ui.components.formatBytes
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Persistent WorkManager queue worker executing multi-connection segmented downloads,
 * single-stream HTTP Range resumes, and adaptive stream exports with `dataSync`
 * foreground-service promotion so transfers survive app backgrounding and network drops.
 */
class MediaDownloadWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  private var lastForegroundUpdateMs = 0L

  override suspend fun doWork(): Result {
    val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
    val url = inputData.getString(KEY_URL).orEmpty()
    val targetPath = inputData.getString(KEY_TARGET_PATH).orEmpty()
    val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "application/octet-stream"
    val isAdaptive = inputData.getBoolean(KEY_IS_ADAPTIVE, false)
    val isHls = inputData.getBoolean(KEY_IS_HLS, false)
    val hadExplicitName = inputData.getBoolean(KEY_HAD_EXPLICIT_NAME, true)
    val referer = inputData.getString(KEY_REFERER)
    val resumePartial = inputData.getBoolean(KEY_RESUME_PARTIAL, false) || runAttemptCount > 0

    if (downloadId <= 0L || url.isBlank() || targetPath.isBlank()) {
      return Result.failure()
    }
    if (downloadId in userCancelledIds) {
      userCancelledIds.remove(downloadId)
      return Result.success()
    }

    val app = applicationContext as? VoltBrowserApp
    val repository =
      app?.repository
        ?: BrowserRepository(
          AppDatabase.getInstance(applicationContext),
          VoltPreferences(applicationContext),
        )
    val okHttpClient = app?.speedEngine?.okHttpClient ?: OkHttpClient()

    val existing = repository.getDownloadById(downloadId)
    if (existing == null || existing.status == DownloadStatus.CANCELLED.name) {
      return Result.success()
    }

    val targetFile = File(existing.localPath.ifBlank { targetPath })
    ensureNotificationChannel()
    promoteForegroundSafe(
      downloadId = downloadId,
      fileName = targetFile.name,
      downloaded = existing.downloadedBytes,
      total = existing.totalBytes,
      streams = 1,
    )

    return withContext(Dispatchers.IO) {
      val downloader =
        MediaDownloader(
          context = applicationContext,
          okHttpClient = okHttpClient,
          repository = repository,
          scope = this,
        )

      try {
        repository.updateDownloadProgress(
          id = downloadId,
          downloaded = existing.downloadedBytes,
          total = existing.totalBytes,
          status = DownloadStatus.DOWNLOADING,
          localPath = targetFile.absolutePath,
        )

        downloader.executeWorkerDownload(
          id = downloadId,
          trimmedUrl = url,
          isAdaptiveStream = isAdaptive,
          isHls = isHls,
          targetFile = targetFile,
          resolvedMime = mimeType,
          hadExplicitName = hadExplicitName,
          referer = referer,
          resumePartial = resumePartial,
          onProgressUpdate = { currentFile, downloaded, total, streamCount ->
            val now = System.currentTimeMillis()
            if (now - lastForegroundUpdateMs >= 450L) {
              lastForegroundUpdateMs = now
              promoteForegroundSafe(
                downloadId = downloadId,
                fileName = currentFile.name,
                downloaded = downloaded,
                total = total,
                streams = streamCount,
              )
            }
          },
        )
        userCancelledIds.remove(downloadId)
        Result.success()
      } catch (cancel: CancellationException) {
        withContext(NonCancellable) {
          val current = repository.getDownloadById(downloadId)
          val wasUserCancelled =
            userCancelledIds.remove(downloadId) ||
              current == null ||
              current.status == DownloadStatus.CANCELLED.name
          if (wasUserCancelled) {
            runCatching { targetFile.delete() }
            SegmentedDownloader.cleanupMetaFile(targetFile)
            repository.updateDownloadProgress(
              id = downloadId,
              downloaded = 0L,
              total = -1L,
              status = DownloadStatus.CANCELLED,
              localPath = targetFile.absolutePath,
            )
          } else {
            val savedBytes = resolveSavedPartialBytes(targetFile, current.downloadedBytes)
            repository.updateDownloadProgress(
              id = downloadId,
              downloaded = savedBytes,
              total = current.totalBytes,
              status = DownloadStatus.QUEUED,
              localPath = targetFile.absolutePath,
            )
          }
        }
        throw cancel
      } catch (err: Exception) {
        val current = repository.getDownloadById(downloadId)
        val savedBytes = resolveSavedPartialBytes(targetFile, current?.downloadedBytes ?: 0L)
        val totalBytes = current?.totalBytes ?: -1L
        val canAutoRetry = err is IOException && runAttemptCount < MAX_AUTO_RETRY_ATTEMPTS
        if (canAutoRetry) {
          repository.updateDownloadProgress(
            id = downloadId,
            downloaded = savedBytes,
            total = totalBytes,
            status = DownloadStatus.QUEUED,
            localPath = targetFile.absolutePath,
          )
          Result.retry()
        } else {
          repository.updateDownloadProgress(
            id = downloadId,
            downloaded = savedBytes,
            total = totalBytes,
            status = DownloadStatus.FAILED,
            localPath = targetFile.absolutePath,
          )
          MediaExportHelper(applicationContext).postDownloadNotification(
            id = downloadId,
            title = targetFile.name,
            message = "Download interrupted — tap Resume",
            ongoing = false,
          )
          Result.failure()
        }
      }
    }
  }

  private fun resolveSavedPartialBytes(targetFile: File, fallbackBytes: Long): Long {
    val segBytes = SegmentedDownloader.readSavedProgressBytes(targetFile)
    if (segBytes != null) return segBytes
    return if (targetFile.exists()) targetFile.length().coerceAtLeast(0L) else fallbackBytes
  }

  private suspend fun promoteForegroundSafe(
    downloadId: Long,
    fileName: String,
    downloaded: Long,
    total: Long,
    streams: Int,
  ) {
    runCatching {
      setForeground(createForegroundInfo(downloadId, fileName, downloaded, total, streams))
    }
  }

  private fun createForegroundInfo(
    downloadId: Long,
    fileName: String,
    downloaded: Long,
    total: Long,
    streams: Int,
  ): ForegroundInfo {
    val notificationId = NOTIFICATION_BASE_ID + downloadId.toInt()
    val hasPct = total > 0L
    val pct = if (hasPct) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
    val streamLabel = if (streams > 1) "$streams streams • " else ""
    val statusText =
      if (hasPct) "$streamLabel$pct% (${formatBytes(downloaded)} / ${formatBytes(total)})"
      else "${streamLabel}Downloading ${formatBytes(downloaded)}"

    val openIntent =
      PendingIntent.getActivity(
        applicationContext,
        notificationId,
        Intent(applicationContext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_volt_stat)
        .setContentTitle(fileName)
        .setContentText(statusText)
        .setContentIntent(openIntent)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setProgress(100, pct, !hasPct)
        .build()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ForegroundInfo(
        notificationId,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
    } else {
      ForegroundInfo(notificationId, notification)
    }
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          "Volt Downloads",
          NotificationManager.IMPORTANCE_LOW,
        )
      manager.createNotificationChannel(channel)
    }
  }

  companion object {
    const val WORK_TAG = "volt_media_downloads"
    private const val CHANNEL_ID = "volt_downloads_progress"
    private const val NOTIFICATION_BASE_ID = 6000
    private const val MAX_AUTO_RETRY_ATTEMPTS = 2

    const val KEY_DOWNLOAD_ID = "download_id"
    const val KEY_URL = "url"
    const val KEY_TARGET_PATH = "target_path"
    const val KEY_MIME_TYPE = "mime_type"
    const val KEY_IS_ADAPTIVE = "is_adaptive"
    const val KEY_IS_HLS = "is_hls"
    const val KEY_HAD_EXPLICIT_NAME = "had_explicit_name"
    const val KEY_REFERER = "referer"
    const val KEY_RESUME_PARTIAL = "resume_partial"

    val userCancelledIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    fun uniqueWorkName(downloadId: Long): String = "volt_download_$downloadId"

    fun buildWorkRequest(
      downloadId: Long,
      url: String,
      targetPath: String,
      mimeType: String,
      isAdaptive: Boolean,
      isHls: Boolean,
      hadExplicitName: Boolean,
      referer: String?,
      resumePartial: Boolean,
    ) =
      OneTimeWorkRequestBuilder<MediaDownloadWorker>()
        .addTag(WORK_TAG)
        .setConstraints(
          Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .setInputData(
          Data.Builder()
            .putLong(KEY_DOWNLOAD_ID, downloadId)
            .putString(KEY_URL, url)
            .putString(KEY_TARGET_PATH, targetPath)
            .putString(KEY_MIME_TYPE, mimeType)
            .putBoolean(KEY_IS_ADAPTIVE, isAdaptive)
            .putBoolean(KEY_IS_HLS, isHls)
            .putBoolean(KEY_HAD_EXPLICIT_NAME, hadExplicitName)
            .apply { if (!referer.isNullOrBlank()) putString(KEY_REFERER, referer) }
            .putBoolean(KEY_RESUME_PARTIAL, resumePartial)
            .build()
        )
        .build()

    fun enqueueOrFallback(
      context: Context,
      downloadId: Long,
      url: String,
      targetPath: String,
      mimeType: String,
      isAdaptive: Boolean,
      isHls: Boolean,
      hadExplicitName: Boolean,
      referer: String?,
      resumePartial: Boolean,
    ): Boolean {
      userCancelledIds.remove(downloadId)
      return runCatching {
        val request =
          buildWorkRequest(
            downloadId = downloadId,
            url = url,
            targetPath = targetPath,
            mimeType = mimeType,
            isAdaptive = isAdaptive,
            isHls = isHls,
            hadExplicitName = hadExplicitName,
            referer = referer,
            resumePartial = resumePartial,
          )
        WorkManager.getInstance(context)
          .enqueueUniqueWork(uniqueWorkName(downloadId), ExistingWorkPolicy.REPLACE, request)
        true
      }.getOrDefault(false)
    }
  }
}
