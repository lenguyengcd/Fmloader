package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "disc_entries")
data class DiscEntryEntity(
  @PrimaryKey val uriString: String,
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
