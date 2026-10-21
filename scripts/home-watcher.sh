#!/usr/bin/env bash
# Liga/desliga o "Vigia da tela inicial" (serviço de acessibilidade) via ADB, PRESERVANDO os
# outros serviços de acessibilidade já ativos. Use se a central esconder a tela Acessibilidade.
#   scripts/home-watcher.sh status | on | off
set -euo pipefail
source "$(dirname "$0")/_common.sh"
need_adb
PKG="$(installed_pkg)"
[ -n "$PKG" ] || { echo "NovaDrive não instalado."; exit 1; }
COMP="$PKG/app.novadrive.launcher.system.HomeWatcherService"
CUR="$(ash settings get secure enabled_accessibility_services)"
[ "$CUR" = "null" ] && CUR=""

case "${1:-status}" in
  status)
    case ":$CUR:" in *":$COMP:"*) echo "Vigia: LIGADO" ;; *) echo "Vigia: desligado" ;; esac
    echo "Serviços ativos: ${CUR:-<nenhum>}"
    ;;
  on)
    case ":$CUR:" in *":$COMP:"*) echo "Já ligado."; exit 0 ;; esac
    if [ -z "$CUR" ]; then VAL="$COMP"; else VAL="$CUR:$COMP"; fi
    ash settings put secure enabled_accessibility_services "$VAL"
    ash settings put secure accessibility_enabled 1
    echo "Depois: $(ash settings get secure enabled_accessibility_services)"
    ;;
  off)
    VAL="$(printf '%s' "$CUR" | tr ':' '\n' | grep -vxF "$COMP" | paste -sd: - || true)"
    if [ -z "$VAL" ]; then
      ash settings delete secure enabled_accessibility_services >/dev/null
      ash settings put secure accessibility_enabled 0
    else
      ash settings put secure enabled_accessibility_services "$VAL"
    fi
    echo "Vigia desligado. Serviços ativos: ${VAL:-<nenhum>}"
    ;;
  *) echo "Uso: $0 [status|on|off]"; exit 1 ;;
esac
