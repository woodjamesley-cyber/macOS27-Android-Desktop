package io.github.goldengate.desktop.fs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** A location in either the private home folder (java.io.File) or a user-granted SAF tree. */
sealed class FsRef {
    abstract val name: String
    abstract val key: String

    data class Local(val file: File) : FsRef() {
        override val name: String get() = file.name
        override val key: String get() = "file:" + file.absolutePath
    }

    data class Saf(val uri: Uri, val treeUri: Uri, val displayName: String, val isDirHint: Boolean) : FsRef() {
        override val name: String get() = displayName
        override val key: String get() = "saf|$treeUri|$uri|$isDirHint|$displayName"
    }
}

data class FsEntry(
    val ref: FsRef,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val mime: String,
) {
    val kind: FileKind get() = FileKind.of(name, isDirectory, mime)
}

enum class FileKind(val label: String) {
    Folder("Folder"), Text("Plain Text"), Markdown("Markdown"), Image("Image"), Audio("Audio"),
    Video("Video"), Pdf("PDF Document"), Web("Web Page"), Csv("Spreadsheet (CSV)"), Archive("Archive"), Other("Document");

    companion object {
        fun of(name: String, dir: Boolean, mime: String): FileKind {
            if (dir) return Folder
            val ext = name.substringAfterLast('.', "").lowercase()
            return when {
                ext in setOf("txt", "log", "json", "kt", "java", "xml", "sh", "py", "js", "css", "ini", "conf") -> Text
                ext == "md" -> Markdown
                ext == "csv" -> Csv
                ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic") || mime.startsWith("image/") -> Image
                ext in setOf("wav", "mp3", "m4a", "ogg", "flac", "aac") || mime.startsWith("audio/") -> Audio
                ext in setOf("mp4", "mkv", "webm", "mov") || mime.startsWith("video/") -> Video
                ext == "pdf" -> Pdf
                ext in setOf("html", "htm", "url") -> Web
                ext in setOf("zip", "tar", "gz", "7z") -> Archive
                mime.startsWith("text/") -> Text
                else -> Other
            }
        }
    }
}

/**
 * File operations over both backends. The private home folder lives in the app's internal
 * storage (no permission needed). External folders are only reachable after the user grants
 * them through the system folder picker (Storage Access Framework); persisted grants are listed
 * in Settings > Privacy & Security and can be revoked there.
 */
class FileSystem(private val context: Context) {
    val home: File = File(context.filesDir, "home").apply { mkdirs() }
    val trash: File = File(home, ".Trash").apply { mkdirs() }

    fun homeDir(name: String) = File(home, name).apply { mkdirs() }
    val desktop get() = homeDir("Desktop")
    val documents get() = homeDir("Documents")
    val downloads get() = homeDir("Downloads")
    val pictures get() = homeDir("Pictures")
    val music get() = homeDir("Music")

    // ---------- listing ----------
    fun list(dir: FsRef, showHidden: Boolean = false): List<FsEntry> = when (dir) {
        is FsRef.Local -> (dir.file.listFiles() ?: emptyArray())
            .filter { showHidden || !it.name.startsWith(".") }
            .map { localEntry(it) }
        is FsRef.Saf -> safList(dir)
    }

    fun localEntry(f: File) = FsEntry(FsRef.Local(f), f.name, f.isDirectory, if (f.isDirectory) 0 else f.length(), f.lastModified(), mimeOf(f.name))

    private fun safList(dir: FsRef.Saf): List<FsEntry> {
        val out = ArrayList<FsEntry>()
        val docId = DocumentsContract.getDocumentId(dir.uri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(dir.treeUri, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        try {
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: "?"
                    val mime = c.getString(2) ?: ""
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    val mod = if (c.isNull(4)) 0L else c.getLong(4)
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val uri = DocumentsContract.buildDocumentUriUsingTree(dir.treeUri, id)
                    out += FsEntry(FsRef.Saf(uri, dir.treeUri, name, isDir), name, isDir, size, mod, mime)
                }
            }
        } catch (t: Throwable) {
            throw FsException("Can't read “${dir.name}”. Access may have been revoked.", t)
        }
        return out
    }

    fun treeRoot(treeUri: Uri): FsRef.Saf {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val name = DocumentFile.fromTreeUri(context, treeUri)?.name ?: "Folder"
        return FsRef.Saf(uri, treeUri, name, true)
    }

    fun grantedTrees(): List<Uri> = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }

    fun persistGrant(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { context.contentResolver.takePersistableUriPermission(uri, flags) } catch (_: SecurityException) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun revokeGrant(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Throwable) { }
    }

    // ---------- mutations ----------
    fun createFolder(parent: FsRef, name: String): FsRef = when (parent) {
        is FsRef.Local -> {
            val f = uniqueFile(parent.file, name)
            if (!f.mkdirs()) throw FsException("Couldn't create folder “$name”.")
            FsRef.Local(f)
        }
        is FsRef.Saf -> {
            val uri = DocumentsContract.createDocument(context.contentResolver, parent.uri, DocumentsContract.Document.MIME_TYPE_DIR, name)
                ?: throw FsException("Couldn't create folder “$name”.")
            FsRef.Saf(uri, parent.treeUri, name, true)
        }
    }

    fun createFile(parent: FsRef, name: String, text: String = ""): FsRef = when (parent) {
        is FsRef.Local -> {
            val f = uniqueFile(parent.file, name)
            f.writeText(text)
            FsRef.Local(f)
        }
        is FsRef.Saf -> {
            val uri = DocumentsContract.createDocument(context.contentResolver, parent.uri, mimeOf(name).ifBlank { "text/plain" }, name)
                ?: throw FsException("Couldn't create “$name”.")
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            FsRef.Saf(uri, parent.treeUri, name, false)
        }
    }

    fun rename(ref: FsRef, newName: String): FsRef {
        require(newName.isNotBlank() && !newName.contains('/')) { "Invalid name" }
        return when (ref) {
            is FsRef.Local -> {
                val target = File(ref.file.parentFile, newName)
                if (target.exists()) throw FsException("The name “$newName” is already taken.")
                if (!ref.file.renameTo(target)) throw FsException("Couldn't rename “${ref.name}”.")
                FsRef.Local(target)
            }
            is FsRef.Saf -> {
                val uri = DocumentsContract.renameDocument(context.contentResolver, ref.uri, newName) ?: ref.uri
                ref.copy(uri = uri, displayName = newName)
            }
        }
    }

    /** Local items go to the Trash; SAF items are deleted permanently (SAF has no trash). */
    fun moveToTrash(ref: FsRef) {
        when (ref) {
            is FsRef.Local -> {
                if (ref.file.parentFile == trash) { ref.file.deleteRecursively(); return }
                val target = uniqueFile(trash, ref.file.name)
                if (!ref.file.renameTo(target)) {
                    ref.file.copyRecursively(target, overwrite = true)
                    ref.file.deleteRecursively()
                }
                File(trash, ".origin_" + target.name).writeText(ref.file.parentFile?.absolutePath ?: home.absolutePath)
            }
            is FsRef.Saf -> if (!DocumentsContract.deleteDocument(context.contentResolver, ref.uri)) throw FsException("Couldn't delete “${ref.name}”.")
        }
    }

    fun putBack(file: File) {
        val marker = File(trash, ".origin_" + file.name)
        val dir = if (marker.exists()) File(marker.readText()) else desktop
        dir.mkdirs()
        file.renameTo(uniqueFile(dir, file.name))
        marker.delete()
    }

    fun emptyTrash() {
        trash.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun trashCount(): Int = trash.listFiles()?.count { !it.name.startsWith(".") } ?: 0

    fun copy(src: FsRef, destDir: FsRef): FsRef = when {
        src is FsRef.Local && destDir is FsRef.Local -> {
            val target = uniqueFile(destDir.file, if (src.file.parentFile == destDir.file) copyName(src.file.name) else src.file.name)
            src.file.copyRecursively(target)
            FsRef.Local(target)
        }
        else -> streamCopy(src, destDir)
    }

    fun move(src: FsRef, destDir: FsRef): FsRef = when {
        src is FsRef.Local && destDir is FsRef.Local -> {
            if (destDir.file.absolutePath.startsWith(src.file.absolutePath + File.separator) || destDir.file == src.file)
                throw FsException("Can't move a folder into itself.")
            if (src.file.parentFile == destDir.file) src
            else {
                val target = uniqueFile(destDir.file, src.file.name)
                if (!src.file.renameTo(target)) { src.file.copyRecursively(target); src.file.deleteRecursively() }
                FsRef.Local(target)
            }
        }
        else -> {
            val out = streamCopy(src, destDir)
            when (src) {
                is FsRef.Local -> src.file.deleteRecursively()
                is FsRef.Saf -> DocumentsContract.deleteDocument(context.contentResolver, src.uri)
            }
            out
        }
    }

    private fun streamCopy(src: FsRef, destDir: FsRef): FsRef {
        val isDir = when (src) { is FsRef.Local -> src.file.isDirectory; is FsRef.Saf -> src.isDirHint }
        if (isDir) {
            val folder = createFolder(destDir, src.name)
            for (child in list(src, showHidden = true)) streamCopy(child.ref, folder)
            return folder
        }
        val target = when (destDir) {
            is FsRef.Local -> FsRef.Local(uniqueFile(destDir.file, src.name))
            is FsRef.Saf -> {
                val uri = DocumentsContract.createDocument(context.contentResolver, destDir.uri, mimeOf(src.name).ifBlank { "application/octet-stream" }, src.name)
                    ?: throw FsException("Couldn't copy “${src.name}”.")
                FsRef.Saf(uri, destDir.treeUri, src.name, false)
            }
        }
        openInput(src).use { input -> openOutput(target).use { input.copyTo(it) } }
        return target
    }

    fun openInput(ref: FsRef): InputStream = when (ref) {
        is FsRef.Local -> ref.file.inputStream()
        is FsRef.Saf -> context.contentResolver.openInputStream(ref.uri) ?: throw FsException("Can't open “${ref.name}”.")
    }

    fun openOutput(ref: FsRef): OutputStream = when (ref) {
        is FsRef.Local -> ref.file.outputStream()
        is FsRef.Saf -> context.contentResolver.openOutputStream(ref.uri, "wt") ?: throw FsException("Can't write “${ref.name}”.")
    }

    fun readText(ref: FsRef, limit: Int = 2_000_000): String = openInput(ref).use { s ->
        val bytes = s.readBytes()
        String(bytes, 0, minOf(bytes.size, limit))
    }

    fun writeText(ref: FsRef, text: String) { openOutput(ref).use { it.write(text.toByteArray()) } }

    fun parentOf(ref: FsRef): FsRef? = when (ref) {
        is FsRef.Local -> ref.file.parentFile?.takeIf { it.absolutePath.startsWith(home.absolutePath) }?.let { FsRef.Local(it) }
        is FsRef.Saf -> null
    }

    fun displayPath(ref: FsRef): String = when (ref) {
        is FsRef.Local -> "~" + ref.file.absolutePath.removePrefix(home.absolutePath).ifEmpty { "" }
        is FsRef.Saf -> ref.displayName
    }

    fun freeSpace(): Long = context.filesDir.usableSpace
    fun totalSpace(): Long = context.filesDir.totalSpace

    fun search(query: String, limit: Int = 30): List<FsEntry> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase()
        return home.walkTopDown().maxDepth(6)
            .filter { it != home && !it.name.startsWith(".") && !it.absolutePath.contains("/.Trash") && it.name.lowercase().contains(q) }
            .take(limit).map { localEntry(it) }.toList()
    }

    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.') && !name.startsWith('.')) "." + name.substringAfterLast('.') else ""
        var i = 2
        while (f.exists()) { f = File(dir, "$base $i$ext"); i++ }
        return f
    }

    private fun copyName(name: String): String {
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        return "$base copy$ext"
    }

    companion object {
        fun mimeOf(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
                "md" -> "text/markdown"; "txt", "log" -> "text/plain"; "csv" -> "text/csv"; else -> ""
            }
        }

        fun formatSize(bytes: Long): String = when {
            bytes < 1000 -> "$bytes bytes"
            bytes < 1_000_000 -> String.format("%.1f KB", bytes / 1000.0)
            bytes < 1_000_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            else -> String.format("%.2f GB", bytes / 1_000_000_000.0)
        }
    }
}

class FsException(message: String, cause: Throwable? = null) : Exception(message, cause)
