package com.example.core.psx

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PpfPatchMetadata(
  val fileName: String,
  val uriString: String,
  val version: Int,
  val magic: String,
  val description: String,
  val imageType: Int,
  val hasBlockCheck: Boolean,
  val hasUndoData: Boolean,
  val chunkCount: Int,
  val totalPatchedBytes: Long,
)

data class PpfApplyResult(
  val outputFileName: String,
  val chunksApplied: Int,
  val bytesPatched: Long,
  val outputTotalBytes: Long,
)

private data class PpfChunk(
  val offset: Long,
  val data: ByteArray,
)

/**
 * Pure Kotlin PlayStation Patch Format (PPF v1.0, v2.0, v3.0) parser and streaming patcher.
 */
object PpfPatchEngine {

  suspend fun inspectPpf(contentResolver: ContentResolver, uri: Uri): Result<PpfPatchMetadata> =
    withContext(Dispatchers.IO) {
      runCatching {
        val fileName = queryName(contentResolver, uri)
        contentResolver.openInputStream(uri)?.use { stream ->
          val bis = BufferedInputStream(stream, 65536)
          val header = ByteArray(60)
          val read = readFully(bis, header)
          require(read >= 56) { "File is too small to be a valid .ppf patch." }

          val magic = String(header, 0, 5, Charsets.US_ASCII)
          val encodingMethod = header[5].toInt() and 0xFF
          val description =
            String(header, 6, 50, Charsets.US_ASCII).replace('\u0000', ' ').trim()

          val version =
            when (magic) {
              "PPF10" -> 1
              "PPF20" -> 2
              "PPF30" -> 3
              else -> error("Invalid PPF magic header: \"$magic\". Expected PPF10, PPF20, or PPF30.")
            }

          var imageType = 0
          var blockCheck = false
          var undoAvailable = false

          when (version) {
            1 -> {
              // Data starts immediately at byte 56; rewind stream via reopen for clean chunk count
            }
            2 -> {
              require(read >= 60) { "Truncated PPF20 header." }
              imageType = 0
              blockCheck = true
            }
            3 -> {
              require(read >= 60) { "Truncated PPF30 header." }
              imageType = header[56].toInt() and 0xFF
              blockCheck = (header[57].toInt() and 0xFF) != 0
              undoAvailable = (header[58].toInt() and 0xFF) != 0
            }
          }

          val parsed = parseChunksFromUri(contentResolver, uri, version, blockCheck, undoAvailable)
          PpfPatchMetadata(
            fileName = fileName,
            uriString = uri.toString(),
            version = version,
            magic = "$magic (enc=$encodingMethod)",
            description = description.ifBlank { "No patch description provided" },
            imageType = imageType,
            hasBlockCheck = blockCheck,
            hasUndoData = undoAvailable,
            chunkCount = parsed.size,
            totalPatchedBytes = parsed.sumOf { it.data.size.toLong() },
          )
        } ?: error("Could not open .ppf file.")
      }
    }

  suspend fun applyPpfPatch(
    contentResolver: ContentResolver,
    baseUri: Uri,
    ppfUri: Uri,
    outputUri: Uri,
    meta: PpfPatchMetadata,
    onProgress: (Float) -> Unit,
  ): Result<PpfApplyResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val chunks =
          parseChunksFromUri(
              contentResolver,
              ppfUri,
              meta.version,
              meta.hasBlockCheck,
              meta.hasUndoData,
            )
            .sortedBy { it.offset }

        val baseSize =
          contentResolver.openFileDescriptor(baseUri, "r")?.use { it.statSize }.let {
            if (it == null || it <= 0L) 1L else it
          }

        val chunkMap = chunks.groupBy { it.offset }
        val buffer = ByteArray(1024 * 1024)
        var cursor = 0L
        var chunkIdx = 0

        contentResolver.openInputStream(baseUri)?.use { rawIn ->
          BufferedInputStream(rawIn, 1024 * 1024).use { input ->
            contentResolver.openOutputStream(outputUri, "wt")?.use { rawOut ->
              BufferedOutputStream(rawOut, 1024 * 1024).use { output ->
                while (true) {
                  val read = input.read(buffer)
                  if (read <= 0) break

                  val blockStart = cursor
                  val blockEnd = cursor + read

                  // Overlay any PPF chunks overlapping [blockStart, blockEnd)
                  while (chunkIdx < chunks.size && chunks[chunkIdx].offset < blockEnd) {
                    val c = chunks[chunkIdx]
                    val cEnd = c.offset + c.data.size
                    if (cEnd > blockStart) {
                      val overlapStart = maxOf(blockStart, c.offset)
                      val overlapEnd = minOf(blockEnd, cEnd)
                      val dstStart = (overlapStart - blockStart).toInt()
                      val srcStart = (overlapStart - c.offset).toInt()
                      val len = (overlapEnd - overlapStart).toInt()
                      System.arraycopy(c.data, srcStart, buffer, dstStart, len)
                    }
                    if (cEnd <= blockEnd) {
                      chunkIdx++
                    } else {
                      break
                    }
                  }

                  output.write(buffer, 0, read)
                  cursor = blockEnd
                  onProgress((cursor.toFloat() / baseSize.toFloat()).coerceIn(0f, 0.99f))
                }
                output.flush()
              }
            } ?: error("Failed to open output stream for patched disc.")
          }
        } ?: error("Failed to open base disc stream.")

        onProgress(1f)
        PpfApplyResult(
          outputFileName = queryName(contentResolver, outputUri),
          chunksApplied = chunkMap.values.sumOf { it.size },
          bytesPatched = chunks.sumOf { it.data.size.toLong() },
          outputTotalBytes = cursor,
        )
      }
    }

  private fun parseChunksFromUri(
    contentResolver: ContentResolver,
    uri: Uri,
    version: Int,
    blockCheck: Boolean,
    undoAvailable: Boolean,
  ): List<PpfChunk> {
    val list = mutableListOf<PpfChunk>()
    contentResolver.openInputStream(uri)?.use { stream ->
      val dis = DataInputStream(BufferedInputStream(stream, 65536))
      val headerSkip =
        when (version) {
          1 -> 56
          2 -> 56 + 4 + if (blockCheck) 1024 else 0
          3 -> 60 + if (blockCheck) 1024 else 0
          else -> 56
        }
      skipExactly(dis, headerSkip.toLong())

      val offsetBuf = ByteArray(8)
      while (true) {
        val offsetBytes = if (version == 3) 8 else 4
        val first = dis.read()
        if (first == -1) break
        offsetBuf[0] = first.toByte()
        if (readFully(dis, offsetBuf, 1, offsetBytes - 1) < offsetBytes - 1) break

        // PPF2/PPF3 may end with "@END_FILE_ID.DIZ" footer
        if (
          offsetBuf[0] == '@'.code.toByte() &&
            offsetBuf[1] == 'E'.code.toByte() &&
            offsetBuf[2] == 'N'.code.toByte() &&
            offsetBuf[3] == 'D'.code.toByte()
        ) {
          break
        }

        val patchOffset =
          if (version == 3) {
            ByteBuffer.wrap(offsetBuf, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
          } else {
            ByteBuffer.wrap(offsetBuf, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
          }

        val lenByte = dis.read()
        if (lenByte == -1) break
        val len = lenByte and 0xFF
        val data = ByteArray(len)
        if (readFully(dis, data, 0, len) < len) break
        if (undoAvailable) {
          skipExactly(dis, len.toLong())
        }
        list.add(PpfChunk(offset = patchOffset, data = data))
      }
    }
    return list
  }

  private fun readFully(stream: java.io.InputStream, buf: ByteArray, off: Int = 0, len: Int = buf.size): Int {
    var total = 0
    while (total < len) {
      val n = stream.read(buf, off + total, len - total)
      if (n <= 0) break
      total += n
    }
    return total
  }

  private fun skipExactly(stream: java.io.InputStream, bytes: Long) {
    var left = bytes
    while (left > 0) {
      val skipped = stream.skip(left)
      if (skipped > 0) {
        left -= skipped
      } else {
        if (stream.read() == -1) break
        left--
      }
    }
  }

  private fun queryName(contentResolver: ContentResolver, uri: Uri): String {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "patch.ppf"
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) {
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) c.getString(idx)?.let { name = it }
      }
    }
    return name
  }
}
