package tv.hydr0.store

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.util.concurrent.Executors

// Loads app icons in the background and keeps them in memory.
// Apps with no icon link get a colored circle with their first letter.
object Icons {

    private val cache = HashMap<String, Bitmap>()
    private val workers = Executors.newFixedThreadPool(3)
    private val mainThread = Handler(Looper.getMainLooper())

    private val tileColors = intArrayOf(
        Color.parseColor("#00B4FF"),
        Color.parseColor("#7C4DFF"),
        Color.parseColor("#FF6D00"),
        Color.parseColor("#00BFA5"),
        Color.parseColor("#E91E63"),
        Color.parseColor("#FFC400")
    )

    fun show(app: StoreApp, view: ImageView) {
        val key = app.id
        view.tag = key   // lets us ignore late results if the card was reused

        val cached = cache[key]
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }

        val letterTile = makeLetterTile(app.name)
        view.setImageBitmap(letterTile)

        if (app.iconUrl.isEmpty() || !app.iconUrl.startsWith("https://")) {
            cache[key] = letterTile
            return
        }

        workers.execute {
            var bitmap: Bitmap? = null
            try {
                val conn = java.net.URL(app.iconUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val bytes = conn.inputStream.readBytes()
                conn.disconnect()

                // Read the size first, then shrink big images so they can't eat all the memory.
                val sizeOnly = BitmapFactory.Options()
                sizeOnly.inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sizeOnly)
                var sample = 1
                while (sizeOnly.outWidth / sample > 256 || sizeOnly.outHeight / sample > 256) {
                    sample = sample * 2
                }
                val options = BitmapFactory.Options()
                options.inSampleSize = sample
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } catch (e: Throwable) {
                bitmap = null
            }

            val finalBitmap = bitmap ?: letterTile
            mainThread.post {
                cache[key] = finalBitmap
                if (view.tag == key) {
                    view.setImageBitmap(finalBitmap)
                }
            }
        }
    }

    private fun makeLetterTile(name: String): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        var total = 0
        for (ch in name) {
            total = total + ch.code
        }
        val color = tileColors[total % tileColors.size]

        val circle = Paint(Paint.ANTI_ALIAS_FLAG)
        circle.color = color
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, circle)

        var letter = "?"
        if (name.isNotEmpty()) {
            letter = name.substring(0, 1).uppercase()
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.color = Color.WHITE
        text.textSize = 64f
        text.isFakeBoldText = true
        text.textAlign = Paint.Align.CENTER
        val y = size / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(letter, size / 2f, y, text)

        return bitmap
    }
}
