package ua.faric.caravanlean.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import ua.faric.caravanlean.R
import ua.faric.caravanlean.core.Product
import ua.faric.caravanlean.data.Cart
import ua.faric.caravanlean.data.CatalogRepo
import ua.faric.caravanlean.net.Images

class ProductScreen(act: MainActivity, private val productId: String) : Screen(act) {
    private lateinit var body: LinearLayout
    private var qty = 1
    /** Стан перевірки на сайті: null — ще перевіряємо, true — свіже, false — сайт не відповів. */
    private var live: Boolean? = null
    private var galleryView: View? = null
    private var galleryKey: List<String>? = null

    private val product: Product? get() = catalog.product(productId)

    override val title: String get() = catalog.path(product?.categoryId).lastOrNull()?.name ?: "Товар"

    override fun actions(): List<View> = listOf(ui.iconButton(R.drawable.ic_cart, desc = "Кошик") { act.onTab(Tab.CART) })

    override fun create(): View {
        val scroll = ScrollView(act).apply { isFillViewport = true }
        body = ui.column()
        scroll.addView(body)
        render()
        product?.let { p ->
            CatalogRepo.refreshProduct(p) { fresh ->
                live = fresh != null
                render()
            }
        }
        return scroll
    }

    override fun onCatalogChanged() { if (::body.isInitialized) render() }
    override fun onCartChanged() { if (::body.isInitialized) render() }

    private fun render() {
        body.removeAllViews()
        val p = product ?: run {
            body.addView(ui.muted("Товару вже немає в каталозі.", 15f).apply { setPadding(ui.dp(20), ui.dp(40), ui.dp(20), 0) })
            return
        }
        // галерею не перебудовуємо, якщо фото ті самі (інакше «+1 шт.» гортав би на перше фото)
        val g = galleryView?.takeIf { galleryKey == p.images } ?: gallery(p).also { galleryView = it; galleryKey = p.images }
        (g.parent as? ViewGroup)?.removeView(g)
        body.addView(g, ui.lp())

        val info = ui.column(12).apply { setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(8)) }
        val path = catalog.path(p.categoryId)
        if (path.isNotEmpty()) {
            val crumbs = ui.row(4)
            path.forEachIndexed { i, c ->
                if (i > 0) crumbs.addView(ui.muted("›", 12.5f))
                crumbs.addView(ui.text(c.name, 12.5f, Palette.accent).apply {
                    setPadding(ui.dp(2), ui.dp(4), ui.dp(2), ui.dp(4))
                    setOnClickListener { act.push(CategoryScreen(act, c.id)) }
                })
            }
            info.addView(android.widget.HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(crumbs) })
        }
        info.addView(ui.title(p.name, 20f))
        info.addView(ui.row(10).apply {
            addView(ui.price(p, big = true))
            p.discountPercent?.let { d ->
                addView(ui.text("−$d%", 13f, Palette.sale, medium = true).apply {
                    setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3))
                    background = ui.rounded(Palette.saleSoft, radiusDp = 10)
                })
            }
        })
        info.addView(ui.stock(p.available, 14f))
        info.addView(liveLine())
        val meta = listOfNotNull(p.sku.takeIf { it.isNotBlank() }?.let { "Артикул: $it" }, p.brand.takeIf { it.isNotBlank() }?.let { "Бренд: $it" })
        if (meta.isNotEmpty()) info.addView(ui.muted(meta.joinToString("   ·   "), 13f))
        body.addView(info, ui.lp())

        body.addView(buyRow(p), ui.lp().apply { setMargins(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(8)) })

        if (p.description.isNotBlank()) body.addView(section("Опис", description(p.description)))
        if (p.attributes.isNotEmpty()) body.addView(section("Характеристики", attributes(p)))

        val similar = catalog.productsIn(p.categoryId).filter { it.id != p.id }.take(12)
        if (similar.isNotEmpty()) {
            body.addView(ui.label("Схожі товари").apply { setPadding(ui.dp(20), ui.dp(16), ui.dp(16), ui.dp(8)) })
            body.addView(ui.horizontalRow(similar.map { s -> ui.miniProduct(s) { act.push(ProductScreen(act, s.id)) } }, 150).apply {
                setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(24))
            })
        }
        body.addView(ui.spacer(16))
    }

    private fun liveLine(): TextView = when (live) {
        null -> ui.muted("Перевіряю ціну й наявність на сайті…", 12.5f)
        true -> ui.text("Ціна й наявність — з сайту щойно", 12.5f, Palette.inStock)
        false -> ui.text("Сайт зараз не відповів — показано дані з каталогу (${Dates.short(catalog.scrapedAt)})", 12.5f, Palette.warning)
    }

    // ---------- галерея: гортання по одному фото, лічильник «1 / 5» ----------

    private fun gallery(p: Product): View {
        val box = FrameLayout(act).apply { setBackgroundColor(Palette.surface) }
        val images = p.images.ifEmpty { listOf("") }
        val counter = ui.text("1 / ${images.size}", 12f, Palette.onAccent, medium = true).apply {
            setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4))
            background = ui.rounded(Palette.toastBg, radiusDp = 12)
            visibility = if (images.size > 1) View.VISIBLE else View.GONE
        }
        val rv = RecyclerView(act)
        val lm = LinearLayoutManager(act, LinearLayoutManager.HORIZONTAL, false)
        rv.layoutManager = lm
        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = images.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val iv = SquareImage(parent.context, 0.85f).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                return object : RecyclerView.ViewHolder(iv) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                Images.load(holder.itemView as ImageView, images[position].ifBlank { null }, ui.dp(720), Palette.surface)
            }
        }
        PagerSnapHelper().attachToRecyclerView(rv)
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val pos = lm.findFirstCompletelyVisibleItemPosition().takeIf { it >= 0 } ?: lm.findFirstVisibleItemPosition()
                    counter.text = "${pos + 1} / ${images.size}"
                }
            }
        })
        box.addView(rv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(counter, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, ui.dp(14), ui.dp(12))
        })
        return box
    }

    // ---------- купити ----------

    private fun buyRow(p: Product): View = ui.column(10).apply {
        val inCart = Cart.qty(p.id)
        if (p.available == false) {
            addView(ui.secondary("Немає в наявності") { act.toast("Коли з'явиться — тут можна буде підписатися на сповіщення (у справжньому застосунку)") })
            return@apply
        }
        val row = ui.row(10)
        row.addView(ui.stepper(qty) { q -> qty = q.coerceIn(1, 99); render() })
        row.addView(ui.primary("У кошик", R.drawable.ic_cart) {
            Cart.add(p.id, qty)
            act.toast("Додано: $qty шт. · у кошику ${Cart.count}")
            qty = 1
        }, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(row, ui.lp())
        if (inCart > 0) {
            addView(ui.secondary("У кошику вже $inCart шт. — відкрити кошик", R.drawable.ic_chevron) { act.onTab(Tab.CART) })
        }
    }

    // ---------- опис і характеристики ----------

    private fun section(name: String, content: View): View = ui.column(10).apply {
        setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(4))
        addView(ui.label(name).apply { setPadding(ui.dp(4), 0, 0, 0) })
        addView(ui.card(16).apply { addView(content) }, ui.lp())
    }

    private fun description(text: String): View = ui.column(10).apply {
        val tv = ui.text(text, 14.5f, Palette.text).apply { setLineSpacing(0f, 1.3f) }
        addView(tv)
        if (text.length > 360) {
            tv.maxLines = 7
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
            val more = ui.text("Показати повністю", 14f, Palette.accent, medium = true)
            more.setOnClickListener {
                val open = tv.maxLines != Int.MAX_VALUE
                tv.maxLines = if (open) Int.MAX_VALUE else 7
                more.text = if (open) "Згорнути" else "Показати повністю"
            }
            addView(more)
        }
    }

    private fun attributes(p: Product): View = ui.column().apply {
        p.attributes.forEachIndexed { i, a ->
            val r = ui.row(12).apply {
                gravity = Gravity.TOP
                setPadding(0, ui.dp(9), 0, ui.dp(9))
                addView(ui.muted(a.name, 13.5f), ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ui.text(a.value, 13.5f, Palette.text, medium = true), ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
            }
            if (i > 0) addView(View(act).apply { setBackgroundColor(Palette.border) }, ui.lp(h = 1))
            addView(r, ui.lp())
        }
    }
}
