package com.example.browser

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import java.io.File

/**
 * Erases all app-level caches, WebView/Chromium leftovers, temporary/staged download files,
 * shared preferences, non-Room databases, and notifications on exit.
 */
object DeviceTraceCleaner {

  fun wipeWebViewTraces(context: Context) {
    runCatching { WebStorage.getInstance().deleteAllData() }
    runCatching {
      val cm = CookieManager.getInstance()
      cm.removeAllCookies(null)
      cm.removeSessionCookies(null)
      cm.flush()
    }
    runCatching {
      @Suppress("DEPRECATION")
      WebViewDatabase.getInstance(context).apply {
        clearFormData()
        clearHttpAuthUsernamePassword()
      }
    }
  }

  fun wipeFileSystemTraces(context: Context) {
    // 1. Dismiss any active/completed download or VPN notifications
    runCatching {
      context.getSystemService(NotificationManager::class.java)?.cancelAll()
    }

    // 2. Internal & code caches
    deleteDirectoryContents(context.cacheDir)
    deleteDirectoryContents(context.codeCacheDir)
    deleteDirectoryContents(context.noBackupFilesDir)

    // 3. External caches & app-sandbox downloaded/staged files
    context.externalCacheDirs.forEach { deleteDirectoryContents(it) }
    context.getExternalFilesDirs(null).forEach { deleteDirectoryContents(it) }

    // 4. Internal filesDir (preserve DataStore dir structure after VoltPreferences.clearAll() resets keys)
    deleteDirectoryContents(context.filesDir, excludedNames = setOf("datastore"))

    // 5. Chromium / WebView internal directories & leftover shared_prefs XMLs
    val appDataDir: File? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) context.dataDir
      else context.filesDir.parentFile

    if (appDataDir != null && appDataDir.exists()) {
      deleteDirectoryRecursively(File(appDataDir, "app_webview"))
      deleteDirectoryRecursively(File(appDataDir, "app_textures"))
      deleteDirectoryContents(File(appDataDir, "shared_prefs"))
      deleteDirectoryContents(File(appDataDir, "cache"))
      deleteDirectoryContents(File(appDataDir, "code_cache"))

      val dbDir = File(appDataDir, "databases")
      if (dbDir.exists() && dbDir.isDirectory) {
        dbDir.listFiles()?.forEach { file ->
          // Room tables are cleared cleanly via SQL DELETE; delete all WebView/legacy SQLite files
          if (!file.name.startsWith("volt_browser.db")) {
            deleteDirectoryRecursively(file)
          }
        }
      }
    }
  }

  fun deleteDirectoryContents(dir: File?, excludedNames: Set<String> = emptySet()) {
    if (dir == null || !dir.exists() || !dir.isDirectory) return
    dir.listFiles()?.forEach { child ->
      if (child.name !in excludedNames) {
        deleteDirectoryRecursively(child)
      }
    }
  }

  fun deleteDirectoryRecursively(target: File?) {
    if (target == null || !target.exists()) return
    runCatching {
      if (target.isDirectory) {
        target.listFiles()?.forEach { child -> deleteDirectoryRecursively(child) }
      }
      target.delete()
    }
  }
}
