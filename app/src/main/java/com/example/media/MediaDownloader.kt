package com.example.media

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.example.data.BrowserRepository
import com.example.data.DownloadItemEntity
import com.example.data.DownloadStatus
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Coordinates WorkManager-backed persistent download queues, multi-connection HTTP Range
 * segmented transfers, single-stream HTTP Range resume, RFC 2397 `data:` decoding, and
 * adaptive HLS/DASH stream MP4 exports.
 */
class MediaDownloader(
  private val context: Context,
  private val okHttpClient: OkHttpClient,
  private val repository: BrowserRepository,
  private val scope: CoroutineScope,
  maxSegments: Int = SegmentedDownloader.DEFAULT_MAX_SEGMENTS,
  minSegmentedBytes: Long = SegmentedDownloader.DEFAULT_MIN_SEGMENTED_BYTES,
) {
  private val activeJobs = ConcurrentHashMap<Long, Job>()
  private val exportHelper = MediaExportHelper(context)
  private val segmentedDownloader =
    SegmentedDownloader(
      okHttpClient = okHttpClient,
      maxSegments = maxSegments,
      minSegmentedBytes = minSegmentedBytes,
    )
  private val adaptiveExporter =
    AdaptiveStreamExporter(
      context = context,
      okHttpClient = okHttpClient,
      repository = repository,
      scope = scope,
      exportHelper = exportHelper,
      resolveUniqueFile = ::uniqueTargetFile,
    )

  fun enqueueDownload(
    url: String,
    suggestedFileName: String? = null,
    mimeType: String = "application/octet-stream",
    referer: String? = null,
    contentDisposition: String? = null,
  ) {
    val trimmedUrl = url.trim()
    val isHttp = trimmedUrl.startsWith("http://", true) || trimmedUrl.startsWith("https://", true)
    val isDataUrl = trimmedUrl.startsWith("data:", ignoreCase = true)
    if (!isHttp && !isDataUrl) return

    if (isDataUrl) {
      enqueueDataUrlDownload(trimmedUrl, suggestedFileName, mimeType, contentDisposition)
      return
    }

    val lowerUrl = trimmedUrl.lowercase(Locale.US)
    val cleanMime = normalizeMimeType(mimeType)
    val isHls = lowerUrl.contains(".m3u8") || cleanMime.equals("application/x-mpegurl", true)
    val isDash = lowerUrl.contains(".mpd") || cleanMime.equals("application/dash+xml", true)
    val isAdaptiveStream = isHls || isDash

    val guessedName =
      if (!suggestedFileName.isNullOrBlank()) {
        ensureFileExtension(suggestedFileName, cleanMime, isAdaptiveStream)
      } else {
        guessDownloadFileName(trimmedUrl, contentDisposition, cleanMime, isAdaptiveStream)
      }
    val resolvedName = sanitizeFileNamePreservingExtension(guessedName)
    val resolvedMime =
      if (isAdaptiveStream) "video/mp4" else resolveMimeType(resolvedName, cleanMime)
    val hadExplicitName = !suggestedFileName.isNullOrBlank() || !contentDisposition.isNullOrBlank()

    scope.launch(Dispatchers.IO) {
      val downloadDir = resolveDownloadDirectory()
      val targetFile = uniqueTargetFile(downloadDir, resolvedName)
      val id =
        repository.insertDownload(
          DownloadItemEntity(
            url = trimmedUrl,
            fileName = targetFile.name,
            localPath = targetFile.absolutePath,
            mimeType = resolvedMime,
            status = DownloadStatus.QUEUED.name,
          )
        )
      scheduleDownloadExecution(
        id = id,
        trimmedUrl = trimmedUrl,
        isAdaptiveStream = isAdaptiveStream,
        isHls = isHls,
        targetFile = targetFile,
        resolvedMime = resolvedMime,
        hadExplicitName = hadExplicitName,
        referer = referer,
        resumePartial = false,
      )
    }
  }

  /**
   * Resumes a paused/failed HTTP download via per-chunk `.segmeta` or single-stream HTTP `Range`
   * when partial state exists on disk, or restarts cleanly if adaptive or missing on disk.
   */
  fun resumeOrRetryDownload(item: DownloadItemEntity, referer: String? = null) {
    val trimmedUrl = item.url.trim()
    val lowerUrl = trimmedUrl.lowercase(Locale.US)
    val cleanMime = normalizeMimeType(item.mimeType)
    val isHls = lowerUrl.contains(".m3u8") || cleanMime.equals("application/x-mpegurl", true)
    val isDash = lowerUrl.contains(".mpd") || cleanMime.equals("application/dash+xml", true)
    val isAdaptiveStream = isHls || isDash
    val partialFile = item.localPath.takeIf { it.isNotBlank() }?.let(::File)
    val hasPartialSegments = partialFile != null && SegmentedDownloader.segMetaFileFor(partialFile).exists()
    val hasPartialSingle = partialFile != null && partialFile.exists() && partialFile.length() > 0L

    if (isAdaptiveStream || (!hasPartialSegments && !hasPartialSingle)) {
      scope.launch(Dispatchers.IO) {
        repository.deleteDownload(item.id)
        enqueueDownload(
          url = trimmedUrl,
          suggestedFileName = item.fileName,
          mimeType = item.mimeType,
          referer = referer,
        )
      }
      return
    }

    activeJobs.remove(item.id)?.cancel()
    val resolvedPartial = partialFile!!
    scope.launch(Dispatchers.IO) {
      val savedBytes =
        SegmentedDownloader.readSavedProgressBytes(resolvedPartial)
          ?: resolvedPartial.length().coerceAtLeast(0L)
      repository.updateDownloadProgress(
        id = item.id,
        downloaded = savedBytes,
        total = item.totalBytes,
        status = DownloadStatus.QUEUED,
        localPath = resolvedPartial.absolutePath,
      )
      scheduleDownloadExecution(
        id = item.id,
        trimmedUrl = trimmedUrl,
        isAdaptiveStream = false,
        isHls = false,
        targetFile = resolvedPartial,
        resolvedMime = resolveMimeType(item.fileName, item.mimeType),
        hadExplicitName = true,
        referer = referer,
        resumePartial = true,
      )
    }
  }

  private fun scheduleDownloadExecution(
    id: Long,
    trimmedUrl: String,
    isAdaptiveStream: Boolean,
    isHls: Boolean,
    targetFile: File,
    resolvedMime: String,
    hadExplicitName: Boolean,
    referer: String?,
    resumePartial: Boolean,
  ) {
    val queuedInWorkManager =
      MediaDownloadWorker.enqueueOrFallback(
        context = context,
        downloadId = id,
        url = trimmedUrl,
        targetPath = targetFile.absolutePath,
        mimeType = resolvedMime,
        isAdaptive = isAdaptiveStream,
        isHls = isHls,
        hadExplicitName = hadExplicitName,
        referer = referer,
        resumePartial = resumePartial,
      )
    if (!queuedInWorkManager) {
      scope.launchDownloadJob(
        id = id,
        trimmedUrl = trimmedUrl,
        isAdaptiveStream = isAdaptiveStream,
        isHls = isHls,
        targetFile = targetFile,
        resolvedMime = resolvedMime,
        hadExplicitName = hadExplicitName,
        referer = referer,
        resumePartial = resumePartial,
      )
    }
  }

  /**
   * Executes a queued download synchronously inside either [MediaDownloadWorker] or an
   * in-process fallback job. Throws on failure so WorkManager can apply backoff/retry logic.
   */
  internal suspend fun executeWorkerDownload(
    id: Long,
    trimmedUrl: String,
    isAdaptiveStream: Boolean,
    isHls: Boolean,
    targetFile: File,
    resolvedMime: String,
    hadExplicitName: Boolean,
    referer: String?,
    resumePartial: Boolean,
    onProgressUpdate: suspend (file: File, downloaded: Long, total: Long, streams: Int) -> Unit = { _, _, _, _ -> },
  ) {
    if (isAdaptiveStream) {
      adaptiveExporter.downloadAdaptiveStreamAsMp4(
        id = id,
        playlistUrl = trimmedUrl,
        isHls = isHls,
        targetFile = targetFile,
        referer = referer,
        onByteRangeMp4Fallback = { mp4Url, outFile ->
          downloadDirectFile(
            id = id,
            url = mp4Url,
            initialTargetFile = outFile,
            initialMimeType = "video/mp4",
            hadExplicitName = true,
            referer = referer,
            allowResume = false,
            onProgressUpdate = onProgressUpdate,
          )
        },
      )
    } else {
      downloadDirectFile(
        id = id,
        url = trimmedUrl,
        initialTargetFile = targetFile,
        initialMimeType = resolvedMime,
        hadExplicitName = hadExplicitName,
        referer = referer,
        allowResume = resumePartial,
        onProgressUpdate = onProgressUpdate,
      )
    }
  }

  private fun CoroutineScope.launchDownloadJob(
    id: Long,
    trimmedUrl: String,
    isAdaptiveStream: Boolean,
    isHls: Boolean,
    targetFile: File,
    resolvedMime: String,
    hadExplicitName: Boolean,
    referer: String?,
    resumePartial: Boolean,
  ) {
    val job =
      launch(Dispatchers.IO) {
        try {
          repository.updateDownloadProgress(
            id = id,
            downloaded = SegmentedDownloader.readSavedProgressBytes(targetFile) ?: 0L,
            total = -1L,
            status = DownloadStatus.DOWNLOADING,
            localPath = targetFile.absolutePath,
          )
          executeWorkerDownload(
            id = id,
            trimmedUrl = trimmedUrl,
            isAdaptiveStream = isAdaptiveStream,
            isHls = isHls,
            targetFile = targetFile,
            resolvedMime = resolvedMime,
            hadExplicitName = hadExplicitName,
            referer = referer,
            resumePartial = resumePartial,
          )
        } catch (_: CancellationException) {
          runCatching { targetFile.delete() }
          SegmentedDownloader.cleanupMetaFile(targetFile)
          repository.updateDownloadProgress(
            id = id,
            downloaded = 0L,
            total = -1L,
            status = DownloadStatus.CANCELLED,
            localPath = targetFile.absolutePath,
          )
        } catch (_: Exception) {
          val savedBytes =
            SegmentedDownloader.readSavedProgressBytes(targetFile)
              ?: if (targetFile.exists()) targetFile.length().coerceAtLeast(0L) else 0L
          repository.updateDownloadProgress(
            id = id,
            downloaded = savedBytes,
            total = -1L,
            status = DownloadStatus.FAILED,
            localPath = targetFile.absolutePath,
          )
          exportHelper.postDownloadNotification(
            id,
            targetFile.name,
            "Download interrupted — tap Resume",
            ongoing = false,
          )
        } finally {
          activeJobs.remove(id)
        }
      }
    activeJobs[id] = job
  }

  private fun enqueueDataUrlDownload(
    dataUrl: String,
    suggestedFileName: String?,
    mimeType: String,
    contentDisposition: String?,
  ) {
    scope.launch(Dispatchers.IO) {
      val decoded = parseDataUrl(dataUrl) ?: return@launch
      val resolvedMime =
        resolveMimeType(
          fileName = suggestedFileName.orEmpty(),
          rawMimeType = decoded.mimeType.ifBlank { mimeType },
        )
      val rawName =
        suggestedFileName?.takeIf { it.isNotBlank() }
          ?: parseDispositionFileName(contentDisposition)
          ?: "volt_file_${System.currentTimeMillis() % 100_000}"
      val ensured = ensureFileExtension(rawName, resolvedMime, isAdaptiveStream = false)
      val finalName = sanitizeFileNamePreservingExtension(ensured)
      val downloadDir = resolveDownloadDirectory()
      val targetFile = uniqueTargetFile(downloadDir, finalName)

      val id =
        repository.insertDownload(
          DownloadItemEntity(
            url = dataUrl.take(256),
            fileName = targetFile.name,
            localPath = targetFile.absolutePath,
            mimeType = resolvedMime,
            totalBytes = decoded.bytes.size.toLong(),
            status = DownloadStatus.DOWNLOADING.name,
          )
        )

      try {
        FileOutputStream(targetFile).use { out ->
          out.write(decoded.bytes)
          out.flush()
        }
        val exported = exportHelper.exportFileToPublicDownloads(targetFile, targetFile.name, resolvedMime)
        repository.updateDownloadProgress(
          id = id,
          downloaded = decoded.bytes.size.toLong(),
          total = decoded.bytes.size.toLong(),
          status = DownloadStatus.COMPLETED,
          localPath = targetFile.absolutePath,
        )
        exportHelper.postDownloadNotification(
          id = id,
          title = targetFile.name,
          message =
            if (exported) "Saved to Downloads/VoltBrowser/${targetFile.name}"
            else "Saved inside Volt Browser (use Save As to export)",
          ongoing = false,
          targetFile = targetFile,
          mimeType = resolvedMime,
        )
      } catch (_: Exception) {
        repository.updateDownloadProgress(
          id = id,
          downloaded = 0L,
          total = -1L,
          status = DownloadStatus.FAILED,
          localPath = targetFile.absolutePath,
        )
      }
    }
  }

  private fun resolveDownloadDirectory(): File {
    val dir =
      context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: File(context.filesDir, "downloads")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  private suspend fun downloadDirectFile(
    id: Long,
    url: String,
    initialTargetFile: File,
    initialMimeType: String,
    hadExplicitName: Boolean = true,
    referer: String?,
    allowResume: Boolean = false,
    onProgressUpdate: suspend (File, Long, Long, Int) -> Unit = { _, _, _, _ -> },
  ) {
    val hasExistingSinglePartial =
      allowResume &&
        !SegmentedDownloader.segMetaFileFor(initialTargetFile).exists() &&
        initialTargetFile.exists() &&
        initialTargetFile.length() > 0L

    if (!hasExistingSinglePartial) {
      val probe = segmentedDownloader.probe(url, referer)
      if (segmentedDownloader.shouldUseSegmentedDownload(probe) && probe != null) {
        val (targetFile, resolvedFinalMime) =
          if (allowResume && SegmentedDownloader.segMetaFileFor(initialTargetFile).exists()) {
            initialTargetFile to resolveMimeType(initialTargetFile.name, initialMimeType)
          } else {
            resolveTargetFileAndMetadata(
              id = id,
              initialTargetFile = initialTargetFile,
              initialMimeType = initialMimeType,
              hadExplicitName = hadExplicitName,
              responseDisposition = probe.contentDisposition,
              responseContentType = normalizeMimeType(probe.contentType),
              finalRequestUrl = probe.finalUrl,
            )
          }

        val lastTrackedBytes = AtomicLong(0L)
        val segmentedResult =
          runCatching {
            segmentedDownloader.download(
              url = url,
              referer = referer,
              targetFile = targetFile,
              probe = probe,
              allowResume = allowResume,
            ) { downloaded, total, streams ->
              lastTrackedBytes.set(downloaded)
              repository.updateDownloadProgress(
                id = id,
                downloaded = downloaded,
                total = total,
                status = DownloadStatus.DOWNLOADING,
                localPath = targetFile.absolutePath,
              )
              onProgressUpdate(targetFile, downloaded, total, streams)
            }
          }

        if (segmentedResult.getOrDefault(false)) {
          finalizeCompletedFile(id, targetFile, resolvedFinalMime, probe.totalBytes)
          return
        }

        val err = segmentedResult.exceptionOrNull()
        if (err is CancellationException) throw err

        val savedOnDisk = SegmentedDownloader.readSavedProgressBytes(targetFile) ?: lastTrackedBytes.get()
        if (savedOnDisk > 0L) {
          throw (err ?: IOException("Segmented download interrupted at $savedOnDisk bytes"))
        }
        // Zero bytes written in segmented mode (e.g. host rejects concurrent ranges): fall back cleanly.
        runCatching { targetFile.delete() }
        SegmentedDownloader.cleanupMetaFile(targetFile)
      }
    }

    downloadDirectSingleStream(
      id = id,
      url = url,
      initialTargetFile = initialTargetFile,
      initialMimeType = initialMimeType,
      hadExplicitName = hadExplicitName,
      referer = referer,
      allowResume = allowResume,
      onProgressUpdate = onProgressUpdate,
    )
  }

  private suspend fun downloadDirectSingleStream(
    id: Long,
    url: String,
    initialTargetFile: File,
    initialMimeType: String,
    hadExplicitName: Boolean,
    referer: String?,
    allowResume: Boolean,
    onProgressUpdate: suspend (File, Long, Long, Int) -> Unit,
  ) {
    val existingBytes =
      if (allowResume && initialTargetFile.exists()) initialTargetFile.length().coerceAtLeast(0L)
      else 0L
    val request = buildResumeRequest(url, referer, existingBytes)

    okHttpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful || response.body == null) {
        throw IOException("Download failed (${response.code})")
      }

      val isResumed206 = existingBytes > 0L && response.code == 206
      val (targetFile, resolvedFinalMime) =
        if (isResumed206) {
          initialTargetFile to resolveMimeType(initialTargetFile.name, initialMimeType)
        } else {
          resolveTargetFileAndMetadata(
            id = id,
            initialTargetFile = initialTargetFile,
            initialMimeType = initialMimeType,
            hadExplicitName = hadExplicitName,
            responseDisposition = response.header("Content-Disposition"),
            responseContentType = normalizeMimeType(response.header("Content-Type")),
            finalRequestUrl = response.request.url.toString(),
          )
        }

      val body = response.body!!
      val baseOffset = if (isResumed206) existingBytes else 0L
      val totalBytes =
        if (isResumed206) {
          parseContentRangeTotalBytes(
            contentRange = response.header("Content-Range"),
            existingBytes = existingBytes,
            remainingLength = body.contentLength(),
          )
        } else {
          body.contentLength()
        }

      var downloadedBytes = baseOffset
      var lastReported = baseOffset
      val buffer = ByteArray(16 * 1024)

      body.byteStream().use { input ->
        FileOutputStream(targetFile, isResumed206).use { output ->
          while (true) {
            coroutineContext.ensureActive()
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
              onProgressUpdate(targetFile, downloadedBytes, totalBytes, 1)
            }
          }
          output.flush()
        }
      }

      if (downloadedBytes <= 0L) {
        throw IOException("Empty download response")
      }

      finalizeCompletedFile(
        id = id,
        targetFile = targetFile,
        resolvedFinalMime = resolvedFinalMime,
        finalSizeBytes = if (totalBytes > 0L) totalBytes else downloadedBytes,
      )
    }
  }

  private suspend fun resolveTargetFileAndMetadata(
    id: Long,
    initialTargetFile: File,
    initialMimeType: String,
    hadExplicitName: Boolean,
    responseDisposition: String?,
    responseContentType: String,
    finalRequestUrl: String,
  ): Pair<File, String> {
    val effectiveMime =
      if (responseContentType.isNotBlank() && responseContentType != "application/octet-stream") {
        responseContentType
      } else {
        initialMimeType
      }

    var targetFile = initialTargetFile
    val headerFileName = parseDispositionFileName(responseDisposition)
    if (!headerFileName.isNullOrBlank()) {
      val cleaned =
        sanitizeFileNamePreservingExtension(
          ensureFileExtension(headerFileName, effectiveMime, isAdaptiveStream = false)
        )
      if (cleaned != targetFile.name) {
        targetFile = uniqueTargetFile(targetFile.parentFile ?: resolveDownloadDirectory(), cleaned)
      }
    } else if (!hadExplicitName || targetFile.name.endsWith(".bin", ignoreCase = true)) {
      val guessed = guessDownloadFileName(finalRequestUrl, responseDisposition, effectiveMime, false)
      val cleaned = sanitizeFileNamePreservingExtension(guessed)
      if (cleaned != targetFile.name && !cleaned.endsWith(".bin", ignoreCase = true)) {
        targetFile = uniqueTargetFile(targetFile.parentFile ?: resolveDownloadDirectory(), cleaned)
      }
    }

    val resolvedFinalMime = resolveMimeType(targetFile.name, effectiveMime)
    if (targetFile.name != initialTargetFile.name || resolvedFinalMime != initialMimeType) {
      repository.updateDownloadMetadata(
        id = id,
        fileName = targetFile.name,
        mimeType = resolvedFinalMime,
        localPath = targetFile.absolutePath,
      )
    }
    return targetFile to resolvedFinalMime
  }

  private suspend fun finalizeCompletedFile(
    id: Long,
    targetFile: File,
    resolvedFinalMime: String,
    finalSizeBytes: Long,
  ) {
    val actualSize = targetFile.length().takeIf { it > 0L } ?: finalSizeBytes
    val exportedToPublic =
      exportHelper.exportFileToPublicDownloads(targetFile, targetFile.name, resolvedFinalMime)

    repository.updateDownloadProgress(
      id = id,
      downloaded = actualSize,
      total = actualSize,
      status = DownloadStatus.COMPLETED,
      localPath = targetFile.absolutePath,
    )
    exportHelper.postDownloadNotification(
      id = id,
      title = targetFile.name,
      message =
        if (exportedToPublic) "Saved to Downloads/VoltBrowser/${targetFile.name}"
        else "Saved inside Volt Browser (use Save As to export)",
      ongoing = false,
      targetFile = targetFile,
      mimeType = resolvedFinalMime,
    )
  }

  fun cancelDownload(id: Long) {
    MediaDownloadWorker.userCancelledIds.add(id)
    activeJobs.remove(id)?.cancel()
    runCatching { WorkManager.getInstance(context).cancelUniqueWork(MediaDownloadWorker.uniqueWorkName(id)) }
    scope.launch(Dispatchers.IO) {
      val existing = repository.getDownloadById(id)
      if (existing != null) {
        if (existing.localPath.isNotBlank()) {
          val file = File(existing.localPath)
          runCatching { file.delete() }
          SegmentedDownloader.cleanupMetaFile(file)
        }
        repository.updateDownloadProgress(
          id = id,
          downloaded = 0L,
          total = -1L,
          status = DownloadStatus.CANCELLED,
          localPath = existing.localPath,
        )
      }
    }
  }

  fun cancelAllDownloads() {
    val jobs = activeJobs.values.toList()
    activeJobs.clear()
    jobs.forEach { it.cancel() }
    runCatching { WorkManager.getInstance(context).cancelAllWorkByTag(MediaDownloadWorker.WORK_TAG) }
  }

  fun exportToPublicDownloads(item: DownloadItemEntity): Boolean =
    exportHelper.exportToPublicDownloads(item)

  fun copyDownloadedFileToUri(item: DownloadItemEntity, destUri: Uri): Boolean =
    exportHelper.copyDownloadedFileToUri(item, destUri)

  fun openDownloadedFile(item: DownloadItemEntity): Boolean =
    exportHelper.openDownloadedFile(item)

  fun shareDownloadedFile(item: DownloadItemEntity) =
    exportHelper.shareDownloadedFile(item)

  private fun uniqueTargetFile(dir: File, name: String): File {
    var candidate = File(dir, name)
    if (!candidate.exists() && !SegmentedDownloader.segMetaFileFor(candidate).exists()) {
      return candidate
    }
    val dotIdx = name.lastIndexOf('.')
    val base = if (dotIdx > 0) name.substring(0, dotIdx) else name
    val ext = if (dotIdx > 0) name.substring(dotIdx + 1) else ""
    var counter = 1
    while (candidate.exists() || SegmentedDownloader.segMetaFileFor(candidate).exists()) {
      val suffix = if (ext.isNotEmpty()) "_$counter.$ext" else "_$counter"
      candidate = File(dir, "$base$suffix")
      counter++
    }
    return candidate
  }

  companion object {
    const val MAX_DATA_URL_BYTES = DataUrlDecoder.MAX_DATA_URL_BYTES

    private val EXT_TO_MIME =
      mapOf(
        "pdf" to "application/pdf",
        "zip" to "application/zip",
        "7z" to "application/x-7z-compressed",
        "rar" to "application/vnd.rar",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "apk" to "application/vnd.android.package-archive",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "csv" to "text/csv",
        "txt" to "text/plain",
        "json" to "application/json",
        "xml" to "application/xml",
        "epub" to "application/epub+zip",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "svg" to "image/svg+xml",
        "avif" to "image/avif",
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "opus" to "audio/opus",
        "flac" to "audio/flac",
        "wav" to "audio/wav",
        "conf" to "text/plain",
      )

    private val MIME_TO_EXT =
      EXT_TO_MIME.entries.associate { (ext, mime) -> mime to ext } +
        mapOf(
          "application/x-mpegurl" to "m3u8",
          "application/dash+xml" to "mpd",
          "text/html" to "html",
          "application/octet-stream" to "bin",
        )

    fun buildResumeRequest(url: String, referer: String?, existingBytes: Long): Request {
      val builder = com.example.browser.MediaSniffer.buildBrowserRequestBuilder(url, referer)
      if (existingBytes > 0L) {
        builder.header("Range", "bytes=$existingBytes-")
      }
      return builder.build()
    }

    fun parseContentRangeTotalBytes(
      contentRange: String?,
      existingBytes: Long,
      remainingLength: Long,
    ): Long {
      val totalToken = contentRange?.substringAfterLast('/')?.trim()
      val parsedTotal = totalToken?.toLongOrNull()
      if (parsedTotal != null && parsedTotal > 0L) return parsedTotal
      return if (remainingLength > 0L) existingBytes + remainingLength else -1L
    }

    fun normalizeMimeType(rawMime: String?): String =
      rawMime?.substringBefore(';')?.trim()?.lowercase(Locale.US).orEmpty()

    fun resolveMimeType(fileName: String, rawMimeType: String?): String {
      val clean = normalizeMimeType(rawMimeType)
      val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
      if (clean.isNotBlank() && clean != "application/octet-stream" && clean != "binary/octet-stream") {
        return clean
      }
      if (ext.isNotBlank()) {
        EXT_TO_MIME[ext]?.let { return it }
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
      }
      return "application/octet-stream"
    }

    fun parseDispositionFileName(contentDisposition: String?): String? {
      if (contentDisposition.isNullOrBlank()) return null
      val extMatch =
        Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)''([^;\r\n]+)""")
          .find(contentDisposition)
          ?.groupValues
          ?.getOrNull(1)
      if (!extMatch.isNullOrBlank()) {
        val decoded =
          runCatching { URLDecoder.decode(extMatch.trim('"', '\'', ' '), "UTF-8") }.getOrNull()
        if (!decoded.isNullOrBlank()) return decoded
      }
      val plainMatch =
        Regex("""filename\s*=\s*"([^"]+)"|filename\s*=\s*([^;\r\n]+)""", RegexOption.IGNORE_CASE)
          .find(contentDisposition)
      val raw =
        plainMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
          ?: plainMatch?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
      return raw?.trim('"', '\'', ' ')
    }

    fun ensureFileExtension(name: String, mimeType: String?, isAdaptiveStream: Boolean): String {
      val trimmed = name.trim()
      if (isAdaptiveStream) {
        val base = if (trimmed.contains('.')) trimmed.substringBeforeLast('.') else trimmed
        return "${base.ifBlank { "volt_video" }}.mp4"
      }
      if (trimmed.contains('.') && trimmed.substringAfterLast('.').length in 1..8) {
        return trimmed
      }
      val cleanMime = normalizeMimeType(mimeType)
      val ext =
        MIME_TO_EXT[cleanMime]
          ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(cleanMime)
          ?: "bin"
      return "${trimmed.ifBlank { "volt_file" }}.$ext"
    }

    fun guessDownloadFileName(
      url: String,
      contentDisposition: String?,
      mimeType: String?,
      isAdaptiveStream: Boolean,
    ): String {
      if (isAdaptiveStream) return "volt_video_${System.currentTimeMillis() % 100_000}.mp4"
      val fromDisp = parseDispositionFileName(contentDisposition)
      if (!fromDisp.isNullOrBlank()) {
        return ensureFileExtension(fromDisp, mimeType, isAdaptiveStream = false)
      }
      val cleanMime = normalizeMimeType(mimeType).ifBlank { "application/octet-stream" }
      val urlGuessed =
        runCatching { URLUtil.guessFileName(url, contentDisposition, cleanMime) }
          .getOrNull()
          ?.takeIf { it.isNotBlank() && !it.startsWith("downloadfile", ignoreCase = true) }
      if (!urlGuessed.isNullOrBlank()) {
        return ensureFileExtension(urlGuessed, cleanMime, isAdaptiveStream = false)
      }
      val lastSegment =
        runCatching { url.toUri().lastPathSegment }
          .getOrNull()
          ?.takeIf { it.isNotBlank() && it != "/" }
      val base = lastSegment ?: "volt_file_${System.currentTimeMillis() % 100_000}"
      return ensureFileExtension(base, cleanMime, isAdaptiveStream = false)
    }

    fun sanitizeFileNamePreservingExtension(raw: String): String {
      val cleaned =
        raw.trim()
          .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
          .replace(Regex("\\s+"), " ")
          .trim('.', ' ')
      if (cleaned.isBlank()) return "volt_download.bin"
      val dotIdx = cleaned.lastIndexOf('.')
      if (dotIdx <= 0 || dotIdx == cleaned.lastIndex) {
        return cleaned.take(80)
      }
      val ext = cleaned.substring(dotIdx + 1).take(10)
      val maxBase = (80 - ext.length - 1).coerceAtLeast(16)
      val base = cleaned.substring(0, dotIdx).take(maxBase).trim('.', ' ')
      return "${base.ifBlank { "volt_file" }}.$ext"
    }

    fun parseDataUrl(dataUrl: String): DecodedDataUrl? = DataUrlDecoder.parse(dataUrl)
  }
}
