#!/usr/bin/env bash
# NovaDrive — Fase 0: descoberta do hardware via ADB.
# Coleta informações da central e salva em docs/device-info.txt.
#
# Uso:
#   scripts/discover.sh                 # usa o único aparelho conectado
#   ANDROID_SERIAL=192.168.0.50:5555 scripts/discover.sh   # escolhe o aparelho
#
# Só LÊ informações. Não instala, não altera e não reinicia nada na central.

set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs/device-info.txt"
mkdir -p "$ROOT/docs"

if ! command -v adb >/dev/null 2>&1; then
  echo "ERRO: adb não encontrado no PATH. Veja docs/adb-setup.md." >&2
  exit 1
fi

# Verifica se há exatamente um aparelho (ou ANDROID_SERIAL definido).
mapfile -t DEVICES < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [ -z "${ANDROID_SERIAL:-}" ]; then
  if [ "${#DEVICES[@]}" -eq 0 ]; then
    echo "ERRO: nenhum aparelho autorizado. Rode 'adb devices' e veja docs/adb-setup.md." >&2
    adb devices >&2
    exit 1
  elif [ "${#DEVICES[@]}" -gt 1 ]; then
    echo "ERRO: mais de um aparelho conectado. Defina ANDROID_SERIAL:" >&2
    printf '  %s\n' "${DEVICES[@]}" >&2
    exit 1
  fi
fi

# Executa um comando no aparelho, remove \r e nunca aborta o script.
sh_dev() { adb shell "$@" 2>&1 | tr -d '\r'; }

section() { printf '\n===== %s =====\n' "$1"; }

# Roda um comando com título; mostra o comando exato para facilitar a conferência.
run() {
  local title="$1"; shift
  section "$title"
  echo "\$ adb shell $*"
  sh_dev "$@"
}

{
  echo "NovaDrive — device-info"
  echo "Gerado em: $(date '+%Y-%m-%d %H:%M:%S %z')"
  echo "adb: $(adb version | head -1)"
  echo "Serial: ${ANDROID_SERIAL:-${DEVICES[0]}}"

  section "IDENTIFICAÇÃO / VERSÃO"
  for p in \
    ro.build.version.sdk \
    ro.build.version.release \
    ro.build.version.preview_sdk \
    ro.build.version.security_patch \
    ro.build.display.id \
    ro.build.fingerprint \
    ro.product.model \
    ro.product.manufacturer \
    ro.product.brand \
    ro.product.device \
    ro.product.name \
    ro.board.platform \
    ro.hardware \
    ro.product.cpu.abi \
    ro.product.cpu.abilist \
    ro.opengles.version \
    ro.sf.lcd_density \
    dalvik.vm.heapsize \
    dalvik.vm.heapgrowthlimit \
    ro.config.low_ram \
    persist.sys.locale \
    persist.sys.timezone
  do
    printf '%-36s = %s\n' "$p" "$(sh_dev getprop "$p")"
  done

  # Em algumas centrais chinesas a versão exibida em "Sobre" é falsificada.
  # Listamos todas as props de versão e o build.prop para comparar com o SDK.
  run "VERSÃO: todas as props de versão/sdk" 'getprop | grep -iE "version|sdk|fingerprint"'
  run "VERSÃO: /system/build.prop (versão)" 'grep -iE "version|sdk" /system/build.prop'

  run "TELA: wm size" wm size
  run "TELA: wm density" wm density
  run "TELA: dumpsys display (resumo)" 'dumpsys display | grep -m 5 -E "mBaseDisplayInfo|DisplayDeviceInfo"'

  run "MEMÓRIA: /proc/meminfo" cat /proc/meminfo
  run "MEMÓRIA: dumpsys meminfo (resumo)" 'dumpsys meminfo | grep -A 20 -E "^Total RAM|Total PSS by category" | head -40'
  run "CPU: /proc/cpuinfo" cat /proc/cpuinfo
  run "CPU: frequências (kHz)" 'for c in /sys/devices/system/cpu/cpu[0-9]*; do echo "$c: max=$(cat $c/cpufreq/cpuinfo_max_freq 2>/dev/null) cur=$(cat $c/cpufreq/scaling_cur_freq 2>/dev/null) gov=$(cat $c/cpufreq/scaling_governor 2>/dev/null)"; done'
  run "ARMAZENAMENTO: df" df /data /system

  run "PACOTES: completo (com caminho do APK)" pm list packages -f
  run "PACOTES: só de sistema" pm list packages -s
  run "PACOTES: instalados pelo usuário" pm list packages -3

  section "PACOTES: filtro (zlink|radio|headunit|navi|music|carplay|auto|launcher|home|media|bt|bluetooth|mcu|can|avin|camera|dvr|setting)"
  sh_dev pm list packages -f \
    | grep -iE 'zlink|radio|fm|headunit|navi|music|carplay|auto|launcher|home|media|player|bt|bluetooth|mcu|can|avin|camera|dvr|setting|car' \
    || echo "(nenhum resultado)"

  # da central pode ser diferente, então tentamos vários métodos e mostramos tudo que funcionar.
  # então tentamos em ordem e mostramos tudo que funcionar.
  run "LAUNCHER: cmd package resolve-activity (ok no Android 7.0 AOSP)" \
    cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
  run "LAUNCHER: cmd package query-activities HOME (ok no Android 7.0 AOSP)" \
    cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
  run "LAUNCHER: preferências salvas (dumpsys package preferred-xml)" 'dumpsys package preferred-xml 2>/dev/null | head -60'
  run "LAUNCHER: activities com categoria HOME (dumpsys package r)" \
    'dumpsys package r | grep -B 2 -A 6 "android.intent.category.HOME" | head -80'
  run "LAUNCHER: activity em foco agora (aperte HOME na central antes de rodar)" \
    'dumpsys activity activities | grep -m 5 -E "mFocusedActivity|mResumedActivity|mFocusedApp"'
  run "LAUNCHER: pilha HOME (dumpsys activity recents/stack)" \
    'dumpsys activity activities | grep -m 10 -E "Stack #0|realActivity|mActivityType|HOME"'

  # Por que o ZLink abre sozinho ao conectar o USB: quem declara os eventos de USB
  # e se há preferência "usar por padrão" salva no serviço de USB.
  run "USB: apps que respondem a USB_DEVICE/ACCESSORY_ATTACHED" \
    'dumpsys package r | grep -A 8 -E "android.hardware.usb.action.USB_(DEVICE|ACCESSORY)_ATTACHED:"'
  run "USB: dumpsys usb (modo, host, preferências)" 'dumpsys usb | head -80'
  run "USB: pacotes zlink (detalhes)" \
    'for p in $(pm list packages | sed "s/^package://" | grep -i zlink); do echo "--- $p"; dumpsys package $p | grep -E "codePath=|versionName=|enabled=|pkgFlags=|android.hardware.usb|BOOT_COMPLETED|Service" | head -30; done'
  run "BOOT: apps que iniciam no boot (BOOT_COMPLETED)" \
    'dumpsys package r | grep -A 40 "android.intent.action.BOOT_COMPLETED:" | head -50'

  run "SERVIÇOS: listeners de notificação habilitados" settings get secure enabled_notification_listeners
  run "SERVIÇOS: sessões de mídia ativas" 'dumpsys media_session | head -60'
  run "REDE: Wi-Fi (IP para ADB sem fio)" 'ip -f inet addr show wlan0 2>/dev/null || ifconfig wlan0'
  run "WEBVIEW / Play Services" 'pm list packages | grep -iE "webview|com.google.android.gms|com.android.vending"'
} > "$OUT"

echo "OK: informações salvas em $OUT"
echo "Linhas: $(wc -l < "$OUT")"
echo "Cole o conteúdo desse arquivo na conversa (ou pelo menos as seções IDENTIFICAÇÃO, TELA, PACOTES: filtro e LAUNCHER)."
