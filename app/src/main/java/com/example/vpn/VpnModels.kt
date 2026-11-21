package com.example.vpn

enum class DohProvider(
  val displayName: String,
  val subtitle: String,
  val endpointUrl: String,
) {
  CLOUDFLARE(
    displayName = "Cloudflare 1.1.1.1",
    subtitle = "Ultra-fast private DNS",
    endpointUrl = "https://cloudflare-dns.com/dns-query",
  ),
  CLOUDFLARE_SECURITY(
    displayName = "Cloudflare Malware Block",
    subtitle = "Blocks phishing & malware domains",
    endpointUrl = "https://security.cloudflare-dns.com/dns-query",
  ),
  ADGUARD(
    displayName = "AdGuard Public DNS",
    subtitle = "Blocks ads & trackers at DNS level",
    endpointUrl = "https://dns.adguard-dns.com/dns-query",
  ),
  QUAD9(
    displayName = "Quad9 Security",
    subtitle = "Threat intelligence DNS shield",
    endpointUrl = "https://dns.quad9.net/dns-query",
  ),
}

data class PublicRelayNode(
  val ip: String,
  val port: Int,
  val country: String,
  val countryCode: String,
  val protocol: String,
  val anonymity: String,
  val source: String = "Public",
  val latencyMs: Long? = null,
  val isReachable: Boolean? = null,
) {
  val id: String get() = "$ip:$port"
}

data class VpnTunnelState(
  val isDnsVpnActive: Boolean = false,
  val isDnsVpnConnecting: Boolean = false,
  val activeDohProvider: DohProvider = DohProvider.CLOUDFLARE,
  val queriesEncrypted: Long = 0L,
  val lastDnsLatencyMs: Long = 0L,
  val activeRelay: PublicRelayNode? = null,
  val isConnectingRelay: Boolean = false,
  val availableRelays: List<PublicRelayNode> = emptyList(),
  val isFetchingRelays: Boolean = false,
  val autoFailoversCount: Int = 0,
  val currentExitIp: String = "Checking...",
  val currentExitCountry: String = "",
  val statusMessage: String? = null,
) {
  val isAutoProxyActive: Boolean
    get() = activeRelay != null

  val isAnyProtectionActive: Boolean
    get() = isAutoProxyActive || isDnsVpnActive
}

data class ParsedDnsPacket(
  val srcIp: ByteArray,
  val dstIp: ByteArray,
  val srcPort: Int,
  val dstPort: Int,
  val dnsPayload: ByteArray,
)

object DnsPacketCodec {

  fun parseIpv4UdpDnsQuery(packet: ByteArray, length: Int): ParsedDnsPacket? {
    if (length < 28) return null
    val version = (packet[0].toInt() ushr 4) and 0x0F
    if (version != 4) return null
    val ihl = (packet[0].toInt() and 0x0F) * 4
    if (ihl < 20 || length < ihl + 8) return null
    val protocol = packet[9].toInt() and 0xFF
    if (protocol != 17) return null // UDP only

    val srcIp = packet.copyOfRange(12, 16)
    val dstIp = packet.copyOfRange(16, 20)
    val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
    val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
    if (dstPort != 53) return null

    val udpLen = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
    if (udpLen < 8) return null
    val payloadEnd = minOf(length, ihl + udpLen)
    if (payloadEnd <= ihl + 8) return null

    val dnsPayload = packet.copyOfRange(ihl + 8, payloadEnd)
    return ParsedDnsPacket(
      srcIp = srcIp,
      dstIp = dstIp,
      srcPort = srcPort,
      dstPort = dstPort,
      dnsPayload = dnsPayload,
    )
  }

  fun buildIpv4UdpDnsResponse(query: ParsedDnsPacket, dnsResponse: ByteArray): ByteArray {
    val ipHeaderLen = 20
    val udpHeaderLen = 8
    val udpLen = udpHeaderLen + dnsResponse.size
    val totalLen = ipHeaderLen + udpLen
    val out = ByteArray(totalLen)

    // IPv4 header
    out[0] = 0x45.toByte() // Version 4, IHL 5
    out[1] = 0x00
    out[2] = ((totalLen ushr 8) and 0xFF).toByte()
    out[3] = (totalLen and 0xFF).toByte()
    out[4] = 0x00
    out[5] = 0x01
    out[6] = 0x40.toByte() // Don't fragment
    out[7] = 0x00
    out[8] = 64.toByte() // TTL
    out[9] = 17.toByte() // UDP
    // Checksum [10..11] filled below

    // Swap src and dst IP
    System.arraycopy(query.dstIp, 0, out, 12, 4)
    System.arraycopy(query.srcIp, 0, out, 16, 4)

    val ipChecksum = computeChecksum(out, 0, ipHeaderLen)
    out[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
    out[11] = (ipChecksum and 0xFF).toByte()

    // UDP header
    out[20] = ((query.dstPort ushr 8) and 0xFF).toByte()
    out[21] = (query.dstPort and 0xFF).toByte()
    out[22] = ((query.srcPort ushr 8) and 0xFF).toByte()
    out[23] = (query.srcPort and 0xFF).toByte()
    out[24] = ((udpLen ushr 8) and 0xFF).toByte()
    out[25] = (udpLen and 0xFF).toByte()
    // UDP checksum [26..27] filled below

    System.arraycopy(dnsResponse, 0, out, 28, dnsResponse.size)

    val udpChecksum = computeUdpChecksum(out, query.dstIp, query.srcIp, udpLen)
    out[26] = ((udpChecksum ushr 8) and 0xFF).toByte()
    out[27] = (udpChecksum and 0xFF).toByte()

    return out
  }

  private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
    var sum = 0L
    var i = offset
    val end = offset + length
    while (i + 1 < end) {
      val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
      sum += word
      i += 2
    }
    if (i < end) {
      sum += (data[i].toInt() and 0xFF) shl 8
    }
    while ((sum ushr 16) != 0L) {
      sum = (sum and 0xFFFF) + (sum ushr 16)
    }
    return (sum.inv() and 0xFFFF).toInt()
  }

  private fun computeUdpChecksum(
    packet: ByteArray,
    srcIp: ByteArray,
    dstIp: ByteArray,
    udpLen: Int,
  ): Int {
    var sum = 0L
    // Pseudo-header: srcIp + dstIp + 0x0011 + udpLen
    for (i in 0 until 4 step 2) {
      sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
      sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
    }
    sum += 17L // UDP protocol
    sum += udpLen.toLong()

    // UDP header + payload (skip checksum bytes 26..27)
    var i = 20
    val end = 20 + udpLen
    while (i + 1 < end) {
      val word =
        if (i == 26) 0 else ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
      sum += word
      i += 2
    }
    if (i < end) {
      sum += (packet[i].toInt() and 0xFF) shl 8
    }
    while ((sum ushr 16) != 0L) {
      sum = (sum and 0xFFFF) + (sum ushr 16)
    }
    val checksum = (sum.inv() and 0xFFFF).toInt()
    return if (checksum == 0) 0xFFFF else checksum
  }
}
