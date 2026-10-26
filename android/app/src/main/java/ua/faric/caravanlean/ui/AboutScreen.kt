package ua.faric.caravanlean.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import ua.faric.caravanlean.BuildConfig
import ua.faric.caravanlean.R
import ua.faric.caravanlean.data.CatalogRepo

/** «Ще»: звідки дані, кнопка оновлення, як це працює (для замовника), версія. */
class AboutScreen(act: MainActivity) : Screen(act) {
    override val title = "Про застосунок"
    private lateinit var body: LinearLayout

    override fun create(): View {
        body = ui.column(14).apply { setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(32)) }
        render()
        return ScrollView(act).apply { addView(body) }
    }

    override fun onCatalogChanged() { if (::body.isInitialized) render() }

    private fun render() {
        body.removeAllViews()
        val c = catalog

        body.addView(ui.row(14).apply {
            addView(ui.icon(R.drawable.ic_store, Palette.accent, 40))
            addView(ui.column(4).apply {
                addView(ui.title("Caravan Lean", 20f))
                addView(ui.muted("Демо-застосунок магазину shop.caravan-lean.com.ua", 13.5f))
            }, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }, ui.lp())

        body.addView(ui.label("Дані каталогу"))
        body.addView(ui.card(16).apply {
            val gap = ui.column(8)
            if (c.isEmpty) {
                gap.addView(ui.text("Каталог ще порожній", 15f, Palette.text, medium = true))
            } else {
                gap.addView(kv("Звідки", CatalogRepo.origin))
                gap.addView(kv("Знімок сайту", Dates.full(c.scrapedAt)))
                gap.addView(kv("Груп", c.categories.size.toString()))
                gap.addView(kv("Товарів", c.products.size.toString()))
                gap.addView(kv("Сайт", c.source.removePrefix("https://")))
            }
            CatalogRepo.lastError?.let { gap.addView(ui.text("Останнє оновлення не вдалось: $it", 13f, Palette.warning)) }
            addView(gap, ui.lp())
            addView(ui.spacer(12))
            addView(ui.secondary(if (CatalogRepo.loading) "Оновлюю…" else "Оновити каталог", R.drawable.ic_refresh) {
                CatalogRepo.refresh { _, msg -> act.toast(msg, longer = true) }
            }, ui.lp())
            addView(ui.muted("Ціна й наявність товару оновлюються з сайту щоразу, коли відкриваєш його картку.", 12.5f).apply {
                setPadding(0, ui.dp(8), 0, 0)
            })
        }, ui.lp())

        body.addView(ui.label("Як це працює"))
        body.addView(ui.card(16).apply {
            addView(ui.column(10).apply {
                addView(step("1", "Сайт магазину", "Групи, підгрупи, товари, ціни, фото — як є зараз."))
                addView(arrow())
                addView(step("2", "Зараз: парсер  ·  Потім: база магазину", "Зараз GitHub читає сайт і складає каталог. Далі — застосунок бере дані напряму з бази через сервер, без парсингу."))
                addView(arrow())
                addView(step("3", "Цей застосунок", "Свої екрани: плитки, пошук, кошик, оформлення. Жодного браузера — сайт не відкривається."))
            }, ui.lp())
        }, ui.lp())

        body.addView(ui.label("Версія"))
        body.addView(ui.card(16).apply {
            addView(kv("Застосунок", "Caravan Lean ${BuildConfig.VERSION_NAME} · збірка ${BuildConfig.VERSION_CODE}"))
            addView(ui.spacer(8))
            addView(kv("Код", BuildConfig.GIT_COMMIT))
        }, ui.lp())
    }

    private fun kv(k: String, v: String): View = ui.row(12).apply {
        gravity = Gravity.TOP
        addView(ui.muted(k, 13.5f), ui.lp(ui.dp(110), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(ui.text(v, 13.5f, Palette.text, medium = true), ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun step(n: String, head: String, text: String): View = ui.row(12).apply {
        gravity = Gravity.TOP
        addView(ui.text(n, 14f, Palette.onAccent, medium = true).apply {
            gravity = Gravity.CENTER
            background = ui.rounded(Palette.accent, radiusDp = 14)
        }, LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)))
        addView(ui.column(4).apply {
            addView(ui.text(head, 14.5f, Palette.text, medium = true))
            addView(ui.muted(text, 13f))
        }, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun arrow(): View = ui.text("↓", 16f, Palette.faint).apply { setPadding(ui.dp(8), 0, 0, 0) }
}
