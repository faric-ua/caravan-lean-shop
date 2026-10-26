package ua.faric.caravanlean.ui

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import ua.faric.caravanlean.R
import ua.faric.caravanlean.core.Money
import ua.faric.caravanlean.data.Cart
import ua.faric.caravanlean.net.Images

/** Кошик: товари з кількістю, разом, «Оформити». Живе на телефоні; замовлення — демо. */
class CartScreen(act: MainActivity) : Screen(act) {
    override val title = "Кошик"
    private lateinit var body: LinearLayout

    override fun actions(): List<View> =
        if (Cart.count > 0) listOf(ui.iconButton(R.drawable.ic_trash, Palette.muted, desc = "Очистити кошик") { Cart.clear(); act.toast("Кошик очищено") })
        else emptyList()

    override fun create(): View {
        body = ui.column(10).apply { setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(24)) }
        render()
        return ScrollView(act).apply { addView(body) }
    }

    override fun onCartChanged() { if (::body.isInitialized) render(); if (act.tab == Tab.CART) act.refreshBar() }
    override fun onCatalogChanged() { if (::body.isInitialized) render() }

    private fun render() {
        body.removeAllViews()
        val entries = Cart.entries(catalog)
        if (entries.isEmpty()) {
            body.addView(ui.column(12).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(ui.dp(24), ui.dp(64), ui.dp(24), ui.dp(24))
                addView(ui.icon(R.drawable.ic_cart, Palette.faint, 48))
                addView(ui.title("Кошик порожній", 18f))
                addView(ui.muted("Додай товари з каталогу — кнопкою «+» на плитці або «У кошик» у картці товару.", 14f).apply { gravity = Gravity.CENTER })
                addView(ui.spacer(4))
                addView(ui.primary("До каталогу", R.drawable.ic_grid) { act.onTab(Tab.CATALOG) })
            }, ui.lp())
            return
        }
        entries.forEach { (p, q) ->
            val row = ui.row(12).apply {
                setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
                background = ui.rounded(Palette.surface, Palette.border)
                gravity = Gravity.TOP
            }
            val img = ImageView(act).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                ui.clipRounded(this, 12)
                setOnClickListener { act.push(ProductScreen(act, p.id)) }
            }
            Images.load(img, p.image, ui.dp(144), Palette.placeholder)
            row.addView(img, LinearLayout.LayoutParams(ui.dp(76), ui.dp(76)))
            val col = ui.column(8)
            col.addView(ui.text(p.name, 14.5f, Palette.text, lines = 2).apply { setOnClickListener { act.push(ProductScreen(act, p.id)) } })
            col.addView(ui.price(p))
            val bottom = ui.row(8)
            bottom.addView(ui.stepper(q) { n -> Cart.set(p.id, n) })
            bottom.addView(View(act), ui.lp(0, 1, 1f))
            bottom.addView(ui.iconButton(R.drawable.ic_trash, Palette.muted, desc = "Прибрати") { Cart.set(p.id, 0) })
            col.addView(bottom, ui.lp())
            row.addView(col, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(row, ui.lp())
        }
        val sum = ui.card(16).apply {
            val r = ui.row(8)
            r.addView(ui.text("Разом (${GridAdapter.goods(Cart.count)})", 15f, Palette.muted), ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(ui.text(Money.format(Cart.total(catalog), catalog.currency), 20f, Palette.text, medium = true).apply { fontFeatureSettings = "tnum" })
            addView(r, ui.lp())
            if (Cart.hasUnpriced(catalog)) addView(ui.muted("Ціну деяких товарів уточнить менеджер.", 12.5f).apply { setPadding(0, ui.dp(6), 0, 0) })
        }
        body.addView(sum, ui.lp().apply { topMargin = ui.dp(6) })
        body.addView(ui.primary("Оформити замовлення") { act.push(CheckoutScreen(act)) }, ui.lp())
    }
}

/** Оформлення — демо: поля як у справжньому магазині, але нікуди не відправляється. */
class CheckoutScreen(act: MainActivity) : Screen(act) {
    override val title = "Оформлення"
    private lateinit var body: LinearLayout

    override fun create(): View {
        body = ui.column(12).apply { setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(32)) }
        form()
        return ScrollView(act).apply { addView(body) }
    }

    private fun form() {
        body.removeAllViews()
        val name = ui.field("Ім'я та прізвище", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val phone = ui.field("Телефон", InputType.TYPE_CLASS_PHONE).apply { setText("+380") }
        val city = ui.field("Місто і відділення Нової пошти")
        val note = ui.field("Коментар (необов'язково)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply { minLines = 3; gravity = Gravity.TOP }
        body.addView(ui.text("Демо-оформлення: замовлення не відправляється. У справжньому застосунку воно піде просто в базу магазину.", 13.5f, Palette.warning))
        listOf(ui.label("Контакти"), name, phone, ui.label("Доставка"), city, note).forEach { body.addView(it, ui.lp()) }
        body.addView(ui.card(16).apply {
            val r = ui.row(8)
            r.addView(ui.text("До сплати", 15f, Palette.muted), ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(ui.text(Money.format(Cart.total(catalog), catalog.currency), 20f, Palette.text, medium = true))
            addView(r, ui.lp())
        }, ui.lp())
        body.addView(ui.primary("Підтвердити замовлення") {
            val digits = phone.text.toString().filter { it.isDigit() }
            when {
                name.text.isBlank() -> { name.requestFocus(); act.toast("Вкажи ім'я") }
                digits.length < 12 -> { phone.requestFocus(); act.toast("Телефон — у форматі +380XXXXXXXXX") }
                city.text.isBlank() -> { city.requestFocus(); act.toast("Вкажи місто і відділення") }
                else -> done(name.text.toString().trim())
            }
        }, ui.lp())
    }

    private fun done(who: String) {
        act.hideKeyboard()
        val total = Money.format(Cart.total(catalog), catalog.currency)
        val n = Cart.count
        Cart.clear()
        body.removeAllViews()
        body.addView(ui.column(12).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ui.dp(8), ui.dp(48), ui.dp(8), ui.dp(16))
            addView(ui.text("●", 40f, Palette.inStock))
            addView(ui.title("Дякуємо, ${who.substringBefore(' ')}!", 20f).apply { gravity = Gravity.CENTER })
            addView(ui.muted("Демо-замовлення сформовано: ${GridAdapter.goods(n)} на $total.", 14.5f).apply { gravity = Gravity.CENTER })
            addView(ui.card(14).apply {
                addView(ui.text("У справжньому застосунку тут замовлення потрапить у базу / CRM магазину, менеджер побачить його одразу, а покупець — статус у розділі «Мої замовлення».", 13.5f, Palette.muted))
            }, ui.lp())
            addView(ui.primary("Повернутись до каталогу", R.drawable.ic_grid) {
                act.back()
                act.onTab(Tab.CATALOG)
            }, ui.lp())
        }, ui.lp())
    }
}
