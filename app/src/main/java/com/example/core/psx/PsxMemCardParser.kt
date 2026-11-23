package com.example.core.psx

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MemCardSlot(
  val slotNumber: Int, // 1..15
  val blockCount: Int,
  val productCode: String,
  val saveIdentifier: String,
  val title: String,
  val isForbiddenMemories: Boolean,
  val isFree: Boolean,
  val isLinkedContinuation: Boolean,
  val iconPixelsArgb: IntArray?, // 16x16 ARGB pixels decoded from PS1 CLUT + 4bpp bitmap
  val rawBlockBytes: ByteArray?, // 8192 * blockCount bytes for MCS export
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MemCardSlot) return false
    return slotNumber == other.slotNumber &&
      productCode == other.productCode &&
      saveIdentifier == other.saveIdentifier &&
      title == other.title
  }

  override fun hashCode(): Int {
    var result = slotNumber
    result = 31 * result + productCode.hashCode()
    result = 31 * result + title.hashCode()
    return result
  }
}

data class PsxMemoryCard(
  val fileName: String,
  val uriString: String,
  val formatLabel: String,
  val usedBlocks: Int,
  val slots: List<MemCardSlot>,
)

/**
 * Parses standard 128 KB PS1 memory card dumps (.mcr, .mcd, .bin) and 134,976-byte DexDrive (.gme)
 * files, including Shift-JIS title decoding and 16x16 4-bit CLUT save icon rendering.
 */
object PsxMemCardParser {

  private const val CARD_SIZE = 131_072
  private const val GME_HEADER_SIZE = 3904
  private const val BLOCK_SIZE = 8192
  private const val FRAME_SIZE = 128

  suspend fun parseMemoryCard(contentResolver: ContentResolver, uri: Uri): Result<PsxMemoryCard> =
    withContext(Dispatchers.IO) {
      runCatching {
        val name = queryName(contentResolver, uri)
        val raw =
          contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read memory card file.")

        val (cardBytes, formatLabel) =
          when {
            raw.size >= CARD_SIZE + GME_HEADER_SIZE &&
              String(raw, 0, 7, Charsets.US_ASCII) == "123-456" ->
              raw.copyOfRange(GME_HEADER_SIZE, GME_HEADER_SIZE + CARD_SIZE) to "DexDrive .GME"
            raw.size >= CARD_SIZE -> raw.copyOfRange(0, CARD_SIZE) to "PS1 Raw 128KB"
            else -> error("Invalid PS1 memory card size (${raw.size} bytes). Expected 131,072 bytes.")
          }

        require(cardBytes[0] == 'M'.code.toByte() && cardBytes[1] == 'C'.code.toByte()) {
          "Missing PS1 Memory Card 'MC' header signature."
        }

        val sjis = runCatching { Charset.forName("Shift_JIS") }.getOrDefault(Charsets.UTF_8)
        val slots = mutableListOf<MemCardSlot>()
        var usedCount = 0

        for (slotIdx in 1..15) {
          val dirOffset = slotIdx * FRAME_SIZE
          val allocState = cardBytes[dirOffset].toInt() and 0xFF
          val isFree = allocState == 0xA0 || allocState == 0xA1 || allocState == 0xA2 || allocState == 0xA3
          val isStart = allocState == 0x51
          val isLinked = allocState == 0x52 || allocState == 0x53

          if (!isFree) usedCount++

          if (isStart) {
            val sizeBytes =
              (cardBytes[dirOffset + 4].toInt() and 0xFF) or
                ((cardBytes[dirOffset + 5].toInt() and 0xFF) shl 8) or
                ((cardBytes[dirOffset + 6].toInt() and 0xFF) shl 16) or
                ((cardBytes[dirOffset + 7].toInt() and 0xFF) shl 24)
            val blocks = (sizeBytes / BLOCK_SIZE).coerceIn(1, 15 - slotIdx + 1)

            // Filename string starts at +10 (20 bytes): e.g. BASLUS-01411YUGIOH
            val fileIdRaw =
              String(cardBytes, dirOffset + 10, 20, Charsets.US_ASCII).substringBefore('\u0000')
            val productCode =
              if (fileIdRaw.length >= 12) fileIdRaw.substring(2, 12) else fileIdRaw
            val saveIdentifier =
              if (fileIdRaw.length > 12) fileIdRaw.substring(12) else ""

            val blockOffset = slotIdx * BLOCK_SIZE
            val scMagic =
              cardBytes[blockOffset] == 'S'.code.toByte() &&
                cardBytes[blockOffset + 1] == 'C'.code.toByte()

            val title =
              if (scMagic) {
                decodeShiftJisTitle(cardBytes, blockOffset + 4, 64, sjis)
                  .ifBlank { fileIdRaw }
              } else {
                fileIdRaw
              }

            val iconPixels =
              if (scMagic) decodePs1Icon(cardBytes, blockOffset) else null

            val rawBlock =
              cardBytes.copyOfRange(
                blockOffset,
                (blockOffset + blocks * BLOCK_SIZE).coerceAtMost(cardBytes.size),
              )

            val isFm =
              productCode.contains("01411") ||
                productCode.contains("03947") ||
                productCode.contains("86398") ||
                title.contains("Yu-Gi-Oh", true) ||
                title.contains("Forbidden", true)

            slots.add(
              MemCardSlot(
                slotNumber = slotIdx,
                blockCount = blocks,
                productCode = productCode.ifBlank { "PSX-SAVE" },
                saveIdentifier = saveIdentifier,
                title = title,
                isForbiddenMemories = isFm,
                isFree = false,
                isLinkedContinuation = false,
                iconPixelsArgb = iconPixels,
                rawBlockBytes = rawBlock,
              )
            )
          } else if (isLinked) {
            slots.add(
              MemCardSlot(
                slotNumber = slotIdx,
                blockCount = 1,
                productCode = "LINKED",
                saveIdentifier = "",
                title = "Continuation Block",
                isForbiddenMemories = false,
                isFree = false,
                isLinkedContinuation = true,
                iconPixelsArgb = null,
                rawBlockBytes = null,
              )
            )
          } else {
            slots.add(
              MemCardSlot(
                slotNumber = slotIdx,
                blockCount = 0,
                productCode = "EMPTY",
                saveIdentifier = "",
                title = "Free Block",
                isForbiddenMemories = false,
                isFree = true,
                isLinkedContinuation = false,
                iconPixelsArgb = null,
                rawBlockBytes = null,
              )
            )
          }
        }

        PsxMemoryCard(
          fileName = name,
          uriString = uri.toString(),
          formatLabel = formatLabel,
          usedBlocks = usedCount,
          slots = slots,
        )
      }
    }

  suspend fun exportSlotAsMcs(
    contentResolver: ContentResolver,
    memCardUri: Uri,
    slot: MemCardSlot,
    outputUri: Uri,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val raw =
          contentResolver.openInputStream(memCardUri)?.use { it.readBytes() }
            ?: error("Could not open source memory card.")
        val base =
          if (raw.size >= CARD_SIZE + GME_HEADER_SIZE &&
            String(raw, 0, 7, Charsets.US_ASCII) == "123-456"
          ) {
            GME_HEADER_SIZE
          } else {
            0
          }
        val dirOffset = base + slot.slotNumber * FRAME_SIZE
        val dirFrame = raw.copyOfRange(dirOffset, dirOffset + FRAME_SIZE)
        val blockBytes = slot.rawBlockBytes ?: error("Slot contains no save data.")

        contentResolver.openOutputStream(outputUri, "wt")?.use { out ->
          out.write(dirFrame)
          out.write(blockBytes)
          out.flush()
        } ?: error("Failed to write .mcs file.")
      }
    }

  private fun decodePs1Icon(cardBytes: ByteArray, blockOffset: Int): IntArray {
    // Palette: 16 entries of 16-bit BGR555 at blockOffset + 0x60
    val palette = IntArray(16)
    for (i in 0 until 16) {
      val lo = cardBytes[blockOffset + 0x60 + i * 2].toInt() and 0xFF
      val hi = cardBytes[blockOffset + 0x60 + i * 2 + 1].toInt() and 0xFF
      val c = lo or (hi shl 8)
      val r = ((c and 0x1F) * 255) / 31
      val g = (((c shr 5) and 0x1F) * 255) / 31
      val b = (((c shr 10) and 0x1F) * 255) / 31
      val a = if (c == 0) 0x00 else 0xFF
      palette[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    // 16x16 4-bit bitmap at blockOffset + 0x80 (128 bytes)
    val pixels = IntArray(16 * 16)
    for (y in 0 until 16) {
      for (x in 0 until 16 step 2) {
        val byteVal = cardBytes[blockOffset + 0x80 + y * 8 + x / 2].toInt() and 0xFF
        val lowNib = byteVal and 0x0F
        val highNib = (byteVal shr 4) and 0x0F
        pixels[y * 16 + x] = palette[lowNib]
        pixels[y * 16 + x + 1] = palette[highNib]
      }
    }
    return pixels
  }

  private fun decodeShiftJisTitle(
    buf: ByteArray,
    offset: Int,
    len: Int,
    charset: Charset,
  ): String {
    var end = offset
    while (end < offset + len && end < buf.size && buf[end] != 0.toByte()) {
      end++
    }
    val raw = String(buf, offset, end - offset, charset)
    // Convert full-width ASCII Shift-JIS characters (FF01..FF5E) to half-width for clean reading
    val normalized = StringBuilder()
    for (ch in raw) {
      when {
        ch.code in 0xFF01..0xFF5E -> normalized.append((ch.code - 0xFEE0).toChar())
        ch == '\u3000' -> normalized.append(' ')
        else -> normalized.append(ch)
      }
    }
    return normalized.toString().trim()
  }

  private fun queryName(contentResolver: ContentResolver, uri: Uri): String {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "memcard.mcr"
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) {
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) c.getString(idx)?.let { name = it }
      }
    }
    return name
  }
}
