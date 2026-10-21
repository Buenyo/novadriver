# Conectar ADB na central K2001N

Guia para conectar o computador à central via ADB, por USB ou por Wi-Fi.
Tudo aqui só usa comandos padrão do Android/ADB. Os nomes de menus da central
variam entre firmwares; quando o texto diz [SUPOSIÇÃO], confira na sua tela.

## 1. Instalar o ADB no computador

- Linux (Arch/Omarchy): `sudo pacman -S android-tools`, ou use o `platform-tools`
  do Android SDK (`~/Android/Sdk/platform-tools/adb`).
- Confira: `adb version`

## 2. Ativar as Opções do desenvolvedor na central

1. Abra **Configurações → Sobre / Informações do sistema**.
2. Toque 7 vezes em **Número da versão** (Build number).
   - [SUPOSIÇÃO] Em muitas centrais chinesas as Configurações do fabricante
     escondem esse item. Nesse caso, procure as **Configurações Android originais**
     (às vezes em "Configurações → Mais", ou um app separado "Settings"). Algumas
     pedem senha de fábrica para áreas avançadas (códigos comuns na internet:
     `8888`, `1234`, `0000`, `3368`, `1617`). Não sei qual vale para este firmware.
3. Entre em **Opções do desenvolvedor** e ative **Depuração USB**.

## 3. Conexão por USB

A central precisa de uma porta USB que funcione em **modo dispositivo (OTG/peripheral)**.

- [SUPOSIÇÃO] Em muitas centrais com Allwinner T3, só uma das portas USB (às vezes
  marcada como USB1 ou a do chicote principal) aceita modo dispositivo, e pode
  ser preciso um cabo **USB-A macho ↔ USB-A macho**. Se nenhuma porta funcionar,
  use Wi-Fi (seção 4).

Passos:

```bash
adb kill-server
adb start-server
adb devices
```

- Na central deve aparecer o pedido **"Permitir depuração USB?"** → marque
  "Sempre permitir" e confirme.
- `adb devices` deve listar o aparelho com o status `device`.
  - `unauthorized` → aceite o pedido na tela da central.
  - nada listado → porta/cabo errado, ou depuração USB desligada.
  - No Linux, se der `no permissions`, instale as regras udev
    (`sudo pacman -S android-udev`) e reconecte o cabo.

## 4. Conexão por Wi-Fi (Android 7: precisa de USB uma vez, ou de root)

No Android 7 **não existe** o "Depuração por Wi-Fi" com pareamento (isso só veio
no Android 11). Há dois caminhos:

### 4a. Ativar via USB (recomendado)

Com o USB funcionando, uma única vez por inicialização:

```bash
adb tcpip 5555
adb shell ip -f inet addr show wlan0    # anote o IP, ex.: 192.168.0.50
adb connect 192.168.0.50:5555
adb devices                              # deve listar 192.168.0.50:5555  device
```

Depois pode tirar o cabo. Ao desligar a central (ou cortar a ignição), o modo TCP
volta ao padrão e precisa repetir `adb tcpip 5555`.

### 4b. Sem USB

- [SUPOSIÇÃO] Alguns firmwares trazem a opção "ADB por rede" / "ADB over network"
  nas Opções do desenvolvedor, ou ela é ativada pelo valor `service.adb.tcp.port=5555`
  (exige root). Confira se a sua tela tem essa opção. Se não tiver e o USB não
  funcionar, não há caminho oficial sem root.

### Rede

- Central e computador precisam estar **na mesma rede**. O mais simples no carro é
  ligar o roteador do celular e conectar os dois a ele.
- Para descobrir o IP da central sem ADB: **Configurações → Wi-Fi → (rede) → detalhes**.

## 5. Mais de um aparelho

Se `adb devices` listar vários, escolha um:

```bash
export ANDROID_SERIAL=192.168.0.50:5555
```

Todos os scripts do projeto respeitam `ANDROID_SERIAL`.

## 6. Rodar a descoberta (Fase 0)

```bash
chmod +x scripts/discover.sh
# aperte o botão HOME na central antes, para registrar o launcher em foco
scripts/discover.sh
```

O resultado fica em `docs/device-info.txt`. O script **só lê** informações; não
instala nem altera nada.

## 7. Desconectar

```bash
adb disconnect          # Wi-Fi
adb usb                 # volta o adbd ao modo USB
```
