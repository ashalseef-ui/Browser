package com.example.media

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.browser.MediaSniffer
import com.example.browser.PlaylistResolver
import com.example.data.BrowserRepository
import com.example.data.DownloadItemEntity
import com.example.data.DownloadStatus
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Coordinates HTTP direct downloads (with HTTP Range resume support), RFC 2397 `data:` decoding,
 * and Media3 Transformer remuxing/export of adaptive HLS/DASH streams.
 */
class MediaDownloader(
  private val context: Context,
  private val okHttpClient: OkHttpClient,
  private val repository: BrowserRepository,
  private val scope: CoroutineScope,
) {
  private val activeJobs = ConcurrentHashMap<Long, Job>()
  private val exportHelper = MediaExportHelper(context)

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
            status = DownloadStatus.DOWNLOADING.name,
          )
        )
      launchDownloadJob(
        id = id,
        trimmedUrl = trimmedUrl,
        isAdaptiveStream = isAdaptiveStream,
        isHls = isHls,
        targetFile = targetFile,
        resolvedMime = resolvedMime,
        hadExplicitName = !suggestedFileName.isNullOrBlank() || !contentDisposition.isNullOrBlank(),
        referer = referer,
        resumePartial = false,
      )
    }
  }

  /**
   * Resumes a failed direct HTTP download using HTTP `Range` when a partial file exists on disk,
   * or restarts cleanly if adaptive or missing on disk.
   */
  fun resumeOrRetryDownload(item: DownloadItemEntity, referer: String? = null) {
    val trimmedUrl = item.url.trim()
    val lowerUrl = trimmedUrl.lowercase(Locale.US)
    val cleanMime = normalizeMimeType(item.mimeType)
    val isHls = lowerUrl.contains(".m3u8") || cleanMime.equals("application/x-mpegurl", true)
    val isDash = lowerUrl.contains(".mpd") || cleanMime.equals("application/dash+xml", true)
    val isAdaptiveStream = isHls || isDash
    val partialFile = item.localPath.takeIf { it.isNotBlank() }?.let(::File)

    if (isAdaptiveStream || partialFile == null || !partialFile.exists() || partialFile.length() <= 0L) {
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
    scope.launch(Dispatchers.IO) {
      val existingLen = partialFile.length()
      repository.updateDownloadProgress(
        id = item.id,
        downloaded = existingLen,
        total = item.totalBytes,
        status = DownloadStatus.DOWNLOADING,
        localPath = partialFile.absolutePath,
      )
      launchDownloadJob(
        id = item.id,
        trimmedUrl = trimmedUrl,
        isAdaptiveStream = false,
        isHls = false,
        targetFile = partialFile,
        resolvedMime = resolveMimeType(item.fileName, item.mimeType),
        hadExplicitName = true,
        referer = referer,
        resumePartial = true,
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
      launch {
        try {
          if (isAdaptiveStream) {
            downloadAdaptiveStreamAsMp4(id, trimmedUrl, isHls, targetFile, resolvedMime, referer)
          } else {
            downloadDirectFile(
              id = id,
              url = trimmedUrl,
              initialTargetFile = targetFile,
              initialMimeType = resolvedMime,
              hadExplicitName = hadExplicitName,
              referer = referer,
              allowResume = resumePartial,
            )
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

  private fun buildRequest(url: String, referer: String?): Request =
    MediaSniffer.buildBrowserRequestBuilder(url, referer).build()

  private suspend fun downloadDirectFile(
    id: Long,
    url: String,
    initialTargetFile: File,
    initialMimeType: String,
    hadExplicitName: Boolean = true,
    referer: String?,
    allowResume: Boolean = false,
  ) {
    val existingBytes =
      if (allowResume && initialTargetFile.exists()) initialTargetFile.length().coerceAtLeast(0L)
      else 0L
    val request = buildResumeRequest(url, referer, existingBytes)

    okHttpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful || response.body == null) {
        repository.updateDownloadProgress(
          id = id,
          downloaded = existingBytes,
          total = -1L,
          status = DownloadStatus.FAILED,
          localPath = initialTargetFile.absolutePath,
        )
        exportHelper.postDownloadNotification(
          id,
          initialTargetFile.name,
          "Download failed (${response.code})",
          ongoing = false,
        )
        return
      }

      val isResumed206 = existingBytes > 0L && response.code == 206
      val responseDisposition = response.header("Content-Disposition")
      val responseContentType = normalizeMimeType(response.header("Content-Type"))
      val finalRequestUrl = response.request.url.toString()
      val effectiveMime =
        if (responseContentType.isNotBlank() && responseContentType != "application/octet-stream") {
          responseContentType
        } else {
          initialMimeType
        }

      var targetFile = initialTargetFile
      if (!isResumed206) {
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
        exportHelper.exportFileToPublicDownloads(targetFile, targetFile.name, resolvedFinalMime)

      repository.updateDownloadProgress(
        id = id,
        downloaded = downloadedBytes,
        total = if (totalBytes > 0) totalBytes else downloadedBytes,
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

      if (stats.hasByteRange && stats.directMp4Urls.size == 1) {
        downloadDirectFile(id, stats.directMp4Urls.first(), targetFile, "video/mp4", referer = referer)
        return
      }
    }

    val remuxedOk = exportWithMedia3Transformer(id, playlistUrl, isHls, targetFile, referer)
    if (remuxedOk && targetFile.exists() && targetFile.length() > 0L) {
      val size = targetFile.length()
      val exportedToPublic =
        exportHelper.exportFileToPublicDownloads(targetFile, targetFile.name, "video/mp4")
      repository.updateDownloadProgress(
        id = id,
        downloaded = size,
        total = size,
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
        mimeType = "video/mp4",
      )
      return
    }

    if (isHls) {
      val fallbackFile = renameExtension(targetFile, "ts")
      repository.updateDownloadMetadata(id, fallbackFile.name, "video/mp2t", fallbackFile.absolutePath)
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
    referer: String?,
  ): Boolean =
    withContext(Dispatchers.Main) {
      val mediaItem =
        MediaItem.Builder()
          .setUri(streamUrl)
          .setMimeType(if (isHls) "application/x-mpegURL" else "application/dash+xml")
          .build()

      val validReferer =
        referer?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
      val originHeader =
        validReferer?.let { ref ->
          runCatching {
            val u = URI(ref)
            if (!u.scheme.isNullOrBlank() && !u.authority.isNullOrBlank()) {
              "${u.scheme}://${u.authority}"
            } else null
          }.getOrNull()
        }

      val mediaOkHttp =
        okHttpClient
          .newBuilder()
          .addInterceptor { chain ->
            val orig = chain.request()
            val builder = orig.newBuilder().header("Accept", "*/*")
            if (validReferer != null && orig.header("Referer") == null) {
              builder.header("Referer", validReferer)
            }
            if (originHeader != null && orig.header("Origin") == null) {
              builder.header("Origin", originHeader)
            }
            if (orig.header("Cookie") == null) {
              MediaSniffer.getCookiesForUrl(orig.url.toString())?.let { cookie ->
                builder.header("Cookie", cookie)
              }
            }
            chain.proceed(builder.build())
          }
          .build()

      val httpFactory =
        OkHttpDataSource.Factory(mediaOkHttp)
          .setUserAgent(MediaSniffer.DEFAULT_BROWSER_USER_AGENT)
      val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
      val assetLoaderFactory =
        ExoPlayerAssetLoader.Factory(
          context,
          DefaultDecoderFactory.Builder(context).build(),
          Clock.DEFAULT,
          DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory),
        )

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
                .setAssetLoaderFactory(assetLoaderFactory)
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

    if (stats.hasByteRange && stats.directMp4Urls.size == 1) {
      val mp4File = renameExtension(initialTargetFile, "mp4")
      downloadDirectFile(id, stats.directMp4Urls.first(), mp4File, "video/mp4", referer = referer)
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
    if (finalTargetFile.name != initialTargetFile.name || finalMime != initialMime) {
      repository.updateDownloadMetadata(id, finalTargetFile.name, finalMime, finalTargetFile.absolutePath)
    }

    var totalWritten = 0L
    FileOutputStream(finalTargetFile).use { output ->
      stats.initMapUrl?.let { mapUrl ->
        val mapBytes = fetchBytes(mapUrl, referer)
        if (mapBytes != null) {
          output.write(mapBytes)
          totalWritten += mapBytes.size
        }
      }

      stats.segmentUrls.forEachIndexed { index, segUrl ->
        coroutineContext.ensureActive()
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

    if (totalWritten <= 0L) throw IllegalStateException("Empty HLS download")

    val exportedToPublic =
      exportHelper.exportFileToPublicDownloads(finalTargetFile, finalTargetFile.name, finalMime)

    repository.updateDownloadProgress(
      id = id,
      downloaded = totalWritten,
      total = totalWritten,
      status = DownloadStatus.COMPLETED,
      localPath = finalTargetFile.absolutePath,
    )
    exportHelper.postDownloadNotification(
      id = id,
      title = finalTargetFile.name,
      message =
        if (exportedToPublic) "Saved to Downloads/VoltBrowser/${finalTargetFile.name}"
        else "Saved inside Volt Browser (use Save As to export)",
      ongoing = false,
      targetFile = finalTargetFile,
      mimeType = finalMime,
    )
  }

  private fun renameExtension(file: File, newExt: String): File {
    val base = file.name.substringBeforeLast('.')
    return uniqueTargetFile(file.parentFile ?: resolveDownloadDirectory(), "$base.$newExt")
  }

  private fun resolveHighestBandwidthVariant(masterUrl: String, playlistText: String): String {
    val lines = playlistText.lines()
    var bestBandwidth = -1L
    var bestUri: String? = null
    val base = runCatching { URI(masterUrl) }.getOrNull() ?: return masterUrl

    for (i in lines.indices) {
      val line = lines[i].trim()
      if (line.startsWith("#EXT-X-STREAM-INF:")) {
        val bw =
          Regex("BANDWIDTH=(\\d+)")
            .find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull() ?: 0L
        val nextLine =
          lines.drop(i + 1).firstOrNull { it.trim().isNotEmpty() && !it.trim().startsWith("#") }?.trim()
        if (nextLine != null && bw >= bestBandwidth) {
          val resolved = runCatching { base.resolve(nextLine).toString() }.getOrNull()
          if (resolved != null) {
            bestBandwidth = bw
            bestUri = resolved
          }
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
    if (!candidate.exists()) return candidate
    val dotIdx = name.lastIndexOf('.')
    val base = if (dotIdx > 0) name.substring(0, dotIdx) else name
    val ext = if (dotIdx > 0) name.substring(dotIdx + 1) else ""
    var counter = 1
    while (candidate.exists()) {
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
      val builder = MediaSniffer.buildBrowserRequestBuilder(url, referer)
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
