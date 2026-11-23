package com.example.core.psx

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.CRC32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure Kotlin PlayStation 1 disc image parser supporting:
 * - Raw Mode 2/2352 & Mode 1/2352 (.bin / .img)
 * - Mode 1/2048 (.iso)
 * - PSP PSone Classics (.pbp) header detection
 * - Compressed Hunks of Data (.chd) header detection
 * - .cue sheet parsing
 *
 * Accepts BOTH original vanilla SLUS-01411 images and modded Yu-Gi-Oh! Forbidden Memories ROMs
 * without rejecting altered WA_MRG.MRG or SLUS_014.11 hashes.
 */
object PsxDiscParser {

  // Known retail SLUS-01411 (NTSC-U Yu-Gi-Oh! Forbidden Memories) reference sizes for comparison only
  private const val VANILLA_WA_MRG_APPROX_MIN = 35_000_000L
  private const val VANILLA_SLUS_01411_SIZE = 743_424L
  private val MODE_SYNC_PATTERN =
    byteArrayOf(
      0x00,
      0xFF.toByte(),
      0xFF.toByte(),
      0xFF.toByte(),
      0xFF.toByte(),
      0xFF.toByte(),
      0xFF.toByte(),
      0xFF.toByte(),
      0xFF.toByte(),
      0xFF.toByte(),
      0xFF.toByte(),
      0x00,
    )

  suspend fun analyzeDisc(contentResolver: ContentResolver, uri: Uri): Result<PsxDiscAnalysis> =
    withContext(Dispatchers.IO) {
      runCatching {
        val (displayName, reportedSize) = queryDisplayNameAndSize(contentResolver, uri)
        val lowerName = displayName.lowercase()

        if (lowerName.endsWith(".cue")) {
          return@runCatching parseCueSheet(contentResolver, uri, displayName, reportedSize)
        }

        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
          FileInputStream(pfd.fileDescriptor).use { fis ->
            val channel = fis.channel
            val channelSize = runCatching { channel.size() }.getOrDefault(0L)
            val fileSize = maxOf(reportedSize, pfd.statSize.coerceAtLeast(0L), channelSize)
            analyzeChannel(displayName, uri.toString(), fileSize, channel)
          }
        }
          ?: error("Unable to open file descriptor for $displayName")
      }
    }

  suspend fun readSectorHex(
    contentResolver: ContentResolver,
    uri: Uri,
    lba: Int,
    sectorStride: Int,
    userDataOffset: Int,
  ): Result<SectorHexDump> =
    withContext(Dispatchers.IO) {
      runCatching {
        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
          FileInputStream(pfd.fileDescriptor).use { fis ->
            val channel = fis.channel
            val safeLba = lba.coerceAtLeast(0)
            val byteOffset = safeLba.toLong() * sectorStride + userDataOffset
            val buf = readBytesAt(channel, byteOffset, 2048)
            SectorHexDump(
              lba = safeLba,
              byteOffset = byteOffset,
              sectorStride = sectorStride,
              rawBytes = buf,
            )
          }
        } ?: error("Unable to open disc image")
      }
    }

  suspend fun scanDiscStringsAndStats(
    contentResolver: ContentResolver,
    uri: Uri,
    analysis: PsxDiscAnalysis,
  ): Result<DiscBinaryScanResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
          FileInputStream(pfd.fileDescriptor).use { fis ->
            val channel = fis.channel
            val targetEntry =
              analysis.files.firstOrNull {
                !it.isDirectory &&
                  (it.name.equals(analysis.bootExecutablePath.substringAfterLast('\\'), true) ||
                    it.name.startsWith("SLUS", true) ||
                    it.name.startsWith("SLES", true) ||
                    it.name.startsWith("SLPM", true))
              } ?: analysis.waMrgEntry

            val scanLba = targetEntry?.lba ?: 16
            val bytesToScan = (targetEntry?.sizeBytes ?: (256 * 1024L)).coerceAtMost(1_048_576L)
            val data =
              readLogicalFileBytes(
                channel = channel,
                startLba = scanLba,
                byteLength = bytesToScan.toInt(),
                stride = analysis.sectorStride,
                userOffset = analysis.userDataOffset,
              )

            val strings = extractPrintableStrings(data, minLength = 5, maxItems = 140)
            val keywords =
              strings
                .filter { str ->
                  val u = str.uppercase()
                  u.contains("YUGI") ||
                    u.contains("DUEL") ||
                    u.contains("CARD") ||
                    u.contains("SLUS") ||
                    u.contains("MOD") ||
                    u.contains("FUSION") ||
                    u.contains("STARCHIP") ||
                    u.contains("KONAMI") ||
                    u.contains("FORBIDDEN") ||
                    u.contains("DECK") ||
                    u.contains("PASS")
                }
                .distinct()
                .take(40)

            DiscBinaryScanResult(
              scannedFileName = targetEntry?.fullPath ?: "Sector 16+",
              scannedBytes = data.size.toLong(),
              extractedStrings = strings,
              detectedCardKeywords = keywords,
              exeEntryPointsCount = strings.count { it.contains("cdrom:", true) },
            )
          }
        } ?: error("Unable to open disc image for scanning")
      }
    }

  private fun parseCueSheet(
    contentResolver: ContentResolver,
    uri: Uri,
    displayName: String,
    fileSize: Long,
  ): PsxDiscAnalysis {
    val cueText =
      contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
    val fileRegex = Regex("""FILE\s+"([^"]+)"\s+\w+""", RegexOption.IGNORE_CASE)
    val referenced = fileRegex.findAll(cueText).map { it.groupValues[1] }.toList()
    val isFmName =
      displayName.contains("yugi", true) ||
        displayName.contains("forbidden", true) ||
        displayName.contains("fm", true) ||
        referenced.any { it.contains("yugi", true) || it.contains("mod", true) }

    return PsxDiscAnalysis(
      displayName = displayName,
      uriString = uri.toString(),
      fileSizeBytes = fileSize,
      format = DiscFormat.CUE_SHEET,
      sectorStride = 2352,
      userDataOffset = 24,
      volumeId = referenced.firstOrNull() ?: displayName.substringBeforeLast('.'),
      systemId = "PLAYSTATION CUE DESCRIPTOR",
      totalSectors = 0L,
      serialCode = if (isFmName) "SLUS-01411 (CUE)" else "CUE-DESCRIPTOR",
      region = "Multi-Track CUE",
      bootExecutablePath = referenced.firstOrNull().orEmpty(),
      systemCnfText = cueText.take(600),
      modStatus = DiscModStatus.CUE_SHEET,
      modReasons =
        listOf(
          "CUE descriptor references ${referenced.size} binary track file(s).",
          "Load the companion .bin file directly to inspect ISO-9660 & WA_MRG.MRG sectors.",
        ),
      exeHeader = null,
      waMrgEntry = null,
      waMrgSampleCrc32 = null,
      files = emptyList(),
      cueReferencedFiles = referenced,
    )
  }

  internal fun analyzeChannel(
    displayName: String,
    uriString: String,
    fileSize: Long,
    channel: FileChannel,
  ): PsxDiscAnalysis {
    val head = readBytesAt(channel, 0L, 64)
    if (head.size >= 4 && head[0] == 0x00.toByte() && head[1] == 'P'.code.toByte() && head[2] == 'B'.code.toByte() && head[3] == 'P'.code.toByte()) {
      return parsePbpContainer(displayName, uriString, fileSize, head)
    }
    if (head.size >= 8 && String(head, 0, 8, Charsets.US_ASCII) == "MComprHD") {
      return parseChdContainer(displayName, uriString, fileSize, head)
    }

    // Detect sector stride & user data offset by checking PVD at sector 16
    val (format, stride, userOffset) = detectSectorLayout(channel, fileSize, displayName)
    if (stride == 0) {
      return PsxDiscAnalysis(
        displayName = displayName,
        uriString = uriString,
        fileSizeBytes = fileSize,
        format = DiscFormat.UNKNOWN,
        sectorStride = 2048,
        userDataOffset = 0,
        volumeId = displayName.substringBeforeLast('.'),
        systemId = "UNRECOGNIZED IMAGE",
        totalSectors = fileSize / 2048,
        serialCode = "UNKNOWN",
        region = "Unknown",
        bootExecutablePath = "",
        systemCnfText = "",
        modStatus = DiscModStatus.OTHER_PSX,
        modReasons = listOf("ISO-9660 Primary Volume Descriptor (CD001) not found at Sector 16."),
        exeHeader = null,
        waMrgEntry = null,
        waMrgSampleCrc32 = null,
        files = emptyList(),
      )
    }

    val pvd = readBytesAt(channel, 16L * stride + userOffset, 2048)
    val systemId = readPaddedAscii(pvd, 8, 32)
    val volumeId = readPaddedAscii(pvd, 40, 32)
    val totalSectors = readUInt32LE(pvd, 80)

    // Root directory record starts at offset 156 (34 bytes) inside PVD
    val rootExtentLba = readUInt32LE(pvd, 156 + 2).toInt()
    val rootDataLen = readUInt32LE(pvd, 156 + 10).toInt().coerceIn(2048, 65536)

    val allFiles = mutableListOf<IsoFileEntry>()
    walkDirectory(
      channel = channel,
      dirLba = rootExtentLba,
      dirSize = rootDataLen,
      parentPath = "",
      stride = stride,
      userOffset = userOffset,
      depth = 0,
      out = allFiles,
    )

    val sysCnfEntry = allFiles.firstOrNull { !it.isDirectory && it.name.equals("SYSTEM.CNF", true) }
    val sysCnfText =
      if (sysCnfEntry != null && sysCnfEntry.sizeBytes in 1..4096) {
        val raw =
          readLogicalFileBytes(
            channel,
            sysCnfEntry.lba,
            sysCnfEntry.sizeBytes.toInt(),
            stride,
            userOffset,
          )
        String(raw, Charsets.US_ASCII).trim('\u0000', ' ', '\r', '\n')
      } else {
        ""
      }

    val bootPath = parseBootPathFromSystemCnf(sysCnfText)
    val serial = extractSerialCode(bootPath, allFiles)
    val region = inferRegionFromSerial(serial)

    val exeFileName = bootPath.substringAfterLast('\\').substringAfterLast('/')
    val exeEntry =
      allFiles.firstOrNull { !it.isDirectory && it.name.equals(exeFileName, true) }
        ?: allFiles.firstOrNull {
          !it.isDirectory &&
            (it.name.startsWith("SLUS", true) ||
              it.name.startsWith("SLES", true) ||
              it.name.startsWith("SLPM", true))
        }

    val exeHeader =
      exeEntry?.let { entry ->
        val exeBytes =
          readLogicalFileBytes(
            channel,
            entry.lba,
            entry.sizeBytes.coerceAtMost(2048 * 8).toInt(),
            stride,
            userOffset,
          )
        parsePsxExeHeader(entry.name, exeBytes)
      }

    val waMrgEntry =
      allFiles.firstOrNull { !it.isDirectory && it.name.equals("WA_MRG.MRG", true) }
        ?: allFiles.firstOrNull { !it.isDirectory && it.name.endsWith(".MRG", true) }

    val waMrgCrc =
      waMrgEntry?.let { entry ->
        val sample =
          readLogicalFileBytes(
            channel,
            entry.lba,
            entry.sizeBytes.coerceAtMost(262_144L).toInt(),
            stride,
            userOffset,
          )
        crc32Hex(sample)
      }

    val (modStatus, modReasons) =
      classifyDiscModStatus(
        displayName = displayName,
        volumeId = volumeId,
        serial = serial,
        exeEntry = exeEntry,
        waMrgEntry = waMrgEntry,
        allFiles = allFiles,
      )

    return PsxDiscAnalysis(
      displayName = displayName,
      uriString = uriString,
      fileSizeBytes = fileSize,
      format = format,
      sectorStride = stride,
      userDataOffset = userOffset,
      volumeId = volumeId.ifBlank { displayName.substringBeforeLast('.') },
      systemId = systemId.ifBlank { "PLAYSTATION" },
      totalSectors = totalSectors,
      serialCode = serial,
      region = region,
      bootExecutablePath = bootPath,
      systemCnfText = sysCnfText,
      modStatus = modStatus,
      modReasons = modReasons,
      exeHeader = exeHeader,
      waMrgEntry = waMrgEntry,
      waMrgSampleCrc32 = waMrgCrc,
      files = allFiles,
    )
  }

  private fun parsePbpContainer(
    displayName: String,
    uriString: String,
    fileSize: Long,
    head: ByteArray,
  ): PsxDiscAnalysis {
    val version = readUInt32LE(head, 4)
    val paramOffset = readUInt32LE(head, 8)
    val psarOffset = readUInt32LE(head, 36)
    val isFm =
      displayName.contains("yugi", true) ||
        displayName.contains("forbidden", true) ||
        displayName.contains("fm", true) ||
        displayName.contains("mod", true)
    return PsxDiscAnalysis(
      displayName = displayName,
      uriString = uriString,
      fileSizeBytes = fileSize,
      format = DiscFormat.PBP_PSONE,
      sectorStride = 2352,
      userDataOffset = 24,
      volumeId = displayName.substringBeforeLast('.'),
      systemId = "PSP PSONE EBOOT (v0x${version.toString(16)})",
      totalSectors = (fileSize - psarOffset).coerceAtLeast(0L) / 2048L,
      serialCode = if (isFm) "SLUS-01411 (PBP)" else "PSONE-PBP",
      region = "PSone EBOOT",
      bootExecutablePath = "DATA.PSAR @ 0x${psarOffset.toString(16)}",
      systemCnfText = "PBP Header v$version\nPARAM.SFO @ 0x${paramOffset.toString(16)}\nDATA.PSAR @ 0x${psarOffset.toString(16)}",
      modStatus = if (isFm) DiscModStatus.MODDED_FM else DiscModStatus.OTHER_PSX,
      modReasons =
        listOf(
          "Valid PBP PSone Classics container detected (PSAR offset 0x${psarOffset.toString(16)}).",
          "Accepted by ePSXe-style Mod Loader Mode without requiring raw vanilla .bin.",
        ),
      exeHeader = null,
      waMrgEntry = null,
      waMrgSampleCrc32 = null,
      files = emptyList(),
    )
  }

  private fun parseChdContainer(
    displayName: String,
    uriString: String,
    fileSize: Long,
    head: ByteArray,
  ): PsxDiscAnalysis {
    val chdVersion =
      ByteBuffer.wrap(head, 12, 4).order(ByteOrder.BIG_ENDIAN).int
    val isFm =
      displayName.contains("yugi", true) ||
        displayName.contains("forbidden", true) ||
        displayName.contains("mod", true)
    return PsxDiscAnalysis(
      displayName = displayName,
      uriString = uriString,
      fileSizeBytes = fileSize,
      format = DiscFormat.CHD_COMPRESSED,
      sectorStride = 2352,
      userDataOffset = 24,
      volumeId = displayName.substringBeforeLast('.'),
      systemId = "MAME CHD v$chdVersion",
      totalSectors = fileSize / 2048L,
      serialCode = if (isFm) "SLUS-01411 (CHD)" else "CHD-DISC",
      region = "Compressed Hunks",
      bootExecutablePath = "Compressed CD-ROM Track",
      systemCnfText = "Magic: MComprHD\nCHD Format Version: $chdVersion",
      modStatus = if (isFm) DiscModStatus.MODDED_FM else DiscModStatus.OTHER_PSX,
      modReasons =
        listOf(
          "Valid CHD v$chdVersion disc archive detected.",
          "Ready for emulator handoff (DuckStation / RetroArch) in Mod Loader Mode.",
        ),
      exeHeader = null,
      waMrgEntry = null,
      waMrgSampleCrc32 = null,
      files = emptyList(),
    )
  }

  private fun detectSectorLayout(
    channel: FileChannel,
    fileSize: Long,
    displayName: String,
  ): Triple<DiscFormat, Int, Int> {
    // Check 2352-byte Mode 2 Form 1 (offset 24) & Mode 1 (offset 16)
    if (fileSize >= 17L * 2352L) {
      val s16 = readBytesAt(channel, 16L * 2352L, 40)
      if (s16.size >= 32) {
        val hasSync = s16.copyOfRange(0, 12).contentEquals(MODE_SYNC_PATTERN)
        if (hasSync && s16[24] == 0x01.toByte() && isCd001(s16, 25)) {
          return Triple(DiscFormat.BIN_2352, 2352, 24)
        }
        if (hasSync && s16[16] == 0x01.toByte() && isCd001(s16, 17)) {
          return Triple(DiscFormat.BIN_2352, 2352, 16)
        }
        if (s16[24] == 0x01.toByte() && isCd001(s16, 25)) {
          return Triple(DiscFormat.BIN_2352, 2352, 24)
        }
      }
    }
    // Check 2048-byte ISO (offset 0)
    if (fileSize >= 17L * 2048L) {
      val s16 = readBytesAt(channel, 16L * 2048L, 16)
      if (s16.size >= 6 && s16[0] == 0x01.toByte() && isCd001(s16, 1)) {
        val fmt =
          if (displayName.lowercase().endsWith(".bin") || displayName.lowercase().endsWith(".img")) {
            DiscFormat.ISO_2048
          } else {
            DiscFormat.ISO_2048
          }
        return Triple(fmt, 2048, 0)
      }
    }
    return Triple(DiscFormat.UNKNOWN, 0, 0)
  }

  private fun isCd001(buf: ByteArray, offset: Int): Boolean {
    if (offset + 5 > buf.size) return false
    return buf[offset] == 'C'.code.toByte() &&
      buf[offset + 1] == 'D'.code.toByte() &&
      buf[offset + 2] == '0'.code.toByte() &&
      buf[offset + 3] == '0'.code.toByte() &&
      buf[offset + 4] == '1'.code.toByte()
  }

  private fun walkDirectory(
    channel: FileChannel,
    dirLba: Int,
    dirSize: Int,
    parentPath: String,
    stride: Int,
    userOffset: Int,
    depth: Int,
    out: MutableList<IsoFileEntry>,
  ) {
    if (depth > 4 || dirLba <= 0 || dirSize <= 0 || out.size > 300) return
    val clampedSize = dirSize.coerceAtMost(65536)
    val data = readLogicalFileBytes(channel, dirLba, clampedSize, stride, userOffset)
    var pos = 0
    while (pos < data.size) {
      val recordLen = data[pos].toInt() and 0xFF
      if (recordLen == 0) {
        // Skip to next 2048-byte sector boundary
        val nextSector = ((pos / 2048) + 1) * 2048
        if (nextSector >= data.size) break
        pos = nextSector
        continue
      }
      if (pos + recordLen > data.size || recordLen < 34) break

      val extentLba = readUInt32LE(data, pos + 2).toInt()
      val dataLen = readUInt32LE(data, pos + 10)
      val flags = data[pos + 25].toInt() and 0xFF
      val isDir = (flags and 0x02) != 0
      val nameLen = data[pos + 32].toInt() and 0xFF
      if (nameLen > 0 && pos + 33 + nameLen <= data.size) {
        val rawName = String(data, pos + 33, nameLen, Charsets.US_ASCII)
        // Skip '.' (0x00) and '..' (0x01)
        if (rawName != "\u0000" && rawName != "\u0001") {
          val cleanName = rawName.substringBefore(';')
          val fullPath = if (parentPath.isEmpty()) cleanName else "$parentPath/$cleanName"
          out.add(
            IsoFileEntry(
              name = cleanName,
              fullPath = fullPath,
              lba = extentLba,
              sizeBytes = dataLen,
              isDirectory = isDir,
            )
          )
          if (isDir && extentLba != dirLba) {
            walkDirectory(
              channel = channel,
              dirLba = extentLba,
              dirSize = dataLen.toInt(),
              parentPath = fullPath,
              stride = stride,
              userOffset = userOffset,
              depth = depth + 1,
              out = out,
            )
          }
        }
      }
      pos += recordLen
    }
  }

  private fun parseBootPathFromSystemCnf(cnf: String): String {
    val line =
      cnf
        .lineSequence()
        .firstOrNull { it.trim().startsWith("BOOT", ignoreCase = true) }
        .orEmpty()
    val raw = line.substringAfter('=', "").trim()
    return raw.substringAfter("cdrom:", raw).trimStart('\\', '/').substringBefore(';')
  }

  private fun extractSerialCode(bootPath: String, files: List<IsoFileEntry>): String {
    val candidate =
      bootPath.substringAfterLast('\\').substringAfterLast('/').ifBlank {
        files
          .firstOrNull {
            !it.isDirectory &&
              (it.name.startsWith("SLUS", true) ||
                it.name.startsWith("SLES", true) ||
                it.name.startsWith("SLPM", true) ||
                it.name.startsWith("SCUS", true))
          }
          ?.name
          .orEmpty()
      }
    if (candidate.isBlank()) return "UNKNOWN"
    val clean = candidate.replace("_", "").replace("-", "").replace(".", "").uppercase()
    return if (clean.length >= 9) "${clean.substring(0, 4)}-${clean.substring(4)}" else clean
  }

  private fun inferRegionFromSerial(serial: String): String {
    val u = serial.uppercase()
    return when {
      u.startsWith("SLUS") || u.startsWith("SCUS") -> "NTSC-U (USA)"
      u.startsWith("SLES") || u.startsWith("SCES") -> "PAL (Europe)"
      u.startsWith("SLPM") || u.startsWith("SLPS") || u.startsWith("SCPS") -> "NTSC-J (Japan)"
      else -> "PS1 Region"
    }
  }

  private fun parsePsxExeHeader(name: String, bytes: ByteArray): PsxExeHeader {
    if (bytes.size < 64) {
      return PsxExeHeader(name, false, 0, 0, 0, 0, 0, "", crc32Hex(bytes))
    }
    val magic = String(bytes, 0, 8, Charsets.US_ASCII)
    val pc0 = readUInt32LE(bytes, 0x10)
    val gp0 = readUInt32LE(bytes, 0x14)
    val tAddr = readUInt32LE(bytes, 0x18)
    val tSize = readUInt32LE(bytes, 0x1C)
    val spAddr = readUInt32LE(bytes, 0x30)
    val marker =
      String(bytes, 0x4C.coerceAtMost(bytes.size), (bytes.size - 0x4C).coerceAtMost(64), Charsets.US_ASCII)
        .substringBefore('\u0000')
        .trim()
    return PsxExeHeader(
      fileName = name,
      magicValid = magic == "PS-X EXE",
      pc0 = pc0,
      gp0 = gp0,
      textAddress = tAddr,
      textSize = tSize,
      stackPointer = spAddr,
      markerText = marker,
      crc32Hex = crc32Hex(bytes),
    )
  }

  private fun classifyDiscModStatus(
    displayName: String,
    volumeId: String,
    serial: String,
    exeEntry: IsoFileEntry?,
    waMrgEntry: IsoFileEntry?,
    allFiles: List<IsoFileEntry>,
  ): Pair<DiscModStatus, List<String>> {
    val reasons = mutableListOf<String>()
    val isFmSerial =
      serial.contains("01411") ||
        serial.contains("03947") ||
        serial.contains("86398") ||
        waMrgEntry != null ||
        allFiles.any { it.name.equals("WA_MRG.MRG", true) }

    if (!isFmSerial) {
      reasons += "PS1 disc image ($serial) parsed via ISO-9660."
      reasons += "Mod Loader Mode allows loading any PS1 .bin/.iso without hash locks."
      return DiscModStatus.OTHER_PSX to reasons
    }

    var looksModded = false
    val lowerName = displayName.lowercase()
    if (
      lowerName.contains("mod") ||
        lowerName.contains("remaster") ||
        lowerName.contains("alpha") ||
        lowerName.contains("beta") ||
        lowerName.contains("15") ||
        lowerName.contains("13") ||
        lowerName.contains("drop") ||
        lowerName.contains("patch")
    ) {
      looksModded = true
      reasons += "Filename indicates a modded Yu-Gi-Oh! FM build."
    }

    if (volumeId.isNotBlank() && !volumeId.equals("YUGIOH", true) && !volumeId.equals("SLUS_01411", true)) {
      looksModded = true
      reasons += "Custom ISO volume label detected: \"$volumeId\"."
    }

    if (exeEntry != null && serial.contains("01411") && exeEntry.sizeBytes != VANILLA_SLUS_01411_SIZE) {
      looksModded = true
      reasons += "Boot executable size (${exeEntry.sizeBytes} B) differs from vanilla SLUS_014.11."
    }

    if (waMrgEntry != null && waMrgEntry.sizeBytes < VANILLA_WA_MRG_APPROX_MIN) {
      looksModded = true
      reasons += "WA_MRG.MRG archive size (${waMrgEntry.sizeBytes} B) differs from stock dump."
    }

    return if (looksModded) {
      reasons += "Vanilla Recompiled check bypassed — modded disc ready to inspect & launch."
      DiscModStatus.MODDED_FM to reasons
    } else {
      reasons += "Matches Forbidden Memories ($serial) structure (WA_MRG.MRG present)."
      reasons += "Both vanilla and modded sectors can be loaded freely in Mod Loader Mode."
      DiscModStatus.ORIGINAL_FM to reasons
    }
  }

  internal fun readLogicalFileBytes(
    channel: FileChannel,
    startLba: Int,
    byteLength: Int,
    stride: Int,
    userOffset: Int,
  ): ByteArray {
    if (byteLength <= 0 || startLba < 0) return ByteArray(0)
    val result = ByteArray(byteLength)
    var remaining = byteLength
    var outPos = 0
    var currentLba = startLba.toLong()
    val sectorBuf = ByteBuffer.allocate(2048)

    while (remaining > 0) {
      sectorBuf.clear()
      val offset = currentLba * stride + userOffset
      val read = channel.read(sectorBuf, offset)
      if (read <= 0) break
      val chunk = minOf(remaining, read)
      System.arraycopy(sectorBuf.array(), 0, result, outPos, chunk)
      outPos += chunk
      remaining -= chunk
      currentLba++
    }
    return if (outPos == byteLength) result else result.copyOf(outPos)
  }

  private fun readBytesAt(channel: FileChannel, offset: Long, length: Int): ByteArray {
    if (offset < 0 || length <= 0) return ByteArray(0)
    val buf = ByteBuffer.allocate(length)
    var totalRead = 0
    while (totalRead < length) {
      val n = channel.read(buf, offset + totalRead)
      if (n <= 0) break
      totalRead += n
    }
    return if (totalRead == length) buf.array() else buf.array().copyOf(totalRead)
  }

  private fun queryDisplayNameAndSize(contentResolver: ContentResolver, uri: Uri): Pair<String, Long> {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "disc_image.bin"
    var size = 0L
    runCatching {
      contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
          }
        }
    }
    return name to size
  }

  private fun readPaddedAscii(buf: ByteArray, offset: Int, len: Int): String {
    if (offset + len > buf.size) return ""
    return String(buf, offset, len, Charsets.US_ASCII)
      .replace("\u0000", "")
      .trim()
  }

  private fun readUInt32LE(buf: ByteArray, offset: Int): Long {
    if (offset + 4 > buf.size) return 0L
    return (buf[offset].toLong() and 0xFFL) or
      ((buf[offset + 1].toLong() and 0xFFL) shl 8) or
      ((buf[offset + 2].toLong() and 0xFFL) shl 16) or
      ((buf[offset + 3].toLong() and 0xFFL) shl 24)
  }

  private fun crc32Hex(bytes: ByteArray): String {
    val crc = CRC32()
    crc.update(bytes)
    return crc.value.toString(16).uppercase().padStart(8, '0')
  }

  private fun extractPrintableStrings(
    data: ByteArray,
    minLength: Int,
    maxItems: Int,
  ): List<String> {
    val results = LinkedHashSet<String>()
    val sb = StringBuilder()
    for (b in data) {
      val c = b.toInt() and 0xFF
      if (c in 0x20..0x7E) {
        sb.append(c.toChar())
      } else {
        if (sb.length >= minLength) {
          val s = sb.toString().trim()
          if (s.length >= minLength && s.any { it.isLetterOrDigit() }) {
            results.add(s)
            if (results.size >= maxItems) break
          }
        }
        sb.setLength(0)
      }
    }
    return results.toList()
  }
}
