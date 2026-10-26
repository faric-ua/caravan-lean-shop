package ua.faric.caravanlean.ui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import ua.faric.caravanlean.R
import ua.faric.caravanlean.core.Catalog
import ua.faric.caravanlean.data.Cart
import ua.faric.caravanlean.data.CatalogRepo
import ua.faric.caravanlean.net.Images

/** Вкладки нижнього меню. У кожної — свій стек екранів (як у звичайних застосунках-магазинах). */
enum class Tab(val label: String, val icon: Int) {
    CATALOG("Каталог", R.drawable.ic_grid),
    SEARCH("Пошук", R.drawable.ic_search),
    CART("Кошик", R.drawable.ic_cart),
    MORE("Ще", R.drawable.ic_info),
}

/** Екран: будує свій View один раз (тож прокрутка зберігається), оновлюється, коли змінились дані. */
abstract class Screen(val act: MainActivity) {
    val ui: Ui get() = act.ui
    val catalog: Catalog get() = CatalogRepo.catalog
    open val title: String = ""
    /** Значки праворуч у шапці. */
    open fun actions(): List<View> = emptyList()
    abstract fun create(): View
    open fun onCatalogChanged() {}
    open fun onCartChanged() {}
    open fun onShown() {}

    internal var built: View? = null
    fun view(): View = built ?: create().also { built = it }
}

class MainActivity : Activity() {
    lateinit var ui: Ui
        private set
    private lateinit var content: FrameLayout
    private lateinit var barTitle: TextView
    private lateinit var barLead: FrameLayout
    private lateinit var barActions: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var toastView: TextView
    private val tabViews = HashMap<Tab, Pair<android.widget.ImageView, TextView>>()
    private lateinit var cartBadge: TextView

    private val stacks = HashMap<Tab, MutableList<Screen>>()
    var tab: Tab = Tab.CATALOG
        private set

    private val catalogListener = { stacks.values.flatten().forEach { it.onCatalogChanged() }; refreshBar() }
    private val cartListener = {
        updateBadge()
        stacks.values.flatten().forEach { it.onCartChanged() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Images.init(this)
        Cart.init(this)
        CatalogRepo.init(this)
        ui = Ui(this)
        edgeToEdge()
        setContentView(buildRoot())
        stacks[Tab.CATALOG] = mutableListOf(HomeScreen(this))
        stacks[Tab.SEARCH] = mutableListOf(SearchScreen(this))
        stacks[Tab.CART] = mutableListOf(CartScreen(this))
        stacks[Tab.MORE] = mutableListOf(AboutScreen(this))
        CatalogRepo.listen(catalogListener)
        Cart.listen(cartListener)
        show()
        updateBadge()
        // Android 13+ (і обов'язково 16+): «назад» приходить сюди, а не в onBackPressed.
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if (!back()) finish()
            }
        }
    }

    /** Малюємо під системними панелями на всіх версіях — тоді відступи однакові (Android 15+ інакше не вміє). */
    @Suppress("DEPRECATION")
    private fun edgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        window.statusBarColor = Palette.transparent
        window.navigationBarColor = Palette.transparent
    }

    /** Поворот / зміна ширини: екрани перебудовуються (кількість колонок сітки залежить від ширини). */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        stacks.values.flatten().forEach { it.built = null }
        content.removeAllViews()
        show()
    }

    override fun onDestroy() {
        CatalogRepo.unlisten(catalogListener)
        Cart.unlisten(cartListener)
        super.onDestroy()
    }

    // ---------- розмітка ----------

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.bg)
        }
        topBar = ui.row(4).apply {
            minimumHeight = ui.dp(56)
            setPadding(ui.dp(6), 0, ui.dp(6), 0)
            setBackgroundColor(Palette.bg)
        }
        barLead = FrameLayout(this)
        barTitle = ui.title("", 19f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        barActions = ui.row(2)
        topBar.addView(barLead)
        topBar.addView(barTitle, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(barActions)

        content = FrameLayout(this)
        toastView = ui.text("", 14.5f, Palette.onAccent).apply {
            setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
            background = ui.rounded(Palette.toastBg, radiusDp = 14)
            visibility = View.GONE
            elevation = ui.dpf(6f)
        }

        val stage = FrameLayout(this)
        stage.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        stage.addView(toastView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(ui.dp(12), 0, ui.dp(12), ui.dp(12))
        })

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Palette.surface)
            elevation = ui.dpf(8f)
        }
        Tab.entries.forEach { t ->
            val item = ui.column(3).apply {
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(8), 0, ui.dp(8))
                background = ui.pressable(null, 0)
                contentDescription = t.label
                setOnClickListener { onTab(t) }
            }
            val iconBox = FrameLayout(this)
            val icon = ui.icon(t.icon, Palette.muted, 24)
            iconBox.addView(icon, FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER))
            if (t == Tab.CART) {
                cartBadge = ui.badge(0)
                iconBox.addView(cartBadge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                    setMargins(0, -ui.dp(4), -ui.dp(10), 0)
                })
                iconBox.clipChildren = false
                item.clipChildren = false
            }
            val label = ui.text(t.label, 11.5f, Palette.muted, medium = true)
            item.addView(iconBox, LinearLayout.LayoutParams(ui.dp(40), ui.dp(26)))
            item.addView(label)
            bottomBar.addView(item, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            tabViews[t] = icon to label
        }

        root.addView(topBar, ui.lp())
        root.addView(stage, ui.lp(h = 0, weight = 1f))
        root.addView(bottomBar, ui.lp())

        // Android 15+: застосунок малюється під системними панелями — відступи додаємо самі.
        root.setOnApplyWindowInsetsListener { _, insets ->
            val top: Int
            val bottom: Int
            val ime: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val sb = insets.getInsets(WindowInsets.Type.systemBars())
                top = sb.top
                bottom = sb.bottom
                ime = insets.getInsets(WindowInsets.Type.ime()).bottom
            } else {
                top = insets.systemWindowInsetTop // застаріле, але це Android 8–10
                bottom = insets.systemWindowInsetBottom
                ime = 0
            }
            topBar.setPadding(ui.dp(6), top, ui.dp(6), 0)
            val keyboardUp = ime > bottom
            bottomBar.visibility = if (keyboardUp) View.GONE else View.VISIBLE
            bottomBar.setPadding(0, 0, 0, bottom)
            stage.setPadding(0, 0, 0, if (keyboardUp) ime else 0)
            insets
        }
        return root
    }

    // ---------- навігація ----------

    fun push(s: Screen) {
        hideKeyboard()
        stacks.getValue(tab).add(s)
        show()
    }

    fun back(): Boolean {
        hideKeyboard()
        val st = stacks.getValue(tab)
        if (st.size > 1) {
            st.removeAt(st.size - 1)
            show()
            return true
        }
        if (tab != Tab.CATALOG) {
            onTab(Tab.CATALOG)
            return true
        }
        return false
    }

    fun onTab(t: Tab) {
        hideKeyboard()
        if (t == tab) {
            // повторний дотик — на початок вкладки
            val st = stacks.getValue(t)
            if (st.size > 1) {
                while (st.size > 1) st.removeAt(st.size - 1)
                show()
            }
            return
        }
        tab = t
        show()
    }

    private fun show() {
        val s = stacks.getValue(tab).last()
        val v = s.view()
        if (content.childCount != 1 || content.getChildAt(0) !== v) {
            content.removeAllViews()
            (v.parent as? ViewGroup)?.removeView(v)
            content.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        refreshBar()
        tabViews.forEach { (t, pair) ->
            val c = if (t == tab) Palette.accent else Palette.muted
            pair.first.imageTintList = android.content.res.ColorStateList.valueOf(c)
            pair.second.setTextColor(c)
        }
        s.onShown()
    }

    fun refreshBar() {
        val st = stacks[tab] ?: return
        val s = st.last()
        barLead.removeAllViews()
        if (st.size > 1) {
            barLead.addView(ui.iconButton(R.drawable.ic_back, desc = "Назад") { back() })
        } else {
            barLead.addView(ui.icon(R.drawable.ic_store, Palette.accent, 26).apply {
                layoutParams = FrameLayout.LayoutParams(ui.dp(26), ui.dp(26), Gravity.CENTER).apply { setMargins(ui.dp(10), 0, ui.dp(8), 0) }
            })
        }
        barTitle.text = s.title
        barActions.removeAllViews()
        s.actions().forEach { barActions.addView(it) }
    }

    private fun updateBadge() {
        val n = Cart.count
        cartBadge.text = if (n > 99) "99+" else n.toString()
        cartBadge.visibility = if (n > 0) View.VISIBLE else View.GONE
    }

    // ---------- сповіщення (замість сірих тостів Android) ----------

    private val hideToast = Runnable {
        toastView.animate().alpha(0f).translationY(ui.dpf(12f)).setDuration(180).withEndAction { toastView.visibility = View.GONE }.start()
    }

    fun toast(msg: String, longer: Boolean = false) {
        toastView.removeCallbacks(hideToast)
        toastView.text = msg
        toastView.alpha = 0f
        toastView.translationY = ui.dpf(12f)
        toastView.visibility = View.VISIBLE
        toastView.animate().alpha(1f).translationY(0f).setDuration(180).start()
        toastView.postDelayed(hideToast, if (longer) 4200L else 2400L)
    }

    fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!back()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
