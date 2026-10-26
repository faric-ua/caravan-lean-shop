package ua.faric.caravanlean.core

import kotlin.math.abs
import kotlin.math.roundToLong

/** Ціни по-українськи: «2 499 ₴», «1 234,50 ₴» (тонкий нерозривний пробіл між тисячами). */
object Money {
    private const val THIN = ' '

    fun format(value: Double?, currency: String = "UAH"): String {
        if (value == null) return "Ціну уточнюйте"
        val cents = (abs(value) * 100).roundToLong()
        val whole = groups(cents / 100)
        val frac = cents % 100
        val num = if (frac == 0L) whole else "$whole,${frac.toString().padStart(2, '0')}"
        return (if (value < 0) "−" else "") + num + " " + symbol(currency)
    }

    fun symbol(currency: String): String = when (currency.uppercase()) {
        "UAH", "ГРН", "" -> "₴"
        "USD" -> "$"
        "EUR" -> "€"
        else -> currency
    }

    private fun groups(n: Long): String {
        val s = n.toString()
        val sb = StringBuilder()
        s.forEachIndexed { i, ch ->
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(THIN)
            sb.append(ch)
        }
        return sb.toString()
    }
}
