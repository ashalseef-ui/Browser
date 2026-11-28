package com.example.media

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.MainActivity
import com.example.R
import com.example.data.DownloadItemEntity
import java.io.File

/**
 * Handles exporting downloaded sandbox files to MediaStore/Downloads, SAF destinations,
 * FileProvider view/share intents, and download progress/completion notifications.
 */
internal class MediaExportHelper(private val context: Context) {

  fun exportToPublicDownloads(item: DownloadItemEntity): Boolean {
    val sourceFile = File(item.localPath)
    if (!sourceFile.exists()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      shareDownloadedFile(item)
      return true
    }
    val resolvedMime = MediaDownloader.resolveMimeType(item.fileName, item.mimeType)
    return exportFileToPublicDownloads(sourceFile, item.fileName, resolvedMime)
  }

  fun exportFileToPublicDownloads(
    sourceFile: File,
    fileName: String,
    mimeType: String,
  ): Boolean {
    if (!sourceFile.exists() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val safeMime = MediaDownloader.resolveMimeType(fileName, mimeType)
    return runCatching {
      val resolver = context.contentResolver
      val values =
        ContentValues().apply {
          put(MediaStore.Downloads.DISPLAY_NAME, fileName)
          put(MediaStore.Downloads.MIME_TYPE, safeMime)
          put(MediaStore.MediaColumns.SIZE, sourceFile.length())
          put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VoltBrowser")
          put(MediaStore.Downloads.IS_PENDING, 1)
        }
      val destUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
      resolver.openOutputStream(destUri)?.use { out ->
        sourceFile.inputStream().use { input -> input.copyTo(out) }
        out.flush()
      } ?: return false
      values.clear()
      values.put(MediaStore.Downloads.IS_PENDING, 0)
      resolver.update(destUri, values, null, null)
      true
    }.getOrDefault(false)
  }

  fun copyDownloadedFileToUri(item: DownloadItemEntity, destUri: Uri): Boolean {
    val sourceFile = File(item.localPath)
    if (!sourceFile.exists()) return false
    return runCatching {
      context.contentResolver.openOutputStream(destUri)?.use { out ->
        sourceFile.inputStream().use { input -> input.copyTo(out) }
        out.flush()
      } != null
    }.getOrDefault(false)
  }

  fun openDownloadedFile(item: DownloadItemEntity): Boolean {
    val file = File(item.localPath)
    if (!file.exists()) return false
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val resolvedMime = MediaDownloader.resolveMimeType(item.fileName, item.mimeType)
    val viewIntent =
      Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, resolvedMime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    val chooser =
      Intent.createChooser(viewIntent, "Open ${item.fileName}").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return try {
      context.startActivity(chooser)
      true
    } catch (_: ActivityNotFoundException) {
      false
    } catch (_: Exception) {
      false
    }
  }

  fun shareDownloadedFile(item: DownloadItemEntity) {
    val file = File(item.localPath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val resolvedMime = MediaDownloader.resolveMimeType(item.fileName, item.mimeType)
    val shareIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = resolvedMime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    val chooser =
      Intent.createChooser(shareIntent, item.fileName).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { context.startActivity(chooser) }
  }

  fun postDownloadNotification(
    id: Long,
    title: String,
    message: String,
    ongoing: Boolean,
    targetFile: File? = null,
    mimeType: String? = null,
  ) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    runCatching {
      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      val channelId = "volt_downloads"
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel =
          NotificationChannel(
            channelId,
            "Volt Downloads",
            NotificationManager.IMPORTANCE_DEFAULT,
          )
        manager.createNotificationChannel(channel)
      }
      val contentIntent =
        if (!ongoing && targetFile != null && targetFile.exists()) {
          runCatching {
            val fileUri =
              FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
            val viewIntent =
              Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, MediaDownloader.resolveMimeType(targetFile.name, mimeType))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
            PendingIntent.getActivity(
              context,
              5000 + id.toInt(),
              viewIntent,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
          }.getOrNull()
        } else {
          null
        } ?: PendingIntent.getActivity(
          context,
          5000 + id.toInt(),
          Intent(context, MainActivity::class.java),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val notification =
        NotificationCompat.Builder(context, channelId)
          .setSmallIcon(R.drawable.ic_volt_stat)
          .setContentTitle(title)
          .setContentText(message)
          .setContentIntent(contentIntent)
          .setOngoing(ongoing)
          .setAutoCancel(!ongoing)
          .build()
      manager.notify(5000 + id.toInt(), notification)
    }
  }
}
