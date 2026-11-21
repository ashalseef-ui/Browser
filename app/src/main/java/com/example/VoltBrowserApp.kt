package com.example

import android.app.Application
import com.example.browser.VoltSpeedEngine
import com.example.data.AppDatabase
import com.example.data.BrowserRepository
import com.example.data.VoltPreferences

class VoltBrowserApp : Application() {

  lateinit var database: AppDatabase
    private set

  lateinit var repository: BrowserRepository
    private set

  lateinit var speedEngine: VoltSpeedEngine
    private set

  override fun onCreate() {
    super.onCreate()
    database = AppDatabase.getInstance(this)
    repository = BrowserRepository(database, VoltPreferences(this))
    speedEngine = VoltSpeedEngine(this)
    // Start Chromium initialization asynchronously off the main thread immediately at launch.
    speedEngine.warmUpChromiumAsync()
  }
}
