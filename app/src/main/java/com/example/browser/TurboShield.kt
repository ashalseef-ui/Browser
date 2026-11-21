package com.example.browser

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.Locale

object TurboShield {

  // Fast O(1) lookup domain suffix table for known ad networks, analytics trackers, and heavy telemetry scripts.
  private val blockedDomains: HashSet<String> =
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
      "propellerads.com",
      "popads.net",
      "popcash.net",
      "adcolony.com",
      "applovin.com",
      "unityads.unity3d.com",
      "vungle.com",
      "chartboost.com",
      "ironsrc.com",
      "inmobi.com",
      "mopub.com",
      "amazon-adsystem.com",
      "aax.amazon-adsystem.com",
      "fls-na.amazon.com",
      "clarity.ms",
      "bat.bing.com",
      "mc.yandex.ru",
      "an.yandex.ru",
      "tiktok.com/i18n/pixel",
      "analytics.tiktok.com",
      "sc-static.net",
      "tr.snapchat.com",
      "ct.pinterest.com",
      "log.pinterest.com",
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
    )

  private val blockedPathKeywords =
    listOf(
      "/pagead/",
      "/ads/ga-audiences",
      "/gtm.js",
      "/fbevents.js",
      "/analytics.js",
      "/gtag/js",
      "/prebid",
    )

  const val ESTIMATED_BYTES_PER_BLOCKED_REQUEST = 48_000L

  fun shouldBlock(uri: Uri?): Boolean {
    uri ?: return false
    val host = uri.host?.lowercase(Locale.US) ?: return false
    if (isDomainBlocked(host)) return true

    val path = uri.encodedPath?.lowercase(Locale.US) ?: return false
    return blockedPathKeywords.any { path.contains(it) }
  }

  private fun isDomainBlocked(host: String): Boolean {
    if (blockedDomains.contains(host)) return true
    var idx = host.indexOf('.')
    while (idx != -1 && idx + 1 < host.length) {
      val parent = host.substring(idx + 1)
      if (blockedDomains.contains(parent)) return true
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
