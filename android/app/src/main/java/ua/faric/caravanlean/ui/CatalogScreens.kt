package ua.faric.caravanlean.ui

import android.view.Gravity
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import ua.faric.caravanlean.R
import ua.faric.caravanlean.core.Category
import ua.faric.caravanlean.core.Product
import ua.faric.caravanlean.core.SortMode
import ua.faric.caravanlean.data.Cart
import ua.faric.caravanlean.data.CatalogRepo

/** Спільне для екранів-сіток: RecyclerView + GridAdapter, переходи на групу й товар, «+» у кошик. */
abstract class GridScreen(act: MainActivity) : Screen(act) {
    protected lateinit var adapter: GridAdapter
    protected lateinit var list: RecyclerView
    /** Екран уже побудовано (isInitialized доступний лише в цьому класі). */
    protected val isBuilt: Boolean get() = ::adapter.isInitialized && ::list.isInitialized

    override fun create(): View {
        adapter = GridAdapter(ui, onGroup = { act.push(CategoryScreen(act, it.id)) }, onItem = { openProduct(it) }, onQuickAdd = { quickAdd(it) })
        list = RecyclerView(act).apply { itemAnimator = null }
        GridAdapter.attach(list, adapter, ui)
        adapter.submit(cells())
        return list
    }

    abstract fun cells(): List<Cell>

    protected fun reload() {
        if (::adapter.isInitialized) adapter.submit(cells())
    }

    protected fun openProduct(p: Product) = act.push(ProductScreen(act, p.id))

    protected fun quickAdd(p: Product) {
        if (p.available == false) {
            act.toast("«${p.name.take(40)}» зараз немає в наявності")
            return
        }
        Cart.add(p.id)
        act.toast("Додано в кошик · усього ${Cart.count}")
    }

    override fun onCatalogChanged() = reload()
    override fun onCartChanged() {
        if (::adapter.isInitialized) adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }
}

/** Головна вкладки «Каталог»: вітання, пошук, плитки груп, ряд «Зі знижкою». */
class HomeScreen(act: MainActivity) : GridScreen(act) {
    override val title = "Caravan Lean"

    override fun actions(): List<View> = listOf(ui.iconButton(R.drawable.ic_search, desc = "Пошук") { act.onTab(Tab.SEARCH) })

    private fun hero(): View = ui.column(12).apply {
        setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(6))
        addView(ui.muted("Інтернет-магазин", 14f))
        val pill = ui.row(10).apply {
            setPadding(ui.dp(16), ui.dp(13), ui.dp(16), ui.dp(13))
            background = ui.pressable(ui.rounded(Palette.surface, Palette.border, radiusDp = 26), 26)
            addView(ui.icon(R.drawable.ic_search, Palette.muted, 20))
            addView(ui.text("Пошук товарів", 15f, Palette.faint))
            setOnClickListener { act.onTab(Tab.SEARCH) }
        }
        addView(pill, ui.lp())
        val c = catalog
        if (!c.isEmpty) {
            addView(ui.muted("${GridAdapter.goods(c.products.size)} · ${c.topLevel.size} груп · ${Dates.short(c.scrapedAt)}", 12.5f).apply {
                setPadding(ui.dp(4), 0, 0, 0)
            })
        }
    }

    override fun cells(): List<Cell> {
        val c = catalog
        val out = ArrayList<Cell>()
        out += Cell.Block("hero", hero())
        if (c.isEmpty) {
            out += if (CatalogRepo.loading) Cell.Empty("Завантажую каталог…", "")
            else Cell.Empty(
                "Каталог ще порожній",
                "У цю збірку ще не вкладено знімок сайту. Коли GitHub пропарсить сайт, нова версія застосунку покаже групи й товари.",
            )
            return out
        }
        out += Cell.Label("Каталог")
        c.topLevel.forEach { out += Cell.Group(it, c.countIn(it.id)) }
        val sale = c.products.filter { it.discountPercent != null }.sortedByDescending { it.discountPercent }.take(12)
        if (sale.isNotEmpty()) {
            out += Cell.Label("Зі знижкою")
            out += Cell.Block("sale", ui.horizontalRow(sale.map { p -> ui.miniProduct(p) { openProduct(p) } }, 150))
        }
        return out
    }
}

/** Група: шлях, підгрупи рядом, сортування, «лише в наявності», сітка товарів (з усіма підгрупами). */
class CategoryScreen(act: MainActivity, private val categoryId: String) : GridScreen(act) {
    private var sort = SortMode.DEFAULT
    private var onlyAvailable = false

    override val title: String get() = catalog.category(categoryId)?.name ?: "Група"

    private fun header(products: List<Product>): View = ui.column(10).apply {
        setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(4))
        val path = catalog.path(categoryId)
        if (path.size > 1) {
            addView(ui.muted(path.dropLast(1).joinToString("  ›  ") { it.name }, 12.5f))
        }
        val subs = catalog.children(categoryId)
        if (subs.isNotEmpty()) {
            addView(ui.horizontalRow(subs.map { s -> ui.subgroupChip(s, catalog.countIn(s.id)) { open(s) } }, 92))
        }
        val chips = listOf(
            ui.chip("Як на сайті", sort == SortMode.DEFAULT) { setSort(SortMode.DEFAULT) },
            ui.chip("Дешевші", sort == SortMode.PRICE_UP) { setSort(SortMode.PRICE_UP) },
            ui.chip("Дорожчі", sort == SortMode.PRICE_DOWN) { setSort(SortMode.PRICE_DOWN) },
            ui.chip("А–Я", sort == SortMode.NAME) { setSort(SortMode.NAME) },
            ui.chip("Лише в наявності", onlyAvailable) { onlyAvailable = !onlyAvailable; reload() },
        )
        val scroll = android.widget.HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false }
        scroll.addView(ui.row(8).apply { chips.forEach { addView(it) } })
        addView(scroll)
        addView(ui.muted(GridAdapter.goods(products.size), 12.5f).apply { gravity = Gravity.START })
    }

    private fun open(c: Category) = act.push(CategoryScreen(act, c.id))

    private fun setSort(s: SortMode) {
        sort = s
        reload()
        list.scrollToPosition(0)
    }

    override fun cells(): List<Cell> {
        if (catalog.category(categoryId) == null) return listOf(Cell.Empty("Групи вже немає", "Каталог оновився — повернись назад."))
        val products = catalog.productsIn(categoryId, sort, onlyAvailable)
        val out = ArrayList<Cell>()
        out += Cell.Block("head", header(products))
        if (products.isEmpty()) {
            out += Cell.Empty("Нічого не знайдено", if (onlyAvailable) "Спробуй вимкнути «Лише в наявності»." else "У цій групі поки немає товарів.")
        } else {
            products.forEach { out += Cell.Item(it) }
        }
        return out
    }
}
