package com.example.browser

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.core.net.toUri
import com.example.data.SocialAppShortcut

/**
 * Encapsulates Social Hub app installation queries and system Autofill settings delegation
 * so BrowserViewModel stays focused on browser state coordination.
 */
object SocialHubHelper {

  fun buildShortcuts(app: Application): List<SocialAppShortcut> {
    val pm = app.packageManager
    return listOf(
      SocialAppShortcut(
        id = "instagram",
        name = "Instagram",
        packageName = "com.instagram.android",
        webUrl = "https://www.instagram.com/",
        loginUrl = "https://www.instagram.com/accounts/login/",
        isInstalled = isPackageInstalled(pm, "com.instagram.android"),
      ),
      SocialAppShortcut(
        id = "facebook",
        name = "Facebook",
        packageName = "com.facebook.katana",
        webUrl = "https://www.facebook.com/",
        loginUrl = "https://www.facebook.com/login/",
        isInstalled = isPackageInstalled(pm, "com.facebook.katana"),
      ),
    )
  }

  fun isAutofillEnabled(app: Application): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    return runCatching {
      val manager = app.getSystemService(AutofillManager::class.java)
      manager != null && manager.isEnabled && manager.hasEnabledAutofillServices()
    }.getOrDefault(false)
  }

  fun openAutofillSettings(app: Application): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val opened =
        runCatching {
          val intent =
            Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
              data = "package:${app.packageName}".toUri()
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
          app.startActivity(intent)
          true
        }.getOrDefault(false)
      if (opened) return true
    }
    return runCatching {
      app.startActivity(
        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
      true
    }.getOrDefault(false)
  }

  private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean =
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, 0)
      }
      true
    }.getOrDefault(false)
}
