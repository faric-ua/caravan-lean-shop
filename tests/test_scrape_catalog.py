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
