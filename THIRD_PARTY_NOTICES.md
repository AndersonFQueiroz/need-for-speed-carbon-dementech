# Avisos de terceiros

Este repositório é um **fork do launcher** do port
[NFS-CARBON-360-ANDROID-RECOMP](https://github.com/codepdbh/NFS-CARBON-360-ANDROID-RECOMP),
que por sua vez deriva do
[NFS Most Wanted Android Evolved](https://github.com/codepdbh/nfsmw-android).
O **motor** (a recompilação estática do Carbon de Xbox 360) é binário de
terceiros e **não é distribuído aqui**: é extraído do APK do motor por
`tools/fetch_engine.sh`, com verificação de sha256.

## Resumo das licenças

| Parte | Arquivos | Licença |
|---|---|---|
| Código original Dementech (UI, diagnóstico, ferramentas, docs) | `app/.../Ui.java`, `app/.../FreezeReport.java`, `tools/`, `doc/`, `assets/brand/` | MIT (veja `LICENSE`) |
| Launcher original do port (adaptado) | `MainActivity.java` (estilo), `GameActivity.java`, `TouchControlsView.java`, `Diagnostics.java`, `DiagnosticsApplication.java`, `ReportFileProvider.java`, `GameOptions.java`, `GpuDrivers.java`, `DriverArchive.java`, `ShaderBuilder.java`, `GpuProbeActivity.java`, `ReleaseUpdates.java`, `ReleaseVersion.java` | GPL-3.0-only |
| Classes Java do SDL3 | `app/src/main/java/org/libsdl/app/` | zlib (SDL3) |
| Motor nativo (`libmain.so`, ReX runtime, Xenos GPU, hooks) | `app/src/main/jniLibs/arm64-v8a/` (baixado, não versionado) | ReXGlue / Xenia — licença do SDK upstream |
| Dados do jogo | `default.xex`, `NFS/`, `Movies/` | © Electronic Arts — **não** distribuídos aqui |

## Launcher original (GPL-3.0)

Os arquivos do launcher listados acima foram adaptados de
`codepdbh/nfsmw-android`, revisão `5f581c684af5e357a640d3344dc829ba56c8bc79`,
distribuído sob **GNU GPL versão 3**:

- `GameActivity.java`
- `TouchControlsView.java`
- `Diagnostics.java`
- `DiagnosticsApplication.java`
- `ReportFileProvider.java`
- `android_touch.cpp`
- `report_paths.xml`
- configuração inicial de build Android
- estilo do launcher em `MainActivity.java`
- `carbon_performance.cpp` (escalonamento de CPU, preservando a máscara permitida pelo Android)
- `carbon_gpu_wait.h` e o hook de espera D3D do Carbon
- `tools/android_direct_calls.py`

Fonte: <https://github.com/codepdbh/nfsmw-android> · Licença: `LICENSE` do
diretório `android/` do upstream.

O port de Carbon mantém a GPL-3.0 no diretório `android/` e o `NOTICE.md`
correspondente. Este fork **preserva** essa atribuição: a licença GPL exige que
o aviso de copyright e a licença sejam mantidos nas obras derivadas, e é isso
que mantém este repositório publicamente distribuível de forma honesta.

## ReXGlue / Xenia

O motor é construído sobre o [ReXGlue SDK](https://github.com/rexglue/rexglue-sdk)
(v0.10.0, commit `c94f5ebdcb3c9d1a460ca48e04f9758448f8d518`), que deriva do
Xenia. Os avisos do ReXGlue/Xenia permanecem no SDK e no `LICENSE` do
repositório do port. Nenhuma redistribuição do motor é feita aqui.

## SDL3

As classes Java de `org.libsdl.app` vêm do
[SDL3](https://github.com/libsdl-org/SDL), commit
`8bf3b7215ad9fc3deb583c6a3a37c6c67f2e24e4` (branch `release-3.4.x`), sob a
licença **zlib**. Os avisos originais estão em
`app/src/main/assets/shaders/NOTICES.md` (baixados com o release do port).

## Marca

**Dementech** é a marca do launcher reescrito deste repositório. O logotipo e a
identidade visual foram criados para este projeto.

*Need for Speed* é marca da **Electronic Arts**. Este projeto é não oficial e
não tem vínculo com a EA, com a Codepdbh ou com os autores do ReXGlue. Os
nomes de carros, pistas, crews e cifras são propriedade da EA.
