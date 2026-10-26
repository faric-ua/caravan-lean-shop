package ua.faric.caravanlean.core

/**
 * Каталог магазину: групи (з підгрупами) і товари. Чисте Kotlin-ядро без Android — тестується на JVM.
 * Звідки береться — не його справа (див. data/CatalogSource): знімок у APK, кеш, сайт, у майбутньому — їхня база.
 */
data class Category(
    val id: String,
    val name: String,
    val url: String?,
    val parentId: String?,
    val image: String?,
    val productCount: Int,
    val order: Int,
)

data class Attr(val name: String, val value: String)

data class Product(
    val id: String,
    val name: String,
    val url: String,
    val categoryId: String,
    val price: Double?,
    val oldPrice: Double?,
    val currency: String,
    /** null — сайт не каже. */
    val available: Boolean?,
    val images: List<String>,
    val sku: String,
    val brand: String,
    val description: String,
    val attributes: List<Attr>,
) {
    val image: String? get() = images.firstOrNull()
    val discountPercent: Int?
        get() = if (price != null && oldPrice != null && oldPrice > price && oldPrice > 0) {
            Math.round((1 - price / oldPrice) * 100).toInt().takeIf { it in 1..95 }
        } else null
}

enum class SortMode { DEFAULT, PRICE_UP, PRICE_DOWN, NAME }

class Catalog(
    val source: String,
    /** ISO-час знімка (UTC). */
    val scrapedAt: String,
    val currency: String,
    val categories: List<Category>,
    val products: List<Product>,
) {
    private val catById: Map<String, Category> = categories.associateBy { it.id }
    private val prodById: Map<String, Product> = products.associateBy { it.id }
    private val childrenOf: Map<String?, List<Category>> =
        categories.groupBy { it.parentId?.takeIf { p -> p in catById } }.mapValues { (_, v) -> v.sortedBy { it.order } }
    private val directProducts: Map<String, List<Product>> = products.groupBy { it.categoryId }
    private val searchIndex: List<Pair<Product, String>> by lazy {
        products.map { p -> p to Text.fold("${p.name} ${p.sku} ${p.brand} ${path(p.categoryId).joinToString(" ") { it.name }}") }
    }

    val isEmpty: Boolean get() = products.isEmpty()
    val topLevel: List<Category> get() = children(null)

    fun category(id: String?): Category? = id?.let { catById[it] }
    fun product(id: String): Product? = prodById[id]
    fun children(parentId: String?): List<Category> = childrenOf[parentId].orEmpty().filter { hasProducts(it.id) }

    /** Ланцюжок від верхньої групи до цієї (для «хлібних крихт» у шапці). */
    fun path(id: String?): List<Category> {
        val out = ArrayList<Category>()
        var c = category(id)
        val guard = HashSet<String>()
        while (c != null && guard.add(c.id)) {
            out.add(0, c)
            c = category(c.parentId)
        }
        return out
    }

    /** Товари групи разом з усіма підгрупами. */
    fun productsIn(id: String, sort: SortMode = SortMode.DEFAULT, onlyAvailable: Boolean = false): List<Product> {
        val out = ArrayList<Product>()
        val stack = ArrayDeque(listOf(id))
        val guard = HashSet<String>()
        while (stack.isNotEmpty()) {
            val cur = stack.removeFirst()
            if (!guard.add(cur)) continue
            out.addAll(directProducts[cur].orEmpty())
            childrenOf[cur]?.forEach { stack.addLast(it.id) }
        }
        return sorted(if (onlyAvailable) out.filter { it.available != false } else out, sort)
    }

    fun countIn(id: String): Int = productsIn(id).size

    private fun hasProducts(id: String): Boolean = directProducts[id]?.isNotEmpty() == true ||
        childrenOf[id].orEmpty().any { hasProducts(it.id) }

    /** Пошук: усі слова запиту мають знайтись у назві, артикулі, бренді чи групі (без регістру, ґ=г, ё=е). */
    fun search(query: String, limit: Int = 200): List<Product> {
        val words = Text.fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return searchIndex.asSequence()
            .filter { (_, hay) -> words.all { hay.contains(it) } }
            .map { (p, hay) -> p to (if (hay.startsWith(words.first())) 0 else 1) }
            .sortedWith(compareBy<Pair<Product, Int>>({ it.second }, { it.first.name.lowercase() }))
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** Замінити товар свіжими даними з сайту (ціна, наявність, фото), лишивши його місце в каталозі. */
    fun withProduct(fresh: Product): Catalog {
        if (fresh.id !in prodById) return this
        return Catalog(source, scrapedAt, currency, categories, products.map { if (it.id == fresh.id) fresh else it })
    }

    companion object {
        val EMPTY = Catalog("", "", "UAH", emptyList(), emptyList())

        fun sorted(list: List<Product>, sort: SortMode): List<Product> = when (sort) {
            SortMode.DEFAULT -> list
            SortMode.PRICE_UP -> list.sortedWith(compareBy<Product, Double?>(nullsLast(naturalOrder())) { it.price })
            SortMode.PRICE_DOWN -> list.sortedWith(compareByDescending<Product, Double?>(nullsFirst(naturalOrder())) { it.price })
            SortMode.NAME -> list.sortedBy { it.name.lowercase() }
        }
    }
}

object Text {
    fun fold(s: String): String = s.lowercase()
        .replace('ґ', 'г').replace('ё', 'е').replace('ї', 'і').replace('й', 'и').replace('’', '\'').replace('ʼ', '\'')
        .replace(Regex("[^\\p{L}\\p{N}']+"), " ")
        .trim()
}
