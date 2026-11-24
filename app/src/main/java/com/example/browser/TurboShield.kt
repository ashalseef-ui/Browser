package com.example.browser

import android.net.Uri
import android.webkit.WebResourceResponse
import com.example.data.AdBlockerType
import java.io.ByteArrayInputStream
import java.util.Locale

object TurboShield {

  // Comprehensive Core Ad, Tracker & Telemetry Domains
  private val standardDomains: HashSet<String> =
    hashSetOf(
      "doubleclick.net",
      "googlesyndication.com",
      "googleadservices.com",
      "google-analytics.com",
      "googletagmanager.com",
      "googletagservices.com",
      "adservice.google.com",
      "pagead2.googlesyndication.com",
      "tpc.googlesyndication.com",
      "analytics.google.com",
      "connect.facebook.net",
      "pixel.facebook.com",
      "an.facebook.com",
      "ads-twitter.com",
      "analytics.twitter.com",
      "static.ads-twitter.com",
      "ads.linkedin.com",
      "px.ads.linkedin.com",
      "snap.licdn.com",
      "taboola.com",
      "trc.taboola.com",
      "outbrain.com",
      "widgets.outbrain.com",
      "criteo.com",
      "criteo.net",
      "adnxs.com",
      "ib.adnxs.com",
      "pubmatic.com",
      "rubiconproject.com",
      "openx.net",
      "casalemedia.com",
      "adsrvr.org",
      "moatads.com",
      "scorecardresearch.com",
      "quantserve.com",
      "quantcount.com",
      "amazon-adsystem.com",
      "aax.amazon-adsystem.com",
      "clarity.ms",
      "bat.bing.com",
      "mc.yandex.ru",
      "an.yandex.ru",
      "analytics.tiktok.com",
      "sc-static.net",
      "tr.snapchat.com",
      "ct.pinterest.com",
      "log.pinterest.com",
      "ads.yahoo.com",
      "adserver.yahoo.com",
      "analytics.yahoo.com",
    )

  // AdGuard Extended Network, Telemetry & Analytics Domains
  private val adGuardDomains: HashSet<String> =
    hashSetOf(
      "hotjar.com",
      "static.hotjar.com",
      "mixpanel.com",
      "api.mixpanel.com",
      "amplitude.com",
      "api.amplitude.com",
      "segment.io",
      "api.segment.io",
      "cdn.segment.com",
      "chartbeat.com",
      "static.chartbeat.com",
      "newrelic.com",
      "js-agent.newrelic.com",
      "nr-data.net",
      "branch.io",
      "app.link",
      "appsflyer.com",
      "adjust.com",
      "kochava.com",
      "demdex.net",
      "omtrdc.net",
      "2o7.net",
      "krxd.net",
      "bluekai.com",
      "exelator.com",
      "mathtag.com",
      "mediamath.com",
      "advertising.com",
      "bidswitch.net",
      "smartadserver.com",
      "sharethrough.com",
      "triplelift.com",
      "indexww.com",
      "yieldmo.com",
      "teads.tv",
      "spotxchange.com",
      "revcontent.com",
      "mgid.com",
      "adcolony.com",
      "applovin.com",
      "unityads.unity3d.com",
      "vungle.com",
      "chartboost.com",
      "ironsrc.com",
      "inmobi.com",
      "mopub.com",
      "fls-na.amazon.com",
      "sentry.io",
      "browser.sentry-cdn.com",
      "bugsnag.com",
      "fullstory.com",
      "logrocket.com",
      "mouseflow.com",
      "crazyegg.com",
      "optimizely.com",
      "cdn.optimizely.com",
      "permutive.com",
      "onesignal.com",
      "pushengage.com",
      "skimresources.com",
      "viglink.com",
      "doubleverify.com",
      "adsafeprotected.com",
      "adform.net",
      "zemanta.com",
      "yieldlab.net",
      "adition.com",
      "smartclip.net",
      "mediavine.com",
      "adthrive.com",
      "raptive.com",
      "ezoic.net",
      "ezojs.com",
      "nitropay.com",
      "buysellads.com",
      "carbonads.com",
      "adroll.com",
      "hs-analytics.net",
      "hs-scripts.com",
      "mktoresp.com",
      "heapanalytics.com",
      "pendo.io",
      "statcounter.com",
      "histats.com",
      "sharethis.com",
      "addthis.com",
    )

  // Strict Anti-Popup, Aggressive Popunder, Anti-Adblock & Cookie Consent Domains
  private val strictDomains: HashSet<String> =
    hashSetOf(
      "propellerads.com",
      "popads.net",
      "popcash.net",
      "exoclick.com",
      "juicyads.com",
      "trafficjunky.com",
      "adsterra.com",
      "clickadu.com",
      "hilltopads.net",
      "monetag.com",
      "galaksion.com",
      "richpush.co",
      "pushnami.com",
      "izooto.com",
      "getadmiral.com",
      "admiral.mgr.consensu.org",
      "consensu.org",
      "cookiebot.com",
      "onetrust.com",
      "cookielaw.org",
      "trustarc.com",
      "didomi.io",
      "sourcepoint.com",
      "bounceexchange.com",
      "wunderkind.co",
      "tsyndicate.com",
      "ero-advertising.com",
      "adtelligent.com",
      "clickiocdn.com",
    )

  // Stealth Mode Anti-Fingerprinting & Advanced Tracker Shield
  private val stealthDomains: HashSet<String> =
    hashSetOf(
      "fingerprint.com",
      "fpjs.io",
      "fingerprintjs.com",
      "geo.ipify.org",
      "maxmind.com",
      "iperf.net",
      "scorecardresearch.com",
      "quantcount.com",
      "krxd.net",
      "bluekai.com",
      "exelator.com",
      "mathtag.com",
      "demdex.net",
      "omtrdc.net",
      "2o7.net",
    )

  private val standardPathKeywords =
    listOf(
      "/pagead/",
      "/ads/ga-audiences",
      "/gtm.js",
      "/fbevents.js",
      "/analytics.js",
      "/gtag/js",
      "/prebid",
      "/tag/js/",
    )

  private val adGuardPathKeywords =
    listOf(
      "/adservice",
      "/adserver",
      "/adunit",
      "/adframe",
      "/banner_ad",
      "/show_ads.js",
      "/ads.js",
      "/telemetry/",
      "/pixel.gif",
      "/px.gif",
      "/tr?id=",
      "/ads/pixel",
      "/collect?",
    )

  private val strictPathKeywords =
    listOf(
      "/popunder",
      "/popup.js",
      "/antiadblock",
      "/blockadblock",
      "/cookie-consent",
      "/gdpr-banner",
      "/consent-notice",
    )

  const val ESTIMATED_BYTES_PER_BLOCKED_REQUEST = 48_000L

  fun shouldBlock(uri: Uri?): Boolean = shouldBlock(uri, AdBlockerType.ADGUARD)

  fun shouldBlock(uri: Uri?, blockerType: AdBlockerType): Boolean {
    if (blockerType == AdBlockerType.OFF) return false
    uri ?: return false
    val host = uri.host?.lowercase(Locale.US) ?: return false

    if (matchesDomainSet(host, standardDomains)) return true
    if (blockerType == AdBlockerType.ADGUARD || blockerType == AdBlockerType.STRICT || blockerType == AdBlockerType.STEALTH) {
      if (matchesDomainSet(host, adGuardDomains)) return true
    }
    if (blockerType == AdBlockerType.STRICT || blockerType == AdBlockerType.STEALTH) {
      if (matchesDomainSet(host, strictDomains)) return true
    }
    if (blockerType == AdBlockerType.STEALTH) {
      if (matchesDomainSet(host, stealthDomains)) return true
    }

    val pathAndQuery =
      buildString {
        append(uri.encodedPath?.lowercase(Locale.US).orEmpty())
        uri.encodedQuery?.lowercase(Locale.US)?.let {
          append('?')
          append(it)
        }
      }
    if (pathAndQuery.isEmpty()) return false

    if (standardPathKeywords.any { pathAndQuery.contains(it) }) return true
    if ((blockerType == AdBlockerType.ADGUARD || blockerType == AdBlockerType.STRICT || blockerType == AdBlockerType.STEALTH) &&
      adGuardPathKeywords.any { pathAndQuery.contains(it) }
    ) {
      return true
    }
    if ((blockerType == AdBlockerType.STRICT || blockerType == AdBlockerType.STEALTH) &&
      strictPathKeywords.any { pathAndQuery.contains(it) }
    ) {
      return true
    }
    return false
  }

  fun cosmeticFilterScript(blockerType: AdBlockerType): String? {
    if (blockerType == AdBlockerType.OFF || blockerType == AdBlockerType.STANDARD) return null

    // Robust, aggressive CSS stylesheet to hide and collapse all known ad selectors, sponsored badges, banners, and cookie popups
    val robustCss = """
      ins.adsbygoogle,
      .adsbygoogle,
      [id^='google_ads_'],
      [id*='taboola'],
      [id*='outbrain'],
      .taboola,
      .outbrain,
      [data-ad-slot],
      [data-google-query-id],
      .ad-banner,
      .ad-container,
      .sponsored-ad,
      .sponsored_content,
      amp-ad,
      amp-embed,
      [aria-label='Advertisement'],
      [class*='ad-container'],
      [class*='ad-banner'],
      [class*='advertisement'],
      [id*='div-gpt-ad'],
      div[id^='ad-'],
      div[class^='ad-'],
      aside[class*='sponsor'],
      div[class*='sponsored'],
      div[id*='sponsored'],
      iframe[src*='doubleclick'],
      iframe[src*='googlesyndication'],
      iframe[src*='ads'],
      iframe[src*='adservice'] {
        display: none !important;
        visibility: hidden !important;
        height: 0 !important;
        min-height: 0 !important;
        max-height: 0 !important;
        margin: 0 !important;
        padding: 0 !important;
        pointer-events: none !important;
        opacity: 0 !important;
      }
    """.trimIndent().replace("\n", " ")

    val strictExtrasCss =
      if (blockerType == AdBlockerType.STRICT || blockerType == AdBlockerType.STEALTH) {
        """
        .fc-consent-root,
        #onetrust-consent-sdk,
        #CybotCookiebotDialog,
        .qc-cmp2-container,
        [class*='cookie-banner'],
        [id*='cookie-consent'],
        [id*='adblock-modal'],
        [class*='paywall-banner'] {
          display: none !important;
          visibility: hidden !important;
          opacity: 0 !important;
          pointer-events: none !important;
        }
        """.trimIndent().replace("\n", " ")
      } else {
        ""
      }

    val fullCss = robustCss + " " + strictExtrasCss

    // Bulletproof MutationObserver JS engine that continuously sweeps the DOM for newly injected ads/popups
    val mutationEngine = """
      if (!window.__voltAdRemoverActive) {
        window.__voltAdRemoverActive = true;
        window.__voltPopupGuard = true;

        // 1. CSS Injection
        var styleId = 'volt-robust-ad-remover';
        var styleEl = document.getElementById(styleId);
        if (!styleEl) {
          styleEl = document.createElement('style');
          styleEl.id = styleId;
          (document.head || document.documentElement).appendChild(styleEl);
        }
        styleEl.textContent = "${fullCss.replace("\"", "\\\"")}";

        // 2. Popup & Popunder Protector
        var origOpen = window.open;
        window.open = function(url, name, specs) {
          if (!url || /doubleclick|popads|popcash|propeller|adsterra|exoclick|taboola|outbrain|tsyndicate|adservice/i.test(String(url))) {
            return null;
          }
          return origOpen ? origOpen.apply(window, arguments) : null;
        };

        // 3. Dynamic Mutation Observer for lazy-loaded ads & banners
        function purgeAds() {
          try {
            var selectors = 'ins.adsbygoogle, .adsbygoogle, [id^="google_ads_"], [id*="taboola"], [id*="outbrain"], .taboola, .outbrain, [data-ad-slot], .ad-banner, .ad-container, .sponsored-ad, amp-ad, amp-embed, [class*="advertisement"], [class*="ad-container"], [id*="div-gpt-ad"], iframe[src*="doubleclick"], iframe[src*="googlesyndication"], iframe[src*="ads"]';
            var ads = document.querySelectorAll(selectors);
            for (var i = 0; i < ads.length; i++) {
              var el = ads[i];
              if (el && el.parentNode) {
                el.style.setProperty('display', 'none', 'important');
                el.style.setProperty('visibility', 'hidden', 'important');
                el.style.setProperty('height', '0px', 'important');
                el.remove();
              }
            }
          } catch(e) {}
        }

        if (document.body) {
          var observer = new MutationObserver(function(mutations) {
            clearTimeout(window.__voltAdDebounce);
            window.__voltAdDebounce = setTimeout(purgeAds, 120);
          });
          observer.observe(document.body, { childList: true, subtree: true });
        }
        window.addEventListener('DOMContentLoaded', purgeAds);
        window.addEventListener('load', purgeAds);
      }
    """.trimIndent()

    val stealthScript =
      if (blockerType == AdBlockerType.STEALTH) {
        """
        try {
          Object.defineProperty(navigator, 'doNotTrack', { get: function() { return '1'; } });
          Object.defineProperty(navigator, 'globalPrivacyControl', { get: function() { return true; } });
          Object.defineProperty(navigator, 'msDoNotTrack', { get: function() { return '1'; } });
          window.doNotTrack = '1';
          Object.defineProperty(navigator, 'webdriver', { get: function() { return false; } });
          Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return 8; } });
          Object.defineProperty(navigator, 'deviceMemory', { get: function() { return 8; } });
          Object.defineProperty(navigator, 'languages', { get: function() { return ['en-US', 'en']; } });
          if (navigator.sendBeacon) {
            var origBeacon = navigator.sendBeacon;
            navigator.sendBeacon = function(url, data) {
              var low = String(url || '').toLowerCase();
              if (/analytics|telemetry|track|collect|metrics|log|sentry|bugsnag/i.test(low)) {
                return true;
              }
              return origBeacon.apply(window, arguments);
            };
          }
          var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
          HTMLCanvasElement.prototype.toDataURL = function(type) {
            try {
              var ctx = this.getContext('2d');
              if (ctx && this.width > 16 && this.height > 16) {
                var imgData = ctx.getImageData(0, 0, 1, 1);
                imgData.data[0] = imgData.data[0] ^ 1;
                ctx.putImageData(imgData, 0, 0);
              }
            } catch (e) {}
            return origToDataURL.apply(this, arguments);
          };
        } catch (e) {}
        """.trimIndent()
      } else {
        ""
      }

    return """
      (function(){
        try {
          $mutationEngine
          $stealthScript
        } catch(e) {}
      })();
    """.trimIndent()
  }

  private fun matchesDomainSet(host: String, domainSet: HashSet<String>): Boolean {
    if (domainSet.contains(host)) return true
    var idx = host.indexOf('.')
    while (idx != -1 && idx + 1 < host.length) {
      val parent = host.substring(idx + 1)
      if (domainSet.contains(parent)) return true
      idx = host.indexOf('.', idx + 1)
    }
    return false
  }

  fun emptyBlockedResponse(): WebResourceResponse =
    WebResourceResponse(
      "text/plain",
      "utf-8",
      200,
      "Blocked by Volt Shield",
      mapOf("Cache-Control" to "no-store"),
      ByteArrayInputStream(ByteArray(0)),
    )
}
