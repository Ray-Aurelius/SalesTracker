package com.salestracker.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every backup goes to one place: Documents › Quota Vault Backups on the phone's own storage. It's the same
 * folder every time, it stays when the app is deleted, and restoring looks there first, so nobody has to
 * remember where a backup went.
 *
 * No storage permission is used:
 * - Android 10 and later let an app save its own files to Documents. The app can then list and delete the
 *   backups it made, but nothing else in the folder or anywhere else.
 * - After the app is reinstalled (or on Android 8 and 9, which have no such shared folder for apps), the
 *   person grants access to that one folder, once, in Android's own folder picker. Nothing else is reachable.
 *
 * Backups are encrypted with the person's password before they're written (see [Backup]), so the folder
 * holds nothing readable.
 */
object BackupFolder {
    const val FOLDER = "Quota Vault Backups"
    const val EXTENSION = ".stbackup"
    /** How the folder is shown in the app. Folder names on the phone are the same in every language. */
    val DISPLAY_PATH = "${Environment.DIRECTORY_DOCUMENTS} › $FOLDER"
    /** A short note in the folder, so anyone browsing their files knows what it is. */
    const val README = "About this folder.txt"
    private const val MIME = "application/octet-stream"
    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
    private val RELATIVE_PATH = "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER/"
    /** Kept apart from the app's settings so "Erase all data" doesn't forget the folder was already made. */
    private const val PREFS = "backup_folder"
    private const val MAX_BYTES = 64L * 1024 * 1024

    class NoFolderException : Exception("No backup folder chosen yet")

    /** One backup file. [modified] is in milliseconds. */
    data class Entry(val uri: Uri, val name: String, val modified: Long, val size: Long)

    /** Android 10+: backups can be saved to the folder with no picker and no permission. */
    val automatic: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun fileName(now: LocalDateTime = LocalDateTime.now()): String =
        "QuotaVault-backup-" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss", Locale.ROOT)) + EXTENSION

    fun isBackupName(name: String) = name.endsWith(EXTENSION, ignoreCase = true)

    /** Where Android's folder picker should open: the backup folder itself, if it's there. */
    fun pickerStart(): Uri =
        DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE, "primary:${Environment.DIRECTORY_DOCUMENTS}/$FOLDER")

    /** The folder the person allowed in Android's folder picker, if any (and if the permission still holds). */
    fun grantedFolder(context: Context): Uri? =
        context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && it.isWritePermission && DocumentsContract.isTreeUri(it.uri) }?.uri

    /** True when a backup can be saved without asking anything first. */
    fun ready(context: Context) = automatic || grantedFolder(context) != null

    /** Keeps the folder chosen in Android's picker. Only one folder is ever kept. */
    fun keep(context: Context, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val resolver = context.contentResolver
        resolver.persistedUriPermissions
            .filter { DocumentsContract.isTreeUri(it.uri) && it.uri != tree }
            .forEach { runCatching { resolver.releasePersistableUriPermission(it.uri, flags) } }
        resolver.takePersistableUriPermission(tree, flags)
    }

    /** Every backup the app can see, newest first. */
    fun list(context: Context): List<Entry> {
        val found = LinkedHashMap<String, Entry>()
        grantedFolder(context)?.let { tree -> runCatching { treeFiles(context, tree) }.getOrNull()?.forEach { found.putIfAbsent(it.name, it) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching { ownFiles(context) }.getOrNull()?.forEach { found.putIfAbsent(it.name, it) }
        return found.values.filter { isBackupName(it.name) }.sortedByDescending { it.modified }
    }

    /** Saves a finished (already encrypted) backup in the folder. */
    fun write(context: Context, name: String, bytes: ByteArray): Uri {
        val tree = grantedFolder(context)
        if (tree != null) return writeToTree(context, tree, name, MIME, bytes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return writeOwn(context, name, MIME, bytes)
        throw NoFolderException()
    }

    fun read(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) error("too large to be a backup")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: error("can't open")

    fun delete(context: Context, entry: Entry): Boolean = runCatching {
        if (DocumentsContract.isDocumentUri(context, entry.uri)) DocumentsContract.deleteDocument(context.contentResolver, entry.uri)
        else context.contentResolver.delete(entry.uri, null, null) > 0
    }.getOrDefault(false)

    /** Deletes every backup the app can see. Returns how many were deleted. */
    fun deleteAll(context: Context): Int = list(context).count { delete(context, it) }

    /**
     * Makes the folder the first time the app is set up (Android 10+), with a short note inside so it's
     * there and recognizable before the first backup. Does nothing if it was made before.
     */
    fun create(context: Context, readme: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("created", false)) return
        runCatching {
            // Already there and visible to the app (made earlier, or the folder was allowed while restoring)?
            val seen = (grantedFolder(context)?.let { treeFiles(context, it, includeAll = true) }.orEmpty() + ownFiles(context, includeAll = true))
            if (seen.none { it.name == README }) writeOwn(context, README, "text/plain", readme.toByteArray(Charsets.UTF_8))
        }
        prefs.edit().putBoolean("created", true).apply()
    }

    // ---- Android 10+: the app's own files in Documents/Quota Vault Backups ----

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun collection() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ownFiles(context: Context, includeAll: Boolean = false): List<Entry> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.SIZE,
        )
        val out = ArrayList<Entry>()
        context.contentResolver.query(
            collection(), projection, "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(RELATIVE_PATH), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (!includeAll && !isBackupName(name)) continue
                out += Entry(ContentUris.withAppendedId(collection(), c.getLong(0)), name, c.getLong(2) * 1000, c.getLong(3))
            }
        }
        return out
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeOwn(context: Context, name: String, mime: String, bytes: ByteArray): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection(), values) ?: error("couldn't create the file")
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("couldn't write the file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    // ---- A folder allowed in Android's folder picker ----

    private class Doc(val id: String, val name: String, val mime: String, val modified: Long, val size: Long)

    private fun children(context: Context, tree: Uri, parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE,
        )
        val out = ArrayList<Doc>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += Doc(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getLong(3), c.getLong(4))
            }
        }
        return out
    }

    private fun displayName(context: Context, tree: Uri, docId: String): String? =
        context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(tree, docId), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /**
     * The backups folder inside the allowed folder: the allowed folder itself if it *is* "Quota Vault Backups",
     * otherwise a "Quota Vault Backups" folder inside it (made when [create] is true).
     */
    private fun backupsDirId(context: Context, tree: Uri, create: Boolean): String? {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        if (displayName(context, tree, rootId) == FOLDER) return rootId
        children(context, tree, rootId).firstOrNull { it.mime == Document.MIME_TYPE_DIR && it.name == FOLDER }?.let { return it.id }
        if (!create) return null
        val made = DocumentsContract.createDocument(
            context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, rootId), Document.MIME_TYPE_DIR, FOLDER,
        ) ?: return null
        return DocumentsContract.getDocumentId(made)
    }

    private fun treeFiles(context: Context, tree: Uri, includeAll: Boolean = false): List<Entry> {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        // Backups in the allowed folder itself, and in a "Quota Vault Backups" folder inside it.
        val dirs = listOfNotNull(rootId, backupsDirId(context, tree, create = false)).distinct()
        return dirs.flatMap { dir ->
            children(context, tree, dir)
                .filter { it.mime != Document.MIME_TYPE_DIR && (includeAll || isBackupName(it.name)) }
                .map { Entry(DocumentsContract.buildDocumentUriUsingTree(tree, it.id), it.name, it.modified, it.size) }
        }
    }

    private fun writeToTree(context: Context, tree: Uri, name: String, mime: String, bytes: ByteArray): Uri {
        val resolver = context.contentResolver
        val dirId = backupsDirId(context, tree, create = true) ?: error("couldn't make the backup folder")
        val doc = DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, dirId), mime, name)
            ?: error("couldn't create the file")
        try {
            resolver.openOutputStream(doc, "w")?.use { it.write(bytes) } ?: error("couldn't write the file")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw e
        }
        return doc
    }
}
