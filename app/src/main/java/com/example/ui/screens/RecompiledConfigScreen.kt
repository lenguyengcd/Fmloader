package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DetectedEmulator
import com.example.data.RecompiledSettings
import com.example.ui.components.SectionHeader
import com.example.ui.components.TechPill
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.MillenniumGold
import com.example.ui.theme.OutlineSubtle
import kotlin.math.roundToInt

@Composable
fun RecompiledConfigScreen(
  settings: RecompiledSettings,
  installedEmulators: List<DetectedEmulator>,
  onUpdateSettings: ((RecompiledSettings) -> RecompiledSettings) -> Unit,
  onRefreshEmulators: () -> Unit,
  onExportJson: () -> Unit,
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
          title = stringResource(R.string.recompiled_title),
          subtitle = stringResource(R.string.recompiled_desc),
        )
      }

      // Mod Loader & Bypass Card
      item {
        ConfigSectionCard(title = "Mod Loader & Vanilla BIN Bypass", accentColor = EmeraldVerified) {
          SettingSwitchRow(
            title = "Bypass Original BIN Hash Check",
            subtitle =
              "Allows modded .bin/.iso/.img/.pbp files with altered WA_MRG.MRG or SLUS_014.11 to load like ePSXe.",
            checked = settings.bypassOriginalBinCheck,
            testTag = "switch_bypass_vanilla",
            onCheckedChange = { checked ->
              onUpdateSettings { it.copy(bypassOriginalBinCheck = checked) }
            },
          )
          SettingSwitchRow(
            title = "Allow Expanded Card IDs (>722)",
            subtitle = "Supports custom FM Recompiled & FM Mod decks beyond the stock 722 cards.",
            checked = settings.allowExpandedCardIds,
            testTag = "switch_expanded_cards",
            onCheckedChange = { checked ->
              onUpdateSettings { it.copy(allowExpandedCardIds = checked) }
            },
          )
        }
      }

      // Video & Recompiled Display
      item {
        ConfigSectionCard(title = "Video & Recompiled Enhancements", accentColor = CyberCyan) {
          Text(
            text = "Internal Resolution Scale (${settings.resolutionScale}x)",
            style = MaterialTheme.typography.titleMedium,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..4).forEach { scale ->
              FilterChip(
                selected = settings.resolutionScale == scale,
                onClick = { onUpdateSettings { it.copy(resolutionScale = scale) } },
                label = { Text("${scale}x") },
                modifier = Modifier.testTag("res_scale_$scale"),
              )
            }
          }
          SettingSwitchRow(
            title = "16:9 Widescreen Hack",
            subtitle = "Matches Yu-Gi-Oh! FM Recompiled widescreen viewport scaling.",
            checked = settings.widescreen16x9,
            testTag = "switch_widescreen",
            onCheckedChange = { checked -> onUpdateSettings { it.copy(widescreen16x9 = checked) } },
          )
          SettingSwitchRow(
            title = "PGXP Geometry Correction",
            subtitle = "Eliminates PS1 polygon jitter during 3D battle animations.",
            checked = settings.pgxpEnabled,
            testTag = "switch_pgxp",
            onCheckedChange = { checked -> onUpdateSettings { it.copy(pgxpEnabled = checked) } },
          )
          SettingSwitchRow(
            title = "HD Font Rendering",
            subtitle = "Crisp card descriptions & duel menus.",
            checked = settings.hdFonts,
            testTag = "switch_hd_fonts",
            onCheckedChange = { checked -> onUpdateSettings { it.copy(hdFonts = checked) } },
          )
        }
      }

      // Gameplay QOL
      item {
        ConfigSectionCard(title = "Gameplay & Mod QoL", accentColor = MillenniumGold) {
          SettingSwitchRow(
            title = "Live Duel-Rank Meter",
            subtitle = "Tracks S-POW / S-TEC rank thresholds in real time.",
            checked = settings.liveDuelRankMeter,
            testTag = "switch_duel_rank",
            onCheckedChange = { checked -> onUpdateSettings { it.copy(liveDuelRankMeter = checked) } },
          )
          SettingSwitchRow(
            title = "Fusion Assistant Overlay",
            subtitle = "Highlights valid hand fusions while dueling.",
            checked = settings.fusionAssistant,
            testTag = "switch_fusion_assistant",
            onCheckedChange = { checked -> onUpdateSettings { it.copy(fusionAssistant = checked) } },
          )
          SettingSwitchRow(
            title = "Fast Text / 60fps Menus",
            subtitle = "Removes classic PS1 menu frame delays.",
            checked = settings.fastText60Fps,
            testTag = "switch_fast_text",
            onCheckedChange = { checked -> onUpdateSettings { it.copy(fastText60Fps = checked) } },
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Card Drop Rate Multiplier: ${settings.dropRateMultiplier}x (1x–99x)",
            style = MaterialTheme.typography.titleMedium,
            color = MillenniumGold,
          )
          Slider(
            value = settings.dropRateMultiplier.toFloat(),
            onValueChange = { v ->
              onUpdateSettings { it.copy(dropRateMultiplier = v.roundToInt().coerceIn(1, 99)) }
            },
            valueRange = 1f..99f,
            modifier = Modifier.testTag("slider_drop_multiplier"),
          )
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf(1, 5, 15, 50, 99).forEach { preset ->
              FilterChip(
                selected = settings.dropRateMultiplier == preset,
                onClick = { onUpdateSettings { it.copy(dropRateMultiplier = preset) } },
                label = { Text("${preset}x") },
                modifier = Modifier.testTag("drop_preset_$preset"),
              )
            }
          }
        }
      }

      // Android PS1 Emulator Handoff
      item {
        ConfigSectionCard(title = "Android PS1 Emulator Target", accentColor = CyberCyan) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Filled.Gamepad, contentDescription = null, tint = CyberCyan)
              Text("Installed PS1 Emulators", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(
              onClick = onRefreshEmulators,
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = "Refresh installed emulators")
            }
          }

          EmulatorRadioRow(
            label = "Auto-detect / Android Chooser",
            subtitle = "Targets ePSXe or DuckStation if installed, else opens chooser",
            selected = settings.preferredEmulatorPackage == "AUTO",
            badgeText = "DEFAULT",
            badgeColor = CyberCyan,
            onSelect = { onUpdateSettings { it.copy(preferredEmulatorPackage = "AUTO") } },
          )

          installedEmulators.forEach { emu ->
            EmulatorRadioRow(
              label = emu.displayName,
              subtitle = emu.packageName,
              selected = settings.preferredEmulatorPackage == emu.packageName,
              badgeText = if (emu.isInstalled) "INSTALLED" else "NOT FOUND",
              badgeColor = if (emu.isInstalled) EmeraldVerified else MaterialTheme.colorScheme.onSurfaceVariant,
              onSelect = { onUpdateSettings { it.copy(preferredEmulatorPackage = emu.packageName) } },
            )
          }
        }
      }

      item {
        Button(
          onClick = onExportJson,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MillenniumGold,
              contentColor = Color(0xFF211700),
            ),
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("export_config_button"),
        ) {
          Icon(Icons.Filled.Download, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.btn_export_config))
        }
      }

      item { Spacer(modifier = Modifier.height(72.dp)) }
    }
  }
}

@Composable
private fun ConfigSectionCard(
  title: String,
  accentColor: Color,
  content: @Composable () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, OutlineSubtle),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(text = title, style = MaterialTheme.typography.titleLarge, color = accentColor)
      content()
    }
  }
}

@Composable
private fun SettingSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  testTag: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}

@Composable
private fun EmulatorRadioRow(
  label: String,
  subtitle: String,
  selected: Boolean,
  badgeText: String,
  badgeColor: Color,
  onSelect: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    RadioButton(selected = selected, onClick = onSelect)
    Column(modifier = Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.bodyLarge)
      Text(
        subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    TechPill(badgeText, badgeColor)
  }
}
