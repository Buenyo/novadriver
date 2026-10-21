# Funções comuns dos scripts. Uso: source "$(dirname "$0")/_common.sh"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG_RELEASE="app.novadrive.launcher"
PKG_DEBUG="app.novadrive.launcher.debug"
ACTIVITY="app.novadrive.launcher.MainActivity"

need_adb() {
  command -v adb >/dev/null 2>&1 || { echo "ERRO: adb não encontrado. Veja docs/adb-setup.md." >&2; exit 1; }
  local n
  n=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)
  if [ -z "${ANDROID_SERIAL:-}" ] && [ "$n" -ne 1 ]; then
    echo "ERRO: $n aparelhos conectados. Conecte um só ou defina ANDROID_SERIAL." >&2
    adb devices >&2
    exit 1
  fi
}

ash() { adb shell "$@" | tr -d '\r'; }

# Pacote instalado (release tem prioridade sobre debug).
installed_pkg() {
  if ash pm list packages "$PKG_RELEASE" | grep -qx "package:$PKG_RELEASE"; then echo "$PKG_RELEASE"
  elif ash pm list packages "$PKG_DEBUG" | grep -qx "package:$PKG_DEBUG"; then echo "$PKG_DEBUG"
  fi
}

confirm() {
  read -r -p "$1 [s/N] " ans
  [[ "$ans" =~ ^[sS]$ ]]
}
