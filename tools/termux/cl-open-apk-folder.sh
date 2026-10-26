#!/usr/bin/env bash
# Відкрити в «Моїх файлах» папку з останнім APK Caravan Lean (з GitHub, зібраним тут чи на іншому телефоні).
set -u
. "$(dirname -- "$0")/cl-common.sh"
apk="$(ls -t "$CL_DOCS"/apk/*/*.apk 2>/dev/null | head -n 1)"
if [ -z "$apk" ]; then
  fail "На цьому телефоні ще немає жодного APK Caravan Lean."
  info "Пункт 2 — завантажити з GitHub; пункт 11 — зібрати тут."
  exit 1
fi
folder="$(basename "$(dirname "$apk")")"
echo "Папка: Documents › Caravan Lean › apk › $folder"
info "APK: $(basename "$apk")"
if cl_open_dir "Documents/Caravan Lean/apk/$folder"; then
  ok "Відкрито. Торкнись APK, щоб встановити."
else
  warn "Android не відкрив папку сам. Відкрий «Мої файли» → Documents → Caravan Lean → apk → $folder."
fi
