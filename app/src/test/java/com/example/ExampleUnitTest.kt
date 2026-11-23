package com.example

import com.example.core.psx.DiscFormat
import com.example.core.psx.DiscModStatus
import com.example.core.psx.FmDirectRuntime
import com.example.core.psx.FmDropWeightEntry
import com.example.core.psx.PsxDiscParser
import com.example.core.psx.PsxMipsEngine
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

  @Test
  fun `mips engine boots PS-X EXE and executes LUI ORI ADDIU instructions`() {
    val exe = ByteArray(0x800 + 16)
    "PS-X EXE".toByteArray(Charsets.US_ASCII).copyInto(exe, 0)
    val buf = ByteBuffer.wrap(exe).order(ByteOrder.LITTLE_ENDIAN)
    buf.putInt(0x10, 0x80010000.toInt()) // PC0
    buf.putInt(0x14, 0x80100000.toInt()) // GP0
    buf.putInt(0x18, 0x80010000.toInt()) // t_addr
    buf.putInt(0x1C, 16) // t_size
    buf.putInt(0x30, 0x801FFF00.toInt()) // sp

    // lui $v0, 0x1234 -> 0x3C021234
    buf.putInt(0x800, 0x3C021234)
    // ori $v0, $v0, 0x5678 -> 0x34425678
    buf.putInt(0x804, 0x34425678)
    // addiu $a0, $v0, 2 -> 0x24440002
    buf.putInt(0x808, 0x24440002)

    val loaded = PsxMipsEngine.loadExeIntoRam(exe)
    assertNotNull(loaded)
    val (header, ram) = loaded!!
    val initCpu = PsxMipsEngine.createInitialState(header)
    val after = PsxMipsEngine.stepCycles(ram, initCpu, 3)

    assertEquals(0x12345678L, after.v0)
    assertEquals(0x1234567AL, after.a0)
    assertEquals(3L, after.cyclesExecuted)
    assertTrue(after.trace.first().mnemonic.startsWith("lui \$v0"))
  }

  @Test
  fun `decodes packed 9-bit FM card stats and 4bpp TIM texture`() {
    val ram = ByteArray(PsxMipsEngine.PS1_RAM_SIZE)
    val tableOff = PsxMipsEngine.mapRamOffset(0x801D4244L)!!
    val buf = ByteBuffer.wrap(ram).order(ByteOrder.LITTLE_ENDIAN)
    // Fill 150 cards: ATK=3000 (300), DEF=2500 (250) -> packed = 300 or (250 shl 9)
    val packedBlueEyes = 300 or (250 shl 9) or (1 shl 18) or (2 shl 22)
    for (i in 0 until 150) {
      buf.putInt(tableOff + i * 4, packedBlueEyes)
    }
    val (cards, addr) = FmDirectRuntime.extractFmCardStats(ram)
    assertEquals(0x801D4244L, addr)
    assertTrue(cards.size >= 150)
    assertEquals(3000, cards[0].atk)
    assertEquals(2500, cards[0].def)

    // Minimal valid 8x8 4bpp TIM with 16-color CLUT
    val tim = ByteArray(8 + (12 + 32) + (12 + 32))
    val tBuf = ByteBuffer.wrap(tim).order(ByteOrder.LITTLE_ENDIAN)
    tBuf.putInt(0, 0x10) // magic
    tBuf.putInt(4, 0x08) // 4bpp + CLUT
    tBuf.putInt(8, 12 + 32) // CLUT size
    tBuf.putShort(20, 0x7FFF.toShort()) // palette color 0 non-zero
    tBuf.putShort(22, 0x001F.toShort()) // palette color 1 red
    val imgStart = 8 + 44
    tBuf.putInt(imgStart, 12 + 32)
    tBuf.putShort(imgStart + 8, 2) // wUnits = 2 -> width = 8 px
    tBuf.putShort(imgStart + 10, 8) // height = 8 px
    for (p in 0 until 32) tim[imgStart + 12 + p] = 0x10.toByte()

    val decoded = FmDirectRuntime.tryDecodeTimAt(tim, 0)
    assertNotNull(decoded)
    assertEquals(8, decoded!!.width)
    assertEquals(8, decoded.height)
    assertEquals(4, decoded.bpp)

    val drops =
      FmDirectRuntime.rollDropsWithMultiplier(
        dropPool = listOf(FmDropWeightEntry(1, 2000), FmDropWeightEntry(380, 1)),
        cardsById = emptyMap(),
        multiplier = 99,
        rollCount = 5,
        random = Random(42),
      )
    assertEquals(5, drops.size)
  }
}
