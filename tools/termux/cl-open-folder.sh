#!/usr/bin/env bash
# Відкрити Documents/Caravan Lean (APK, логи збірки, резервна копія ключа) у «Моїх файлах».
set -u
. "$(dirname -- "$0")/cl-common.sh"
cl_need_storage || exit 1
if cl_open_dir "Documents/Caravan Lean"; then
  ok "Відкрито: Documents › Caravan Lean"
else
  warn "Не відкрилось. Відкрий «Мої файли» → Documents → Caravan Lean."
fi
