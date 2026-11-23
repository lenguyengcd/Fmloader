package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.psx.MemCardSlot
import com.example.ui.MemCardUiState
import com.example.ui.components.Ps1PixelIcon
import com.example.ui.components.SectionHeader
import com.example.ui.components.TechPill
import com.example.ui.theme.CrimsonMod
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.MillenniumGold
import com.example.ui.theme.OutlineSubtle

@Composable
fun MemCardScreen(
  memCardState: MemCardUiState,
  onPickMemCard: () -> Unit,
  onExportSlotMcs: (MemCardSlot) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        SectionHeader(
          title = stringResource(R.string.memcard_title),
          subtitle = stringResource(R.string.memcard_desc),
        )
      }

      item {
        Button(
          onClick = onPickMemCard,
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("load_memcard_button"),
        ) {
          Icon(Icons.Filled.SdCard, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.btn_load_memcard))
        }
      }

      when (memCardState) {
        is MemCardUiState.Idle -> {
          item {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainer,
              shape = MaterialTheme.shapes.large,
              border = BorderStroke(1.dp, OutlineSubtle),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Icon(
                  Icons.Filled.SdCard,
                  contentDescription = null,
                  tint = MillenniumGold,
                  modifier = Modifier.size(44.dp),
                )
                Text(stringResource(R.string.memcard_empty_title), style = MaterialTheme.typography.titleLarge)
                Text(
                  stringResource(R.string.memcard_empty_desc),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        is MemCardUiState.Loading -> {
          item {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = MillenniumGold)
            }
          }
        }

        is MemCardUiState.Error -> {
          item {
            Text(
              text = memCardState.message,
              style = MaterialTheme.typography.bodyLarge,
              color = CrimsonMod,
            )
          }
        }

        is MemCardUiState.Loaded -> {
          val card = memCardState.card
          item {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainer,
              shape = MaterialTheme.shapes.medium,
              border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(card.fileName, style = MaterialTheme.typography.titleMedium)
                  Text(
                    text = "${card.formatLabel} • ${card.usedBlocks} / 15 Blocks Used",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan,
                  )
                }
                TechPill("${15 - card.usedBlocks} FREE", EmeraldVerified)
              }
            }
          }

          items(card.slots, key = { it.slotNumber }) { slot ->
            MemCardSlotRow(slot = slot, onExportMcs = { onExportSlotMcs(slot) })
          }
        }
      }

      item { Spacer(modifier = Modifier.height(72.dp)) }
    }
  }
}

@Composable
private fun MemCardSlotRow(slot: MemCardSlot, onExportMcs: () -> Unit) {
  val borderColor =
    when {
      slot.isForbiddenMemories -> MillenniumGold.copy(alpha = 0.7f)
      slot.isFree -> OutlineSubtle.copy(alpha = 0.5f)
      else -> OutlineSubtle
    }

  Surface(
    color =
      if (slot.isFree) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
      else MaterialTheme.colorScheme.surfaceContainer,
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, borderColor),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!slot.isFree && !slot.isLinkedContinuation) {
        Ps1PixelIcon(pixelsArgb = slot.iconPixelsArgb, size = 46.dp)
      } else {
        TechPill(
          text = "B${slot.slotNumber.toString().padStart(2, '0')}",
          accentColor = if (slot.isFree) MaterialTheme.colorScheme.onSurfaceVariant else CyberCyan,
        )
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Slot ${slot.slotNumber}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (slot.isForbiddenMemories) {
            TechPill("YU-GI-OH! FM", MillenniumGold)
          } else if (!slot.isFree && !slot.isLinkedContinuation) {
            TechPill(slot.productCode, CyberCyan)
          }
        }
        Text(
          text = slot.title,
          style = MaterialTheme.typography.titleMedium,
          color =
            if (slot.isFree) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
        if (!slot.isFree && !slot.isLinkedContinuation) {
          Text(
            text = "ID: ${slot.saveIdentifier.ifBlank { "MAIN" }} • ${slot.blockCount} block(s)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (slot.rawBlockBytes != null) {
        OutlinedButton(
          onClick = onExportMcs,
          modifier = Modifier.heightIn(min = 48.dp).testTag("export_mcs_button_${slot.slotNumber}"),
        ) {
          Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(".MCS")
        }
      }
    }
  }
}
