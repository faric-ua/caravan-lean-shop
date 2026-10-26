#!/usr/bin/env bash
# Пункт 14: GitHub заново парсить сайт магазину (workflow catalog.yml) і кладе свіжий знімок каталогу
# в гілку data/catalog. Наступна збірка APK бере його як «запасний» каталог (коли нема інтернету).
# Застосунок і сам оновлює каталог із сайту — цей пункт для знімка в APK і для перевірки, що парсер живий.
set -u
. "$(dirname -- "$0")/cl-common.sh"
cl_gh_ready || { fail "gh не увійшов у GitHub — запустити не можу."; exit 1; }

echo "Запускаю парсинг сайту на GitHub…"
if ! gh workflow run catalog.yml -R "$CL_SLUG" --ref main >/dev/null 2>&1; then
  fail "Не вдалося запустити. Напиши Claude."
  exit 1
fi
sleep 6
run="$(gh run list -R "$CL_SLUG" -w catalog.yml -L 1 --json databaseId -q '.[0].databaseId' 2>/dev/null)"
[ -n "$run" ] || { warn "Запущено, але номер запуску не видно. Перевір пізніше пунктом 3."; exit 0; }
echo "Чекаю (зазвичай 2–6 хв)…"
start=$(date +%s)
while :; do
  st="$(gh run view "$run" -R "$CL_SLUG" --json status,conclusion -q '"\(.status) \(.conclusion)"' 2>/dev/null)"
  case "$st" in completed*) break ;; esac
  el=$(( $(date +%s) - start ))
  printf '\r⏳ %d:%02d  %s   ' $((el / 60)) $((el % 60)) "${st%% *}"
  [ "$el" -gt 1200 ] && { echo; warn "Довго — перевір пізніше пунктом 3."; exit 0; }
  sleep 10
done
echo
if [ "${st#completed }" = "success" ]; then
  summary="$(gh api "repos/$CL_SLUG/contents/catalog-summary.txt?ref=data/catalog" -q .content 2>/dev/null | base64 -d 2>/dev/null)"
  ok "Каталог оновлено."
  [ -n "$summary" ] && printf '%s\n' "$summary" | sed 's/^/   /'
else
  fail "Парсинг не вдався ($st). Напиши Claude — він подивиться."
  exit 1
fi
