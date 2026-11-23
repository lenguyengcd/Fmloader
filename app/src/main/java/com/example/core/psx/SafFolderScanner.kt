package com.example.core.psx

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScannedDiscFile(
  val uri: Uri,
  val displayName: String,
  val sizeBytes: Long,
)

object SafFolderScanner {

  private val discExtensions = setOf("bin", "iso", "img", "pbp", "chd", "cue")
  private const val MIN_BINARY_DISC_SIZE = 512L * 1024L // Exclude 128KB .bin memory cards

  fun getFolderDisplayName(contentResolver: ContentResolver, treeUri: Uri): String {
    val docId =
      try {
        DocumentsContract.getTreeDocumentId(treeUri)
      } catch (_: IllegalArgumentException) {
        return treeUri.lastPathSegment ?: "Granted Folder"
      }
    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    contentResolver
      .query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          val name = cursor.getString(0)
          if (!name.isNullOrBlank()) return name
        }
      }
    return docId.substringAfterLast(':').ifBlank { "Internal Storage Folder" }
  }

  suspend fun scanTreeForDiscs(
    contentResolver: ContentResolver,
    treeUri: Uri,
    maxDepth: Int = 3,
  ): Result<List<ScannedDiscFile>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val found = mutableListOf<ScannedDiscFile>()
        scanDirectoryRecursive(
          contentResolver = contentResolver,
          treeUri = treeUri,
          parentDocId = rootDocId,
          depth = 0,
          maxDepth = maxDepth,
          out = found,
        )
        found
      }
    }

  private fun scanDirectoryRecursive(
    contentResolver: ContentResolver,
    treeUri: Uri,
    parentDocId: String,
    depth: Int,
    maxDepth: Int,
    out: MutableList<ScannedDiscFile>,
  ) {
    if (depth > maxDepth || out.size >= 100) return
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    val projection =
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
      )

    runCatching {
      contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

        while (cursor.moveToNext() && out.size < 100) {
          val docId = if (idCol >= 0) cursor.getString(idCol) else continue
          val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
          val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
          val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L

          if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            scanDirectoryRecursive(contentResolver, treeUri, docId, depth + 1, maxDepth, out)
          } else {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in discExtensions) {
              val isValidSize = ext == "cue" || size == 0L || size >= MIN_BINARY_DISC_SIZE
              if (isValidSize) {
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                out.add(ScannedDiscFile(uri = fileUri, displayName = name, sizeBytes = size))
              }
            }
          }
        }
      }
    }
  }
}
