package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.screens.DirectRunnerScreen
import com.example.ui.screens.InspectorScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MemCardScreen
import com.example.ui.screens.PatcherScreen
import com.example.ui.screens.RecompiledConfigScreen
import com.example.ui.theme.FmModLoaderTheme
import com.example.ui.theme.MillenniumGold

private object Routes {
  const val LIBRARY = "library"
  const val INSPECTOR = "inspector"
  const val PATCHER = "patcher"
  const val MEMCARD = "memcard"
  const val RECOMPILED = "recompiled"
  const val RUNNER = "runner"
}

private data class NavTabItem(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

private val navTabs =
  listOf(
    NavTabItem(
      Routes.LIBRARY,
      R.string.nav_library,
      Icons.Filled.Album,
      Icons.Outlined.Album,
      "nav_library",
    ),
    NavTabItem(
      Routes.INSPECTOR,
      R.string.nav_inspector,
      Icons.Filled.Memory,
      Icons.Outlined.Memory,
      "nav_inspector",
    ),
    NavTabItem(
      Routes.PATCHER,
      R.string.nav_patcher,
      Icons.Filled.AutoFixHigh,
      Icons.Outlined.AutoFixHigh,
      "nav_patcher",
    ),
    NavTabItem(
      Routes.MEMCARD,
      R.string.nav_memcard,
      Icons.Filled.SdCard,
      Icons.Outlined.SdCard,
      "nav_memcard",
    ),
    NavTabItem(
      Routes.RECOMPILED,
      R.string.nav_recompiled,
      Icons.Filled.Tune,
      Icons.Outlined.Tune,
      "nav_recompiled",
    ),
  )

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { FmModLoaderTheme { FmModLoaderApp() } }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FmModLoaderApp(vm: MainViewModel = viewModel()) {
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: Routes.LIBRARY

  val discs by vm.discs.collectAsStateWithLifecycle()
  val libraryFilter by vm.libraryFilter.collectAsStateWithLifecycle()
  val isImportingDisc by vm.isImportingDisc.collectAsStateWithLifecycle()
  val isScanningFolders by vm.isScanningFolders.collectAsStateWithLifecycle()
  val grantedFolders by vm.grantedFolders.collectAsStateWithLifecycle()
  val showStoragePrompt by vm.showStoragePrompt.collectAsStateWithLifecycle()
  val inspectionState by vm.inspectionState.collectAsStateWithLifecycle()
  val patcherState by vm.patcherState.collectAsStateWithLifecycle()
  val memCardState by vm.memCardState.collectAsStateWithLifecycle()
  val directRunnerState by vm.directRunnerState.collectAsStateWithLifecycle()
  val settings by vm.settings.collectAsStateWithLifecycle()
  val installedEmulators by vm.installedEmulators.collectAsStateWithLifecycle()
  val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(snackbarMessage) {
    snackbarMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      vm.clearSnackbar()
    }
  }

  // Storage Access Framework (SAF) zero-permission file & folder pickers
  val grantFolderTreeLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
      treeUri?.let { vm.grantStorageFolder(it) }
    }

  val pickDiscLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) {
        vm.importMultipleDiscUris(uris)
      }
    }

  val pickPatchBaseLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let { vm.selectBaseDiscForPatch(it) }
    }

  val pickPpfLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let { vm.selectPpfPatchUri(it) }
    }

  val createPatchedBinLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
      uri?.let { vm.applyPpfToOutputUri(it) }
    }

  val pickMemCardLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let { vm.loadMemoryCard(it) }
    }

  val createMcsLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
      uri?.let { vm.exportPendingMcs(it) }
    }

  val createConfigJsonLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      uri?.let { vm.exportRecompiledConfigJson(it) }
    }

  fun navigateToTab(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MillenniumGold,
              )
              Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background,
              titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
      },
      bottomBar = {
        if (!useNavigationRail) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            navTabs.forEach { tab ->
              val selected = currentRoute == tab.route
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavigationRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight(),
          ) {
            navTabs.forEach { tab ->
              val selected = currentRoute == tab.route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          NavHost(navController = navController, startDestination = Routes.LIBRARY) {
            composable(Routes.LIBRARY) {
              LibraryScreen(
                discs = discs,
                filter = libraryFilter,
                settings = settings,
                grantedFolders = grantedFolders,
                isImporting = isImportingDisc,
                isScanningFolders = isScanningFolders,
                directRunnerState = directRunnerState,
                onFilterChange = vm::setLibraryFilter,
                onPickDiscFile = { pickDiscLauncher.launch(arrayOf("*/*")) },
                onGrantFolderAccess = { grantFolderTreeLauncher.launch(null) },
                onRescanFolders = vm::rescanGrantedFolders,
                onRevokeFolder = vm::revokeStorageFolder,
                onInspectDisc = { entity ->
                  vm.inspectDisc(entity)
                  navigateToTab(Routes.INSPECTOR)
                },
                onRunDirect = { entity ->
                  vm.launchDirectRunner(entity)
                  navController.navigate(Routes.RUNNER) { launchSingleTop = true }
                },
                onResumeRunner = {
                  navController.navigate(Routes.RUNNER) { launchSingleTop = true }
                },
                onRescanDisc = vm::rescanDisc,
                onRemoveDisc = vm::removeDisc,
              )
            }

            composable(Routes.RUNNER) {
              DirectRunnerScreen(
                runnerState = directRunnerState,
                settings = settings,
                onBack = { navController.popBackStack() },
                onStepCycles = vm::stepCpuCycles,
                onToggleAutoRun = vm::toggleCpuAutoRun,
                onResetCpu = vm::resetCpuBoot,
                onRollDrops = vm::rollModDrops,
                onOpenExternalEmu = vm::launchInEmulator,
              )
            }

            composable(Routes.INSPECTOR) {
              InspectorScreen(
                discs = discs,
                inspectionState = inspectionState,
                onSelectDiscToInspect = vm::inspectDisc,
                onReadSector = vm::readSectorHex,
                onScanStrings = vm::scanActiveDiscStrings,
                onGoToLibrary = { navigateToTab(Routes.LIBRARY) },
              )
            }

            composable(Routes.PATCHER) {
              PatcherScreen(
                discs = discs,
                state = patcherState,
                onPickBaseDisc = { pickPatchBaseLauncher.launch(arrayOf("*/*")) },
                onUseLibraryDiscAsBase = { disc ->
                  vm.selectBaseDiscForPatch(Uri.parse(disc.uriString), disc.displayName)
                },
                onPickPpfFile = { pickPpfLauncher.launch(arrayOf("*/*")) },
                onApplyAndSave = { suggestedName ->
                  createPatchedBinLauncher.launch(suggestedName)
                },
                onImportOutputToLibrary = { uriStr ->
                  vm.importDiscUri(Uri.parse(uriStr), autoInspect = true)
                  navigateToTab(Routes.INSPECTOR)
                },
              )
            }

            composable(Routes.MEMCARD) {
              MemCardScreen(
                memCardState = memCardState,
                onPickMemCard = { pickMemCardLauncher.launch(arrayOf("*/*")) },
                onExportSlotMcs = { slot ->
                  vm.prepareMcsExport(slot)
                  createMcsLauncher.launch("${slot.productCode}_slot${slot.slotNumber}.mcs")
                },
              )
            }

            composable(Routes.RECOMPILED) {
              RecompiledConfigScreen(
                settings = settings,
                installedEmulators = installedEmulators,
                onUpdateSettings = vm::updateSettings,
                onRefreshEmulators = vm::refreshInstalledEmulators,
                onExportJson = { createConfigJsonLauncher.launch("fm_recompiled_mod.json") },
              )
            }
          }
        }
      }
    }

    if (showStoragePrompt) {
      AlertDialog(
        onDismissRequest = vm::dismissStoragePrompt,
        title = { Text(stringResource(R.string.storage_perm_dialog_title)) },
        text = { Text(stringResource(R.string.storage_perm_dialog_desc)) },
        confirmButton = {
          Button(
            onClick = {
              vm.dismissStoragePrompt()
              pickDiscLauncher.launch(arrayOf("*/*"))
            },
            modifier = Modifier.testTag("pick_files_direct_dialog_button"),
          ) {
            Text(stringResource(R.string.btn_pick_files_direct))
          }
        },
        dismissButton = {
          Row {
            TextButton(
              onClick = {
                vm.dismissStoragePrompt()
                grantFolderTreeLauncher.launch(null)
              },
              modifier = Modifier.testTag("grant_storage_permission_button"),
            ) {
              Text(stringResource(R.string.btn_grant_storage_access))
            }
            TextButton(
              onClick = vm::dismissStoragePrompt,
              modifier = Modifier.testTag("dismiss_storage_prompt_button"),
            ) {
              Text(stringResource(R.string.btn_later))
            }
          }
        },
      )
    }
  }
}
