package com.example

import com.example.core.psx.DiscFormat
import com.example.core.psx.DiscModStatus
import com.example.core.psx.PsxDiscParser
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun `parses modded PS1 2352-byte BIN with ISO-9660 SYSTEM CNF and WA_MRG`() {
    val tempBin = File.createTempFile("yugioh_fm_mod_15", ".bin")
    tempBin.deleteOnExit()

    val totalSectors = 25
    val bin = ByteArray(totalSectors * 2352)

    fun writeMode2Sector(lba: Int, userData: ByteArray) {
      val base = lba * 2352
      bin[base] = 0x00
      for (i in 1..10) bin[base + i] = 0xFF.toByte()
      bin[base + 11] = 0x00
      System.arraycopy(userData, 0, bin, base + 24, minOf(2048, userData.size))
    }

    val pvd = ByteArray(2048)
    pvd[0] = 0x01
    "CD001".toByteArray(Charsets.US_ASCII).copyInto(pvd, 1)
    "PLAYSTATION".padEnd(32, ' ').toByteArray(Charsets.US_ASCII).copyInto(pvd, 8)
    "FM_MOD_15".padEnd(32, ' ').toByteArray(Charsets.US_ASCII).copyInto(pvd, 40)
    ByteBuffer.wrap(pvd, 80, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalSectors)
    pvd[156] = 34
    ByteBuffer.wrap(pvd, 156 + 2, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(18)
    ByteBuffer.wrap(pvd, 156 + 10, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(2048)
    pvd[156 + 25] = 0x02
    pvd[156 + 32] = 1
    writeMode2Sector(16, pvd)

    val rootDir = ByteArray(2048)
    var pos = 0
    fun appendDirEntry(name: String, lba: Int, size: Int) {
      val nameBytes = name.toByteArray(Charsets.US_ASCII)
      val recLen = 33 + nameBytes.size + if ((33 + nameBytes.size) % 2 != 0) 1 else 0
      rootDir[pos] = recLen.toByte()
      ByteBuffer.wrap(rootDir, pos + 2, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(lba)
      ByteBuffer.wrap(rootDir, pos + 10, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(size)
      rootDir[pos + 25] = 0x00
      rootDir[pos + 32] = nameBytes.size.toByte()
      nameBytes.copyInto(rootDir, pos + 33)
      pos += recLen
    }

    val sysCnf =
      "BOOT = cdrom:\\SLUS_014.11;1\r\nTCB = 4\r\nEVENT = 10\r\nSTACK = 801FFF00\r\n"
        .toByteArray()
    appendDirEntry("SYSTEM.CNF;1", 19, sysCnf.size)
    appendDirEntry("SLUS_014.11;1", 20, 2048)
    appendDirEntry("WA_MRG.MRG;1", 21, 4096)
    writeMode2Sector(18, rootDir)

    writeMode2Sector(19, sysCnf)
    val exe = ByteArray(2048)
    "PS-X EXE".toByteArray(Charsets.US_ASCII).copyInto(exe, 0)
    writeMode2Sector(20, exe)
    writeMode2Sector(21, ByteArray(2048) { 0x42 })

    tempBin.writeBytes(bin)

    RandomAccessFile(tempBin, "r").use { raf ->
      val analysis =
        PsxDiscParser.analyzeChannel(
          displayName = "yugioh_fm_mod_15.bin",
          uriString = "file://${tempBin.absolutePath}",
          fileSize = tempBin.length(),
          channel = raf.channel,
        )
      assertEquals(DiscFormat.BIN_2352, analysis.format)
      assertEquals("SLUS-01411", analysis.serialCode)
      assertEquals(DiscModStatus.MODDED_FM, analysis.modStatus)
      assertEquals(3, analysis.files.size)
      assertTrue(analysis.exeHeader?.magicValid == true)
    }
  }
}
