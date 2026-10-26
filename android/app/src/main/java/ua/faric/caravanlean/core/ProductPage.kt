package ua.faric.caravanlean.core

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Живе оновлення товару зі сторінки сайту на телефоні: ціна, стара ціна, наявність, фото, опис.
 * Те саме правило, що в scripts/scrape_catalog.py (schema.org Product → microdata → og:/HTML).
 * Повертає null, якщо сторінка не схожа на товар (тоді лишаються дані знімка).
 */
object ProductPage {

    fun refresh(base: Product, html: String, pageUrl: String = base.url): Product? {
        val doc = Jsoup.parse(html, pageUrl)
        val ld = jsonLd(doc).firstOrNull { it.isType("Product") }
        val micro = doc.selectFirst("[itemtype*=schema.org/Product]")
        val ogType = doc.selectFirst("meta[property=og:type]")?.attr("content")
        if (ld == null && micro == null && ogType != "product") return null

        var price: Double? = null
        var currency: String? = null
        var available: Boolean? = null
        var images = emptyList<String>()
        var description = ""
        if (ld != null) {
            var offers: Any? = ld.opt("offers")
            if (offers is JSONArray) offers = offers.optJSONObject(0)
            if (offers is JSONObject) {
                price = parsePrice(offers.optString("price").ifBlank { offers.optString("lowPrice") })
                currency = offers.optString("priceCurrency").ifBlank { null }
                val av = offers.optString("availability")
                if (av.isNotBlank()) available = av.contains("InStock") || av.contains("PreOrder")
            }
            images = when (val img = ld.opt("image")) {
                is JSONArray -> (0 until img.length()).mapNotNull { i ->
                    img.optString(i).takeIf { it.isNotBlank() && !it.startsWith("{") } ?: img.optJSONObject(i)?.optString("url")
                }
                is JSONObject -> listOfNotNull(img.optString("url").ifBlank { null })
                is String -> listOf(img)
                else -> emptyList()
            }
            description = Jsoup.parse(ld.optString("description")).text()
        }
        if (price == null) {
            val el = doc.selectFirst("[itemprop=price]") ?: doc.selectFirst("meta[property=product:price:amount]")
            price = parsePrice(el?.attr("content")?.ifBlank { null } ?: el?.text())
        }
        if (images.isEmpty()) {
            images = (listOf(doc.selectFirst("meta[property=og:image]")?.attr("content")) +
                doc.select("[itemprop=image]").map { it.attr("src").ifBlank { it.attr("content") } })
                .filterNotNull().filter { it.isNotBlank() }
        }
        if (description.isBlank()) description = doc.selectFirst("[itemprop=description]")?.text().orEmpty()
        val old = doc.selectFirst("[class*=old-price], [class*=price-old], [class*=oldPrice], del")?.text()?.let(::parsePrice)

        val absImages = images.filter { it.isNotBlank() }.map { absolutize(pageUrl, it.trim()) }.distinct().take(12)
        val newPrice = price ?: base.price
        return base.copy(
            price = newPrice,
            oldPrice = old?.takeIf { newPrice != null && it > newPrice } ?: base.oldPrice?.takeIf { newPrice != null && it > newPrice },
            currency = currency ?: base.currency,
            available = available ?: base.available,
            images = absImages.ifEmpty { base.images },
            description = description.take(4000).ifBlank { base.description },
        )
    }

    fun parsePrice(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        var s = raw.replace(Regex("[^\\d,.]"), "").replace(',', '.')
        if (s.count { it == '.' } > 1) {
            val last = s.substringAfterLast('.')
            s = if (last.length == 3) s.replace(".", "") else s.substringBeforeLast('.').replace(".", "") + "." + last
        }
        return s.toDoubleOrNull()
    }

    private fun jsonLd(doc: Document): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        for (tag in doc.select("script[type=application/ld+json]")) {
            val parsed: Any = try {
                val raw = tag.data().trim()
                if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw)
            } catch (e: Exception) {
                continue
            }
            val stack = ArrayDeque<Any>(listOf(parsed))
            while (stack.isNotEmpty()) {
                when (val node = stack.removeFirst()) {
                    is JSONArray -> for (i in 0 until node.length()) stack.addLast(node.get(i))
                    is JSONObject -> {
                        node.opt("@graph")?.let { g -> stack.addLast(g) }
                        out.add(node)
                    }
                }
            }
        }
        return out
    }

    private fun JSONObject.isType(name: String): Boolean = when (val t = opt("@type")) {
        is String -> t == name
        is JSONArray -> (0 until t.length()).any { t.optString(it) == name }
        else -> false
    }

    fun absolutize(page: String, u: String): String = when {
        u.startsWith("http") -> u
        u.startsWith("//") -> "https:$u"
        else -> try {
            java.net.URI(page).resolve(u.replace(" ", "%20")).toString()
        } catch (e: Exception) {
            if (u.startsWith("/")) Regex("^(https?://[^/]+)").find(page)?.value.orEmpty() + u else u
        }
    }
}
