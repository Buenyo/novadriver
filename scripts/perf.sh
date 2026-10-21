#!/usr/bin/env bash
# Mede abertura a frio (meta < 1,5 s) e RAM (meta < 120 MB) na central.
#   scripts/perf.sh [repetições]
set -euo pipefail
source "$(dirname "$0")/_common.sh"
need_adb
N="${1:-5}"
PKG="$(installed_pkg)"
[ -n "$PKG" ] || { echo "NovaDrive não instalado."; exit 1; }

echo "Pacote: $PKG — $N aberturas a frio"
total=0
for i in $(seq 1 "$N"); do
  ash am force-stop "$PKG"
  sleep 2
  # -W espera o primeiro frame e mostra TotalTime (ms) — disponível no Android 7.
  t=$(ash am start -W -n "$PKG/$ACTIVITY" | awk -F': ' '/TotalTime/ {print $2}')
  echo "  #$i: ${t:-?} ms"
  total=$((total + ${t:-0}))
  sleep 4
done
echo "Média: $((total / N)) ms (meta: < 1500 ms)"
echo
echo "RAM (PSS) após abrir:"
ash dumpsys meminfo "$PKG" | grep -E "TOTAL|Java Heap|Native Heap|Graphics" | head -5
echo "(meta: TOTAL < 122880 kB = 120 MB)"
