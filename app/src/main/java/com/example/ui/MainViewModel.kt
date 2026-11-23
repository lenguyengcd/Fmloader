package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.psx.DiscBinaryScanResult
import com.example.core.psx.DiscModStatus
import com.example.core.psx.MemCardSlot
import com.example.core.psx.PpfApplyResult
import com.example.core.psx.PpfPatchEngine
import com.example.core.psx.PpfPatchMetadata
import com.example.core.psx.PsxDiscAnalysis
import com.example.core.psx.PsxMemCardParser
import com.example.core.psx.PsxMemoryCard
import com.example.core.psx.SafFolderScanner
import com.example.core.psx.SectorHexDump
import com.example.data.AppDatabase
import com.example.data.DetectedEmulator
import com.example.data.DiscEntryEntity
import com.example.data.DiscRepository
import com.example.data.EmulatorLauncher
import com.example.data.RecompiledSettings
import com.example.data.RecompiledSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryFilter {
  ALL,
  MODDED,
  ORIGINAL,
}

sealed interface InspectionUiState {
  data object Idle : InspectionUiState

  data class Loading(val fileName: String) : InspectionUiState

  data class Loaded(
    val analysis: PsxDiscAnalysis,
    val scanResult: DiscBinaryScanResult? = null,
    val isScanning: Boolean = false,
    val hexDump: SectorHexDump? = null,
    val isReadingHex: Boolean = false,
    val currentLba: Int = 16,
  ) : InspectionUiState

  data class Error(val message: String) : InspectionUiState
}

data class PatcherUiState(
  val baseDiscUri: String? = null,
  val baseDiscName: String? = null,
  val ppfMetadata: PpfPatchMetadata? = null,
  val isInspectingPpf: Boolean = false,
  val isPatching: Boolean = false,
  val progress: Float = 0f,
  val lastResult: PpfApplyResult? = null,
  val lastOutputUri: String? = null,
  val errorMessage: String? = null,
)

sealed interface MemCardUiState {
  data object Idle : MemCardUiState

  data object Loading : MemCardUiState

  data class Loaded(val card: PsxMemoryCard) : MemCardUiState

  data class Error(val message: String) : MemCardUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext
  private val contentResolver = appContext.contentResolver
  private val discRepository =
    DiscRepository(AppDatabase.getInstance(appContext).discEntryDao(), contentResolver)
  private val settingsRepository = RecompiledSettingsRepository(appContext)

  val discs: StateFlow<List<DiscEntryEntity>> =
    discRepository.allDiscs.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val settings: StateFlow<RecompiledSettings> =
    settingsRepository.settings.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = RecompiledSettings(),
    )

  val showStoragePrompt: StateFlow<Boolean> =
    settingsRepository.settings
      .map { it.grantedFolderUris.isEmpty() && !it.storagePromptDismissed }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
      )

  val grantedFolders: StateFlow<List<Pair<String, String>>> =
    settingsRepository.settings
      .map { s ->
        s.grantedFolderUris.map { uriStr ->
          uriStr to SafFolderScanner.getFolderDisplayName(contentResolver, Uri.parse(uriStr))
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  private val _libraryFilter = MutableStateFlow(LibraryFilter.ALL)
  val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter.asStateFlow()

  private val _isImportingDisc = MutableStateFlow(false)
  val isImportingDisc: StateFlow<Boolean> = _isImportingDisc.asStateFlow()

  private val _isScanningFolders = MutableStateFlow(false)
  val isScanningFolders: StateFlow<Boolean> = _isScanningFolders.asStateFlow()

  private val _inspectionState = MutableStateFlow<InspectionUiState>(InspectionUiState.Idle)
  val inspectionState: StateFlow<InspectionUiState> = _inspectionState.asStateFlow()

  private val _patcherState = MutableStateFlow(PatcherUiState())
  val patcherState: StateFlow<PatcherUiState> = _patcherState.asStateFlow()

  private val _memCardState = MutableStateFlow<MemCardUiState>(MemCardUiState.Idle)
  val memCardState: StateFlow<MemCardUiState> = _memCardState.asStateFlow()

  private val _pendingMcsExportSlot = MutableStateFlow<MemCardSlot?>(null)
  val pendingMcsExportSlot: StateFlow<MemCardSlot?> = _pendingMcsExportSlot.asStateFlow()

  private val _installedEmulators = MutableStateFlow<List<DetectedEmulator>>(emptyList())
  val installedEmulators: StateFlow<List<DetectedEmulator>> = _installedEmulators.asStateFlow()

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  init {
    refreshInstalledEmulators()
  }

  fun refreshInstalledEmulators() {
    _installedEmulators.value = EmulatorLauncher.detectInstalledEmulators(appContext)
  }

  fun setLibraryFilter(filter: LibraryFilter) {
    _libraryFilter.value = filter
  }

  fun clearSnackbar() {
    _snackbarMessage.value = null
  }

  fun dismissStoragePrompt() {
    viewModelScope.launch {
      settingsRepository.update { it.copy(storagePromptDismissed = true) }
    }
  }

  fun grantStorageFolder(treeUri: Uri) {
    takePersistableReadPermission(treeUri)
    viewModelScope.launch {
      settingsRepository.update {
        it.copy(
          grantedFolderUris = it.grantedFolderUris + treeUri.toString(),
          storagePromptDismissed = true,
        )
      }
      scanSingleFolderAndImport(treeUri)
    }
  }

  fun rescanGrantedFolders() {
    val folderUris = settings.value.grantedFolderUris
    if (folderUris.isEmpty()) return
    viewModelScope.launch {
      _isScanningFolders.value = true
      var totalAdded = 0
      folderUris.forEach { uriStr ->
        val found =
          SafFolderScanner.scanTreeForDiscs(contentResolver, Uri.parse(uriStr)).getOrDefault(emptyList())
        found.forEach { file ->
          discRepository.importAndAnalyzeDisc(file.uri).onSuccess { totalAdded++ }
        }
      }
      _isScanningFolders.value = false
      _snackbarMessage.value =
        appContext.getString(R.string.msg_folder_scanned, "${folderUris.size} folder(s)", totalAdded)
    }
  }

  fun revokeStorageFolder(uriString: String) {
    try {
      contentResolver.releasePersistableUriPermission(
        Uri.parse(uriString),
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
      )
    } catch (_: SecurityException) {
      // Ignore if already released
    }
    viewModelScope.launch {
      settingsRepository.update { it.copy(grantedFolderUris = it.grantedFolderUris - uriString) }
    }
  }

  private suspend fun scanSingleFolderAndImport(treeUri: Uri) {
    _isScanningFolders.value = true
    val folderName = SafFolderScanner.getFolderDisplayName(contentResolver, treeUri)
    val scanRes = SafFolderScanner.scanTreeForDiscs(contentResolver, treeUri)
    var imported = 0
    scanRes
      .onSuccess { files ->
        files.forEach { file ->
          discRepository.importAndAnalyzeDisc(file.uri).onSuccess { analysis ->
            imported++
            if (_inspectionState.value is InspectionUiState.Idle) {
              setInspectionLoaded(analysis)
            }
          }
        }
        _snackbarMessage.value = appContext.getString(R.string.msg_folder_scanned, folderName, imported)
      }
      .onFailure { err ->
        _snackbarMessage.value = err.message ?: appContext.getString(R.string.err_invalid_uri)
      }
    _isScanningFolders.value = false
  }

  fun importMultipleDiscUris(uris: List<Uri>) {
    if (uris.isEmpty()) return
    if (uris.size == 1) {
      importDiscUri(uris.first(), autoInspect = false)
      return
    }
    viewModelScope.launch {
      _isImportingDisc.value = true
      var successCount = 0
      uris.forEach { uri ->
        takePersistableReadPermission(uri)
        discRepository.importAndAnalyzeDisc(uri).onSuccess { analysis ->
          successCount++
          if (_inspectionState.value is InspectionUiState.Idle) {
            setInspectionLoaded(analysis)
          }
        }
      }
      _isImportingDisc.value = false
      _snackbarMessage.value =
        if (successCount > 0) "Loaded $successCount PS1 disc(s)."
        else appContext.getString(R.string.err_invalid_uri)
    }
  }

  fun importDiscUri(uri: Uri, autoInspect: Boolean = false) {
    takePersistableReadPermission(uri)
    viewModelScope.launch {
      _isImportingDisc.value = true
      val result = discRepository.importAndAnalyzeDisc(uri)
      _isImportingDisc.value = false
      result
        .onSuccess { analysis ->
          _snackbarMessage.value =
            appContext.getString(R.string.msg_disc_added, analysis.displayName, analysis.serialCode)
          if (autoInspect || _inspectionState.value is InspectionUiState.Idle) {
            setInspectionLoaded(analysis)
          }
        }
        .onFailure { err ->
          _snackbarMessage.value =
            err.message ?: appContext.getString(R.string.err_invalid_uri)
        }
    }
  }

  fun inspectDisc(entity: DiscEntryEntity) {
    val uri = Uri.parse(entity.uriString)
    viewModelScope.launch {
      _inspectionState.value = InspectionUiState.Loading(entity.displayName)
      discRepository
        .inspectDisc(uri)
        .onSuccess { analysis -> setInspectionLoaded(analysis) }
        .onFailure { err ->
          _inspectionState.value =
            InspectionUiState.Error(err.message ?: appContext.getString(R.string.err_invalid_uri))
        }
    }
  }

  fun rescanDisc(entity: DiscEntryEntity) {
    importDiscUri(Uri.parse(entity.uriString), autoInspect = false)
  }

  fun removeDisc(entity: DiscEntryEntity) {
    viewModelScope.launch {
      discRepository.removeDisc(entity.uriString)
      val current = _inspectionState.value
      if (current is InspectionUiState.Loaded && current.analysis.uriString == entity.uriString) {
        _inspectionState.value = InspectionUiState.Idle
      }
    }
  }

  private fun setInspectionLoaded(analysis: PsxDiscAnalysis) {
    _inspectionState.value = InspectionUiState.Loaded(analysis = analysis, currentLba = 16)
    readSectorHex(16)
  }

  fun readSectorHex(lba: Int) {
    val current = _inspectionState.value as? InspectionUiState.Loaded ?: return
    val safeLba = lba.coerceAtLeast(0)
    viewModelScope.launch {
      _inspectionState.update { state ->
        if (state is InspectionUiState.Loaded) {
          state.copy(isReadingHex = true, currentLba = safeLba)
        } else {
          state
        }
      }
      val hex =
        discRepository
          .readSectorHex(
            uri = Uri.parse(current.analysis.uriString),
            lba = safeLba,
            sectorStride = current.analysis.sectorStride,
            userDataOffset = current.analysis.userDataOffset,
          )
          .getOrNull()
      _inspectionState.update { state ->
        if (state is InspectionUiState.Loaded) {
          state.copy(isReadingHex = false, hexDump = hex, currentLba = safeLba)
        } else {
          state
        }
      }
    }
  }

  fun scanActiveDiscStrings() {
    val current = _inspectionState.value as? InspectionUiState.Loaded ?: return
    viewModelScope.launch {
      _inspectionState.update { state ->
        if (state is InspectionUiState.Loaded) state.copy(isScanning = true) else state
      }
      val res =
        discRepository.scanDiscStrings(Uri.parse(current.analysis.uriString), current.analysis)
      _inspectionState.update { state ->
        if (state is InspectionUiState.Loaded) {
          state.copy(isScanning = false, scanResult = res.getOrNull())
        } else {
          state
        }
      }
      res.onFailure { err -> _snackbarMessage.value = err.message ?: "Scan failed" }
    }
  }

  fun launchInEmulator(entity: DiscEntryEntity) {
    val uri = Uri.parse(entity.uriString)
    val preferred = settings.value.preferredEmulatorPackage
    EmulatorLauncher.openDiscInEmulator(appContext, uri, preferred)
      .onSuccess { targetedEmulator ->
        if (!targetedEmulator) {
          _snackbarMessage.value = appContext.getString(R.string.msg_no_emulator_found)
        }
      }
      .onFailure { err ->
        _snackbarMessage.value = err.message ?: "Unable to launch emulator."
      }
  }

  // PPF Mod Patcher Actions
  fun selectBaseDiscForPatch(uri: Uri, knownName: String? = null) {
    takePersistableReadPermission(uri)
    viewModelScope.launch {
      val name =
        knownName
          ?: withContext(Dispatchers.IO) {
            uri.lastPathSegment?.substringAfterLast('/') ?: "base_disc.bin"
          }
      _patcherState.update {
        it.copy(
          baseDiscUri = uri.toString(),
          baseDiscName = name,
          lastResult = null,
          errorMessage = null,
        )
      }
    }
  }

  fun selectPpfPatchUri(uri: Uri) {
    takePersistableReadPermission(uri)
    viewModelScope.launch {
      _patcherState.update { it.copy(isInspectingPpf = true, errorMessage = null, lastResult = null) }
      PpfPatchEngine.inspectPpf(contentResolver, uri)
        .onSuccess { meta ->
          _patcherState.update {
            it.copy(isInspectingPpf = false, ppfMetadata = meta, errorMessage = null)
          }
        }
        .onFailure { err ->
          _patcherState.update {
            it.copy(
              isInspectingPpf = false,
              ppfMetadata = null,
              errorMessage = err.message ?: "Invalid PPF patch file.",
            )
          }
        }
    }
  }

  fun applyPpfToOutputUri(outputUri: Uri) {
    val state = _patcherState.value
    val baseStr = state.baseDiscUri ?: return
    val meta = state.ppfMetadata ?: return
    viewModelScope.launch {
      _patcherState.update {
        it.copy(isPatching = true, progress = 0f, errorMessage = null, lastResult = null)
      }
      PpfPatchEngine.applyPpfPatch(
          contentResolver = contentResolver,
          baseUri = Uri.parse(baseStr),
          ppfUri = Uri.parse(meta.uriString),
          outputUri = outputUri,
          meta = meta,
          onProgress = { pct -> _patcherState.update { it.copy(progress = pct) } },
        )
        .onSuccess { result ->
          _patcherState.update {
            it.copy(
              isPatching = false,
              progress = 1f,
              lastResult = result,
              lastOutputUri = outputUri.toString(),
            )
          }
          _snackbarMessage.value =
            appContext.getString(R.string.msg_patch_success, result.chunksApplied)
        }
        .onFailure { err ->
          _patcherState.update {
            it.copy(
              isPatching = false,
              errorMessage = err.message ?: "Failed to apply PPF patch.",
            )
          }
        }
    }
  }

  // PS1 Memory Card Actions
  fun loadMemoryCard(uri: Uri) {
    takePersistableReadPermission(uri)
    viewModelScope.launch {
      _memCardState.value = MemCardUiState.Loading
      PsxMemCardParser.parseMemoryCard(contentResolver, uri)
        .onSuccess { card -> _memCardState.value = MemCardUiState.Loaded(card) }
        .onFailure { err ->
          _memCardState.value = MemCardUiState.Error(err.message ?: "Failed to read memory card.")
        }
    }
  }

  fun prepareMcsExport(slot: MemCardSlot) {
    _pendingMcsExportSlot.value = slot
  }

  fun exportPendingMcs(outputUri: Uri) {
    val slot = _pendingMcsExportSlot.value ?: return
    val loaded = _memCardState.value as? MemCardUiState.Loaded ?: return
    _pendingMcsExportSlot.value = null
    viewModelScope.launch {
      PsxMemCardParser.exportSlotAsMcs(
          contentResolver = contentResolver,
          memCardUri = Uri.parse(loaded.card.uriString),
          slot = slot,
          outputUri = outputUri,
        )
        .onSuccess { _snackbarMessage.value = "Exported ${slot.productCode}.mcs" }
        .onFailure { err -> _snackbarMessage.value = err.message ?: "Export failed" }
    }
  }

  // Recompiled Settings Actions
  fun updateSettings(transform: (RecompiledSettings) -> RecompiledSettings) {
    viewModelScope.launch { settingsRepository.update(transform) }
  }

  fun exportRecompiledConfigJson(outputUri: Uri) {
    viewModelScope.launch {
      val active = (_inspectionState.value as? InspectionUiState.Loaded)?.analysis
      val json = settings.value.toModJson(active?.serialCode, active?.displayName)
      withContext(Dispatchers.IO) {
        runCatching {
          contentResolver.openOutputStream(outputUri, "wt")?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
          } ?: error("Cannot write config file.")
        }
      }
        .onSuccess { _snackbarMessage.value = appContext.getString(R.string.msg_config_exported) }
        .onFailure { err -> _snackbarMessage.value = err.message ?: "Config export failed" }
    }
  }

  private fun takePersistableReadPermission(uri: Uri) {
    try {
      contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
      // Non-persistable provider; still readable during active session
    }
  }

  fun matchesFilter(entity: DiscEntryEntity, filter: LibraryFilter): Boolean {
    return when (filter) {
      LibraryFilter.ALL -> true
      LibraryFilter.MODDED -> entity.modStatusName == DiscModStatus.MODDED_FM.name
      LibraryFilter.ORIGINAL -> entity.modStatusName == DiscModStatus.ORIGINAL_FM.name
    }
  }
}
