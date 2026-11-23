package com.example.vpn

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class PublicIpInfo(
  val ip: String,
  val country: String = "",
  val countryCode: String = "",
)

data class RelayScanSummary(
  val relays: List<PublicRelayNode>,
  val totalCandidatesScanned: Int,
)

class PublicRelayManager {

  private val proxyExecutor = Executors.newSingleThreadExecutor()

  // Always direct (never loops through an active/stale WebView proxy)
  private val directClient: OkHttpClient =
    OkHttpClient.Builder()
      .proxy(Proxy.NO_PROXY)
      .connectionPool(ConnectionPool(24, 2, TimeUnit.MINUTES))
      .connectTimeout(2200, TimeUnit.MILLISECONDS)
      .readTimeout(2200, TimeUnit.MILLISECONDS)
      .build()

  suspend fun fetchCurrentExitIp(
    viaRelay: PublicRelayNode? = null,
    timeoutMs: Long = 1800L,
  ): PublicIpInfo? =
    withContext(Dispatchers.IO) {
      val client =
        if (viaRelay == null) {
          directClient
        } else {
          directClient.newBuilder()
            .proxy(toJavaNetProxy(viaRelay))
            .apply {
              val user = viaRelay.username
              val pass = viaRelay.password.orEmpty()
              if (!user.isNullOrBlank()) {
                proxyAuthenticator { _, response ->
                  if (response.request.header("Proxy-Authorization") != null) null
                  else {
                    response.request
                      .newBuilder()
                      .header("Proxy-Authorization", Credentials.basic(user, pass))
                      .build()
                  }
                }
              }
            }
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
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
          country = traceResult.country.ifBlank { viaRelay?.country.orEmpty() },
          countryCode = traceResult.countryCode.ifBlank { viaRelay?.countryCode.orEmpty() },
        )
      }

      // Fallback to ipify JSON only for direct/custom checks (avoid doubling probe time on bulk scans)
      if (viaRelay != null && viaRelay.source != "Custom") return@withContext null

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

  /**
   * High-speed 2-stage scanner prioritizing Germany (DE) and India (IN) first:
   * 1) Fetch targeted DE/IN + fast global feeds in parallel with strict 2s feed timeouts.
   * 2) Ultra-fast TCP pre-filter (500ms timeout) eliminates dead/laggy nodes immediately.
   * 3) Real HTTPS verify prioritizing DE/IN nodes first, with strict 1.8s timeout and DE/IN-first sorting.
   */
  suspend fun fetchAndProbePublicRelays(
    excludeIds: Set<String> = emptySet(),
    tcpPoolLimit: Int = 100,
    httpsVerifyLimit: Int = 36,
    tcpTimeoutMs: Int = 550,
    httpsTimeoutMs: Long = 1800L,
  ): RelayScanSummary =
    withContext(Dispatchers.IO) {
      val candidates = fetchMultiSourceCandidates(excludeIds)
      if (candidates.isEmpty()) {
        return@withContext RelayScanSummary(emptyList(), 0)
      }

      // Prioritize DE / IN tagged candidates at the head of the TCP scan batch
      val prioritizedCandidates = prioritizePreferredCandidates(candidates).take(tcpPoolLimit)
      val tcpSemaphore = Semaphore(40)

      // Stage 1: Fast TCP connect check filters out dead/slow proxies in ~0.5s
      val tcpAlive =
        coroutineScope {
          prioritizedCandidates.map { node ->
            async {
              tcpSemaphore.withPermit {
                val tcpMs = probeTcpConnect(node.ip, node.port, timeoutMs = tcpTimeoutMs)
                if (tcpMs != null) node to tcpMs else null
              }
            }
          }.awaitAll()
            .filterNotNull()
            .sortedWith(
              compareByDescending<Pair<PublicRelayNode, Long>> { it.first.isPreferredRegion }
                .thenBy { it.second }
            )
            .map { it.first }
        }

      if (tcpAlive.isEmpty()) {
        return@withContext RelayScanSummary(
          relays = prioritizedCandidates.take(10).map { it.copy(isReachable = false, latencyMs = null) },
          totalCandidatesScanned = prioritizedCandidates.size,
        )
      }

      // Stage 2: Verify real HTTPS tunneling (DE/IN first, then fastest TCP nodes)
      val httpsSemaphore = Semaphore(24)
      val verified =
        coroutineScope {
          tcpAlive.take(httpsVerifyLimit).map { node ->
            async {
              httpsSemaphore.withPermit {
                verifyHttpsRelay(node, timeoutMs = httpsTimeoutMs)
              }
            }
          }.awaitAll()
        }.sortedWith(relayRankingComparator)

      RelayScanSummary(
        relays = verified,
        totalCandidatesScanned = prioritizedCandidates.size,
      )
    }

  /**
   * Ultra-fast startup discovery (~1.5s): targeted DE/IN + low-latency global relays,
   * 450ms TCP check, and verifying top 16 alive nodes (DE/IN first) with 1500ms HTTPS cap.
   */
  suspend fun findFastestStartupBatch(excludeIds: Set<String> = emptySet()): RelayScanSummary =
    fetchAndProbePublicRelays(
      excludeIds = excludeIds,
      tcpPoolLimit = 56,
      httpsVerifyLimit = 16,
      tcpTimeoutMs = 450,
      httpsTimeoutMs = 1500L,
    )

  private suspend fun verifyHttpsRelay(
    node: PublicRelayNode,
    timeoutMs: Long,
  ): PublicRelayNode {
    val started = System.currentTimeMillis()
    val probeIp = fetchCurrentExitIp(node, timeoutMs = timeoutMs)
    val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
    return if (probeIp != null) {
      val resolvedCode = probeIp.countryCode.ifBlank { node.countryCode }
      val resolvedCountry = probeIp.country.ifBlank { countryCodeToName(resolvedCode) }
      node.copy(
        countryCode = resolvedCode,
        country = resolvedCountry,
        latencyMs = elapsed,
        isReachable = true,
        resolvedExitIp = probeIp.ip,
      )
    } else {
      node.copy(
        latencyMs = null,
        isReachable = false,
      )
    }
  }

  private fun probeTcpConnect(ip: String, port: Int, timeoutMs: Int): Long? {
    val started = System.currentTimeMillis()
    return runCatching {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(ip, port), timeoutMs)
        (System.currentTimeMillis() - started).coerceAtLeast(1L)
      }
    }.getOrNull()
  }

  private suspend fun fetchMultiSourceCandidates(excludeIds: Set<String>): List<PublicRelayNode> =
    coroutineScope {
      // Every feed is bounded to 2000ms so no slow mirror can stall proxy discovery
      val geoNodePreferred = async {
        withTimeoutOrNull(2000L) {
          fetchGeoNodeRelays(
            "https://proxylist.geonode.com/api/proxy-list?limit=35&page=1&sort_by=lastChecked&sort_type=desc&country=DE%2CIN&protocols=http%2Chttps%2Csocks5"
          )
        }.orEmpty()
      }
      val proxyScrapeDeHttp = async {
        withTimeoutOrNull(2000L) {
          fetchPlainTextFeed(
            url = "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=1500&country=DE&ssl=yes&anonymity=elite,anonymous",
            protocol = "HTTP",
            source = "ProxyScrape",
            defaultCountryCode = "DE",
            limit = 20,
          )
        }.orEmpty()
      }
      val proxyScrapeDeSocks = async {
        withTimeoutOrNull(2000L) {
          fetchPlainTextFeed(
            url = "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=socks5&timeout=1500&country=DE",
            protocol = "SOCKS5",
            source = "ProxyScrape",
            defaultCountryCode = "DE",
            limit = 16,
          )
        }.orEmpty()
      }
      val proxyScrapeInHttp = async {
        withTimeoutOrNull(2000L) {
          fetchPlainTextFeed(
            url = "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=1500&country=IN&ssl=yes&anonymity=elite,anonymous",
            protocol = "HTTP",
            source = "ProxyScrape",
            defaultCountryCode = "IN",
            limit = 20,
          )
        }.orEmpty()
      }
      val proxyScrapeInSocks = async {
        withTimeoutOrNull(2000L) {
          fetchPlainTextFeed(
            url = "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=socks5&timeout=1500&country=IN",
            protocol = "SOCKS5",
            source = "ProxyScrape",
            defaultCountryCode = "IN",
            limit = 16,
          )
        }.orEmpty()
      }
      val geoNodeGlobal = async {
        withTimeoutOrNull(2000L) {
          fetchGeoNodeRelays(
            "https://proxylist.geonode.com/api/proxy-list?limit=35&page=1&sort_by=speed&sort_type=asc&protocols=http%2Chttps%2Csocks5"
          )
        }.orEmpty()
      }
      val proxyScrapeGlobalHttp = async {
        withTimeoutOrNull(2000L) {
          fetchPlainTextFeed(
            url = "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=1200&country=all&ssl=yes&anonymity=elite,anonymous",
            protocol = "HTTP",
            source = "ProxyScrape",
            defaultCountryCode = "AUTO",
            limit = 20,
          )
        }.orEmpty()
      }
      val monosansSocks = async {
        withTimeoutOrNull(2000L) {
          fetchPlainTextFeed(
            url = "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/socks5.txt",
            protocol = "SOCKS5",
            source = "Monosans",
            defaultCountryCode = "AUTO",
            limit = 16,
          )
        }.orEmpty()
      }
      val vakhovHttps = async {
        withTimeoutOrNull(2000L) {
          fetchPlainTextFeed(
            url = "https://raw.githubusercontent.com/vakhov/fresh-proxy-list/master/https.txt",
            protocol = "HTTP",
            source = "Vakhov",
            defaultCountryCode = "AUTO",
            limit = 16,
          )
        }.orEmpty()
      }

      val combined =
        geoNodePreferred.await() +
          proxyScrapeDeHttp.await() +
          proxyScrapeDeSocks.await() +
          proxyScrapeInHttp.await() +
          proxyScrapeInSocks.await() +
          geoNodeGlobal.await() +
          proxyScrapeGlobalHttp.await() +
          monosansSocks.await() +
          vakhovHttps.await()

      val deduped = LinkedHashMap<String, PublicRelayNode>()
      combined.forEach { node ->
        if (node.id !in excludeIds && isPublicRoutableIp(node.ip)) {
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
    defaultCountryCode: String,
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
          defaultCountryCode = defaultCountryCode,
        ).take(limit)
      }
    }.getOrDefault(emptyList())
  }

  private fun fetchGeoNodeRelays(url: String): List<PublicRelayNode> {
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
            if (!isPublicRoutableIp(ip) || port !in 1..65535) continue
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
    val PREFERRED_COUNTRY_CODES = setOf("DE", "IN")

    val relayRankingComparator: Comparator<PublicRelayNode> =
      compareByDescending<PublicRelayNode> { it.isReachable == true }
        .thenByDescending { it.isPreferredRegion }
        .thenBy { it.latencyMs ?: Long.MAX_VALUE }

    fun prioritizePreferredCandidates(candidates: List<PublicRelayNode>): List<PublicRelayNode> {
      val preferred = candidates.filter { it.isPreferredRegion }
      val others = candidates.filterNot { it.isPreferredRegion }
      return preferred + others
    }

    fun toJavaNetProxy(relay: PublicRelayNode): Proxy {
      val type =
        if (relay.protocol.equals("SOCKS5", ignoreCase = true)) Proxy.Type.SOCKS
        else Proxy.Type.HTTP
      return Proxy(type, InetSocketAddress(relay.ip, relay.port))
    }

    fun isPublicRoutableIp(ip: String): Boolean {
      val parts = ip.split('.')
      if (parts.size != 4) return false
      val octets = parts.map { it.toIntOrNull() ?: return false }
      if (octets.any { it !in 0..255 }) return false
      val a = octets[0]
      val b = octets[1]
      if (a == 0 || a == 10 || a == 127 || a >= 224) return false
      if (a == 169 && b == 254) return false
      if (a == 172 && b in 16..31) return false
      if (a == 192 && b == 168) return false
      return true
    }

    fun parseSingleProxy(rawInput: String, protocol: String): PublicRelayNode? {
      val clean = rawInput.trim()
      if (clean.isEmpty()) return null

      val inferredProto =
        when {
          clean.startsWith("socks5://", ignoreCase = true) -> "SOCKS5"
          clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true) -> "HTTP"
          protocol.equals("SOCKS5", ignoreCase = true) -> "SOCKS5"
          else -> "HTTP"
        }

      val withoutScheme =
        clean
          .removePrefix("http://")
          .removePrefix("https://")
          .removePrefix("socks5://")
          .substringBefore('/')

      var username: String? = null
      var password: String? = null
      val hostPortPart: String

      if (withoutScheme.contains('@')) {
        val creds = withoutScheme.substringBeforeLast('@')
        hostPortPart = withoutScheme.substringAfterLast('@')
        username = creds.substringBefore(':').trim().takeIf { it.isNotEmpty() }
        password = creds.substringAfter(':', "").trim()
      } else {
        val colonParts = withoutScheme.split(':')
        if (colonParts.size == 4) {
          hostPortPart = "${colonParts[0]}:${colonParts[1]}"
          username = colonParts[2].trim().takeIf { it.isNotEmpty() }
          password = colonParts[3].trim()
        } else {
          hostPortPart = withoutScheme
        }
      }

      val parts = hostPortPart.split(':')
      if (parts.size != 2) return null
      val ip = parts[0].trim()
      val port = parts[1].trim().toIntOrNull() ?: return null
      if (!isPublicRoutableIp(ip) || port !in 1..65535) return null
      return PublicRelayNode(
        ip = ip,
        port = port,
        country = if (username != null) "Private Auth Proxy" else "Custom Proxy",
        countryCode = "CUSTOM",
        protocol = inferredProto,
        anonymity = if (username != null) "private" else "custom",
        source = if (username != null) "Private Auth" else "Custom",
        username = username,
        password = password,
      )
    }

    fun parsePlainProxyLines(
      rawText: String,
      protocol: String,
      source: String,
      defaultCountryCode: String = "AUTO",
    ): List<PublicRelayNode> {
      val lineRegex = Regex("""^(\d{1,3}(?:\.\d{1,3}){3}):(\d{2,5})$""")
      val countryName = countryCodeToName(defaultCountryCode)
      return rawText
        .lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
          val match = lineRegex.matchEntire(line) ?: return@mapNotNull null
          val ip = match.groupValues[1]
          val port = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
          if (port !in 1..65535 || !isPublicRoutableIp(ip)) return@mapNotNull null
          PublicRelayNode(
            ip = ip,
            port = port,
            country = countryName,
            countryCode = defaultCountryCode.uppercase(),
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
        "CUSTOM" -> "Custom Proxy"
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
        "NO" -> "Norway"
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
        "TW" -> "Taiwan"
        "TR" -> "Turkey"
        "IT" -> "Italy"
        "ES" -> "Spain"
        else -> code.uppercase()
      }
  }
}
