package com.nezzar.nfcattendance.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

/**
 * Writes an exported workbook into the phone's shared Documents folder, so the
 * teacher - or anyone handed the file - can open it in Excel, Sheets or any
 * file manager. No storage permission is involved on API 29+: MediaStore owns
 * the write. On API 24-28 MediaStore has no RELATIVE_PATH, and the caller uses
 * the system folder picker (CreateDocument) instead.
 */
object DocumentsExport {

    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val FOLDER = "NFC Attendance"

    /** What was written, so the UI can show the path and offer a Share button. */
    data class Written(val uri: Uri, val displayPath: String)

    /** The readable path shown on screen, e.g. /storage/emulated/0/Documents/NFC Attendance/x.xlsx */
    fun displayPath(fileName: String): String {
        val root = Environment.getExternalStorageDirectory().absolutePath
        return root + "/" + Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER + "/" + fileName
    }

    /** True when this device can write straight to Documents without a picker. */
    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private fun folder(): String = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER + "/"

    /**
     * MediaStore write into Documents/NFC Attendance. Earlier copies of the same
     * export - including the "(1)" names MediaStore invents when an old copy is
     * still on disk - are removed first, so the folder keeps exactly one file per
     * export instead of stacking duplicates. Throws when the write fails; the
     * caller then falls back to the private reports folder.
     *
     * The name MediaStore actually assigned is read back, because a copy made by
     * an earlier install of the app cannot always be deleted by this one.
     */
    @Throws(Throwable::class)
    fun save(context: Context, fileName: String, write: (OutputStream) -> Unit): Written {
        check(supported()) { "MediaStore RELATIVE_PATH needs Android 10 (API 29) or newer." }
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        deleteEarlierExports(resolver, collection, fileName)

        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_XLSX)
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER)
        values.put(MediaStore.MediaColumns.IS_PENDING, 1)

        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("The Documents folder refused the new file.")
        try {
            val stream = resolver.openOutputStream(uri, "w")
                ?: throw IllegalStateException("The new file could not be opened for writing.")
            stream.use { out -> write(out) }
            val done = ContentValues()
            done.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, done, null, null)
        } catch (t: Throwable) {
            // Never leave a half-written pending file behind.
            try {
                resolver.delete(uri, null, null)
            } catch (ignored: Throwable) {
                // the original failure is the one worth reporting
            }
            throw t
        }
        val actual = displayNameOf(resolver, uri) ?: fileName
        return Written(uri, displayPath(actual))
    }

    /**
     * Deletes the rows in Documents/NFC Attendance whose name is this export's
     * name or the "(n)" variant of it. Listing and deleting row by row keeps a
     * row this install does not own from aborting the whole sweep.
     */
    private fun deleteEarlierExports(resolver: ContentResolver, collection: Uri, fileName: String) {
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        val suffix = if (dot > 0) fileName.substring(dot) else ""
        val doomed = mutableListOf<Long>()
        try {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                arrayOf(folder()),
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "" else ""
                    // This export's own name, or the "name (1).xlsx" copies MediaStore invents.
                    val sameExport = name == fileName ||
                        (suffix.isNotEmpty() && name.startsWith(stem + " (") && name.endsWith(suffix))
                    if (sameExport && idIndex >= 0) doomed += cursor.getLong(idIndex)
                }
            }
        } catch (ignored: Throwable) {
            // A query failure must not stop the export itself.
        }
        for (id in doomed) {
            try {
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            } catch (ignored: Throwable) {
                // A copy owned by an earlier install may refuse; the read-back name covers it.
            }
        }
    }

    private fun displayNameOf(resolver: ContentResolver, uri: Uri): String? = try {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (ignored: Throwable) {
        null
    }
}
