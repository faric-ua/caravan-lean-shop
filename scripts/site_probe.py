"""Розвідка сайту магазину: що за движок, як влаштовані групи й товари, чи є готове API.

Запускає GitHub Actions (workflow site-probe.yml) — у середовищі Claude сайт недоступний.
Результат — папка з сирими сторінками і report.md; workflow кладе її в гілку diag/site-probe.

Використання: python3 scripts/site_probe.py <папка-результату>
"""
from __future__ import annotations

import json
import re
import sys
from collections import Counter
from pathlib import Path
from urllib.parse import urljoin, urlparse

from bs4 import BeautifulSoup

sys.path.insert(0, str(Path(__file__).resolve().parent))
from site_fetch import BASE, Fetcher, same_site  # noqa: E402

CMS_MARKS = {
    "WordPress / WooCommerce": ["wp-content", "woocommerce", "wp-json"],
    "OpenCart": ["catalog/view/theme", "route=product", "index.php?route="],
    "Horoshop": ["horoshop", "/_widget/", "data-hs-"],
    "Prom.ua / Satu": ["prom.ua", "images.prom.ua", "satu.kz"],
    "Shopify": ["cdn.shopify.com", "Shopify.theme"],
    "1C-Bitrix": ["bitrix/", "BX.setJSList"],
    "Magento": ["/static/version", "Magento_", "mage/cookies"],
    "PrestaShop": ["prestashop", "/modules/ps_"],
    "Tilda": ["tilda.ws", "tildacdn"],
    "Wix": ["wixstatic.com", "_wixCssImports"],
    "Shop-Express / Weblium / Kreator": ["shop-express", "weblium", "kreator"],
    "Nuxt / Vue SPA": ["__NUXT__", "data-v-"],
    "Next.js SPA": ["__NEXT_DATA__"],
}
API_TRIES = [
    "/wp-json/wc/store/v1/products?per_page=5",
    "/wp-json/wc/store/v1/products/categories?per_page=100",
    "/products.json?limit=5",
    "/index.php?route=product/category&path=1",
    "/api/products",
    "/sitemap_index.xml",
    "/sitemap.xml",
    "/product-sitemap.xml",
    "/product_cat-sitemap.xml",
    "/feed/",
    "/yml.xml",
    "/rozetka.xml",
    "/hotline.xml",
    "/products_feed.xml",
]


def jsonld_types(soup: BeautifulSoup) -> list:
    out = []
    for tag in soup.find_all("script", type="application/ld+json"):
        try:
            data = json.loads(tag.string or "")
        except Exception:  # noqa: BLE001
            out.append("(битий JSON-LD)")
            continue
        items = data if isinstance(data, list) else data.get("@graph", [data]) if isinstance(data, dict) else []
        for it in items:
            if isinstance(it, dict):
                out.append(str(it.get("@type")))
    return out


def page_summary(name: str, url: str, html: str) -> str:
    soup = BeautifulSoup(html, "lxml")
    title = (soup.title.string or "").strip() if soup.title else ""
    og = {m.get("property"): m.get("content") for m in soup.find_all("meta", property=re.compile("^og:"))}
    micro = sorted({t.get("itemtype", "") for t in soup.find_all(attrs={"itemtype": True})})
    prices = re.findall(r"\d[\d\s]{1,9}(?:[.,]\d{2})?\s*(?:грн|₴|UAH)", soup.get_text(" "))[:5]
    classes = Counter()
    for t in soup.find_all(class_=True):
        for c in t.get("class", []):
            if re.search(r"product|catalog|category|price|card|item|tile|breadcrumb|menu", c, re.I):
                classes[c] += 1
    return (
        f"### {name}\n- URL: {url}\n- title: {title[:120]}\n- og:type: {og.get('og:type')} · og:image: {bool(og.get('og:image'))}\n"
        f"- JSON-LD: {jsonld_types(soup) or '—'}\n- microdata: {micro or '—'}\n- ціни в тексті: {prices or '—'}\n"
        f"- класи (product/catalog/price…): {', '.join(f'{c}×{n}' for c, n in classes.most_common(25)) or '—'}\n"
    )


def nav_links(soup: BeautifulSoup) -> list:
    """Посилання з меню/шапки — кандидати в групи товарів."""
    seen, out = set(), []
    zones = soup.select("nav, header, [class*=menu], [class*=catalog], [class*=categor], aside") or [soup]
    for z in zones:
        for a in z.find_all("a", href=True):
            u = urljoin(BASE + "/", a["href"]).split("#")[0]
            if not same_site(u) or u.rstrip("/") == BASE or u in seen:
                continue
            if re.search(r"(login|account|cart|checkout|wishlist|compare|contact|about|blog|news|tel:|mailto:|\.jpg|\.png)", u, re.I):
                continue
            seen.add(u)
            out.append((u, a.get_text(" ", strip=True)[:60]))
    return out


def tile_links(soup: BeautifulSoup) -> list:
    """Посилання, усередині яких є картинка, поза шапкою/підвалом — кандидати в товари."""
    for z in soup.select("header, footer, nav"):
        z.decompose()
    seen, out = set(), []
    for a in soup.find_all("a", href=True):
        if not a.find("img"):
            continue
        u = urljoin(BASE + "/", a["href"]).split("#")[0]
        if same_site(u) and u.rstrip("/") != BASE and u not in seen:
            seen.add(u)
            out.append(u)
    return out


def main(out_dir: str) -> int:
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    f = Fetcher()
    rep = ["# Розвідка сайту " + BASE, ""]

    home = f.get("/")
    (out / "home.html").write_text(home.text, encoding="utf-8")
    rep.append(f"Головна: статус {home.status}, спосіб «{home.mode}», кінцева адреса {home.final_url}, {len(home.text)} символів.")
    if not home.ok:
        rep += ["", "**Головна не відкрилась жодним способом.**", "", "## Журнал запитів", *[f"- {x}" for x in f.log]]
        (out / "report.md").write_text("\n".join(rep), encoding="utf-8")
        f.close()
        print("\n".join(rep))
        return 0  # звіт усе одно потрібен — workflow його збереже

    low = home.text.lower()
    cms = [name for name, marks in CMS_MARKS.items() if any(m.lower() in low for m in marks)]
    rep += [f"Движок (за ознаками): {', '.join(cms) or 'невідомо'}", "", page_summary("Головна", home.final_url, home.text)]

    robots = f.get("/robots.txt")
    (out / "robots.txt").write_text(robots.text[:20000], encoding="utf-8")
    sitemaps = re.findall(r"(?im)^sitemap:\s*(\S+)", robots.text)
    rep.append(f"robots.txt: {robots.status}; sitemap у robots: {sitemaps or '—'}")

    rep += ["", "## Спроби готових API / фідів / карт сайту"]
    for path in API_TRIES + [s for s in sitemaps if s not in API_TRIES]:
        p = f.get(path)
        kind = "JSON" if p.text.lstrip()[:1] in "[{" else ("XML" if p.text.lstrip().startswith("<?xml") or "<urlset" in p.text[:500] or "<yml_catalog" in p.text[:500] else "HTML/інше")
        rep.append(f"- `{path}` → {p.status}, {kind}, {len(p.text)} символів")
        if p.ok and kind in ("JSON", "XML"):
            safe = re.sub(r"[^a-z0-9]+", "_", path.lower()).strip("_")[:60]
            (out / f"api_{safe}.{kind.lower()}").write_text(p.text[:400000], encoding="utf-8")
            if kind == "XML":
                locs = re.findall(r"<loc>\s*([^<\s]+)\s*</loc>", p.text)
                rep.append(f"  - <loc>: {len(locs)}; приклади: {locs[:6]}")
                pats = Counter("/".join(urlparse(u).path.strip("/").split("/")[:1]) for u in locs)
                rep.append(f"  - перший сегмент шляху: {pats.most_common(12)}")

    soup = BeautifulSoup(home.text, "lxml")
    navs = nav_links(soup)
    rep += ["", f"## Посилання з меню ({len(navs)})", *[f"- {t or '·'} — {u}" for u, t in navs[:120]]]
    seg = Counter(urlparse(u).path.strip("/").split("/")[0] for u, _ in navs)
    rep.append(f"Перший сегмент шляху в меню: {seg.most_common(15)}")

    # VirtueMart: справжні групи — у блоці VMmenu (меню вище — це сторінки сайту)
    vm = [(urljoin(BASE + "/", a["href"]), a.get_text(" ", strip=True)) for a in soup.select(".VMmenu a[href]")]
    if vm:
        navs = vm + [x for x in navs if x not in vm]
        rep.append(f"VMmenu (групи VirtueMart): {len(vm)}")
    rep += ["", "## Сторінки груп (перші 5 з меню груп)"]
    products = []
    for i, (u, t) in enumerate(navs[:5], 1):
        p = f.get(u)
        (out / f"cat_{i}.html").write_text(p.text, encoding="utf-8")
        rep.append(page_summary(f"Група {i}: {t}", p.final_url, p.text))
        tl = tile_links(BeautifulSoup(p.text, "lxml"))
        rep.append(f"  посилань-плиток з картинкою: {len(tl)}; приклади: {tl[:8]}")
        products += [x for x in tl if x not in products]

    # друга сторінка першої групи з пагінацією (як влаштована пагінація)
    for i in range(1, 6):
        fp = out / f"cat_{i}.html"
        if not fp.exists():
            continue
        cs = BeautifulSoup(fp.read_text(encoding="utf-8"), "lxml")
        pag = [urljoin(BASE + "/", a["href"]) for a in cs.select(".pagination a[href], .vm-pagination a[href], [class*=pagination] a[href]")]
        if pag:
            rep.append(f"Пагінація в групі {i}: {pag[:8]}")
            p2 = f.get(pag[0])
            (out / "cat_page2.html").write_text(p2.text, encoding="utf-8")
            rep.append(page_summary("Друга сторінка групи", p2.final_url, p2.text))
            break

    rep += ["", "## Сторінки товарів (перші 6 кандидатів)"]
    for i, u in enumerate(products[:6], 1):
        p = f.get(u)
        (out / f"prod_{i}.html").write_text(p.text, encoding="utf-8")
        rep.append(page_summary(f"Товар-кандидат {i}", p.final_url, p.text))

    rep += ["", "## Журнал запитів", *[f"- {x}" for x in f.log]]
    (out / "report.md").write_text("\n".join(rep), encoding="utf-8")
    f.close()
    print("\n".join(rep[:60]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "site-probe"))
