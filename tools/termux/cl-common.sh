#!/usr/bin/env bash
# Спільне для скриптів меню Caravan Lean.
CL_SLUG="faric-ua/caravan-lean-shop"
CL_REPO="${CL_REPO:-$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)}"
CL_DOCS="/storage/emulated/0/Documents/Caravan Lean"
CL_KEY="$HOME/.android/caravan-lean.jks"
CL_PROPS="$HOME/.android/caravan-lean-signing.properties"
CL_ALIAS="caravanlean"

ok() { printf '✅ %s\n' "$*"; }
info() { printf '   %s\n' "$*"; }
warn() { printf '⚠️  %s\n' "$*"; }
fail() { printf '❌ %s\n' "$*"; }

cl_version() {
  sed -n 's/^ *versionName = "\(.*\)"$/\1/p' "$CL_REPO/android/app/build.gradle.kts" 2>/dev/null | head -n 1
}

cl_need_storage() {
  if ! mkdir -p "$CL_DOCS" 2>/dev/null; then
    fail "Termux не має доступу до пам'яті телефона."
    info "Один раз: команда termux-setup-storage → «Дозволити»."
    return 1
  fi
}

cl_gh_ready() {
  command -v gh >/dev/null 2>&1 && gh auth status -h github.com >/dev/null 2>&1
}

cl_prop() {
  sed -n "s/^$1=//p" "$CL_PROPS" 2>/dev/null | head -n 1
}

# Відкрити папку пам'яті телефона в «Моїх файлах». Аргумент — шлях від /storage/emulated/0,
# напр. "Documents/Caravan Lean/apk/v0.1.4". Три спроби, як у Renault (A26 і M30s відкривають по-різному).
cl_open_dir() {
  local enc uri
  enc="$(printf '%s' "$1" | sed 's/%/%25/g; s/ /%20/g; s#/#%2F#g')"
  uri="content://com.android.externalstorage.documents/document/primary%3A$enc"
  if am start -a android.intent.action.VIEW -d "$uri" -t vnd.android.document/directory 2>&1 | grep -qi "error"; then
    :
  else
    return 0
  fi
  if am start -a android.intent.action.OPEN_DOCUMENT_TREE --eu android.provider.extra.INITIAL_URI "$uri" 2>&1 | grep -qi "error"; then
    return 1
  fi
  return 0
}

cl_open() {
  # Відкрити файл (APK → встановлення).
  if command -v termux-open >/dev/null 2>&1; then
    termux-open --view "$1" >/dev/null 2>&1 || termux-open "$1" >/dev/null 2>&1
  else
    return 1
  fi
}
