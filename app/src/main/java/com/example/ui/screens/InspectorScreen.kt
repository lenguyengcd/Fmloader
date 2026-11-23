package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.psx.IsoFileEntry
import com.example.core.psx.PsxDiscAnalysis
import com.example.core.psx.SectorHexDump
import com.example.data.DiscEntryEntity
import com.example.ui.InspectionUiState
import com.example.ui.components.ModStatusBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.TechPill
import com.example.ui.components.formatByteSize
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.MillenniumGold
import com.example.ui.theme.OutlineSubtle

@Composable
fun InspectorScreen(
  discs: List<DiscEntryEntity>,
  inspectionState: InspectionUiState,
  onSelectDiscToInspect: (DiscEntryEntity) -> Unit,
  onReadSector: (Int) -> Unit,
  onScanStrings: () -> Unit,
  onGoToLibrary: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var activeSubTab by remember { mutableIntStateOf(0) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    when (inspectionState) {
      is InspectionUiState.Idle -> {
        Column(
          modifier = Modifier.fillMaxSize().widthIn(max = 700.dp).padding(20.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            imageVector = Icons.Filled.Memory,
            contentDescription = null,
            tint = CyberCyan,
            modifier = Modifier.size(56.dp),
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = stringResource(R.string.inspector_empty_title),
            style = MaterialTheme.typography.headlineSmall,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = stringResource(R.string.inspector_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(16.dp))
          if (discs.isNotEmpty()) {
            Button(
              onClick = { onSelectDiscToInspect(discs.first()) },
              modifier = Modifier.heightIn(min = 48.dp).testTag("inspect_first_disc_button"),
            ) {
              Text("Inspect ${discs.first().displayName}")
            }
          } else {
            OutlinedButton(
              onClick = onGoToLibrary,
              modifier = Modifier.heightIn(min = 48.dp).testTag("go_to_library_button"),
            ) {
              Text(stringResource(R.string.btn_load_rom))
            }
          }
        }
      }

      is InspectionUiState.Loading -> {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          CircularProgressIndicator(color = MillenniumGold)
          Spacer(modifier = Modifier.height(12.dp))
          Text("Reading ISO-9660 sectors from ${inspectionState.fileName}...")
        }
      }

      is InspectionUiState.Error -> {
        Column(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = inspectionState.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.tertiary,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Button(onClick = onGoToLibrary, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Back to Discs")
          }
        }
      }

      is InspectionUiState.Loaded -> {
        val analysis = inspectionState.analysis
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {
          if (discs.size > 1) {
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .horizontalScroll(rememberScrollState())
                  .padding(horizontal = 16.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              discs.forEach { entry ->
                FilterChip(
                  selected = entry.uriString == analysis.uriString,
                  onClick = { onSelectDiscToInspect(entry) },
                  label = {
                    Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  },
                )
              }
            }
          }

          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, OutlineSubtle),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ModStatusBadge(analysis.modStatus.name)
                TechPill(analysis.format.label, CyberCyan)
                TechPill("Stride ${analysis.sectorStride}B", MillenniumGold)
              }
              Text(
                text = "${analysis.displayName} (${analysis.serialCode})",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = MaterialTheme.colorScheme.surface,
          ) {
            Tab(
              selected = activeSubTab == 0,
              onClick = { activeSubTab = 0 },
              text = { Text(stringResource(R.string.inspector_tab_files)) },
              modifier = Modifier.testTag("inspector_tab_files"),
            )
            Tab(
              selected = activeSubTab == 1,
              onClick = { activeSubTab = 1 },
              text = { Text(stringResource(R.string.inspector_tab_cards)) },
              modifier = Modifier.testTag("inspector_tab_scan"),
            )
            Tab(
              selected = activeSubTab == 2,
              onClick = { activeSubTab = 2 },
              text = { Text(stringResource(R.string.inspector_tab_hex)) },
              modifier = Modifier.testTag("inspector_tab_hex"),
            )
          }

          when (activeSubTab) {
            0 ->
              IsoFilesTab(
                analysis = analysis,
                onJumpToSector = { lba ->
                  onReadSector(lba)
                  activeSubTab = 2
                },
              )
            1 ->
              ExeCardScanTab(
                state = inspectionState,
                onScanStrings = onScanStrings,
              )
            else ->
              SectorHexTab(
                analysis = analysis,
                currentLba = inspectionState.currentLba,
                hexDump = inspectionState.hexDump,
                isReading = inspectionState.isReadingHex,
                onReadSector = onReadSector,
              )
          }
        }
      }
    }
  }
}

@Composable
private fun IsoFilesTab(
  analysis: PsxDiscAnalysis,
  onJumpToSector: (Int) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val filteredFiles =
    remember(analysis.files, query) {
      if (query.isBlank()) analysis.files
      else analysis.files.filter { it.fullPath.contains(query, ignoreCase = true) }
    }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    item {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, EmeraldVerified.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = EmeraldVerified)
            Text(
              text = stringResource(R.string.label_wa_mrg_analysis),
              style = MaterialTheme.typography.titleMedium,
            )
          }
          Text(
            text = stringResource(R.string.label_bypass_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          analysis.modReasons.forEach { reason ->
            Text(
              text = "• $reason",
              style = MaterialTheme.typography.labelMedium,
              color = MillenniumGold,
            )
          }
        }
      }
    }

    if (analysis.systemCnfText.isNotBlank()) {
      item {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = MaterialTheme.shapes.medium,
          border = BorderStroke(1.dp, OutlineSubtle),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = stringResource(R.string.label_system_cnf),
              style = MaterialTheme.typography.titleMedium,
              color = CyberCyan,
            )
            Text(
              text = analysis.systemCnfText,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }

    analysis.exeHeader?.let { exe ->
      item {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = MaterialTheme.shapes.medium,
          border = BorderStroke(1.dp, OutlineSubtle),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "${stringResource(R.string.label_psx_exe)} (${exe.fileName})",
              style = MaterialTheme.typography.titleMedium,
              color = MillenniumGold,
            )
            Text(
              text =
                "Magic: ${if (exe.magicValid) "PS-X EXE (OK)" else "Custom"} • CRC32: ${exe.crc32Hex}",
              style = MaterialTheme.typography.labelMedium,
            )
            Text(
              text =
                "PC0: 0x${exe.pc0.toString(16).uppercase()}  •  GP0: 0x${exe.gp0.toString(16).uppercase()}  •  Text: 0x${exe.textAddress.toString(16).uppercase()} (${formatByteSize(exe.textSize)})",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (exe.markerText.isNotBlank()) {
              Text(
                text = exe.markerText,
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan,
              )
            }
          }
        }
      }
    }

    if (analysis.files.isNotEmpty()) {
      item {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          label = { Text("Filter ISO-9660 files (${analysis.files.size} total)") },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("iso_file_search_input"),
        )
      }

      items(filteredFiles, key = { "${it.fullPath}_${it.lba}" }) { entry ->
        IsoEntryRow(entry = entry, onClick = { onJumpToSector(entry.lba) })
      }
    }
  }
}

@Composable
private fun IsoEntryRow(entry: IsoFileEntry, onClick: () -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, OutlineSubtle),
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
        contentDescription = null,
        tint = if (entry.isDirectory) MillenniumGold else CyberCyan,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = entry.fullPath,
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "LBA ${entry.lba} • ${if (entry.isDirectory) "Directory" else formatByteSize(entry.sizeBytes)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TechPill("HEX", CyberCyan)
    }
  }
}

@Composable
private fun ExeCardScanTab(
  state: InspectionUiState.Loaded,
  onScanStrings: () -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    item {
      SectionHeader(
        title = "Binary String & Card Table Scanner",
        subtitle =
          "Reads readable ASCII/mod strings directly from your loaded SLUS executable or WA_MRG.MRG sectors.",
      )
    }

    item {
      Button(
        onClick = onScanStrings,
        enabled = !state.isScanning,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("scan_bin_strings_button"),
      ) {
        if (state.isScanning) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Scanning sectors...")
        } else {
          Icon(Icons.Filled.Code, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.btn_scan_cards))
        }
      }
    }

    state.scanResult?.let { scan ->
      item {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = MaterialTheme.shapes.medium,
          border = BorderStroke(1.dp, OutlineSubtle),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Scanned: ${scan.scannedFileName} (${formatByteSize(scan.scannedBytes)})",
              style = MaterialTheme.typography.titleMedium,
              color = MillenniumGold,
            )
            Text(
              text =
                "Extracted ${scan.extractedStrings.size} ASCII strings • ${scan.detectedCardKeywords.size} Yu-Gi-Oh!/Mod keyword hits",
              style = MaterialTheme.typography.labelMedium,
              color = CyberCyan,
            )
          }
        }
      }

      if (scan.detectedCardKeywords.isNotEmpty()) {
        item {
          Text(
            text = "Key Disc / Mod Signatures",
            style = MaterialTheme.typography.titleMedium,
          )
        }
        items(scan.detectedCardKeywords) { kw ->
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = kw,
              style = MaterialTheme.typography.labelMedium,
              color = EmeraldVerified,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }

      item {
        Text(
          text = "Extracted Binary Strings",
          style = MaterialTheme.typography.titleMedium,
        )
      }
      items(scan.extractedStrings) { str ->
        Text(
          text = str,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun SectorHexTab(
  analysis: PsxDiscAnalysis,
  currentLba: Int,
  hexDump: SectorHexDump?,
  isReading: Boolean,
  onReadSector: (Int) -> Unit,
) {
  var lbaInput by remember(currentLba) { mutableStateOf(currentLba.toString()) }

  val quickTargets =
    remember(analysis) {
      buildList {
        add("PVD (16)" to 16)
        analysis.files
          .firstOrNull { it.name.equals("SYSTEM.CNF", true) }
          ?.let { add("SYSTEM.CNF (${it.lba})" to it.lba) }
        analysis.files
          .firstOrNull { it.name.startsWith("SL", true) }
          ?.let { add("BOOT EXE (${it.lba})" to it.lba) }
        analysis.waMrgEntry?.let { add("WA_MRG (${it.lba})" to it.lba) }
      }
    }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        quickTargets.forEach { (label, lba) ->
          FilterChip(
            selected = currentLba == lba,
            onClick = { onReadSector(lba) },
            label = { Text(label) },
          )
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = lbaInput,
          onValueChange = { lbaInput = it.filter { ch -> ch.isDigit() } },
          label = { Text(stringResource(R.string.hint_sector_number)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("lba_sector_input"),
        )
        Button(
          onClick = { onReadSector(lbaInput.toIntOrNull() ?: 16) },
          modifier = Modifier.heightIn(min = 48.dp).testTag("read_sector_button"),
        ) {
          Text(stringResource(R.string.btn_read_sector))
        }
      }
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = { onReadSector((currentLba - 1).coerceAtLeast(0)) },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
          Text("Prev Sector")
        }
        OutlinedButton(
          onClick = { onReadSector(currentLba + 1) },
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
          Text("Next Sector")
        }
      }
    }

    if (isReading) {
      item {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = CyberCyan)
        }
      }
    } else if (hexDump != null) {
      item {
        Text(
          text =
            "LBA ${hexDump.lba} • Byte Offset 0x${hexDump.byteOffset.toString(16).uppercase()} • ${hexDump.rawBytes.size} bytes",
          style = MaterialTheme.typography.labelMedium,
          color = MillenniumGold,
        )
      }
      val rows = formatHexLines(hexDump.rawBytes)
      items(rows) { line ->
        Text(
          text = line,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

private fun formatHexLines(bytes: ByteArray): List<String> {
  val lines = ArrayList<String>((bytes.size + 15) / 16)
  var offset = 0
  while (offset < bytes.size) {
    val end = minOf(offset + 16, bytes.size)
    val hexPart = StringBuilder()
    val asciiPart = StringBuilder()
    for (i in offset until offset + 16) {
      if (i < end) {
        val v = bytes[i].toInt() and 0xFF
        hexPart.append(v.toString(16).uppercase().padStart(2, '0')).append(' ')
        asciiPart.append(if (v in 0x20..0x7E) v.toChar() else '.')
      } else {
        hexPart.append("   ")
        asciiPart.append(' ')
      }
    }
    lines.add("${offset.toString(16).uppercase().padStart(4, '0')}: $hexPart |$asciiPart|")
    offset += 16
  }
  return lines
}
