#!/usr/bin/env bash
# Downloads the map label glyphs (Noto Sans) into the APK's assets, so the offline map needs no
# network. Run before building; CI does this automatically. Output is git-ignored.
set -euo pipefail
out="$(dirname "$0")/../src/main/assets/map/fonts"
ranges="0-255 256-511 512-767 1536-1791 1792-2047 8192-8447 8448-8703 64256-64511 64512-64767 64768-65023 65024-65279"
for pair in "NotoSans-Regular:Noto%20Sans%20Regular" "NotoSans-Bold:Noto%20Sans%20Bold"; do
  name="${pair%%:*}"; remote="${pair#*:}"
  mkdir -p "$out/$name"
  for r in $ranges; do
    [ -s "$out/$name/$r.pbf" ] && continue
    curl -fsSL --retry 3 "https://tiles.openfreemap.org/fonts/$remote/$r.pbf" -o "$out/$name/$r.pbf" \
      || { echo "warning: $name $r not available"; rm -f "$out/$name/$r.pbf"; }
  done
done
test -s "$out/NotoSans-Regular/0-255.pbf" && test -s "$out/NotoSans-Bold/0-255.pbf"
echo "Fonts ready in $out"
