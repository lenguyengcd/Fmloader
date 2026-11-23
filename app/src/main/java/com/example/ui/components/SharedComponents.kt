package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.psx.DiscModStatus
import com.example.ui.theme.CrimsonMod
import com.example.ui.theme.CrimsonModContainer
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.MillenniumGold
import com.example.ui.theme.MillenniumGoldContainer
import com.example.ui.theme.OutlineSubtle

@Composable
fun ModStatusBadge(statusName: String, modifier: Modifier = Modifier) {
  val (label, bgColor, textColor) =
    when (statusName) {
      DiscModStatus.MODDED_FM.name -> Triple("MODDED ROM", CrimsonModContainer, CrimsonMod)
      DiscModStatus.ORIGINAL_FM.name -> Triple("ORIGINAL BIN", EmeraldContainer, EmeraldVerified)
      DiscModStatus.CUE_SHEET.name -> Triple("CUE SHEET", MillenniumGoldContainer, MillenniumGold)
      else -> Triple("PS1 DISC", CyberCyanContainer, CyberCyan)
    }

  Surface(
    modifier = modifier,
    color = bgColor,
    shape = RoundedCornerShape(6.dp),
    border = BorderStroke(1.dp, textColor.copy(alpha = 0.45f)),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = textColor,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

@Composable
fun TechPill(
  text: String,
  accentColor: Color = MaterialTheme.colorScheme.secondary,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    color = accentColor.copy(alpha = 0.12f),
    shape = RoundedCornerShape(6.dp),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = accentColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

@Composable
fun MetricCard(
  label: String,
  value: String,
  icon: ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, OutlineSubtle),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = accentColor,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    if (!subtitle.isNullOrBlank()) {
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun Ps1PixelIcon(pixelsArgb: IntArray?, size: Dp = 44.dp, modifier: Modifier = Modifier) {
  val imageBitmap =
    remember(pixelsArgb) {
      if (pixelsArgb != null && pixelsArgb.size == 16 * 16) {
        Bitmap.createBitmap(pixelsArgb, 16, 16, Bitmap.Config.ARGB_8888).asImageBitmap()
      } else {
        null
      }
    }

  Box(
    modifier =
      modifier
        .size(size)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .border(1.dp, OutlineSubtle, RoundedCornerShape(8.dp)),
    contentAlignment = Alignment.Center,
  ) {
    if (imageBitmap != null) {
      Image(
        bitmap = imageBitmap,
        contentDescription = "PS1 Save Icon",
        filterQuality = FilterQuality.None,
        modifier = Modifier.size(size - 6.dp),
      )
    } else {
      Spacer(modifier = Modifier.width(1.dp))
    }
  }
}

fun formatByteSize(bytes: Long): String {
  if (bytes <= 0L) return "0 B"
  val kb = bytes / 1024.0
  val mb = kb / 1024.0
  return when {
    mb >= 1.0 -> String.format("%.1f MB", mb)
    kb >= 1.0 -> String.format("%.1f KB", kb)
    else -> "$bytes B"
  }
}
