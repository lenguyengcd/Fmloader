package com.example.data

import android.content.ContentResolver
import android.net.Uri
import com.example.core.psx.DiscBinaryScanResult
import com.example.core.psx.PsxDiscAnalysis
import com.example.core.psx.PsxDiscParser
import com.example.core.psx.SectorHexDump
import kotlinx.coroutines.flow.Flow

class DiscRepository(
  private val discEntryDao: DiscEntryDao,
  private val contentResolver: ContentResolver,
) {
  val allDiscs: Flow<List<DiscEntryEntity>> = discEntryDao.observeAllDiscs()

  suspend fun importAndAnalyzeDisc(uri: Uri): Result<PsxDiscAnalysis> {
    val result = PsxDiscParser.analyzeDisc(contentResolver, uri)
    result.onSuccess { analysis ->
      discEntryDao.upsertDisc(
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
      )
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
  ): Result<DiscBinaryScanResult> = PsxDiscParser.scanDiscStringsAndStats(contentResolver, uri, analysis)

  suspend fun removeDisc(uriString: String) {
    discEntryDao.deleteDisc(uriString)
  }
}
