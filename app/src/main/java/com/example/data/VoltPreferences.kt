package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.voltDataStore by preferencesDataStore(name = "volt_browser_prefs")

class VoltPreferences(private val context: Context) {

  private object Keys {
    val TURBO_SHIELD = booleanPreferencesKey("turbo_shield_enabled")
    val LITE_MODE = booleanPreferencesKey("lite_mode_enabled")
    val DARK_WEB = booleanPreferencesKey("dark_web_enabled")
    val DESKTOP_MODE = booleanPreferencesKey("desktop_mode_enabled")
    val AUTO_PROXY_ON_LAUNCH = booleanPreferencesKey("auto_proxy_on_launch")
    val AUTO_PROXY_FAILOVER = booleanPreferencesKey("auto_proxy_failover")
    val SEARCH_ENGINE = stringPreferencesKey("search_engine")
    val TRACKERS_BLOCKED = longPreferencesKey("cumulative_trackers_blocked")
    val BYTES_SAVED = longPreferencesKey("cumulative_bytes_saved")
    val AVG_LOAD_MS = longPreferencesKey("avg_load_time_ms")
    val MEASURED_PAGES = intPreferencesKey("measured_page_count")
  }

  val settings: Flow<BrowserSettings> =
    context.voltDataStore.data.map { prefs ->
      val engineName = prefs[Keys.SEARCH_ENGINE] ?: SearchEngine.DUCKDUCKGO.name
      val engine =
        SearchEngine.entries.firstOrNull { it.name == engineName } ?: SearchEngine.DUCKDUCKGO
      BrowserSettings(
        turboShieldEnabled = prefs[Keys.TURBO_SHIELD] ?: true,
        liteModeEnabled = prefs[Keys.LITE_MODE] ?: false,
        darkWebEnabled = prefs[Keys.DARK_WEB] ?: true,
        desktopModeEnabled = prefs[Keys.DESKTOP_MODE] ?: false,
        autoProxyOnLaunchEnabled = prefs[Keys.AUTO_PROXY_ON_LAUNCH] ?: false,
        autoProxyFailoverEnabled = prefs[Keys.AUTO_PROXY_FAILOVER] ?: true,
        searchEngine = engine,
        cumulativeTrackersBlocked = prefs[Keys.TRACKERS_BLOCKED] ?: 0L,
        cumulativeBytesSaved = prefs[Keys.BYTES_SAVED] ?: 0L,
        avgLoadTimeMs = prefs[Keys.AVG_LOAD_MS] ?: 0L,
        measuredPageCount = prefs[Keys.MEASURED_PAGES] ?: 0,
      )
    }

  suspend fun setTurboShield(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.TURBO_SHIELD] = enabled }
  }

  suspend fun setLiteMode(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.LITE_MODE] = enabled }
  }

  suspend fun setDarkWeb(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.DARK_WEB] = enabled }
  }

  suspend fun setDesktopMode(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.DESKTOP_MODE] = enabled }
  }

  suspend fun setAutoProxyOnLaunch(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.AUTO_PROXY_ON_LAUNCH] = enabled }
  }

  suspend fun setAutoProxyFailover(enabled: Boolean) {
    context.voltDataStore.edit { it[Keys.AUTO_PROXY_FAILOVER] = enabled }
  }

  suspend fun setSearchEngine(engine: SearchEngine) {
    context.voltDataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
  }

  suspend fun recordTelemetry(trackersBlocked: Int, bytesSaved: Long, loadTimeMs: Long) {
    context.voltDataStore.edit { prefs ->
      if (trackersBlocked > 0) {
        prefs[Keys.TRACKERS_BLOCKED] = (prefs[Keys.TRACKERS_BLOCKED] ?: 0L) + trackersBlocked
      }
      if (bytesSaved > 0L) {
        prefs[Keys.BYTES_SAVED] = (prefs[Keys.BYTES_SAVED] ?: 0L) + bytesSaved
      }
      if (loadTimeMs in 15L..30_000L) {
        val count = prefs[Keys.MEASURED_PAGES] ?: 0
        val currentAvg = prefs[Keys.AVG_LOAD_MS] ?: 0L
        val newCount = count + 1
        val newAvg = ((currentAvg * count) + loadTimeMs) / newCount
        prefs[Keys.MEASURED_PAGES] = newCount
        prefs[Keys.AVG_LOAD_MS] = newAvg
      }
    }
  }

  suspend fun clearAll() {
    context.voltDataStore.edit { it.clear() }
  }
}
