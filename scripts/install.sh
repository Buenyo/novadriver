#!/usr/bin/env bash
# Compila e instala o NovaDrive na central.
#   scripts/install.sh            -> release (R8 ligado, o que vai para o carro)
#   scripts/install.sh debug      -> debug (pacote app.novadrive.launcher.debug, convive com o release)
#   scripts/install.sh release --aot   -> também compila AOT completo (abre mais rápido no Cortex-A7)
set -euo pipefail
source "$(dirname "$0")/_common.sh"
VARIANT="${1:-release}"
AOT="${2:-}"
need_adb

case "$VARIANT" in
  release) TASK=assembleRelease; APK="$ROOT/app/build/outputs/apk/release/app-release.apk"; PKG=$PKG_RELEASE ;;
  debug)   TASK=assembleDebug;   APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk";     PKG=$PKG_DEBUG ;;
  *) echo "Uso: $0 [release|debug] [--aot]"; exit 1 ;;
esac

(cd "$ROOT" && ./gradlew --quiet "$TASK")
echo "Instalando $(basename "$APK") ($(du -h "$APK" | cut -f1))..."
if ! adb install -r "$APK"; then
  echo
  echo "Falhou. Se o erro for INSTALL_FAILED_UPDATE_INCOMPATIBLE, a assinatura mudou"
  echo "(ex.: trocou a chave de debug pela de release). Desinstale antes:"
  echo "  adb uninstall $PKG"
  echo "ATENÇÃO: se o NovaDrive for o launcher padrão, a central volta ao launcher original ao desinstalar."
  exit 1
fi

if [ "$AOT" = "--aot" ]; then
  # 'cmd package compile' existe a partir do Android 7.0 [verificar na central; se falhar, o app funciona igual].
  echo "Compilando AOT (speed)..."
  ash cmd package compile -m speed -f "$PKG" || echo "Aviso: compilação AOT não suportada neste firmware."
fi

echo
echo "OK. Para usar como tela inicial: aperte HOME na central e escolha NovaDrive > Sempre."
echo "Para voltar ao launcher original, veja a seção 'Caminho de volta' no README."
