package ua.faric.caravanlean.ui

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Дати знімка каталогу по-людськи. */
object Dates {
    private fun parse(iso: String): java.util.Date? = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)
    }.getOrNull()

    /** «знімок від 27.10.2026». */
    fun short(iso: String): String = parse(iso)?.let { "знімок від " + SimpleDateFormat("dd.MM.yyyy", Locale.US).format(it) } ?: "знімок"

    /** «27.10.2026 о 14:05» (місцевий час телефона). */
    fun full(iso: String): String = parse(iso)?.let { SimpleDateFormat("dd.MM.yyyy 'о' HH:mm", Locale.US).format(it) } ?: "—"
}
