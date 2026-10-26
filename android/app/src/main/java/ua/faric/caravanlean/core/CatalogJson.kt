package ua.faric.caravanlean.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * catalog.json ⇄ Catalog. Формат пише scripts/scrape_catalog.py (schema 1);
 * у майбутньому той самий формат може віддавати сервер магазину — тоді застосунок не міняється.
 */
object CatalogJson {
    const val SCHEMA = 1

    fun parse(text: String): Catalog {
        val o = JSONObject(text)
        val schema = o.optInt("schema", SCHEMA)
        require(schema <= SCHEMA) { "Каталог новішого формату ($schema) — онови застосунок" }
        val currency = o.optString("currency", "UAH").ifBlank { "UAH" }
        val cats = o.optJSONArray("categories").objects().mapIndexed { i, c ->
            Category(
                id = c.getString("id"),
                name = c.optString("name").ifBlank { "Без назви" },
                url = c.str("url"),
                parentId = c.str("parentId"),
                image = c.str("image"),
                productCount = c.optInt("productCount", 0),
                order = c.optInt("order", i),
            )
        }
        val prods = o.optJSONArray("products").objects().mapNotNull { p ->
            val name = p.optString("name")
            if (name.isBlank()) return@mapNotNull null
            Product(
                id = p.getString("id"),
                name = name,
                url = p.optString("url"),
                categoryId = p.optString("categoryId"),
                price = p.num("price"),
                oldPrice = p.num("oldPrice"),
                currency = p.str("currency") ?: currency,
                available = if (p.isNull("available") || !p.has("available")) null else p.optBoolean("available"),
                images = p.optJSONArray("images").strings(),
                sku = p.optString("sku"),
                brand = p.optString("brand"),
                description = p.optString("description"),
                attributes = p.optJSONArray("attributes").objects().map { Attr(it.optString("name"), it.optString("value")) }
                    .filter { it.name.isNotBlank() && it.value.isNotBlank() },
            )
        }
        return Catalog(o.optString("source"), o.optString("scrapedAt"), currency, cats, prods)
    }

    fun write(c: Catalog): String {
        val o = JSONObject()
        o.put("schema", SCHEMA)
        o.put("source", c.source)
        o.put("scrapedAt", c.scrapedAt)
        o.put("currency", c.currency)
        o.put("categories", JSONArray().apply {
            c.categories.forEach { k ->
                put(JSONObject().apply {
                    put("id", k.id); put("name", k.name); putOpt("url", k.url); putOpt("parentId", k.parentId)
                    putOpt("image", k.image); put("productCount", k.productCount); put("order", k.order)
                })
            }
        })
        o.put("products", JSONArray().apply {
            c.products.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("url", p.url); put("categoryId", p.categoryId)
                    putOpt("price", p.price); putOpt("oldPrice", p.oldPrice); put("currency", p.currency)
                    putOpt("available", p.available)
                    put("images", JSONArray(p.images)); put("sku", p.sku); put("brand", p.brand)
                    put("description", p.description)
                    put("attributes", JSONArray().apply {
                        p.attributes.forEach { a -> put(JSONObject().put("name", a.name).put("value", a.value)) }
                    })
                })
            }
        })
        return o.toString()
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

    private fun JSONObject.str(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.num(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
}
