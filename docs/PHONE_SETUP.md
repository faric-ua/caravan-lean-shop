# Телефон: перший запуск Caravan Lean

## Один раз — поставити меню

У Termux вставити один рядок (далі — лише пункти меню):

```
git clone https://github.com/faric-ua/caravan-lean-shop ~/caravan-lean-shop && bash ~/caravan-lean-shop/menu.sh
```

Репо приватне — `git clone` бере вхід з `gh` (він уже налаштований для інших репо).
Ярлик «Caravan Lean» для Termux:Widget ставиться сам (пункт **9** — те саме вручну).

## Один раз — ключ підпису

Меню → **7**: створює ключ `~/.android/caravan-lean.jks`, передає в секрети GitHub (`CL_*`), запускає збірку релізу.
Меню → **8** — резервна копія ключа в `Documents/Caravan Lean/key-backup/`.

## Встановити / оновити

Меню → **2**: останній реліз з GitHub, перевірка SHA-256, `Documents/Caravan Lean/apk/vX.Y.Z/`, вікно встановлення.

## Каталог

Меню → **14**: GitHub заново парсить сайт (2–10 хв) і показує, скільки груп і товарів знайшов. Наступна збірка APK візьме цей знімок.

## Резерв — збірка на телефоні

Меню → **11** / **12** — як у Track Catcher (Java 17, `~/android-sdk`, `~/gradle/gradle-8.13`, ключ з пункту 7).
