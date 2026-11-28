package com.example.media

import android.content.Context
import androidx.annotation.OptIn
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
import com.example.data.DownloadStatus
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
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

/**
 * Exports adaptive HLS/DASH playlists to local MP4 via Media3 Transformer, with an HLS
 * segment-concatenation fallback when hardware transcoding/remuxing cannot complete.
 */
internal class AdaptiveStreamExporter(
  private val context: Context,
  private val okHttpClient: OkHttpClient,
  private val repository: BrowserRepository,
  private val scope: CoroutineScope,
  private val exportHelper: MediaExportHelper,
  private val resolveUniqueFile: (File, String) -> File,
) {

  @OptIn(UnstableApi::class)
  suspend fun downloadAdaptiveStreamAsMp4(
    id: Long,
    playlistUrl: String,
    isHls: Boolean,
    targetFile: File,
    referer: String?,
    onByteRangeMp4Fallback: suspend (mp4Url: String, outFile: File) -> Unit,
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
        onByteRangeMp4Fallback(stats.directMp4Urls.first(), targetFile)
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
      downloadHlsStream(id, playlistUrl, fallbackFile, "video/mp2t", referer, onByteRangeMp4Fallback)
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
    }

  private suspend fun downloadHlsStream(
    id: Long,
    playlistUrl: String,
    initialTargetFile: File,
    initialMime: String,
    referer: String?,
    onByteRangeMp4Fallback: suspend (mp4Url: String, outFile: File) -> Unit,
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
      onByteRangeMp4Fallback(stats.directMp4Urls.first(), mp4File)
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
    val dir = file.parentFile ?: context.filesDir
    return resolveUniqueFile(dir, "$base.$newExt")
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
      okHttpClient.newCall(MediaSniffer.buildBrowserRequestBuilder(url, referer).build()).execute().use { res ->
        if (res.isSuccessful) res.body?.string() else null
      }
    }.getOrNull()

  private fun fetchBytes(url: String, referer: String?): ByteArray? =
    runCatching {
      okHttpClient.newCall(MediaSniffer.buildBrowserRequestBuilder(url, referer).build()).execute().use { res ->
        if (res.isSuccessful) res.body?.bytes() else null
      }
    }.getOrNull()
}
