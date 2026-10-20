# doc/04 — Build no Termux (o próprio celular)

O launcher é compilado **no aparelho**: Android 13 (SM-G781B, aarch64),
Termux com root, ~1,5 GB de RAM livre. Nenhum PC necessário.

## 1. Preparar o Termux

```bash
# rodar como root não funciona com pkg; use o uid do app Termux
setpriv --reuid=10530 --regid=10530 --clear-groups \
  env HOME=$PREFIX/home PATH=$PREFIX/bin:/system/bin LC_ALL=C \
  pkg install -y gradle aapt2
```

- `aapt2` do Termux é **obrigatório**: o `aapt2` oficial do build-tools é
  x86_64 e não roda em ARM64 (e o antigo do Debian, 2.19, não lê a tabela de
  recursos do android-35).
- Se o `aapt2` reclamar de símbolo do Abseil, atualize o `abseil-cpp`:
  `pkg upgrade -y abseil-cpp`.

## 2. SDK Android

```bash
SDK=$HOME/android-sdk
mkdir -p $SDK/cmdline-tools && cd /tmp
curl -fLO https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q commandlinetools-linux-13114758_latest.zip -d cmdline
mv cmdline/cmdline-tools $SDK/cmdline-tools/latest
export ANDROID_HOME=$SDK
yes | $SDK/cmdline-tools/latest/bin/sdkmanager --sdk_root=$SDK --licenses
$SDK/cmdline-tools/latest/bin/sdkmanager --sdk_root=$SDK \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Consome ~500 MB.

## 3. Gradle

O wrapper do projeto baixa o Gradle 8.9; se preferir instalar à mão:

```bash
curl -fLO https://services.gradle.org/distributions/gradle-8.9-bin.zip
unzip -q gradle-8.9-bin.zip -d $HOME
```

O Gradle 8.9 traz nativos para `linux-aarch64` ✓ (funciona no celular).

## 4. Ajustes do projeto

`gradle.properties` já vem com o perfil do celular:

```properties
org.gradle.jvmargs=-Xmx900m -XX:MaxMetaspaceSize=384m
org.gradle.workers.max=1
org.gradle.daemon=false
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

E `local.properties` (não versionado):

```properties
sdk.dir=/data/data/com.termux/files/usr/home/android-sdk
```

> O `JAVA_HOME` precisa apontar para um **JDK** (o `java` do Debian em `/usr`
> não tem `javac`):
> `JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk`.

## 5. Compilar

```bash
tools/fetch_engine.sh          # motor oficial + sha256 (69 MB de .so, 18 MB de shaders)
./gradlew :app:assembleRelease
# ou, com Gradle instalado à mão:
$HOME/gradle-8.9/bin/gradle :app:assembleRelease --no-daemon
```

Tempo: 2–6 min (a primeira vez baixa as dependências do Maven).

Saída: `app/build/outputs/apk/release/app-release-unsigned.apk` (~38,6 MB).

## 6. Assinar e instalar

```bash
apksigner sign --ks chave.jks --ks-key-alias t --ks-pass pass:SEGREDO \
  --key-pass pass:SEGREDO --v1-signing-enabled false \
  --out app-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
adb install -r app-release.apk
```

- Use **sempre a mesma chave**: assinatura diferente = Android recusa a
  instalação; desinstalar = **perde o save**.
- Faça backup do save antes (`adb pull
  /sdcard/Android/data/com.nfscarbon.android/files/saveio/out/ALIAS_ANDERSON`).

## 7. Problemas conhecidos

| Sintoma | Causa | Solução |
|---|---|---|
| `failed to load include path …/android.jar` | `aapt2` antigo (Debian 2.19) | usar o `aapt2` do Termux |
| `cannot locate symbol absl::…` | `abseil-cpp` desatualizado | `pkg upgrade -y abseil-cpp` |
| `failed to load include path` no `javac` | `JAVA_HOME` sem `javac` | apontar para o JDK do Termux |
| Build morre sem mensagem | falta de memória | `-Xmx900m`, `workers.max=1`, sem daemon |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | chave diferente | usar a chave original |

## 8. Build em PC (alternativa)

Em um PC com Linux/macOS/Windows: JDK 17, Android SDK (API 35 + build-tools
35), `tools/fetch_engine.sh` e `./gradlew :app:assembleRelease`. O
`aapt2FromMavenOverride` só é necessário em ARM64 — apague a linha do
`gradle.properties` num PC x86_64.
