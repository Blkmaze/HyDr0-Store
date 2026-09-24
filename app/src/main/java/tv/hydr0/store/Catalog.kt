package tv.hydr0.store

import org.json.JSONObject

// Where an app gets installed from.
//   "apk"    - direct download link to an .apk file
//   "github" - newest release of a GitHub repo; the asset is picked by a pattern
//   "store"  - open the app's page in the Amazon Appstore or Google Play
//   "web"    - open a web page (for apps that only offer a website)
class AppSource(
    val type: String,
    val url: String,
    val repo: String,
    val assetPattern: String,
    val sha256: String
)

class StoreApp(
    val id: String,
    val name: String,
    val category: String,
    val packageName: String,
    val version: String,
    val versionCode: Long,
    val description: String,
    val iconUrl: String,
    val code: String,
    val source: AppSource
)

class Catalog(
    val categories: List<String>,
    val apps: List<StoreApp>,
    val storeVersionCode: Long,
    val storeApkUrl: String,
    val storeSha256: String
)

object CatalogParser {

    fun parse(text: String): Catalog {
        val root = JSONObject(text)

        // Category order as written in the file.
        val categories = ArrayList<String>()
        val categoryArray = root.optJSONArray("categories")
        if (categoryArray != null) {
            for (i in 0 until categoryArray.length()) {
                categories.add(categoryArray.getString(i))
            }
        }

        // The apps themselves.
        val apps = ArrayList<StoreApp>()
        val appArray = root.optJSONArray("apps")
        if (appArray != null) {
            for (i in 0 until appArray.length()) {
                val item = appArray.getJSONObject(i)
                val sourceJson = item.optJSONObject("source") ?: JSONObject()

                val source = AppSource(
                    type = sourceJson.optString("type", "store"),
                    url = sourceJson.optString("url", ""),
                    repo = sourceJson.optString("repo", ""),
                    assetPattern = sourceJson.optString("asset", "\\.apk$"),
                    sha256 = sourceJson.optString("sha256", "").lowercase()
                )

                val app = StoreApp(
                    id = item.optString("id", "app$i"),
                    name = item.optString("name", "Unnamed app"),
                    category = item.optString("category", "Other"),
                    packageName = item.optString("package", ""),
                    version = item.optString("version", ""),
                    versionCode = item.optLong("version_code", 0L),
                    description = item.optString("description", ""),
                    iconUrl = item.optString("icon", ""),
                    code = item.optString("code", ""),
                    source = source
                )
                apps.add(app)

                // Any category an app uses but the list forgot still shows up.
                if (!categories.contains(app.category)) {
                    categories.add(app.category)
                }
            }
        }

        // Optional self-update info for the store app.
        var storeVersionCode = 0L
        var storeApkUrl = ""
        var storeSha256 = ""
        val update = root.optJSONObject("store_update")
        if (update != null) {
            storeVersionCode = update.optLong("version_code", 0L)
            storeApkUrl = update.optString("apk", "")
            storeSha256 = update.optString("sha256", "").lowercase()
        }

        return Catalog(categories, apps, storeVersionCode, storeApkUrl, storeSha256)
    }
}
