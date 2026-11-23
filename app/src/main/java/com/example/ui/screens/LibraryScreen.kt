package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.psx.DiscFormat
import com.example.core.psx.DiscModStatus
import com.example.data.DiscEntryEntity
import com.example.data.RecompiledSettings
import com.example.ui.LibraryFilter
import com.example.ui.components.MetricCard
import com.example.ui.components.ModStatusBadge
import com.example.ui.components.TechPill
import com.example.ui.components.formatByteSize
import com.example.ui.theme.CrimsonMod
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldVerified
import com.example.ui.theme.MillenniumGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineSubtle

@Composable
fun LibraryScreen(
  discs: List<DiscEntryEntity>,
  filter: LibraryFilter,
  settings: RecompiledSettings,
  grantedFolders: List<Pair<String, String>>,
  isImporting: Boolean,
  isScanningFolders: Boolean,
  onFilterChange: (LibraryFilter) -> Unit,
  onPickDiscFile: () -> Unit,
  onGrantFolderAccess: () -> Unit,
  onRescanFolders: () -> Unit,
  onRevokeFolder: (String) -> Unit,
  onInspectDisc: (DiscEntryEntity) -> Unit,
  onLaunchEmulator: (DiscEntryEntity) -> Unit,
  onRescanDisc: (DiscEntryEntity) -> Unit,
  onRemoveDisc: (DiscEntryEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val filtered =
    discs.filter { entry ->
      when (filter) {
        LibraryFilter.ALL -> true
        LibraryFilter.MODDED -> entry.modStatusName == DiscModStatus.MODDED_FM.name
        LibraryFilter.ORIGINAL -> entry.modStatusName == DiscModStatus.ORIGINAL_FM.name
      }
    }
  val moddedCount = discs.count { it.modStatusName == DiscModStatus.MODDED_FM.name }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        HeroLoaderBanner(
          bypassEnabled = settings.bypassOriginalBinCheck,
          isImporting = isImporting,
          onPickDiscFile = onPickDiscFile,
        )
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MetricCard(
            label = "LOADED DISCS",
            value = "${discs.size} ROMs",
            icon = Icons.Filled.Album,
            accentColor = CyberCyan,
            modifier = Modifier.weight(1f),
          )
          MetricCard(
            label = "MODDED BUILDS",
            value = "$moddedCount Active",
            icon = Icons.Filled.Tune,
            accentColor = CrimsonMod,
            modifier = Modifier.weight(1f),
          )
          MetricCard(
            label = "VANILLA LOCK",
            value = if (settings.bypassOriginalBinCheck) "Bypassed" else "Strict",
            icon = Icons.Filled.LockOpen,
            accentColor = if (settings.bypassOriginalBinCheck) EmeraldVerified else MillenniumGold,
            modifier = Modifier.weight(1f),
          )
        }
      }

      item {
        StorageAccessStatusCard(
          grantedFolders = grantedFolders,
          isScanningFolders = isScanningFolders,
          onPickDiscFile = onPickDiscFile,
          onGrantFolderAccess = onGrantFolderAccess,
          onRescanFolders = onRescanFolders,
          onRevokeFolder = onRevokeFolder,
        )
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LibraryFilter.entries.forEach { item ->
            val selected = filter == item
            val label =
              when (item) {
                LibraryFilter.ALL -> "All (${discs.size})"
                LibraryFilter.MODDED -> "Modded ($moddedCount)"
                LibraryFilter.ORIGINAL -> "Original (${discs.size - moddedCount})"
              }
            FilterChip(
              selected = selected,
              onClick = { onFilterChange(item) },
              label = { Text(label, style = MaterialTheme.typography.labelLarge) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
              modifier = Modifier.testTag("filter_chip_${item.name.lowercase()}"),
            )
          }
        }
      }

      if (filtered.isEmpty()) {
        item { EmptyDiscLibraryCard(onPickDiscFile = onPickDiscFile) }
      } else {
        itemsIndexed(filtered, key = { _, it -> it.uriString }) { idx, disc ->
          DiscLibraryCard(
            disc = disc,
            index = idx,
            bypassActive = settings.bypassOriginalBinCheck,
            onInspect = { onInspectDisc(disc) },
            onLaunch = { onLaunchEmulator(disc) },
            onRescan = { onRescanDisc(disc) },
            onRemove = { onRemoveDisc(disc) },
          )
        }
      }

      item { Spacer(modifier = Modifier.height(72.dp)) }
    }
  }
}

@Composable
private fun HeroLoaderBanner(
  bypassEnabled: Boolean,
  isImporting: Boolean,
  onPickDiscFile: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, MillenniumGold.copy(alpha = 0.35f)),
    color = MaterialTheme.colorScheme.surfaceContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Image(
        painter = painterResource(id = R.drawable.img_hero_banner),
        contentDescription = "FM Mod Loader Hero Banner",
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize(),
      )
      Box(
        modifier =
          Modifier.matchParentSize()
            .background(
              Brush.horizontalGradient(
                colors =
                  listOf(
                    ObsidianBg.copy(alpha = 0.96f),
                    ObsidianBg.copy(alpha = 0.84f),
                    ObsidianBg.copy(alpha = 0.45f),
                  )
              )
            )
      )

      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        TechPill(
          text =
            if (bypassEnabled) stringResource(R.string.hero_badge) else "STRICT VANILLA CHECK ON",
          accentColor = if (bypassEnabled) EmeraldVerified else MillenniumGold,
        )
        Text(
          text = stringResource(R.string.hero_title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = stringResource(R.string.hero_desc),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = onPickDiscFile,
            enabled = !isImporting,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.heightIn(min = 48.dp).testTag("load_rom_button"),
          ) {
            if (isImporting) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Parsing ISO-9660...")
            } else {
              Icon(Icons.Filled.FolderOpen, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.btn_load_rom))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyDiscLibraryCard(onPickDiscFile: () -> Unit) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, OutlineSubtle),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_empty_disc),
        contentDescription = "Empty disc tray illustration",
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(14.dp)),
      )
      Text(
        text = stringResource(R.string.empty_library_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = stringResource(R.string.empty_library_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        TechPill(".BIN (2352)", CyberCyan, Modifier.weight(1f))
        TechPill(".ISO (2048)", MillenniumGold, Modifier.weight(1f))
        TechPill(".PBP / .CUE", EmeraldVerified, Modifier.weight(1f))
      }
      OutlinedButton(
        onClick = onPickDiscFile,
        border = BorderStroke(1.dp, MillenniumGold.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("empty_pick_rom_button"),
      ) {
        Icon(Icons.Filled.AddCircle, contentDescription = null, tint = MillenniumGold)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.btn_load_rom), color = MillenniumGold)
      }
    }
  }
}

@Composable
private fun DiscLibraryCard(
  disc: DiscEntryEntity,
  index: Int,
  bypassActive: Boolean,
  onInspect: () -> Unit,
  onLaunch: () -> Unit,
  onRescan: () -> Unit,
  onRemove: () -> Unit,
) {
  val formatLabel =
    runCatching { DiscFormat.valueOf(disc.formatName).label }.getOrDefault(disc.formatName)

  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    border =
      BorderStroke(
        1.dp,
        if (disc.modStatusName == DiscModStatus.MODDED_FM.name) {
          CrimsonMod.copy(alpha = 0.45f)
        } else {
          OutlineSubtle
        },
      ),
    modifier = Modifier.fillMaxWidth().testTag("disc_card_$index"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            ModStatusBadge(disc.modStatusName)
            TechPill(formatLabel, CyberCyan)
            if (bypassActive) {
              Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.label_bypass_active),
                tint = EmeraldVerified,
                modifier = Modifier.size(16.dp),
              )
            }
          }
          Text(
            text = disc.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              "${disc.serialCode} • ${disc.region} • Vol: ${disc.volumeId} • ${formatByteSize(disc.fileSizeBytes)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row {
          IconButton(
            onClick = onRescan,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("rescan_disc_$index"),
          ) {
            Icon(
              Icons.Filled.Refresh,
              contentDescription = stringResource(R.string.btn_rescan),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = onRemove,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("remove_disc_$index"),
          ) {
            Icon(
              Icons.Filled.DeleteOutline,
              contentDescription = stringResource(R.string.action_remove_disc),
              tint = CrimsonMod,
            )
          }
        }
      }

      if (disc.modSummary.isNotBlank()) {
        Text(
          text = disc.modSummary,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (disc.waMrgSizeBytes > 0L) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "WA_MRG.MRG: ${formatByteSize(disc.waMrgSizeBytes)}",
              style = MaterialTheme.typography.labelMedium,
              color = MillenniumGold,
            )
            if (!disc.waMrgSampleCrc32.isNullOrBlank()) {
              Text(
                text = "CRC32 ${disc.waMrgSampleCrc32}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onInspect,
          border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
          modifier =
            Modifier.weight(1f).heightIn(min = 48.dp).testTag("inspect_disc_button_$index"),
        ) {
          Icon(Icons.Filled.Search, contentDescription = null, tint = CyberCyan)
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.action_inspect), color = CyberCyan, maxLines = 1)
        }
        Button(
          onClick = onLaunch,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MillenniumGold,
              contentColor = Color(0xFF211700),
            ),
          modifier =
            Modifier.weight(1f).heightIn(min = 48.dp).testTag("launch_emu_button_$index"),
        ) {
          Icon(Icons.Filled.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.action_launch_emulator), maxLines = 1)
        }
      }
    }
  }
}

@Composable
private fun StorageAccessStatusCard(
  grantedFolders: List<Pair<String, String>>,
  isScanningFolders: Boolean,
  onPickDiscFile: () -> Unit,
  onGrantFolderAccess: () -> Unit,
  onRescanFolders: () -> Unit,
  onRevokeFolder: (String) -> Unit,
) {
  val hasAccess = grantedFolders.isNotEmpty()
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
    border =
      BorderStroke(
        1.dp,
        if (hasAccess) EmeraldVerified.copy(alpha = 0.45f) else MillenniumGold.copy(alpha = 0.45f),
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Security,
          contentDescription = null,
          tint = if (hasAccess) EmeraldVerified else MillenniumGold,
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text =
              if (hasAccess) stringResource(R.string.storage_status_granted, grantedFolders.size)
              else stringResource(R.string.storage_status_missing),
            style = MaterialTheme.typography.titleMedium,
            color = if (hasAccess) EmeraldVerified else MillenniumGold,
          )
          Text(
            text = stringResource(R.string.storage_perm_dialog_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (hasAccess) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          grantedFolders.forEach { (uriStr, folderName) ->
            FilterChip(
              selected = true,
              onClick = { onRevokeFolder(uriStr) },
              label = { Text(folderName) },
              trailingIcon = {
                Icon(
                  Icons.Filled.Close,
                  contentDescription = "Revoke folder access",
                  modifier = Modifier.size(16.dp),
                )
              },
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = onRescanFolders,
            enabled = !isScanningFolders,
            modifier =
              Modifier.weight(1f).heightIn(min = 48.dp).testTag("rescan_folders_button"),
          ) {
            if (isScanningFolders) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Scanning...")
            } else {
              Icon(Icons.Filled.Refresh, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text(stringResource(R.string.btn_scan_folders))
            }
          }
          OutlinedButton(
            onClick = onGrantFolderAccess,
            enabled = !isScanningFolders,
            modifier =
              Modifier.weight(1f).heightIn(min = 48.dp).testTag("add_folder_permission_button"),
          ) {
            Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.btn_add_folder))
          }
        }
      } else {
        Button(
          onClick = onPickDiscFile,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MillenniumGold,
              contentColor = Color(0xFF211700),
            ),
          modifier =
            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("library_pick_direct_button"),
        ) {
          Icon(Icons.Filled.FolderOpen, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.btn_pick_files_direct))
        }
        OutlinedButton(
          onClick = onGrantFolderAccess,
          modifier =
            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("library_grant_storage_button"),
        ) {
          Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.btn_grant_storage_access))
        }
      }
    }
  }
}
