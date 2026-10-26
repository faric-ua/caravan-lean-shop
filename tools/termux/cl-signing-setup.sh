#!/usr/bin/env bash
# Один раз: створити власний ключ підпису Caravan Lean на телефоні і передати його в секрети GitHub.
# Ключ потрібен, щоб кожна нова версія ставилась поверх старої (без видалення застосунку).
set -u
. "$(dirname -- "$0")/cl-common.sh"

if ! command -v keytool >/dev/null 2>&1; then
  fail "Немає Java (keytool)."
  info "Java 17 уже ставилась для збірки інших застосунків; якщо ні — напиши Claude."
  exit 1
fi
if ! cl_gh_ready; then
  fail "gh не увійшов у GitHub — без цього ключ не передати."
  exit 1
fi
mkdir -p "$HOME/.android" && chmod 700 "$HOME/.android"

# Ключ уже в GitHub, а на цьому телефоні його немає (напр., старий телефон) — новий НЕ створюємо:
# він перезаписав би секрети, і нові версії перестали б ставитись поверх на основному телефоні.
if [ ! -f "$CL_KEY" ] && gh secret list -R "$CL_SLUG" 2>/dev/null | cut -f1 | grep -qx CL_KEYSTORE_B64; then
  fail "Ключ підпису вже є в GitHub — його створив інший телефон. Новий не створюю."
  info "На цьому телефоні ключ не потрібен: встановлюй через пункт 2 або 5."
  exit 1
fi

if [ -f "$CL_KEY" ] && [ -f "$CL_PROPS" ]; then
  ok "Ключ уже є на телефоні — новий не створюю (інакше нові версії не встановляться поверх)."
else
  if [ -f "$CL_KEY" ] || [ -f "$CL_PROPS" ]; then
    fail "Знайдено лише частину ключа ($CL_KEY / $CL_PROPS). Нічого не чіпаю — напиши Claude."
    exit 1
  fi
  pass="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 28)"
  if ! keytool -genkeypair -keystore "$CL_KEY" -storetype PKCS12 -storepass "$pass" -keypass "$pass" \
    -alias "$CL_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Caravan Lean, O=faric-ua, C=UA" >/dev/null 2>&1; then
    fail "keytool не створив ключ."
    exit 1
  fi
  umask 077
  printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=%s\nkeyPassword=%s\n' "$CL_KEY" "$pass" "$CL_ALIAS" "$pass" > "$CL_PROPS"
  chmod 600 "$CL_KEY" "$CL_PROPS"
  ok "Ключ створено: ~/.android/caravan-lean.jks"
fi

pass="$(cl_prop storePassword)"
alias_name="$(cl_prop keyAlias)"
cert="$(keytool -list -v -keystore "$CL_KEY" -storepass "$pass" -alias "$alias_name" 2>/dev/null \
  | sed -n 's/^.*SHA256: *//p' | head -n 1 | tr -d ': ' | tr 'A-F' 'a-f')"
if [ -z "$cert" ]; then
  fail "Не вдалося прочитати сертифікат ключа."
  exit 1
fi
info "Сертифікат: ${cert:0:16}…"

echo "Передаю ключ у секрети GitHub…"
base64 -w0 "$CL_KEY" | gh secret set CL_KEYSTORE_B64 -R "$CL_SLUG" >/dev/null || { fail "Не вдалося записати CL_KEYSTORE_B64"; exit 1; }
gh secret set CL_STORE_PASSWORD -R "$CL_SLUG" --body "$pass" >/dev/null || { fail "CL_STORE_PASSWORD"; exit 1; }
gh secret set CL_KEY_ALIAS -R "$CL_SLUG" --body "$alias_name" >/dev/null || { fail "CL_KEY_ALIAS"; exit 1; }
gh secret set CL_KEY_PASSWORD -R "$CL_SLUG" --body "$pass" >/dev/null || { fail "CL_KEY_PASSWORD"; exit 1; }
gh secret set CL_CERT_SHA256 -R "$CL_SLUG" --body "$cert" >/dev/null || { fail "CL_CERT_SHA256"; exit 1; }
ok "Ключ у GitHub (5 секретів)."

echo "Запускаю збірку релізу…"
if gh workflow run android.yml -R "$CL_SLUG" --ref main >/dev/null 2>&1; then
  ok "Збірка запущена. Через 5–7 хв — пункт 2 «Встановити останній реліз»."
else
  warn "Збірку не запущено автоматично — напиши Claude, він запустить."
fi
echo
info "Порада: пункт 8 — зробити резервну копію ключа."
