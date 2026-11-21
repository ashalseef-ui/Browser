package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class VpnServiceRuntimeStats(
  val isRunning: Boolean = false,
  val activeProvider: DohProvider = DohProvider.CLOUDFLARE,
  val queriesEncrypted: Long = 0L,
  val lastLatencyMs: Long = 0L,
  val errorMessage: String? = null,
)

class VoltVpnService : VpnService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var tunInterface: ParcelFileDescriptor? = null
  private var tunnelJob: Job? = null
  private var currentProvider: DohProvider = DohProvider.CLOUDFLARE

  private val protectedSocketFactory =
    object : SocketFactory() {
      override fun createSocket(): Socket = Socket().also { protect(it) }

      override fun createSocket(host: String, port: Int): Socket =
        Socket().also {
          protect(it)
          it.connect(InetSocketAddress(host, port), 8_000)
        }

      override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
      ): Socket =
        Socket().also {
          protect(it)
          it.bind(InetSocketAddress(localHost, localPort))
          it.connect(InetSocketAddress(host, port), 8_000)
        }

      override fun createSocket(host: InetAddress, port: Int): Socket =
        Socket().also {
          protect(it)
          it.connect(InetSocketAddress(host, port), 8_000)
        }

      override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
      ): Socket =
        Socket().also {
          protect(it)
          it.bind(InetSocketAddress(localAddress, localPort))
          it.connect(InetSocketAddress(address, port), 8_000)
        }
    }

  private val dohClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .socketFactory(protectedSocketFactory)
      .connectTimeout(6, TimeUnit.SECONDS)
      .readTimeout(6, TimeUnit.SECONDS)
      .build()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopTunnel()
      stopSelf()
      return START_NOT_STICKY
    }

    val providerName = intent?.getStringExtra(EXTRA_DOH_PROVIDER)
    currentProvider =
      DohProvider.entries.firstOrNull { it.name == providerName } ?: DohProvider.CLOUDFLARE

    startForegroundNotification()
    startTunnel()
    return START_STICKY
  }

  private fun startForegroundNotification() {
    val channelId = "volt_vpn_tunnel"
    val manager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          channelId,
          "Volt Encrypted VPN",
          NotificationManager.IMPORTANCE_LOW,
        )
      manager.createNotificationChannel(channel)
    }

    val openAppIntent =
      PendingIntent.getActivity(
        this,
        10,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val disconnectIntent =
      PendingIntent.getService(
        this,
        11,
        Intent(this, VoltVpnService::class.java).apply { action = ACTION_STOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification: Notification =
      NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Volt Encrypted VPN Active")
        .setContentText("DNS encrypted via ${currentProvider.displayName}")
        .setOngoing(true)
        .setContentIntent(openAppIntent)
        .addAction(0, "Disconnect", disconnectIntent)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun startTunnel() {
    stopTunnel()

    val builder =
      Builder()
        .setSession("Volt Encrypted VPN (${currentProvider.displayName})")
        .setMtu(1500)
        .addAddress(TUN_CLIENT_IP, 32)
        .addDnsServer(TUN_DNS_IP)
        .addRoute(TUN_DNS_IP, 32)
        .allowBypass()

    val pfd = builder.establish()
    if (pfd == null) {
      _runtimeStats.update {
        it.copy(isRunning = false, errorMessage = "VPN interface could not be established")
      }
      stopSelf()
      return
    }

    tunInterface = pfd
    _runtimeStats.update {
      it.copy(isRunning = true, activeProvider = currentProvider, errorMessage = null)
    }

    tunnelJob =
      serviceScope.launch {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val writeLock = Any()
        val packetBuffer = ByteArray(32767)

        while (isActive) {
          val readBytes =
            runCatching { input.read(packetBuffer) }.getOrElse { -1 }
          if (readBytes < 0) break
          if (readBytes == 0) continue

          val parsed = DnsPacketCodec.parseIpv4UdpDnsQuery(packetBuffer, readBytes) ?: continue
          launch {
            val started = System.currentTimeMillis()
            val responseBytes = forwardDnsOverHttps(parsed.dnsPayload)
            if (responseBytes != null) {
              val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
              val ipPacket = DnsPacketCodec.buildIpv4UdpDnsResponse(parsed, responseBytes)
              synchronized(writeLock) {
                runCatching {
                  output.write(ipPacket)
                  output.flush()
                }
              }
              _runtimeStats.update { stats ->
                stats.copy(
                  queriesEncrypted = stats.queriesEncrypted + 1,
                  lastLatencyMs = elapsed,
                )
              }
            }
          }
        }
      }
  }

  private fun forwardDnsOverHttps(dnsQuery: ByteArray): ByteArray? {
    val mediaType = "application/dns-message".toMediaType()
    val request =
      Request.Builder()
        .url(currentProvider.endpointUrl)
        .header("Accept", "application/dns-message")
        .post(dnsQuery.toRequestBody(mediaType))
        .build()

    return runCatching {
      dohClient.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body?.bytes() else null
      }
    }.getOrNull()
  }

  private fun stopTunnel() {
    tunnelJob?.cancel()
    tunnelJob = null
    runCatching { tunInterface?.close() }
    tunInterface = null
    _runtimeStats.update { it.copy(isRunning = false) }
  }

  override fun onRevoke() {
    stopTunnel()
    stopSelf()
    super.onRevoke()
  }

  override fun onDestroy() {
    stopTunnel()
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    const val ACTION_START = "com.example.vpn.ACTION_START"
    const val ACTION_STOP = "com.example.vpn.ACTION_STOP"
    const val EXTRA_DOH_PROVIDER = "extra_doh_provider"

    private const val NOTIFICATION_ID = 4201
    private const val TUN_CLIENT_IP = "10.111.222.1"
    private const val TUN_DNS_IP = "10.111.222.2"

    private val _runtimeStats = MutableStateFlow(VpnServiceRuntimeStats())
    val runtimeStats: StateFlow<VpnServiceRuntimeStats> = _runtimeStats.asStateFlow()
  }
}
