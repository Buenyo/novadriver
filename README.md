# NovaDrive

Launcher (tela inicial) leve para a central multimídia **K2001N / NUOVEIDA T3** (Allwinner T3,
Android 7, 2 GB), com o **Open Headunit** no lugar do ZLink para o Android Auto.

- HOME em paisagem com relógio, clima (Open-Meteo), "Tocando agora" de qualquer player,
  6 atalhos configuráveis e botão **Android Auto** em destaque. Toques de no mínimo 64 dp.
- Gaveta de apps com busca (sem acento), configurações, modo performance, tema automático
  pelo nascer e pôr do sol.
- Kotlin + Jetpack Compose (Material 3), MVVM + StateFlow, DI manual. Nenhuma dependência além de
  AndroidX e Kotlin. Tamanho do APK de release: cerca de 1,6 MB.

> ⚠️ **Antes de definir como padrão no carro, leia [Caminho de volta](#caminho-de-volta-obrigatório).**

## Compilar

Requisitos: JDK 17 e Android SDK com `platforms;android-37.0` e `build-tools;37.0.0`.

```bash
./gradlew testDebugUnitTest      # testes de unidade
./gradlew assembleRelease        # APK em app/build/outputs/apk/release/
```

- **Assinatura:** rode `scripts/make-keystore.sh` uma vez. A chave fica em `~/.novadrive/`, fora do
  git, e o script cria o `keystore.properties`. Sem esse arquivo, o release é assinado com a chave de
  debug: serve para testar, mas não para atualizar depois com a chave definitiva.
- **Visual HR-V:** as imagens do carro e o logo ficam em `branding/`, que **não vai para o git**
  porque o repositório é público e são imagens de marca. Com a pasta presente, o build usa essas
  imagens; sem ela, usa um fundo genérico.

## Instalar e usar

Conecte o ADB ([docs/adb-setup.md](docs/adb-setup.md)) e rode:

```bash
scripts/install.sh               # release
scripts/install.sh release --aot # + compilação AOT (abre mais rápido no Cortex-A7)
scripts/logs.sh                  # logs do app
scripts/perf.sh                  # mede abertura a frio e RAM
```

Para definir como launcher padrão: aperte **HOME** na central → escolha **NovaDrive** → **Sempre**.

## Caminho de volta (OBRIGATÓRIO)

Antes de tudo, rode `scripts/discover.sh` e **anote o pacote do launcher original** (seção `LAUNCHER`
de `docs/device-info.txt`). Há várias formas de voltar, da mais simples para a mais drástica:

1. **Pelo próprio NovaDrive:** Configurações → *Sistema e caminho de volta* → **Abrir \<launcher original\>**
   ou **Escolher a tela inicial padrão**.
2. **Modo de segurança automático:** se o NovaDrive travar 3 vezes em 2 minutos, ele abre uma tela
   simples (sem Compose) com botões para o launcher original e as configurações.
3. **Configurações do Android:** Apps → NovaDrive → *Abrir por padrão* → **Limpar padrões**. Depois
   aperte HOME e escolha o launcher original.
4. **Por ADB, sem apagar nada:**
   ```bash
   adb shell pm disable-user --user 0 app.novadrive.launcher   # volta ao original na hora
   adb shell pm enable app.novadrive.launcher                  # desfaz
   ```
5. **Por ADB, trocando o padrão** (verificado no Android 7.0 do emulador):
   ```bash
   adb shell cmd package set-home-activity <pacote/activity do launcher original>
   ```
   No Android 7 o próprio comando `cmd` pode fechar com erro (SIGSEGV) depois de aplicar a troca. Isso
   é um bug do shell, não do NovaDrive: confira apertando HOME.
6. **Por ADB, removendo:** `adb uninstall app.novadrive.launcher`.

Se a central só tiver o NovaDrive e o original como telas iniciais, as opções 4 e 5 fazem o Android
voltar sozinho ao original.

## Android Auto (Open Headunit) e ZLink

Veja [docs/open-headunit.md](docs/open-headunit.md): instalação, configurações para o Allwinner T3 e
como fazer o USB abrir o Open Headunit em vez do ZLink (`scripts/zlink.sh status|disable|enable`,
tudo reversível).

## Acesso às notificações

Necessário para o cartão "Tocando agora": é a única forma oficial de um launcher ler as sessões de
mídia de outros apps. O app explica isso e abre a tela certa. Se o firmware esconder essa tela, use
`scripts/grant-notifications.sh`, que adiciona o NovaDrive e preserva os outros apps já liberados.

## Performance (Cortex-A7 + 2 GB)

| Item | Como |
|---|---|
| Abertura a frio < 1,5 s | Tema XML com fundo escuro (sem flash), Compose sem bibliotecas extras, baseline profiles das bibliotecas (`profileinstaller`), AOT opcional (`--aot`) |
| RAM < 120 MB | Papel de parede decodificado uma vez, no tamanho exato da tela (cerca de 2,4 MB a 1024x600); capas com no máximo 256 px (160 px no modo performance); cache de ícones de 6 MB (LRU) |
| Nada em segundo plano | Mídia, clima, relógio e rede só rodam com a HOME visível (`onStart`/`onStop`). Fora isso, só o NotificationListener, que o sistema mantém |
| Animações baratas | Só fade de 160 a 260 ms. O **modo performance** (e "animações desligadas" do sistema) zera tudo e também tira a barra de progresso |
| Sem blur nem sombras pesadas | Cartões com cor translúcida sólida; o blur não existe na API 24 |

## Segurança e conectividade

- **Só HTTPS** (`cleartextTrafficPermitted=false`). O Android 7.0 não tem a raiz *ISRG Root X1*
  (Let's Encrypt, usada pelo Open-Meteo), então ela vai embutida no app
  (SHA-256 `96:BC:EC:06:…:08:C6`), junto das raízes do sistema.
- O clima envia só a posição **arredondada para cerca de 1 km**; nada mais sai do aparelho. Sem contas,
  sem rastreamento.
- `allowBackup=false`. Só a Activity HOME e o serviço protegido por `BIND_NOTIFICATION_LISTENER_SERVICE`
  são exportados.
- **Rede instável no carro:** cache de clima de 30 min; nova tentativa quando a rede volta (hotspot
  do celular); espera progressiva de 1 a 30 min; aviso quando a data/hora da central está errada
  (central sem bateria de RTC quebra o HTTPS).
- Release com R8 e encolhimento de recursos; logs `v`/`d` removidos.

## Estrutura

```
app/src/main/java/app/novadrive/launcher/
  NovaDriveApp.kt       AppContainer (DI manual), ciclo de vida
  MainActivity.kt       HOME, ações, modo de segurança
  apps/                 lista de apps (LauncherApps), atalhos, abertura de apps
  media/                NotificationListener + MediaSessionManager
  weather/              Open-Meteo, localização
  settings/             DataStore
  system/               CrashGuard, relógio/rede, diagnóstico
  ui/                   Compose: home, gaveta, configurações, onboarding, tema
scripts/                discover, install, logs, perf, zlink, grant-notifications, make-keystore
docs/                   adb-setup, open-headunit, device-info (gerado)
```

## Status

| Fase | Situação |
|---|---|
| 0 Descoberta | Script pronto. **Falta rodar na central** |
| 1 a 5 | Implementadas e compilando, com testes de unidade e lint limpo. Testadas no emulador API 24 (1024x600). **Falta validar no carro** |

### Verificado no emulador Android 7.0 (API 24, 1024x600, 160 dpi)

- O botão HOME abre o NovaDrive quando ele é o padrão; voltar ao Launcher3 com `set-home-activity`
  e com `disable-user`/`enable` funcionou.
- A permissão de notificações abre direto no item do NovaDrive; depois de conceder, o cartão funciona.
- Clima real via HTTPS: o sistema **não tem** a ISRG Root X1 (hash `6187b673` ausente entre as 148 raízes),
  e mesmo assim o app buscou o Open-Meteo graças à raiz embutida.
- O Open Headunit v3.5.0 oficial é detectado, e o botão Android Auto o abre. O APK traz bibliotecas
  `armeabi-v7a`, compatíveis com o T3.
- O Open Headunit liga o **modo carro** do Android. O HOME continuou no NovaDrive, que também declara
  `CAR_DOCK` por segurança. Com as barras do sistema maiores, a HOME entra no layout compacto.
- RAM (PSS) no emulador: cerca de 46 MB. **O tempo de abertura medido no emulador não vale**: sem
  aceleração (KVM), a CPU é emulada em software. Meça no carro com `scripts/perf.sh`.
