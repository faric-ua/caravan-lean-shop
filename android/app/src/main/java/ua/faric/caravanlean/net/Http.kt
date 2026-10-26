package ua.faric.caravanlean.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Простий HTTP GET без бібліотек. Заголовки — як у звичайного мобільного Chrome (сайт ріже «ботів»). */
object Http {
    const val UA = "Mozilla/5.0 (Linux; Android 14; SM-A266B) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    class Response(val code: Int, val body: ByteArray, val finalUrl: String, val contentType: String) {
        val ok: Boolean get() = code in 200..299
        fun text(): String = String(body, bodyCharset())
        private fun bodyCharset(): java.nio.charset.Charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(contentType)
            ?.groupValues?.get(1)?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
    }

    @Throws(IOException::class)
    fun get(url: String, accept: String = "text/html,application/xhtml+xml,*/*;q=0.8", maxBytes: Int = 8 shl 20): Response {
        var current = url
        repeat(6) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 25_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", accept)
            c.setRequestProperty("Accept-Language", "uk-UA,uk;q=0.9,ru;q=0.7,en;q=0.6")
            c.setRequestProperty("Accept-Encoding", "gzip")
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw IOException("Переадресація без адреси")
                    current = URL(URL(current), loc).toString()
                    return@repeat
                }
                val raw = if (code >= 400) c.errorStream else c.inputStream
                val stream = if ("gzip".equals(c.contentEncoding, true) && raw != null) GZIPInputStream(raw) else raw
                val out = ByteArrayOutputStream()
                stream?.use { s ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = s.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > maxBytes) throw IOException("Відповідь завелика")
                    }
                }
                return Response(code, out.toByteArray(), current, c.contentType.orEmpty())
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Забагато переадресацій")
    }
}
