#!/usr/bin/env bash
# Concede o acesso a notificações via ADB, PRESERVANDO os outros apps já liberados.
# Use só se a central não mostrar a tela "Acesso a notificações".
set -euo pipefail
source "$(dirname "$0")/_common.sh"
need_adb
PKG="$(installed_pkg)"
[ -n "$PKG" ] || { echo "NovaDrive não instalado."; exit 1; }
COMP="$PKG/app.novadrive.launcher.media.MediaListenerService"
CUR="$(ash settings get secure enabled_notification_listeners)"
case ":$CUR:" in *":$COMP:"*) echo "Já concedido."; exit 0 ;; esac
if [ -z "$CUR" ] || [ "$CUR" = "null" ]; then VAL="$COMP"; else VAL="$CUR:$COMP"; fi
echo "Antes: ${CUR:-<vazio>}"
ash settings put secure enabled_notification_listeners "$VAL"
echo "Depois: $(ash settings get secure enabled_notification_listeners)"
echo "Volte para a tela inicial; o cartão 'Tocando agora' deve aparecer."
