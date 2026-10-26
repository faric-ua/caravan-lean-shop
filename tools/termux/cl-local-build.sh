#!/usr/bin/env bash
# Резерв: зібрати APK на самому телефоні (як у Track Catcher, пункт 11) і відкрити встановлення.
# Підписується тим самим ключем, що й у GitHub, — ставиться поверх.
# --offline (пункт 12): без інтернету — Gradle бере все з кешу першої збірки.
set -u
offline=""
[ "${1:-}" = "--offline" ] && offline="--offline"
. "$(dirname -- "$0")/cl-common.sh"
cd "$CL_REPO" || exit 1

gradle="$HOME/gradle/gradle-8.13/bin/gradle"
[ -x "$gradle" ] || { fail "Не знайдено Gradle 8.13 ($gradle)."; info "Налаштування телефона — як для Track Catcher (docs/PHONE_SETUP.md)."; exit 1; }
if [ ! -f "$CL_PROPS" ]; then
  fail "Немає ключа підпису — без нього APK не встановиться поверх. Спершу пункт 7."
  exit 1
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/android-sdk/platforms" ]; then export ANDROID_HOME="$HOME/android-sdk"; fi
[ -n "${ANDROID_HOME:-}" ] || { fail "Не знайдено Android SDK (~/android-sdk)."; exit 1; }
export ANDROID_SDK_ROOT="$ANDROID_HOME"
props="$CL_REPO/android/local.properties"
grep -qxF "sdk.dir=$ANDROID_HOME" "$props" 2>/dev/null || printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$props"
cl_need_storage || exit 1
# Знімок каталогу (гілка data/catalog) — у APK як «запасний» каталог. Без інтернету — лишається попередній.
mkdir -p "$CL_REPO/android/app/src/main/assets/catalog"
if [ -z "$offline" ] && git fetch -q --depth 1 origin data/catalog 2>/dev/null; then
  git show FETCH_HEAD:catalog.json > "$CL_REPO/android/app/src/main/assets/catalog/catalog.json" 2>/dev/null && ok "Знімок каталогу: $(git show FETCH_HEAD:catalog-summary.txt 2>/dev/null | sed -n 2p)"
fi
log_dir="$CL_DOCS/logs"
mkdir -p "$log_dir"
log="$log_dir/local-build-$(date +%Y%m%d-%H%M%S).log"
version="$(cl_version)"
if [ -n "$offline" ]; then
  echo "Збираю Caravan Lean v$version на телефоні без інтернету…"
else
  echo "Збираю Caravan Lean v$version на телефоні… (перший раз — 5–10 хв, качає бібліотеки)"
fi
echo "Код — з цієї папки (останнє оновлення: пункт 1)."
# Збірка у фоні, а на екрані — час і що зараз робиться (щоб не здавалось, що зависло).
"$gradle" -p android :app:assembleRelease $offline --stacktrace --console=plain > "$log" 2>&1 &
pid=$!
start=$(date +%s)
while kill -0 "$pid" 2>/dev/null; do
  sleep 3
  el=$(( $(date +%s) - start ))
  step="$(grep -E '^> Task |Download |^> Configure' "$log" 2>/dev/null | tail -n 1 | sed 's/^> //; s#https\?://[^ ]*/##' | cut -c1-52)"
  printf '\r⏳ %d:%02d  %-52s' $((el / 60)) $((el % 60)) "${step:-готуюсь…}"
done
wait "$pid"
rc=$?
# Збірка скінчилась (вдало чи ні) — зупинити фоновий Gradle і Kotlin, щоб не тримали пам'ять телефона.
# Чому: A26 23.10 02:36 — після збірки лишались GradleDaemon (-Xmx2g) і ще 2 процеси java у підкачці
# (+1,3 ГБ підкачки, система закривала Claude і Caravan Lean). faric 23.10 02:40: «так».
"$gradle" --stop >/dev/null 2>&1 || true
if command -v pkill >/dev/null 2>&1; then pkill -f KotlinCompileDaemon >/dev/null 2>&1 || true; fi
echo "Фоновий Gradle зупинено — пам'ять телефона вільна."
el=$(( $(date +%s) - start ))
printf '\r%-70s\r' ""
echo "Час збірки: $((el / 60)) хв $((el % 60)) с"
if [ "$rc" = 0 ]; then
  apk="$CL_REPO/android/app/build/outputs/apk/release/app-release.apk"
  [ -s "$apk" ] || { fail "Збірка пройшла, але APK не знайдено."; exit 1; }
  dest_dir="$CL_DOCS/apk/v$version-local"
  dest="$dest_dir/Caravan-Lean-v$version-local-$(date +%Y%m%d-%H%M).apk"
  mkdir -p "$dest_dir"
  cp "$apk" "$dest"
  sha256sum "$dest" > "$dest.sha256"
  ok "Готово: Documents/Caravan Lean/apk/v$version-local/"
  # Для іншого (старого) телефона: той самий APK — на GitHub (меню 5 там). Без інтернету — просто пропускаємо.
  bash "$(dirname -- "$0")/cl-publish-phone-apk.sh" "$dest" || true
  echo "Відкриваю встановлення — натисни «Оновити»."
  cl_open "$dest" || warn "Відкрий файл у «Моїх файлах»."
else
  if [ -n "$offline" ] && grep -q "No cached version\|offline mode" "$log"; then
    fail "Без інтернету не вийшло: бібліотек ще немає в кеші."
    info "Один раз збери з інтернетом (пункт 11) — далі пункт 12 працюватиме офлайн."
    exit 1
  fi
  fail "Збірка не вдалась. Коротко:"
  sed -n '/What went wrong/,/^\* Try/p' "$log" | grep -v '^\* Try' | head -n 30
  info "Повний лог: Documents/Caravan Lean/logs/$(basename "$log")"
  exit 1
fi
