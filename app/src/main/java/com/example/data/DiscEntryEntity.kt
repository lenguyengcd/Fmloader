package com.example.data

data class DiscEntryEntity(
  val uriString: String,
  val displayName: String,
  val volumeId: String,
  val serialCode: String,
  val region: String,
  val formatName: String,
  val sectorStride: Int,
  val userDataOffset: Int,
  val fileSizeBytes: Long,
  val totalSectors: Long,
  val modStatusName: String,
  val modSummary: String,
  val waMrgSizeBytes: Long,
  val waMrgSampleCrc32: String?,
  val addedAtMillis: Long = System.currentTimeMillis(),
)
