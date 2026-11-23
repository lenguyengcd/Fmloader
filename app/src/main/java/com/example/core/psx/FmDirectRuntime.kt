package com.example.core.psx

import android.content.ContentResolver
import android.net.Uri
import java.io.FileInputStream
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FmCardBinaryEntry(
  val cardId: Int,
  val ramAddress: Long,
  val atk: Int,
  val def: Int,
  val level: Int,
  val attributeId: Int,
  val guardianStar1: Int,
  val guardianStar2: Int,
  val rawStatsWord: Long,
) {
  val attributeLabel: String
    get() =
      when (attributeId) {
        0 -> "Light"
        1 -> "Dark"
        2 -> "Earth"
        3 -> "Water"
        4 -> "Fire"
        5 -> "Wind"
        else -> "Attr #$attributeId"
      }
}

data class ExtractedTimTexture(
  val offsetBytes: Long,
  val width: Int,
  val height: Int,
  val bpp: Int,
  val argbPixels: IntArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ExtractedTimTexture) return false
    return offsetBytes == other.offsetBytes &&
      width == other.width &&
      height == other.height &&
      bpp == other.bpp &&
      argbPixels.contentEquals(other.argbPixels)
  }

  override fun hashCode(): Int {
    var result = offsetBytes.hashCode()
    result = 31 * result + width
    result = 31 * result + height
    return result
  }
}

data class FmDropWeightEntry(
  val cardId: Int,
  val baseWeight: Int,
)

data class FmDropRollResult(
  val cardId: Int,
  val baseWeight: Int,
  val boostedWeight: Int,
  val basePercent: Float,
  val boostedPercent: Float,
  val cardStats: FmCardBinaryEntry?,
)

data class FmDirectSession(
  val analysis: PsxDiscAnalysis,
  val ramImage: ByteArray,
  val initialCpu: MipsCpuState,
  val cpu: MipsCpuState,
  val cards: List<FmCardBinaryEntry>,
  val cardTableRamAddress: Long?,
  val textures: List<ExtractedTimTexture>,
  val dropPool: List<FmDropWeightEntry>,
  val dropPoolOffset: Long?,
  val dropRolls: List<FmDropRollResult> = emptyList(),
  val runtimeNotes: List<String> = emptyList(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FmDirectSession) return false
    return analysis == other.analysis &&
      cpu == other.cpu &&
      cards.size == other.cards.size &&
      textures.size == other.textures.size &&
      dropRolls == other.dropRolls
  }

  override fun hashCode(): Int {
    var result = analysis.hashCode()
    result = 31 * result + cpu.hashCode()
    return result
  }
}

/**
 * Loads and runs modded/vanilla PS1 discs directly inside the app:
 * 1) Maps the disc's boot PS-X EXE into 2MB virtual PlayStation RAM and steps real MIPS R3000A code.
 * 2) Decodes packed FM card stat tables (`SLUS_014.11`) from actual loaded EXE bytes.
 * 3) Extracts & renders real PS1 `.TIM` RGB555 textures from `WA_MRG.MRG` / disc sectors.
 * 4) Reads real duelist drop pool weights and executes live `1x-99x` drop rolls.
 */
object FmDirectRuntime {

  // Standard SLUS-01411 (Yu-Gi-Oh! Forbidden Memories NTSC-U) RAM addresses
  private const val STANDARD_CARD_STATS_VADDR = 0x801D4244L
  private const val STANDARD_LEVEL_ATTR_DELTA = 0x10EE // 0x801D5332 - 0x801D4244
  private const val STANDARD_WA_MRG_DROP_OFFSET = 0xE99800L

  suspend fun bootDiscDirect(
    contentResolver: ContentResolver,
    uri: Uri,
    analysis: PsxDiscAnalysis,
  ): Result<FmDirectSession> =
    withContext(Dispatchers.IO) {
      runCatching {
        val notes = mutableListOf<String>()
        if (analysis.format == DiscFormat.PBP_PSONE || analysis.format == DiscFormat.CHD_COMPRESSED || analysis.format == DiscFormat.CUE_SHEET) {
          notes += "Container format (${analysis.format.label}): ISO-9660 sectors are packed; load an extracted .bin/.iso for raw MIPS EXE & TIM execution."
          val emptyHeader = PsxExeHeader(analysis.displayName, false, 0, 0, 0, 0, 0, "", "")
          val initialCpu = PsxMipsEngine.createInitialState(emptyHeader)
          return@runCatching FmDirectSession(
            analysis = analysis,
            ramImage = ByteArray(0),
            initialCpu = initialCpu,
            cpu = initialCpu,
            cards = emptyList(),
            cardTableRamAddress = null,
            textures = emptyList(),
            dropPool = emptyList(),
            dropPoolOffset = null,
            runtimeNotes = notes,
          )
        }

        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
          FileInputStream(pfd.fileDescriptor).use { fis ->
            val channel = fis.channel
            val exeEntry =
              analysis.files.firstOrNull {
                !it.isDirectory &&
                  (it.name.equals(analysis.bootExecutablePath.substringAfterLast('\\'), true) ||
                    it.name.startsWith("SLUS", true) ||
                    it.name.startsWith("SLES", true) ||
                    it.name.startsWith("SLPM", true))
              }

            val ramImage: ByteArray
            val exeHeader: PsxExeHeader
            if (exeEntry != null && exeEntry.sizeBytes > 0x800) {
              val maxExe = exeEntry.sizeBytes.coerceAtMost(2_097_152L).toInt()
              val exeBytes =
                PsxDiscParser.readLogicalFileBytes(
                  channel = channel,
                  startLba = exeEntry.lba,
                  byteLength = maxExe,
                  stride = analysis.sectorStride,
                  userOffset = analysis.userDataOffset,
                )
              val loaded = PsxMipsEngine.loadExeIntoRam(exeBytes)
              if (loaded != null) {
                exeHeader = loaded.first.copy(fileName = exeEntry.name)
                ramImage = loaded.second
                notes += "Loaded ${exeEntry.name} (${exeBytes.size} B) into 2 MB PS1 RAM @ 0x${PsxMipsEngine.hex8(exeHeader.textAddress)}."
              } else {
                exeHeader = analysis.exeHeader ?: PsxExeHeader(exeEntry.name, false, 0, 0, 0, 0, 0, "", "")
                ramImage = ByteArray(PsxMipsEngine.PS1_RAM_SIZE)
                notes += "${exeEntry.name} lacks a valid PS-X EXE header."
              }
            } else {
              exeHeader = analysis.exeHeader ?: PsxExeHeader(analysis.displayName, false, 0, 0, 0, 0, 0, "", "")
              ramImage = ByteArray(PsxMipsEngine.PS1_RAM_SIZE)
              notes += "No SLUS/SLES/SLPM executable located inside ISO-9660 directory tree."
            }

            // Step an initial boot burst (24 cycles) so user immediately sees live instructions & registers
            val initialCpu = PsxMipsEngine.createInitialState(exeHeader)
            val bootedCpu =
              if (!initialCpu.halted && ramImage.isNotEmpty()) {
                PsxMipsEngine.stepCycles(ramImage, initialCpu, 24)
              } else {
                initialCpu
              }

            // Decode real card stats from loaded RAM
            val (cards, cardAddr) =
              if (ramImage.isNotEmpty()) extractFmCardStats(ramImage) else (emptyList<FmCardBinaryEntry>() to null)
            if (cards.isNotEmpty() && cardAddr != null) {
              notes += "Decoded ${cards.size} card entries directly from RAM 0x${PsxMipsEngine.hex8(cardAddr)}."
            }

            // Extract real .TIM graphics & drop table from WA_MRG.MRG or fallback data file
            val dataFile =
              analysis.waMrgEntry
                ?: analysis.files.filter { !it.isDirectory && it.sizeBytes >= 4096 }.maxByOrNull { it.sizeBytes }

            val textures = mutableListOf<ExtractedTimTexture>()
            val dropPool = mutableListOf<FmDropWeightEntry>()
            var dropPoolOffset: Long? = null

            if (dataFile != null) {
              // Scan first up to 2.5 MB of WA_MRG.MRG (where FM card icons & menu TIMs live)
              val chunkLen = dataFile.sizeBytes.coerceAtMost(2_621_440L).toInt()
              val mrgHead =
                PsxDiscParser.readLogicalFileBytes(
                  channel = channel,
                  startLba = dataFile.lba,
                  byteLength = chunkLen,
                  stride = analysis.sectorStride,
                  userOffset = analysis.userDataOffset,
                )
              textures += extractTimTextures(mrgHead, maxCount = 24)
              if (textures.isNotEmpty()) {
                notes += "Extracted ${textures.size} PS1 .TIM textures from ${dataFile.name}."
              }

              // Read duelist drop weights from WA_MRG.MRG if large enough (~15MB+)
              if (analysis.waMrgEntry != null && dataFile.sizeBytes > STANDARD_WA_MRG_DROP_OFFSET + 4096L) {
                val dropStartSector = dataFile.lba + (STANDARD_WA_MRG_DROP_OFFSET / 2048L).toInt()
                val dropBytes =
                  PsxDiscParser.readLogicalFileBytes(
                    channel = channel,
                    startLba = dropStartSector,
                    byteLength = 4096,
                    stride = analysis.sectorStride,
                    userOffset = analysis.userDataOffset,
                  )
                val parsedDrops = parseDropPoolWeights(dropBytes, maxCardId = maxOf(cards.size, 722))
                if (parsedDrops.isNotEmpty()) {
                  dropPool += parsedDrops
                  dropPoolOffset = STANDARD_WA_MRG_DROP_OFFSET
                  notes += "Loaded ${parsedDrops.size} weighted drop entries from WA_MRG.MRG @ 0x${STANDARD_WA_MRG_DROP_OFFSET.toString(16)}."
                }
              }
            }

            FmDirectSession(
              analysis = analysis,
              ramImage = ramImage,
              initialCpu = initialCpu,
              cpu = bootedCpu,
              cards = cards,
              cardTableRamAddress = cardAddr,
              textures = textures,
              dropPool = dropPool,
              dropPoolOffset = dropPoolOffset,
              runtimeNotes = notes,
            )
          }
        } ?: error("Unable to open disc image for direct execution.")
      }
    }

  /**
   * Decodes Yu-Gi-Oh! Forbidden Memories packed 32-bit card stats:
   *   atk = (word and 0x1FF) * 10
   *   def = ((word ushr 9) and 0x1FF) * 10
   *   guardianStar2 = (word ushr 18) and 0x0F
   *   guardianStar1 = (word ushr 22) and 0x0F
   * Checks standard SLUS-01411 offset (0x801D4244) first, then scans RAM for valid FM card tables.
   */
  fun extractFmCardStats(ram: ByteArray): Pair<List<FmCardBinaryEntry>, Long?> {
    if (ram.size < PsxMipsEngine.PS1_RAM_SIZE) return emptyList<FmCardBinaryEntry>() to null

    val stdOff = PsxMipsEngine.mapRamOffset(STANDARD_CARD_STATS_VADDR)
    if (stdOff != null && looksLikeCardStatsTable(ram, stdOff, checkCount = 120)) {
      return decodeCardTableAt(ram, stdOff, STANDARD_CARD_STATS_VADDR) to STANDARD_CARD_STATS_VADDR
    }

    // Fallback scan for modded/relocated executables (4-byte aligned)
    var off = 0x10000
    val limit = ram.size - 722 * 4
    while (off < limit) {
      if (looksLikeCardStatsTable(ram, off, checkCount = 180)) {
        val vaddr = 0x80000000L + off.toLong()
        return decodeCardTableAt(ram, off, vaddr) to vaddr
      }
      off += 4
    }
    return emptyList<FmCardBinaryEntry>() to null
  }

  private fun looksLikeCardStatsTable(ram: ByteArray, offset: Int, checkCount: Int): Boolean {
    if (offset < 0 || offset + checkCount * 4 > ram.size) return false
    var nonZeroStats = 0
    var validEntries = 0
    for (i in 0 until checkCount) {
      val w = readU32LE(ram, offset + i * 4)
      val atk = (w and 0x1FFL).toInt() * 10
      val def = ((w ushr 9) and 0x1FFL).toInt() * 10
      val gs2 = ((w ushr 18) and 0xFL).toInt()
      val gs1 = ((w ushr 22) and 0xFL).toInt()
      val unusedTop = (w ushr 26) and 0x3FL
      if (atk <= 5100 && def <= 5100 && gs1 <= 11 && gs2 <= 11 && unusedTop == 0L) {
        validEntries++
        if (atk in 100..5000 && def in 100..5000) {
          nonZeroStats++
        }
      } else {
        return false
      }
    }
    return validEntries == checkCount && nonZeroStats >= (checkCount * 2 / 3)
  }

  private fun decodeCardTableAt(ram: ByteArray, startOffset: Int, startVaddr: Long): List<FmCardBinaryEntry> {
    val entries = mutableListOf<FmCardBinaryEntry>()
    var idx = 0
    val maxCards = 900
    while (idx < maxCards && startOffset + idx * 4 + 4 <= ram.size) {
      val w = readU32LE(ram, startOffset + idx * 4)
      val atk = (w and 0x1FFL).toInt() * 10
      val def = ((w ushr 9) and 0x1FFL).toInt() * 10
      val gs2 = ((w ushr 18) and 0xFL).toInt()
      val gs1 = ((w ushr 22) and 0xFL).toInt()
      val unusedTop = (w ushr 26) and 0x3FL
      if (unusedTop != 0L || gs1 > 12 || gs2 > 12 || (idx >= 722 && w == 0L)) {
        break
      }
      val lvAttrOff = startOffset + STANDARD_LEVEL_ATTR_DELTA + idx
      val lvAttrByte = ram.getOrNull(lvAttrOff)?.toInt()?.and(0xFF) ?: 0
      val level = lvAttrByte and 0x0F
      val attr = (lvAttrByte ushr 4) and 0x0F
      entries +=
        FmCardBinaryEntry(
          cardId = idx + 1,
          ramAddress = startVaddr + idx * 4L,
          atk = atk,
          def = def,
          level = level,
          attributeId = attr,
          guardianStar1 = gs1,
          guardianStar2 = gs2,
          rawStatsWord = w,
        )
      idx++
    }
    return entries
  }

  /**
   * Scans raw bytes for valid PlayStation 1 `.TIM` image headers (magic 0x00000010)
   * and decodes 4bpp, 8bpp (CLUT RGB555), and 16bpp direct pixels to ARGB8888.
   */
  fun extractTimTextures(data: ByteArray, maxCount: Int = 24): List<ExtractedTimTexture> {
    val out = mutableListOf<ExtractedTimTexture>()
    var pos = 0
    while (pos + 24 < data.size && out.size < maxCount) {
      if (
        data[pos] == 0x10.toByte() &&
          data[pos + 1] == 0x00.toByte() &&
          data[pos + 2] == 0x00.toByte() &&
          data[pos + 3] == 0x00.toByte()
      ) {
        val decoded = tryDecodeTimAt(data, pos)
        if (decoded != null) {
          out += decoded
          pos += 64
          continue
        }
      }
      pos += 4
    }
    return out
  }

  fun tryDecodeTimAt(data: ByteArray, offset: Int): ExtractedTimTexture? {
    if (offset + 8 > data.size) return null
    val flags = readU32LE(data, offset + 4).toInt()
    val pmode = flags and 0x07
    val hasClut = (flags and 0x08) != 0
    if ((flags and 0xFFFFFFF0.toInt()) != 0) return null
    if (pmode !in 0..2) return null

    var cursor = offset + 8
    var clut = IntArray(0)
    if (hasClut) {
      if (cursor + 12 > data.size) return null
      val clutBytes = readU32LE(data, cursor).toInt()
      if (clutBytes < 12 || clutBytes > 65536 || cursor + clutBytes > data.size) return null
      val numColors = (clutBytes - 12) / 2
      clut = IntArray(numColors)
      for (i in 0 until numColors) {
        val rgb555 = readU16LE(data, cursor + 12 + i * 2)
        clut[i] = psxRgb555ToArgb(rgb555)
      }
      cursor += clutBytes
    } else if (pmode != 2) {
      return null
    }

    if (cursor + 12 > data.size) return null
    val imgBlockBytes = readU32LE(data, cursor).toInt()
    val wUnits = readU16LE(data, cursor + 8)
    val height = readU16LE(data, cursor + 10)
    if (imgBlockBytes < 12 || cursor + imgBlockBytes > data.size) return null
    if (height !in 8..256 || wUnits <= 0) return null

    val width =
      when (pmode) {
        0 -> wUnits * 4
        1 -> wUnits * 2
        else -> wUnits
      }
    if (width !in 8..256) return null

    val expectedPayload = wUnits * 2 * height
    if (imgBlockBytes - 12 < expectedPayload) return null

    val pixelStart = cursor + 12
    val pixels = IntArray(width * height)
    var nonTransparent = 0

    when (pmode) {
      0 -> {
        var idx = 0
        for (i in 0 until expectedPayload) {
          val b = data[pixelStart + i].toInt() and 0xFF
          val c0 = clut.getOrElse(b and 0x0F) { 0 }
          val c1 = clut.getOrElse((b ushr 4) and 0x0F) { 0 }
          if (idx < pixels.size) {
            pixels[idx++] = c0
            if (c0 != 0) nonTransparent++
          }
          if (idx < pixels.size) {
            pixels[idx++] = c1
            if (c1 != 0) nonTransparent++
          }
        }
      }
      1 -> {
        for (i in 0 until minOf(pixels.size, expectedPayload)) {
          val b = data[pixelStart + i].toInt() and 0xFF
          val c = clut.getOrElse(b) { 0 }
          pixels[i] = c
          if (c != 0) nonTransparent++
        }
      }
      2 -> {
        for (i in 0 until minOf(pixels.size, expectedPayload / 2)) {
          val c = psxRgb555ToArgb(readU16LE(data, pixelStart + i * 2))
          pixels[i] = c
          if (c != 0) nonTransparent++
        }
      }
    }

    if (nonTransparent < (pixels.size / 12)) return null
    val bpp =
      when (pmode) {
        0 -> 4
        1 -> 8
        else -> 16
      }
    return ExtractedTimTexture(
      offsetBytes = offset.toLong(),
      width = width,
      height = height,
      bpp = bpp,
      argbPixels = pixels,
    )
  }

  private fun psxRgb555ToArgb(raw: Int): Int {
    if (raw == 0) return 0x00000000
    val r = ((raw and 0x1F) * 255) / 31
    val g = (((raw ushr 5) and 0x1F) * 255) / 31
    val b = (((raw ushr 10) and 0x1F) * 255) / 31
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
  }

  fun parseDropPoolWeights(bytes: ByteArray, maxCardId: Int = 722): List<FmDropWeightEntry> {
    val entries = mutableListOf<FmDropWeightEntry>()
    val count = minOf(maxCardId, bytes.size / 2)
    var sum = 0
    for (i in 0 until count) {
      val w = readU16LE(bytes, i * 2)
      if (w in 1..2048) {
        entries += FmDropWeightEntry(cardId = i + 1, baseWeight = w)
        sum += w
      }
      if (sum > 8192) break
    }
    return if (entries.size >= 4 && sum > 0) entries else emptyList()
  }

  /**
   * Rolls cards using real drop pool weights scaled by `dropRateMultiplier` (1x-99x).
   * Rare (lower-weight) entries receive proportional boost relative to high-frequency fodder,
   * matching Recompiled drop-multiplier behavior.
   */
  fun rollDropsWithMultiplier(
    dropPool: List<FmDropWeightEntry>,
    cardsById: Map<Int, FmCardBinaryEntry>,
    multiplier: Int,
    rollCount: Int = 5,
    random: Random = Random.Default,
  ): List<FmDropRollResult> {
    if (dropPool.isEmpty()) return emptyList()
    val safeMult = multiplier.coerceIn(1, 99)
    val baseSum = dropPool.sumOf { it.baseWeight }.coerceAtLeast(1)
    val avgWeight = (baseSum.toDouble() / dropPool.size).coerceAtLeast(1.0)

    val boosted =
      dropPool.map { entry ->
        val w =
          if (safeMult == 1) {
            entry.baseWeight
          } else if (entry.baseWeight <= avgWeight) {
            (entry.baseWeight * safeMult).coerceAtMost(200_000)
          } else {
            entry.baseWeight
          }
        entry to w
      }
    val boostedSum = boosted.sumOf { it.second }.coerceAtLeast(1)

    return List(rollCount) {
      var pick = random.nextInt(boostedSum)
      var chosen = boosted.last()
      for (pair in boosted) {
        if (pick < pair.second) {
          chosen = pair
          break
        }
        pick -= pair.second
      }
      val entry = chosen.first
      val boostedWeight = chosen.second
      FmDropRollResult(
        cardId = entry.cardId,
        baseWeight = entry.baseWeight,
        boostedWeight = boostedWeight,
        basePercent = (entry.baseWeight * 100f) / baseSum,
        boostedPercent = (boostedWeight * 100f) / boostedSum,
        cardStats = cardsById[entry.cardId],
      )
    }
  }

  private fun readU16LE(buf: ByteArray, offset: Int): Int =
    (buf[offset].toInt() and 0xFF) or ((buf[offset + 1].toInt() and 0xFF) shl 8)

  private fun readU32LE(buf: ByteArray, offset: Int): Long =
    (buf[offset].toLong() and 0xFFL) or
      ((buf[offset + 1].toLong() and 0xFFL) shl 8) or
      ((buf[offset + 2].toLong() and 0xFFL) shl 16) or
      ((buf[offset + 3].toLong() and 0xFFL) shl 24)
}
