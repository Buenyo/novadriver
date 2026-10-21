#!/usr/bin/env bash
# Logcat filtrado do NovaDrive (+ travamentos e tempo de abertura do sistema).
#   scripts/logs.sh        -> acompanha ao vivo
#   scripts/logs.sh -c     -> limpa o buffer antes
set -euo pipefail
source "$(dirname "$0")/_common.sh"
need_adb
[ "${1:-}" = "-c" ] && adb logcat -c
# Tag do app (NovaDrive), crashes (AndroidRuntime) e "Displayed" do ActivityManager.
adb logcat -v time NovaDrive:V AndroidRuntime:E ActivityManager:I '*:S' \
  | grep --line-buffered -E "NovaDrive|novadrive|FATAL|AndroidRuntime"
