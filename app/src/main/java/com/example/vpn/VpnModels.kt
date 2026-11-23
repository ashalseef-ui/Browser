package com.example.vpn

enum class DohProvider(
  val displayName: String,
  val subtitle: String,
  val endpointUrl: String,
) {
  CLOUDFLARE(
    displayName = "Cloudflare 1.1.1.1",
    subtitle = "Ultra-fast global private DNS",
    endpointUrl = "https://cloudflare-dns.com/dns-query",
  ),
  CLOUDFLARE_SECURITY(
    displayName = "Cloudflare Malware Shield",
    subtitle = "Blocks phishing & malware domains",
    endpointUrl = "https://security.cloudflare-dns.com/dns-query",
  ),
  MULLVAD_ADBLOCK(
    displayName = "Mullvad AdBlock DNS",
    subtitle = "No-logs DNS blocking ads & trackers",
    endpointUrl = "https://adblock.dns.mullvad.net/dns-query",
  ),
  MULLVAD(
    displayName = "Mullvad Unfiltered",
    subtitle = "No-logs uncensored privacy DNS",
    endpointUrl = "https://dns.mullvad.net/dns-query",
  ),
  ADGUARD(
    displayName = "AdGuard Public DNS",
    subtitle = "Blocks ads & tracking at DNS level",
    endpointUrl = "https://dns.adguard-dns.com/dns-query",
  ),
  CONTROLD_ADBLOCK(
    displayName = "Control D Anti-Tracker",
    subtitle = "Blocks ads, trackers & malware",
    endpointUrl = "https://freedns.controld.com/p2",
  ),
  CONTROLD_UNFILTERED(
    displayName = "Control D Unfiltered",
    subtitle = "Fast uncensored anycast DNS",
    endpointUrl = "https://freedns.controld.com/p0",
  ),
  QUAD9(
    displayName = "Quad9 Security",
    subtitle = "Threat intelligence DNS shield",
    endpointUrl = "https://dns.quad9.net/dns-query",
  ),
  DNS_SB(
    displayName = "DNS.SB Privacy",
    subtitle = "No-logs uncensored European DNS",
    endpointUrl = "https://doh.dns.sb/dns-query",
  ),
}

enum class ProxyProtocolFilter(val label: String) {
  ALL("All"),
  HTTP("HTTP/S"),
  SOCKS5("SOCKS5"),
}

enum class WireGuardMode {
  NONE,
  WARP_FREE,
  CUSTOM_CONF,
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
  val resolvedExitIp: String? = null,
  val username: String? = null,
  val password: String? = null,
) {
  val id: String get() = "$ip:$port"

  val hasAuthentication: Boolean
    get() = !username.isNullOrBlank()

  val isPreferredRegion: Boolean
    get() = countryCode.equals("DE", ignoreCase = true) || countryCode.equals("IN", ignoreCase = true)
}

data class VpnTunnelState(
  val isWireGuardActive: Boolean = false,
  val isWireGuardConnecting: Boolean = false,
  val wireGuardMode: WireGuardMode = WireGuardMode.NONE,
  val wireGuardProfileName: String = "",
  val wireGuardEndpoint: String = "",
  val wireGuardRxBytes: Long = 0L,
  val wireGuardTxBytes: Long = 0L,
  val hasSavedWarpConfig: Boolean = false,
  val hasSavedCustomWgConf: Boolean = false,
  val isDnsVpnActive: Boolean = false,
  val isDnsVpnConnecting: Boolean = false,
  val activeDohProvider: DohProvider = DohProvider.CLOUDFLARE,
  val queriesEncrypted: Long = 0L,
  val lastDnsLatencyMs: Long = 0L,
  val activeRelay: PublicRelayNode? = null,
  val isConnectingRelay: Boolean = false,
  val availableRelays: List<PublicRelayNode> = emptyList(),
  val totalCandidatesScanned: Int = 0,
  val isFetchingRelays: Boolean = false,
  val autoFailoversCount: Int = 0,
  val selectedProtocolFilter: ProxyProtocolFilter = ProxyProtocolFilter.ALL,
  val selectedCountryFilter: String = "ALL",
  val currentExitIp: String = "Checking...",
  val currentExitCountry: String = "",
  val statusMessage: String? = null,
) {
  val isAutoProxyActive: Boolean
    get() = activeRelay != null

  val isAnyProtectionActive: Boolean
    get() = isWireGuardActive || isAutoProxyActive || isDnsVpnActive
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
    val fragField = ((packet[6].toInt() and 0x3F) shl 8) or (packet[7].toInt() and 0xFF)
    if (fragField != 0) return null // Reject fragmented IPv4 packets
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
    if (payloadEnd - (ihl + 8) < 12) return null

    val dnsPayload = packet.copyOfRange(ihl + 8, payloadEnd)
    // QR bit (bit 7 of byte 2) must be 0 for DNS queries
    if ((dnsPayload[2].toInt() and 0x80) != 0) return null

    return ParsedDnsPacket(
      srcIp = srcIp,
      dstIp = dstIp,
      srcPort = srcPort,
      dstPort = dstPort,
      dnsPayload = dnsPayload,
    )
  }

  fun extractNormalizedQuestionKey(dnsQuery: ByteArray): String? {
    if (dnsQuery.size <= 12) return null
    var pos = 12
    val labels = StringBuilder()
    var steps = 0
    while (pos < dnsQuery.size && steps < 128) {
      val len = dnsQuery[pos].toInt() and 0xFF
      if (len == 0) {
        pos += 1
        break
      }
      if ((len and 0xC0) != 0 || pos + 1 + len > dnsQuery.size) {
        return hexFallbackKey(dnsQuery)
      }
      for (i in 1..len) {
        val ch = (dnsQuery[pos + i].toInt() and 0xFF).toChar().lowercaseChar()
        labels.append(ch)
      }
      labels.append('.')
      pos += 1 + len
      steps++
    }
    if (pos + 4 > dnsQuery.size) {
      return hexFallbackKey(dnsQuery)
    }
    val qtype = ((dnsQuery[pos].toInt() and 0xFF) shl 8) or (dnsQuery[pos + 1].toInt() and 0xFF)
    val qclass = ((dnsQuery[pos + 2].toInt() and 0xFF) shl 8) or (dnsQuery[pos + 3].toInt() and 0xFF)
    return "${labels}|$qtype|$qclass"
  }

  private fun hexFallbackKey(dnsQuery: ByteArray): String {
    val sb = StringBuilder((dnsQuery.size - 12) * 2)
    for (i in 12 until dnsQuery.size) {
      val b = dnsQuery[i].toInt() and 0xFF
      sb.append(HEX_CHARS[b ushr 4]).append(HEX_CHARS[b and 0x0F])
    }
    return sb.toString()
  }

  fun extractRcode(dnsResponse: ByteArray): Int {
    if (dnsResponse.size < 4) return -1
    return dnsResponse[3].toInt() and 0x0F
  }

  fun extractMinAnswerTtlSeconds(dnsResponse: ByteArray): Long? {
    if (dnsResponse.size < 12) return null
    val qdCount = ((dnsResponse[4].toInt() and 0xFF) shl 8) or (dnsResponse[5].toInt() and 0xFF)
    val anCount = ((dnsResponse[6].toInt() and 0xFF) shl 8) or (dnsResponse[7].toInt() and 0xFF)
    if (anCount <= 0 || qdCount > 16 || anCount > 64) return null

    var pos = 12
    for (q in 0 until qdCount) {
      pos = skipDnsName(dnsResponse, pos) ?: return null
      pos += 4 // QTYPE + QCLASS
      if (pos > dnsResponse.size) return null
    }

    var minTtl: Long? = null
    for (a in 0 until anCount) {
      pos = skipDnsName(dnsResponse, pos) ?: break
      if (pos + 10 > dnsResponse.size) break
      val ttl =
        ((dnsResponse[pos + 4].toLong() and 0xFF) shl 24) or
          ((dnsResponse[pos + 5].toLong() and 0xFF) shl 16) or
          ((dnsResponse[pos + 6].toLong() and 0xFF) shl 8) or
          (dnsResponse[pos + 7].toLong() and 0xFF)
      val rdLen = ((dnsResponse[pos + 8].toInt() and 0xFF) shl 8) or (dnsResponse[pos + 9].toInt() and 0xFF)
      pos += 10 + rdLen
      if (pos > dnsResponse.size) break
      minTtl = if (minTtl == null) ttl else minOf(minTtl, ttl)
    }
    return minTtl?.coerceIn(5L, 3600L)
  }

  private fun skipDnsName(packet: ByteArray, startOffset: Int): Int? {
    var pos = startOffset
    var steps = 0
    while (pos < packet.size && steps < 128) {
      val len = packet[pos].toInt() and 0xFF
      when {
        len == 0 -> return pos + 1
        (len and 0xC0) == 0xC0 -> return if (pos + 1 < packet.size) pos + 2 else null
        (len and 0xC0) != 0 -> return null
        else -> {
          pos += 1 + len
          if (pos > packet.size) return null
        }
      }
      steps++
    }
    return null
  }

  private const val HEX_CHARS = "0123456789abcdef"

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
    for (i in 0 until 4 step 2) {
      sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
      sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
    }
    sum += 17L // UDP protocol
    sum += udpLen.toLong()

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
