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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DiscEntryEntity
import com.example.ui.PatcherUiState
import com.example.ui.components.SectionHeader
import com.example.ui.components.TechPill
import com.example.ui.components.formatByteSize
import com.example.ui.theme.CrimsonMod
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.MillenniumGold
import com.example.ui.theme.OutlineSubtle

@Composable
fun PatcherScreen(
  discs: List<DiscEntryEntity>,
  state: PatcherUiState,
  onPickBaseDisc: () -> Unit,
  onUseLibraryDiscAsBase: (DiscEntryEntity) -> Unit,
  onPickPpfFile: () -> Unit,
  onApplyAndSave: (suggestedName: String) -> Unit,
  onImportOutputToLibrary: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        SectionHeader(
          title = stringResource(R.string.patcher_title),
          subtitle = stringResource(R.string.patcher_desc),
        )
      }

      // Step 1: Base BIN/ISO
      item {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.dp, OutlineSubtle),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              TechPill("STEP 1", CyberCyan)
              Text("Base PS1 Disc Image (.bin / .iso / .img)", style = MaterialTheme.typography.titleMedium)
            }
            if (!state.baseDiscName.isNullOrBlank()) {
              Text(
                text = "Selected: ${state.baseDiscName}",
                style = MaterialTheme.typography.labelMedium,
                color = EmeraldVerified,
              )
            }
            OutlinedButton(
              onClick = onPickBaseDisc,
              enabled = !state.isPatching,
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("select_base_disc_button"),
            ) {
              Icon(Icons.Filled.Album, contentDescription = null, tint = CyberCyan)
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.btn_select_base), color = CyberCyan)
            }
            if (discs.isNotEmpty() && state.baseDiscUri == null) {
              Text(
                text = "Or pick from Disc Library:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              discs.take(3).forEach { disc ->
                OutlinedButton(
                  onClick = { onUseLibraryDiscAsBase(disc) },
                  modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                  Text("Use ${disc.displayName}")
                }
              }
            }
          }
        }
      }

      // Step 2: Select .PPF
      item {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.dp, OutlineSubtle),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              TechPill("STEP 2", MillenniumGold)
              Text("PlayStation Patch (.ppf)", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
              onClick = onPickPpfFile,
              enabled = !state.isPatching && !state.isInspectingPpf,
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("select_ppf_button"),
            ) {
              if (state.isInspectingPpf) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Parsing PPF...")
              } else {
                Icon(Icons.Filled.Build, contentDescription = null, tint = MillenniumGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_select_ppf), color = MillenniumGold)
              }
            }

            state.ppfMetadata?.let { meta ->
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = "${stringResource(R.string.label_ppf_summary)}: ${meta.fileName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MillenniumGold,
                  )
                  Text(
                    text = "Format: ${meta.magic} • Chunks: ${meta.chunkCount} (${formatByteSize(meta.totalPatchedBytes)} modified)",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan,
                  )
                  Text(
                    text = "Description: ${meta.description}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }

      // Step 3: Apply & Save
      item {
        val ready = state.baseDiscUri != null && state.ppfMetadata != null && !state.isPatching
        val suggestedOut =
          (state.baseDiscName?.substringBeforeLast('.') ?: "yugioh_fm") + "_modded.bin"

        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.dp, if (ready) MillenniumGold.copy(alpha = 0.5f) else OutlineSubtle),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              TechPill("STEP 3", EmeraldVerified)
              Text("Build Modded BIN / ISO", style = MaterialTheme.typography.titleMedium)
            }

            Button(
              onClick = { onApplyAndSave(suggestedOut) },
              enabled = ready,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MillenniumGold,
                  contentColor = Color(0xFF211700),
                ),
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("apply_ppf_button"),
            ) {
              Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.btn_apply_ppf))
            }

            if (state.isPatching) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                  progress = { state.progress },
                  modifier = Modifier.fillMaxWidth(),
                  color = CyberCyan,
                )
                Text(
                  text = "Applying mod chunks... ${(state.progress * 100).toInt()}%",
                  style = MaterialTheme.typography.labelMedium,
                  color = CyberCyan,
                )
              }
            }

            state.errorMessage?.let { err ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = CrimsonMod)
                Text(err, style = MaterialTheme.typography.bodyMedium, color = CrimsonMod)
              }
            }

            state.lastResult?.let { res ->
              Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, EmeraldVerified.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldVerified)
                    Text(
                      text = "Patched ${res.outputFileName} (${res.chunksApplied} chunks, ${formatByteSize(res.outputTotalBytes)})",
                      style = MaterialTheme.typography.titleMedium,
                      color = EmeraldVerified,
                    )
                  }
                  state.lastOutputUri?.let { outUri ->
                    Button(
                      onClick = { onImportOutputToLibrary(outUri) },
                      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("import_patched_disc_button"),
                    ) {
                      Text("Add Modded BIN to Disc Library & Inspect")
                    }
                  }
                }
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(72.dp)) }
    }
  }
}
