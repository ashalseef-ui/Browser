package com.example.media

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.R
import com.example.browser.MediaSniffer
import com.example.browser.PlaylistResolver
import com.example.data.BrowserRepository
import com.example.data.DownloadItemEntity
import com.example.data.DownloadStatus
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume

class MediaDownloader(
  private val context: Context,
  private val okHttpClient: OkHttpClient,
  private val repository: BrowserRepository,
  private val scope: CoroutineScope,
) {
  private val activeJobs = ConcurrentHashMap<Long, Job>()

  fun enqueueDownload(
    url: String,
    suggestedFileName: String? = null,
    mimeType: String = "application/octet-stream",
    referer: String? = null,
  ) {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return

    val lowerUrl = url.lowercase()
    val isHls =
      lowerUrl.contains(".m3u8") || mimeType.equals("application/x-mpegURL", true)
    val isDash =
      lowerUrl.contains(".mpd") || mimeType.equals("application/dash+xml", true)
    val isAdaptiveStream = isHls || isDash

    val rawName = suggestedFileName ?: deriveFileName(url, mimeType, isAdaptiveStream)
    val ensuredExtName =
      when {
        isAdaptiveStream -> "${rawName.substringBeforeLast('.')}.mp4"
        !rawName.contains('.') -> {
          val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "mp4"
          "$rawName.$ext"
        }
        else -> rawName
      }

    val resolvedName = sanitizeFileName(ensuredExtName)
    val resolvedMime =
      when {
        isAdaptiveStream -> "video/mp4"
        mimeType.isNotBlank() && mimeType != "application/octet-stream" -> mimeType
        else -> guessMimeFromName(resolvedName)
      }

    scope.launch(Dispatchers.IO) {
      val downloadDir =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
          ?: File(context.filesDir, "downloads").apply { mkdirs() }
      if (!downloadDir.exists()) downloadDir.mkdirs()

      val targetFile = uniqueTargetFile(downloadDir, resolvedName)
      val id =
        repository.insertDownload(
          DownloadItemEntity(
            url = url,
            fileName = targetFile.name,
            localPath = targetFile.absolutePath,
            mimeType = resolvedMime,
            status = DownloadStatus.DOWNLOADING.name,
          )
        )

      val job =
        launch {
          try {
            if (isAdaptiveStream) {
              downloadAdaptiveStreamAsMp4(id, url, isHls, targetFile, resolvedMime, referer)
            } else {
              downloadDirectFile(id, url, targetFile, resolvedMime, referer)
            }
          } catch (_: CancellationException) {
            runCatching { targetFile.delete() }
            repository.updateDownloadProgress(
              id = id,
              downloaded = 0L,
              total = -1L,
              status = DownloadStatus.CANCELLED,
              localPath = targetFile.absolutePath,
            )
          } catch (_: Exception) {
            repository.updateDownloadProgress(
              id = id,
              downloaded = targetFile.length(),
              total = -1L,
              status = DownloadStatus.FAILED,
              localPath = targetFile.absolutePath,
            )
            postDownloadNotification(id, targetFile.name, "Download failed — tap Retry", ongoing = false)
          } finally {
            activeJobs.remove(id)
          }
        }
      activeJobs[id] = job
    }
  }

  private fun buildRequest(url: String, referer: String?): Request =
    MediaSniffer.buildBrowserRequestBuilder(url, referer).build()

  private suspend fun downloadDirectFile(
    id: Long,
    url: String,
    targetFile: File,
    mimeType: String,
    referer: String?,
  ) {
    okHttpClient.newCall(buildRequest(url, referer)).execute().use { response ->
      if (!response.isSuccessful || response.body == null) {
        repository.updateDownloadProgress(
          id = id,
          downloaded = 0L,
          total = -1L,
          status = DownloadStatus.FAILED,
          localPath = targetFile.absolutePath,
        )
        postDownloadNotification(id, targetFile.name, "Download failed (${response.code})", ongoing = false)
        return
      }

      val body = response.body!!
      val totalBytes = body.contentLength()
      var downloadedBytes = 0L
      var lastReported = 0L
      val buffer = ByteArray(16 * 1024)

      body.byteStream().use { input ->
        FileOutputStream(targetFile).use { output ->
          while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            downloadedBytes += read
            if (downloadedBytes - lastReported >= 65_536L) {
              lastReported = downloadedBytes
              repository.updateDownloadProgress(
                id = id,
                downloaded = downloadedBytes,
                total = totalBytes,
                status = DownloadStatus.DOWNLOADING,
                localPath = targetFile.absolutePath,
              )
            }
          }
          output.flush()
        }
      }

      if (downloadedBytes <= 0L) {
        repository.updateDownloadProgress(
          id = id,
          downloaded = 0L,
          total = -1L,
          status = DownloadStatus.FAILED,
          localPath = targetFile.absolutePath,
        )
        return
      }

      val exportedToPublic =
        exportFileToPublicDownloads(targetFile, targetFile.name, mimeType)

      repository.updateDownloadProgress(
        id = id,
        downloaded = downloadedBytes,
        total = if (totalBytes > 0) totalBytes else downloadedBytes,
        status = DownloadStatus.COMPLETED,
        localPath = targetFile.absolutePath,
      )
      postDownloadNotification(
        id = id,
        title = targetFile.name,
        message =
          if (exportedToPublic) "Saved to Downloads/VoltBrowser/${targetFile.name}"
          else "Saved inside Volt Browser (use Save As to export)",
        ongoing = false,
      )
    }
  }

  @OptIn(UnstableApi::class)
  private suspend fun downloadAdaptiveStreamAsMp4(
    id: Long,
    playlistUrl: String,
    isHls: Boolean,
    targetFile: File,
    mimeType: String,
    referer: String?,
  ) {
    if (isHls) {
      val initialPlaylist =
        fetchText(playlistUrl, referer) ?: throw IllegalStateException("Playlist fetch failed")
      val mediaPlaylistUrl = resolveHighestBandwidthVariant(playlistUrl, initialPlaylist)
      val mediaPlaylistText =
        if (mediaPlaylistUrl == playlistUrl) initialPlaylist
        else fetchText(mediaPlaylistUrl, referer) ?: throw IllegalStateException("Variant fetch failed")
      val stats = PlaylistResolver.parseHlsMediaPlaylistStats(mediaPlaylistUrl, mediaPlaylistText)

      // Fast path: if HLS references a single full .mp4 via #EXT-X-BYTERANGE, download it directly
      if (stats.hasByteRange && stats.directMp4Urls.size == 1) {
        downloadDirectFile(id, stats.directMp4Urls.first(), targetFile, "video/mp4", referer)
        return
      }
    }

    // Best-practice Media3 Transformer remux/export into a clean seekable .mp4 file
    val remuxedOk = exportWithMedia3Transformer(id, playlistUrl, isHls, targetFile)
    if (remuxedOk && targetFile.exists() && targetFile.length() > 0L) {
      val size = targetFile.length()
      val exportedToPublic =
        exportFileToPublicDownloads(targetFile, targetFile.name, "video/mp4")
      repository.updateDownloadProgress(
        id = id,
        downloaded = size,
        total = size,
        status = DownloadStatus.COMPLETED,
        localPath = targetFile.absolutePath,
      )
      postDownloadNotification(
        id = id,
        title = targetFile.name,
        message =
          if (exportedToPublic) "Saved to Downloads/VoltBrowser/${targetFile.name}"
          else "Saved inside Volt Browser (use Save As to export)",
        ongoing = false,
      )
      return
    }

    if (isHls) {
      // Fallback: direct segment assembly if Transformer rejects an unusual container/header
      val fallbackFile = renameExtension(targetFile, "ts")
      downloadHlsStream(id, playlistUrl, fallbackFile, "video/mp2t", referer)
    } else {
      throw IllegalStateException("Could not export stream to MP4")
    }
  }

  @OptIn(UnstableApi::class)
  private suspend fun exportWithMedia3Transformer(
    id: Long,
    streamUrl: String,
    isHls: Boolean,
    targetFile: File,
  ): Boolean =
    withContext(Dispatchers.Main) {
      val mediaItem =
        MediaItem.Builder()
          .setUri(streamUrl)
          .setMimeType(if (isHls) "application/x-mpegURL" else "application/dash+xml")
          .build()

      var progressPoller: Job? = null
      val success =
        suspendCancellableCoroutine { continuation ->
          var transformer: Transformer? = null
          val listener =
            object : Transformer.Listener {
              override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                progressPoller?.cancel()
                if (continuation.isActive) continuation.resume(true)
              }

              override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
              ) {
                progressPoller?.cancel()
                runCatching { targetFile.delete() }
                if (continuation.isActive) continuation.resume(false)
              }
            }

          try {
            val instance =
              Transformer.Builder(context)
                .addListener(listener)
                .build()
            transformer = instance
            instance.start(mediaItem, targetFile.absolutePath)

            val holder = ProgressHolder()
            progressPoller =
              scope.launch(Dispatchers.Main) {
                while (isActive && continuation.isActive) {
                  val state = instance.getProgress(holder)
                  if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val pct = holder.progress.coerceIn(0, 99).toLong()
                    repository.updateDownloadProgress(
                      id = id,
                      downloaded = pct,
                      total = 100L,
                      status = DownloadStatus.DOWNLOADING,
                      localPath = targetFile.absolutePath,
                    )
                  }
                  delay(450L)
                }
              }

            continuation.invokeOnCancellation {
              progressPoller?.cancel()
              runCatching { transformer?.cancel() }
              runCatching { targetFile.delete() }
            }
          } catch (_: Throwable) {
            progressPoller?.cancel()
            runCatching { targetFile.delete() }
            if (continuation.isActive) continuation.resume(false)
          }
        }
      success
    }

  private suspend fun downloadHlsStream(
    id: Long,
    playlistUrl: String,
    initialTargetFile: File,
    initialMime: String,
    referer: String?,
  ) {
    val initialPlaylist =
      fetchText(playlistUrl, referer) ?: throw IllegalStateException("Playlist fetch failed")
    val mediaPlaylistUrl = resolveHighestBandwidthVariant(playlistUrl, initialPlaylist)
    val mediaPlaylistText =
      if (mediaPlaylistUrl == playlistUrl) initialPlaylist
      else fetchText(mediaPlaylistUrl, referer) ?: throw IllegalStateException("Variant fetch failed")

    val stats = PlaylistResolver.parseHlsMediaPlaylistStats(mediaPlaylistUrl, mediaPlaylistText)
    if (stats.segmentUrls.isEmpty()) throw IllegalStateException("No segments in playlist")

    // If HLS points to a single full .mp4 via #EXT-X-BYTERANGE, download that MP4 directly!
    if (stats.hasByteRange && stats.directMp4Urls.size == 1) {
      val mp4File = renameExtension(initialTargetFile, "mp4")
      downloadDirectFile(id, stats.directMp4Urls.first(), mp4File, "video/mp4", referer)
      return
    }

    val isFmp4 =
      stats.initMapUrl != null ||
        stats.segmentUrls.any {
          val p = it.substringBefore('?').lowercase()
          p.endsWith(".mp4") || p.endsWith(".m4s") || p.endsWith(".m4v")
        }

    val finalTargetFile =
      if (isFmp4 && !initialTargetFile.name.endsWith(".mp4", true)) {
        renameExtension(initialTargetFile, "mp4")
      } else {
        initialTargetFile
      }
    val finalMime = if (isFmp4) "video/mp4" else initialMime

    var totalWritten = 0L
    FileOutputStream(finalTargetFile).use { output ->
      // Write fMP4 init segment first if present (#EXT-X-MAP)
      stats.initMapUrl?.let { mapUrl ->
        val mapBytes = fetchBytes(mapUrl, referer)
        if (mapBytes != null) {
          output.write(mapBytes)
          totalWritten += mapBytes.size
        }
      }

      stats.segmentUrls.forEachIndexed { index, segUrl ->
        val segBytes = fetchBytes(segUrl, referer)
        if (segBytes != null) {
          output.write(segBytes)
          totalWritten += segBytes.size
        }
        val estTotal =
          if (index > 0) (totalWritten / (index + 1)) * stats.segmentUrls.size else -1L
        repository.updateDownloadProgress(
          id = id,
          downloaded = totalWritten,
          total = estTotal,
          status = DownloadStatus.DOWNLOADING,
          localPath = finalTargetFile.absolutePath,
        )
      }
      output.flush()
    }

    if (totalWritten <= 0L) {
      throw IllegalStateException("Empty HLS download")
    }

    val exportedToPublic =
      exportFileToPublicDownloads(finalTargetFile, finalTargetFile.name, finalMime)

    repository.updateDownloadProgress(
      id = id,
      downloaded = totalWritten,
      total = totalWritten,
      status = DownloadStatus.COMPLETED,
      localPath = finalTargetFile.absolutePath,
    )
    postDownloadNotification(
      id = id,
      title = finalTargetFile.name,
      message =
        if (exportedToPublic) "Saved to Downloads/VoltBrowser/${finalTargetFile.name}"
        else "Saved inside Volt Browser (use Save As to export)",
      ongoing = false,
    )
  }

  private fun renameExtension(file: File, newExt: String): File {
    val base = file.name.substringBeforeLast('.')
    val candidate = uniqueTargetFile(file.parentFile ?: context.filesDir, "$base.$newExt")
    return candidate
  }

  private fun resolveHighestBandwidthVariant(masterUrl: String, playlistText: String): String {
    val lines = playlistText.lines()
    var bestBandwidth = -1L
    var bestUri: String? = null
    val base = URI(masterUrl)

    for (i in lines.indices) {
      val line = lines[i].trim()
      if (line.startsWith("#EXT-X-STREAM-INF:")) {
        val bw =
          Regex("BANDWIDTH=(\\d+)")
            .find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull() ?: 0L
        val nextLine = lines.drop(i + 1).firstOrNull { it.trim().isNotEmpty() && !it.trim().startsWith("#") }?.trim()
        if (nextLine != null && bw >= bestBandwidth) {
          bestBandwidth = bw
          bestUri = base.resolve(nextLine).toString()
        }
      }
    }
    return bestUri ?: masterUrl
  }

  private fun fetchText(url: String, referer: String?): String? =
    runCatching {
      okHttpClient.newCall(buildRequest(url, referer)).execute().use { res ->
        if (res.isSuccessful) res.body?.string() else null
      }
    }.getOrNull()

  private fun fetchBytes(url: String, referer: String?): ByteArray? =
    runCatching {
      okHttpClient.newCall(buildRequest(url, referer)).execute().use { res ->
        if (res.isSuccessful) res.body?.bytes() else null
      }
    }.getOrNull()

  fun cancelDownload(id: Long) {
    activeJobs.remove(id)?.cancel()
  }

  fun cancelAllDownloads() {
    val jobs = activeJobs.values.toList()
    activeJobs.clear()
    jobs.forEach { it.cancel() }
  }

  fun exportToPublicDownloads(item: DownloadItemEntity): Boolean {
    val sourceFile = File(item.localPath)
    if (!sourceFile.exists()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      shareDownloadedFile(item)
      return true
    }
    return exportFileToPublicDownloads(sourceFile, item.fileName, item.mimeType)
  }

  private fun exportFileToPublicDownloads(
    sourceFile: File,
    fileName: String,
    mimeType: String,
  ): Boolean {
    if (!sourceFile.exists() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return runCatching {
      val resolver = context.contentResolver
      val values =
        ContentValues().apply {
          put(MediaStore.Downloads.DISPLAY_NAME, fileName)
          put(MediaStore.Downloads.MIME_TYPE, mimeType)
          put(MediaStore.MediaColumns.SIZE, sourceFile.length())
          put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VoltBrowser")
          put(MediaStore.Downloads.IS_PENDING, 1)
        }
      val destUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
      resolver.openOutputStream(destUri)?.use { out ->
        sourceFile.inputStream().use { input -> input.copyTo(out) }
        out.flush()
      } ?: return false
      values.clear()
      values.put(MediaStore.Downloads.IS_PENDING, 0)
      resolver.update(destUri, values, null, null)
      true
    }.getOrDefault(false)
  }

  fun copyDownloadedFileToUri(item: DownloadItemEntity, destUri: Uri): Boolean {
    val sourceFile = File(item.localPath)
    if (!sourceFile.exists()) return false
    return runCatching {
      context.contentResolver.openOutputStream(destUri)?.use { out ->
        sourceFile.inputStream().use { input -> input.copyTo(out) }
        out.flush()
      } != null
    }.getOrDefault(false)
  }

  fun shareDownloadedFile(item: DownloadItemEntity) {
    val file = File(item.localPath)
    if (!file.exists()) return
    val uri =
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    val chooser =
      Intent.createChooser(shareIntent, item.fileName).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    context.startActivity(chooser)
  }

  private fun postDownloadNotification(
    id: Long,
    title: String,
    message: String,
    ongoing: Boolean,
  ) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    runCatching {
      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      val channelId = "volt_downloads"
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel =
          NotificationChannel(
            channelId,
            "Volt Downloads",
            NotificationManager.IMPORTANCE_DEFAULT,
          )
        manager.createNotificationChannel(channel)
      }
      val notification =
        NotificationCompat.Builder(context, channelId)
          .setSmallIcon(R.drawable.ic_launcher_foreground)
          .setContentTitle(title)
          .setContentText(message)
          .setOngoing(ongoing)
          .setAutoCancel(!ongoing)
          .build()
      manager.notify(5000 + id.toInt(), notification)
    }
  }

  private fun deriveFileName(url: String, mimeType: String, isHls: Boolean): String {
    if (isHls) return "volt_video_${System.currentTimeMillis() % 100_000}.mp4"
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    val lastSegment = uri?.lastPathSegment?.takeIf { it.isNotBlank() }
    if (!lastSegment.isNullOrBlank() && lastSegment.contains('.')) {
      return lastSegment
    }
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "mp4"
    return "volt_media_${System.currentTimeMillis() % 100_000}.$ext"
  }

  private fun sanitizeFileName(raw: String): String =
    raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80).ifBlank { "volt_download.bin" }

  private fun guessMimeFromName(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
  }

  private fun uniqueTargetFile(dir: File, name: String): File {
    var candidate = File(dir, name)
    if (!candidate.exists()) return candidate
    val base = name.substringBeforeLast('.')
    val ext = name.substringAfterLast('.', "")
    var counter = 1
    while (candidate.exists()) {
      val suffix = if (ext.isNotEmpty()) "_$counter.$ext" else "_$counter"
      candidate = File(dir, "$base$suffix")
      counter++
    }
    return candidate
  }
}
