package com.example.browser

import android.net.Uri
import android.webkit.WebResourceResponse
import com.example.data.AdBlockerType
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONArray

/**
 * Multi-layered Ad Remover & Privacy Shield engine for Android WebView:
 *  1. Fast O(labels) domain/subdomain blocking across EasyList, EasyPrivacy, AdGuard Mobile,
 *     and popunder/redirect threat lists with false-positive host safeguards.
 *  2. Token-safe URL path/query rules including YouTube/HTML5 video ad tags & VAST/VPAID endpoints.
 *  3. MIME-aware surrogate responses (JS no-op stubs, 1x1 GIF, empty VAST XML, empty JSON/CSS/HTML)
 *     with CORS headers to prevent anti-adblock breakage and stalled video players.
 *  4. Pre-execution Document-Start JavaScript scriptlets (fetch/XHR/beacon interception, window.open
 *     popunder guard, video ad placement stripping + auto-skip, anti-adblock bypass, cosmetic CSS,
 *     and Stealth DNT/GPC/tracking-param sanitization).
 */
object TurboShield {

  const val ESTIMATED_BYTES_PER_BLOCKED_REQUEST = 48_000L

  private val TRANSPARENT_GIF_BYTES = byteArrayOf(
    0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
    0x80.toByte(), 0x00, 0x00, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
    0x00, 0x00, 0x00, 0x21, 0xf9.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00,
    0x00, 0x2c, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
    0x02, 0x02, 0x44, 0x01, 0x00, 0x3b,
  )

  private val CORS_HEADERS =
    mapOf(
      "Cache-Control" to "no-store",
      "Access-Control-Allow-Origin" to "*",
      "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
    )

  private val JS_SURROGATE_BYTES =
    """
    (function(){
      try {
        window.canRunAds = true;
        window.isAdBlockActive = false;
        window.adblock = false;
        window.adsBlocked = false;
        window.adsbygoogle = window.adsbygoogle || [];
        if (typeof window.adsbygoogle.push !== 'function') {
          window.adsbygoogle.push = function(){ return 0; };
        }
        window.googletag = window.googletag || {
          cmd: [],
          apiReady: true,
          pubadsReady: true,
          defineSlot: function(){ return { addService: function(){ return this; }, setTargeting: function(){ return this; }, defineSizeMapping: function(){ return this; } }; },
          defineOutOfPageSlot: function(){ return { addService: function(){ return this; } }; },
          pubads: function(){ return { enableSingleRequest: function(){}, collapseEmptyDivs: function(){}, setTargeting: function(){ return this; }, addEventListener: function(){}, refresh: function(){} }; },
          enableServices: function(){},
          display: function(){},
          destroySlots: function(){}
        };
        window.pbjs = window.pbjs || { que: [], requestBids: function(c){ if (c && c.bidsBackHandler) try { c.bidsBackHandler({}); } catch(_){} }, addAdUnits: function(){}, setConfig: function(){}, getBidResponses: function(){ return {}; } };
        if (!window.google) window.google = {};
        window.google.ima = window.google.ima || {
          AdDisplayContainer: function(){ this.initialize = function(){}; },
          AdsLoader: function(){ this.addEventListener = function(){}; this.requestAds = function(){}; this.contentComplete = function(){}; },
          AdsRequest: function(){},
          AdsManagerLoadedEvent: { Type: { ADS_MANAGER_LOADED: 'adsManagerLoaded' } },
          AdErrorEvent: { Type: { AD_ERROR: 'adError' } },
          AdEvent: { Type: { CONTENT_PAUSE_REQUESTED: 'contentPauseRequested', CONTENT_RESUME_REQUESTED: 'contentResumeRequested', ALL_ADS_COMPLETED: 'allAdsCompleted' } }
        };
        if (typeof window.gtag !== 'function') window.gtag = function(){};
        if (typeof window.ga !== 'function') window.ga = function(){};
        if (typeof window.fbq !== 'function') window.fbq = function(){};
        window.uetq = window.uetq || [];
      } catch(_) {}
    })();
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)

  private val EMPTY_JSON_BYTES = "{}".toByteArray(StandardCharsets.UTF_8)
  private val EMPTY_VAST_BYTES = "<VAST version=\"3.0\"></VAST>".toByteArray(StandardCharsets.UTF_8)
  private val EMPTY_CSS_BYTES = "/* blocked by volt shield */".toByteArray(StandardCharsets.UTF_8)
  private val EMPTY_HTML_BYTES = "<!doctype html><html><head></head><body></body></html>".toByteArray(StandardCharsets.UTF_8)

  /** Hosts where domain-level blocking must NEVER blanket-trigger; only explicit ad path/query rules apply. */
  private val SAFE_ALLOWLIST_HOSTS =
    setOf(
      "youtube.com",
      "m.youtube.com",
      "music.youtube.com",
      "youtube-nocookie.com",
      "youtu.be",
      "ytimg.com",
      "googlevideo.com",
      "googleusercontent.com",
      "gstatic.com",
      "google.com",
      "googleapis.com",
      "github.com",
      "githubusercontent.com",
      "wikipedia.org",
      "wikimedia.org",
      "cloudflare.com",
      "cdnjs.cloudflare.com",
      "jsdelivr.net",
      "unpkg.com",
      "vimeo.com",
      "vimeocdn.com",
      "twitch.tv",
      "ttvnw.net",
      "cloudfront.net",
      "akamaihd.net",
      "fastly.net",
      "reddit.com",
      "redd.it",
      "redditmedia.com",
      "twitter.com",
      "x.com",
      "twimg.com",
      "instagram.com",
      "facebook.com",
      "fbcdn.net",
      "tiktok.com",
    )

  /** Core ad networks and tracking pixels blocked in STANDARD and above. */
  private val STANDARD_DOMAINS =
    setOf(
      "doubleclick.net",
      "googlesyndication.com",
      "googleadservices.com",
      "adservice.google.com",
      "adservice.google.co.in",
      "adservice.google.co.uk",
      "adservice.google.de",
      "pagead2.googlesyndication.com",
      "tpc.googlesyndication.com",
      "ads.youtube.com",
      "google-analytics.com",
      "ssl.google-analytics.com",
      "googletagmanager.com",
      "googletagservices.com",
      "imasdk.googleapis.com",
      "admob.com",
      "adclick.g.doubleclick.net",
      "googleads.g.doubleclick.net",
      "stats.g.doubleclick.net",
      "pubads.g.doubleclick.net",
      "securepubads.g.doubleclick.net",
      "cm.g.doubleclick.net",
      "bid.g.doubleclick.net",
      "facebook.net",
      "connect.facebook.net",
      "pixel.facebook.com",
      "an.facebook.com",
      "amazon-adsystem.com",
      "aax.amazon-adsystem.com",
      "aax-us-east.amazon-adsystem.com",
      "c.amazon-adsystem.com",
      "s.amazon-adsystem.com",
      "fls-na.amazon-adsystem.com",
      "bat.bing.com",
      "ads.msn.com",
      "c.msn.com",
      "adnxs.com",
      "ib.adnxs.com",
      "acdn.adnxs.com",
      "criteo.com",
      "criteo.net",
      "bidder.criteo.com",
      "static.criteo.net",
      "taboola.com",
      "cdn.taboola.com",
      "trc.taboola.com",
      "outbrain.com",
      "widgets.outbrain.com",
      "log.outbrainimg.com",
      "pubmatic.com",
      "ads.pubmatic.com",
      "hbopenbid.pubmatic.com",
      "rubiconproject.com",
      "fastlane.rubiconproject.com",
      "pixel.rubiconproject.com",
      "openx.net",
      "rtb.openx.net",
      "us-u.openx.net",
      "casalemedia.com",
      "as-sec.casalemedia.com",
      "scorecardresearch.com",
      "sb.scorecardresearch.com",
      "quantserve.com",
      "pixel.quantserve.com",
      "adroll.com",
      "s.adroll.com",
      "media.net",
      "contextual.media.net",
      "static.media.net",
      "revcontent.com",
      "trends.revcontent.com",
      "mgid.com",
      "jsc.mgid.com",
      "servicer.mgid.com",
      "unityads.unity3d.com",
      "applovin.com",
      "d.applovin.com",
      "adcolony.com",
      "vungle.com",
      "ironsrc.com",
      "inmobi.com",
      "chartboost.com",
      "mopub.com",
      "ads.yahoo.com",
      "advertising.com",
      "adtechus.com",
      "serving-sys.com",
      "flashtalking.com",
      "adform.net",
      "track.adform.net",
      "innovid.com",
      "fwmrm.net",
      "2mdn.net",
      "moatads.com",
      "doubleverify.com",
      "adsafeprotected.com",
      "zedo.com",
      "yieldmo.com",
      "sharethrough.com",
      "3lift.com",
      "tlx.3lift.com",
      "lijit.com",
      "sovrn.com",
      "bidswitch.net",
      "gumgum.com",
      "kargo.com",
      "nativo.com",
      "zemanta.com",
      "teads.tv",
      "a.teads.tv",
      "smartadserver.com",
      "ww251.smartadserver.com",
      "spotxchange.com",
      "springserve.com",
    )

  /** Additional mobile trackers, attribution, session-replay, and telemetry domains in ADGUARD+. */
  private val ADGUARD_DOMAINS =
    setOf(
      "analytics.tiktok.com",
      "ads.tiktok.com",
      "business-api.tiktok.com",
      "tr.snapchat.com",
      "sc-static.net",
      "ct.pinterest.com",
      "ads.pinterest.com",
      "ads-twitter.com",
      "analytics.twitter.com",
      "static.ads-twitter.com",
      "ads.linkedin.com",
      "px.ads.linkedin.com",
      "snap.licdn.com",
      "alb.reddit.com",
      "events.redditmedia.com",
      "pixel.redditmedia.com",
      "app.link",
      "bnc.lt",
      "branch.io",
      "appsflyer.com",
      "app.appsflyer.com",
      "adjust.com",
      "app.adjust.com",
      "kochava.com",
      "singular.net",
      "tenjin.com",
      "hotjar.com",
      "static.hotjar.com",
      "script.hotjar.com",
      "fullstory.com",
      "rs.fullstory.com",
      "mouseflow.com",
      "luckyorange.com",
      "crazyegg.com",
      "clarity.ms",
      "www.clarity.ms",
      "mixpanel.com",
      "api-js.mixpanel.com",
      "amplitude.com",
      "api.amplitude.com",
      "api2.amplitude.com",
      "segment.com",
      "cdn.segment.com",
      "api.segment.io",
      "heapanalytics.com",
      "cdn.heapanalytics.com",
      "logrocket.com",
      "cdn.lr-ingest.io",
      "bugsnag.com",
      "notify.bugsnag.com",
      "sentry.io",
      "browser.sentry-cdn.com",
      "nr-data.net",
      "js-agent.newrelic.com",
      "bam.nr-data.net",
      "chartbeat.com",
      "static.chartbeat.com",
      "ping.chartbeat.net",
      "parsely.com",
      "cdn.parsely.com",
      "omtrdc.net",
      "2o7.net",
      "demdex.net",
      "everesttech.net",
      "krxd.net",
      "bluekai.com",
      "tags.bluekai.com",
      "crwdcntrl.net",
      "rlcdn.com",
      "idsync.rlcdn.com",
      "tapad.com",
      "exelator.com",
      "agkn.com",
      "mathtag.com",
      "rfihub.com",
      "turn.com",
      "contextweb.com",
      "simpli.fi",
      "eyeota.net",
      "adsrvr.org",
      "match.adsrvr.org",
      "js.adsrvr.org",
      "liadm.com",
      "idx.liadm.com",
      "id5-sync.com",
      "c.bing.com",
      "browser.events.data.msn.com",
      "events.data.microsoft.com",
      "vortex.data.microsoft.com",
      "app-measurement.com",
      "firebaseinstallations.googleapis.com",
      "braze.com",
      "sdk.iad-01.braze.com",
      "clevertap.com",
      "onesignal.com",
      "cdn.onesignal.com",
      "intercom.io",
      "widget.intercom.io",
      "drift.com",
      "js.driftt.com",
      "permutive.com",
      "cdn.permutive.com",
      "connatix.com",
      "cd.connatix.com",
      "primis.tech",
      "live.primis.tech",
      "aniview.com",
      "player.aniview.com",
      "lkqd.net",
      "tidaltv.com",
      "adswizz.com",
      "vidoomy.com",
      "vlyby.com",
      "seedtag.com",
      "t.seedtag.com",
      "playbuzz.com",
      "skimresources.com",
      "go.skimresources.com",
      "viglink.com",
      "narrativ.com",
      "narrativ.io",
      "narrativ.com",
      "narrativ.io",
      "narrativ.net",
    )

  /** Notorious popunder, redirect-hijack, push-spam, and adult/streaming malvertising domains. */
  private val POPUNDER_REDIRECT_DOMAINS =
    setOf(
      "popads.net",
      "serve.popads.net",
      "popcash.net",
      "cdn.popcash.net",
      "propellerads.com",
      "propellerclick.com",
      "onclkds.com",
      "monetag.com",
      "alwingulla.com",
      "glomex.com",
      "hilltopads.net",
      "exoclick.com",
      "a.exoclick.com",
      "syndication.exoclick.com",
      "realsrv.com",
      "tsyndicate.com",
      "magsrv.com",
      "pemsrv.com",
      "a.magsrv.com",
      "a.realsrv.com",
      "trafficjunky.net",
      "trafficjunky.com",
      "ads.trafficjunky.net",
      "juicyads.com",
      "adserver.juicyads.com",
      "adsterra.com",
      "highperformanceformat.com",
      "topcreativeformat.com",
      "profitabledisplaynetwork.com",
      "effectivegatecpm.com",
      "adcash.com",
      "acint.net",
      "clickadu.com",
      "galaksion.com",
      "evadav.com",
      "richpush.co",
      "pushground.com",
      "zeropark.com",
      "admaven.com",
      "dolohen.com",
      "ouo.io",
      "adf.ly",
      "shorte.st",
      "linkvertise.com",
      "bidgear.com",
      "a-ads.com",
      "ad.a-ads.com",
      "coinzilla.com",
      "bitmedia.io",
      "cointraffic.io",
      "betterbanners.org",
      "adsco.re",
      "trafficstars.com",
      "ero-advertising.com",
      "plugrush.com",
      "clickaine.com",
      "rollerads.com",
      "push.house",
    )

  /** Cookie/GDPR consent nag scripts blocked in STRICT and STEALTH modes. */
  private val CONSENT_NAG_DOMAINS =
    setOf(
      "cookiebot.com",
      "consent.cookiebot.com",
      "consentcdn.cookiebot.com",
      "cookielaw.org",
      "cdn.cookielaw.org",
      "optanon.blob.core.windows.net",
      "didomi.io",
      "sdk.privacy-center.org",
      "trustarc.com",
      "consent.trustarc.com",
      "consensu.org",
      "usercentrics.eu",
      "app.usercentrics.eu",
      "termly.io",
      "app.termly.io",
      "iubenda.com",
      "cdn.iubenda.com",
      "quantcast.mgr.consensu.org",
      "cmp.quantcast.com",
      "sourcepoint.mgr.consensu.org",
      "cdn.privacy-mgmt.com",
    )

  private val YOUTUBE_AD_PATH_REGEX =
    Regex(
      """/(?:pagead|ptracking|get_midroll_info|api/stats/ads|youtubei/v1/player/ad_break)(?:[/?&#]|$)""",
      RegexOption.IGNORE_CASE,
    )

  private val URL_AD_PATTERN_REGEX =
    Regex(
      """(?:^|[/?&._-])(?:pagead|gampad|pubads|adsense|adserver|adservice|adsystem|prebid|openrtb|vast(?:tag|xml)?|vpaid|ima3|adsbygoogle|gpt\.js|dfp_|popunder|interstitial|ad_break|sponsored_ads|nativeads|fbevents)(?:[/?&.=_-]|$)""",
      RegexOption.IGNORE_CASE,
    )

  private val STRICT_URL_PATTERN_REGEX =
    Regex(
      """(?:^|[/?&._-])(?:aclk|clicktrack|adclick|adunit|adframe|adbanner|banner_ad|bannerad|popclick|afu\.php|trackclick|pixel\.(?:gif|png|js)|beacon(?:\.js|/)|telemetry(?:\.js|/))(?:[/?&.=_-]|$)""",
      RegexOption.IGNORE_CASE,
    )

  private val SCRIPT_URL_HINT_REGEX =
    Regex(
      """(?:adsbygoogle|gpt\.js|pubads|ima3|prebid|fbevents|gtag/js|analytics\.js|gtm\.js|bat\.js|hotjar|clarity|outbrain|taboola|criteo|mgid)""",
      RegexOption.IGNORE_CASE,
    )

  private val JSON_URL_HINT_REGEX =
    Regex(
      """(?:ad_break|get_midroll|player/ad|openrtb|prebid|bidder|hbopenbid|/ads\?|\.json(?:$|[?#]))""",
      RegexOption.IGNORE_CASE,
    )

  fun shouldBlock(uri: Uri?): Boolean = shouldBlock(uri, AdBlockerType.ADGUARD)

  fun shouldBlock(uri: Uri?, blockerType: AdBlockerType): Boolean {
    if (blockerType == AdBlockerType.OFF || uri == null) return false
    val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
    if (scheme != "http" && scheme != "https") return false

    val host = normalizeHost(uri.host)
    if (host.isEmpty()) return false
    val fullUrl = uri.toString()

    // 1. Explicit ad/tracker domains (checked before parent allowlists so dedicated ad subdomains
    //    like analytics.tiktok.com, pixel.facebook.com, ads.youtube.com, adservice.google.com match)
    if (isHostInSet(host, STANDARD_DOMAINS)) return true
    if (blockerType != AdBlockerType.STANDARD) {
      if (isHostInSet(host, ADGUARD_DOMAINS) || isHostInSet(host, POPUNDER_REDIRECT_DOMAINS)) {
        return true
      }
    }
    if (blockerType == AdBlockerType.STRICT || blockerType == AdBlockerType.STEALTH) {
      if (isHostInSet(host, CONSENT_NAG_DOMAINS)) return true
    }

    // 2. Targeted video-ad rules on allowlisted media & platform hosts (never blanket-block playback)
    if (isHostInSet(host, SAFE_ALLOWLIST_HOSTS)) {
      if (host == "googlevideo.com" || host.endsWith(".googlevideo.com")) {
        return hasGoogleVideoAdParams(fullUrl)
      }
      if (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtube-nocookie.com") {
        return YOUTUBE_AD_PATH_REGEX.containsMatchIn(fullUrl)
      }
      return false
    }

    // 3. Token-safe URL path & query rules
    if (URL_AD_PATTERN_REGEX.containsMatchIn(fullUrl)) return true
    if ((blockerType == AdBlockerType.STRICT || blockerType == AdBlockerType.STEALTH) &&
      STRICT_URL_PATTERN_REGEX.containsMatchIn(fullUrl)
    ) {
      return true
    }

    return false
  }

  /**
   * Checks whether a navigation/redirect/popup target should be blocked in `shouldOverrideUrlLoading`
   * or `onCreateWindow`. Main-frame blocking focuses on known popunder/malvertising redirect traps;
   * sub-frame/popup blocking applies the full active filter set.
   */
  fun isNavigationAdBlocked(
    uri: Uri?,
    blockerType: AdBlockerType,
    isMainFrame: Boolean,
  ): Boolean {
    if (blockerType == AdBlockerType.OFF || uri == null) return false
    val host = normalizeHost(uri.host)
    if (host.isEmpty()) return false
    if (isHostInSet(host, POPUNDER_REDIRECT_DOMAINS)) return true
    if (!isMainFrame) {
      return shouldBlock(uri, blockerType)
    }
    return false
  }

  /**
   * Builds a MIME-aware surrogate/clean response for blocked resource requests so websites and
   * HTML5 video players don't stall, throw CORS errors, or trigger anti-adblock overlays.
   */
  fun blockedResponseForRequest(
    uri: Uri?,
    headers: Map<String, String> = emptyMap(),
  ): WebResourceResponse {
    val rawUrl = uri?.toString().orEmpty()
    val cleanPath = rawUrl.substringBefore('?').substringBefore('#').lowercase(Locale.US)
    val ext = cleanPath.substringAfterLast('.', "")
    val accept =
      headers.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value.orEmpty()
        .lowercase(Locale.US)
    val secFetchDest =
      headers.entries.firstOrNull { it.key.equals("Sec-Fetch-Dest", ignoreCase = true) }?.value
        .orEmpty()
        .lowercase(Locale.US)

    return when {
      ext == "js" ||
        ext == "mjs" ||
        secFetchDest == "script" ||
        accept.contains("javascript") ||
        SCRIPT_URL_HINT_REGEX.containsMatchIn(rawUrl) ->
        WebResourceResponse(
          "application/javascript",
          "utf-8",
          200,
          "OK",
          CORS_HEADERS,
          ByteArrayInputStream(JS_SURROGATE_BYTES),
        )

      ext in IMAGE_EXTENSIONS || secFetchDest == "image" || accept.contains("image/") ->
        WebResourceResponse(
          "image/gif",
          "utf-8",
          200,
          "OK",
          CORS_HEADERS,
          ByteArrayInputStream(TRANSPARENT_GIF_BYTES),
        )

      ext == "css" || secFetchDest == "style" || accept.contains("text/css") ->
        WebResourceResponse(
          "text/css",
          "utf-8",
          200,
          "OK",
          CORS_HEADERS,
          ByteArrayInputStream(EMPTY_CSS_BYTES),
        )

      rawUrl.contains("vast", ignoreCase = true) ||
        rawUrl.contains("vpaid", ignoreCase = true) ||
        accept.contains("application/xml") ||
        accept.contains("text/xml") ->
        WebResourceResponse(
          "application/xml",
          "utf-8",
          200,
          "OK",
          CORS_HEADERS,
          ByteArrayInputStream(EMPTY_VAST_BYTES),
        )

      ext == "json" ||
        accept.contains("application/json") ||
        JSON_URL_HINT_REGEX.containsMatchIn(rawUrl) ->
        WebResourceResponse(
          "application/json",
          "utf-8",
          200,
          "OK",
          CORS_HEADERS,
          ByteArrayInputStream(EMPTY_JSON_BYTES),
        )

      secFetchDest == "iframe" || accept.contains("text/html") ->
        WebResourceResponse(
          "text/html",
          "utf-8",
          200,
          "OK",
          CORS_HEADERS,
          ByteArrayInputStream(EMPTY_HTML_BYTES),
        )

      else -> emptyBlockedResponse()
    }
  }

  fun emptyBlockedResponse(): WebResourceResponse =
    WebResourceResponse(
      "text/plain",
      "utf-8",
      200,
      "OK",
      CORS_HEADERS,
      ByteArrayInputStream(ByteArray(0)),
    )

  /**
   * Pre-execution scriptlet injected via `WebViewCompat.addDocumentStartJavaScript` so anti-adblock
   * detectors, popunders, `fetch`/`XHR` beacons, and YouTube/HTML5 video ads are neutralized
   * before page scripts parse.
   */
  fun documentStartScript(
    blockerType: AdBlockerType,
    pausedHosts: Set<String> = emptySet(),
  ): String? {
    if (blockerType == AdBlockerType.OFF) return null

    val pausedJson = JSONArray(pausedHosts.map { normalizeHost(it) }.filter { it.isNotEmpty() }.distinct()).toString()
    val isStrictOrHigher = blockerType == AdBlockerType.STRICT || blockerType == AdBlockerType.STEALTH
    val isStealth = blockerType == AdBlockerType.STEALTH
    val isAdGuardOrHigher = blockerType != AdBlockerType.STANDARD
    val cosmeticCss = buildCosmeticCss(includeConsentRules = isStrictOrHigher)

    return """
      (function() {
        try {
          if (window.__voltShieldInit) return;
          var pausedHosts = $pausedJson;
          var host = (window.location && window.location.hostname || '').toLowerCase().replace(/^www\./, '');
          for (var i = 0; i < pausedHosts.length; i++) {
            var ph = pausedHosts[i];
            if (host === ph || (host.length > ph.length && host.endsWith('.' + ph))) {
              return;
            }
          }
          window.__voltShieldInit = true;

          // 1. Anti-Adblock & Surrogate Globals
          try {
            Object.defineProperty(window, 'canRunAds', { get: function(){ return true; }, set: function(){}, configurable: true });
            Object.defineProperty(window, 'isAdBlockActive', { get: function(){ return false; }, set: function(){}, configurable: true });
            Object.defineProperty(window, 'adblock', { get: function(){ return false; }, set: function(){}, configurable: true });
            Object.defineProperty(window, 'adsBlocked', { get: function(){ return false; }, set: function(){}, configurable: true });
          } catch(_) {
            window.canRunAds = true;
            window.isAdBlockActive = false;
          }
          if (!window.adsbygoogle) {
            window.adsbygoogle = [];
            window.adsbygoogle.push = function(){ return 0; };
          }
          if (!window.BlockAdBlock) {
            var noopBab = function(){
              this.onDetected = function(){ return this; };
              this.onNotDetected = function(fn){ if (typeof fn === 'function') try { fn(); } catch(_){} return this; };
              this.on = function(detected, fn){ if (!detected && typeof fn === 'function') try { fn(); } catch(_){} return this; };
              this.check = function(){ return false; };
            };
            window.BlockAdBlock = noopBab;
            window.FuckAdBlock = noopBab;
            window.blockAdBlock = new noopBab();
            window.fuckAdBlock = new noopBab();
          }

          // 2. Popunder & Ad-Popup Guard
          var adUrlRe = /(?:doubleclick\.net|googlesyndication\.com|googleadservices\.com|adservice\.google|popads\.net|popcash\.net|propellerads\.com|propellerclick\.com|onclkds\.com|monetag\.com|exoclick\.com|adsterra\.com|clickadu\.com|hilltopads\.net|juicyads\.com|trafficjunky|tsyndicate\.com|magsrv\.com|pemsrv\.com|realsrv\.com|admaven\.com|taboola\.com|outbrain\.com|mgid\.com|criteo\.|prebid|openrtb|\/pagead\/|\/gampad\/|\/ptracking|\/api\/stats\/ads|\/get_midroll_info|\/ad_break|\/vast|\/vpaid|ima3\.js|fbevents\.js)/i;
          var popupAdRe = /(?:popads\.net|popcash\.net|propeller|onclkds|monetag|exoclick|adsterra|clickadu|hilltopads|juicyads|trafficjunky|realsrv|tsyndicate|magsrv|pemsrv|admaven|doubleclick\.net|googleadservices|\/popunder|\/afu\.php)/i;

          if (${if (isAdGuardOrHigher) "true" else "false"}) {
            var origOpen = window.open;
            window.open = function(url, name, specs) {
              try {
                var u = url ? String(url) : '';
                if (!u || u === 'about:blank' || popupAdRe.test(u)) {
                  return null;
                }
                if (${if (isStrictOrHigher) "true" else "false"} && (!navigator.userActivation || !navigator.userActivation.isActive)) {
                  return null;
                }
              } catch(_) {}
              return origOpen ? origOpen.apply(this, arguments) : null;
            };
          }

          // 3. In-Page Fetch / XHR / Beacon Interceptor
          if (typeof window.fetch === 'function') {
            var origFetch = window.fetch;
            window.fetch = function(input, init) {
              try {
                var reqUrl = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                if (reqUrl && adUrlRe.test(reqUrl)) {
                  return Promise.resolve(new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }));
                }
              } catch(_) {}
              return origFetch.apply(this, arguments);
            };
          }
          if (window.XMLHttpRequest && XMLHttpRequest.prototype) {
            var origXhrOpen = XMLHttpRequest.prototype.open;
            var origXhrSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.open = function(method, url) {
              try {
                if (url && adUrlRe.test(String(url))) {
                  this.__voltBlocked = true;
                  return;
                }
              } catch(_) {}
              this.__voltBlocked = false;
              return origXhrOpen.apply(this, arguments);
            };
            XMLHttpRequest.prototype.send = function() {
              if (this.__voltBlocked) {
                try {
                  Object.defineProperty(this, 'readyState', { value: 4 });
                  Object.defineProperty(this, 'status', { value: 200 });
                  Object.defineProperty(this, 'responseText', { value: '{}' });
                } catch(_) {}
                if (typeof this.onreadystatechange === 'function') try { this.onreadystatechange(); } catch(_) {}
                if (typeof this.onload === 'function') try { this.onload(); } catch(_) {}
                return;
              }
              return origXhrSend.apply(this, arguments);
            };
          }
          if (navigator && typeof navigator.sendBeacon === 'function') {
            var origBeacon = navigator.sendBeacon.bind(navigator);
            navigator.sendBeacon = function(url, data) {
              try {
                if (${if (isStealth) "true" else "false"} || (url && adUrlRe.test(String(url)))) {
                  return true;
                }
              } catch(_) {}
              return origBeacon(url, data);
            };
          }

          // 4. Video & YouTube Ad Metadata Stripper + Auto-Skipper
          if (${if (isAdGuardOrHigher) "true" else "false"}) {
            var origJsonParse = JSON.parse;
            JSON.parse = function(text, reviver) {
              var parsed = origJsonParse.call(this, text, reviver);
              try {
                if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
                  if ('adPlacements' in parsed) parsed.adPlacements = [];
                  if ('playerAds' in parsed) parsed.playerAds = [];
                  if ('adSlots' in parsed) parsed.adSlots = [];
                  if ('adBreakHeartbeatParams' in parsed) delete parsed.adBreakHeartbeatParams;
                  if (parsed.playerResponse && typeof parsed.playerResponse === 'object') {
                    if ('adPlacements' in parsed.playerResponse) parsed.playerResponse.adPlacements = [];
                    if ('playerAds' in parsed.playerResponse) parsed.playerResponse.playerAds = [];
                    if ('adSlots' in parsed.playerResponse) parsed.playerResponse.adSlots = [];
                  }
                }
              } catch(_) {}
              return parsed;
            };
          }

          // 5. Stealth Signals & Tracking Query Stripping
          if (${if (isStealth) "true" else "false"}) {
            try {
              Object.defineProperty(navigator, 'doNotTrack', { get: function(){ return '1'; }, configurable: true });
              Object.defineProperty(navigator, 'globalPrivacyControl', { get: function(){ return true; }, configurable: true });
              Object.defineProperty(navigator, 'webdriver', { get: function(){ return false; }, configurable: true });
            } catch(_) {}
            try {
              if (window.location && window.location.search && window.history && typeof window.history.replaceState === 'function') {
                var u = new URL(window.location.href);
                var stripKeys = ['fbclid','gclid','dclid','yclid','mc_eid','msclkid','igshid','utm_source','utm_medium','utm_campaign','utm_term','utm_content'];
                var changed = false;
                for (var k = 0; k < stripKeys.length; k++) {
                  if (u.searchParams.has(stripKeys[k])) {
                    u.searchParams.delete(stripKeys[k]);
                    changed = true;
                  }
                }
                if (changed) {
                  window.history.replaceState(null, '', u.toString());
                }
              }
            } catch(_) {}
          }

          // 6. Cosmetic CSS Injection & Added-Node MutationObserver (Zero Polling)
          var cssText = ${org.json.JSONObject.quote(cosmeticCss)};
          var injectCosmeticStyle = function() {
            try {
              if (!document.getElementById('volt-cosmetic-shield')) {
                var style = document.createElement('style');
                style.id = 'volt-cosmetic-shield';
                style.type = 'text/css';
                style.textContent = cssText;
                (document.head || document.documentElement).appendChild(style);
              }
            } catch(_) {}
          };

          var skipVideoAds = function() {
            try {
              var skipBtns = document.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton, [class*="skip-ad-button"], [aria-label*="Skip ad" i]');
              for (var b = 0; b < skipBtns.length; b++) {
                try { skipBtns[b].click(); } catch(_) {}
              }
              var adPlayer = document.querySelector('.ad-showing video, .ad-interrupting video');
              if (adPlayer) {
                adPlayer.muted = true;
                if (isFinite(adPlayer.duration) && adPlayer.duration > 0.2) {
                  adPlayer.currentTime = adPlayer.duration - 0.05;
                }
              }
            } catch(_) {}
          };

          var cleanClickjackOverlays = function() {
            if (!${if (isStrictOrHigher) "true" else "false"}) return;
            try {
              var vw = window.innerWidth || 0;
              var vh = window.innerHeight || 0;
              if (vw === 0 || vh === 0) return;
              var candidates = document.querySelectorAll('body > div, body > a');
              for (var idx = 0; idx < candidates.length && idx < 40; idx++) {
                var el = candidates[idx];
                if (!el || el.id === 'volt-cosmetic-shield') continue;
                var cs = window.getComputedStyle(el);
                if (!cs) continue;
                var zi = parseInt(cs.zIndex, 10);
                if ((cs.position === 'fixed' || cs.position === 'absolute') && zi >= 9999) {
                  var r = el.getBoundingClientRect();
                  var coversMost = r.width >= vw * 0.85 && r.height >= vh * 0.85;
                  var isEmptyOrTransparent = (el.textContent || '').trim().length === 0 && parseFloat(cs.opacity || '1') < 0.25;
                  if (coversMost && isEmptyOrTransparent) {
                    el.style.setProperty('display', 'none', 'important');
                    el.style.setProperty('pointer-events', 'none', 'important');
                  }
                }
              }
            } catch(_) {}
          };

          var runDomSweep = function() {
            injectCosmeticStyle();
            skipVideoAds();
            cleanClickjackOverlays();
          };

          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', runDomSweep, { once: true });
          } else {
            runDomSweep();
          }

          var sweepScheduled = false;
          var observer = new MutationObserver(function(mutations) {
            if (sweepScheduled) return;
            var hasAddedOrAdClass = false;
            for (var m = 0; m < mutations.length; m++) {
              var mut = mutations[m];
              if ((mut.addedNodes && mut.addedNodes.length > 0) || mut.type === 'attributes') {
                hasAddedOrAdClass = true;
                break;
              }
            }
            if (!hasAddedOrAdClass) return;
            sweepScheduled = true;
            requestAnimationFrame(function() {
              sweepScheduled = false;
              runDomSweep();
            });
          });
          var rootNode = document.documentElement || document;
          if (rootNode) {
            observer.observe(rootNode, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
          }
        } catch(_) {}
      })();
    """.trimIndent()
  }

  /**
   * Idempotent cosmetic filter + video ad sweep executed on `onPageFinished` and on WebViews
   * without `DOCUMENT_START_SCRIPT` support.
   */
  fun cosmeticFilterScript(blockerType: AdBlockerType): String? {
    if (blockerType == AdBlockerType.OFF) return null
    val isStrictOrHigher = blockerType == AdBlockerType.STRICT || blockerType == AdBlockerType.STEALTH
    val cosmeticCss = buildCosmeticCss(includeConsentRules = isStrictOrHigher)

    return """
      (function() {
        try {
          if (!document.getElementById('volt-cosmetic-shield')) {
            var style = document.createElement('style');
            style.id = 'volt-cosmetic-shield';
            style.type = 'text/css';
            style.textContent = ${org.json.JSONObject.quote(cosmeticCss)};
            (document.head || document.documentElement).appendChild(style);
          }
          var skipBtns = document.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton, [class*="skip-ad-button"]');
          for (var i = 0; i < skipBtns.length; i++) {
            try { skipBtns[i].click(); } catch(_) {}
          }
          var adPlayer = document.querySelector('.ad-showing video, .ad-interrupting video');
          if (adPlayer) {
            adPlayer.muted = true;
            if (isFinite(adPlayer.duration) && adPlayer.duration > 0.2) {
              adPlayer.currentTime = adPlayer.duration - 0.05;
            }
          }
        } catch(_) {}
      })();
    """.trimIndent()
  }

  private fun buildCosmeticCss(includeConsentRules: Boolean): String {
    val coreRules =
      """
      ins.adsbygoogle,
      .adsbygoogle,
      iframe[src*="doubleclick.net"],
      iframe[src*="googlesyndication.com"],
      iframe[src*="googleadservices.com"],
      iframe[src*="adnxs.com"],
      iframe[src*="taboola.com"],
      iframe[src*="outbrain.com"],
      iframe[src*="mgid.com"],
      iframe[src*="exoclick.com"],
      iframe[src*="adsterra.com"],
      iframe[id^="google_ads_iframe"],
      div[id^="div-gpt-ad"],
      div[id^="google_ads_"],
      .taboola-widget,
      #taboola-below-article-thumbnails,
      [id^="taboola-"],
      .OUTBRAIN,
      [data-widget-id^="outbrain"],
      [id^="M"][id*="ScriptRootC"],
      .mgbox,
      .rc-wc,
      [data-ad-slot],
      [data-ad-unit],
      [data-google-query-id],
      .ad-banner,
      .ad-slot,
      .ad-wrapper,
      .ad-container,
      .sponsored-ads,
      .sticky-bottom-ad,
      ytd-ad-slot-renderer,
      ytd-banner-promo-renderer,
      ytd-promoted-sparkles-web-renderer,
      ytm-promoted-sparkles-web-renderer,
      ytm-companion-slot-renderer,
      #player-ads,
      .ytp-ad-overlay-container,
      .ytp-ad-message-container {
        display: none !important;
        visibility: hidden !important;
        height: 0 !important;
        min-height: 0 !important;
        max-height: 0 !important;
        overflow: hidden !important;
      }
      """.trimIndent()

    if (!includeConsentRules) return coreRules

    val consentAndPopupRules =
      """
      #onetrust-consent-sdk,
      #CybotCookiebotDialog,
      #CybotCookiebotDialogBodyUnderlay,
      .qc-cmp2-container,
      .fc-consent-root,
      #didomi-host,
      .truste_overlay,
      .truste_box_overlay,
      [id*="sp_message_container"],
      div[class*="cookie-banner"],
      div[id*="cookie-banner"],
      div[class*="consent-banner"],
      div[id*="consent-banner"] {
        display: none !important;
        pointer-events: none !important;
      }
      html.fc-ab-root,
      body.fc-ab-root {
        overflow: auto !important;
      }
      """.trimIndent()

    return "$coreRules\n$consentAndPopupRules"
  }

  private fun normalizeHost(rawHost: String?): String =
    rawHost?.trim()?.lowercase(Locale.US)?.removePrefix("www.")?.trimEnd('.').orEmpty()

  private fun isHostInSet(normalizedHost: String, set: Set<String>): Boolean {
    if (normalizedHost.isEmpty()) return false
    var candidate = normalizedHost
    while (candidate.isNotEmpty()) {
      if (set.contains(candidate)) return true
      val dotIdx = candidate.indexOf('.')
      if (dotIdx == -1 || dotIdx == candidate.lastIndex) break
      candidate = candidate.substring(dotIdx + 1)
    }
    return false
  }

  private fun hasGoogleVideoAdParams(url: String): Boolean {
    val lower = url.lowercase(Locale.US)
    return lower.contains("&adformat=") ||
      lower.contains("?adformat=") ||
      lower.contains("&ctier=l") ||
      lower.contains("&oad=") ||
      lower.contains("&ad_type=")
  }

  private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif")
}
