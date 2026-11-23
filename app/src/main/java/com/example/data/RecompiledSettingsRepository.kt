package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recompiledDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "fm_recompiled_settings")

data class RecompiledSettings(
  val bypassOriginalBinCheck: Boolean = true,
  val allowExpandedCardIds: Boolean = true,
  val resolutionScale: Int = 2,
  val widescreen16x9: Boolean = true,
  val pgxpEnabled: Boolean = true,
  val hdFonts: Boolean = true,
  val liveDuelRankMeter: Boolean = true,
  val fusionAssistant: Boolean = true,
  val fastText60Fps: Boolean = true,
  val dropRateMultiplier: Int = 1,
  val preferredEmulatorPackage: String = "AUTO",
  val grantedFolderUris: Set<String> = emptySet(),
  val storagePromptDismissed: Boolean = false,
) {
  fun toModJson(activeDiscSerial: String?, activeDiscName: String?): String =
    """
    |{
    |  "profile": "Yu-Gi-Oh-Forbidden-Memories-Recompiled-Mod-Loader",
    |  "loaderMode": "ePSXe-Compatible-Any-ROM",
    |  "bypassVanillaBinHashCheck": $bypassOriginalBinCheck,
    |  "allowExpandedCardIds": $allowExpandedCardIds,
    |  "activeDisc": {
    |    "name": "${activeDiscName.orEmpty().replace("\"", "'")}",
    |    "serial": "${activeDiscSerial.orEmpty()}"
    |  },
    |  "video": {
    |    "internalResolutionScale": $resolutionScale,
    |    "widescreen16x9": $widescreen16x9,
    |    "pgxpGeometryCorrection": $pgxpEnabled,
    |    "hdFonts": $hdFonts
    |  },
    |  "gameplay": {
    |    "liveDuelRankMeter": $liveDuelRankMeter,
    |    "fusionAssistantOverlay": $fusionAssistant,
    |    "fastText60Fps": $fastText60Fps,
    |    "cardDropMultiplier": $dropRateMultiplier
    |  },
    |  "preferredAndroidEmulator": "$preferredEmulatorPackage"
    |}
    """
      .trimMargin()
}

class RecompiledSettingsRepository(private val context: Context) {

  private object Keys {
    val BYPASS_ORIGINAL_BIN = booleanPreferencesKey("bypass_original_bin")
    val EXPANDED_CARD_IDS = booleanPreferencesKey("expanded_card_ids")
    val RESOLUTION_SCALE = intPreferencesKey("resolution_scale")
    val WIDESCREEN = booleanPreferencesKey("widescreen_16_9")
    val PGXP = booleanPreferencesKey("pgxp_enabled")
    val HD_FONTS = booleanPreferencesKey("hd_fonts")
    val DUEL_RANK_METER = booleanPreferencesKey("duel_rank_meter")
    val FUSION_ASSISTANT = booleanPreferencesKey("fusion_assistant")
    val FAST_TEXT = booleanPreferencesKey("fast_text_60fps")
    val DROP_MULTIPLIER = intPreferencesKey("drop_multiplier")
    val PREFERRED_EMULATOR = stringPreferencesKey("preferred_emulator")
    val GRANTED_FOLDER_URIS = stringSetPreferencesKey("granted_folder_uris")
    val STORAGE_PROMPT_DISMISSED = booleanPreferencesKey("storage_prompt_dismissed")
  }

  val settings: Flow<RecompiledSettings> =
    context.recompiledDataStore.data.map { prefs ->
      RecompiledSettings(
        bypassOriginalBinCheck = prefs[Keys.BYPASS_ORIGINAL_BIN] ?: true,
        allowExpandedCardIds = prefs[Keys.EXPANDED_CARD_IDS] ?: true,
        resolutionScale = (prefs[Keys.RESOLUTION_SCALE] ?: 2).coerceIn(1, 4),
        widescreen16x9 = prefs[Keys.WIDESCREEN] ?: true,
        pgxpEnabled = prefs[Keys.PGXP] ?: true,
        hdFonts = prefs[Keys.HD_FONTS] ?: true,
        liveDuelRankMeter = prefs[Keys.DUEL_RANK_METER] ?: true,
        fusionAssistant = prefs[Keys.FUSION_ASSISTANT] ?: true,
        fastText60Fps = prefs[Keys.FAST_TEXT] ?: true,
        dropRateMultiplier = (prefs[Keys.DROP_MULTIPLIER] ?: 1).coerceIn(1, 99),
        preferredEmulatorPackage = prefs[Keys.PREFERRED_EMULATOR] ?: "AUTO",
        grantedFolderUris = prefs[Keys.GRANTED_FOLDER_URIS] ?: emptySet(),
        storagePromptDismissed = prefs[Keys.STORAGE_PROMPT_DISMISSED] ?: false,
      )
    }

  suspend fun update(transform: (RecompiledSettings) -> RecompiledSettings) {
    context.recompiledDataStore.edit { prefs ->
      val current =
        RecompiledSettings(
          bypassOriginalBinCheck = prefs[Keys.BYPASS_ORIGINAL_BIN] ?: true,
          allowExpandedCardIds = prefs[Keys.EXPANDED_CARD_IDS] ?: true,
          resolutionScale = (prefs[Keys.RESOLUTION_SCALE] ?: 2).coerceIn(1, 4),
          widescreen16x9 = prefs[Keys.WIDESCREEN] ?: true,
          pgxpEnabled = prefs[Keys.PGXP] ?: true,
          hdFonts = prefs[Keys.HD_FONTS] ?: true,
          liveDuelRankMeter = prefs[Keys.DUEL_RANK_METER] ?: true,
          fusionAssistant = prefs[Keys.FUSION_ASSISTANT] ?: true,
          fastText60Fps = prefs[Keys.FAST_TEXT] ?: true,
          dropRateMultiplier = (prefs[Keys.DROP_MULTIPLIER] ?: 1).coerceIn(1, 99),
          preferredEmulatorPackage = prefs[Keys.PREFERRED_EMULATOR] ?: "AUTO",
          grantedFolderUris = prefs[Keys.GRANTED_FOLDER_URIS] ?: emptySet(),
          storagePromptDismissed = prefs[Keys.STORAGE_PROMPT_DISMISSED] ?: false,
        )
      val next = transform(current)
      prefs[Keys.BYPASS_ORIGINAL_BIN] = next.bypassOriginalBinCheck
      prefs[Keys.EXPANDED_CARD_IDS] = next.allowExpandedCardIds
      prefs[Keys.RESOLUTION_SCALE] = next.resolutionScale
      prefs[Keys.WIDESCREEN] = next.widescreen16x9
      prefs[Keys.PGXP] = next.pgxpEnabled
      prefs[Keys.HD_FONTS] = next.hdFonts
      prefs[Keys.DUEL_RANK_METER] = next.liveDuelRankMeter
      prefs[Keys.FUSION_ASSISTANT] = next.fusionAssistant
      prefs[Keys.FAST_TEXT] = next.fastText60Fps
      prefs[Keys.DROP_MULTIPLIER] = next.dropRateMultiplier
      prefs[Keys.PREFERRED_EMULATOR] = next.preferredEmulatorPackage
      prefs[Keys.GRANTED_FOLDER_URIS] = next.grantedFolderUris
      prefs[Keys.STORAGE_PROMPT_DISMISSED] = next.storagePromptDismissed
    }
  }
}
