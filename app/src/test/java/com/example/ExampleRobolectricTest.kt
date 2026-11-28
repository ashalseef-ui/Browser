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
import com.example.browser.WebViewTabPool
import com.example.data.DetectedMediaItem
import com.example.data.MediaKind
import com.example.data.SearchEngine
import com.example.data.SecureConfigCipher
import com.example.media.MediaDownloader
import com.example.vpn.DnsPacketCodec
import com.example.vpn.PublicRelayManager
import java.io.File
import javax.crypto.KeyGenerator
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
  fun `turbo shield ad remover blocks ads trackers popunders returns surrogates and prevents false positives`() {
    val settings = com.example.data.BrowserSettings()
    assertEquals(com.example.data.AdBlockerType.ADGUARD, settings.adBlockerType)
    assertTrue(settings.turboShieldEnabled)

    // Core ad networks & trackers blocked
    assertTrue(TurboShield.shouldBlock(Uri.parse("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js")))
    assertTrue(TurboShield.shouldBlock(Uri.parse("https://www.google-analytics.com/analytics.js"), com.example.data.AdBlockerType.STANDARD))
    assertTrue(TurboShield.shouldBlock(Uri.parse("https://analytics.tiktok.com/i18n/pixel/events.js"), com.example.data.AdBlockerType.ADGUARD))
    assertTrue(TurboShield.shouldBlock(Uri.parse("https://consent.cookiebot.com/uc.js"), com.example.data.AdBlockerType.STRICT))

    // OFF mode disables blocking
    assertFalse(TurboShield.shouldBlock(Uri.parse("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"), com.example.data.AdBlockerType.OFF))
    assertNull(TurboShield.documentStartScript(com.example.data.AdBlockerType.OFF))
    assertNull(TurboShield.cosmeticFilterScript(com.example.data.AdBlockerType.OFF))

    // Safe allowlisted content & legitimate paths (no false positives on 'adobe', 'download', 'header', normal video)
    assertFalse(TurboShield.shouldBlock(Uri.parse("https://en.wikipedia.org/wiki/Android")))
    assertFalse(TurboShield.shouldBlock(Uri.parse("https://github.com/example/download/header_badge")))
    assertFalse(TurboShield.shouldBlock(Uri.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")))
    assertFalse(TurboShield.shouldBlock(Uri.parse("https://rr3---sn-abc.googlevideo.com/videoplayback?expire=123&itag=22")))

    // Targeted video ad endpoints ARE blocked without breaking playback
    assertTrue(TurboShield.shouldBlock(Uri.parse("https://www.youtube.com/youtubei/v1/player/ad_break?key=1")))
    assertTrue(TurboShield.shouldBlock(Uri.parse("https://rr3---sn-abc.googlevideo.com/videoplayback?expire=123&adformat=15_2_1")))

    // Popunder & redirect navigation blocking
    assertTrue(TurboShield.isNavigationAdBlocked(Uri.parse("https://serve.popads.net/c?id=1"), com.example.data.AdBlockerType.ADGUARD, isMainFrame = true))
    assertFalse(TurboShield.isNavigationAdBlocked(Uri.parse("https://en.wikipedia.org/wiki/Kotlin"), com.example.data.AdBlockerType.ADGUARD, isMainFrame = true))

    // MIME-aware surrogates
    val jsResp = TurboShield.blockedResponseForRequest(Uri.parse("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
    assertEquals("application/javascript", jsResp.mimeType)
    assertEquals("*", jsResp.responseHeaders["Access-Control-Allow-Origin"])
    val jsBody = jsResp.data.readBytes().decodeToString()
    assertTrue(jsBody.contains("adsbygoogle"))
    assertTrue(jsBody.contains("googletag"))

    val gifResp = TurboShield.blockedResponseForRequest(Uri.parse("https://pixel.facebook.com/tr?id=123"), mapOf("Accept" to "image/webp,*/*"))
    assertEquals("image/gif", gifResp.mimeType)

    val vastResp = TurboShield.blockedResponseForRequest(Uri.parse("https://ads.example.com/vast.xml"))
    assertEquals("application/xml", vastResp.mimeType)
    assertTrue(vastResp.data.readBytes().decodeToString().contains("<VAST"))

    // Document-start & cosmetic scripts generated properly
    val docScript = TurboShield.documentStartScript(com.example.data.AdBlockerType.STEALTH, setOf("trusted.example.com"))
    assertNotNull(docScript)
    assertTrue(docScript!!.contains("trusted.example.com"))
    assertTrue(docScript.contains("globalPrivacyControl"))
    assertNotNull(TurboShield.cosmeticFilterScript(com.example.data.AdBlockerType.ADGUARD))
    assertNotNull(TurboShield.cosmeticFilterScript(com.example.data.AdBlockerType.STRICT))

    // Per-host Ad Remover pause registry
    WebViewTabPool.clearAdBlockPausedHosts()
    assertFalse(WebViewTabPool.isHostAdBlockPaused("https://news.example.com/article"))
    WebViewTabPool.setHostAdBlockPaused("https://www.news.example.com/article", true)
    assertTrue(WebViewTabPool.isHostAdBlockPaused("https://news.example.com/sports"))
    WebViewTabPool.clearAdBlockPausedHosts()
    assertFalse(WebViewTabPool.isHostAdBlockPaused("https://news.example.com/article"))
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
  fun `media sniffer verifies downloadable video responses of any size or null content length and flags drm hls playlists`() {
    assertTrue(MediaSniffer.isDownloadableVideoResponse(200, "video/mp4", 15_000_000L))
    assertTrue(MediaSniffer.isDownloadableVideoResponse(206, "application/octet-stream", 1_048_577L))
    assertTrue(MediaSniffer.isDownloadableVideoResponse(200, "video/mp4", 1_048_576L))
    assertTrue(MediaSniffer.isDownloadableVideoResponse(200, "video/mp4", null))
    assertFalse(MediaSniffer.isDownloadableVideoResponse(403, "video/mp4", 5_200_000L))
    assertFalse(MediaSniffer.isDownloadableVideoResponse(200, "text/html; charset=utf-8", 5_200_000L))
    assertFalse(MediaSniffer.isDownloadableVideoResponse(200, "audio/mpeg", 4_000_000L))
    assertTrue(MediaSniffer.isDownloadableVideoResponse(200, "video/mp4", 512L))

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

  @Test
  fun `dns packet codec normalizes question keys extracts rcode and clamps min answer ttl`() {
    fun buildQuery(txIdHi: Int, txIdLo: Int, labels: List<String>, qType: Int = 1): ByteArray {
      val body = mutableListOf<Byte>(
        txIdHi.toByte(), txIdLo.toByte(),
        0x01, 0x00, // Standard query
        0x00, 0x01, // QDCOUNT = 1
        0x00, 0x00, // ANCOUNT = 0
        0x00, 0x00, // NSCOUNT = 0
        0x00, 0x00, // ARCOUNT = 0
      )
      labels.forEach { label ->
        body.add(label.length.toByte())
        label.toByteArray().forEach { body.add(it) }
      }
      body.add(0)
      body.add(((qType ushr 8) and 0xFF).toByte())
      body.add((qType and 0xFF).toByte())
      body.add(0x00)
      body.add(0x01) // QCLASS IN
      return body.toByteArray()
    }

    val qUpper = buildQuery(0x11, 0x22, listOf("Example", "COM"), qType = 1)
    val qLower = buildQuery(0x99, 0xAA, listOf("example", "com"), qType = 1)
    val qOther = buildQuery(0x11, 0x22, listOf("other", "com"), qType = 1)
    val keyUpper = DnsPacketCodec.extractNormalizedQuestionKey(qUpper)
    val keyLower = DnsPacketCodec.extractNormalizedQuestionKey(qLower)
    val keyOther = DnsPacketCodec.extractNormalizedQuestionKey(qOther)
    assertNotNull(keyUpper)
    assertEquals(keyUpper, keyLower)
    assertFalse(keyUpper == keyOther)

    // Synthetic DNS response with RCODE=0, compressed answer name 0xC0 0x0C, TTL = 2s (clamped to 5s)
    // and second answer TTL = 120s -> min = 5s; also test 120s and 7200s (clamped to 3600s)
    fun buildResponse(rcode: Int, ttls: List<Long>): ByteArray {
      val out = mutableListOf<Byte>(
        0x11, 0x22,
        0x81.toByte(), (0x80 or (rcode and 0x0F)).toByte(),
        0x00, 0x01, // QDCOUNT = 1
        0x00, ttls.size.toByte(), // ANCOUNT
        0x00, 0x00,
        0x00, 0x00,
      )
      // Question: example.com A IN (offset 12)
      out.addAll(listOf(7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte()))
      out.addAll(listOf(3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0))
      out.addAll(listOf(0x00, 0x01, 0x00, 0x01))
      ttls.forEach { ttl ->
        out.addAll(listOf(0xC0.toByte(), 0x0C)) // Compression pointer to offset 12
        out.addAll(listOf(0x00, 0x01, 0x00, 0x01)) // Type A, Class IN
        out.add(((ttl ushr 24) and 0xFF).toByte())
        out.add(((ttl ushr 16) and 0xFF).toByte())
        out.add(((ttl ushr 8) and 0xFF).toByte())
        out.add((ttl and 0xFF).toByte())
        out.addAll(listOf(0x00, 0x04, 93, 184.toByte(), 216.toByte(), 34))
      }
      return out.toByteArray()
    }

    val okShortResp = buildResponse(rcode = 0, ttls = listOf(120L, 2L))
    assertEquals(0, DnsPacketCodec.extractRcode(okShortResp))
    assertEquals(5L, DnsPacketCodec.extractMinAnswerTtlSeconds(okShortResp))

    val okLongResp = buildResponse(rcode = 0, ttls = listOf(7200L, 600L))
    assertEquals(600L, DnsPacketCodec.extractMinAnswerTtlSeconds(okLongResp))

    val clampedMaxResp = buildResponse(rcode = 0, ttls = listOf(86400L))
    assertEquals(3600L, DnsPacketCodec.extractMinAnswerTtlSeconds(clampedMaxResp))

    val nxDomainResp = buildResponse(rcode = 3, ttls = emptyList())
    assertEquals(3, DnsPacketCodec.extractRcode(nxDomainResp))
    assertNull(DnsPacketCodec.extractMinAnswerTtlSeconds(nxDomainResp))
  }

  @Test
  fun `webview tab pool only provides proxy credentials for exact matching proxy host`() {
    assertTrue(WebViewTabPool.shouldProvideProxyCredentials("203.0.113.10", "203.0.113.10"))
    assertTrue(WebViewTabPool.shouldProvideProxyCredentials("Proxy.Example.COM", "proxy.example.com"))
    assertFalse(WebViewTabPool.shouldProvideProxyCredentials("evil.example.com", "203.0.113.10"))
    assertFalse(WebViewTabPool.shouldProvideProxyCredentials("203.0.113.10", null))
    assertFalse(WebViewTabPool.shouldProvideProxyCredentials("", "203.0.113.10"))
  }

  @Test
  fun `secure config cipher encrypts decrypts handles empty and passes legacy plaintext`() {
    val keyGen = KeyGenerator.getInstance("AES")
    keyGen.init(256)
    val secretKey = keyGen.generateKey()

    val wgConf = "[Interface]\nPrivateKey = abc123==\n[Peer]\nEndpoint = 162.159.192.1:2408"
    val encrypted = SecureConfigCipher.encryptWithKey(wgConf, secretKey)
    assertTrue(encrypted.startsWith("enc:v1:"))
    assertFalse(encrypted.contains("abc123=="))
    assertEquals(wgConf, SecureConfigCipher.decryptWithKey(encrypted, secretKey))

    assertEquals(wgConf, SecureConfigCipher.decryptWithKey(wgConf, secretKey))
    assertEquals("", SecureConfigCipher.encrypt(""))
    assertEquals("", SecureConfigCipher.decrypt(""))
  }

  @Test
  fun `media downloader rejects oversized data urls`() {
    val overDecodedLimit = "data:application/octet-stream;base64," + "A".repeat(34_952_540)
    assertNull(MediaDownloader.parseDataUrl(overDecodedLimit))

    val overCharLimit = "data:text/plain;base64," + "A".repeat(36_000_005)
    assertNull(MediaDownloader.parseDataUrl(overCharLimit))
  }

  @Test
  fun `volt speed engine validates prewarm hosts strictly`() {
    assertTrue(VoltSpeedEngine.isValidPrewarmHost("wikipedia.org"))
    assertTrue(VoltSpeedEngine.isValidPrewarmHost("localhost"))
    assertFalse(VoltSpeedEngine.isValidPrewarmHost(""))
    assertFalse(VoltSpeedEngine.isValidPrewarmHost("invalid host.com"))
    assertFalse(VoltSpeedEngine.isValidPrewarmHost(".leadingdot.com"))
    assertFalse(VoltSpeedEngine.isValidPrewarmHost("trailingdot.com."))
    assertFalse(VoltSpeedEngine.isValidPrewarmHost("singlelabel"))
    assertFalse(VoltSpeedEngine.isValidPrewarmHost("user@host.com"))
  }

  @Test
  fun `search engine cycles seamlessly and normalizes queries and direct urls`() {
    val entries = SearchEngine.entries
    assertEquals(entries[1], entries.first().cycled(1))
    assertEquals(entries.last(), entries.first().cycled(-1))
    assertEquals(entries.first(), entries.last().cycled(1))

    assertEquals(
      "https://en.wikipedia.org/wiki/Special:Search?search=android+compose",
      VoltSpeedEngine.normalizeInputToUrl("android compose", SearchEngine.WIKIPEDIA),
    )
    assertEquals(
      "https://priv.au/search?q=privacy",
      VoltSpeedEngine.normalizeInputToUrl("privacy", SearchEngine.SEARXNG),
    )
    assertEquals(
      "https://wikipedia.org",
      VoltSpeedEngine.normalizeInputToUrl("wikipedia.org", SearchEngine.DUCKDUCKGO),
    )
    assertEquals(
      "https://search.brave.com/search?q=compose+swipe",
      VoltSpeedEngine.normalizeInputToUrl("compose swipe", SearchEngine.BRAVE),
    )
    assertNull(VoltSpeedEngine.normalizeInputToUrl("compose swipe", SearchEngine.BRAVE, forceUrlOnly = true))
  }

  @Test
  fun `site media compat bypass host registry and playback header context work accurately`() {
    WebViewTabPool.clearMediaCompatBypassHosts()
    assertEquals("stream.example.com", WebViewTabPool.normalizeHostKey("https://www.Stream.Example.com/watch?v=1"))
    assertFalse(WebViewTabPool.isHostMediaCompatBypassed("https://stream.example.com/video/1"))

    WebViewTabPool.setHostMediaCompatBypass("https://www.stream.example.com/watch?v=1", true)
    assertTrue(WebViewTabPool.isHostMediaCompatBypassed("https://stream.example.com/embed/42"))

    WebViewTabPool.setHostMediaCompatBypass("stream.example.com", false)
    assertFalse(WebViewTabPool.isHostMediaCompatBypassed("https://stream.example.com/embed/42"))

    WebViewTabPool.setHostMediaCompatBypass("media.test.org", true)
    WebViewTabPool.clearMediaCompatBypassHosts()
    assertFalse(WebViewTabPool.isHostMediaCompatBypassed("https://media.test.org/live"))

    MediaSniffer.clearPlaybackContexts()
    val mediaUrl = "https://cdn.example.com/hls/master.m3u8?token=xyz"
    val refererUrl = "https://player.example.com/embed/99"
    MediaSniffer.registerPlaybackContext(mediaUrl, refererUrl, "VoltUA/1.0")
    val ctx = MediaSniffer.getPlaybackContext(mediaUrl)
    assertNotNull(ctx)
    assertEquals(refererUrl, ctx!!.referer)
    assertEquals("https://player.example.com", ctx.origin)
    assertEquals("VoltUA/1.0", ctx.userAgent)

    MediaSniffer.clearPlaybackContexts()
    assertNull(MediaSniffer.getPlaybackContext(mediaUrl))
  }

  @Test
  fun `segmented downloader plans contiguous chunks and reads checkpoint metadata`() {
    val segments = com.example.media.SegmentedDownloader.planSegments(totalBytes = 10_000_005L, maxSegments = 8)
    assertEquals(8, segments.size)
    assertEquals(0L, segments.first().startByte)
    assertEquals(10_000_004L, segments.last().endByte)
    for (i in 1 until segments.size) {
      assertEquals(segments[i - 1].endByte + 1L, segments[i].startByte)
    }
    assertEquals(10_000_005L, segments.sumOf { it.totalLength })

    val probeAcceptRange =
      com.example.media.SegmentedDownloader.ProbeResult(
        totalBytes = 5_000_000L,
        acceptRanges = true,
        etag = "\"abc\"",
        lastModified = null,
        contentDisposition = null,
        contentType = "video/mp4",
        finalUrl = "https://cdn.example.com/video.mp4",
      )
    val probeSmall = probeAcceptRange.copy(totalBytes = 256_000L)
    val probeNoRange = probeAcceptRange.copy(acceptRanges = false)
    val downloader = com.example.media.SegmentedDownloader(okhttp3.OkHttpClient())
    assertTrue(downloader.shouldUseSegmentedDownload(probeAcceptRange))
    assertFalse(downloader.shouldUseSegmentedDownload(probeSmall))
    assertFalse(downloader.shouldUseSegmentedDownload(probeNoRange))

    val context = ApplicationProvider.getApplicationContext<Context>()
    val targetFile = File(context.cacheDir, "seg_test_video.mp4")
    val metaFile = com.example.media.SegmentedDownloader.segMetaFileFor(targetFile)
    try {
      metaFile.writeText(
        """{"totalBytes":5000000,"segments":[{"index":0,"start":0,"end":2499999,"downloaded":1024},{"index":1,"start":2500000,"end":4999999,"downloaded":2048}]}"""
      )
      assertEquals(3072L, com.example.media.SegmentedDownloader.readSavedProgressBytes(targetFile))
      com.example.media.SegmentedDownloader.cleanupMetaFile(targetFile)
      assertFalse(metaFile.exists())
    } finally {
      metaFile.delete()
      targetFile.delete()
    }
  }
}
