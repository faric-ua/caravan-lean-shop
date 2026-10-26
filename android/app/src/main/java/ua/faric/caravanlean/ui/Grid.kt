package ua.faric.caravanlean.ui

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ua.faric.caravanlean.R
import ua.faric.caravanlean.core.Category
import ua.faric.caravanlean.core.Product
import ua.faric.caravanlean.data.Cart
import ua.faric.caravanlean.net.Images

/** Що лежить у сітці екрана. Шапки й підписи — на всю ширину, плитки — по колонках. */
sealed class Cell {
    /** Готовий View (шапка екрана, рядок фільтрів, горизонтальний ряд). [key] — щоб не перебудовувати зайве. */
    class Block(val key: String, val view: View) : Cell()
    class Label(val text: String) : Cell()
    class Group(val category: Category, val count: Int) : Cell()
    class Item(val product: Product) : Cell()
    class Empty(val title: String, val hint: String) : Cell()
}

/** Картинка, що завжди квадратна (висота = ширині) — плитки в сітці однакові. */
class SquareImage(ctx: Context, private val ratio: Float = 1f) : ImageView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((w * ratio).toInt(), MeasureSpec.EXACTLY))
    }
}

class GridAdapter(
    private val ui: Ui,
    private val onGroup: (Category) -> Unit,
    private val onItem: (Product) -> Unit,
    private val onQuickAdd: (Product) -> Unit,
) : RecyclerView.Adapter<GridAdapter.Holder>() {

    var cells: List<Cell> = emptyList()
        private set

    fun submit(list: List<Cell>) {
        cells = list
        notifyDataSetChanged()
    }

    fun isFull(pos: Int): Boolean = cells.getOrNull(pos).let { it !is Cell.Group && it !is Cell.Item }

    class Holder(val root: FrameLayout) : RecyclerView.ViewHolder(root) {
        var bind: (Cell) -> Unit = {}
    }

    override fun getItemCount() = cells.size

    override fun getItemViewType(position: Int): Int = when (cells[position]) {
        is Cell.Block -> 0
        is Cell.Label -> 1
        is Cell.Group -> 2
        is Cell.Item -> 3
        is Cell.Empty -> 4
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val root = FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val h = Holder(root)
        when (viewType) {
            0 -> h.bind = { c ->
                val v = (c as Cell.Block).view
                if (v.parent !== root) {
                    (v.parent as? ViewGroup)?.removeView(v)
                    root.removeAllViews()
                    root.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            }
            1 -> {
                val tv = ui.label("").apply { setPadding(ui.dp(4), ui.dp(14), ui.dp(4), ui.dp(2)) }
                root.addView(tv)
                h.bind = { c -> tv.text = (c as Cell.Label).text.uppercase() }
            }
            2 -> groupTile(root, h)
            3 -> productTile(root, h)
            4 -> {
                val col = ui.column(8).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(ui.dp(24), ui.dp(48), ui.dp(24), ui.dp(48))
                }
                val t = ui.title("", 18f).apply { gravity = Gravity.CENTER }
                val m = ui.muted("", 14f).apply { gravity = Gravity.CENTER }
                col.addView(t); col.addView(m)
                root.addView(col)
                h.bind = { c -> (c as Cell.Empty).let { t.text = it.title; m.text = it.hint } }
            }
        }
        return h
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(cells[position])

    // ---------- плитка групи: фото 4:3, назва, кількість ----------

    private fun groupTile(root: FrameLayout, h: Holder) {
        val card = ui.column().apply {
            background = ui.pressable(ui.rounded(Palette.surface, Palette.border), 16)
            ui.clipRounded(this, 16)
        }
        val img = SquareImage(root.context, 0.72f).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val name = ui.text("", 15f, Palette.text, medium = true, lines = 2)
        val count = ui.muted("", 12.5f)
        val txt = ui.column(4).apply { setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(12)) }
        txt.addView(name); txt.addView(count)
        card.addView(img, ui.lp()); card.addView(txt, ui.lp())
        root.addView(card)
        h.bind = { c ->
            val g = (c as Cell.Group)
            name.text = g.category.name
            count.text = goods(g.count)
            Images.load(img, g.category.image, ui.dp(260), Palette.placeholder)
            card.setOnClickListener { onGroup(g.category) }
        }
    }

    // ---------- плитка товару: квадратне фото, знижка, назва, ціна, наявність, «+» у кошик ----------

    private fun productTile(root: FrameLayout, h: Holder) {
        val card = ui.column().apply {
            background = ui.pressable(ui.rounded(Palette.surface, Palette.border), 16)
            ui.clipRounded(this, 16)
        }
        val imgBox = FrameLayout(root.context)
        val img = SquareImage(root.context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val sale = ui.text("", 12f, Palette.onAccent, medium = true).apply {
            setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3))
            background = ui.rounded(Palette.sale, radiusDp = 10)
        }
        imgBox.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        imgBox.addView(sale, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            setMargins(ui.dp(8), ui.dp(8), 0, 0)
        })

        val name = ui.text("", 14f, Palette.text, lines = 2).apply { minLines = 2 }
        val priceBox = FrameLayout(root.context)
        val stock = ui.stock(null, 11.5f)
        val add = ui.iconButton(R.drawable.ic_plus, Palette.onAccent, Palette.accent, desc = "У кошик") {}
        val inCart = ui.text("", 12f, Palette.accent, medium = true)
        val bottom = ui.row(6)
        val left = ui.column(4)
        left.addView(priceBox); left.addView(stock)
        bottom.addView(left, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(add)
        val txt = ui.column(8).apply { setPadding(ui.dp(12), ui.dp(10), ui.dp(10), ui.dp(10)) }
        txt.addView(name); txt.addView(bottom); txt.addView(inCart)
        card.addView(imgBox, ui.lp()); card.addView(txt, ui.lp())
        root.addView(card)

        h.bind = { c ->
            val p = (c as Cell.Item).product
            name.text = p.name
            priceBox.removeAllViews()
            priceBox.addView(ui.price(p))
            val st = ui.stock(p.available, 11.5f)
            stock.text = st.text
            stock.setTextColor(st.currentTextColor)
            val d = p.discountPercent
            sale.visibility = if (d != null) View.VISIBLE else View.GONE
            sale.text = "−$d%"
            val q = Cart.qty(p.id)
            inCart.visibility = if (q > 0) View.VISIBLE else View.GONE
            inCart.text = "У кошику: $q"
            Images.load(img, p.image, ui.dp(240), Palette.placeholder)
            card.setOnClickListener { onItem(p) }
            add.alpha = if (p.available == false) 0.35f else 1f
            add.setOnClickListener { onQuickAdd(p) }
        }
    }

    companion object {
        fun goods(n: Int): String {
            val m10 = n % 10
            val m100 = n % 100
            val w = when {
                m10 == 1 && m100 != 11 -> "товар"
                m10 in 2..4 && m100 !in 12..14 -> "товари"
                else -> "товарів"
            }
            return "$n $w"
        }

        /** Сітка: [columns] колонок, шапки на всю ширину, однакові відступи між плитками. */
        fun attach(rv: RecyclerView, adapter: GridAdapter, ui: Ui, columns: Int = ui.gridColumns) {
            val lm = GridLayoutManager(rv.context, columns)
            lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (adapter.isFull(position)) columns else 1
            }
            rv.layoutManager = lm
            rv.adapter = adapter
            rv.clipToPadding = false
            val side = ui.dp(12)
            val half = ui.dp(5)
            rv.setPadding(side - half, ui.dp(4), side - half, ui.dp(20))
            rv.addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(out: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    out.set(half, half, half, half)
                }
            })
        }
    }
}

/** Горизонтальний ряд плиток (підгрупи, «зі знижкою», схожі товари). */
fun Ui.horizontalRow(views: List<View>, itemWidthDp: Int): View {
    val scroll = android.widget.HorizontalScrollView(ctx).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        setPadding(dp(1), 0, dp(1), dp(4))
    }
    val row = row(10)
    views.forEach { row.addView(it, LinearLayout.LayoutParams(dp(itemWidthDp), ViewGroup.LayoutParams.WRAP_CONTENT)) }
    scroll.addView(row)
    return scroll
}

/** Мала плитка підгрупи для горизонтального ряду: кругле фото + назва. */
fun Ui.subgroupChip(c: Category, count: Int, onClick: () -> Unit): View = column(6).apply {
    gravity = Gravity.CENTER_HORIZONTAL
    setPadding(dp(4), dp(8), dp(4), dp(8))
    background = pressable(null, 16)
    val img = SquareImage(ctx).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipRounded(this, 36)
    }
    addView(img, LinearLayout.LayoutParams(dp(72), dp(72)))
    addView(text(c.name, 12.5f, Palette.text, medium = true, lines = 2).apply { gravity = Gravity.CENTER })
    addView(muted(count.toString(), 11.5f))
    Images.load(img, c.image, dp(144), Palette.placeholder)
    setOnClickListener { onClick() }
}

/** Мала плитка товару для горизонтального ряду. */
fun Ui.miniProduct(p: Product, onClick: () -> Unit): View = column(6).apply {
    background = pressable(rounded(Palette.surface, Palette.border), 14)
    clipRounded(this, 14)
    val img = SquareImage(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    addView(img, lp())
    val t = column(4).apply { setPadding(dp(10), dp(4), dp(10), dp(10)) }
    t.addView(text(p.name, 13f, Palette.text, lines = 2).apply { minLines = 2 })
    t.addView(price(p))
    addView(t, lp())
    Images.load(img, p.image, dp(200), Palette.placeholder)
    setOnClickListener { onClick() }
}
