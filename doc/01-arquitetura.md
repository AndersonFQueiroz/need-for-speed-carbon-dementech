# doc/01 — Arquitetura

## Visão geral

```
┌──────────────────────── Activity (Java) ─────────────────────────┐
│ MainActivity (Dementech)     GameActivity    GpuProbeActivity    │
│  ├ aba Jogo                  ├ SDLActivity   └ teste de GPU      │
│  ├ aba Diagnóstico           ├ TouchControlsView                │
│  └ aba Créditos              ├ ShaderBuilder (WebView + WASM)    │
│                              └ Diagnostics                        │
└───────────────┬──────────────────────────────────────────────────┘
                │ JNI (nomes fixos: GameActivity.*, TouchControlsView.*)
┌───────────────▼──────────────���───────────────────────────────────┐
│ Motor nativo (binário de terceiros, pinado por sha256)            │
│  libmain.so (jogo nativo) · librexruntime · librexgpu-xenos       │
│  xenos · libSDL3 · hooks (main, file_redirect, gsl_alloc)         │
│  ReXGlue: presenter, command processor, logging, cvar              │
└───────────────┬──────────────────────────────────────────────────┘
                │ sistema de arquivos virtual (XamContent)
┌───────────────▼──────────────────────────────────────────────────┐
│ /data/user/0/com.nfscarbon.android/files/                        │
│   nfscarbon/user/<XUID>/454107EC/00000001/ALIAS_ANDERSON  (save) │
│   nfscarbon/user/…/nfscarbon_shaders.nfsp                (shaders)│
│ /sdcard/Jogos/NFSCARBON/{default.xex,NFS/,Movies/}              (jogo)  │
│ /sdcard/Android/data/com.nfscarbon.android/files/logs/carbon.log │
└──────────────────────────────────────────────────────────────────┘
```

## Componentes

### `MainActivity` (Dementech)
Telas, abas, diagnóstico e perfis. Mantém a lógica de importar cópia, checar
o executável, importar driver, gerar shaders e subir o jogo.

### `GameActivity` (upstream)
Estende `org.libsdl.app.SDLActivity`. Monta a linha de comando do motor:

```
--game_data_root=…  --user_data_root=…  --cache_root=…  --log_file=…
--user_language=…  --render_target_path_vulkan=fbo  --vulkan_dynamic_rendering=false
--present_letterbox=false  … + GameOptions.arguments(context)
```

As opções do launcher **vencem** o `nfscarbon.toml`.

### `ShaderBuilder` (upstream)
Compila a biblioteca de shaders no próprio aparelho (WebView + WASM do DXC e do
HLSL do motor). Roda uma vez; o resultado fica em `nfscarbon_shaders.nfsp`.

### `Ui` (Dementech)
Paleta e componentes. Único lugar com cor literal.

### `FreezeReport` (Dementech)
Lê a cauda do `carbon.log` e extrai o estado da apresentação. Mesma lógica do
`tools/diagnose_freeze.py`, em Java, para funcionar dentro do app.

## Onde o save vive

O motor não enxerga `/sdcard` diretamente: o *hook* de redirecionamento de
arquivos e a camada de conteúdo virtual (XamContent) do ReXGlue mapeiam
`/data/user/0/com.nfscarbon.android/files/nfscarbon/user/<XUID>/…` para o
jogo. É por isso que o launcher consegue ler e escrever o save **sem root**:
ele é o dono do diretório privado do app.

## Contrato com o motor (não quebrar)

| Item | Valor | Por quê |
|---|---|---|
| Classes JNI | `com.nfscarbon.android.GameActivity`, `…TouchControlsView` | o símbolo JNI é gerado do nome da classe |
| Métodos nativos | `nativeSetTouchState`, `nativeSetStretch`, `nativeFrameTimeUs`, `nativeGpuReport` | chamada direta do `.so` |
| `applicationId` | `com.nfscarbon.android` (até a v0.2) | guarda o save e as preferências |
| Config | `nfscarbon.toml` | lido pelo motor na pasta de dados |
| Shaders | `files/nfscarbon/user/…/nfscarbon_shaders.nfsp` | cache do pipeline |
| Log | `files/logs/carbon.log` (externo) | lido pela aba Diagnóstico |
| Pasta do jogo | `/sdcard/Jogos/NFSCARBON` | `default.xex` + `NFS/` + `Movies/` |

## Decisões de build

- **Sem NDK**: o motor já vem pronto; recompilá-lo é o item `1.0`.
- **Java, sem Kotlin**: o toolchain do celular não tem compilador Kotlin, e o
  upstream é Java.
- **UI por código**: sem layouts XML, para não depender de Android Studio.
- **Assinatura fora do Gradle**: a chave fica com o mantenedor; o Gradle gera
  APK sem assinatura e o `apksigner` assina.
