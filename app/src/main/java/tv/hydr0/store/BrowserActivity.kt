package tv.hydr0.store

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast

// A simple web browser for the TV remote.
//
// Safety rules:
//  - Pages can't install anything on their own. When a page offers an .apk,
//    you're asked first, and the file then goes through the store's normal
//    https-only download and Android's own install screen.
//  - Other downloads (videos, zips, ...) are refused.
//  - Links to other apps (intent://, market://, file://, ...) are blocked.
//  - No local file access, and no mixing of http content into https pages.
class BrowserActivity : Activity() {

    companion object {
        const val EXTRA_APK_URL = "apk_url"
        const val EXTRA_APK_NAME = "apk_name"
        private const val HOME_PAGE = "https://www.google.com"
        private const val SEARCH_URL = "https://www.google.com/search?q="
    }

    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        web = findViewById(R.id.web)
        address = findViewById(R.id.address)
        progress = findViewById(R.id.progress)

        setUpWebView()

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            if (web.canGoBack()) web.goBack()
        }
        findViewById<Button>(R.id.btnForward).setOnClickListener {
            if (web.canGoForward()) web.goForward()
        }
        findViewById<Button>(R.id.btnReload).setOnClickListener { web.reload() }
        findViewById<Button>(R.id.btnHome).setOnClickListener { web.loadUrl(HOME_PAGE) }
        findViewById<Button>(R.id.btnGo).setOnClickListener { go() }
        findViewById<Button>(R.id.btnClose).setOnClickListener { finish() }

        address.setOnEditorActionListener { _, actionId, event ->
            val enterKey = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enterKey) {
                go()
                true
            } else {
                false
            }
        }

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            web.loadUrl(HOME_PAGE)
        }
        web.requestFocus()
    }

    private fun setUpWebView() {
        val s = web.settings
        s.javaScriptEnabled = true          // most sites need it
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.setSupportMultipleWindows(false)  // pop-ups open in the same page
        s.javaScriptCanOpenWindowsAutomatically = false
        s.builtInZoomControls = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.mediaPlaybackRequiresUserGesture = true

        web.isFocusable = true
        web.isFocusableInTouchMode = true

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return !allowedToOpen(request.url)
            }

            @Deprecated("Needed for Android 6 and older")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return !allowedToOpen(Uri.parse(url))
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (!address.hasFocus()) {
                    address.setText(url ?: "")
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        web.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            onDownloadRequested(url, contentDisposition, mimeType)
        }
    }

    // Only normal web pages. Everything else (other apps, local files) is blocked.
    private fun allowedToOpen(uri: Uri): Boolean {
        val scheme = (uri.scheme ?: "").lowercase()
        if (scheme == "https" || scheme == "http") {
            return true
        }
        toast("Blocked a link that tries to open another app")
        return false
    }

    private fun onDownloadRequested(url: String, contentDisposition: String?, mimeType: String?) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val isApk = fileName.lowercase().endsWith(".apk") ||
            (mimeType ?: "") == "application/vnd.android.package-archive"

        if (!isApk) {
            toast("Only app files (.apk) can be downloaded here")
            return
        }
        if (!url.startsWith("https://")) {
            toast("Refused: this app file isn't on a secure (https) link")
            return
        }

        val host = Uri.parse(url).host ?: "unknown site"
        AlertDialog.Builder(this)
            .setTitle("Install $fileName?")
            .setMessage(
                "This app file comes from:\n$host\n\n" +
                    "Only install apps from sites you trust. " +
                    "Android will still show its own install screen."
            )
            .setPositiveButton("Download") { _, _ ->
                val result = Intent()
                result.putExtra(EXTRA_APK_URL, url)
                result.putExtra(EXTRA_APK_NAME, fileName.removeSuffix(".apk"))
                setResult(RESULT_OK, result)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Web address, bare domain, or search words.
    private fun go() {
        val text = address.text.toString().trim()
        if (text.isEmpty()) {
            return
        }
        val url = when {
            text.startsWith("https://") || text.startsWith("http://") -> text
            !text.contains(" ") && text.contains(".") -> "https://$text"
            else -> SEARCH_URL + Uri.encode(text)
        }
        web.loadUrl(url)
        web.requestFocus()
    }

    // The remote's Back key walks back through pages before leaving the browser.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack() && !address.hasFocus()) {
            web.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
