# Motor (binário de terceiros)

Este diretório **não contém** o motor. Ele é o contrato: o que o launcher
espera do binário e como obtê-lo de forma verificável.

## O que é o motor

A recompilação estática do *Need for Speed Carbon* de Xbox 360 para ARM64
Android, com o renderizador Vulkan do ReXGlue. Ele vem em binários
(`.so`) dentro do APK do motor (build `v0.3.3`, arm64-v8a).

## Como obter

```bash
ENGINE_APK=/caminho/engine-arm64.apk tools/fetch_engine.sh
```

O script:

1. lê o APK do motor de `ENGINE_APK` (ou `tools/engine/engine-arm64.apk`);
2. confere o sha256 contra `SHA256SUMS` quando essa variável é definida;
3. extrai `lib/arm64-v8a/*.so` para `app/src/main/jniLibs/arm64-v8a/`;
4. extrai `assets/shaders/*` para `app/src/main/assets/shaders/`.

Nada disso é versionado: `.gitignore` cobre `app/src/main/jniLibs/` e
`app/src/main/assets/shaders/`.

## Binários esperados (arm64-v8a)

| Arquivo | Papel |
|---|---|
| `libmain.so` | app nativo do port + recompilação do Carbon |
| `librexruntime.so` | runtime do ReXGlue (presenter, command processor, logging) |
| `librexgpu-xenos.so` | emulação de GPU Xenos (renderizador alternativo) |
| `libSDL3.so` | SDL3 (janela, input, áudio) |
| `libc++_shared.so` | runtime C++ |
| `libmain_hook.so`, `libfile_redirect_hook.so`, `libgsl_alloc_hook.so`, `libhook_impl.so` | hooks de integração com o Android |
| `libnfscarbon_diagnostics.so` | sondas de diagnóstico |
| `librex_android_gpu_driver.so` | carregamento de driver Vulkan importado |

## Contrato com o Java

Nomes fixos — mudá-los quebra o carregamento das bibliotecas:

| Lado | Nome |
|---|---|
| JNI | `Java_com_nfscarbon_android_GameActivity_nativeSetStretch` |
| JNI | `Java_com_nfscarbon_android_GameActivity_nativeFrameTimeUs` |
| JNI | `Java_com_nfscarbon_android_TouchControlsView_nativeSetTouchState` |
| JNI | `Java_com_nfscarbon_android_Diagnostics_nativeGpuReport` |
| Java | `org.libsdl.app.SDLActivity` (superclasse de `GameActivity`) |

## Configuração lida pelo motor

- `nfscarbon.toml` — na pasta de dados do app; as opções do launcher
  (linha de comando) têm prioridade sobre o arquivo.
- Argumentos extras para teste: um por linha, em
  `Android/data/com.nfscarbon.android/files/args.txt`, prefixados com `--`.
  Exemplo: `--log_level=warn`.

## Dados do jogo

O motor exige uma cópia do Xbox 360 **English PAL** (Title ID `454107EC`,
`default.xex` com sha256 `b1e42391…`) em `/sdcard/NFSCARBON/`, com `NFS/` e
`Movies/`. O launcher confere o sha256 antes de subir o motor. Esses arquivos
**não** são distribuídos aqui.
