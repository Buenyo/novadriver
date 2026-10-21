# Open Headunit no K2001N (Allwinner T3) — no lugar do ZLink

Legenda: **[FATO]** = lido no repositório oficial (README/código, consultado em 2026-10-20).
**[SUPOSIÇÃO]** = recomendação para testar; ainda não validada nesta central.

## 1. O que é

- **[FATO]** Open Headunit (antes "Headunit Revived") transforma um aparelho Android em receptor de
  Android Auto. Código: https://github.com/andreknieriem/open-headunit — licença AGPL-3.0.
- **[FATO]** applicationId: `com.andrerinas.headunitrevived`, igual nos flavors `github` e `playstore`
  (app/build.gradle.kts). O flavor `github` usa minSdk 16, então roda no Android 7.
- **[FATO]** A versão do GitHub é gratuita (Releases, arquivo `com.andrerinas.headunitrevived_<versão>.apk`,
  use o que **não** termina em `_debug`). A da Play Store é paga.
- **[FATO]** A última versão estável na data da consulta era a v.3.5.0 (2026-10-17).
- O NovaDrive **não presume** o pacote: detecta pelo ID acima, por qualquer pacote/nome com
  "headunit", ou pelo app escolhido em Configurações → Android Auto.

- **[FATO, emulador]** O APK v3.5.0 traz bibliotecas nativas para `armeabi-v7a` (32 bits, como o T3),
  `arm64-v8a`, `x86` e `x86_64`. A activity de abertura é `com.andrerinas.openheadunit.main.MainActivity`.
- **[FATO, emulador]** Na primeira abertura, o Open Headunit liga o **modo carro** do Android (ícone de
  volante na barra de status). O NovaDrive continua sendo a HOME e declara `CAR_DOCK` por segurança.

## 2. Instalar

```bash
# Baixe o APK das Releases no computador e instale:
adb install -r com.andrerinas.headunitrevived_X.Y.Z.apk
```

Sem computador: copie para um pendrive, libere "Fontes desconhecidas" (o botão está na tela de ajuda
do Android Auto no NovaDrive) e instale pelo gerenciador de arquivos da central.

## 3. Fazer o USB abrir o Open Headunit (e não o ZLink)

Hoje, ao conectar o celular no USB, a central abre o ZLink. O Android decide quem abre por um destes
mecanismos:

1. **[FATO, Android]** Apps declaram `android.hardware.usb.action.USB_DEVICE_ATTACHED` no manifest. Se só um
   app declara, ou se o usuário marcou "Usar por padrão para este dispositivo USB", ele abre direto.
   **[FATO]** O Open Headunit declara esse evento.
2. **[SUPOSIÇÃO]** O ZLink pode ter um serviço próprio que monitora o USB (sem passar pelo seletor do
   Android), iniciado no boot.

O `scripts/discover.sh` coleta as seções `USB:` e `BOOT:` para saber qual é o caso. Os passos abaixo
resolvem os dois e são todos reversíveis:

### Passo A — ver a situação

```bash
scripts/zlink.sh status
```

Mostra o(s) pacote(s) com "zlink" no nome, se estão ativos, e a lista de apps que respondem ao USB.

### Passo B — limpar o "abrir por padrão" do ZLink (sem ADB)

Configurações do Android → Apps → ZLink → **Abrir por padrão** → **Limpar padrões**. Depois conecte o
celular: o Android deve perguntar qual app usar → escolha **Open Headunit** → marque "Usar por padrão
para este dispositivo USB".

### Passo C — se ainda abrir o ZLink: desativar o ZLink (reversível)

```bash
scripts/zlink.sh disable   # pede confirmação; usa pm disable-user (não apaga o app)
scripts/zlink.sh enable    # desfaz
```

- **[SUPOSIÇÃO]** Alguns firmwares bloqueiam desativar apps de sistema sem root. Se o comando falhar, o
  passo B é a alternativa.
- **[SUPOSIÇÃO]** Em algumas centrais o ZLink também cuida do espelhamento/CarPlay. Desativá-lo tira
  esses recursos. Por isso o `enable` existe.

### Passo D — dentro do Open Headunit

- **[FATO]** Existe a opção "Listen for USB Devices" (o pedido de permissão USB do Android vem ligado por
  padrão) e listas de permissão/bloqueio de dispositivos USB. Deixe "ouvir dispositivos USB" ligado.

## 4. Configurações para tentar no Allwinner T3

O T3 tem 4 núcleos Cortex-A7 a cerca de 1,2 GHz e GPU Mali-400 MP2. **[SUPOSIÇÃO]** O decodificador de
vídeo por hardware dele lida bem com H.264 até 1080p, mas não com H.265.

| Ajuste | Valor para começar | Base |
|---|---|---|
| Assistente | Rode o **Auto-Optimization Wizard** primeiro | [FATO] existe no app |
| Codec de vídeo | **H.264** | [FATO] README: use h264 se o aparelho não suporta h265; h265 quebrado trava em "Android is starting" |
| Resolução | **800x480 (480p)**; se fluir bem, tente 1280x720 | [SUPOSIÇÃO] 1440p exige h265 [FATO] |
| FPS | **30** | [SUPOSIÇÃO] o modo 60 fps foi otimizado para MediaTek [FATO] |
| DPI | Fixo, comece em **160** (1024x600) ou **190** se o Maps não aceitar toque | [FATO] README sugere < 200 (ex.: 190) quando o toque no Maps falha |
| View-Mode | Comece com **SurfaceView**; se ficar tela preta ao voltar de tela apagada, teste **Texture** | [FATO] os dois modos existem; [SUPOSIÇÃO] qual é melhor no T3 |
| Force Scale | Ligue se a imagem sair cortada no SurfaceView | [FATO] "Force Scale Option for older devices on surface view" |
| Force Legacy Decoder | Teste se travar ou ficar preto | [FATO] existe (criado para MediaTek) |
| Decoder Allwinner | Use versão ≥ 2.3.0 | [FATO] changelog: "Fixed video decoder settings for allwinner devices" |
| Tela cheia | "Immersive" | [FATO] opções Immersive / Status Only / None |
| Modo noturno | "Automatic (System)" ou sinal de farol (ILL+) se a central expuser | [FATO] as duas opções existem |
| GPS do carro | Ligado (entrega a posição da central ao celular) | [FATO] "Car GPS: deliver the head unit's fix" |
| Áudio | Se cortar ou picotar: "Static Audio Focus" e aumentar o buffer de áudio | [FATO] as opções existem |
| Música na HOME do NovaDrive | Áudio → **"Faixa atual do AA na sessão de mídia"** ligado | [FATO] código v3.5: vem **desligado**; desligado, a sessão mostra só "Android Auto" / "Conectado" |

## 5. Android Auto sem fio (Bluetooth + Wi-Fi)

O Android Auto sem fio não usa só Bluetooth: o Bluetooth faz o "aperto de mão" e o vídeo/áudio vão
por **Wi-Fi**. Fontes: README e código do Open Headunit v3.5.

- **[FATO, código]** O **Modo Nativo** abre um canal RFCOMM no `BluetoothAdapter` **do Android** (UUID
  do Android Auto e HFP) e depois conecta por **Wi-Fi Direct** ou pelo **hotspot da central**.
- **[SUPOSIÇÃO]** Muitas centrais T3 usam um **módulo Bluetooth separado**, controlado pelo app de
  telefone do fabricante. Nesse caso o Android não "vê" o celular pareado e o Modo Nativo não funciona.
  O README do Open Headunit só documenta um caminho para módulo externo em centrais **FYT**
  (`/dev/auto_serial`), não para esta.
- **Para saber qual é o caso:** rode `scripts/discover.sh` e veja as seções `BLUETOOTH:` e `WI-FI:`.
  - `dumpsys bluetooth_manager` com `enabled: true`, endereço e o celular entre os pareados = o
    Android enxerga o Bluetooth, então o Modo Nativo pode funcionar.
  - Vazio, desligado ou sem o celular, mesmo pareado pelo app do fabricante = módulo separado, então
    use o dongle.
  - Sem `android.hardware.wifi.direct` em `pm list features`: tente o transporte **Headunit Hotspot**.

| Opção | Precisa | Base |
|---|---|---|
| **Dongle USB de Android Auto sem fio** | Só o dongle no USB; o Open Headunit continua no modo USB | [FATO] README: "Most Reliable", independe da versão do Android Auto |
| Modo Nativo | Bluetooth do Android + Wi-Fi Direct ou hotspot | [FATO] Configurações → Android Auto Mode → Native Mode |
| Headunit Server | Modo desenvolvedor do Android Auto no celular, mesma rede Wi-Fi | [FATO] README, opção sem root |
| Wireless Helper | Android Auto até 17.3 (17.4+ só com root) | [FATO] README |

**Recomendação [SUPOSIÇÃO]:** se o `discover.sh` mostrar módulo separado, o dongle é o único caminho
sem fio confiável. O Wi-Fi do T3 provavelmente é só 2,4 GHz, então mesmo o Modo Nativo pode travar o
vídeo. Confira na seção `WI-FI:`. O NovaDrive não muda nada nisso: ele só abre o Open Headunit, e o
cartão "Tocando agora" funciona igual por USB ou sem fio.

**Música por Bluetooth sem Android Auto:** o cartão mostra qualquer sessão de mídia.
**[SUPOSIÇÃO]** O app de Bluetooth do fabricante pode não publicar uma sessão. Para conferir: toque
música pelo Bluetooth e abra Configurações → Diagnóstico de mídia.

## 6. Ordem de teste no carro

1. Celular com Android Auto atualizado, cabo USB de dados bom (curto).
2. Abrir o Open Headunit pelo botão Android Auto do NovaDrive e rodar o assistente.
3. Conectar o USB → confirmar que abre o Open Headunit (não o ZLink).
4. Navegar 10 min com Maps/Waze + música: anotar travadas, atraso de toque, áudio.
5. Ajustar um item da tabela por vez.

## 7. Fora do escopo

O NovaDrive só **abre** o Open Headunit. O protocolo Android Auto, o vídeo e o áudio são todos dele.
CarPlay não é suportado.
