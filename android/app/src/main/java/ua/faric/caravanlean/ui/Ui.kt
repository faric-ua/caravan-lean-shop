package ua.faric.caravanlean.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import ua.faric.caravanlean.core.Money
import ua.faric.caravanlean.core.Product

/**
 * Будівельні блоки інтерфейсу. Кожен новий елемент — звідси («успадковуй стиль найближчого сусіда»).
 * Ніяких стандартних сірих діалогів і кнопок; кольори — лише з Palette.
 */
class Ui(val ctx: Context) {
    private val density = ctx.resources.displayMetrics.density

    fun dp(v: Int): Int = (v * density + 0.5f).toInt()
    fun dpf(v: Float): Float = v * density

    /** Колонок у сітці товарів — за реальною шириною екрана (телефон 2, поворот/планшет 3–4). */
    val gridColumns: Int get() = (ctx.resources.configuration.screenWidthDp / 180).coerceIn(2, 5)

    // ---------- фон ----------

    fun rounded(fill: Int, stroke: Int = Palette.transparent, radiusDp: Int = 16, strokeDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dpf(radiusDp.toFloat())
            if (stroke != Palette.transparent) setStroke(dp(strokeDp), stroke)
        }

    fun pressable(base: Drawable?, radiusDp: Int = 16): Drawable =
        RippleDrawable(ColorStateList.valueOf(Palette.ripple), base, rounded(Palette.text, radiusDp = radiusDp))

    /** Обрізати вміст (фото) по закругленню картки. */
    fun clipRounded(v: View, radiusDp: Int) {
        val r = dpf(radiusDp.toFloat())
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, r)
        }
        v.clipToOutline = true
    }

    // ---------- текст ----------

    fun text(s: CharSequence, sizeSp: Float = 15f, color: Int = Palette.text, medium: Boolean = false, lines: Int = 0): TextView =
        TextView(ctx).apply {
            text = s
            textSize = sizeSp
            setTextColor(color)
            typeface = if (medium) MEDIUM else REGULAR
            setLineSpacing(0f, 1.12f)
            includeFontPadding = false
            if (lines > 0) {
                maxLines = lines
                ellipsize = TextUtils.TruncateAt.END
            }
        }

    fun title(s: CharSequence, sizeSp: Float = 21f): TextView = text(s, sizeSp, Palette.text, medium = true)
    fun muted(s: CharSequence, sizeSp: Float = 13f): TextView = text(s, sizeSp, Palette.muted)

    /** Підпис розділу: дрібні великі літери з розрядкою. */
    fun label(s: String): TextView = text(s.uppercase(), 11.5f, Palette.muted, medium = true).apply { letterSpacing = 0.08f }

    fun price(p: Product, big: Boolean = false): LinearLayout = row(gapDp = 8).apply {
        gravity = Gravity.BOTTOM
        val now = text(Money.format(p.price, p.currency), if (big) 24f else 16f, if (p.oldPrice != null) Palette.sale else Palette.text, medium = true)
        now.fontFeatureSettings = "tnum"
        addView(now)
        if (p.oldPrice != null) {
            addView(text(Money.format(p.oldPrice, p.currency), if (big) 15f else 12.5f, Palette.faint).apply {
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                setPadding(0, 0, 0, dp(if (big) 3 else 1))
            })
        }
    }

    /** Наявність: кольорова крапка + слова (без галочок). */
    fun stock(available: Boolean?, sizeSp: Float = 12.5f): TextView {
        val (t, c) = when (available) {
            true -> "Є в наявності" to Palette.inStock
            false -> "Немає в наявності" to Palette.outOfStock
            null -> "Наявність уточнюйте" to Palette.warning
        }
        return text("●  $t", sizeSp, c)
    }

    // ---------- контейнери ----------

    fun column(gapDp: Int = 0): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        if (gapDp > 0) {
            dividerDrawable = gap(gapDp)
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        }
    }

    fun row(gapDp: Int = 0): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (gapDp > 0) {
            dividerDrawable = gap(gapDp)
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        }
    }

    fun card(padDp: Int = 16): LinearLayout = column().apply {
        background = rounded(Palette.surface, Palette.border)
        setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp))
    }

    private fun gap(d: Int) = GradientDrawable().apply { setSize(dp(d), dp(d)); setColor(Palette.transparent) }

    fun spacer(hDp: Int): View = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, dp(hDp)) }

    // ---------- значки й кнопки ----------

    fun icon(res: Int, color: Int = Palette.text, sizeDp: Int = 24): ImageView = ImageView(ctx).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    /** Кругла кнопка-значок (назад, пошук, сортування). */
    fun iconButton(res: Int, color: Int = Palette.text, fill: Int = Palette.transparent, desc: String, onClick: () -> Unit): FrameLayout =
        FrameLayout(ctx).apply {
            val size = dp(48)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = pressable(rounded(fill, radiusDp = 22), 22)
            contentDescription = desc
            addView(icon(res, color).apply { layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER) })
            setOnClickListener { onClick() }
        }

    /** Головна кнопка дії (повна ширина): акцентна заливка. */
    fun primary(label: String, iconRes: Int? = null, onClick: () -> Unit): LinearLayout = row(gapDp = 10).apply {
        gravity = Gravity.CENTER
        minimumHeight = dp(52)
        setPadding(dp(18), dp(12), dp(18), dp(12))
        background = pressable(rounded(Palette.accent, radiusDp = 14), 14)
        if (iconRes != null) addView(icon(iconRes, Palette.onAccent, 22))
        addView(text(label, 16f, Palette.onAccent, medium = true))
        setOnClickListener { onClick() }
    }

    /** Другорядна кнопка: світла з рамкою. */
    fun secondary(label: String, iconRes: Int? = null, onClick: () -> Unit): LinearLayout = row(gapDp = 8).apply {
        gravity = Gravity.CENTER
        minimumHeight = dp(46)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = pressable(rounded(Palette.surface, Palette.border, radiusDp = 14), 14)
        if (iconRes != null) addView(icon(iconRes, Palette.accent, 20))
        addView(text(label, 15f, Palette.accent, medium = true))
        setOnClickListener { onClick() }
    }

    /** Чіп-перемикач (сортування, «лише в наявності»). Вибраний — акцентна рамка й заливка, без галочки. */
    fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView = text(label, 13.5f, if (selected) Palette.accent else Palette.text, medium = selected).apply {
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = pressable(
            rounded(if (selected) Palette.accentSoft else Palette.surface, if (selected) Palette.accent else Palette.border, radiusDp = 18),
            18,
        )
        setOnClickListener { onClick() }
    }

    /** Лічильник на значку кошика. */
    fun badge(n: Int): TextView = text(if (n > 99) "99+" else n.toString(), 10.5f, Palette.onAccent, medium = true).apply {
        gravity = Gravity.CENTER
        minWidth = dp(18)
        setPadding(dp(5), dp(1), dp(5), dp(1))
        background = rounded(Palette.sale, radiusDp = 9)
        visibility = if (n > 0) View.VISIBLE else View.GONE
    }

    /** Степер кількості: − N +. */
    fun stepper(q: Int, onChange: (Int) -> Unit): LinearLayout = row().apply {
        background = rounded(Palette.surface, Palette.border, radiusDp = 14)
        val minus = iconButton(ua.faric.caravanlean.R.drawable.ic_minus, Palette.text, desc = "Менше") { onChange(q - 1) }
        val num = text(q.toString(), 16f, Palette.text, medium = true).apply {
            gravity = Gravity.CENTER
            minWidth = dp(32)
            fontFeatureSettings = "tnum"
        }
        val plus = iconButton(ua.faric.caravanlean.R.drawable.ic_plus, Palette.accent, desc = "Більше") { onChange(q + 1) }
        addView(minus); addView(num); addView(plus)
    }

    /** Поле вводу в стилі застосунку (світле, з рамкою; без стандартної сірої лінії Android). */
    fun field(hint: String, inputType: Int = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES): android.widget.EditText =
        android.widget.EditText(ctx).apply {
            this.hint = hint
            this.inputType = inputType
            textSize = 16f
            setTextColor(Palette.text)
            setHintTextColor(Palette.faint)
            typeface = REGULAR
            background = rounded(Palette.surface, Palette.border, radiusDp = 14)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            setSingleLine((inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0)
        }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight)

    companion object {
        val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
}
