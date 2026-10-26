package ua.faric.caravanlean.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors
import ua.faric.caravanlean.BuildConfig
import ua.faric.caravanlean.core.Catalog
import ua.faric.caravanlean.core.CatalogJson
import ua.faric.caravanlean.core.Product
import ua.faric.caravanlean.core.ProductPage
import ua.faric.caravanlean.net.Http

/**
 * Звідки береться каталог. Застосунок бачить лише цей інтерфейс — тож коли магазин дасть прямий доступ
 * до своєї бази, додається ще одне джерело (RemoteJson на їхній сервер), а екрани не міняються.
 */
interface CatalogSource {
    /** Людською мовою, для «Про застосунок». */
    val label: String
    fun load(): Catalog
}

/** Знімок, вкладений у APK під час збірки (GitHub пропарсив сайт). Працює без інтернету. */
class BundledSnapshot(private val ctx: Context) : CatalogSource {
    override val label = "знімок у застосунку"
    override fun load(): Catalog = try {
        ctx.assets.open(PATH).bufferedReader(Charsets.UTF_8).use { CatalogJson.parse(it.readText()) }
    } catch (e: java.io.FileNotFoundException) {
        Catalog.EMPTY
    }

    companion object { const val PATH = "catalog/catalog.json" }
}

/** Останній отриманий каталог — на телефоні, щоб наступний запуск був миттєвим і без інтернету. */
class LocalCache(ctx: Context) : CatalogSource {
    private val file = File(ctx.filesDir, "catalog.json")
    override val label = "збережено на телефоні"
    override fun load(): Catalog = if (file.isFile) CatalogJson.parse(file.readText()) else Catalog.EMPTY
    fun save(c: Catalog) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(CatalogJson.write(c))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

/**
 * Каталог у форматі catalog.json за адресою в інтернеті.
 * Зараз — свіжий знімок з GitHub; потім — та сама точка, але на сервері магазину (дані прямо з бази).
 */
class RemoteJson(private val url: String, override val label: String) : CatalogSource {
    override fun load(): Catalog {
        val r = Http.get(url, accept = "application/json,*/*;q=0.5", maxBytes = 40 shl 20)
        if (!r.ok) throw java.io.IOException("Сервер відповів ${r.code}")
        return CatalogJson.parse(r.text())
    }
}

/** Один товар просто зі сторінки сайту: ціна й наявність «зараз». */
object LiveProduct {
    fun refresh(p: Product): Product? {
        if (p.url.isBlank()) return null
        val r = Http.get(p.url)
        if (!r.ok) return null
        return ProductPage.refresh(p, r.text(), r.finalUrl)
    }
}

object CatalogRepo {
    private val bg = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()
    private lateinit var cache: LocalCache
    private lateinit var bundled: BundledSnapshot

    @Volatile var catalog: Catalog = Catalog.EMPTY
        private set
    /** Звідки поточні дані: «знімок у застосунку», «оновлено з сервера» … */
    var origin: String = ""
        private set
    var loading = false
        private set
    var lastError: String? = null
        private set

    /** Адреса свіжого знімка (порожня — оновлення лише з APK і живими цінами з сайту). */
    val remoteUrl: String get() = BuildConfig.CATALOG_URL

    fun init(ctx: Context) {
        if (::cache.isInitialized) return
        cache = LocalCache(ctx.applicationContext)
        bundled = BundledSnapshot(ctx.applicationContext)
        loading = true
        bg.execute {
            val fromCache = runCatching { cache.load() }.getOrDefault(Catalog.EMPTY)
            val fromApk = runCatching { bundled.load() }.getOrDefault(Catalog.EMPTY)
            // новіший з двох (новий APK може привезти свіжіший знімок, ніж збережений)
            val pick = if (!fromCache.isEmpty && fromCache.scrapedAt >= fromApk.scrapedAt) fromCache to cache.label else fromApk to bundled.label
            main.post {
                catalog = pick.first
                origin = pick.second
                loading = false
                notifyChanged()
            }
        }
    }

    fun refresh(done: (ok: Boolean, message: String) -> Unit) {
        val url = remoteUrl
        if (url.isBlank()) {
            done(false, "Сервер каталогу ще не підключено — ціни оновлюються з сайту, коли відкриваєш товар.")
            return
        }
        loading = true
        notifyChanged()
        bg.execute {
            val res = runCatching { RemoteJson(url, "оновлено з сервера").load() }
            main.post {
                loading = false
                res.onSuccess { c ->
                    if (c.isEmpty) {
                        lastError = "Сервер віддав порожній каталог"
                    } else {
                        catalog = c
                        origin = "оновлено з сервера"
                        lastError = null
                        bg.execute { runCatching { cache.save(c) } }
                    }
                }.onFailure { lastError = it.message ?: it.javaClass.simpleName }
                notifyChanged()
                done(res.isSuccess && lastError == null, lastError ?: "Каталог оновлено: ${catalog.products.size} товарів")
            }
        }
    }

    /** Підтягнути свіжу ціну/наявність товару з сайту. [done] отримує оновлений товар або null. */
    fun refreshProduct(p: Product, done: (Product?) -> Unit) {
        bg.execute {
            val fresh = runCatching { LiveProduct.refresh(p) }.getOrNull()
            main.post {
                if (fresh != null && fresh != catalog.product(p.id)) {
                    catalog = catalog.withProduct(fresh)
                    val snap = catalog
                    bg.execute { runCatching { cache.save(snap) } }
                    notifyChanged()
                }
                done(fresh)
            }
        }
    }

    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }
    private fun notifyChanged() { listeners.toList().forEach { it() } }
}
