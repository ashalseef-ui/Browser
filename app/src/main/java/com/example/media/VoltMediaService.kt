package com.example.media

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.VoltBrowserApp
import com.example.browser.MediaSniffer

class VoltMediaService : MediaSessionService() {

  private var mediaSession: MediaSession? = null

  @OptIn(UnstableApi::class)
  override fun onCreate() {
    super.onCreate()

    val sharedOkHttp = (application as VoltBrowserApp).speedEngine.okHttpClient
    val mediaOkHttp =
      sharedOkHttp
        .newBuilder()
        .addInterceptor { chain ->
          val orig = chain.request()
          val reqUrl = orig.url.toString()
          val ctx = MediaSniffer.getPlaybackContext(reqUrl)
          val builder =
            orig
              .newBuilder()
              .header("Accept", "*/*")
              .header("Accept-Language", "en-US,en;q=0.9")
          if (ctx?.referer != null && orig.header("Referer") == null) {
            builder.header("Referer", ctx.referer)
          }
          if (ctx?.origin != null && orig.header("Origin") == null) {
            builder.header("Origin", ctx.origin)
          }
          if (ctx != null && orig.header("User-Agent") == null) {
            builder.header("User-Agent", ctx.userAgent)
          }
          if (orig.header("Cookie") == null) {
            MediaSniffer.getCookiesForUrl(reqUrl)?.let { cookie ->
              builder.header("Cookie", cookie)
            }
          }
          chain.proceed(builder.build())
        }
        .build()
    val httpFactory =
      OkHttpDataSource.Factory(mediaOkHttp)
        .setUserAgent(MediaSniffer.DEFAULT_BROWSER_USER_AGENT)
    val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

    val audioAttributes =
      AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    val player =
      ExoPlayer.Builder(this)
        .setMediaSourceFactory(
          DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)
        )
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(10_000L)
        .setSeekForwardIncrementMs(10_000L)
        .build()

    val openAppIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val sessionActivityPendingIntent =
      PendingIntent.getActivity(
        this,
        0,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build())

    mediaSession =
      MediaSession.Builder(this, player)
        .setSessionActivity(sessionActivityPendingIntent)
        .build()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

  override fun onTaskRemoved(rootIntent: Intent?) {
    val player = mediaSession?.player
    if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
      stopSelf()
    }
  }

  override fun onDestroy() {
    mediaSession?.run {
      player.release()
      release()
      mediaSession = null
    }
    super.onDestroy()
  }
}
