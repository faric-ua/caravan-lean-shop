"""Парсер каталогу: товар із JSON-LD і з microdata, дерево груп із «хлібних крихт»."""
import sys
import unittest
from pathlib import Path

from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
import scrape_catalog as sc  # noqa: E402

FIX = ROOT / "tests" / "fixtures"


def load(name, url):
    soup = BeautifulSoup((FIX / name).read_text(encoding="utf-8"), "lxml")
    return sc.parse_product(url, soup, sc.jsonld_items(soup))


class ProductTest(unittest.TestCase):
    def test_jsonld(self):
        p = load("product_jsonld.html", "https://shop.caravan-lean.com.ua/kemping/tenty/tent-3x4/")
        self.assertEqual(p["name"], "Тент Caravan 3x4")
        self.assertEqual(p["price"], 2499.0)
        self.assertEqual(p["oldPrice"], 2999.0)
        self.assertEqual(p["currency"], "UAH")
        self.assertTrue(p["available"])
        self.assertEqual(p["sku"], "CL-334")
        self.assertEqual(p["brand"], "Caravan")
        self.assertEqual(p["description"], "Міцний & легкий тент")
        self.assertEqual(p["images"][1], "https://shop.caravan-lean.com.ua/img/tent2.jpg")
        self.assertEqual([c[0] for c in p["crumbs"]], ["Кемпінг", "Тенти", "Тент Caravan 3x4"])
        self.assertEqual(p["attributes"][0], {"name": "Розмір", "value": "3×4 м"})

    def test_microdata(self):
        p = load("product_microdata.html", "https://shop.caravan-lean.com.ua/mebli/stilets/")
        self.assertEqual(p["name"], "Стілець складаний")
        self.assertEqual(p["price"], 850.0)
        self.assertEqual(p["images"], ["https://shop.caravan-lean.com.ua/img/chair.webp"])
        self.assertEqual(p["crumbs"][0][0], "Меблі")

    def test_tree(self):
        a = load("product_jsonld.html", "https://shop.caravan-lean.com.ua/kemping/tenty/tent-3x4/")
        b = load("product_microdata.html", "https://shop.caravan-lean.com.ua/mebli/stilets/")
        cat = sc.build({a["url"]: a, b["url"]: b}, {}, ["https://shop.caravan-lean.com.ua/mebli/", "https://shop.caravan-lean.com.ua/kemping/"])
        names = {c["name"]: c for c in cat["categories"]}
        self.assertEqual(set(names), {"Кемпінг", "Тенти", "Меблі"})
        self.assertEqual(names["Тенти"]["parentId"], names["Кемпінг"]["id"])
        self.assertIsNone(names["Кемпінг"]["parentId"])
        self.assertEqual(names["Кемпінг"]["productCount"], 1)
        self.assertEqual(cat["categories"][0]["name"], "Меблі")  # порядок як у меню
        tent = next(p for p in cat["products"] if p["sku"] == "CL-334")
        self.assertEqual(tent["categoryId"], names["Тенти"]["id"])
        self.assertNotIn("crumbs", tent)
        self.assertEqual(names["Тенти"]["image"], "https://shop.caravan-lean.com.ua/img/tent.jpg")

    def test_price_formats(self):
        self.assertEqual(sc.to_price("1 234,50 грн"), 1234.5)
        self.assertEqual(sc.to_price("1.234.567"), 1234567.0)
        self.assertIsNone(sc.to_price("—"))


if __name__ == "__main__":
    unittest.main()


class VirtueMartTest(unittest.TestCase):
    """Справжня сторінка товару shop.caravan-lean.com.ua (знята розвідкою 27.10)."""

    def test_product(self):
        soup = BeautifulSoup((FIX / "vm_product.html").read_text(encoding="utf-8"), "lxml")
        p = sc.parse_vm_product("https://shop.caravan-lean.com.ua/home/oborud/322617-detail.html", soup)
        self.assertEqual(p["name"], "Оконный упор")
        self.assertEqual(p["sku"], "322617")
        self.assertEqual(p["price"], 1421.0)
        self.assertEqual(len(p["images"]), 2)
        self.assertTrue(p["images"][0].startswith("https://shop.caravan-lean.com.ua/images/stories/virtuemart/product/"))
        self.assertIn("135", p["description"])
        self.assertTrue(p["available"])

    def test_paths(self):
        self.assertEqual(sc.cat_key("https://shop.caravan-lean.com.ua/home/woda.html"), "/home/woda")
        self.assertEqual(sc.cat_key("https://shop.caravan-lean.com.ua/home/woda/results,21-40.html"), "/home/woda")
        self.assertTrue(sc.is_product_url("https://shop.caravan-lean.com.ua/home/woda/9970900-detail.html"))
        self.assertFalse(sc.is_product_url("https://shop.caravan-lean.com.ua/home/woda.html"))

    def test_build_tree(self):
        cats = {
            "/home/woda": {"name": "Вода", "url": "https://shop.caravan-lean.com.ua/home/woda.html", "parent": None, "image": None, "order": 0},
            "/home/woda/nasosy": {"name": "Насосы", "url": "https://shop.caravan-lean.com.ua/home/woda/nasosy.html", "parent": "/home/woda", "image": None, "order": 1},
        }
        prods = {
            "https://shop.caravan-lean.com.ua/home/woda/nasosy/1-detail.html": {"url": "https://shop.caravan-lean.com.ua/home/woda/nasosy/1-detail.html", "name": "Насос", "images": ["https://x/1.jpg"], "_cat": "/home/woda/nasosy", "crumbs": []},
            "https://shop.caravan-lean.com.ua/home/woda/2-detail.html": {"url": "https://shop.caravan-lean.com.ua/home/woda/2-detail.html", "name": "Душ", "images": [], "_cat": "/home/woda", "crumbs": []},
        }
        cat = sc.build_virtuemart(cats, prods)
        by = {c["name"]: c for c in cat["categories"]}
        self.assertEqual(by["Насосы"]["parentId"], by["Вода"]["id"])
        self.assertEqual(by["Вода"]["productCount"], 2)
        self.assertEqual(by["Насосы"]["image"], "https://x/1.jpg")
        pump = next(p for p in cat["products"] if p["name"] == "Насос")
        self.assertEqual(pump["categoryId"], by["Насосы"]["id"])
        self.assertNotIn("_cat", pump)
