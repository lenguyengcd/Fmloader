package com.example.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.psx.DiscBinaryScanResult
import com.example.core.psx.PsxDiscAnalysis
import com.example.core.psx.PsxDiscParser
import com.example.core.psx.SectorHexDump
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.discLibraryDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "fm_disc_library")

class DiscRepository(private val context: Context) {

  private val contentResolver = context.contentResolver
  private val discsJsonKey = stringPreferencesKey("disc_entries_json")

  val allDiscs: Flow<List<DiscEntryEntity>> =
    context.discLibraryDataStore.data.map { prefs ->
      decodeList(prefs[discsJsonKey])
    }

  suspend fun importAndAnalyzeDisc(uri: Uri): Result<PsxDiscAnalysis> {
    val result = PsxDiscParser.analyzeDisc(contentResolver, uri)
    result.onSuccess { analysis ->
      val newEntry =
        DiscEntryEntity(
          uriString = analysis.uriString,
          displayName = analysis.displayName,
          volumeId = analysis.volumeId,
          serialCode = analysis.serialCode,
          region = analysis.region,
          formatName = analysis.format.name,
          sectorStride = analysis.sectorStride,
          userDataOffset = analysis.userDataOffset,
          fileSizeBytes = analysis.fileSizeBytes,
          totalSectors = analysis.totalSectors,
          modStatusName = analysis.modStatus.name,
          modSummary = analysis.modReasons.firstOrNull().orEmpty(),
          waMrgSizeBytes = analysis.waMrgEntry?.sizeBytes ?: 0L,
          waMrgSampleCrc32 = analysis.waMrgSampleCrc32,
        )
      context.discLibraryDataStore.edit { prefs ->
        val current = decodeList(prefs[discsJsonKey]).filterNot { it.uriString == newEntry.uriString }
        prefs[discsJsonKey] = encodeList(listOf(newEntry) + current)
      }
    }
    return result
  }

  suspend fun inspectDisc(uri: Uri): Result<PsxDiscAnalysis> =
    PsxDiscParser.analyzeDisc(contentResolver, uri)

  suspend fun readSectorHex(
    uri: Uri,
    lba: Int,
    sectorStride: Int,
    userDataOffset: Int,
  ): Result<SectorHexDump> =
    PsxDiscParser.readSectorHex(contentResolver, uri, lba, sectorStride, userDataOffset)

  suspend fun scanDiscStrings(
    uri: Uri,
    analysis: PsxDiscAnalysis,
  ): Result<DiscBinaryScanResult> =
    PsxDiscParser.scanDiscStringsAndStats(contentResolver, uri, analysis)

  suspend fun removeDisc(uriString: String) {
    context.discLibraryDataStore.edit { prefs ->
      val next = decodeList(prefs[discsJsonKey]).filterNot { it.uriString == uriString }
      prefs[discsJsonKey] = encodeList(next)
    }
  }

  private fun encodeList(items: List<DiscEntryEntity>): String {
    val arr = JSONArray()
    items.forEach { item ->
      arr.put(
        JSONObject().apply {
          put("uriString", item.uriString)
          put("displayName", item.displayName)
          put("volumeId", item.volumeId)
          put("serialCode", item.serialCode)
          put("region", item.region)
          put("formatName", item.formatName)
          put("sectorStride", item.sectorStride)
          put("userDataOffset", item.userDataOffset)
          put("fileSizeBytes", item.fileSizeBytes)
          put("totalSectors", item.totalSectors)
          put("modStatusName", item.modStatusName)
          put("modSummary", item.modSummary)
          put("waMrgSizeBytes", item.waMrgSizeBytes)
          put("waMrgSampleCrc32", item.waMrgSampleCrc32 ?: JSONObject.NULL)
          put("addedAtMillis", item.addedAtMillis)
        }
      )
    }
    return arr.toString()
  }

  private fun decodeList(json: String?): List<DiscEntryEntity> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        buildList(arr.length()) {
          for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val crcRaw = obj.opt("waMrgSampleCrc32")
            add(
              DiscEntryEntity(
                uriString = obj.getString("uriString"),
                displayName = obj.getString("displayName"),
                volumeId = obj.optString("volumeId"),
                serialCode = obj.optString("serialCode"),
                region = obj.optString("region"),
                formatName = obj.optString("formatName"),
                sectorStride = obj.optInt("sectorStride", 2352),
                userDataOffset = obj.optInt("userDataOffset", 24),
                fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                totalSectors = obj.optLong("totalSectors", 0L),
                modStatusName = obj.optString("modStatusName"),
                modSummary = obj.optString("modSummary"),
                waMrgSizeBytes = obj.optLong("waMrgSizeBytes", 0L),
                waMrgSampleCrc32 = if (crcRaw == null || crcRaw == JSONObject.NULL) null else crcRaw.toString(),
                addedAtMillis = obj.optLong("addedAtMillis", 0L),
              )
            )
          }
        }.sortedByDescending { it.addedAtMillis }
      }
      .getOrDefault(emptyList())
  }
}
