package com.example.core.psx

enum class DiscFormat(val label: String) {
  BIN_2352("BIN / 2352"),
  ISO_2048("ISO / 2048"),
  PBP_PSONE("PBP / PSone"),
  CHD_COMPRESSED("CHD"),
  CUE_SHEET("CUE Sheet"),
  UNKNOWN("RAW / Unknown"),
}

enum class DiscModStatus {
  MODDED_FM,
  ORIGINAL_FM,
  OTHER_PSX,
  CUE_SHEET,
}

data class IsoFileEntry(
  val name: String,
  val fullPath: String,
  val lba: Int,
  val sizeBytes: Long,
  val isDirectory: Boolean,
)

data class PsxExeHeader(
  val fileName: String,
  val magicValid: Boolean,
  val pc0: Long,
  val gp0: Long,
  val textAddress: Long,
  val textSize: Long,
  val stackPointer: Long,
  val markerText: String,
  val crc32Hex: String,
)

data class PsxDiscAnalysis(
  val displayName: String,
  val uriString: String,
  val fileSizeBytes: Long,
  val format: DiscFormat,
  val sectorStride: Int,
  val userDataOffset: Int,
  val volumeId: String,
  val systemId: String,
  val totalSectors: Long,
  val serialCode: String,
  val region: String,
  val bootExecutablePath: String,
  val systemCnfText: String,
  val modStatus: DiscModStatus,
  val modReasons: List<String>,
  val exeHeader: PsxExeHeader?,
  val waMrgEntry: IsoFileEntry?,
  val waMrgSampleCrc32: String?,
  val files: List<IsoFileEntry>,
  val cueReferencedFiles: List<String> = emptyList(),
)

data class DiscBinaryScanResult(
  val scannedFileName: String,
  val scannedBytes: Long,
  val extractedStrings: List<String>,
  val detectedCardKeywords: List<String>,
  val exeEntryPointsCount: Int,
)

data class SectorHexDump(
  val lba: Int,
  val byteOffset: Long,
  val sectorStride: Int,
  val rawBytes: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SectorHexDump) return false
    return lba == other.lba &&
      byteOffset == other.byteOffset &&
      sectorStride == other.sectorStride &&
      rawBytes.contentEquals(other.rawBytes)
  }

  override fun hashCode(): Int {
    var result = lba
    result = 31 * result + byteOffset.hashCode()
    result = 31 * result + sectorStride
    result = 31 * result + rawBytes.contentHashCode()
    return result
  }
}
