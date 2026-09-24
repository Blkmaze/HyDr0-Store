package tv.hydr0.store

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// All network work lives here. Every function blocks, so call them from a
// background thread, never from the UI thread.
object Net {

    private const val USER_AGENT = "HyDr0Store/1.0 (Android)"
    private const val MAX_REDIRECTS = 6

    // Opens a connection and follows redirects by hand, so we can check every hop.
    // Every hop must be https; plain http is refused.
    private fun open(startUrl: String): HttpURLConnection {
        var url = startUrl
        var hops = 0
        while (true) {
            if (!url.startsWith("https://")) {
                throw IOException("Blocked insecure (non-https) link: $url")
            }

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", USER_AGENT)

            val code = conn.responseCode
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next == null) {
                    throw IOException("Redirect with no location from $url")
                }
                hops = hops + 1
                if (hops > MAX_REDIRECTS) {
                    throw IOException("Too many redirects")
                }
                url = URL(URL(url), next).toString()   // handles relative locations
                continue
            }

            if (code != 200) {
                conn.disconnect()
                throw IOException("Server answered $code for $url")
            }
            return conn
        }
    }

    // Downloads a small text file such as the catalog.
    fun getText(url: String): String {
        val conn = open(url)
        try {
            val bytes = conn.inputStream.readBytes()
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    // Asks GitHub for the newest release of a repo and returns the download
    // link of the first asset whose name matches the pattern.
    fun latestGithubAsset(repo: String, pattern: String): String {
        val text = getText("https://api.github.com/repos/$repo/releases/latest")
        val release = JSONObject(text)
        val assets = release.getJSONArray("assets")
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)

        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name")
            if (regex.containsMatchIn(name)) {
                return asset.getString("browser_download_url")
            }
        }
        throw IOException("No file matching \"$pattern\" in the latest $repo release")
    }

    // Downloads a file to disk and reports progress (0-100, or -1 when the size is unknown).
    // If expectedSha256 is not empty, the file is checked and deleted on mismatch.
    // Returns the file's SHA-256 (lowercase hex).
    fun downloadFile(
        url: String,
        target: File,
        expectedSha256: String,
        onProgress: (Int) -> Unit
    ): String {
        val hasHash = expectedSha256.isNotEmpty()
        val conn = open(url)
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            val total = conn.contentLength.toLong()
            var done = 0L
            var lastPercent = -2

            val input = conn.inputStream
            val output = FileOutputStream(target)
            try {
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) {
                        break
                    }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    done = done + count

                    var percent = -1
                    if (total > 0) {
                        percent = ((done * 100) / total).toInt()
                    }
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent)
                    }
                }
            } finally {
                output.close()
                input.close()
            }
        } finally {
            conn.disconnect()
        }

        val actual = toHex(digest.digest())
        if (hasHash && actual != expectedSha256) {
            target.delete()
            throw IOException("Checksum mismatch. The file was deleted for safety.")
        }
        return actual
    }

    // What VirusTotal knows about a file. known = false when it has never seen it.
    class ScanResult(
        val known: Boolean,
        val malicious: Int,
        val suspicious: Int,
        val engines: Int
    )

    // Looks a file up on VirusTotal by its SHA-256. Only the hash is sent,
    // never the file itself.
    fun virusTotalLookup(sha256: String, apiKey: String): ScanResult {
        val conn = URL("https://www.virustotal.com/api/v3/files/$sha256").openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("x-apikey", apiKey)
        try {
            val code = conn.responseCode
            if (code == 404) {
                return ScanResult(false, 0, 0, 0)
            }
            if (code == 401 || code == 403) {
                throw IOException("VirusTotal rejected the API key. Check it in Settings.")
            }
            if (code == 429) {
                throw IOException("VirusTotal limit reached (free keys allow 4 checks a minute). Try again shortly.")
            }
            if (code != 200) {
                throw IOException("VirusTotal answered $code")
            }
            val text = String(conn.inputStream.readBytes(), Charsets.UTF_8)
            val stats = JSONObject(text)
                .getJSONObject("data")
                .getJSONObject("attributes")
                .getJSONObject("last_analysis_stats")
            var engines = 0
            val keys = stats.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                // "type-unsupported" and "failure" engines didn't really scan it
                if (key != "type-unsupported" && key != "failure") {
                    engines = engines + stats.optInt(key, 0)
                }
            }
            return ScanResult(true, stats.optInt("malicious", 0), stats.optInt("suspicious", 0), engines)
        } finally {
            conn.disconnect()
        }
    }

    private fun toHex(bytes: ByteArray): String {
        val builder = StringBuilder()
        for (b in bytes) {
            builder.append(String.format("%02x", b))
        }
        return builder.toString()
    }
}
