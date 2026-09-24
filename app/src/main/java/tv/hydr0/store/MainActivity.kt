package tv.hydr0.store

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException

class MainActivity : Activity() {

    private val prefsName = "store_settings"
    private val prefCatalogUrl = "catalog_url"
    private val allCategory = "All apps"

    private lateinit var grid: GridView
    private lateinit var categoryList: ListView
    private lateinit var searchBox: EditText
    private lateinit var statusText: TextView
    private lateinit var updateStoreButton: Button

    private lateinit var appAdapter: AppAdapter
    private lateinit var categoryAdapter: ArrayAdapter<String>
    private val categoryNames = ArrayList<String>()

    private var catalog: Catalog? = null
    private var selectedCategory = allCategory

    // ---------------------------------------------------------------- setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        grid = findViewById(R.id.grid)
        categoryList = findViewById(R.id.categories)
        searchBox = findViewById(R.id.search)
        statusText = findViewById(R.id.status)
        updateStoreButton = findViewById(R.id.btnUpdateStore)

        appAdapter = AppAdapter(this)
        grid.adapter = appAdapter
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            showAppDialog(appAdapter.getItem(position))
        }

        categoryAdapter = ArrayAdapter(this, R.layout.item_category, categoryNames)
        categoryList.adapter = categoryAdapter
        categoryList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            chooseCategory(position)
            grid.requestFocus()
        }
        // Moving up and down the list with the remote filters right away.
        categoryList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                chooseCategory(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                applyFilter()
            }
        })

        findViewById<Button>(R.id.btnCode).setOnClickListener { showCodeDialog() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { loadCatalog() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }
        updateStoreButton.setOnClickListener { updateStore() }

        loadCatalog()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the installer: refresh the "Installed" labels.
        appAdapter.notifyDataSetChanged()
    }

    // -------------------------------------------------------------- catalog

    private fun catalogUrl(): String {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val saved = prefs.getString(prefCatalogUrl, "") ?: ""
        if (saved.isNotEmpty()) {
            return saved
        }
        return BuildConfig.CATALOG_URL
    }

    // Tries the online catalog first, then the last saved copy, then the one
    // built into the APK. Runs in the background.
    private fun loadCatalog() {
        statusText.text = "Loading catalog..."
        val url = catalogUrl()

        Thread {
            var text: String? = null
            var from = ""
            val savedCopy = File(filesDir, "catalog.json")

            try {
                val downloaded = Net.getText(url)
                CatalogParser.parse(downloaded)    // make sure it is valid before saving
                savedCopy.writeText(downloaded)
                text = downloaded
                from = "online"
            } catch (e: Exception) {
                text = null
            }

            if (text == null && savedCopy.exists()) {
                try {
                    text = savedCopy.readText()
                    from = "saved copy (offline)"
                } catch (e: Exception) {
                    text = null
                }
            }

            if (text == null) {
                try {
                    val stream = assets.open("catalog.json")
                    text = String(stream.readBytes(), Charsets.UTF_8)
                    stream.close()
                    from = "built-in copy"
                } catch (e: Exception) {
                    text = null
                }
            }

            var parsed: Catalog? = null
            var error = ""
            try {
                parsed = CatalogParser.parse(text ?: "")
            } catch (e: Exception) {
                error = e.message ?: "unknown error"
            }

            val result: Catalog? = parsed
            val errorText = error
            val source = from
            runOnUiThread {
                if (screenGone()) {
                    return@runOnUiThread
                }
                if (result == null) {
                    statusText.text = "Could not read the catalog: $errorText"
                } else {
                    showCatalog(result, source)
                }
            }
        }.start()
    }

    private fun showCatalog(newCatalog: Catalog, from: String) {
        catalog = newCatalog

        categoryNames.clear()
        categoryNames.add(allCategory)
        for (name in newCatalog.categories) {
            categoryNames.add(name)
        }
        categoryAdapter.notifyDataSetChanged()

        if (!categoryNames.contains(selectedCategory)) {
            selectedCategory = allCategory
        }
        applyFilter()

        statusText.text = "${newCatalog.apps.size} apps  •  catalog: $from  •  store v${BuildConfig.VERSION_NAME}"

        // Offer a store update when the catalog lists a newer build of this app.
        val newer = newCatalog.storeVersionCode > BuildConfig.VERSION_CODE
        if (newer && newCatalog.storeApkUrl.isNotEmpty()) {
            updateStoreButton.visibility = View.VISIBLE
        } else {
            updateStoreButton.visibility = View.GONE
        }

        grid.requestFocus()
    }

    private fun chooseCategory(position: Int) {
        if (position < 0 || position >= categoryNames.size) {
            return
        }
        val name = categoryNames[position]
        if (name != selectedCategory) {
            selectedCategory = name
            applyFilter()
        }
    }

    private fun applyFilter() {
        val current = catalog ?: return
        val query = searchBox.text.toString().trim().lowercase()

        val shown = ArrayList<StoreApp>()
        for (app in current.apps) {
            var categoryOk = selectedCategory == allCategory || app.category == selectedCategory
            // A search looks through every category.
            if (query.isNotEmpty()) {
                categoryOk = true
            }
            var searchOk = true
            if (query.isNotEmpty()) {
                searchOk = app.name.lowercase().contains(query)
            }
            if (categoryOk && searchOk) {
                shown.add(app)
            }
        }
        appAdapter.setItems(shown)
    }

    // ------------------------------------------------------------- dialogs

    private fun showAppDialog(app: StoreApp) {
        val status = Packages.state(this, app)
        val installed = Packages.lookup(this, app.packageName)

        val message = StringBuilder()
        if (app.description.isNotEmpty()) {
            message.append(app.description).append("\n\n")
        }
        message.append("Category: ").append(app.category).append("\n")
        if (app.version.isNotEmpty()) {
            message.append("Catalog version: ").append(app.version).append("\n")
        }
        if (installed != null) {
            message.append("Installed version: ").append(installed.versionName).append("\n")
        }
        message.append("Source: ").append(describeSource(app))
        if (app.code.isNotEmpty()) {
            message.append("\nCode: ").append(app.code)
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle(app.name)
        builder.setMessage(message.toString())

        if (status == Packages.NOT_INSTALLED) {
            builder.setPositiveButton(primaryLabel(app)) { _, _ -> getApp(app) }
            builder.setNegativeButton("Close", null)
        } else if (status == Packages.UPDATE_AVAILABLE) {
            builder.setPositiveButton("Update") { _, _ -> getApp(app) }
            builder.setNeutralButton("Open") { _, _ -> launchApp(app.packageName) }
            builder.setNegativeButton("Uninstall") { _, _ -> uninstall(app.packageName) }
        } else {
            builder.setPositiveButton("Open") { _, _ -> launchApp(app.packageName) }
            builder.setNeutralButton("Uninstall") { _, _ -> uninstall(app.packageName) }
            builder.setNegativeButton("Close", null)
        }
        builder.show()
    }

    private fun primaryLabel(app: StoreApp): String {
        if (app.source.type == "store") {
            return "Get from app store"
        }
        if (app.source.type == "web") {
            return "Open website"
        }
        return "Install"
    }

    private fun describeSource(app: StoreApp): String {
        val type = app.source.type
        if (type == "apk") {
            val host = Uri.parse(app.source.url).host ?: "unknown"
            var text = "direct download from $host"
            if (app.source.sha256.isNotEmpty()) {
                text = text + " (checksum verified)"
            }
            return text
        }
        if (type == "github") {
            return "latest GitHub release of ${app.source.repo}"
        }
        if (type == "web") {
            return app.source.url
        }
        if (Packages.isAmazonDevice()) {
            return "Amazon Appstore"
        }
        return "Google Play"
    }

    private fun showCodeDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Code"

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter app code")
        builder.setView(wrap(input))
        builder.setPositiveButton("Go") { _, _ ->
            val code = input.text.toString().trim()
            var found: StoreApp? = null
            val current = catalog
            if (current != null) {
                for (app in current.apps) {
                    if (app.code.isNotEmpty() && app.code == code) {
                        found = app
                        break
                    }
                }
            }
            if (found != null) {
                showAppDialog(found)
            } else {
                toast("No app uses code $code")
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showSettingsDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.setText(catalogUrl())

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Settings")
        builder.setMessage("Catalog link (must start with https://)\n\nStore version ${BuildConfig.VERSION_NAME}")
        builder.setView(wrap(input))
        builder.setPositiveButton("Save") { _, _ ->
            val url = input.text.toString().trim()
            if (!url.startsWith("https://")) {
                toast("The link must start with https://")
            } else {
                saveCatalogUrl(url)
                loadCatalog()
            }
        }
        builder.setNeutralButton("Reset to default") { _, _ ->
            saveCatalogUrl("")
            loadCatalog()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun saveCatalogUrl(url: String) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putString(prefCatalogUrl, url).apply()
    }

    // Gives a dialog's text box some breathing room.
    private fun wrap(view: View): View {
        val box = LinearLayout(this)
        val pad = (20 * resources.displayMetrics.density).toInt()
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(view, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        return box
    }

    // ------------------------------------------------------------- actions

    private fun getApp(app: StoreApp) {
        val type = app.source.type
        if (type == "apk") {
            downloadAndInstall(app.name, safeFileName(app.id), app.source.url, app.source.sha256)
        } else if (type == "github") {
            statusText.text = "Finding the latest ${app.name} release..."
            Thread {
                try {
                    val url = Net.latestGithubAsset(app.source.repo, app.source.assetPattern)
                    runOnUiThread {
                        if (screenGone()) {
                            return@runOnUiThread
                        }
                        downloadAndInstall(app.name, safeFileName(app.id), url, "")
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        if (screenGone()) {
                            return@runOnUiThread
                        }
                        statusText.text = ""
                        showError("Could not find a download for ${app.name}", e)
                    }
                }
            }.start()
        } else if (type == "web") {
            openLink(app.source.url)
        } else {
            openStorePage(app.packageName)
        }
    }

    private fun updateStore() {
        val current = catalog ?: return
        downloadAndInstall("Store update", "store-update", current.storeApkUrl, current.storeSha256)
    }

    private fun downloadAndInstall(title: String, fileBase: String, url: String, sha256: String) {
        if (!canInstallApps()) {
            askForInstallPermission()
            return
        }

        // Folder: private cache on Android 7+, app's external folder on older boxes
        // (the old installers can only read plain files).
        var folder: File
        if (Build.VERSION.SDK_INT >= 24) {
            folder = File(cacheDir, ApkProvider.FOLDER)
        } else {
            folder = getExternalFilesDir(ApkProvider.FOLDER) ?: File(cacheDir, ApkProvider.FOLDER)
        }
        folder.mkdirs()
        clearOldDownloads(folder)
        val target = File(folder, "$fileBase.apk")

        // Progress dialog
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.isIndeterminate = true
        val label = TextView(this)
        label.text = "Starting download..."
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.addView(bar)
        box.addView(label)

        var cancelled = false
        val dialog = AlertDialog.Builder(this)
            .setTitle("Downloading $title")
            .setView(wrap(box))
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> cancelled = true }
            .show()

        Thread {
            try {
                Net.downloadFile(url, target, sha256) { percent ->
                    if (cancelled) {
                        throw IOException("Cancelled")
                    }
                    runOnUiThread {
                        if (screenGone()) {
                            return@runOnUiThread
                        }
                        if (percent >= 0) {
                            bar.isIndeterminate = false
                            bar.progress = percent
                            label.text = "$percent%"
                        } else {
                            label.text = "Downloading..."
                        }
                    }
                }
                runOnUiThread {
                    if (screenGone()) {
                        return@runOnUiThread
                    }
                    dialog.dismiss()
                    installApk(target)
                }
            } catch (e: Exception) {
                target.delete()
                runOnUiThread {
                    if (screenGone()) {
                        return@runOnUiThread
                    }
                    dialog.dismiss()
                    if (!cancelled) {
                        showError("Download failed", e)
                    }
                }
            }
        }.start()
    }

    private fun installApk(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        var uri: Uri
        if (Build.VERSION.SDK_INT >= 24) {
            uri = ApkProvider.uriFor("$packageName.apks", file)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            uri = Uri.fromFile(file)
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showError("No installer found on this device", e)
        }
    }

    private fun canInstallApps(): Boolean {
        if (Build.VERSION.SDK_INT >= 26) {
            return packageManager.canRequestPackageInstalls()
        }
        return true   // older boxes use the global "Unknown sources" switch
    }

    private fun askForInstallPermission() {
        AlertDialog.Builder(this)
            .setTitle("Allow installs")
            .setMessage(
                "This store needs permission to install apps.\n\n" +
                "On Fire TV: Settings > My Fire TV > Developer options > Install unknown apps, " +
                "then turn this store on."
            )
            .setPositiveButton("Open settings") { _, _ ->
                var opened = false
                if (Build.VERSION.SDK_INT >= 26) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                        opened = true
                    } catch (e: Exception) {
                        opened = false
                    }
                }
                if (!opened) {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (e: Exception) {
                        toast("Open Settings from the home screen")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchApp(pkg: String) {
        var intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
        if (intent == null) {
            intent = packageManager.getLaunchIntentForPackage(pkg)
        }
        if (intent == null) {
            toast("This app has no screen to open")
            return
        }
        startActivity(intent)
    }

    private fun uninstall(pkg: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("Uninstall this app from Settings > Applications")
        }
    }

    // Amazon devices try the Amazon Appstore first, everything else tries Google Play.
    private fun openStorePage(pkg: String) {
        val links = ArrayList<String>()
        if (Packages.isAmazonDevice()) {
            links.add("amzn://apps/android?p=$pkg")
        }
        links.add("market://details?id=$pkg")
        links.add("https://play.google.com/store/apps/details?id=$pkg")

        for (link in links) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // try the next one
            }
        }
        toast("No app store found. Search for the app by name in your device's store.")
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            AlertDialog.Builder(this)
                .setTitle("No browser found")
                .setMessage("Open this link on another device:\n\n$url")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // -------------------------------------------------------------- helpers

    // True once the user has left the screen; background work must not touch it then.
    private fun screenGone(): Boolean {
        return isFinishing || isDestroyed
    }

    private fun clearOldDownloads(folder: File) {
        val files = folder.listFiles() ?: return
        for (f in files) {
            if (f.name.endsWith(".apk")) {
                f.delete()
            }
        }
    }

    private fun safeFileName(text: String): String {
        val builder = StringBuilder()
        for (ch in text) {
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_') {
                builder.append(ch)
            } else {
                builder.append('_')
            }
        }
        if (builder.isEmpty()) {
            return "app"
        }
        return builder.toString()
    }

    private fun showError(title: String, e: Exception) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(e.message ?: e.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
