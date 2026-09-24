package tv.hydr0.store

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

// A tiny file provider: it shares downloaded APKs with the system installer.
// (Replaces androidx FileProvider so the app needs no extra libraries.)
class ApkProvider : ContentProvider() {

    companion object {
        const val FOLDER = "apks"

        fun uriFor(authority: String, file: File): Uri {
            return Uri.parse("content://$authority/${file.name}")
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun getType(uri: Uri): String {
        return "application/vnd.android.package-archive"
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("No context")
        val name = uri.lastPathSegment ?: throw FileNotFoundException("No file name")

        // Only plain file names inside our apks folder are allowed.
        if (name.contains("/") || name.contains("..") || !name.endsWith(".apk")) {
            throw FileNotFoundException("Not allowed: $name")
        }
        val file = File(File(ctx.cacheDir, FOLDER), name)
        if (!file.exists()) {
            throw FileNotFoundException("Missing: $name")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    // Some installers ask for the file size and name through query().
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val name = uri.lastPathSegment ?: return null
        val file = File(File(ctx.cacheDir, FOLDER), name)
        val cursor = android.database.MatrixCursor(arrayOf("_display_name", "_size"))
        cursor.addRow(arrayOf<Any>(name, file.length()))
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }
}
