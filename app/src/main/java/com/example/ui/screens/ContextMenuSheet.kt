package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.MediaKind
import com.example.data.WebContextMenuTarget
import com.example.ui.theme.LocalVoltColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebContextMenuSheet(
  target: WebContextMenuTarget,
  onOpenCurrentTab: (String) -> Unit,
  onOpenNewTab: (url: String, incognito: Boolean) -> Unit,
  onPlayMediaInternal: (url: String, title: String?, kind: MediaKind?, pageUrl: String) -> Unit,
  onOpenMediaExternal: (url: String, title: String?, kind: MediaKind?, pageUrl: String) -> Unit,
  onDownloadUrl: (url: String, title: String?, mimeType: String, referer: String?) -> Unit,
  onShareUrl: (url: String, title: String?) -> Unit,
  onDeleteBookmark: ((Long) -> Unit)? = null,
  onCopiedToast: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val voltColors = LocalVoltColors.current
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val primaryUrl = target.mediaUrl ?: target.linkUrl ?: target.imageUrl.orEmpty()
  val isMediaTarget = !target.mediaUrl.isNullOrBlank()
  val isHttpMedia =
    target.mediaUrl?.let {
      it.startsWith("http://", true) || it.startsWith("https://", true)
    } == true
  val hasImage = !target.imageUrl.isNullOrBlank()
  val imageDistinctFromPrimary = hasImage && target.imageUrl != target.linkUrl && target.imageUrl != target.mediaUrl

  val headerIcon =
    when {
      isMediaTarget -> Icons.Default.PlayCircle
      !target.linkUrl.isNullOrBlank() -> Icons.Default.Link
      else -> Icons.Default.Image
    }
  val headerTint =
    when {
      isMediaTarget -> voltColors.amberAccent
      !target.linkUrl.isNullOrBlank() -> voltColors.brandAccent
      else -> voltColors.emeraldShield
    }
  val headerTitle =
    target.mediaTitle?.takeIf { it.isNotBlank() }
      ?: target.linkText?.takeIf { it.isNotBlank() }
      ?: when {
        isMediaTarget -> "Media Stream"
        !target.linkUrl.isNullOrBlank() -> "Web Link"
        else -> "Image"
      }

  fun copyText(label: String, text: String, toast: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    onCopiedToast(toast)
    onDismiss()
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp)
          .padding(bottom = 20.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = voltColors.cardSurface,
        border = BorderStroke(1.dp, voltColors.cardBorder),
        modifier = Modifier.fillMaxWidth().testTag("context_menu_header"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier =
              Modifier.size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(headerTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = headerIcon,
              contentDescription = null,
              tint = headerTint,
              modifier = Modifier.size(22.dp),
            )
          }
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = headerTitle,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = primaryUrl,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (primaryUrl.isNotBlank()) {
            IconButton(
              onClick = { copyText("url", primaryUrl, "Address copied") },
              modifier = Modifier.testTag("context_copy_header_url"),
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy address",
                tint = voltColors.brandAccent,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }

      if (isMediaTarget) {
        val mUrl = target.mediaUrl!!
        ContextMenuActionRow(
          icon = Icons.Default.PlayCircle,
          title = "Play in Internal Player",
          subtitle = "Volt Cinema player with speed, PiP & background audio",
          accentColor = voltColors.amberAccent,
          onClick = {
            onPlayMediaInternal(mUrl, target.mediaTitle, target.mediaKind, target.pageUrl)
          },
          testTag = "context_play_internal",
        )
        ContextMenuActionRow(
          icon = Icons.AutoMirrored.Filled.OpenInNew,
          title = "Open in External Player",
          subtitle = "Send stream to VLC, MX, or installed media apps",
          accentColor = voltColors.brandAccent,
          onClick = {
            onOpenMediaExternal(mUrl, target.mediaTitle, target.mediaKind, target.pageUrl)
          },
          testTag = "context_open_external",
        )
        if (isHttpMedia) {
          ContextMenuActionRow(
            icon = Icons.Default.Download,
            title = "Download Media",
            subtitle = "Save directly to Downloads/VoltBrowser",
            accentColor = voltColors.emeraldShield,
            onClick = {
              onDismiss()
              onDownloadUrl(
                mUrl,
                target.mediaTitle,
                if (target.mediaKind == MediaKind.AUDIO) "audio/*" else "video/mp4",
                target.pageUrl,
              )
            },
            testTag = "context_download_media",
          )
        }
      }

      val navUrl = target.linkUrl ?: target.mediaUrl?.takeIf { isHttpMedia }
      if (!navUrl.isNullOrBlank()) {
        val canTabOpen = navUrl.startsWith("http://", true) || navUrl.startsWith("https://", true)
        if (canTabOpen) {
          if (isMediaTarget) {
            HorizontalDivider(
              modifier = Modifier.padding(vertical = 2.dp),
              color = voltColors.cardBorder,
            )
          }
          ContextMenuActionRow(
            icon = Icons.Default.Language,
            title = "Open in Current Tab",
            accentColor = MaterialTheme.colorScheme.onSurface,
            onClick = {
              onDismiss()
              onOpenCurrentTab(navUrl)
            },
            testTag = "context_open_current_tab",
          )
          ContextMenuActionRow(
            icon = Icons.Default.AddToQueue,
            title = "Open in New Tab",
            accentColor = voltColors.brandAccent,
            onClick = {
              onDismiss()
              onOpenNewTab(navUrl, false)
            },
            testTag = "context_open_new_tab",
          )
          ContextMenuActionRow(
            icon = Icons.Outlined.VisibilityOff,
            title = "Open in Private Tab",
            accentColor = voltColors.incognitoAccent,
            onClick = {
              onDismiss()
              onOpenNewTab(navUrl, true)
            },
            testTag = "context_open_private_tab",
          )
          if (!isMediaTarget) {
            ContextMenuActionRow(
              icon = Icons.Default.Download,
              title = "Download Link",
              accentColor = voltColors.emeraldShield,
              onClick = {
                onDismiss()
                onDownloadUrl(navUrl, target.linkText, "application/octet-stream", target.pageUrl)
              },
              testTag = "context_download_link",
            )
          }
        }

        ContextMenuActionRow(
          icon = Icons.Default.ContentCopy,
          title = if (isMediaTarget) "Copy Media Link" else "Copy Link Address",
          accentColor = MaterialTheme.colorScheme.onSurface,
          onClick = {
            copyText(
              "link",
              navUrl,
              if (isMediaTarget) "Media link copied" else "Link copied",
            )
          },
          testTag = "context_copy_link",
        )

        if (!target.linkText.isNullOrBlank()) {
          ContextMenuActionRow(
            icon = Icons.Default.TextFields,
            title = "Copy Link Text",
            accentColor = MaterialTheme.colorScheme.onSurface,
            onClick = { copyText("link_text", target.linkText, "Link text copied") },
            testTag = "context_copy_link_text",
          )
        }

        ContextMenuActionRow(
          icon = Icons.Default.Share,
          title = "Share Link",
          accentColor = MaterialTheme.colorScheme.onSurface,
          onClick = { onShareUrl(navUrl, target.linkText ?: target.mediaTitle) },
          testTag = "context_share_link",
        )
      }

      if (hasImage) {
        val imgUrl = target.imageUrl!!
        if (!navUrl.isNullOrBlank() || isMediaTarget) {
          HorizontalDivider(
            modifier = Modifier.padding(vertical = 2.dp),
            color = voltColors.cardBorder,
          )
        }
        ContextMenuActionRow(
          icon = Icons.Default.Image,
          title = "Open Image in New Tab",
          accentColor = voltColors.brandAccent,
          onClick = {
            onDismiss()
            onOpenNewTab(imgUrl, false)
          },
          testTag = "context_open_image_new_tab",
        )
        ContextMenuActionRow(
          icon = Icons.Default.Download,
          title = "Download Image",
          accentColor = voltColors.emeraldShield,
          onClick = {
            onDismiss()
            onDownloadUrl(imgUrl, null, "image/*", target.pageUrl)
          },
          testTag = "context_download_image",
        )
        if (imageDistinctFromPrimary) {
          ContextMenuActionRow(
            icon = Icons.Default.ContentCopy,
            title = "Copy Image Address",
            accentColor = MaterialTheme.colorScheme.onSurface,
            onClick = { copyText("image_url", imgUrl, "Image address copied") },
            testTag = "context_copy_image",
          )
          ContextMenuActionRow(
            icon = Icons.Default.Share,
            title = "Share Image Link",
            accentColor = MaterialTheme.colorScheme.onSurface,
            onClick = { onShareUrl(imgUrl, "Image") },
            testTag = "context_share_image",
          )
        }
      }

      if (target.bookmarkId != null && onDeleteBookmark != null) {
        HorizontalDivider(
          modifier = Modifier.padding(vertical = 2.dp),
          color = voltColors.cardBorder,
        )
        ContextMenuActionRow(
          icon = Icons.Default.DeleteOutline,
          title = "Remove from Workspace",
          accentColor = MaterialTheme.colorScheme.error,
          onClick = {
            onDeleteBookmark(target.bookmarkId)
            onDismiss()
          },
          testTag = "context_remove_speed_dial",
        )
      }

      Spacer(modifier = Modifier.height(4.dp))
    }
  }
}

@Composable
private fun ContextMenuActionRow(
  icon: ImageVector,
  title: String,
  accentColor: Color,
  onClick: () -> Unit,
  testTag: String,
  subtitle: String? = null,
) {
  val voltColors = LocalVoltColors.current
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = voltColors.cardSurface,
    border = BorderStroke(1.dp, voltColors.cardBorder.copy(alpha = 0.6f)),
    modifier =
      Modifier.fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = accentColor,
        modifier = Modifier.size(20.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color =
            if (accentColor == MaterialTheme.colorScheme.error) accentColor
            else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
