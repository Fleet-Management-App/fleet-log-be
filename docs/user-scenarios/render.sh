#!/usr/bin/env bash
# Renders every US-XX.svg in this folder to a 2x PNG with headless Edge (no ImageMagick on this machine).
set -euo pipefail
cd "$(dirname "$0")"
EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
TMP="$(mktemp -d)"
for svg in US-*.svg; do
  name="${svg%.svg}"
  w=$(grep -o 'width="[0-9]*"' "$svg" | head -1 | grep -o '[0-9]*')
  h=$(grep -o 'height="[0-9]*"' "$svg" | head -1 | grep -o '[0-9]*')
  { echo '<!doctype html><html><head><style>html,body{margin:0;background:#fff}svg{display:block}</style></head><body>'
    cat "$svg"; echo '</body></html>'; } > "$TMP/$name.html"
  "$EDGE" --headless=new --disable-gpu --no-sandbox --hide-scrollbars \
    --force-device-scale-factor=2 --virtual-time-budget=3000 \
    --window-size="$w,$h" --user-data-dir="$TMP/prof-$name" \
    --screenshot="$(cygpath -w "$PWD/$name.png")" "file:///$(cygpath -m "$TMP/$name.html")" 2>/dev/null
  echo "$name.png ${w}x${h}"
done
rm -rf "$TMP" 2>/dev/null || true  # Edge may still hold the last profile open
