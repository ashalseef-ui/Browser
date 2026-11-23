package com.example

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import com.example.browser.DeviceTraceCleaner
import com.example.browser.MediaSniffer
import com.example.browser.PlaylistResolver
import com.example.browser.TurboShield
import com.example.browser.VoltSpeedEngine
import com.example.data.DetectedMediaItem
import com.example.data.MediaKind
import com.example.data.SearchEngine
import com.example.vpn.DnsPacketCodec
import com.example.vpn.PublicRelayManager
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

  @Test
  fun `app name matches Volt Browser`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Volt Browser", appName)
  }

  @Test
  fun `turbo shield blocks known tracker subdomains and respects blocker tiers`() {
    assertTrue(TurboShield.shouldBlock(Uri.parse("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js")))
    assertTrue(TurboShield.shouldBlock(Uri.parse("https://www.google-analytics.com/analytics.js")))
    assertFalse(TurboShield.shouldBlock(Uri.parse("https://en.wikipedia.org/wiki/Android")))

    val adGuardUri = Uri.parse("https://cdn.ezoic.net/ezoic/sa.min.js")
    val popupUri = Uri.parse("https://srv.popads.net/pop.js")
    assertFalse(TurboShield.shouldBlock(adGuardUri, com.example.data.AdBlockerType.OFF))
    assertFalse(TurboShield.shouldBlock(adGuardUri, com.example.data.AdBlockerType.STANDARD))
    assertTrue(TurboShield.shouldBlock(adGuardUri, com.example.data.AdBlockerType.ADGUARD))
    assertFalse(TurboShield.shouldBlock(popupUri, com.example.data.AdBlockerType.ADGUARD))
    assertTrue(TurboShield.shouldBlock(popupUri, com.example.data.AdBlockerType.STRICT))

    assertNull(TurboShield.cosmeticFilterScript(com.example.data.AdBlockerType.OFF))
    assertNull(TurboShield.cosmeticFilterScript(com.example.data.AdBlockerType.STANDARD))
    val adgScript = TurboShield.cosmeticFilterScript(com.example.data.AdBlockerType.ADGUARD)
    assertNotNull(adgScript)
    assertTrue(adgScript!!.contains("adsbygoogle"))
    val strictScript = TurboShield.cosmeticFilterScript(com.example.data.AdBlockerType.STRICT)
    assertNotNull(strictScript)
    assertTrue(strictScript!!.contains("__voltPopupGuard"))
    assertEquals(com.example.data.AdBlockerType.ADGUARD, com.example.data.BrowserSettings().adBlockerType)
  }

  @Test
  fun `media sniffer detects mp4 hls dash and audio streams and filters ts chunks`() {
    val hls = MediaSniffer.classifyUrl("https://cdn.example.com/live/master.m3u8", "https://example.com", "Stream")
    assertNotNull(hls)
    assertEquals(MediaKind.STREAM_HLS, hls?.kind)

    val tsChunk = MediaSniffer.classifyUrl("https://cdn.example.com/live/seg_001.ts", "https://example.com", "Stream")
    assertNull(tsChunk)

    val mp4 = MediaSniffer.classifyUrl("https://cdn.example.com/video.mp4?token=123", "https://example.com", "Clip")
    assertNotNull(mp4)
    assertEquals(MediaKind.VIDEO, mp4?.kind)

    val queryMimeVideo = MediaSniffer.classifyUrl("https://cdn.example.com/stream?id=abc&mime=video%2Fmp4", "https://example.com", "CDN Clip")
    assertNotNull(queryMimeVideo)
    assertEquals(MediaKind.VIDEO, queryMimeVideo?.kind)

    val audio = MediaSniffer.classifyUrl("https://cdn.example.com/track.mp3", "https://example.com", "Track")
    assertNotNull(audio)
    assertEquals(MediaKind.AUDIO, audio?.kind)

    val html = MediaSniffer.classifyUrl("https://example.com/index.html", "https://example.com", "Page")
    assertNull(html)
  }

  @Test
  fun `speed engine normalizes domains and search queries`() {
    assertEquals(
      "https://wikipedia.org",
      VoltSpeedEngine.normalizeInputToUrl("wikipedia.org", SearchEngine.DUCKDUCKGO),
    )
    assertEquals(
      "https://duckduckgo.com/?q=android+media3",
      VoltSpeedEngine.normalizeInputToUrl("android media3", SearchEngine.DUCKDUCKGO),
    )
  }

  @Test
  fun `dns packet codec parses ipv4 udp query and builds valid swapped response`() {
    val dnsQuery = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    val totalLen = 20 + 8 + dnsQuery.size
    val rawPacket = ByteArray(totalLen)
    rawPacket[0] = 0x45.toByte()
    rawPacket[2] = ((totalLen ushr 8) and 0xFF).toByte()
    rawPacket[3] = (totalLen and 0xFF).toByte()
    rawPacket[9] = 17.toByte() // UDP
    // Src IP 10.111.222.1 -> Dst IP 10.111.222.2
    rawPacket[12] = 10; rawPacket[13] = 111; rawPacket[14] = 222.toByte(); rawPacket[15] = 1
    rawPacket[16] = 10; rawPacket[17] = 111; rawPacket[18] = 222.toByte(); rawPacket[19] = 2
    // Src port 54321 -> Dst port 53
    rawPacket[20] = (54321 ushr 8).toByte(); rawPacket[21] = (54321 and 0xFF).toByte()
    rawPacket[22] = 0; rawPacket[23] = 53
    val udpLen = 8 + dnsQuery.size
    rawPacket[24] = (udpLen ushr 8).toByte(); rawPacket[25] = (udpLen and 0xFF).toByte()
    System.arraycopy(dnsQuery, 0, rawPacket, 28, dnsQuery.size)

    val parsed = DnsPacketCodec.parseIpv4UdpDnsQuery(rawPacket, totalLen)
    assertNotNull(parsed)
    assertEquals(54321, parsed!!.srcPort)
    assertEquals(53, parsed.dstPort)
    assertArrayEquals(dnsQuery, parsed.dnsPayload)

    val dnsResponse = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte())
    val built = DnsPacketCodec.buildIpv4UdpDnsResponse(parsed, dnsResponse)
    assertEquals(20 + 8 + dnsResponse.size, built.size)
    // Verify swapped IPs and ports
    assertEquals(2, built[15].toInt() and 0xFF)
    assertEquals(1, built[19].toInt() and 0xFF)
    assertEquals(53, ((built[20].toInt() and 0xFF) shl 8) or (built[21].toInt() and 0xFF))
    assertEquals(54321, ((built[22].toInt() and 0xFF) shl 8) or (built[23].toInt() and 0xFF))
  }

  @Test
  fun `public relay manager parses plain proxy feeds and cloudflare trace responses`() {
    val rawList = """
      203.0.113.10:8080
      invalid-line
      198.51.100.42:1080
      99.99.99.99:99999
    """.trimIndent()
    val parsed = PublicRelayManager.parsePlainProxyLines(rawList, protocol = "SOCKS5", source = "ProxyScrape")
    assertEquals(2, parsed.size)
    assertEquals("203.0.113.10", parsed[0].ip)
    assertEquals(8080, parsed[0].port)
    assertEquals("SOCKS5", parsed[0].protocol)

    val traceBody = """
      fl=29f12
      ip=198.51.100.77
      loc=DE
      tls=TLSv1.3
    """.trimIndent()
    val trace = PublicRelayManager.parseCloudflareTrace(traceBody)
    assertNotNull(trace)
    assertEquals("198.51.100.77", trace!!.ip)
    assertEquals("DE", trace.countryCode)
    assertEquals("Germany", trace.country)
  }

  @Test
  fun `playlist resolver extracts m3u8 variants mpd dash baseurl m3u pls xspf and content range sizes`() {
    assertEquals(42_500_000L, MediaSniffer.parseTotalSizeFromContentRange("bytes 0-0/42500000"))
    assertNull(MediaSniffer.parseTotalSizeFromContentRange("bytes 0-0/*"))

    val parentHls =
      DetectedMediaItem(
        url = "https://cdn.example.com/videos/master.m3u8",
        title = "Demo Stream",
        kind = MediaKind.STREAM_HLS,
        mimeType = "application/x-mpegURL",
        sourcePageUrl = "https://example.com",
      )
    val hlsMaster = """
      #EXTM3U
      #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
      low/index.m3u8
      #EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080
      hd/index.m3u8
    """.trimIndent()
    val variants = PlaylistResolver.parseHlsMasterVariants(parentHls, hlsMaster)
    assertEquals(2, variants.size)
    assertEquals("1080p", variants[0].resolutionLabel)
    assertEquals("https://cdn.example.com/videos/hd/index.m3u8", variants[0].url)

    val mediaPlaylist = """
      #EXTM3U
      #EXTINF:6.0,
      seg1.ts
      #EXTINF:4.0,
      seg2.ts
    """.trimIndent()
    val stats = PlaylistResolver.parseHlsMediaPlaylistStats("https://cdn.example.com/videos/hd/index.m3u8", mediaPlaylist)
    assertEquals(2, stats.segmentUrls.size)
    assertEquals(10L, stats.totalDurationSeconds)

    val parentMpd = parentHls.copy(url = "https://cdn.example.com/dash/manifest.mpd", kind = MediaKind.STREAM_DASH)
    val mpdXml = """
      <MPD mediaPresentationDuration="PT1M40S">
        <Period>
          <AdaptationSet>
            <Representation id="1" bandwidth="2000000" height="720" mimeType="video/mp4">
              <BaseURL>video_720p.mp4</BaseURL>
            </Representation>
          </AdaptationSet>
        </Period>
      </MPD>
    """.trimIndent()
    val dashItems = PlaylistResolver.parseDashMpd(parentMpd, mpdXml)
    assertEquals(1, dashItems.size)
    assertEquals(MediaKind.VIDEO, dashItems[0].kind)
    assertEquals("https://cdn.example.com/dash/video_720p.mp4", dashItems[0].url)
    assertEquals(100L, dashItems[0].durationSeconds)
    assertEquals(25_000_000L, dashItems[0].contentSizeBytes)

    val parentPls = parentHls.copy(url = "https://cdn.example.com/list.pls", kind = MediaKind.PLAYLIST)
    val plsText = """
      [playlist]
      File1=https://cdn.example.com/clip1.mp4
      Title1=First Clip
      Length1=90
    """.trimIndent()
    val plsItems = PlaylistResolver.parsePlsPlaylist(parentPls, plsText)
    assertEquals(1, plsItems.size)
    assertEquals("First Clip", plsItems[0].title)
    assertEquals(90L, plsItems[0].durationSeconds)

    val m3uItems = PlaylistResolver.parseStandardM3u(
      parentPls.copy(url = "https://cdn.example.com/mix.m3u"),
      "#EXTM3U\n#EXTINF:120,Sample MP4\nmedia/sample.mp4",
    )
    assertEquals(1, m3uItems.size)
    assertEquals("https://cdn.example.com/media/sample.mp4", m3uItems[0].url)

    val xspfItems = PlaylistResolver.parseXspfPlaylist(
      parentPls.copy(url = "https://cdn.example.com/mix.xspf"),
      "<playlist><trackList><track><location>https://cdn.example.com/movie.webm</location><title>WebM Video</title><duration>45000</duration></track></trackList></playlist>",
    )
    assertEquals(1, xspfItems.size)
    assertEquals("WebM Video", xspfItems[0].title)
    assertEquals(45L, xspfItems[0].durationSeconds)
  }

  @Test
  fun `playlist resolver extracts mp4 links from byterange fmp4 hls and escaped json text`() {
    val byteRangeHls = """
      #EXTM3U
      #EXT-X-TARGETDURATION:10
      #EXT-X-MAP:URI="init.mp4"
      #EXTINF:10.0,
      #EXT-X-BYTERANGE:102400@0
      full_movie.mp4
      #EXTINF:10.0,
      #EXT-X-BYTERANGE:102400@102400
      full_movie.mp4
    """.trimIndent()
    val stats =
      PlaylistResolver.parseHlsMediaPlaylistStats(
        "https://cdn.example.com/vod/playlist.m3u8",
        byteRangeHls,
      )
    assertTrue(stats.hasByteRange)
    assertEquals("https://cdn.example.com/vod/init.mp4", stats.initMapUrl)
    assertEquals(listOf("https://cdn.example.com/vod/full_movie.mp4"), stats.directMp4Urls)

    val jsonHtmlSnippet = """
      {"hls":"https:\/\/cdn.example.com\/hls\/master.m3u8?t=1","mp4":"https:\/\/cdn.example.com\/files\/video_1080p.mp4?token=abc"}
    """.trimIndent()
    val extracted =
      PlaylistResolver.extractMediaLinksFromText(
        "https://example.com/watch",
        "Watch Demo",
        jsonHtmlSnippet,
      )
    assertEquals(2, extracted.size)
    // Direct .mp4 file should be prioritized first
    assertEquals(MediaKind.VIDEO, extracted[0].kind)
    assertEquals("https://cdn.example.com/files/video_1080p.mp4?token=abc", extracted[0].url)
    assertEquals(MediaKind.STREAM_HLS, extracted[1].kind)
  }

  @Test
  fun `media sniffer verifies only downloadable video responses larger than 1 MB and flags drm hls playlists`() {
    assertTrue(MediaSniffer.isDownloadableVideoResponse(200, "video/mp4", 15_000_000L))
    assertTrue(MediaSniffer.isDownloadableVideoResponse(206, "application/octet-stream", 1_048_577L))
    assertFalse(MediaSniffer.isDownloadableVideoResponse(200, "video/mp4", 1_048_576L))
    assertFalse(MediaSniffer.isDownloadableVideoResponse(200, "video/mp4", null))
    assertFalse(MediaSniffer.isDownloadableVideoResponse(403, "video/mp4", 5_200_000L))
    assertFalse(MediaSniffer.isDownloadableVideoResponse(200, "text/html; charset=utf-8", 5_200_000L))
    assertFalse(MediaSniffer.isDownloadableVideoResponse(200, "audio/mpeg", 4_000_000L))
    assertFalse(MediaSniffer.isDownloadableVideoResponse(200, "video/mp4", 512L))

    val drmPlaylist = """
      #EXTM3U
      #EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://fairplay.example.com/key"
      #EXTINF:6.0,
      seg1.ts
    """.trimIndent()
    val drmStats =
      PlaylistResolver.parseHlsMediaPlaylistStats(
        "https://cdn.example.com/drm/stream.m3u8",
        drmPlaylist,
      )
    assertTrue(drmStats.isDrmProtected)
  }

  @Test
  fun `playlist resolver extracts relative video tags json contentUrl and browser headers`() {
    val htmlWithJsonLdAndSource = """
      <html>
        <script type="application/ld+json">
          {"@type":"VideoObject","contentUrl":"https:\/\/cdn.example.com\/ld\/feature.mp4?a=1\u0026b=2"}
        </script>
        <video><source src="/assets/trailer_720p.mp4" type="video/mp4"></video>
      </html>
    """.trimIndent()
    val extracted =
      PlaylistResolver.extractMediaLinksFromText(
        "https://watch.example.com/player/42",
        "Watch Feature",
        htmlWithJsonLdAndSource,
      )
    val urls = extracted.map { it.url }
    assertTrue(urls.contains("https://cdn.example.com/ld/feature.mp4?a=1&b=2"))
    assertTrue(urls.contains("https://watch.example.com/assets/trailer_720p.mp4"))

    val req =
      MediaSniffer.buildBrowserRequestBuilder(
        url = "https://cdn.example.com/video.mp4",
        referer = "https://watch.example.com/player/42",
      ).build()
    assertEquals("https://watch.example.com/player/42", req.header("Referer"))
    assertEquals("https://watch.example.com", req.header("Origin"))
    assertEquals("*/*", req.header("Accept"))
  }

  @Test
  fun `device trace cleaner erases cache webview external downloads and leftover app files`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    val cacheTrace = File(context.cacheDir, "http_cache_trace.tmp").apply { writeText("cached") }
    val codeCacheTrace = File(context.codeCacheDir, "shader_cache.bin").apply { writeText("shader") }
    val noBackupTrace = File(context.noBackupFilesDir, "nobackup_trace.dat").apply { writeText("trace") }
    val internalDownloadTrace =
      File(File(context.filesDir, "downloads").apply { mkdirs() }, "temp_video.mp4").apply {
        writeText("video")
      }
    val extDownloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    val extDownloadTrace = extDownloadsDir?.let { File(it, "staged.mp4").apply { writeText("ext") } }
    val appWebViewDir = File(context.dataDir, "app_webview").apply { mkdirs() }
    val webViewCookieFile = File(appWebViewDir, "Cookies").apply { writeText("cookie-data") }
    val sharedPrefsDir = File(context.dataDir, "shared_prefs").apply { mkdirs() }
    val prefsTrace = File(sharedPrefsDir, "ChromiumPrefs.xml").apply { writeText("<map/>") }
    val datastoreDir = File(context.filesDir, "datastore").apply { mkdirs() }
    val datastoreFile = File(datastoreDir, "volt_browser_prefs.preferences_pb").apply { writeText("ds") }

    DeviceTraceCleaner.wipeFileSystemTraces(context)

    assertFalse(cacheTrace.exists())
    assertFalse(codeCacheTrace.exists())
    assertFalse(noBackupTrace.exists())
    assertFalse(internalDownloadTrace.exists())
    if (extDownloadTrace != null) {
      assertFalse(extDownloadTrace.exists())
    }
    assertFalse(webViewCookieFile.exists())
    assertFalse(appWebViewDir.exists())
    assertFalse(prefsTrace.exists())
    assertTrue(datastoreFile.exists())
  }

  @Test
  fun `data saver blocks heavy images and webfonts while allowing html pages and svg icons`() {
    val (blockImg, imgBytes) = com.example.browser.VoltDataSaver.shouldBlockForDataSaver(Uri.parse("https://cdn.example.com/hero.webp?w=1200"))
    assertTrue(blockImg)
    assertTrue(imgBytes > 0L)

    val (blockFont, fontBytes) = com.example.browser.VoltDataSaver.shouldBlockForDataSaver(Uri.parse("https://fonts.example.com/inter.woff2"))
    assertTrue(blockFont)
    assertTrue(fontBytes > 0L)

    val (blockSvg, _) = com.example.browser.VoltDataSaver.shouldBlockForDataSaver(Uri.parse("https://cdn.example.com/icons/menu.svg"))
    assertFalse(blockSvg)

    val (blockVideo, _) = com.example.browser.VoltDataSaver.shouldBlockForDataSaver(Uri.parse("https://cdn.example.com/clip.mp4"))
    assertFalse(blockVideo)
  }

  @Test
  fun `all search engines and theme modes resolve clean urls and states`() {
    assertEquals(2, com.example.data.AppThemeMode.entries.size)
    assertTrue(com.example.data.AppThemeMode.entries.contains(com.example.data.AppThemeMode.CYBER_OBSIDIAN))
    assertTrue(com.example.data.AppThemeMode.entries.contains(com.example.data.AppThemeMode.ZEN_LIGHT))
    assertTrue(com.example.data.SearchEngine.entries.size >= 12)
    com.example.data.SearchEngine.entries.forEach { engine ->
      val queryUrl = VoltSpeedEngine.normalizeInputToUrl("privacy browser", engine)
      assertNotNull(queryUrl)
      assertTrue(queryUrl!!.startsWith("https://"))
    }
    assertEquals(
      "https://priv.au/search?q=privacy+browser",
      VoltSpeedEngine.normalizeInputToUrl("privacy browser", SearchEngine.SEARXNG),
    )
    assertEquals(
      "https://search.marginalia.nu/search?query=privacy+browser",
      VoltSpeedEngine.normalizeInputToUrl("privacy browser", SearchEngine.MARGINALIA),
    )
    assertEquals(
      "https://yacy.searchlab.eu/yacysearch.html?query=privacy+browser",
      VoltSpeedEngine.normalizeInputToUrl("privacy browser", SearchEngine.YACY),
    )
  }

  @Test
  fun `expanded doh resolvers custom proxy parser and startup defaults work accurately`() {
    assertEquals(9, com.example.vpn.DohProvider.entries.size)
    com.example.vpn.DohProvider.entries.forEach { provider ->
      assertTrue(provider.endpointUrl.startsWith("https://"))
    }

    assertTrue(PublicRelayManager.isPublicRoutableIp("198.51.100.42"))
    assertFalse(PublicRelayManager.isPublicRoutableIp("127.0.0.1"))
    assertFalse(PublicRelayManager.isPublicRoutableIp("192.168.1.1"))
    assertFalse(PublicRelayManager.isPublicRoutableIp("10.0.0.5"))

    val custom = PublicRelayManager.parseSingleProxy("socks5://203.0.113.25:1080", "SOCKS5")
    assertNotNull(custom)
    assertEquals("203.0.113.25", custom!!.ip)
    assertEquals(1080, custom.port)
    assertEquals("SOCKS5", custom.protocol)
    assertNull(custom.username)

    val authUrlProxy = PublicRelayManager.parseSingleProxy("http://alice:s3cr3t@198.51.100.88:3128", "HTTP")
    assertNotNull(authUrlProxy)
    assertEquals("198.51.100.88", authUrlProxy!!.ip)
    assertEquals(3128, authUrlProxy.port)
    assertEquals("alice", authUrlProxy.username)
    assertEquals("s3cr3t", authUrlProxy.password)
    assertTrue(authUrlProxy.hasAuthentication)

    val authColonProxy = PublicRelayManager.parseSingleProxy("198.51.100.99:1080:bob:pass42", "SOCKS5")
    assertNotNull(authColonProxy)
    assertEquals("bob", authColonProxy!!.username)
    assertEquals("pass42", authColonProxy.password)

    assertNull(PublicRelayManager.parseSingleProxy("127.0.0.1:8080", "HTTP"))

    val defaultSettings = com.example.data.BrowserSettings()
    assertFalse(defaultSettings.autoWireGuardOnLaunchEnabled)
    assertFalse(defaultSettings.autoProxyOnLaunchEnabled)
    assertFalse(defaultSettings.autoDnsVpnOnLaunchEnabled)

    val warpConf =
      com.example.vpn.WireGuardManager.buildWarpConfigString(
        privateKeyBase64 = "cPrivKeyBase64Placeholder0000000000000000000=",
        peerPublicKeyBase64 = com.example.vpn.WireGuardManager.DEFAULT_WARP_PUBLIC_KEY,
        clientIpv4 = "172.16.0.2",
        endpoint = "162.159.192.1:2408",
      )
    assertTrue(com.example.vpn.WireGuardManager.hasValidWireGuardSections(warpConf))
    assertEquals("162.159.192.1:2408", com.example.vpn.WireGuardManager.parseEndpointFromConf(warpConf))
    assertTrue(warpConf.contains("Address = 172.16.0.2/32"))
    assertTrue(warpConf.contains("MTU = 1280"))

    val relays =
      listOf(
        com.example.vpn.PublicRelayNode("198.51.100.1", 8080, "United States", "US", "HTTP", "elite", latencyMs = 90L, isReachable = true),
        com.example.vpn.PublicRelayNode("198.51.100.2", 8080, "India", "IN", "HTTP", "elite", latencyMs = 240L, isReachable = true),
        com.example.vpn.PublicRelayNode("198.51.100.3", 1080, "Germany", "DE", "SOCKS5", "elite", latencyMs = 180L, isReachable = true),
        com.example.vpn.PublicRelayNode("198.51.100.4", 8080, "Germany", "DE", "HTTP", "elite", latencyMs = null, isReachable = false),
      ).sortedWith(PublicRelayManager.relayRankingComparator)
    assertEquals("DE", relays[0].countryCode)
    assertEquals("IN", relays[1].countryCode)
    assertEquals("US", relays[2].countryCode)
    assertFalse(relays[3].isReachable == true)
  }

  @Test
  fun `media downloader supports all file extensions dispositions mime resolution and data urls`() {
    assertEquals(
      "Q4 Report Final.pdf",
      com.example.media.MediaDownloader.parseDispositionFileName(
        "attachment; filename*=UTF-8''Q4%20Report%20Final.pdf"
      ),
    )
    assertEquals(
      "bundle-v2.zip",
      com.example.media.MediaDownloader.parseDispositionFileName(
        """attachment; filename="bundle-v2.zip""""
      ),
    )

    val longName =
      "very_long_download_filename_that_exceeds_eighty_characters_for_sure_without_losing_ext_final.apk"
    val sanitized = com.example.media.MediaDownloader.sanitizeFileNamePreservingExtension(longName)
    assertTrue(sanitized.endsWith(".apk"))
    assertTrue(sanitized.length <= 80)

    assertEquals(
      "application/pdf",
      com.example.media.MediaDownloader.resolveMimeType("manual.pdf", "application/octet-stream"),
    )
    assertEquals(
      "application/zip",
      com.example.media.MediaDownloader.resolveMimeType("archive.zip", null),
    )
    assertEquals(
      "application/vnd.android.package-archive",
      com.example.media.MediaDownloader.resolveMimeType("installer.apk", "application/octet-stream"),
    )
    assertEquals(
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      com.example.media.MediaDownloader.resolveMimeType("notes.docx", "application/octet-stream"),
    )

    val guessedPdf =
      com.example.media.MediaDownloader.guessDownloadFileName(
        url = "https://example.com/export?id=99",
        contentDisposition = "attachment; filename=\"invoice_99.pdf\"",
        mimeType = "application/octet-stream",
        isAdaptiveStream = false,
      )
    assertEquals("invoice_99.pdf", guessedPdf)

    val guessedZipFromMime =
      com.example.media.MediaDownloader.guessDownloadFileName(
        url = "https://example.com/archive/build",
        contentDisposition = null,
        mimeType = "application/zip",
        isAdaptiveStream = false,
      )
    assertEquals("build.zip", guessedZipFromMime)

    val decodedBase64 =
      com.example.media.MediaDownloader.parseDataUrl("data:text/plain;base64,SGVsbG8gVm9sdA==")
    assertNotNull(decodedBase64)
    assertEquals("text/plain", decodedBase64!!.mimeType)
    assertEquals("Hello Volt", String(decodedBase64.bytes))

    val decodedCsv =
      com.example.media.MediaDownloader.parseDataUrl("data:text/csv;charset=utf-8,col1%2Ccol2%0A1%2C2")
    assertNotNull(decodedCsv)
    assertEquals("text/csv", decodedCsv!!.mimeType)
    assertEquals("col1,col2\n1,2", String(decodedCsv.bytes))
  }

  @Test
  fun `compact data usage counter formats bytes kilobytes megabytes and gigabytes cleanly`() {
    assertEquals("0B", com.example.ui.components.formatCompactDataCounter(0L))
    assertEquals("512B", com.example.ui.components.formatCompactDataCounter(512L))
    assertEquals("42K", com.example.ui.components.formatCompactDataCounter(42 * 1024L))
    assertEquals("1.5M", com.example.ui.components.formatCompactDataCounter(1_572_864L))
    assertEquals("250M", com.example.ui.components.formatCompactDataCounter(250 * 1024L * 1024L))
    assertEquals("1.5G", com.example.ui.components.formatCompactDataCounter(1_610_612_736L))
  }

  @Test
  fun `long press context resolver handles links images media links video elements and plain text`() {
    val anchor =
      com.example.browser.WebViewTabPool.resolveContextTarget(
        pageUrl = "https://example.com",
        hitType = android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE,
        hitExtra = "https://example.com/article",
        domTarget = com.example.data.WebContextMenuTarget(linkText = "Read Article"),
      )
    assertNotNull(anchor)
    assertEquals("https://example.com/article", anchor!!.linkUrl)
    assertEquals("Read Article", anchor.linkText)
    assertNull(anchor.mediaUrl)

    val mediaLink =
      com.example.browser.WebViewTabPool.resolveContextTarget(
        pageUrl = "https://example.com/watch",
        hitType = android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE,
        hitExtra = "https://cdn.example.com/clip.mp4",
        domTarget = com.example.data.WebContextMenuTarget(linkText = "Download Video"),
      )
    assertNotNull(mediaLink)
    assertEquals("https://cdn.example.com/clip.mp4", mediaLink!!.mediaUrl)
    assertEquals(MediaKind.VIDEO, mediaLink.mediaKind)

    val videoDom =
      com.example.browser.WebViewTabPool.resolveContextTarget(
        pageUrl = "https://example.com/player",
        hitType = android.webkit.WebView.HitTestResult.UNKNOWN_TYPE,
        hitExtra = null,
        domTarget =
          com.example.data.WebContextMenuTarget(
            mediaUrl = "https://cdn.example.com/live/master.m3u8",
            mediaTitle = "Live Stream",
            imageUrl = "https://cdn.example.com/poster.jpg",
          ),
      )
    assertNotNull(videoDom)
    assertEquals("https://cdn.example.com/live/master.m3u8", videoDom!!.mediaUrl)
    assertEquals(MediaKind.STREAM_HLS, videoDom.mediaKind)
    assertEquals("https://cdn.example.com/poster.jpg", videoDom.imageUrl)

    val editField =
      com.example.browser.WebViewTabPool.resolveContextTarget(
        pageUrl = "https://example.com",
        hitType = android.webkit.WebView.HitTestResult.EDIT_TEXT_TYPE,
        hitExtra = null,
        domTarget = com.example.data.WebContextMenuTarget(linkUrl = "https://example.com"),
      )
    assertNull(editField)

    val plainText =
      com.example.browser.WebViewTabPool.resolveContextTarget(
        pageUrl = "https://example.com",
        hitType = android.webkit.WebView.HitTestResult.UNKNOWN_TYPE,
        hitExtra = null,
        domTarget = null,
      )
    assertNull(plainText)
  }
}
