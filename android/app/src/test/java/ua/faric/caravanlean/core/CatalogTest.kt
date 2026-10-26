package ua.faric.caravanlean.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    private fun res(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader(Charsets.UTF_8).readText()

    /** Той самий файл, що пише scripts/scrape_catalog.py (тест tests/test_scrape_catalog.py його породжує). */
    private val catalog by lazy { CatalogJson.parse(res("sample_catalog.json")) }

    @Test fun treeFromPythonOutput() {
        assertEquals(listOf("Меблі", "Кемпінг"), catalog.topLevel.map { it.name })
        val camping = catalog.topLevel[1]
        assertEquals(listOf("Тенти"), catalog.children(camping.id).map { it.name })
        assertEquals(1, catalog.productsIn(camping.id).size)
        val tents = catalog.children(camping.id)[0]
        assertEquals(listOf("Кемпінг", "Тенти"), catalog.path(tents.id).map { it.name })
    }

    @Test fun productFields() {
        val tent = catalog.products.first { it.sku == "CL-334" }
        assertEquals(2499.0, tent.price!!, 0.001)
        assertEquals(2999.0, tent.oldPrice!!, 0.001)
        assertEquals(17, tent.discountPercent)
        assertEquals(true, tent.available)
        assertEquals("Розмір", tent.attributes[0].name)
        assertEquals("https://shop.caravan-lean.com.ua/img/tent.jpg", tent.image)
        val chair = catalog.products.first { it.name.startsWith("Стілець") }
        assertNull(chair.available)
        assertNull(chair.discountPercent)
    }

    @Test fun jsonRoundTrip() {
        val again = CatalogJson.parse(CatalogJson.write(catalog))
        assertEquals(catalog.products, again.products)
        assertEquals(catalog.categories, again.categories)
        assertEquals(catalog.scrapedAt, again.scrapedAt)
    }

    @Test fun search() {
        assertEquals(listOf("Тент Caravan 3x4"), catalog.search("тент").map { it.name })
        assertEquals(1, catalog.search("CARAVAN 3X4").size)
        assertEquals(1, catalog.search("кемпінг").size) // за назвою групи
        assertEquals(1, catalog.search("стiлець".replace('i', 'і')).size)
        assertTrue(catalog.search("   ").isEmpty())
        assertTrue(catalog.search("неіснуючий").isEmpty())
    }

    @Test fun sorting() {
        val all = catalog.products
        assertEquals(listOf(850.0, 2499.0), Catalog.sorted(all, SortMode.PRICE_UP).map { it.price })
        assertEquals(listOf(2499.0, 850.0), Catalog.sorted(all, SortMode.PRICE_DOWN).map { it.price })
        val withNull = all + all[0].copy(id = "x", price = null)
        assertNull(Catalog.sorted(withNull, SortMode.PRICE_UP).last().price)
        assertNull(Catalog.sorted(withNull, SortMode.PRICE_DOWN).last().price)
    }

    @Test fun withProductReplaces() {
        val tent = catalog.products.first { it.sku == "CL-334" }
        val upd = catalog.withProduct(tent.copy(price = 1999.0))
        assertEquals(1999.0, upd.product(tent.id)!!.price!!, 0.001)
        assertEquals(catalog.products.size, upd.products.size)
    }

    @Test fun money() {
        assertEquals("2 499 ₴", Money.format(2499.0))
        assertEquals("1 234,50 ₴", Money.format(1234.5))
        assertEquals("850 ₴", Money.format(850.0, "UAH"))
        assertEquals("1 000 000 $", Money.format(1_000_000.0, "USD"))
        assertEquals("Ціну уточнюйте", Money.format(null))
    }

    @Test fun productPageRefresh() {
        val base = catalog.products.first { it.sku == "CL-334" }.copy(price = 1.0, available = false, images = emptyList())
        val fresh = ProductPage.refresh(base, res("product_jsonld.html"), "https://shop.caravan-lean.com.ua/kemping/tenty/tent-3x4/")
        assertNotNull(fresh)
        assertEquals(2499.0, fresh!!.price!!, 0.001)
        assertEquals(true, fresh.available)
        assertEquals("https://shop.caravan-lean.com.ua/img/tent2.jpg", fresh.images[1])
        assertEquals("Міцний & легкий тент", fresh.description)
        assertNull(ProductPage.refresh(base, "<html><body><h1>Контакти</h1></body></html>"))
    }

    @Test fun priceParsing() {
        assertEquals(1234.5, ProductPage.parsePrice("1 234,50 грн")!!, 0.001)
        assertEquals(1234567.0, ProductPage.parsePrice("1.234.567")!!, 0.001)
        assertNull(ProductPage.parsePrice("—"))
        assertFalse(Text.fold("Ґанок").contains('ґ'))
    }
}
