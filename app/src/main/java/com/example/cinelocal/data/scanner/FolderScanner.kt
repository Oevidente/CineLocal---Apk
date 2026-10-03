package com.example.cinelocal.data.scanner

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScannedVideoFile(
    val uri: Uri,
    val documentId: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val lastModified: Long,
    val parentFolder: String?
)

object FolderScanner {

    private const val TAG = "FolderScanner"
    private const val MAX_DEPTH = 8
    private const val MIN_SAMPLE_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB

    private val VIDEO_EXTENSIONS = setOf(
        "mkv", "mp4", "m4v", "avi", "mov", "wmv", "flv", "webm",
        "ts", "m2ts", "mpg", "mpeg", "3gp", "rmvb", "vob"
    )

    suspend fun scanTree(
        contentResolver: ContentResolver,
        treeUri: Uri,
        onProgress: ((count: Int, currentFile: String) -> Unit)? = null
    ): List<ScannedVideoFile> = withContext(Dispatchers.IO) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val results = mutableListOf<ScannedVideoFile>()
        val visitedDocIds = mutableSetOf<String>()

        scanDirectory(
            contentResolver = contentResolver,
            treeUri = treeUri,
            parentDocId = rootDocId,
            parentFolderName = null,
            depth = 0,
            visitedDocIds = visitedDocIds,
            results = results,
            onProgress = onProgress
        )

        results
    }

    private fun scanDirectory(
        contentResolver: ContentResolver,
        treeUri: Uri,
        parentDocId: String,
        parentFolderName: String?,
        depth: Int,
        visitedDocIds: MutableSet<String>,
        results: MutableList<ScannedVideoFile>,
        onProgress: ((count: Int, currentFile: String) -> Unit)?
    ) {
        if (depth > MAX_DEPTH || !visitedDocIds.add(parentDocId)) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        try {
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val dateIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx) ?: continue
                    val displayName = cursor.getString(nameIdx) ?: "arquivo"
                    val mimeType = cursor.getString(mimeIdx)
                    val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else 0L
                    val lastModified = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L

                    // Ignorar arquivos/pastas ocultos ou lixeiras
                    if (displayName.startsWith(".") ||
                        displayName.startsWith("@eaDir") ||
                        displayName.startsWith(".Trash") ||
                        displayName.equals("lost+found", ignoreCase = true)
                    ) {
                        continue
                    }

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        // Recursão no subdiretório
                        scanDirectory(
                            contentResolver = contentResolver,
                            treeUri = treeUri,
                            parentDocId = docId,
                            parentFolderName = displayName,
                            depth = depth + 1,
                            visitedDocIds = visitedDocIds,
                            results = results,
                            onProgress = onProgress
                        )
                    } else if (isVideoFile(displayName, mimeType)) {
                        // Ignorar samples/trailers pequenos (< 50 MB)
                        val lowerName = displayName.lowercase()
                        if ((lowerName.contains("sample") || lowerName.contains("trailer")) && size > 0 && size < MIN_SAMPLE_SIZE_BYTES) {
                            continue
                        }

                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val scanned = ScannedVideoFile(
                            uri = fileUri,
                            documentId = docId,
                            displayName = displayName,
                            mimeType = mimeType,
                            sizeBytes = size,
                            lastModified = lastModified,
                            parentFolder = parentFolderName
                        )
                        results.add(scanned)
                        onProgress?.invoke(results.size, displayName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao escanear diretório $parentDocId", e)
        }
    }

    fun isVideoFile(fileName: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("video/", ignoreCase = true) == true) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }
}
