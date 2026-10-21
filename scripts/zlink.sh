#!/usr/bin/env bash
# Faz o USB abrir o Open Headunit em vez do ZLink. Tudo aqui é REVERSÍVEL.
#   scripts/zlink.sh status    -> mostra o ZLink e quem responde ao evento de USB
#   scripts/zlink.sh disable   -> desativa o ZLink (não apaga nada)
#   scripts/zlink.sh enable    -> reativa o ZLink (desfaz o disable)
# O nome do pacote do ZLink NÃO é presumido: é procurado na central ("zlink" no nome).
# Para forçar um pacote: ZLINK_PKG=nome.do.pacote scripts/zlink.sh disable
set -euo pipefail
source "$(dirname "$0")/_common.sh"
need_adb
CMD="${1:-status}"

find_zlink() {
  if [ -n "${ZLINK_PKG:-}" ]; then echo "$ZLINK_PKG"; return; fi
  ash pm list packages | sed 's/^package://' | grep -i zlink || true
}

usb_handlers() {
  # Tabela de resolução de activities: quem declara USB_DEVICE_ATTACHED / USB_ACCESSORY_ATTACHED.
  ash dumpsys package r | awk '
    /Activity Resolver Table/ {inres=1}
    /Receiver Resolver Table/ {inres=0}
    inres && /android.hardware.usb.action.USB_(DEVICE|ACCESSORY)_ATTACHED:/ {show=1; print; next}
    show && /^      [a-z]/ {print; next}
    show {show=0}'
}

case "$CMD" in
  status)
    echo "== Pacotes com 'zlink' =="
    Z=$(find_zlink); echo "${Z:-(nenhum)}"
    for p in $Z; do
      echo "-- $p"
      ash dumpsys package "$p" | grep -E "codePath=|enabled=|versionName=|pkgFlags=" | head -6
      ash pm list packages -d | grep -qx "package:$p" && echo "   ESTADO: DESATIVADO" || echo "   ESTADO: ativo"
    done
    echo
    echo "== Apps que respondem a USB conectado =="
    usb_handlers || true
    echo
    echo "== Open Headunit =="
    ash pm list packages | grep -i headunit || echo "(não instalado)"
    ;;
  disable)
    Z=$(find_zlink)
    [ -n "$Z" ] || { echo "Nenhum pacote com 'zlink' encontrado."; exit 1; }
    [ "$(echo "$Z" | wc -l)" -eq 1 ] || { echo "Vários pacotes encontrados; use ZLINK_PKG=..."; echo "$Z"; exit 1; }
    echo "Vai DESATIVAR: $Z (reversível com: scripts/zlink.sh enable)"
    confirm "Continuar?" || exit 0
    # disable-user funciona sem root na maioria dos firmwares [SUPOSIÇÃO: alguns fabricantes bloqueiam].
    ash pm disable-user --user 0 "$Z"
    echo "Pronto. Conecte o celular no USB: o Android deve perguntar qual app usar."
    echo "Escolha o Open Headunit e marque 'Usar por padrão para este dispositivo USB'."
    ;;
  enable)
    Z=$(ZLINK_PKG="${ZLINK_PKG:-}" find_zlink)
    [ -n "$Z" ] || Z=$(ash pm list packages -d | sed 's/^package://' | grep -i zlink || true)
    [ -n "$Z" ] || { echo "Nenhum pacote com 'zlink' encontrado."; exit 1; }
    for p in $Z; do ash pm enable "$p"; done
    ;;
  *) echo "Uso: $0 [status|disable|enable]"; exit 1 ;;
esac
