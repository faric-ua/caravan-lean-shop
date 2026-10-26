"""Парсер каталогу магазину → catalog.json (групи → підгрупи → товари).

Загальний підхід (працює на більшості движків, деталі сайту — у SITE_RULES нижче):
  • адреси сторінок — з карти сайту (sitemap), а якщо її нема — обхід посилань від головної;
  • товар = сторінка з розміткою schema.org Product (JSON-LD, microdata) або og:type=product;
  • дерево груп = «хлібні крихти» товару (BreadcrumbList або блок breadcrumbs): Головна › Група › Підгрупа › Товар;
  • порядок груп — як у меню сайту.

Запускає GitHub Actions (catalog.yml). Використання:
  python3 scripts/scrape_catalog.py <папка-результату> [--max-pages N]
"""
from __future__ import annotations

import argparse
import hashlib
import html as htmllib
import json
import re
import sys
import time
from collections import OrderedDict, deque
from pathlib import Path
from urllib.parse import urljoin, urlparse, urlunparse

from bs4 import BeautifulSoup

sys.path.insert(0, str(Path(__file__).resolve().parent))
from site_fetch import BASE, Fetcher, same_site  # noqa: E402

SCHEMA_VERSION = 1

# Що точно не каталог (не ходимо туди при обході).
SKIP = re.compile(
    r"(login|logout|register|account|cart|checkout|wishlist|compare|basket|kontakt|contact|about|o-nas|"
    r"blog|news|novyny|statti|article|faq|oplata|dostavka|payment|delivery|policy|privacy|terms|"
    r"\?.*(sort|order|limit|filter|view)=|/tag/|/author/|/feed|\.(jpg|jpeg|png|webp|gif|svg|pdf|zip|xml|js|css)$)",
    re.I,
)


# ---------- нормалізація ----------

def norm_url(u: str) -> str:
    p = urlparse(urljoin(BASE + "/", u))
    path = re.sub(r"/{2,}", "/", p.path) or "/"
    return urlunparse(("https", p.netloc or urlparse(BASE).netloc, path, "", p.query, ""))


def uid(url: str) -> str:
    return hashlib.sha1(urlparse(url).path.rstrip("/").encode()).hexdigest()[:12]


def clean(s) -> str:
    if s is None:
        return ""
    s = htmllib.unescape(str(s))
    return re.sub(r"\s+", " ", s).strip()


def to_price(v):
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    s = re.sub(r"[^\d,.]", "", str(v)).replace(",", ".")
    if s.count(".") > 1:
        # «1.234.567» — розділювачі тисяч; «1.234.50» — тисячі + копійки
        s = s.replace(".", "") if len(s.rsplit(".", 1)[1]) == 3 else s.replace(".", "", s.count(".") - 1)
    try:
        return float(s) if s else None
    except ValueError:
        return None


# ---------- розмітка сторінки ----------

def jsonld_items(soup: BeautifulSoup) -> list:
    out = []
    for tag in soup.find_all("script", type="application/ld+json"):
        raw = tag.string or tag.get_text() or ""
        try:
            data = json.loads(raw)
        except Exception:  # noqa: BLE001
            try:
                data = json.loads(re.sub(r"[\x00-\x1f]", " ", raw))
            except Exception:  # noqa: BLE001
                continue
        stack = data if isinstance(data, list) else [data]
        while stack:
            it = stack.pop(0)
            if isinstance(it, list):
                stack.extend(it)
            elif isinstance(it, dict):
                if "@graph" in it:
                    stack.extend(it["@graph"] if isinstance(it["@graph"], list) else [it["@graph"]])
                out.append(it)
    return out


def has_type(it: dict, name: str) -> bool:
    t = it.get("@type")
    return name in (t if isinstance(t, list) else [t])


def breadcrumbs(soup: BeautifulSoup, items: list) -> list:
    """[(назва, url)] без «Головної»."""
    crumbs = []
    for it in items:
        if has_type(it, "BreadcrumbList"):
            els = sorted(it.get("itemListElement") or [], key=lambda e: int(e.get("position") or 0) if isinstance(e, dict) else 0)
            for e in els:
                if not isinstance(e, dict):
                    continue
                item = e.get("item")
                url = item.get("@id") or item.get("url") if isinstance(item, dict) else item
                name = e.get("name") or (item.get("name") if isinstance(item, dict) else None)
                crumbs.append((clean(name), norm_url(url) if url else ""))
            break
    if not crumbs:
        box = soup.select_one("[itemtype*=BreadcrumbList], .breadcrumb, .breadcrumbs, [class*=breadcrumb], nav[aria-label*=read]")
        if box:
            for a in box.find_all(["a", "span"], recursive=True):
                if a.name == "span" and a.find("a"):
                    continue
                name = clean(a.get_text(" "))
                href = a.get("href")
                if name and name not in ("/", "›", "»", ">"):
                    crumbs.append((name, norm_url(href) if href else ""))
    # без «Головної» і дублікатів
    out, seen = [], set()
    for n, u in crumbs:
        if not n or u.rstrip("/") == BASE.rstrip("/") or n.lower() in ("головна", "главная", "home", "магазин"):
            continue
        key = (n, u)
        if key not in seen:
            seen.add(key)
            out.append((n, u))
    return out


def parse_product(url: str, soup: BeautifulSoup, items: list):
    prod = next((it for it in items if has_type(it, "Product")), None)
    og = {m.get("property"): m.get("content") for m in soup.find_all("meta", property=True)}
    micro = soup.select_one("[itemtype*='schema.org/Product']")
    if not prod and og.get("og:type") not in ("product", "og:product") and not micro:
        return None
    p = {"url": url}
    if prod:
        offers = prod.get("offers") or {}
        if isinstance(offers, list):
            offers = offers[0] if offers else {}
        if has_type(offers, "AggregateOffer"):
            offers = {**offers, "price": offers.get("lowPrice") or offers.get("price")}
        img = prod.get("image")
        imgs = [i if isinstance(i, str) else (i or {}).get("url") for i in (img if isinstance(img, list) else [img])]
        brand = prod.get("brand")
        p.update(
            name=clean(prod.get("name")),
            sku=clean(prod.get("sku") or prod.get("mpn")),
            brand=clean(brand.get("name") if isinstance(brand, dict) else brand),
            description=clean(BeautifulSoup(prod.get("description") or "", "lxml").get_text(" ")),
            price=to_price(offers.get("price")),
            currency=offers.get("priceCurrency"),
            available=None if not offers.get("availability") else ("InStock" in str(offers.get("availability")) or "PreOrder" in str(offers.get("availability"))),
            images=[norm_url(i) for i in imgs if i],
        )
    # Доповнення з og:/microdata/HTML, де JSON-LD мовчить.
    if not p.get("name"):
        h1 = soup.find("h1")
        p["name"] = clean(og.get("og:title") or (h1.get_text(" ") if h1 else ""))
    if p.get("price") is None:
        el = soup.select_one("[itemprop=price]") or soup.find("meta", property="product:price:amount")
        val = (el.get("content") or el.get_text(" ")) if el else None
        if val is None:
            box = soup.select_one("[class*=price]:not([class*=old])")
            val = box.get_text(" ") if box else None
        p["price"] = to_price(val)
    if not p.get("currency"):
        el = soup.select_one("[itemprop=priceCurrency]") or soup.find("meta", property="product:price:currency")
        p["currency"] = (el.get("content") if el else None) or "UAH"
    if not p.get("images"):
        imgs = [og.get("og:image")] + [i.get("src") or i.get("content") for i in soup.select("[itemprop=image]")]
        p["images"] = [norm_url(i) for i in imgs if i]
    if not p.get("description"):
        el = soup.select_one("[itemprop=description]") or soup.find("meta", attrs={"name": "description"})
        p["description"] = clean(el.get("content") if el and el.name == "meta" else (el.get_text(" ") if el else ""))
    old = soup.select_one("[class*=old-price], [class*=price-old], [class*=oldPrice], del [class*=price], s[class*=price], del")
    p["oldPrice"] = to_price(old.get_text(" ")) if old else None
    if p["oldPrice"] is not None and p.get("price") is not None and p["oldPrice"] <= p["price"]:
        p["oldPrice"] = None
    # Характеристики: таблиці/списки «назва — значення».
    attrs = []
    for row in soup.select("[class*=attribute] tr, [class*=characteristic] tr, [class*=spec] tr, table.shop_attributes tr, [class*=props] tr"):
        cells = [clean(c.get_text(" ")) for c in row.find_all(["th", "td"])]
        if len(cells) >= 2 and cells[0] and cells[1] and len(cells[0]) < 80:
            attrs.append({"name": cells[0].rstrip(":"), "value": cells[1][:300]})
    p["attributes"] = attrs[:40]
    p["images"] = list(OrderedDict.fromkeys(p["images"]))[:12]
    p["description"] = (p.get("description") or "")[:4000]
    p["crumbs"] = breadcrumbs(soup, items)
    return p if p.get("name") else None


def nav_order(soup: BeautifulSoup) -> list:
    """Порядок посилань у меню сайту — щоб групи йшли так само, як на сайті."""
    out = []
    for z in soup.select("nav, header, [class*=menu], [class*=catalog]"):
        for a in z.find_all("a", href=True):
            u = norm_url(a["href"])
            if same_site(u) and u not in out:
                out.append(u)
    return out


# ---------- обхід ----------

def sitemap_urls(f: Fetcher) -> list:
    robots = f.get("/robots.txt")
    maps = re.findall(r"(?im)^sitemap:\s*(\S+)", robots.text if robots.ok else "") or ["/sitemap.xml", "/sitemap_index.xml"]
    seen, urls, queue = set(), [], deque(maps)
    while queue and len(seen) < 60:
        m = queue.popleft()
        if m in seen:
            continue
        seen.add(m)
        p = f.get(m)
        if not p.ok or "<loc>" not in p.text:
            continue
        locs = [htmllib.unescape(x) for x in re.findall(r"<loc>\s*([^<\s]+)\s*</loc>", p.text)]
        if "<sitemapindex" in p.text:
            # спершу карти товарів і груп
            locs.sort(key=lambda u: 0 if re.search(r"product|tovar|categor|catalog", u, re.I) else 1)
            queue.extend(locs)
        else:
            urls += [norm_url(u) for u in locs if same_site(u)]
    return list(OrderedDict.fromkeys(urls))


def crawl(f: Fetcher, max_pages: int, log: list):
    home = f.get("/")
    if not home.ok:
        raise SystemExit(f"Головна не відкрилась: {home.status} ({home.mode}). Журнал: {f.log[-5:]}")
    home_soup = BeautifulSoup(home.text, "lxml")
    order = nav_order(home_soup)
    start = sitemap_urls(f)
    log.append(f"sitemap: {len(start)} адрес")
    # товарні адреси з карти сайту — першими
    start.sort(key=lambda u: 0 if re.search(r"product|tovar|/p\d|item", u, re.I) else 1)
    queue = deque(OrderedDict.fromkeys(start + order))
    seen = set(queue)
    products, cat_pages, visited = {}, {}, 0
    t0 = time.time()
    while queue and visited < max_pages:
        url = queue.popleft()
        if SKIP.search(url) or not same_site(url):
            continue
        p = f.get(url)
        visited += 1
        if not p.ok or "html" not in (p.content_type or "text/html"):
            continue
        soup = BeautifulSoup(p.text, "lxml")
        items = jsonld_items(soup)
        prod = parse_product(norm_url(p.final_url), soup, items)
        if prod:
            products[prod["url"]] = prod
        else:
            h1 = soup.find("h1")
            og_img = soup.find("meta", property="og:image")
            cat_pages[norm_url(p.final_url)] = {
                "name": clean(h1.get_text(" ")) if h1 else "",
                "image": norm_url(og_img["content"]) if og_img and og_img.get("content") else None,
                "crumbs": breadcrumbs(soup, items),
            }
            # з групи — далі: посилання на товари, підгрупи, наступні сторінки
            for a in soup.find_all("a", href=True):
                u = norm_url(a["href"])
                if u not in seen and same_site(u) and not SKIP.search(u):
                    seen.add(u)
                    queue.append(u)
        if visited % 50 == 0:
            log.append(f"{visited} сторінок · товарів {len(products)} · {int(time.time() - t0)} с")
    log.append(f"обійдено {visited} сторінок, товарів {len(products)}, інших сторінок {len(cat_pages)}")
    return products, cat_pages, order


def build(products: dict, cat_pages: dict, order: list) -> dict:
    cats: "OrderedDict[str, dict]" = OrderedDict()

    def ensure(chain):
        parent = None
        for name, url in chain:
            key = url or f"{parent}/{name}"
            cid = uid(key) if url else hashlib.sha1(key.encode()).hexdigest()[:12]
            if cid not in cats:
                cats[cid] = {"id": cid, "name": name, "url": url or None, "parentId": parent, "image": None, "productCount": 0}
            parent = cid
        return parent

    out_products = []
    for url, p in products.items():
        chain = [c for c in p.pop("crumbs") if c[1].rstrip("/") != url.rstrip("/") and c[0] != p["name"]]
        cid = ensure(chain) if chain else ensure([("Інше", "")])
        p.update(id=uid(url), categoryId=cid)
        out_products.append(p)

    # кількість товарів (з підгрупами) і картинка групи
    by_cat = {}
    for p in out_products:
        c = p["categoryId"]
        while c:
            by_cat.setdefault(c, []).append(p)
            c = cats[c]["parentId"]
    for cid, c in cats.items():
        lst = by_cat.get(cid, [])
        c["productCount"] = len(lst)
        page = cat_pages.get(c["url"] or "", {})
        c["image"] = page.get("image") or next((p["images"][0] for p in lst if p.get("images")), None)

    # порядок як у меню сайту; чого в меню нема — за назвою
    pos = {u: i for i, u in enumerate(order)}
    ordered = sorted(cats.values(), key=lambda c: (pos.get(c["url"] or "", 10**6), c["name"].lower()))
    for i, c in enumerate(ordered):
        c["order"] = i
    currency = next((p["currency"] for p in out_products if p.get("currency")), "UAH")
    return {
        "schema": SCHEMA_VERSION,
        "source": BASE,
        "scrapedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "currency": currency,
        "categories": ordered,
        "products": sorted(out_products, key=lambda p: (p["categoryId"], p["name"].lower())),
    }


# ---------- VirtueMart (Joomla) — shop.caravan-lean.com.ua ----------
# ЧПУ-адреси: група /home/woda.html → підгрупа /home/woda/sub.html → товар /home/woda/123-detail.html.
# Тобто шлях адреси і є деревом груп. Schema.org-розмітки нема — читаємо HTML шаблону VirtueMart.

def is_virtuemart(html: str) -> bool:
    return "com_virtuemart" in html or "VMmenu" in html or "virtuemart" in html.lower()


def cat_key(url: str) -> str:
    """/home/woda.html → /home/woda ; /home/woda/results,21-40.html → /home/woda."""
    path = urlparse(url).path
    path = re.sub(r"/results,\d+-\d+\.html$", "", path)
    return re.sub(r"\.html$", "", path).rstrip("/")


def is_product_url(url: str) -> bool:
    return bool(re.search(r"-detail\.html$", urlparse(url).path))


def vm_price(el) -> float | None:
    return to_price(el.get_text(" ")) if el else None


def parse_vm_product(url: str, soup: BeautifulSoup):
    box = soup.select_one(".productdetails-view")
    if not box:
        return None
    h1 = box.find("h1")
    name = clean(h1.get_text(" ")) if h1 else ""
    if not name:
        return None
    sku = ""
    h4 = box.find("h4")
    if h4:
        m = re.search(r"Артикул\s*:?\s*([^\s<]+)", h4.get_text(" "))
        sku = m.group(1) if m else ""
    price = vm_price(box.select_one("span.PricesalesPrice") or box.select_one(".PricesalesPrice"))
    old = None
    for sel in ("span.PricebasePriceWithTax", "span.PricebasePrice", ".price-crossed span", "span.PricediscountedPriceWithoutTax"):
        v = vm_price(box.select_one(sel))
        if v and price and v > price:
            old = v
            break
    imgs = []
    for a in box.select(".main-image a[href], .additional-images a[href]"):
        imgs.append(norm_url(a["href"]))
    for im in box.select(".main-image img[src], .additional-images img[src], img.product-image[src]"):
        imgs.append(norm_url(im["src"]))
    imgs = [i for i in OrderedDict.fromkeys(imgs) if re.search(r"\.(jpe?g|png|webp|gif)$", i, re.I) and "noimage" not in i]
    # прибрати зменшені копії, якщо є оригінал
    full = [i for i in imgs if "/resized/" not in i]
    imgs = full or imgs
    short = clean(box.select_one(".product-short-description").get_text(" ")) if box.select_one(".product-short-description") else ""
    desc_el = box.select_one(".product-description")
    desc = ""
    if desc_el:
        t = desc_el.select_one(".title")
        if t:
            t.decompose()
        desc = clean(desc_el.get_text(" "))
    description = (short + ("\n\n" if short and desc and desc != short else "") + (desc if desc != short else "")).strip()
    attrs = []
    for f in box.select(".product-fields .product-field, .product-field"):
        t = f.select_one(".product-fields-title, .product-field-title")
        v = f.select_one(".product-field-display")
        if t and v and clean(t.get_text(" ")) and clean(v.get_text(" ")):
            attrs.append({"name": clean(t.get_text(" ")).rstrip(":"), "value": clean(v.get_text(" "))[:300]})
    manuf = box.select_one(".manufacturer a, .manufacturer")
    btn = box.select_one("input.addtocart-button, .addtocart-button input, a.notify, .notify")
    btxt = ((btn.get("value") or btn.get_text(" ")) if btn else "").lower()
    if btn is None:
        available = None
    else:
        available = not any(w in btxt for w in ("уведом", "notify", "повідом", "нет в нал"))
    if re.search(r"нет в наличии|немає в наявності|out of stock", box.get_text(" "), re.I):
        available = False
    return {
        "url": url, "name": name, "sku": sku, "brand": clean(manuf.get_text(" ")).replace("Производитель:", "").strip() if manuf else "",
        "description": description[:4000], "price": price, "oldPrice": old, "currency": "UAH",
        "available": available, "images": imgs[:12], "attributes": attrs[:40], "crumbs": [],
    }


def crawl_virtuemart(f: Fetcher, home_soup: BeautifulSoup, max_pages: int, log: list):
    cats: "OrderedDict[str, dict]" = OrderedDict()  # cat_key → {name,url,parent,image,order}
    home_imgs = {}
    for tile in home_soup.select(".category"):
        a = tile.find("a", href=True)
        im = tile.find("img")
        if a and im:
            home_imgs[cat_key(norm_url(a["href"]))] = norm_url(im["src"])
    for i, a in enumerate(home_soup.select(".VMmenu a[href]")):
        u = norm_url(a["href"])
        k = cat_key(u)
        cats.setdefault(k, {"name": clean(a.get_text(" ")), "url": u, "parent": None, "image": home_imgs.get(k), "order": i})
    log.append(f"VirtueMart: груп у меню {len(cats)}")

    # одразу по 200 товарів на сторінку (VirtueMart: results,1-200.html) — менше запитів до сайту
    def big_page(u: str) -> str:
        return re.sub(r"\.html$", "/results,1-200.html", u)

    queue = deque(big_page(c["url"]) for c in cats.values())
    seen_pages = set(queue)
    product_urls: "OrderedDict[str, dict]" = OrderedDict()  # url → дані з плитки (запасні)
    visited = 0
    while queue and visited < max_pages:
        url = queue.popleft()
        p = f.get(url)
        visited += 1
        if not p.ok:
            log.append(f"група не відкрилась: {url} ({p.status})")
            continue
        soup = BeautifulSoup(p.text, "lxml")
        here = cat_key(url)
        main = soup.select_one(".category-view, .browse-view, #content, .main-content, #main") or soup
        # підгрупи: плитки .category з посиланням глибше за поточну групу
        for tile in main.select(".category"):
            a = tile.find("a", href=True)
            if not a:
                continue
            u = norm_url(a["href"])
            k = cat_key(u)
            if k == here or is_product_url(u):
                continue
            im = tile.find("img")
            if k not in cats:
                cats[k] = {"name": clean(a.get("title") or a.get_text(" ")), "url": u, "parent": here,
                           "image": norm_url(im["src"]) if im and im.get("src") else None, "order": len(cats)}
            elif cats[k]["parent"] is None and k.startswith(here + "/"):
                cats[k]["parent"] = here
            if u not in seen_pages:
                seen_pages.add(u)
                queue.append(big_page(u))
        # товари на сторінці групи
        for a in main.select("a[href]"):
            u = norm_url(a["href"])
            if not is_product_url(u) or not cat_key(u).startswith(here):
                continue
            if u not in product_urls:
                product_urls[u] = {"listed_in": here}
        # пагінація групи
        for a in soup.select("a[href]"):
            u = norm_url(a["href"])
            if u in seen_pages or is_product_url(u):
                continue
            if cat_key(u) == here and (re.search(r"results,\d+-\d+\.html$", u) or re.search(r"[?&](limitstart|start)=\d+", u)):
                seen_pages.add(u)
                queue.append(u)
    log.append(f"сторінок груп: {visited}, груп: {len(cats)}, адрес товарів: {len(product_urls)}")

    products = {}
    budget = max(0, max_pages - visited)
    for i, (u, meta) in enumerate(product_urls.items()):
        if i >= budget:
            log.append(f"ліміт сторінок: товарів не відкрито {len(product_urls) - i}")
            break
        p = f.get(u)
        if not p.ok:
            continue
        prod = parse_vm_product(norm_url(p.final_url), BeautifulSoup(p.text, "lxml"))
        if prod:
            prod["_cat"] = meta["listed_in"]
            products[prod["url"]] = prod
        if (i + 1) % 100 == 0:
            log.append(f"товарів відкрито {i + 1}, розпізнано {len(products)}")
    return cats, products


def build_virtuemart(cats: dict, products: dict) -> dict:
    # найглибша відома група для кожного товару: за шляхом адреси, інакше — де знайдено
    keys = sorted(cats, key=len, reverse=True)
    out_products = []
    for url, p in products.items():
        pk = cat_key(url).rsplit("/", 1)[0]
        cat = next((k for k in keys if pk == k or pk.startswith(k + "/")), p.pop("_cat"))
        p.pop("_cat", None)
        p.pop("crumbs", None)
        p.update(id=uid(url), categoryId=uid(cats[cat]["url"]) if cat in cats else "other")
        out_products.append(p)
    categories = []
    for k, c in cats.items():
        parent = c["parent"]
        if parent is None and "/" in k.strip("/")[len("home"):]:
            # батько за шляхом, якщо група прийшла не з меню
            up = k.rsplit("/", 1)[0]
            parent = up if up in cats else None
        categories.append({"id": uid(c["url"]), "name": c["name"] or "Без назви", "url": c["url"],
                           "parentId": uid(cats[parent]["url"]) if parent in cats else None,
                           "image": c["image"], "productCount": 0, "order": c["order"]})
    by_id = {c["id"]: c for c in categories}
    for p in out_products:
        cid = p["categoryId"]
        guard = set()
        while cid in by_id and cid not in guard:
            guard.add(cid)
            by_id[cid]["productCount"] += 1
            if not by_id[cid]["image"] and p.get("images"):
                by_id[cid]["image"] = p["images"][0]
            cid = by_id[cid]["parentId"]
    if any(p["categoryId"] == "other" for p in out_products):
        categories.append({"id": "other", "name": "Інше", "url": None, "parentId": None, "image": None,
                           "productCount": sum(p["categoryId"] == "other" for p in out_products), "order": 10**6})
    categories.sort(key=lambda c: c["order"])
    for i, c in enumerate(categories):
        c["order"] = i
    return {
        "schema": SCHEMA_VERSION, "source": BASE,
        "scrapedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "currency": "UAH",
        "categories": categories, "products": out_products,  # порядок товарів — як на сайті
    }


def summary(cat: dict) -> str:
    cs, ps = cat["categories"], cat["products"]
    top = [c for c in cs if not c["parentId"]]
    lines = [
        f"Знімок: {cat['scrapedAt']}",
        f"Груп верхнього рівня: {len(top)} · усіх груп: {len(cs)} · товарів: {len(ps)}",
        f"З ціною: {sum(1 for p in ps if p.get('price') is not None)} · з фото: {sum(1 for p in ps if p.get('images'))} · у наявності: {sum(1 for p in ps if p.get('available'))}",
        "Групи: " + ", ".join(f"{c['name']} ({c['productCount']})" for c in top[:12]),
    ]
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--max-pages", type=int, default=1500)
    a = ap.parse_args()
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    f, log = Fetcher(), []
    try:
        home = f.get("/")
        if home.ok and is_virtuemart(home.text):
            log.append("движок: Joomla + VirtueMart")
            cats, products = crawl_virtuemart(f, BeautifulSoup(home.text, "lxml"), a.max_pages, log)
            cat = build_virtuemart(cats, products)
        else:
            products, cat_pages, order = crawl(f, a.max_pages, log)
            cat = build(products, cat_pages, order)
    finally:
        f.close()
    (out / "catalog.json").write_text(json.dumps(cat, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    s = summary(cat)
    (out / "catalog-summary.txt").write_text(s + "\n", encoding="utf-8")
    (out / "scrape-log.txt").write_text("\n".join(log + ["", "Запити (останні 200):"] + f.log[-200:]), encoding="utf-8")
    print(s)
    return 0 if cat["products"] else 2


if __name__ == "__main__":
    sys.exit(main())
