package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.psx.ExtractedTimTexture
import com.example.core.psx.FmCardBinaryEntry
import com.example.core.psx.FmDirectSession
import com.example.core.psx.MipsCpuState
import com.example.core.psx.PsxMipsEngine
import com.example.data.DiscEntryEntity
import com.example.data.RecompiledSettings
import com.example.ui.DirectRunnerUiState
import com.example.ui.components.ModStatusBadge
import com.example.ui.components.TechPill
import com.example.ui.theme.CrimsonMod
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.MillenniumGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineSubtle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectRunnerScreen(
  runnerState: DirectRunnerUiState,
  settings: RecompiledSettings,
  onBack: () -> Unit,
  onStepCycles: (Int) -> Unit,
  onToggleAutoRun: () -> Unit,
  onResetCpu: () -> Unit,
  onRollDrops: () -> Unit,
  onOpenExternalEmu: (DiscEntryEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 920.dp)) {
      when (runnerState) {
        is DirectRunnerUiState.Idle -> {
          RunnerTopHeader(
            title = stringResource(R.string.runner_title),
            subtitle = "No disc booted yet",
            onBack = onBack,
            onExternalLaunch = null,
          )
          Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = "Select any disc in the library and tap Run Direct.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp).testTag("runner_idle_back_btn"),
              ) {
                Text(stringResource(R.string.runner_back))
              }
            }
          }
        }

        is DirectRunnerUiState.Booting -> {
          RunnerTopHeader(
            title = runnerState.discName,
            subtitle = "Mapping ISO-9660 & PS-X EXE into 2 MB PS1 RAM...",
            onBack = onBack,
            onExternalLaunch = null,
          )
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              CircularProgressIndicator(color = MillenniumGold)
              Text(
                text = "Booting ${runnerState.discName}...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        is DirectRunnerUiState.Error -> {
          RunnerTopHeader(
            title = runnerState.discName,
            subtitle = "Boot error",
            onBack = onBack,
            onExternalLaunch = null,
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, CrimsonMod.copy(alpha = 0.6f)),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text(
                text = runnerState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = CrimsonMod,
              )
              Button(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp).testTag("runner_error_back_btn"),
              ) {
                Text(stringResource(R.string.runner_back))
              }
            }
          }
        }

        is DirectRunnerUiState.Running -> {
          val session = runnerState.session
          val analysis = session.analysis
          RunnerTopHeader(
            title = analysis.displayName,
            subtitle = "${analysis.serialCode} • ${analysis.format.label} • ${analysis.region}",
            onBack = onBack,
            onExternalLaunch = { onOpenExternalEmu(runnerState.entity) },
          )

          Row(
            modifier =
              Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ModStatusBadge(analysis.modStatus.name)
            TechPill("Drop ${settings.dropRateMultiplier}x", MillenniumGold)
            TechPill("${settings.resolutionScale}x Res", CyberCyan)
            if (settings.bypassOriginalBinCheck) {
              TechPill("Bypass BIN Lock ON", EmeraldVerified)
            }
          }

          var selectedTab by remember { mutableIntStateOf(0) }
          val tabs =
            listOf(
              stringResource(R.string.runner_tab_cpu),
              stringResource(R.string.runner_tab_cards) + " (${session.cards.size})",
              stringResource(R.string.runner_tab_textures) + " (${session.textures.size})",
            )

          PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MillenniumGold,
          ) {
            tabs.forEachIndexed { idx, label ->
              Tab(
                selected = selectedTab == idx,
                onClick = { selectedTab = idx },
                text = {
                  Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                  )
                },
                modifier = Modifier.testTag("runner_tab_$idx"),
              )
            }
          }

          when (selectedTab) {
            0 ->
              CpuExecutionTab(
                session = session,
                isAutoRunning = runnerState.isAutoRunning,
                onToggleAutoRun = onToggleAutoRun,
                onStepCycles = onStepCycles,
                onResetCpu = onResetCpu,
              )
            1 ->
              CardStatsAndDropsTab(
                session = session,
                dropMultiplier = settings.dropRateMultiplier,
                onRollDrops = onRollDrops,
              )
            else -> TimTexturesTab(textures = session.textures)
          }
        }
      }
    }
  }
}

@Composable
private fun RunnerTopHeader(
  title: String,
  subtitle: String,
  onBack: () -> Unit,
  onExternalLaunch: (() -> Unit)?,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, OutlineSubtle),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("runner_back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.runner_back),
          tint = MillenniumGold,
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (onExternalLaunch != null) {
        IconButton(
          onClick = onExternalLaunch,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("runner_external_emu_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.action_launch_emulator),
            tint = CyberCyan,
          )
        }
      }
    }
  }
}

@Composable
private fun CpuExecutionTab(
  session: FmDirectSession,
  isAutoRunning: Boolean,
  onToggleAutoRun: () -> Unit,
  onStepCycles: (Int) -> Unit,
  onResetCpu: () -> Unit,
) {
  val cpu = session.cpu
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = stringResource(R.string.runner_honest_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(12.dp),
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onToggleAutoRun,
          enabled = !cpu.halted && session.ramImage.isNotEmpty(),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = if (isAutoRunning) CrimsonMod else MillenniumGold,
              contentColor = if (isAutoRunning) Color.White else Color(0xFF211700),
            ),
          modifier = Modifier.weight(1.2f).heightIn(min = 48.dp).testTag("runner_play_pause_btn"),
        ) {
          Icon(
            imageVector = if (isAutoRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text =
              if (isAutoRunning) stringResource(R.string.runner_btn_pause)
              else stringResource(R.string.runner_btn_play),
            maxLines = 1,
          )
        }

        OutlinedButton(
          onClick = { onStepCycles(1) },
          enabled = !cpu.halted && !isAutoRunning && session.ramImage.isNotEmpty(),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("runner_step1_btn"),
        ) {
          Icon(Icons.Filled.SkipNext, contentDescription = null)
          Spacer(modifier = Modifier.width(2.dp))
          Text(stringResource(R.string.runner_btn_step), maxLines = 1)
        }

        OutlinedButton(
          onClick = { onStepCycles(50) },
          enabled = !cpu.halted && !isAutoRunning && session.ramImage.isNotEmpty(),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("runner_step50_btn"),
        ) {
          Icon(Icons.Filled.FastForward, contentDescription = null)
          Spacer(modifier = Modifier.width(2.dp))
          Text(stringResource(R.string.runner_btn_step_burst), maxLines = 1)
        }

        IconButton(
          onClick = onResetCpu,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("runner_reset_cpu_btn"),
        ) {
          Icon(
            Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.runner_btn_reset),
            tint = CyberCyan,
          )
        }
      }
    }

    item { CpuRegistersCard(cpu = cpu) }

    if (session.runtimeNotes.isNotEmpty() || cpu.biosCalls.isNotEmpty()) {
      item {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = ObsidianBg,
          border = BorderStroke(1.dp, OutlineSubtle),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "PS1 BOOT & BIOS LOG",
              style = MaterialTheme.typography.labelMedium,
              color = EmeraldVerified,
            )
            session.runtimeNotes.forEach { note ->
              Text(
                text = "• $note",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            cpu.biosCalls.takeLast(8).forEach { call ->
              Text(
                text = "> $call",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = CyberCyan,
              )
            }
            cpu.haltReason?.let { reason ->
              Text(
                text = "! HALT: $reason",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = CrimsonMod,
              )
            }
          }
        }
      }
    }

    item {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = ObsidianBg,
        border = BorderStroke(1.dp, OutlineSubtle),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "MIPS R3000A LIVE INSTRUCTION TRACE",
            style = MaterialTheme.typography.labelMedium,
            color = MillenniumGold,
          )
          if (cpu.trace.isEmpty()) {
            Text(
              text = "No instructions stepped yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            cpu.trace.takeLast(18).reversed().forEach { entry ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Text(
                  text = "0x${PsxMipsEngine.hex8(entry.pc)}",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace,
                  color = CyberCyan,
                )
                Text(
                  text = PsxMipsEngine.hex8(entry.rawInstruction.toLong() and 0xFFFFFFFFL),
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = entry.mnemonic,
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CpuRegistersCard(cpu: MipsCpuState) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, OutlineSubtle),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "MIPS R3000A REGISTERS",
          style = MaterialTheme.typography.labelMedium,
          color = MillenniumGold,
        )
        Text(
          text = "Cycles: ${cpu.cyclesExecuted}",
          style = MaterialTheme.typography.labelMedium,
          color = EmeraldVerified,
          fontFamily = FontFamily.Monospace,
        )
      }

      val regPairs =
        listOf(
          "PC" to cpu.pc,
          "SP" to cpu.sp,
          "GP" to cpu.gp,
          "RA" to cpu.ra,
          "V0" to cpu.v0,
          "V1" to cpu.v1,
          "A0" to cpu.a0,
          "A1" to cpu.a1,
          "A2" to cpu.a2,
          "A3" to cpu.a3,
        )

      regPairs.chunked(2).forEach { pair ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          pair.forEach { (name, value) ->
            Row(
              modifier =
                Modifier.weight(1f)
                  .background(ObsidianBg, RoundedCornerShape(6.dp))
                  .padding(horizontal = 8.dp, vertical = 5.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = CyberCyan,
              )
              Text(
                text = "0x${PsxMipsEngine.hex8(value)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CardStatsAndDropsTab(
  session: FmDirectSession,
  dropMultiplier: Int,
  onRollDrops: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val filteredCards =
    remember(session.cards, query) {
      val trimmed = query.trim()
      if (trimmed.isEmpty()) {
        session.cards.take(120)
      } else {
        session.cards
          .filter { card ->
            card.cardId.toString().contains(trimmed) ||
              card.atk.toString().contains(trimmed) ||
              card.def.toString().contains(trimmed) ||
              card.attributeLabel.contains(trimmed, ignoreCase = true)
          }
          .take(120)
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MillenniumGold.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text =
              if (session.dropPool.isNotEmpty()) {
                "WA_MRG.MRG Drop Pool (${session.dropPool.size} entries @ 0x${(session.dropPoolOffset ?: 0L).toString(16)})"
              } else {
                "Drop Pool Unavailable For This Disc"
              },
            style = MaterialTheme.typography.titleSmall,
            color = MillenniumGold,
          )
          if (session.dropPool.isNotEmpty()) {
            Button(
              onClick = onRollDrops,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MillenniumGold,
                  contentColor = Color(0xFF211700),
                ),
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("runner_roll_drops_btn"),
            ) {
              Icon(Icons.Filled.Casino, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.runner_btn_roll_drops, dropMultiplier))
            }
          } else {
            Text(
              text = "No standard FM WA_MRG.MRG duelist drop table found in this disc image.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (session.dropRolls.isNotEmpty()) {
            session.dropRolls.forEachIndexed { idx, roll ->
              val statsText =
                roll.cardStats?.let { "ATK ${it.atk} / DEF ${it.def} (Lv ${it.level})" }
                  ?: "Card #${roll.cardId}"
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .background(ObsidianBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Drop #${idx + 1}: Card ID #${roll.cardId.toString().padStart(3, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldVerified,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = statsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
                Text(
                  text =
                    String.format(
                      "%.2f%% -> %.2f%%",
                      roll.basePercent,
                      roll.boostedPercent,
                    ),
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace,
                  color = CyberCyan,
                )
              }
            }
          }
        }
      }
    }

    item {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Filter decoded SLUS cards (ID, ATK, DEF, Attribute)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("runner_card_search_input"),
      )
    }

    if (session.cards.isEmpty()) {
      item {
        Text(
          text = "No packed Yu-Gi-Oh! FM card stat table found in this executable.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      item {
        Text(
          text =
            "Showing ${filteredCards.size} of ${session.cards.size} cards decoded from RAM 0x${PsxMipsEngine.hex8(session.cardTableRamAddress ?: 0L)}",
          style = MaterialTheme.typography.labelSmall,
          color = CyberCyan,
        )
      }
      items(filteredCards, key = { it.cardId }) { card -> CardBinaryRow(card = card) }
    }
  }
}

@Composable
private fun CardBinaryRow(card: FmCardBinaryEntry) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, OutlineSubtle),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Card #${card.cardId.toString().padStart(3, '0')} • ${card.attributeLabel}",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "RAM 0x${PsxMipsEngine.hex8(card.ramAddress)} • Raw 0x${PsxMipsEngine.hex8(card.rawStatsWord)}",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "ATK ${card.atk} / DEF ${card.def}",
          style = MaterialTheme.typography.labelMedium,
          color = MillenniumGold,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "LV ${card.level} • GS ${card.guardianStar1}/${card.guardianStar2}",
          style = MaterialTheme.typography.labelSmall,
          color = CyberCyan,
        )
      }
    }
  }
}

@Composable
private fun TimTexturesTab(textures: List<ExtractedTimTexture>) {
  if (textures.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
      Text(
        text = "No standard uncompressed PS1 .TIM textures detected in the scanned sectors of this disc.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    return
  }

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 140.dp),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    items(textures, key = { it.offsetBytes }) { tim ->
      val bmp =
        remember(tim) {
          Bitmap.createBitmap(tim.argbPixels, tim.width, tim.height, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
        }
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, OutlineSubtle),
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier =
              Modifier.size(104.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianBg)
                .border(1.dp, OutlineSubtle, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Image(
              bitmap = bmp,
              contentDescription = "TIM Texture @ 0x${tim.offsetBytes.toString(16)}",
              filterQuality = FilterQuality.None,
              modifier = Modifier.size(92.dp),
            )
          }
          Text(
            text = "${tim.width}x${tim.height} (${tim.bpp}bpp)",
            style = MaterialTheme.typography.labelMedium,
            color = MillenniumGold,
          )
          Text(
            text = "@ 0x${tim.offsetBytes.toString(16).uppercase()}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = CyberCyan,
          )
        }
      }
    }
  }
}
