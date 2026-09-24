package tv.hydr0.store

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

// Answers "is this app installed, and is it out of date?"
object Packages {

    const val NOT_INSTALLED = 0
    const val INSTALLED = 1
    const val UPDATE_AVAILABLE = 2

    class Installed(val versionName: String, val versionCode: Long)

    fun lookup(context: Context, packageName: String): Installed? {
        if (packageName.isEmpty()) {
            return null
        }
        try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            var code: Long
            if (Build.VERSION.SDK_INT >= 28) {
                code = info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                code = info.versionCode.toLong()
            }
            return Installed(info.versionName ?: "", code)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
    }

    fun state(context: Context, app: StoreApp): Int {
        val installed = lookup(context, app.packageName)
        if (installed == null) {
            return NOT_INSTALLED
        }
        // Only direct/GitHub downloads can be updated from here. A catalog
        // version_code bigger than the installed one means "update available".
        val downloadable = app.source.type == "apk" || app.source.type == "github"
        if (downloadable && app.versionCode > 0 && app.versionCode > installed.versionCode) {
            return UPDATE_AVAILABLE
        }
        return INSTALLED
    }

    fun isAmazonDevice(): Boolean {
        return Build.MANUFACTURER.equals("Amazon", ignoreCase = true)
    }
}
