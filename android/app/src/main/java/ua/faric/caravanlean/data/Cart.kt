package ua.faric.caravanlean.data

import android.content.Context
import android.content.SharedPreferences
import ua.faric.caravanlean.core.Catalog
import ua.faric.caravanlean.core.Product

/**
 * Кошик-демо: живе на телефоні (SharedPreferences), замовлення нікуди не відправляє.
 * У справжньому застосунку — заявка в їхню базу/CRM через той самий сервер, що й каталог.
 */
object Cart {
    private lateinit var prefs: SharedPreferences
    private val items = LinkedHashMap<String, Int>()
    private val listeners = LinkedHashSet<() -> Unit>()

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("cart", Context.MODE_PRIVATE)
        prefs.getString("items", "").orEmpty().split(';').forEach { part ->
            val id = part.substringBefore('=')
            val q = part.substringAfter('=', "").toIntOrNull() ?: 0
            if (id.isNotBlank() && q > 0) items[id] = q
        }
    }

    val count: Int get() = items.values.sum()
    fun qty(id: String): Int = items[id] ?: 0
    fun entries(c: Catalog): List<Pair<Product, Int>> = items.mapNotNull { (id, q) -> c.product(id)?.let { it to q } }
    fun total(c: Catalog): Double = entries(c).sumOf { (p, q) -> (p.price ?: 0.0) * q }
    fun hasUnpriced(c: Catalog): Boolean = entries(c).any { it.first.price == null }

    fun add(id: String, n: Int = 1) = set(id, qty(id) + n)

    fun set(id: String, q: Int) {
        if (q <= 0) items.remove(id) else items[id] = q.coerceAtMost(999)
        save()
    }

    fun clear() {
        items.clear()
        save()
    }

    private fun save() {
        prefs.edit().putString("items", items.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
        listeners.toList().forEach { it() }
    }

    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }
}
