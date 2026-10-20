# agents.md — como trabalhar neste repositório

Instruções para agentes de IA (e para humanos) que mexem neste projeto.

## O que este repositório é

O **launcher** (a casca Android) do port *NFS Carbon 360*. O **motor** (a
recompilação estática do jogo de Xbox 360) é binário de terceiros, baixado
por `tools/fetch_engine.sh` e **não é versionado aqui**.

## Regras de ouro

1. **Nunca altere o contrato com o motor.** Estes nomes são fixos porque os
   símbolos JNI do `.so` dependem deles:
   - classes: `com.nfscarbon.android.GameActivity`, `com.nfscarbon.android.TouchControlsView`
   - métodos nativos: `nativeSetTouchState`, `nativeSetStretch`, `nativeFrameTimeUs`, `nativeGpuReport`
   - `applicationId`: `com.nfscarbon.android` (até a v0.2 — mudar exige migração do save)
   - asset de configuração do motor: `nfscarbon.toml`
   - diretório de shaders: `files/nfscarbon/user`
   - pasta do jogo: `/sdcard/NFSCARBON` com `default.xex`, `NFS/`, `Movies/`
2. **Nunca distribua o jogo.** `default.xex`, `NFS/` e `Movies/` são da
   Electronic Arts e não entram no repositorio, nem em issue, nem em log.
3. **Nunca troque os *values* das opções por tradução.** `GameOptions.Option`
   tem `values` (vai para o motor como cvar) e `labels` (só texto da tela).
   Traduza apenas `labels` e `title`.
4. **Assinatura é local.** A chave fica fora do repositório (`.gitignore`).
   Assine sempre com a **mesma** chave das versões anteriores, senão o Android
   recusa a instalação e o save é perdido.
5. **Não mexa no motor para "consertar" o congelamento** sem medir antes:
   leia `doc/02-congelamento.md` e use `tools/diagnose_freeze.py`.

## Antes de qualquer mudança

```bash
# 1. leia o diagnóstico do problema
cat doc/02-congelamento.md

# 2. compile e instale (no próprio celular)
tools/fetch_engine.sh
./gradlew :app:assembleRelease
apksigner sign --ks <sua-chave>.jks --ks-key-alias t --ks-pass pass:<senha> \
  --key-pass pass:<senha> --v1-signing-enabled false \
  --out app-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
adb install -r app-release.apk
```

## Convenções de código

- **Java puro** (sem Kotlin): o toolchain do build não tem compilador Kotlin e
  o upstream é Java.
- **UI por código** (sem XML de layout): o launcher foi escrito assim e
  evita dependência de Android Studio. Novos componentes entram em `Ui.java`.
- Identidade visual: paleta e helpers ficam em `Ui.java`. Nada de cor
  literal fora dali (o resto do app usa `Ui.*`).
- Textos de interface: **pt-BR**, sem acento obrigatório nos *labels* de
  cvar (para casar com o valor do motor).
- Nomes de método em português quando a lógica é do Dementech; código
  derivado do upstream mantém o nome original (para deixar a origem
  rastreável).
- Comentários explicam **por quê**, não o óbvio.

## Validação

| O que mudou | Como validar |
|---|---|
| UI / texto | `adb install -r` e conferir as três abas; print em `doc/img/` |
| Opção do motor | JOGAR → sessão curta → conferir `carbon.log` com `tools/diagnose_freeze.py` |
| Log/diagnóstico | `python3 tools/diagnose_freeze.py --sessions <logs>` |
| Motor | nunca reinstale um APK de assinatura diferente; backup do save antes |

## Limites conhecidos

- O build no celular é lento (~2–6 min) e consome memória; veja
  `doc/04-build-termux.md`.
- `aapt2` oficial do build-tools é x86_64: no ARM64 use o do Termux
  (`android.aapt2FromMavenOverride`).
- O JDK do Termux (17) não tem `javac` em `/usr`; aponte `JAVA_HOME` para
  `$PREFIX/lib/jvm/java-17-openjdk`.

## Onde as coisas estão

| Assunto | Arquivo |
|---|---|
| Congelamento (diagnóstico, hipóteses, cvars) | `doc/02-congelamento.md` |
| Arquitetura do app | `doc/01-arquitetura.md` |
| Requisitos | `requirements.md` |
| Especificação por versão | `specs.md` |
| Identidade visual | `doc/05-guia-visual.md` |
| Roadmap | `doc/08-roadmap.md` |
| Ferramentas | `tools/` |
