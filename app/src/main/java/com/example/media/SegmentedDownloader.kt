package com.example.media

import com.example.browser.MediaSniffer
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Multi-connection HTTP Range downloader with per-segment checkpoint persistence (`.segmeta`).
 * Probes server range support, splits large payloads across up to [maxSegments] concurrent streams,
 * and resumes incomplete chunks cleanly across interruptions or WorkManager retries.
 */
internal class SegmentedDownloader(
  private val okHttpClient: OkHttpClient,
  private val maxSegments: Int = DEFAULT_MAX_SEGMENTS,
  private val minSegmentedBytes: Long = DEFAULT_MIN_SEGMENTED_BYTES,
) {

  data class ProbeResult(
    val totalBytes: Long,
    val acceptRanges: Boolean,
    val etag: String?,
    val lastModified: String?,
    val contentDisposition: String?,
    val contentType: String?,
    val finalUrl: String,
  )

  data class SegmentRange(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long = 0L,
  ) {
    val totalLength: Long
      get() = (endByte - startByte + 1L).coerceAtLeast(0L)

    val remainingBytes: Long
      get() = (totalLength - downloadedBytes).coerceAtLeast(0L)

    val isComplete: Boolean
      get() = downloadedBytes >= totalLength
  }

  /**
   * Probes content length, byte-range support, and validator headers before selecting
   * single-stream or multi-connection segmented execution.
   */
  fun probe(url: String, referer: String?): ProbeResult? {
    val headRequest = MediaSniffer.buildBrowserRequestBuilder(url, referer).head().build()
    val headProbe =
      runCatching {
        okHttpClient.newCall(headRequest).execute().use { res ->
          if (!res.isSuccessful) return@use null
          val len = res.header("Content-Length")?.toLongOrNull() ?: -1L
          val acceptRangesHeader = res.header("Accept-Ranges")?.trim()?.lowercase(Locale.US)
          val supportsRanges = acceptRangesHeader == "bytes" && len > 0L
          ProbeResult(
            totalBytes = len,
            acceptRanges = supportsRanges,
            etag = res.header("ETag"),
            lastModified = res.header("Last-Modified"),
            contentDisposition = res.header("Content-Disposition"),
            contentType = res.header("Content-Type"),
            finalUrl = res.request.url.toString(),
          )
        }
      }.getOrNull()

    if (headProbe != null && headProbe.acceptRanges && headProbe.totalBytes > 0L) {
      return headProbe
    }

    // Fallback for servers that reject HEAD or omit Accept-Ranges on HEAD: probe 1 byte via GET Range.
    val rangeProbeRequest =
      MediaSniffer.buildBrowserRequestBuilder(url, referer).header("Range", "bytes=0-0").build()
    val rangeProbe =
      runCatching {
        okHttpClient.newCall(rangeProbeRequest).execute().use { res ->
          if (res.code == 206) {
            val total =
              MediaDownloader.parseContentRangeTotalBytes(
                contentRange = res.header("Content-Range"),
                existingBytes = 0L,
                remainingLength = -1L,
              )
            if (total > 0L) {
              ProbeResult(
                totalBytes = total,
                acceptRanges = true,
                etag = res.header("ETag") ?: headProbe?.etag,
                lastModified = res.header("Last-Modified") ?: headProbe?.lastModified,
                contentDisposition =
                  res.header("Content-Disposition") ?: headProbe?.contentDisposition,
                contentType = res.header("Content-Type") ?: headProbe?.contentType,
                finalUrl = res.request.url.toString(),
              )
            } else {
              null
            }
          } else {
            null
          }
        }
      }.getOrNull()

    return rangeProbe ?: headProbe
  }

  fun shouldUseSegmentedDownload(probe: ProbeResult?): Boolean =
    probe != null &&
      probe.acceptRanges &&
      probe.totalBytes >= minSegmentedBytes &&
      maxSegments > 1

  /**
   * Executes a multi-connection HTTP Range download into [targetFile], saving per-segment
   * progress in a sibling `.segmeta` file for fault-tolerant resume.
   */
  suspend fun download(
    url: String,
    referer: String?,
    targetFile: File,
    probe: ProbeResult,
    allowResume: Boolean,
    onProgress: suspend (downloadedBytes: Long, totalBytes: Long, activeSegments: Int) -> Unit,
  ): Boolean {
    val totalBytes = probe.totalBytes
    if (totalBytes <= 0L) return false
    val metaFile = segMetaFileFor(targetFile)

    val initialSegments =
      loadOrCreateSegments(
        metaFile = metaFile,
        targetFile = targetFile,
        probe = probe,
        allowResume = allowResume,
      )

    if (!targetFile.exists() || targetFile.length() != totalBytes) {
      RandomAccessFile(targetFile, "rw").use { raf -> raf.setLength(totalBytes) }
    }

    val perSegmentDownloaded = LongArray(initialSegments.size) { initialSegments[it].downloadedBytes }
    val totalDownloaded = AtomicLong(perSegmentDownloaded.sum())
    val lastReportedBytes = AtomicLong(totalDownloaded.get())
    val lastPersistedBytes = AtomicLong(totalDownloaded.get())
    val stateLock = Any()

    saveSegmentMeta(metaFile, probe, initialSegments, perSegmentDownloaded)
    val incompleteCount = initialSegments.count { !it.isComplete }.coerceAtLeast(1)
    onProgress(totalDownloaded.get(), totalBytes, incompleteCount)

    val semaphore = Semaphore(maxSegments.coerceAtLeast(1))

    try {
      coroutineScope {
        initialSegments
          .map { segment ->
            async(Dispatchers.IO) {
              if (segment.isComplete) return@async
              semaphore.withPermit {
                downloadSingleSegment(
                  url = url,
                  referer = referer,
                  targetFile = targetFile,
                  segment = segment,
                  onBytesWritten = { delta ->
                    val newTotal = totalDownloaded.addAndGet(delta)
                    var shouldPersist = false
                    var shouldReport = false
                    synchronized(stateLock) {
                      perSegmentDownloaded[segment.index] += delta
                      if (newTotal - lastPersistedBytes.get() >= META_FLUSH_INTERVAL_BYTES) {
                        lastPersistedBytes.set(newTotal)
                        shouldPersist = true
                      }
                      if (newTotal - lastReportedBytes.get() >= PROGRESS_REPORT_INTERVAL_BYTES) {
                        lastReportedBytes.set(newTotal)
                        shouldReport = true
                      }
                    }
                    if (shouldPersist) {
                      synchronized(stateLock) {
                        saveSegmentMeta(metaFile, probe, initialSegments, perSegmentDownloaded)
                      }
                    }
                    if (shouldReport) {
                      onProgress(newTotal, totalBytes, incompleteCount)
                    }
                  },
                )
              }
            }
          }
          .awaitAll()
      }
    } catch (cancel: CancellationException) {
      withContext(NonCancellable) {
        synchronized(stateLock) {
          saveSegmentMeta(metaFile, probe, initialSegments, perSegmentDownloaded)
        }
      }
      throw cancel
    } catch (io: Exception) {
      withContext(NonCancellable) {
        synchronized(stateLock) {
          saveSegmentMeta(metaFile, probe, initialSegments, perSegmentDownloaded)
        }
      }
      throw io
    }

    val completedAll =
      initialSegments.indices.all { idx ->
        perSegmentDownloaded[idx] >= initialSegments[idx].totalLength
      }
    if (!completedAll || totalDownloaded.get() < totalBytes) {
      synchronized(stateLock) {
        saveSegmentMeta(metaFile, probe, initialSegments, perSegmentDownloaded)
      }
      throw IOException("Incomplete segmented download (${totalDownloaded.get()}/$totalBytes)")
    }

    onProgress(totalBytes, totalBytes, incompleteCount)
    runCatching { metaFile.delete() }
    return true
  }

  private suspend fun downloadSingleSegment(
    url: String,
    referer: String?,
    targetFile: File,
    segment: SegmentRange,
    onBytesWritten: suspend (Long) -> Unit,
  ) {
    val startOffset = segment.startByte + segment.downloadedBytes
    if (startOffset > segment.endByte) return

    val request: Request =
      MediaSniffer.buildBrowserRequestBuilder(url, referer)
        .header("Range", "bytes=$startOffset-${segment.endByte}")
        .build()

    okHttpClient.newCall(request).execute().use { response ->
      if (response.code != 206) {
        throw IOException("Server did not honor byte range for segment ${segment.index} (${response.code})")
      }
      val body = response.body ?: throw IOException("Empty response body for segment ${segment.index}")
      var remainingExpected = (segment.endByte - startOffset + 1L).coerceAtLeast(0L)
      val buffer = ByteArray(16 * 1024)

      RandomAccessFile(targetFile, "rw").use { raf ->
        raf.seek(startOffset)
        body.byteStream().use { input ->
          while (remainingExpected > 0L) {
            coroutineContext.ensureActive()
            val maxToRead = minOf(buffer.size.toLong(), remainingExpected).toInt()
            val read = input.read(buffer, 0, maxToRead)
            if (read == -1) break
            raf.write(buffer, 0, read)
            remainingExpected -= read.toLong()
            onBytesWritten(read.toLong())
          }
        }
      }

      if (remainingExpected > 0L) {
        throw IOException("Segment ${segment.index} truncated with $remainingExpected bytes remaining")
      }
    }
  }

  private fun loadOrCreateSegments(
    metaFile: File,
    targetFile: File,
    probe: ProbeResult,
    allowResume: Boolean,
  ): List<SegmentRange> {
    if (allowResume && metaFile.exists() && targetFile.exists() && targetFile.length() == probe.totalBytes) {
      val parsed = parseSegmentMeta(metaFile, probe)
      if (!parsed.isNullOrEmpty()) return parsed
    }
    return planSegments(probe.totalBytes, maxSegments)
  }

  private fun saveSegmentMeta(
    metaFile: File,
    probe: ProbeResult,
    segments: List<SegmentRange>,
    downloadedCounts: LongArray,
  ) {
    runCatching {
      val arr = JSONArray()
      segments.forEachIndexed { idx, seg ->
        arr.put(
          JSONObject()
            .put("index", seg.index)
            .put("start", seg.startByte)
            .put("end", seg.endByte)
            .put("downloaded", downloadedCounts[idx].coerceIn(0L, seg.totalLength))
        )
      }
      val root =
        JSONObject()
          .put("totalBytes", probe.totalBytes)
          .put("etag", probe.etag.orEmpty())
          .put("lastModified", probe.lastModified.orEmpty())
          .put("segments", arr)
      metaFile.writeText(root.toString())
    }
  }

  private fun parseSegmentMeta(metaFile: File, probe: ProbeResult): List<SegmentRange>? {
    return runCatching {
      val root = JSONObject(metaFile.readText())
      if (root.optLong("totalBytes", -1L) != probe.totalBytes) return null
      val savedEtag = root.optString("etag", "")
      if (savedEtag.isNotBlank() && !probe.etag.isNullOrBlank() && savedEtag != probe.etag) {
        return null
      }
      val savedLastMod = root.optString("lastModified", "")
      if (savedLastMod.isNotBlank() && !probe.lastModified.isNullOrBlank() && savedLastMod != probe.lastModified) {
        return null
      }
      val arr = root.optJSONArray("segments") ?: return null
      val list = ArrayList<SegmentRange>(arr.length())
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val start = obj.getLong("start")
        val end = obj.getLong("end")
        val downloaded = obj.optLong("downloaded", 0L).coerceIn(0L, (end - start + 1L).coerceAtLeast(0L))
        list.add(SegmentRange(index = obj.optInt("index", i), startByte = start, endByte = end, downloadedBytes = downloaded))
      }
      list.takeIf { it.isNotEmpty() && it.first().startByte == 0L && it.last().endByte == probe.totalBytes - 1L }
    }.getOrNull()
  }

  companion object {
    const val DEFAULT_MAX_SEGMENTS = 8
    const val DEFAULT_MIN_SEGMENTED_BYTES = 1_048_576L // 1 MB
    private const val META_FLUSH_INTERVAL_BYTES = 131_072L // 128 KB
    private const val PROGRESS_REPORT_INTERVAL_BYTES = 65_536L // 64 KB

    fun segMetaFileFor(targetFile: File): File =
      File(targetFile.parentFile, "${targetFile.name}.segmeta")

    fun cleanupMetaFile(targetFile: File) {
      runCatching { segMetaFileFor(targetFile).delete() }
    }

    fun readSavedProgressBytes(targetFile: File): Long? {
      val metaFile = segMetaFileFor(targetFile)
      if (!metaFile.exists()) return null
      return runCatching {
        val arr = JSONObject(metaFile.readText()).optJSONArray("segments") ?: return null
        var sum = 0L
        for (i in 0 until arr.length()) {
          sum += arr.getJSONObject(i).optLong("downloaded", 0L).coerceAtLeast(0L)
        }
        sum
      }.getOrNull()
    }

    /**
     * Computes contiguous `[startByte..endByte]` chunks spanning `0 until totalBytes`.
     */
    fun planSegments(totalBytes: Long, maxSegments: Int = DEFAULT_MAX_SEGMENTS): List<SegmentRange> {
      if (totalBytes <= 0L) return emptyList()
      val count = maxSegments.coerceAtLeast(1).coerceAtMost(totalBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
      val baseChunk = totalBytes / count
      val remainder = totalBytes % count
      val result = ArrayList<SegmentRange>(count)
      var cursor = 0L
      for (i in 0 until count) {
        val size = baseChunk + if (i < remainder) 1L else 0L
        val start = cursor
        val end = cursor + size - 1L
        result.add(SegmentRange(index = i, startByte = start, endByte = end, downloadedBytes = 0L))
        cursor = end + 1L
      }
      return result
    }
  }
}
