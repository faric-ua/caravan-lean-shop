#!/usr/bin/env bash
# Паспорт сайту: движок і версії, PHP, сервер, хостинг (IP → мережа), DNS, пошта, SSL, домен.
# Лише публічні дані й звичайні GET-запити (без входу в адмінку, без сканерів). Запускає site-stack.yml.
# Використання: bash scripts/site_stack.sh <папка-результату>
set -u
OUT="${1:-site-stack}"
mkdir -p "$OUT"
UA="Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
R="$OUT/report.md"
say() { printf '%s\n' "$*" >> "$R"; }
get() { curl -sS -L --max-time 25 -A "$UA" "$@"; }
code() { curl -s -o /dev/null -L --max-time 25 -A "$UA" -w '%{http_code} %{size_download}B %{content_type}' "$1"; }

: > "$R"
say "# Паспорт сайту shop.caravan-lean.com.ua"
say "Знято: $(date -u +%Y-%m-%dT%H:%MZ) (GitHub Actions)"

say "" "## Заголовки сервера"
for u in https://shop.caravan-lean.com.ua/ http://shop.caravan-lean.com.ua/ https://caravan-lean.com.ua/ https://shop.caravan-lean.com.ua/ru/; do
  say "### $u" '```'
  curl -sS -I -L --max-time 25 -A "$UA" "$u" 2>&1 | tr -d '\r' | grep -iE '^(HTTP|server|x-powered-by|set-cookie|location|content-type|x-content|strict|cache-control|via|cf-|x-cache|x-server|x-proxy)' | sed 's/=[^;]*;/=…;/' >> "$R"
  say '```'
done

say "" "## Версії движка (публічні файли-маніфести)"
for p in \
  /administrator/manifests/files/joomla.xml \
  /language/en-GB/en-GB.xml \
  /language/ru-RU/ru-RU.xml \
  /administrator/components/com_virtuemart/virtuemart.xml \
  /administrator/components/com_virtuemart/virtuemart.j15.xml \
  /templates/leo_sport/templateDetails.xml \
  /media/system/js/mootools-core.js \
  /media/jui/js/jquery.min.js \
  /README.txt /LICENSE.txt /web.config.txt /htaccess.txt ; do
  c="$(code "https://shop.caravan-lean.com.ua$p")"
  say "- \`$p\` → $c"
  case "$c" in
    200*)
      body="$(get "https://shop.caravan-lean.com.ua$p" | head -c 200000)"
      v="$(printf '%s' "$body" | grep -oE '<(version|creationDate|name|author)>[^<]*' | head -6 | tr '\n' ' ')"
      [ -n "$v" ] && say "  - $v"
      case "$p" in
        *mootools-core.js) say "  - $(printf '%s' "$body" | grep -oE "version: ?'[^']+'|MooTools[^,]{0,30}" | head -2 | tr '\n' ' ')" ;;
        /README.txt) say "  - $(printf '%s' "$body" | grep -iE 'joomla|version|[0-9]\.[0-9]' | head -4 | tr '\n' ' ' | cut -c1-300)" ;;
      esac ;;
  esac
done
home="$(get https://shop.caravan-lean.com.ua/)"
say "- generator: $(printf '%s' "$home" | grep -o '<meta name="generator"[^>]*>' | head -1)"
say "- jQuery/MooTools/шаблон у коді: $(printf '%s' "$home" | grep -oE '(jquery/[0-9.]+|mootools-[a-z]+\.js|templates/[a-z_0-9]+|com_virtuemart|com_[a-z]+)' | sort -u | tr '\n' ' ')"
say "- розмір головної: $(printf '%s' "$home" | wc -c) байт"

say "" "## Інші сайти на тому ж домені"
for u in https://caravan-lean.com.ua/ https://shop.caravan-lean.com.ua/ru/; do
  b="$(get "$u")"
  say "### $u"
  say "- title: $(printf '%s' "$b" | grep -oE '<title>[^<]*' | head -1 | sed 's/<title>//')"
  say "- generator: $(printf '%s' "$b" | grep -o '<meta name="generator"[^>]*>' | head -1)"
  say "- ознаки: $(printf '%s' "$b" | grep -oE '(com_[a-z]+|wp-content|catalog/view/theme|templates/[a-z_0-9]+|bitrix|opencart|prestashop|jshopping)' | sort -u | head -12 | tr '\n' ' ')"
done

say "" "## DNS"
for d in caravan-lean.com.ua shop.caravan-lean.com.ua www.caravan-lean.com.ua; do
  say "### $d" '```'
  for t in A AAAA CNAME NS MX TXT; do
    r="$(dig +short "$d" "$t" 2>/dev/null | tr '\n' ' ')"
    [ -n "$r" ] && say "$t: $r"
  done
  say '```'
done

say "" "## Хостинг (IP → мережа / власник)"
for ip in $(dig +short shop.caravan-lean.com.ua A; dig +short caravan-lean.com.ua A; dig +short "$(dig +short caravan-lean.com.ua MX | sort -n | head -1 | awk '{print $2}')" A) ; do
  case "$ip" in *[!0-9.]*) continue ;; esac
  say "### $ip"
  say "- зворотний DNS: $(dig +short -x "$ip" | tr '\n' ' ')"
  say '```'
  curl -sS --max-time 15 "https://ipinfo.io/$ip/json" 2>/dev/null | grep -E '"(hostname|city|region|country|org)"' >> "$R"
  whois "$ip" 2>/dev/null | grep -iE '^(netname|descr|org-name|organisation|country|role|origin|address)' | head -10 >> "$R"
  say '```'
done
for ns in $(dig +short caravan-lean.com.ua NS); do
  say "- NS $ns → $(dig +short "$ns" A | head -1) · $(dig +short -x "$(dig +short "$ns" A | head -1)" 2>/dev/null | head -1)"
done

say "" "## Домен (whois)"
say '```'
whois caravan-lean.com.ua 2>/dev/null | grep -iE '^(domain|registrar|created|modified|expires|status|nserver|organization|org|source|mnt-by)' | grep -viE 'e-mail|phone|fax|address|person' | head -20 >> "$R"
say '```'

say "" "## SSL"
say '```'
echo | openssl s_client -connect shop.caravan-lean.com.ua:443 -servername shop.caravan-lean.com.ua 2>/dev/null | openssl x509 -noout -issuer -subject -dates -ext subjectAltName 2>/dev/null >> "$R"
echo | openssl s_client -connect shop.caravan-lean.com.ua:443 -servername shop.caravan-lean.com.ua 2>/dev/null | grep -E 'Protocol|Cipher' | head -2 >> "$R"
say '```'

say "" "## Що відкрито назовні (лише перевірка «чи відповідає», без входу)"
for p in /administrator/ /phpmyadmin/ /cpanel /installation/ /configuration.php-dist /logs/ /tmp/; do
  say "- \`$p\` → $(code "https://shop.caravan-lean.com.ua$p")"
done
for port in 21 22 25 110 143 2082 2083 2222 3306 8080 8443; do
  if timeout 4 bash -c "</dev/tcp/shop.caravan-lean.com.ua/$port" 2>/dev/null; then say "- порт $port: відкритий"; fi
done
echo "Готово: $R"
