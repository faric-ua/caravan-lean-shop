"""Завантаження сторінок сайту магазину — спільне для розвідки (site_probe.py) і парсера (scrape_catalog.py).

Сайт ріже «ботів», тому три сходинки, від легкої до важкої:
  1. requests зі звичайними заголовками браузера;
  2. curl_cffi — TLS-«відбиток» справжнього Chrome (обходить простий захист на кшталт Cloudflare «bot fight»);
  3. Playwright + Chromium — справжній браузер без вікна (виконує JS-перевірку).
Перша сходинка, що спрацювала на головній, лишається для решти сторінок (Fetcher.mode).
"""
from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Optional
from urllib.parse import urljoin, urlparse

BASE = "https://shop.caravan-lean.com.ua"
UA = (
    "Mozilla/5.0 (Linux; Android 14; SM-A266B) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
)
HEADERS = {
    "User-Agent": UA,
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "uk-UA,uk;q=0.9,ru;q=0.7,en;q=0.6",
}
# Ознаки сторінки-перевірки замість справжньої (Cloudflare, DDoS-Guard, «I'm under attack»).
CHALLENGE_MARKS = (
    "cf-browser-verification", "cf_chl_", "challenge-platform", "Just a moment...",
    "ddos-guard", "Checking your browser", "Attention Required!",
)


@dataclass
class Page:
    url: str
    status: int
    text: str
    final_url: str
    mode: str
    content_type: str = ""

    @property
    def ok(self) -> bool:
        return 200 <= self.status < 300 and not looks_like_challenge(self.text)


def looks_like_challenge(text: str) -> bool:
    head = text[:20000]
    return any(m in head for m in CHALLENGE_MARKS)


@dataclass
class Fetcher:
    base: str = BASE
    delay: float = 0.6  # ввічливо: не частіше ніж ~1,5 запиту/с
    mode: Optional[str] = None
    log: list = field(default_factory=list)
    _last: float = 0.0
    _session: object = None
    _cffi: object = None
    _pw: object = None
    _browser: object = None
    _page: object = None

    # ---------- три способи ----------

    def _get_requests(self, url: str) -> Page:
        import requests

        if self._session is None:
            self._session = requests.Session()
            self._session.headers.update(HEADERS)
        r = self._session.get(url, timeout=30, allow_redirects=True)
        r.encoding = r.encoding or r.apparent_encoding
        return Page(url, r.status_code, r.text, r.url, "requests", r.headers.get("content-type", ""))

    def _get_cffi(self, url: str) -> Page:
        from curl_cffi import requests as creq

        if self._cffi is None:
            self._cffi = creq.Session(impersonate="chrome")
            self._cffi.headers.update({k: v for k, v in HEADERS.items() if k != "User-Agent"})
        r = self._cffi.get(url, timeout=30, allow_redirects=True)
        return Page(url, r.status_code, r.text, str(r.url), "curl_cffi", r.headers.get("content-type", ""))

    def _get_browser(self, url: str) -> Page:
        if self._page is None:
            from playwright.sync_api import sync_playwright

            self._pw = sync_playwright().start()
            self._browser = self._pw.chromium.launch(headless=True)
            ctx = self._browser.new_context(user_agent=UA, locale="uk-UA", viewport={"width": 412, "height": 915})
            self._page = ctx.new_page()
        resp = self._page.goto(url, wait_until="domcontentloaded", timeout=60000)
        # JS-перевірка зазвичай сама перезавантажує сторінку за 3–8 с
        for _ in range(12):
            html = self._page.content()
            if not looks_like_challenge(html):
                break
            self._page.wait_for_timeout(1500)
        html = self._page.content()
        status = resp.status if resp else 0
        if status in (403, 503) and not looks_like_challenge(html):
            status = 200  # перевірку пройдено, а статус лишився від першої відповіді
        return Page(url, status, html, self._page.url, "browser", "text/html")

    # ---------- спільне ----------

    def _call(self, mode: str, url: str) -> Page:
        return {"requests": self._get_requests, "curl_cffi": self._get_cffi, "browser": self._get_browser}[mode](url)

    def get(self, url: str) -> Page:
        url = urljoin(self.base + "/", url)
        wait = self.delay - (time.time() - self._last)
        if wait > 0:
            time.sleep(wait)
        self._last = time.time()
        modes = [self.mode] if self.mode else ["requests", "curl_cffi", "browser"]
        last: Optional[Page] = None
        for m in modes:
            try:
                p = self._call(m, url)
            except Exception as e:  # noqa: BLE001 — у звіт, далі наступний спосіб
                self.log.append(f"{m} {url} → помилка {type(e).__name__}: {str(e)[:160]}")
                continue
            self.log.append(f"{m} {url} → {p.status}{' (перевірка-бот)' if looks_like_challenge(p.text) else ''}")
            last = p
            if p.ok:
                if self.mode is None:
                    self.mode = m
                return p
        return last or Page(url, 0, "", url, "none")

    def close(self) -> None:
        try:
            if self._browser:
                self._browser.close()
            if self._pw:
                self._pw.stop()
        except Exception:  # noqa: BLE001
            pass


def same_site(url: str, base: str = BASE) -> bool:
    return urlparse(url).netloc in ("", urlparse(base).netloc)
