package ua.faric.caravanlean.ui

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import ua.faric.caravanlean.R

/** Пошук по всьому каталогу: назва, артикул, бренд, група. Результати — тією ж сіткою плиток. */
class SearchScreen(act: MainActivity) : GridScreen(act) {
    override val title = "Пошук"
    private var query = ""
    private val handler = Handler(Looper.getMainLooper())
    private val runSearch = Runnable { reload(); if (isBuilt) list.scrollToPosition(0) }
    private val field: EditText by lazy { buildField() }
    private val head: View by lazy { buildHead() }

    private fun buildField(): EditText = ui.field("Назва, артикул або бренд").apply {
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setPadding(ui.dp(46), ui.dp(13), ui.dp(46), ui.dp(13))
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                handler.removeCallbacks(runSearch)
                handler.postDelayed(runSearch, 220)
            }
        })
        setOnEditorActionListener { _, _, _ -> act.hideKeyboard(); true }
    }

    private fun buildHead(): View = FrameLayout(act).apply {
        setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(6))
        addView(field, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(ui.icon(R.drawable.ic_search, Palette.muted, 20), FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER_VERTICAL or Gravity.START).apply {
            marginStart = ui.dp(18)
        })
        addView(ui.iconButton(R.drawable.ic_close, Palette.muted, desc = "Очистити") { field.setText("") },
            FrameLayout.LayoutParams(ui.dp(48), ui.dp(48), Gravity.CENTER_VERTICAL or Gravity.END).apply { marginEnd = ui.dp(4) })
    }

    override fun cells(): List<Cell> {
        val out = ArrayList<Cell>()
        out += Cell.Block("search", head)
        val c = catalog
        if (query.isBlank()) {
            if (c.isEmpty) return out + Cell.Empty("Каталог ще порожній", "Шукати поки нічого.")
            out += Cell.Label("Групи")
            c.topLevel.forEach { out += Cell.Group(it, c.countIn(it.id)) }
            return out
        }
        val found = c.search(query)
        if (found.isEmpty()) {
            out += Cell.Empty("Нічого не знайдено", "Спробуй коротше слово або артикул.")
        } else {
            out += Cell.Label("Знайдено: ${GridAdapter.goods(found.size)}${if (found.size >= 200) " (перші 200)" else ""}")
            found.forEach { out += Cell.Item(it) }
        }
        return out
    }

    override fun onShown() {
        if (query.isBlank()) {
            field.requestFocus()
            field.post { act.getSystemService(InputMethodManager::class.java)?.showSoftInput(field, 0) }
        }
    }
}
