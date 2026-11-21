package com.example.vpn

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class PublicIpInfo(
  val ip: String,
  val country: String = "",
  val countryCode: String = "",
)

class PublicRelayManager {

  private val proxyExecutor = Executors.newSingleThreadExecutor()

  // Always direct (never loops through an active/stale WebView proxy)
  private val directClient: OkHttpClient =
    OkHttpClient.Builder()
      .proxy(Proxy.NO_PROXY)
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .build()

  suspend fun fetchCurrentExitIp(viaRelay: PublicRelayNode? = null): PublicIpInfo? =
    withContext(Dispatchers.IO) {
      val client =
        if (viaRelay == null) {
          directClient
        } else {
          OkHttpClient.Builder()
            .proxy(toJavaNetProxy(viaRelay))
            .connectTimeout(3200, TimeUnit.MILLISECONDS)
            .readTimeout(3200, TimeUnit.MILLISECONDS)
            .build()
        }

      // Fast trace check returns both IP and real exit country code in ~150 bytes
      val traceResult =
        runCatching {
          val req = Request.Builder().url("https://1.1.1.1/cdn-cgi/trace").build()
          client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@runCatching null
            parseCloudflareTrace(res.body?.string().orEmpty())
          }
        }.getOrNull()

      if (traceResult != null) {
        return@withContext traceResult.copy(
          country =
            traceResult.country.ifBlank { viaRelay?.country.orEmpty() },
          countryCode =
            traceResult.countryCode.ifBlank { viaRelay?.countryCode.orEmpty() },
        )
      }

      // Fallback to ipify JSON
      runCatching {
        val request = Request.Builder().url("https://api.ipify.org?format=json").build()
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@withContext null
          val body = response.body?.string().orEmpty()
          val ip = JSONObject(body).optString("ip")
          if (ip.isBlank()) return@withContext null
          PublicIpInfo(
            ip = ip,
            country = viaRelay?.country.orEmpty(),
            countryCode = viaRelay?.countryCode.orEmpty(),
          )
        }
      }.getOrNull()
    }

  suspend fun fetchAndProbePublicRelays(excludeIds: Set<String> = emptySet()): List<PublicRelayNode> =
    withContext(Dispatchers.IO) {
      val candidates = fetchMultiSourceCandidates(excludeIds)
      if (candidates.isEmpty()) return@withContext emptyList()

      coroutineScope {
        candidates.take(36).map { node ->
          async {
            val started = System.currentTimeMillis()
            val probeIp = fetchCurrentExitIp(node)
            val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
            if (probeIp != null) {
              val resolvedCode = probeIp.countryCode.ifBlank { node.countryCode }
              val resolvedCountry =
                probeIp.country.ifBlank { countryCodeToName(resolvedCode) }
              node.copy(
                countryCode = resolvedCode,
                country = resolvedCountry,
                latencyMs = elapsed,
                isReachable = true,
              )
            } else {
              node.copy(
                latencyMs = null,
                isReachable = false,
              )
            }
          }
        }.awaitAll()
      }.sortedWith(
        compareByDescending<PublicRelayNode> { it.isReachable == true }
          .thenBy { it.latencyMs ?: Long.MAX_VALUE }
      )
    }

  private suspend fun fetchMultiSourceCandidates(excludeIds: Set<String>): List<PublicRelayNode> =
    coroutineScope {
      val proxyScrapeHttpDeferred = async {
        fetchPlainTextFeed(
          url =
            "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=3500&country=all&ssl=yes&anonymity=elite,anonymous",
          protocol = "HTTP",
          source = "ProxyScrape",
          limit = 20,
        )
      }
      val proxyScrapeSocksDeferred = async {
        fetchPlainTextFeed(
          url =
            "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=socks5&timeout=3500&country=all",
          protocol = "SOCKS5",
          source = "ProxyScrape",
          limit = 16,
        )
      }
      val geoNodeDeferred = async { fetchGeoNodeRelays() }

      val primaryPool =
        proxyScrapeHttpDeferred.await() +
          proxyScrapeSocksDeferred.await() +
          geoNodeDeferred.await()

      val combined =
        if (primaryPool.size < 18) {
          val speedXHttp =
            fetchPlainTextFeed(
              url = "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
              protocol = "HTTP",
              source = "SpeedX",
              limit = 15,
            )
          val speedXSocks =
            fetchPlainTextFeed(
              url = "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt",
              protocol = "SOCKS5",
              source = "SpeedX",
              limit = 10,
            )
          primaryPool + speedXHttp + speedXSocks
        } else {
          primaryPool
        }

      val deduped = LinkedHashMap<String, PublicRelayNode>()
      combined.forEach { node ->
        if (node.id !in excludeIds) {
          val existing = deduped[node.id]
          if (existing == null || (existing.countryCode == "AUTO" && node.countryCode != "AUTO")) {
            deduped[node.id] = node
          }
        }
      }
      deduped.values.toList()
    }

  private fun fetchPlainTextFeed(
    url: String,
    protocol: String,
    source: String,
    limit: Int,
  ): List<PublicRelayNode> {
    val request = Request.Builder().url(url).build()
    return runCatching {
      directClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return emptyList()
        parsePlainProxyLines(
          rawText = response.body?.string().orEmpty(),
          protocol = protocol,
          source = source,
        ).take(limit)
      }
    }.getOrDefault(emptyList())
  }

  private fun fetchGeoNodeRelays(): List<PublicRelayNode> {
    val url =
      "https://proxylist.geonode.com/api/proxy-list?limit=30&page=1&sort_by=lastChecked&sort_type=desc&protocols=http%2Chttps%2Csocks5"
    val request = Request.Builder().url(url).build()
    return runCatching {
      directClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return emptyList()
        val json = JSONObject(response.body?.string().orEmpty())
        val data = json.optJSONArray("data") ?: return emptyList()
        buildList {
          for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val ip = item.optString("ip").trim()
            val port = item.optInt("port", 0)
            if (ip.isBlank() || port !in 1..65535) continue
            val countryCode = item.optString("country", "AUTO").uppercase()
            val anonymity = item.optString("anonymityLevel", "public")
            val protocols = item.optJSONArray("protocols")
            val rawProto = protocols?.optString(0)?.uppercase() ?: "HTTP"
            val normalizedProto = if (rawProto == "SOCKS5") "SOCKS5" else "HTTP"
            add(
              PublicRelayNode(
                ip = ip,
                port = port,
                country = countryCodeToName(countryCode),
                countryCode = countryCode,
                protocol = normalizedProto,
                anonymity = anonymity,
                source = "GeoNode",
              )
            )
          }
        }
      }
    }.getOrDefault(emptyList())
  }

  fun applyWebViewProxy(relay: PublicRelayNode, onApplied: (Boolean) -> Unit) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
      onApplied(false)
      return
    }
    val rule =
      if (relay.protocol.equals("SOCKS5", ignoreCase = true)) {
        "socks5://${relay.ip}:${relay.port}"
      } else {
        "http://${relay.ip}:${relay.port}"
      }
    val config =
      ProxyConfig.Builder()
        .addProxyRule(rule)
        .addBypassRule("<-loopback>")
        .build()
    ProxyController.getInstance().setProxyOverride(config, proxyExecutor) {
      onApplied(true)
    }
  }

  fun clearWebViewProxy(onCleared: () -> Unit) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
      onCleared()
      return
    }
    ProxyController.getInstance().clearProxyOverride(proxyExecutor) {
      onCleared()
    }
  }

  companion object {
    fun toJavaNetProxy(relay: PublicRelayNode): Proxy {
      val type =
        if (relay.protocol.equals("SOCKS5", ignoreCase = true)) Proxy.Type.SOCKS
        else Proxy.Type.HTTP
      return Proxy(type, InetSocketAddress(relay.ip, relay.port))
    }

    fun parsePlainProxyLines(
      rawText: String,
      protocol: String,
      source: String,
    ): List<PublicRelayNode> {
      val lineRegex = Regex("""^(\d{1,3}(?:\.\d{1,3}){3}):(\d{2,5})$""")
      return rawText
        .lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
          val match = lineRegex.matchEntire(line) ?: return@mapNotNull null
          val ip = match.groupValues[1]
          val port = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
          if (port !in 1..65535) return@mapNotNull null
          PublicRelayNode(
            ip = ip,
            port = port,
            country = "Auto Detect",
            countryCode = "AUTO",
            protocol = protocol,
            anonymity = "anonymous",
            source = source,
          )
        }
        .toList()
    }

    fun parseCloudflareTrace(body: String): PublicIpInfo? {
      var ip = ""
      var loc = ""
      body.lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
          trimmed.startsWith("ip=") -> ip = trimmed.removePrefix("ip=").trim()
          trimmed.startsWith("loc=") -> loc = trimmed.removePrefix("loc=").trim().uppercase()
        }
      }
      if (ip.isBlank()) return null
      return PublicIpInfo(
        ip = ip,
        country = if (loc.isNotBlank()) countryCodeToName(loc) else "",
        countryCode = loc,
      )
    }

    fun countryCodeToName(code: String): String =
      when (code.uppercase()) {
        "AUTO", "UN", "" -> "Auto Detect"
        "US" -> "United States"
        "DE" -> "Germany"
        "GB", "UK" -> "United Kingdom"
        "NL" -> "Netherlands"
        "FR" -> "France"
        "CA" -> "Canada"
        "SG" -> "Singapore"
        "JP" -> "Japan"
        "CH" -> "Switzerland"
        "SE" -> "Sweden"
        "FI" -> "Finland"
        "BR" -> "Brazil"
        "IN" -> "India"
        "KR" -> "South Korea"
        "AU" -> "Australia"
        "PL" -> "Poland"
        "RO" -> "Romania"
        "ID" -> "Indonesia"
        "TH" -> "Thailand"
        "VN" -> "Vietnam"
        "HK" -> "Hong Kong"
        "TR" -> "Turkey"
        "IT" -> "Italy"
        "ES" -> "Spain"
        else -> code.uppercase()
      }
  }
}
