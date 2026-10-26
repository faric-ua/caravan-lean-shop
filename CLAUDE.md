# Caravan Lean — правила для Claude

Тут лише правила й карта. Стан — `STATUS.md`. Задачі — `TODO.md`. План — `docs/PLAN.md`.
Спільні правила всіх репо faric-ua (формат відповіді, значок 📲/🚫, «Від тебе зараз:») — `docs/RESPONSE_ALGORITHM.md`.
Шаблон репо й меню — `faric-ua/faric-track-catcher` (пункти 1–13 меню однакові).

## Що це

Демо-застосунок для власника інтернет-магазину **shop.caravan-lean.com.ua** (faric показує його замовнику):
свій Android-застосунок замість «відкрити сайт у браузері». Каталог — групи → підгрупи → товари, плитками;
картка товару, пошук, кошик, демо-оформлення. **Жодного WebView / відкриття сайту в браузері** — у цьому вся суть.

Етапи (`docs/PLAN.md`): зараз дані беруться **парсингом сайту**; потім замовник дасть доступ до сервера/бази —
додається одне джерело `RemoteJson` на їхній сервер (той самий формат `catalog.json`), екрани не міняються.

## Власник

- **faric** (`faric-ua`). Працює **лише з телефона** (Samsung Galaxy A26) через Termux-меню. Не програміст.
- Українською, коротко, простими словами. Дії на телефоні — **лише пунктами меню** `CARAVAN LEAN → 2`.
- Кожна робоча відповідь — за `docs/RESPONSE_ALGORITHM.md` (у кінці «Від тебе зараз:», першим рядком 📲/🚫 з версією й збіркою).
- Що треба скопіювати — окремим блоком коду, перед ним — куди вставити.

## Старт сесії

1. `CLAUDE.md` → `STATUS.md` → `TODO.md` (+ нові записи `docs/INSPECTOR.md`).
2. Сайт у середовищі Claude **недоступний** (WebFetch — блок «ботів», shell — закритий інтернет).
   Сайт читає лише GitHub Actions: `site-probe.yml` → гілка `diag/site-probe` (сирі сторінки + `report.md`),
   `catalog.yml` → гілка `data/catalog` (`catalog.json`, `catalog-summary.txt`, `scrape-log.txt`). Звідти й дивитись.

## Цикл роботи

1. Гілка `feat/…` / `fix/…` → PR у `main` → зелений CI (`Checks`, `Android`) → squash-мерж самому.
2. Нова версія = `versionName` (X.Y.Z) **і** `versionCode` в `android/app/build.gradle.kts`; мерж у `main` публікує реліз (лише з ключем).
3. faric: `CARAVAN LEAN → 2` і до 3 перевірок. Результат — у `STATUS.md`.

## Докази

- Код ≠ юніт-тест ≠ зелений CI ≠ **пас на телефоні**. Пас ставить лише faric.

## Заборонено

- Відкривати сайт у браузері / WebView у застосунку (суть проєкту — свої екрани).
- Класти ключі, паролі, особисту пошту в репо; класти `catalog.json` у `main` (лише гілка `data/catalog`).
- Зсувати номери пунктів меню; `git reset --hard`, `git clean`, force-push у скриптах телефона.
- Стандартні сірі діалоги/тости Android, кольори поза `ui/Palette.kt`, галочки ✓ (`scripts/audit_ui.py`).
- Сайт магазину — чужий: обхід ввічливий (≈1,5 запиту/с, `site_fetch.py`), не частіше ніж треба.

## Карта

| Що | Де |
|---|---|
| Розвідка сайту / парсер (Python, лише в CI) | `scripts/site_fetch.py`, `site_probe.py`, `scrape_catalog.py` + `tests/test_scrape_catalog.py` |
| Формат каталогу | `catalog.json` (schema 1): пише `scrape_catalog.py`, читає `core/CatalogJson.kt` — тест з одним файлом на обидва |
| Ядро без Android (каталог, пошук, ціни, живий розбір товару) | `android/app/src/main/java/ua/faric/caravanlean/core/` + `src/test/…` |
| Джерела каталогу, кошик | `data/CatalogRepo.kt` (`BundledSnapshot`, `LocalCache`, `RemoteJson`, `LiveProduct`), `data/Cart.kt` |
| Мережа, фото | `net/Http.kt`, `net/Images.kt` |
| Екрани | `ui/` (`Palette.kt` — усі кольори, `Ui.kt` — усі кнопки, `Grid.kt` — плитки) |
| Меню телефона | `menu.sh`, `tools/termux/cl-*.sh` |
| CI | `.github/workflows/android.yml`, `checks.yml`, `site-probe.yml`, `catalog.yml` |

## Технічне

- Gradle 8.13 без wrapper; AGP 8.13.2, Kotlin 2.3.21, SDK 36, minSdk 26. Залежності: recyclerview, jsoup.
- Kotlin компілює лише CI (у Claude нема Android SDK і Maven). Логи Actions недоступні — помилки йдуть анотаціями.
- Ключ підпису: меню 7 → секрети `CL_*`. `CL_CATALOG_URL` (необов'язково) — адреса свіжого `catalog.json` для кнопки «Оновити каталог».
