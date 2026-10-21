#!/usr/bin/env bash
# Cria a chave de assinatura de release FORA do repositório e o keystore.properties (ignorado pelo git).
# Guarde um backup da chave: sem ela, atualizações exigem desinstalar o app.
set -euo pipefail
source "$(dirname "$0")/_common.sh"
DIR="$HOME/.novadrive"
JKS="$DIR/release.jks"
mkdir -p "$DIR" && chmod 700 "$DIR"
[ -f "$JKS" ] && { echo "Já existe: $JKS"; exit 1; }
read -r -s -p "Senha da chave (mín. 6 caracteres): " PASS; echo
keytool -genkeypair -v -keystore "$JKS" -alias novadrive -keyalg RSA -keysize 3072 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=NovaDrive, O=Pessoal, C=BR"
chmod 600 "$JKS"
umask 177
cat > "$ROOT/keystore.properties" <<PROPS
storeFile=$JKS
storePassword=$PASS
keyAlias=novadrive
keyPassword=$PASS
PROPS
echo "OK: $JKS e keystore.properties criados. Faça backup de $JKS e da senha."
