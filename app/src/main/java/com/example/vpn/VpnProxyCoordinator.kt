package com.example.vpn

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.example.browser.VoltSpeedEngine
import com.example.data.BrowserRepository
import com.example.data.BrowserSettings
import com.example.data.BrowserTabState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VpnProxyCoordinator(
  private val app: Application,
  private val repository: BrowserRepository,
  private val speedEngine: VoltSpeedEngine,
  private val scope: CoroutineScope,
  private val settingsFlow: StateFlow<BrowserSettings>,
  private val activeTabProvider: () -> BrowserTabState,
  private val onReloadActiveTab: () -> Unit,
  private val onStopAllWebViewLoads: () -> Unit,
  private val onKillSwitchBlockingChanged: (Boolean) -> Unit,
  private val onShowMessage: (String) -> Unit,
) {
  val relayManager = PublicRelayManager()
  private val wireGuardManager = WireGuardManager(app)
  private var wireGuardStatsJob: Job? = null
  private val skippedRelayIds = mutableSetOf<String>()
  private var lastFailoverTimestamp = 0L
  @Volatile private var expectedWireGuardDisconnect = false

  private val _vpnRelayState = MutableStateFlow(VpnTunnelState())
  val vpnState: StateFlow<VpnTunnelState> =
    combine(_vpnRelayState, VoltVpnService.runtimeStats) { local, runtime ->
        local.copy(
          isDnsVpnActive = runtime.isRunning,
          activeDohProvider = if (runtime.isRunning) runtime.activeProvider else local.activeDohProvider,
          queriesEncrypted = runtime.queriesEncrypted,
          lastDnsLatencyMs = runtime.lastLatencyMs,
          statusMessage = runtime.errorMessage ?: local.statusMessage,
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, VpnTunnelState())

  fun syncSavedConfigs(currentSettings: BrowserSettings) {
    val decryptFailed = currentSettings.warpConfigDecryptFailed || currentSettings.customWgConfigDecryptFailed
    _vpnRelayState.update { state ->
      state.copy(
        hasSavedWarpConfig = currentSettings.savedWarpConf.isNotBlank(),
        hasSavedCustomWgConf = currentSettings.savedCustomWgConf.isNotBlank(),
        warpConfigDecryptFailed = currentSettings.warpConfigDecryptFailed,
        customWgConfigDecryptFailed = currentSettings.customWgConfigDecryptFailed,
        statusMessage =
          if (decryptFailed && state.statusMessage == null) {
            "Saved WireGuard config could not be decrypted — please re-import"
          } else {
            state.statusMessage
          },
      )
    }
  }

  fun initializeOnStartup(initialSettings: BrowserSettings) {
    val decryptFailed = initialSettings.warpConfigDecryptFailed || initialSettings.customWgConfigDecryptFailed
    _vpnRelayState.update {
      it.copy(
        activeDohProvider = initialSettings.preferredDohProvider,
        hasSavedWarpConfig = initialSettings.savedWarpConf.isNotBlank(),
        hasSavedCustomWgConf = initialSettings.savedCustomWgConf.isNotBlank(),
        warpConfigDecryptFailed = initialSettings.warpConfigDecryptFailed,
        customWgConfigDecryptFailed = initialSettings.customWgConfigDecryptFailed,
        statusMessage =
          if (decryptFailed) "Saved WireGuard config could not be decrypted — please re-import"
          else it.statusMessage,
      )
    }

    var startedWireGuardOnBoot = false
    if (initialSettings.autoWireGuardOnLaunchEnabled && VpnService.prepare(app) == null) {
      when {
        initialSettings.preferredWireGuardMode == WireGuardMode.CUSTOM_CONF &&
          initialSettings.savedCustomWgConf.isNotBlank() -> {
          startedWireGuardOnBoot = true
          connectCustomWireGuardConf(
            rawConf = initialSettings.savedCustomWgConf,
            profileName = initialSettings.savedCustomWgName.ifBlank { "Custom WireGuard" },
            persistConfig = false,
          )
        }
        initialSettings.savedWarpConf.isNotBlank() -> {
          startedWireGuardOnBoot = true
          connectCloudflareWarp(silentStartup = true)
        }
      }
    }

    if (!startedWireGuardOnBoot && initialSettings.autoDnsVpnOnLaunchEnabled && VpnService.prepare(app) == null) {
      startDnsVpnService()
    }
    if (!startedWireGuardOnBoot && initialSettings.autoProxyOnLaunchEnabled) {
      autoConnectFastestRelay(fastStartup = true)
    }
  }

  fun handleMainFrameNetworkError(tabId: String, isProxyOrNetworkError: Boolean) {
    val relay = _vpnRelayState.value.activeRelay ?: return
    if (!settingsFlow.value.autoProxyFailoverEnabled || !isProxyOrNetworkError) return

    val now = System.currentTimeMillis()
    if (now - lastFailoverTimestamp < 5_000L || _vpnRelayState.value.isConnectingRelay) return
    lastFailoverTimestamp = now
    skippedRelayIds.add(relay.id)
    _vpnRelayState.update { it.copy(autoFailoversCount = it.autoFailoversCount + 1) }
    rotateToNextProxy(reloadActiveTab = tabId == activeTabProvider().id)
  }

  fun toggleAutoWireGuardOnLaunch(enabled: Boolean) {
    scope.launch { repository.preferences.setAutoWireGuardOnLaunch(enabled) }
  }

  fun toggleWireGuardKillSwitch(enabled: Boolean) {
    scope.launch { repository.preferences.setWireGuardKillSwitch(enabled) }
    if (!enabled && _vpnRelayState.value.isKillSwitchTripped) {
      resetKillSwitch()
    }
  }

  fun resetKillSwitch() {
    onKillSwitchBlockingChanged(false)
    speedEngine.setActiveProxy(null)
    if (_vpnRelayState.value.activeRelay == null) {
      relayManager.clearWebViewProxy {}
    }
    _vpnRelayState.update {
      it.copy(
        isKillSwitchTripped = false,
        statusMessage = "VPN Kill Switch cleared — direct browsing restored.",
      )
    }
    onShowMessage("Kill Switch cleared")
  }

  fun markPublicProxyWarned() {
    scope.launch { repository.preferences.setPublicProxyWarned(true) }
  }

  fun toggleAutoProxyOnLaunch(enabled: Boolean) {
    scope.launch { repository.preferences.setAutoProxyOnLaunch(enabled) }
    if (enabled && _vpnRelayState.value.activeRelay == null && !_vpnRelayState.value.isWireGuardActive) {
      autoConnectFastestRelay(fastStartup = true)
    }
  }

  fun toggleAutoProxyFailover(enabled: Boolean) {
    scope.launch { repository.preferences.setAutoProxyFailover(enabled) }
  }

  fun toggleAutoDnsVpnOnLaunch(enabled: Boolean) {
    scope.launch { repository.preferences.setAutoDnsVpnOnLaunch(enabled) }
  }

  fun setProxyProtocolFilter(filter: ProxyProtocolFilter) {
    _vpnRelayState.update { it.copy(selectedProtocolFilter = filter) }
  }

  fun setProxyCountryFilter(countryCode: String) {
    _vpnRelayState.update { it.copy(selectedCountryFilter = countryCode) }
  }

  fun connectCustomProxy(rawAddress: String, protocol: String) {
    val parsed = PublicRelayManager.parseSingleProxy(rawAddress, protocol)
    if (parsed == null) {
      onShowMessage("Enter ip:port or user:pass@ip:port (public IPv4)")
      return
    }
    connectRelay(parsed, reloadActiveTab = false)
  }

  fun connectCloudflareWarp(silentStartup: Boolean = false) {
    if (_vpnRelayState.value.isWireGuardConnecting) return
    scope.launch {
      if (VoltVpnService.runtimeStats.value.isRunning) {
        stopDnsVpn(silent = true)
      }
      if (_vpnRelayState.value.activeRelay != null) {
        relayManager.clearWebViewProxy {}
        speedEngine.setActiveProxy(null)
      }
      _vpnRelayState.update {
        it.copy(
          isWireGuardConnecting = true,
          activeRelay = null,
          statusMessage = "Connecting Cloudflare WARP (WireGuard)...",
        )
      }
      val existingConf = settingsFlow.value.savedWarpConf
      var warpSavedOnDisk = existingConf.isNotBlank()
      var cipherErrorMessage: String? = null
      val confResult =
        if (existingConf.isNotBlank()) {
          Result.success(existingConf)
        } else {
          wireGuardManager.registerCloudflareWarpConfig().also { result ->
            result.getOrNull()?.let { freshConf ->
              runCatching { repository.preferences.saveWarpConfig(freshConf) }
                .onSuccess { warpSavedOnDisk = true }
                .onFailure { err ->
                  warpSavedOnDisk = false
                  cipherErrorMessage =
                    "Android Keystore unavailable: WARP config could not be saved securely (${err.message ?: "cipher error"})"
                }
            }
          }
        }

      val confText = confResult.getOrElse { err ->
        _vpnRelayState.update {
          it.copy(
            isWireGuardConnecting = false,
            statusMessage = "WARP setup error: ${err.message ?: "Network unreachable"}",
          )
        }
        if (!silentStartup) onShowMessage("Could not register Cloudflare WARP right now")
        return@launch
      }

      if (existingConf.isNotBlank()) {
        repository.preferences.setPreferredWireGuardMode(WireGuardMode.WARP_FREE)
      }

      var connectResult = wireGuardManager.connect(confText, "Cloudflare WARP")
      if (connectResult.isFailure && existingConf.isNotBlank()) {
        val fresh = wireGuardManager.registerCloudflareWarpConfig().getOrNull()
        if (fresh != null) {
          runCatching { repository.preferences.saveWarpConfig(fresh) }
            .onSuccess { warpSavedOnDisk = true }
            .onFailure { err ->
              warpSavedOnDisk = false
              cipherErrorMessage =
                "Android Keystore unavailable: WARP config could not be saved securely (${err.message ?: "cipher error"})"
            }
          connectResult = wireGuardManager.connect(fresh, "Cloudflare WARP")
        }
      }

      connectResult.fold(
        onSuccess = { info ->
          expectedWireGuardDisconnect = false
          if (_vpnRelayState.value.isKillSwitchTripped) {
            onKillSwitchBlockingChanged(false)
            relayManager.clearWebViewProxy {}
            speedEngine.setActiveProxy(null)
          }
          _vpnRelayState.update {
            it.copy(
              isWireGuardActive = true,
              isWireGuardConnecting = false,
              isKillSwitchTripped = false,
              wireGuardMode = WireGuardMode.WARP_FREE,
              wireGuardProfileName = info.profileName,
              wireGuardEndpoint = info.endpoint,
              hasSavedWarpConfig = warpSavedOnDisk,
              warpConfigDecryptFailed = false,
              statusMessage =
                cipherErrorMessage ?: "Cloudflare WARP WireGuard active (${info.endpoint})",
            )
          }
          startWireGuardStatsPolling()
          delay(350L)
          refreshExitIp()
          if (cipherErrorMessage != null) {
            onShowMessage(cipherErrorMessage!!)
          } else if (!silentStartup) {
            onShowMessage("Cloudflare WARP WireGuard connected")
          }
        },
        onFailure = { err ->
          _vpnRelayState.update {
            it.copy(
              isWireGuardActive = false,
              isWireGuardConnecting = false,
              statusMessage =
                cipherErrorMessage ?: "WireGuard failed: ${err.message ?: "VPN permission needed"}",
            )
          }
          if (cipherErrorMessage != null) {
            onShowMessage(cipherErrorMessage!!)
          } else if (!silentStartup) {
            onShowMessage("Failed to start WireGuard tunnel")
          }
        },
      )
    }
  }

  fun connectSavedCustomWireGuard() {
    val saved = settingsFlow.value.savedCustomWgConf
    if (saved.isBlank()) {
      onShowMessage("Import or paste a WireGuard .conf first")
      return
    }
    val name = settingsFlow.value.savedCustomWgName.ifBlank { "Custom WireGuard" }
    connectCustomWireGuardConf(saved, name, persistConfig = false)
  }

  fun connectCustomWireGuardConf(
    rawConf: String,
    profileName: String = "Custom WireGuard",
    persistConfig: Boolean = true,
  ) {
    if (_vpnRelayState.value.isWireGuardConnecting) return
    val validation = wireGuardManager.parseConfig(rawConf)
    if (validation.isFailure) {
      val msg = validation.exceptionOrNull()?.message ?: "Invalid WireGuard .conf format"
      _vpnRelayState.update { it.copy(statusMessage = msg) }
      onShowMessage(msg)
      return
    }

    scope.launch {
      if (VoltVpnService.runtimeStats.value.isRunning) {
        stopDnsVpn(silent = true)
      }
      if (_vpnRelayState.value.activeRelay != null) {
        relayManager.clearWebViewProxy {}
        speedEngine.setActiveProxy(null)
      }
      _vpnRelayState.update {
        it.copy(
          isWireGuardConnecting = true,
          activeRelay = null,
          statusMessage = "Starting $profileName...",
        )
      }
      var customSavedOnDisk = settingsFlow.value.savedCustomWgConf.isNotBlank()
      var cipherErrorMessage: String? = null
      if (persistConfig) {
        runCatching {
          repository.preferences.saveCustomWireGuardConfig(rawConf.trim(), profileName.trim())
        }.onSuccess {
          customSavedOnDisk = true
        }.onFailure { err ->
          customSavedOnDisk = settingsFlow.value.savedCustomWgConf.isNotBlank()
          cipherErrorMessage =
            "Android Keystore unavailable: config could not be saved securely (${err.message ?: "cipher error"})"
        }
      } else {
        repository.preferences.setPreferredWireGuardMode(WireGuardMode.CUSTOM_CONF)
      }

      wireGuardManager.connect(rawConf, profileName).fold(
        onSuccess = { info ->
          expectedWireGuardDisconnect = false
          if (_vpnRelayState.value.isKillSwitchTripped) {
            onKillSwitchBlockingChanged(false)
            relayManager.clearWebViewProxy {}
            speedEngine.setActiveProxy(null)
          }
          _vpnRelayState.update {
            it.copy(
              isWireGuardActive = true,
              isWireGuardConnecting = false,
              isKillSwitchTripped = false,
              wireGuardMode = WireGuardMode.CUSTOM_CONF,
              wireGuardProfileName = info.profileName,
              wireGuardEndpoint = info.endpoint,
              hasSavedCustomWgConf = customSavedOnDisk,
              customWgConfigDecryptFailed = false,
              statusMessage = cipherErrorMessage ?: "$profileName active (${info.endpoint})",
            )
          }
          startWireGuardStatsPolling()
          delay(350L)
          refreshExitIp()
          onShowMessage(cipherErrorMessage ?: "$profileName connected")
        },
        onFailure = { err ->
          _vpnRelayState.update {
            it.copy(
              isWireGuardActive = false,
              isWireGuardConnecting = false,
              statusMessage =
                cipherErrorMessage ?: "WireGuard error: ${err.message ?: "Unable to connect"}",
            )
          }
          onShowMessage(cipherErrorMessage ?: "WireGuard error: ${err.message ?: "Unable to connect"}")
        },
      )
    }
  }

  fun disconnectWireGuard(silent: Boolean = false) {
    expectedWireGuardDisconnect = true
    stopWireGuardStatsPolling()
    scope.launch {
      wireGuardManager.disconnect()
      if (_vpnRelayState.value.isKillSwitchTripped) {
        onKillSwitchBlockingChanged(false)
        relayManager.clearWebViewProxy {}
        speedEngine.setActiveProxy(null)
      }
      _vpnRelayState.update {
        it.copy(
          isWireGuardActive = false,
          isWireGuardConnecting = false,
          isKillSwitchTripped = false,
          wireGuardMode = WireGuardMode.NONE,
          wireGuardProfileName = "",
          wireGuardEndpoint = "",
          wireGuardRxBytes = 0L,
          wireGuardTxBytes = 0L,
          statusMessage = if (silent) it.statusMessage else "WireGuard VPN disconnected.",
        )
      }
      if (!silent) {
        refreshExitIp()
        onShowMessage("WireGuard VPN disconnected")
      }
    }
  }

  private fun tripWireGuardKillSwitch() {
    onKillSwitchBlockingChanged(true)
    onStopAllWebViewLoads()
    relayManager.applyKillSwitchDeadProxy {}
    speedEngine.setActiveProxy(
      java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", 9))
    )
    _vpnRelayState.update {
      it.copy(
        isWireGuardActive = false,
        isKillSwitchTripped = true,
        wireGuardMode = WireGuardMode.NONE,
        statusMessage = "VPN tunnel dropped — Kill Switch blocking all browser traffic.",
      )
    }
    onShowMessage("VPN dropped! Kill Switch blocking traffic")
  }

  private fun startWireGuardStatsPolling() {
    wireGuardStatsJob?.cancel()
    wireGuardStatsJob =
      scope.launch {
        while (true) {
          val up = wireGuardManager.isTunnelActiveNow()
          if (!up) {
            val unexpectedDrop = !expectedWireGuardDisconnect && _vpnRelayState.value.isWireGuardActive
            if (unexpectedDrop && settingsFlow.value.wireGuardKillSwitchEnabled) {
              tripWireGuardKillSwitch()
            } else {
              _vpnRelayState.update {
                if (it.isWireGuardActive) {
                  it.copy(
                    isWireGuardActive = false,
                    wireGuardMode = WireGuardMode.NONE,
                    statusMessage = "WireGuard VPN disconnected.",
                  )
                } else {
                  it
                }
              }
            }
            break
          }
          val (rx, tx) = wireGuardManager.readStatistics()
          _vpnRelayState.update {
            it.copy(
              wireGuardRxBytes = rx,
              wireGuardTxBytes = tx,
            )
          }
          delay(1500L)
        }
      }
  }

  fun stopWireGuardStatsPolling() {
    wireGuardStatsJob?.cancel()
    wireGuardStatsJob = null
  }

  fun toggleFreeAutoProxy() {
    if (_vpnRelayState.value.activeRelay != null) {
      disconnectRelay()
    } else {
      autoConnectFastestRelay()
    }
  }

  fun prepareVpnConsentIntent(): Intent? = VpnService.prepare(app)

  fun startDnsVpnService() {
    scope.launch {
      if (_vpnRelayState.value.isWireGuardActive) {
        expectedWireGuardDisconnect = true
        stopWireGuardStatsPolling()
        wireGuardManager.disconnect()
        _vpnRelayState.update {
          it.copy(
            isWireGuardActive = false,
            wireGuardMode = WireGuardMode.NONE,
            wireGuardProfileName = "",
            wireGuardEndpoint = "",
            wireGuardRxBytes = 0L,
            wireGuardTxBytes = 0L,
          )
        }
      }
      if (_vpnRelayState.value.isKillSwitchTripped) {
        onKillSwitchBlockingChanged(false)
        relayManager.clearWebViewProxy {}
        speedEngine.setActiveProxy(null)
        _vpnRelayState.update { it.copy(isKillSwitchTripped = false) }
      }
      val provider = _vpnRelayState.value.activeDohProvider
      val intent =
        Intent(app, VoltVpnService::class.java).apply {
          action = VoltVpnService.ACTION_START
          putExtra(VoltVpnService.EXTRA_DOH_PROVIDER, provider.name)
        }
      ContextCompat.startForegroundService(app, intent)
      onShowMessage("Encrypted DNS VPN active (${provider.displayName})")
    }
  }

  fun stopDnsVpn(silent: Boolean = false) {
    val intent =
      Intent(app, VoltVpnService::class.java).apply {
        action = VoltVpnService.ACTION_STOP
      }
    app.startService(intent)
    if (!silent) onShowMessage("Encrypted DNS VPN disconnected")
  }

  fun selectDohProvider(provider: DohProvider) {
    _vpnRelayState.update { it.copy(activeDohProvider = provider) }
    scope.launch { repository.preferences.setPreferredDohProvider(provider) }
    if (VoltVpnService.runtimeStats.value.isRunning) {
      startDnsVpnService()
    }
  }

  fun refreshExitIp() {
    val relay = _vpnRelayState.value.activeRelay
    scope.launch {
      val info = relayManager.fetchCurrentExitIp(relay)
      _vpnRelayState.update {
        it.copy(
          currentExitIp = info?.ip ?: "Unavailable",
          currentExitCountry = info?.country.orEmpty(),
        )
      }
    }
  }

  fun refreshPublicRelays() {
    if (_vpnRelayState.value.isFetchingRelays) return
    _vpnRelayState.update {
      it.copy(
        isFetchingRelays = true,
        statusMessage = "Scanning 9 global feeds & benchmarking HTTPS/SOCKS5 proxies...",
      )
    }
    scope.launch {
      val summary = relayManager.fetchAndProbePublicRelays(excludeIds = skippedRelayIds)
      val relays = summary.relays
      val reachableCount = relays.count { it.isReachable == true }
      _vpnRelayState.update {
        it.copy(
          availableRelays = relays,
          totalCandidatesScanned = summary.totalCandidatesScanned,
          isFetchingRelays = false,
          statusMessage =
            when {
              relays.isEmpty() -> "Could not reach public proxy feeds right now."
              reachableCount == 0 ->
                "Scanned ${summary.totalCandidatesScanned} proxies; none responded cleanly. Tap Refresh to try a fresh batch."
              else ->
                "$reachableCount live relays verified from ${summary.totalCandidatesScanned} scanned."
            },
        )
      }
    }
  }

  fun autoConnectFastestRelay(reloadActiveTab: Boolean = false, fastStartup: Boolean = false) {
    if (_vpnRelayState.value.isFetchingRelays || _vpnRelayState.value.isConnectingRelay) return
    scope.launch {
      val stateSnapshot = _vpnRelayState.value
      val existingCandidates =
        filterRelays(
          relays = stateSnapshot.availableRelays,
          protocolFilter = stateSnapshot.selectedProtocolFilter,
          countryFilter = stateSnapshot.selectedCountryFilter,
        ).filter { it.isReachable == true && it.id !in skippedRelayIds }

      val candidates =
        if (existingCandidates.isNotEmpty()) {
          existingCandidates
        } else {
          _vpnRelayState.update {
            it.copy(
              isFetchingRelays = true,
              statusMessage =
                if (fastStartup) "Auto-loading fastest free proxy on startup..."
                else "Scanning global feeds & benchmarking fastest free proxies...",
            )
          }
          val summary =
            if (fastStartup) relayManager.findFastestStartupBatch(excludeIds = skippedRelayIds)
            else relayManager.fetchAndProbePublicRelays(excludeIds = skippedRelayIds)
          _vpnRelayState.update {
            it.copy(
              availableRelays = summary.relays,
              totalCandidatesScanned = summary.totalCandidatesScanned,
              isFetchingRelays = false,
            )
          }
          val filtered =
            filterRelays(
              relays = summary.relays,
              protocolFilter = _vpnRelayState.value.selectedProtocolFilter,
              countryFilter = _vpnRelayState.value.selectedCountryFilter,
            ).filter { it.isReachable == true && it.id !in skippedRelayIds }
          filtered.ifEmpty {
            summary.relays.filter { it.isReachable == true && it.id !in skippedRelayIds }
          }
        }

      val ordered = candidates.sortedWith(PublicRelayManager.relayRankingComparator)
      val selected =
        if (fastStartup) {
          val fastDeOrIn = ordered.firstOrNull { it.isPreferredRegion && (it.latencyMs ?: Long.MAX_VALUE) <= 1200L }
          fastDeOrIn ?: ordered.firstOrNull { (it.latencyMs ?: Long.MAX_VALUE) <= 1200L }
        } else {
          ordered.firstOrNull()
        }

      if (selected != null) {
        connectRelay(selected, reloadActiveTab = reloadActiveTab)
      } else {
        skippedRelayIds.clear()
        _vpnRelayState.update {
          it.copy(
            statusMessage =
              if (fastStartup && ordered.isNotEmpty())
                "Public proxies too slow right now (>1.2s) — staying direct for max speed."
              else
                "No responsive free proxies found in this batch. Tap Auto-Connect to retry.",
          )
        }
      }
    }
  }

  private fun filterRelays(
    relays: List<PublicRelayNode>,
    protocolFilter: ProxyProtocolFilter,
    countryFilter: String,
  ): List<PublicRelayNode> =
    relays.filter { relay ->
      val protoOk =
        when (protocolFilter) {
          ProxyProtocolFilter.ALL -> true
          ProxyProtocolFilter.HTTP -> relay.protocol.equals("HTTP", ignoreCase = true)
          ProxyProtocolFilter.SOCKS5 -> relay.protocol.equals("SOCKS5", ignoreCase = true)
        }
      val countryOk =
        countryFilter == "ALL" || relay.countryCode.equals(countryFilter, ignoreCase = true)
      protoOk && countryOk
    }

  fun rotateToNextProxy(reloadActiveTab: Boolean = false) {
    _vpnRelayState.value.activeRelay?.let { skippedRelayIds.add(it.id) }
    autoConnectFastestRelay(reloadActiveTab = reloadActiveTab)
  }

  fun connectRelay(relay: PublicRelayNode, reloadActiveTab: Boolean = false) {
    _vpnRelayState.update {
      it.copy(
        isConnectingRelay = true,
        statusMessage = "Connecting auto proxy ${relay.country} (${relay.protocol})...",
      )
    }
    relayManager.applyWebViewProxy(relay) { applied ->
      scope.launch {
        if (!applied) {
          _vpnRelayState.update {
            it.copy(
              isConnectingRelay = false,
              statusMessage = "WebView proxy override not supported on this device.",
            )
          }
          return@launch
        }
        speedEngine.setActiveProxy(
          proxy = PublicRelayManager.toJavaNetProxy(relay),
          username = relay.username,
          password = relay.password,
        )
        val ipInfo =
          if (relay.resolvedExitIp != null) null
          else relayManager.fetchCurrentExitIp(relay)
        val resolvedIp = relay.resolvedExitIp ?: ipInfo?.ip ?: relay.ip
        val resolvedCountry = ipInfo?.country?.ifBlank { relay.country } ?: relay.country
        val updatedRelay = relay.copy(country = resolvedCountry, resolvedExitIp = resolvedIp)
        _vpnRelayState.update {
          it.copy(
            activeRelay = updatedRelay,
            isConnectingRelay = false,
            currentExitIp = resolvedIp,
            currentExitCountry = resolvedCountry,
            statusMessage =
              "Optional Proxy active: $resolvedCountry (${relay.protocol} • ${relay.latencyMs ?: 0}ms)",
          )
        }
        if (reloadActiveTab && !activeTabProvider().isSpeedDial) {
          onReloadActiveTab()
        }
        onShowMessage("Proxy connected ($resolvedCountry)")
      }
    }
  }

  fun disconnectRelay() {
    relayManager.clearWebViewProxy {
      speedEngine.setActiveProxy(null)
      scope.launch {
        _vpnRelayState.update {
          it.copy(
            activeRelay = null,
            isConnectingRelay = false,
            statusMessage = "Optional Proxy disconnected (direct connection).",
          )
        }
        refreshExitIp()
        onShowMessage("Proxy disconnected")
      }
    }
  }

  fun shutdownAndWipeState() {
    expectedWireGuardDisconnect = true
    stopWireGuardStatsPolling()
    onKillSwitchBlockingChanged(false)
    if (VoltVpnService.runtimeStats.value.isRunning) {
      runCatching {
        app.startService(Intent(app, VoltVpnService::class.java).apply { action = VoltVpnService.ACTION_STOP })
      }
      runCatching { app.stopService(Intent(app, VoltVpnService::class.java)) }
    }
    speedEngine.setActiveProxy(null)
    relayManager.clearWebViewProxy {}
    skippedRelayIds.clear()
    _vpnRelayState.value = VpnTunnelState()
  }

  suspend fun disconnectWireGuardBlocking() {
    expectedWireGuardDisconnect = true
    runCatching { wireGuardManager.disconnect() }
  }
}
