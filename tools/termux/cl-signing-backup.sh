#!/usr/bin/env bash
# Резервна копія ключа підпису в Documents/Caravan Lean/key-backup.
set -u
. "$(dirname -- "$0")/cl-common.sh"
if [ ! -f "$CL_KEY" ] || [ ! -f "$CL_PROPS" ]; then
  fail "Ключа ще немає — спершу пункт 7."
  exit 1
fi
cl_need_storage || exit 1
dest="$CL_DOCS/key-backup"
mkdir -p "$dest"
stamp="$(date +%Y%m%d-%H%M)"
cp "$CL_KEY" "$dest/caravan-lean-$stamp.jks"
cp "$CL_PROPS" "$dest/caravan-lean-signing-$stamp.properties"
ok "Копія: Documents/Caravan Lean/key-backup/ ($stamp)"
warn "Цей файл = право підписувати Caravan Lean. Нікому не пересилай і не клади в GitHub."
