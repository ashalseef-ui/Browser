package com.example.vpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import java.io.ByteArrayInputStream
import java.net.Proxy
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class WireGuardConnectionInfo(
  val endpoint: String,
  val profileName: String,
)

class WireGuardManager(private val context: Context) {

  private val backend: GoBackend by lazy { GoBackend(context.applicationContext) }

  private val _tunnelUpFlow = MutableStateFlow(false)
  val tunnelUpFlow: StateFlow<Boolean> = _tunnelUpFlow.asStateFlow()

  private val voltTunnel =
    object : Tunnel {
      override fun getName(): String = TUNNEL_NAME

      override fun onStateChange(newState: Tunnel.State) {
        _tunnelUpFlow.value = newState == Tunnel.State.UP
      }
    }

  // Always direct (never loops through an active WebView proxy when registering WARP)
  private val directClient: OkHttpClient =
    OkHttpClient.Builder()
      .proxy(Proxy.NO_PROXY)
      .connectTimeout(6, TimeUnit.SECONDS)
      .readTimeout(8, TimeUnit.SECONDS)
      .build()

  suspend fun registerCloudflareWarpConfig(): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val keyPair = KeyPair()
        val privateKeyB64 = keyPair.privateKey.toBase64()
        val publicKeyB64 = keyPair.publicKey.toBase64()

        val formatter =
          SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
          }
        val tosTimestamp = formatter.format(Date())
        val serialNumber = UUID.randomUUID().toString().replace("-", "").take(12)

        val payload =
          JSONObject().apply {
            put("key", publicKeyB64)
            put("install_id", "")
            put("fcm_token", "")
            put("tos", tosTimestamp)
            put("model", "Android")
            put("serial_number", serialNumber)
            put("locale", "en_US")
          }

        val request =
          Request.Builder()
            .url(WARP_REG_URL)
            .header("User-Agent", "okhttp/3.12.1")
            .header("Content-Type", "application/json; charset=UTF-8")
            .post(payload.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        val bodyString =
          directClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
              error("WARP registration failed (HTTP ${response.code})")
            }
            response.body?.string().orEmpty()
          }

        val root = JSONObject(bodyString)
        val configObj = root.optJSONObject("config") ?: error("WARP config missing in response")
        val ifaceObj = configObj.optJSONObject("interface")
        val addrObj = ifaceObj?.optJSONObject("addresses")
        val clientIpv4 = addrObj?.optString("v4").orEmpty().ifBlank { "172.16.0.2" }

        val peer0 = configObj.optJSONArray("peers")?.optJSONObject(0)
        val peerPubKey =
          peer0?.optString("public_key").orEmpty().ifBlank { DEFAULT_WARP_PUBLIC_KEY }
        val rawEndpointV4 = peer0?.optJSONObject("endpoint")?.optString("v4").orEmpty().trim()
        val resolvedEndpoint =
          if (rawEndpointV4.isBlank() || rawEndpointV4.endsWith(":0")) {
            DEFAULT_WARP_ENDPOINT
          } else {
            rawEndpointV4
          }

        val conf =
          buildWarpConfigString(
            privateKeyBase64 = privateKeyB64,
            peerPublicKeyBase64 = peerPubKey,
            clientIpv4 = clientIpv4,
            endpoint = resolvedEndpoint,
          )
        // Verify WireGuard Config.parse accepts it cleanly before saving/returning
        parseConfig(conf).getOrThrow()
        conf
      }
    }

  fun parseConfig(rawConf: String): Result<Config> =
    runCatching {
      val trimmed = rawConf.trim()
      require(trimmed.isNotEmpty()) { "WireGuard configuration is empty" }
      ByteArrayInputStream(trimmed.toByteArray(StandardCharsets.UTF_8)).use { stream ->
        Config.parse(stream)
      }
    }

  suspend fun connect(rawConf: String, profileName: String): Result<WireGuardConnectionInfo> =
    withContext(Dispatchers.IO) {
      runCatching {
        val parsed = parseConfig(rawConf).getOrThrow()
        val endpointSummary =
          parsed.peers
            .firstOrNull()
            ?.endpoint
            ?.map { it.toString() }
            ?.orElse("")
            .orEmpty()
            .ifBlank { parseEndpointFromConf(rawConf) }

        val newState = backend.setState(voltTunnel, Tunnel.State.UP, parsed)
        val active = newState == Tunnel.State.UP
        _tunnelUpFlow.value = active
        if (!active) error("WireGuard tunnel did not reach UP state")

        WireGuardConnectionInfo(
          endpoint = endpointSummary,
          profileName = profileName,
        )
      }
    }

  suspend fun disconnect() {
    withContext(Dispatchers.IO) {
      runCatching {
        if (backend.getState(voltTunnel) == Tunnel.State.UP) {
          backend.setState(voltTunnel, Tunnel.State.DOWN, null)
        }
      }
      _tunnelUpFlow.value = false
    }
  }

  suspend fun readStatistics(): Pair<Long, Long> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (backend.getState(voltTunnel) != Tunnel.State.UP) return@runCatching 0L to 0L
        val stats = backend.getStatistics(voltTunnel)
        stats.totalRx() to stats.totalTx()
      }.getOrDefault(0L to 0L)
    }

  fun isTunnelActiveNow(): Boolean =
    runCatching { backend.getState(voltTunnel) == Tunnel.State.UP }.getOrDefault(false)

  companion object {
    private const val TUNNEL_NAME = "volt_wg"
    private const val WARP_REG_URL = "https://api.cloudflareclient.com/v0a2158/reg"
    const val DEFAULT_WARP_PUBLIC_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
    const val DEFAULT_WARP_ENDPOINT = "engage.cloudflareclient.com:2408"

    fun buildWarpConfigString(
      privateKeyBase64: String,
      peerPublicKeyBase64: String,
      clientIpv4: String,
      endpoint: String,
      dnsServers: String = "1.1.1.1, 1.0.0.1",
    ): String {
      val cleanAddress =
        if (clientIpv4.contains('/')) clientIpv4.trim()
        else "${clientIpv4.trim()}/32"
      return """
        |[Interface]
        |PrivateKey = ${privateKeyBase64.trim()}
        |Address = $cleanAddress
        |DNS = $dnsServers
        |MTU = 1280
        |
        |[Peer]
        |PublicKey = ${peerPublicKeyBase64.trim()}
        |AllowedIPs = 0.0.0.0/0
        |Endpoint = ${endpoint.trim()}
        |PersistentKeepalive = 25
      """.trimMargin()
    }

    fun parseEndpointFromConf(rawConf: String): String {
      rawConf.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("Endpoint", ignoreCase = true) && trimmed.contains('=')) {
          return trimmed.substringAfter('=').trim()
        }
      }
      return ""
    }

    fun hasValidWireGuardSections(rawConf: String): Boolean {
      val hasInterface = rawConf.contains("[Interface]", ignoreCase = true)
      val hasPeer = rawConf.contains("[Peer]", ignoreCase = true)
      val hasPrivateKey = rawConf.contains("PrivateKey", ignoreCase = true)
      val hasPublicKey = rawConf.contains("PublicKey", ignoreCase = true)
      return hasInterface && hasPeer && hasPrivateKey && hasPublicKey
    }
  }
}
