package com.example.core.psx

/**
 * Pure-Kotlin MIPS R3000A (PS1 CPU) disassembler & safe execution engine.
 * Loads a PS-X EXE into a 2 MB virtual PlayStation RAM buffer (0x80000000..0x801FFFFF),
 * executes instructions from PC0 with branch delay slots, and intercepts PS1 BIOS
 * vectors (A0/B0/C0) and hardware MMIO registers cleanly.
 */
data class MipsTraceEntry(
  val pc: Long,
  val rawInstruction: Int,
  val mnemonic: String,
)

data class MipsCpuState(
  val pc: Long,
  val gprs: LongArray,
  val hi: Long,
  val lo: Long,
  val cyclesExecuted: Long,
  val biosCalls: List<String>,
  val trace: List<MipsTraceEntry>,
  val halted: Boolean = false,
  val haltReason: String? = null,
) {
  val gp: Long get() = gprs[28]
  val sp: Long get() = gprs[29]
  val ra: Long get() = gprs[31]
  val v0: Long get() = gprs[2]
  val v1: Long get() = gprs[3]
  val a0: Long get() = gprs[4]
  val a1: Long get() = gprs[5]
  val a2: Long get() = gprs[6]
  val a3: Long get() = gprs[7]

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MipsCpuState) return false
    return pc == other.pc &&
      gprs.contentEquals(other.gprs) &&
      cyclesExecuted == other.cyclesExecuted &&
      halted == other.halted
  }

  override fun hashCode(): Int {
    var result = pc.hashCode()
    result = 31 * result + gprs.contentHashCode()
    result = 31 * result + cyclesExecuted.hashCode()
    return result
  }
}

object PsxMipsEngine {
  const val PS1_RAM_SIZE = 2 * 1024 * 1024

  private val REG_NAMES =
    arrayOf(
      "zero", "at", "v0", "v1", "a0", "a1", "a2", "a3",
      "t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7",
      "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7",
      "t8", "t9", "k0", "k1", "gp", "sp", "fp", "ra",
    )

  fun createInitialState(exeHeader: PsxExeHeader): MipsCpuState {
    val gprs = LongArray(32)
    gprs[28] = exeHeader.gp0 and 0xFFFFFFFFL
    gprs[29] = (if (exeHeader.stackPointer != 0L) exeHeader.stackPointer else 0x801FFF00L) and 0xFFFFFFFFL
    gprs[30] = gprs[29]
    gprs[31] = 0x80000000L // Sentinel return to BIOS kernel
    val startPc = exeHeader.pc0 and 0xFFFFFFFFL
    return MipsCpuState(
      pc = startPc,
      gprs = gprs,
      hi = 0L,
      lo = 0L,
      cyclesExecuted = 0L,
      biosCalls = listOf("Boot PC0=0x${hex8(startPc)} GP=0x${hex8(gprs[28])} SP=0x${hex8(gprs[29])}"),
      trace = emptyList(),
      halted = !exeHeader.magicValid || startPc == 0L,
      haltReason = if (!exeHeader.magicValid) "Missing PS-X EXE header" else null,
    )
  }

  fun loadExeIntoRam(exeBytes: ByteArray): Pair<PsxExeHeader, ByteArray>? {
    if (exeBytes.size < 0x800) return null
    val magic = String(exeBytes, 0, 8, Charsets.US_ASCII)
    if (magic != "PS-X EXE") return null

    val pc0 = readU32LE(exeBytes, 0x10)
    val gp0 = readU32LE(exeBytes, 0x14)
    val tAddr = readU32LE(exeBytes, 0x18)
    val tSize = readU32LE(exeBytes, 0x1C)
    val spAddr = readU32LE(exeBytes, 0x30)
    val marker =
      String(exeBytes, 0x4C, (exeBytes.size - 0x4C).coerceAtMost(64), Charsets.US_ASCII)
        .substringBefore('\u0000')
        .trim()

    val ram = ByteArray(PS1_RAM_SIZE)
    val payloadLen = minOf(exeBytes.size - 0x800, tSize.toInt().coerceAtLeast(0))
    val ramOffset = (tAddr and 0x001FFFFFL).toInt()
    if (payloadLen > 0 && ramOffset in 0 until PS1_RAM_SIZE) {
      val copyLen = minOf(payloadLen, PS1_RAM_SIZE - ramOffset)
      System.arraycopy(exeBytes, 0x800, ram, ramOffset, copyLen)
    }

    val header =
      PsxExeHeader(
        fileName = "BOOT.EXE",
        magicValid = true,
        pc0 = pc0,
        gp0 = gp0,
        textAddress = tAddr,
        textSize = tSize,
        stackPointer = spAddr,
        markerText = marker,
        crc32Hex = "",
      )
    return header to ram
  }

  fun stepCycles(ram: ByteArray, state: MipsCpuState, count: Int): MipsCpuState {
    if (state.halted || count <= 0) return state
    val regs = state.gprs.copyOf()
    var pc = state.pc
    var hi = state.hi
    var lo = state.lo
    var cycles = state.cyclesExecuted
    val biosLog = state.biosCalls.toMutableList()
    val traceLog = state.trace.toMutableList()
    var halted = false
    var haltReason: String? = null

    repeat(count) {
      if (halted) return@repeat

      val biosTrap = handleBiosVectorIfMatched(pc, regs)
      if (biosTrap != null) {
        biosLog.add(biosTrap)
        if (biosLog.size > 40) biosLog.removeAt(0)
        // Return from BIOS call via $ra
        val retPc = regs[31] and 0xFFFFFFFFL
        traceLog.add(MipsTraceEntry(pc, 0, "BIOS -> ret 0x${hex8(retPc)}"))
        if (traceLog.size > 60) traceLog.removeAt(0)
        pc = retPc
        cycles++
        if (pc == 0x80000000L || pc == 0L) {
          halted = true
          haltReason = "Returned to PS1 Kernel"
        }
        return@repeat
      }

      val ramOff = mapRamOffset(pc)
      if (ramOff == null) {
        halted = true
        haltReason = "PC outside PS1 2MB RAM: 0x${hex8(pc)}"
        return@repeat
      }

      val instr = readS32LE(ram, ramOff)
      val mnemonic = disassemble(pc, instr)
      traceLog.add(MipsTraceEntry(pc, instr, mnemonic))
      if (traceLog.size > 60) traceLog.removeAt(0)

      val stepOut = executeOne(ram, pc, instr, regs, hi, lo)
      hi = stepOut.hi
      lo = stepOut.lo
      regs[0] = 0L

      if (stepOut.biosNote != null) {
        biosLog.add(stepOut.biosNote)
        if (biosLog.size > 40) biosLog.removeAt(0)
      }

      if (stepOut.branchTaken && stepOut.nextTarget != null) {
        // Execute MIPS branch delay slot at pc + 4 before taking target
        val delayPc = (pc + 4L) and 0xFFFFFFFFL
        val delayOff = mapRamOffset(delayPc)
        if (delayOff != null) {
          val delayInstr = readS32LE(ram, delayOff)
          val delayRes = executeOne(ram, delayPc, delayInstr, regs, hi, lo)
          hi = delayRes.hi
          lo = delayRes.lo
          regs[0] = 0L
          cycles++
        }
        pc = stepOut.nextTarget
      } else {
        pc = (pc + 4L) and 0xFFFFFFFFL
      }
      cycles++

      if (stepOut.halted) {
        halted = true
        haltReason = stepOut.biosNote ?: "SYSCALL / BREAK"
      }
    }

    return state.copy(
      pc = pc,
      gprs = regs,
      hi = hi,
      lo = lo,
      cyclesExecuted = cycles,
      biosCalls = biosLog,
      trace = traceLog,
      halted = halted,
      haltReason = haltReason,
    )
  }

  fun disassemble(pc: Long, instr: Int): String {
    if (instr == 0) return "nop"
    val op = (instr ushr 26) and 0x3F
    val rs = (instr ushr 21) and 0x1F
    val rt = (instr ushr 16) and 0x1F
    val rd = (instr ushr 11) and 0x1F
    val shamt = (instr ushr 6) and 0x1F
    val funct = instr and 0x3F
    val imm = instr and 0xFFFF
    val simm = instr.toShort().toInt()
    val target = ((pc + 4L) and 0xF0000000L) or ((instr.toLong() and 0x03FFFFFFL) shl 2)

    return when (op) {
      0 ->
        when (funct) {
          0x00 -> "sll \$${REG_NAMES[rd]}, \$${REG_NAMES[rt]}, $shamt"
          0x02 -> "srl \$${REG_NAMES[rd]}, \$${REG_NAMES[rt]}, $shamt"
          0x03 -> "sra \$${REG_NAMES[rd]}, \$${REG_NAMES[rt]}, $shamt"
          0x08 -> "jr \$${REG_NAMES[rs]}"
          0x09 -> "jalr \$${REG_NAMES[rd]}, \$${REG_NAMES[rs]}"
          0x0C -> "syscall"
          0x0D -> "break"
          0x10 -> "mfhi \$${REG_NAMES[rd]}"
          0x12 -> "mflo \$${REG_NAMES[rd]}"
          0x18 -> "mult \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          0x19 -> "multu \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          0x1A -> "div \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          0x20 -> "add \$${REG_NAMES[rd]}, \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          0x21 -> "addu \$${REG_NAMES[rd]}, \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          0x22 -> "sub \$${REG_NAMES[rd]}, \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          0x23 -> "subu \$${REG_NAMES[rd]}, \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          0x24 -> "and \$${REG_NAMES[rd]}, \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          0x25 -> "or \$${REG_NAMES[rd]}, \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          0x26 -> "xor \$${REG_NAMES[rd]}, \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          0x27 -> "nor \$${REG_NAMES[rd]}, \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          0x2A -> "slt \$${REG_NAMES[rd]}, \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          0x2B -> "sltu \$${REG_NAMES[rd]}, \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}"
          else -> "special 0x${funct.toString(16)}"
        }
      0x01 -> {
        val name = if (rt == 0) "bltz" else if (rt == 1) "bgez" else "regimm"
        "$name \$${REG_NAMES[rs]}, 0x${hex8(pc + 4 + (simm shl 2))}"
      }
      0x02 -> "j 0x${hex8(target)}"
      0x03 -> "jal 0x${hex8(target)}"
      0x04 -> "beq \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}, 0x${hex8(pc + 4 + (simm shl 2))}"
      0x05 -> "bne \$${REG_NAMES[rs]}, \$${REG_NAMES[rt]}, 0x${hex8(pc + 4 + (simm shl 2))}"
      0x06 -> "blez \$${REG_NAMES[rs]}, 0x${hex8(pc + 4 + (simm shl 2))}"
      0x07 -> "bgtz \$${REG_NAMES[rs]}, 0x${hex8(pc + 4 + (simm shl 2))}"
      0x08 -> "addi \$${REG_NAMES[rt]}, \$${REG_NAMES[rs]}, $simm"
      0x09 -> "addiu \$${REG_NAMES[rt]}, \$${REG_NAMES[rs]}, $simm"
      0x0A -> "slti \$${REG_NAMES[rt]}, \$${REG_NAMES[rs]}, $simm"
      0x0B -> "sltiu \$${REG_NAMES[rt]}, \$${REG_NAMES[rs]}, $simm"
      0x0C -> "andi \$${REG_NAMES[rt]}, \$${REG_NAMES[rs]}, 0x${imm.toString(16)}"
      0x0D -> "ori \$${REG_NAMES[rt]}, \$${REG_NAMES[rs]}, 0x${imm.toString(16)}"
      0x0E -> "xori \$${REG_NAMES[rt]}, \$${REG_NAMES[rs]}, 0x${imm.toString(16)}"
      0x0F -> "lui \$${REG_NAMES[rt]}, 0x${imm.toString(16).padStart(4, '0')}"
      0x10 -> if (rs == 0) "mfc0 \$${REG_NAMES[rt]}, c0_$rd" else "mtc0 \$${REG_NAMES[rt]}, c0_$rd"
      0x20 -> "lb \$${REG_NAMES[rt]}, $simm(\$${REG_NAMES[rs]})"
      0x21 -> "lh \$${REG_NAMES[rt]}, $simm(\$${REG_NAMES[rs]})"
      0x23 -> "lw \$${REG_NAMES[rt]}, $simm(\$${REG_NAMES[rs]})"
      0x24 -> "lbu \$${REG_NAMES[rt]}, $simm(\$${REG_NAMES[rs]})"
      0x25 -> "lhu \$${REG_NAMES[rt]}, $simm(\$${REG_NAMES[rs]})"
      0x28 -> "sb \$${REG_NAMES[rt]}, $simm(\$${REG_NAMES[rs]})"
      0x29 -> "sh \$${REG_NAMES[rt]}, $simm(\$${REG_NAMES[rs]})"
      0x2B -> "sw \$${REG_NAMES[rt]}, $simm(\$${REG_NAMES[rs]})"
      else -> "op_0x${op.toString(16)} 0x${hex8(instr.toLong() and 0xFFFFFFFFL)}"
    }
  }

  private data class StepResult(
    val hi: Long,
    val lo: Long,
    val branchTaken: Boolean = false,
    val nextTarget: Long? = null,
    val halted: Boolean = false,
    val biosNote: String? = null,
  )

  private fun executeOne(
    ram: ByteArray,
    pc: Long,
    instr: Int,
    regs: LongArray,
    hiIn: Long,
    loIn: Long,
  ): StepResult {
    var hi = hiIn
    var lo = loIn
    val op = (instr ushr 26) and 0x3F
    val rs = (instr ushr 21) and 0x1F
    val rt = (instr ushr 16) and 0x1F
    val rd = (instr ushr 11) and 0x1F
    val shamt = (instr ushr 6) and 0x1F
    val funct = instr and 0x3F
    val imm = instr and 0xFFFF
    val simm = instr.toShort().toInt()
    val jumpTarget = ((pc + 4L) and 0xF0000000L) or ((instr.toLong() and 0x03FFFFFFL) shl 2)
    val branchTarget = (pc + 4L + (simm.toLong() shl 2)) and 0xFFFFFFFFL

    when (op) {
      0 -> {
        when (funct) {
          0x00 -> regs[rd] = ((regs[rt].toInt() shl shamt).toLong()) and 0xFFFFFFFFL
          0x02 -> regs[rd] = ((regs[rt].toInt() ushr shamt).toLong()) and 0xFFFFFFFFL
          0x03 -> regs[rd] = ((regs[rt].toInt() shr shamt).toLong()) and 0xFFFFFFFFL
          0x08 -> return StepResult(hi, lo, branchTaken = true, nextTarget = regs[rs] and 0xFFFFFFFFL)
          0x09 -> {
            val tgt = regs[rs] and 0xFFFFFFFFL
            regs[rd] = (pc + 8L) and 0xFFFFFFFFL
            return StepResult(hi, lo, branchTaken = true, nextTarget = tgt)
          }
          0x0C -> return StepResult(hi, lo, biosNote = "SYSCALL a0=0x${hex8(regs[4])}")
          0x0D -> return StepResult(hi, lo, halted = true, biosNote = "BREAK @ 0x${hex8(pc)}")
          0x10 -> regs[rd] = hi
          0x12 -> regs[rd] = lo
          0x18 -> {
            val prod = regs[rs].toInt().toLong() * regs[rt].toInt().toLong()
            lo = prod and 0xFFFFFFFFL
            hi = (prod ushr 32) and 0xFFFFFFFFL
          }
          0x19 -> {
            val prod = (regs[rs] and 0xFFFFFFFFL) * (regs[rt] and 0xFFFFFFFFL)
            lo = prod and 0xFFFFFFFFL
            hi = (prod ushr 32) and 0xFFFFFFFFL
          }
          0x1A -> {
            val divisor = regs[rt].toInt()
            if (divisor != 0) {
              val dividend = regs[rs].toInt()
              lo = (dividend / divisor).toLong() and 0xFFFFFFFFL
              hi = (dividend % divisor).toLong() and 0xFFFFFFFFL
            }
          }
          0x20, 0x21 -> regs[rd] = ((regs[rs].toInt() + regs[rt].toInt()).toLong()) and 0xFFFFFFFFL
          0x22, 0x23 -> regs[rd] = ((regs[rs].toInt() - regs[rt].toInt()).toLong()) and 0xFFFFFFFFL
          0x24 -> regs[rd] = (regs[rs] and regs[rt]) and 0xFFFFFFFFL
          0x25 -> regs[rd] = (regs[rs] or regs[rt]) and 0xFFFFFFFFL
          0x26 -> regs[rd] = (regs[rs] xor regs[rt]) and 0xFFFFFFFFL
          0x27 -> regs[rd] = ((regs[rs] or regs[rt]).inv()) and 0xFFFFFFFFL
          0x2A -> regs[rd] = if (regs[rs].toInt() < regs[rt].toInt()) 1L else 0L
          0x2B -> regs[rd] = if ((regs[rs] and 0xFFFFFFFFL) < (regs[rt] and 0xFFFFFFFFL)) 1L else 0L
        }
      }
      0x01 -> {
        val v = regs[rs].toInt()
        val take = if (rt == 0) v < 0 else v >= 0
        if (take) return StepResult(hi, lo, branchTaken = true, nextTarget = branchTarget)
      }
      0x02 -> return StepResult(hi, lo, branchTaken = true, nextTarget = jumpTarget)
      0x03 -> {
        regs[31] = (pc + 8L) and 0xFFFFFFFFL
        return StepResult(hi, lo, branchTaken = true, nextTarget = jumpTarget)
      }
      0x04 -> if (regs[rs] == regs[rt]) return StepResult(hi, lo, branchTaken = true, nextTarget = branchTarget)
      0x05 -> if (regs[rs] != regs[rt]) return StepResult(hi, lo, branchTaken = true, nextTarget = branchTarget)
      0x06 -> if (regs[rs].toInt() <= 0) return StepResult(hi, lo, branchTaken = true, nextTarget = branchTarget)
      0x07 -> if (regs[rs].toInt() > 0) return StepResult(hi, lo, branchTaken = true, nextTarget = branchTarget)
      0x08, 0x09 -> regs[rt] = ((regs[rs].toInt() + simm).toLong()) and 0xFFFFFFFFL
      0x0A -> regs[rt] = if (regs[rs].toInt() < simm) 1L else 0L
      0x0B -> regs[rt] = if ((regs[rs] and 0xFFFFFFFFL) < (simm.toLong() and 0xFFFFFFFFL)) 1L else 0L
      0x0C -> regs[rt] = (regs[rs] and imm.toLong()) and 0xFFFFFFFFL
      0x0D -> regs[rt] = (regs[rs] or imm.toLong()) and 0xFFFFFFFFL
      0x0E -> regs[rt] = (regs[rs] xor imm.toLong()) and 0xFFFFFFFFL
      0x0F -> regs[rt] = (imm.toLong() shl 16) and 0xFFFFFFFFL
      0x20 -> {
        val addr = (regs[rs] + simm) and 0xFFFFFFFFL
        regs[rt] = readMem8(ram, addr).toByte().toLong() and 0xFFFFFFFFL
      }
      0x21 -> {
        val addr = (regs[rs] + simm) and 0xFFFFFFFFL
        regs[rt] = readMem16(ram, addr).toShort().toLong() and 0xFFFFFFFFL
      }
      0x23 -> {
        val addr = (regs[rs] + simm) and 0xFFFFFFFFL
        regs[rt] = readMem32(ram, addr).toLong() and 0xFFFFFFFFL
      }
      0x24 -> {
        val addr = (regs[rs] + simm) and 0xFFFFFFFFL
        regs[rt] = (readMem8(ram, addr) and 0xFF).toLong()
      }
      0x25 -> {
        val addr = (regs[rs] + simm) and 0xFFFFFFFFL
        regs[rt] = (readMem16(ram, addr) and 0xFFFF).toLong()
      }
      0x28 -> {
        val addr = (regs[rs] + simm) and 0xFFFFFFFFL
        writeMem8(ram, addr, regs[rt].toInt())
      }
      0x29 -> {
        val addr = (regs[rs] + simm) and 0xFFFFFFFFL
        writeMem16(ram, addr, regs[rt].toInt())
      }
      0x2B -> {
        val addr = (regs[rs] + simm) and 0xFFFFFFFFL
        writeMem32(ram, addr, regs[rt].toInt())
      }
    }
    return StepResult(hi, lo)
  }

  private fun handleBiosVectorIfMatched(pc: Long, regs: LongArray): String? {
    val masked = pc and 0x1FFFFFFFL
    val fn = regs[9].toInt() and 0xFF // $t1 holds BIOS function index
    return when (masked) {
      0x000000A0L -> {
        regs[2] = 0L // Success return default
        "BIOS A0h fn=0x${fn.toString(16)} (${biosA0Name(fn)})"
      }
      0x000000B0L -> {
        regs[2] = 0L
        "BIOS B0h fn=0x${fn.toString(16)} (${biosB0Name(fn)})"
      }
      0x000000C0L -> {
        regs[2] = 0L
        "BIOS C0h fn=0x${fn.toString(16)} (Kernel)"
      }
      else -> null
    }
  }

  private fun biosA0Name(fn: Int): String =
    when (fn) {
      0x13 -> "SaveState"
      0x25 -> "toupper"
      0x28 -> "bzero"
      0x2A -> "memcpy"
      0x2B -> "memset"
      0x39 -> "InitHeap"
      0x3F -> "printf"
      0x40 -> "SystemErrorUnresolvedException"
      0x44 -> "FlushCache"
      0x70 -> "_bu_init"
      0x72 -> "_96_remove"
      0x96 -> "AddCDROMDevice"
      0x97 -> "AddMemCardDevice"
      0x99 -> "AddDummyTtyDevice"
      else -> "A0_${fn.toString(16)}"
    }

  private fun biosB0Name(fn: Int): String =
    when (fn) {
      0x00 -> "alloc_kernel_memory"
      0x07 -> "DeliverEvent"
      0x08 -> "OpenEvent"
      0x09 -> "CloseEvent"
      0x0C -> "EnableEvent"
      0x12 -> "InitPad"
      0x13 -> "StartPad"
      0x17 -> "ReturnFromException"
      0x18 -> "SetDefaultExitFromException"
      0x3D -> "std_out_putchar"
      0x4A -> "InitCard"
      0x4B -> "StartCard"
      0x5B -> "ChangeClearPad"
      else -> "B0_${fn.toString(16)}"
    }

  fun mapRamOffset(vaddr: Long): Int? {
    val masked = vaddr and 0xFFFFFFFFL
    return when {
      masked in 0x80000000L..0x801FFFFFL -> (masked - 0x80000000L).toInt()
      masked in 0x00000000L..0x001FFFFFL -> masked.toInt()
      masked in 0xA0000000L..0xA01FFFFFL -> (masked - 0xA0000000L).toInt()
      else -> null
    }
  }

  private fun readMem8(ram: ByteArray, vaddr: Long): Int {
    val off = mapRamOffset(vaddr) ?: return 0
    return ram.getOrNull(off)?.toInt()?.and(0xFF) ?: 0
  }

  private fun readMem16(ram: ByteArray, vaddr: Long): Int {
    val off = mapRamOffset(vaddr) ?: return 0
    if (off + 1 >= ram.size) return 0
    return (ram[off].toInt() and 0xFF) or ((ram[off + 1].toInt() and 0xFF) shl 8)
  }

  private fun readMem32(ram: ByteArray, vaddr: Long): Int {
    val off = mapRamOffset(vaddr) ?: return 0
    if (off + 3 >= ram.size) return 0
    return readS32LE(ram, off)
  }

  private fun writeMem8(ram: ByteArray, vaddr: Long, value: Int) {
    val off = mapRamOffset(vaddr) ?: return
    if (off in ram.indices) ram[off] = (value and 0xFF).toByte()
  }

  private fun writeMem16(ram: ByteArray, vaddr: Long, value: Int) {
    val off = mapRamOffset(vaddr) ?: return
    if (off + 1 < ram.size) {
      ram[off] = (value and 0xFF).toByte()
      ram[off + 1] = ((value ushr 8) and 0xFF).toByte()
    }
  }

  private fun writeMem32(ram: ByteArray, vaddr: Long, value: Int) {
    val off = mapRamOffset(vaddr) ?: return
    if (off + 3 < ram.size) {
      ram[off] = (value and 0xFF).toByte()
      ram[off + 1] = ((value ushr 8) and 0xFF).toByte()
      ram[off + 2] = ((value ushr 16) and 0xFF).toByte()
      ram[off + 3] = ((value ushr 24) and 0xFF).toByte()
    }
  }

  private fun readU32LE(buf: ByteArray, offset: Int): Long =
    readS32LE(buf, offset).toLong() and 0xFFFFFFFFL

  private fun readS32LE(buf: ByteArray, offset: Int): Int {
    if (offset < 0 || offset + 4 > buf.size) return 0
    return (buf[offset].toInt() and 0xFF) or
      ((buf[offset + 1].toInt() and 0xFF) shl 8) or
      ((buf[offset + 2].toInt() and 0xFF) shl 16) or
      ((buf[offset + 3].toInt() and 0xFF) shl 24)
  }

  fun hex8(v: Long): String = (v and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')
}
