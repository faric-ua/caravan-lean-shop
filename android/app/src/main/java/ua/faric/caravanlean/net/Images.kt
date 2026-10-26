package ua.faric.caravanlean.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import ua.faric.caravanlean.R

/**
 * Фото товарів: пам'ять (LruCache) → диск (cacheDir/img) → сайт. Без бібліотек.
 * Картинка зменшується до розміру плитки — щоб сітка з сотень товарів не з'їдала пам'ять.
 */
object Images {
    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())
    private val mem = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private lateinit var dir: File
    @Volatile private var downloads = 0

    fun init(ctx: Context) {
        dir = File(ctx.cacheDir, "img").apply { mkdirs() }
    }

    /** [targetPx] — найбільша сторона, до якої зменшувати (для плитки ~ширина плитки). */
    fun load(view: ImageView, url: String?, targetPx: Int, placeholder: Int) {
        view.setTag(R.id.image_url, url)
        if (url.isNullOrBlank()) {
            view.setImageDrawable(null)
            view.setBackgroundColor(placeholder)
            return
        }
        val key = "$url@$targetPx"
        mem.get(key)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageDrawable(null)
        view.setBackgroundColor(placeholder)
        pool.execute {
            val bmp = runCatching { fetch(url, targetPx) }.getOrNull()
            if (bmp != null) mem.put(key, bmp)
            main.post {
                if (view.getTag(R.id.image_url) == url && bmp != null) {
                    view.setImageBitmap(bmp)
                    view.alpha = 0f
                    view.animate().alpha(1f).setDuration(160).start()
                }
            }
        }
    }

    private fun fetch(url: String, targetPx: Int): Bitmap? {
        val file = File(dir, sha1(url))
        if (!file.isFile || file.length() == 0L) {
            val r = Http.get(url, accept = "image/avif,image/webp,image/*,*/*;q=0.5", maxBytes = 12 shl 20)
            if (!r.ok || r.body.isEmpty()) return null
            val tmp = File(file.path + ".part")
            tmp.writeBytes(r.body)
            tmp.renameTo(file)
            if (++downloads % 40 == 0) trim()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) {
            file.delete() // не картинка (сторінка-заглушка) — наступного разу спробуємо ще
            return null
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565.takeIf { bounds.outMimeType == "image/jpeg" } ?: Bitmap.Config.ARGB_8888
        })
    }

    /** Диск-кеш не більше ~150 МБ: старі файли — геть. */
    private fun trim() {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total < 150L shl 20) break
            total -= f.length()
            f.delete()
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
