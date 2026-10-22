# O congelamento da imagem — diagnóstico

> Status: **mapeado com números**, correções em andamento.
> Aparelho de referência: Samsung SM-G781B (Exynos 1280, Adreno 650, Android 13).
> Dados: 8 sessões de jogo, 40,3 MB de log nativo (`carbon.log` + rotacionados),
> coletados em 19–20/10/2026.
> Ferramenta: [`tools/diagnose_freeze.py`](../tools/diagnose_freeze.py) (e a mesma
> leitura dentro do app, em `app/src/main/java/com/nfscarbon/android/FreezeReport.java`).

## 1. O sintoma

> A imagem para, mas o jogo continua rodando. O contador de FPS não para de
> andar, o tempo de quadro continua mudando, o áudio engasga. Sair do app
> (Recentes) e voltar destrava na hora.

Isso não é impressão: o motor registra, no log, quantos quadros **entregou de
verdade** ao display. E ele para de entregar.

## 2. Como medir

O motor nativo tem uma linha de log do apresentador (o "presentador" do ReX):

```
[2026-10-20 17:17:47.997] [info] [core] [t14482] [presentador] adquisicion anticipada: 2399 de 2400 fotogramas sin esperar (100.0 %)
```

O contador `M` (2400) é cumulativo e cresce de 600 a cada ~10 s — ou seja,
**60 quadros por segundo entregues à tela**. Se `M` para de crescer enquanto o
log continua sendo escrito, o motor está vivo mas **nada está chegando ao
display**: é o congelamento, medido.

O `tools/diagnose_freeze.py` faz exatamente isso:

```bash
adb pull /sdcard/Android/data/com.nfscarbon.android/files/logs/carbon.log .
python3 tools/diagnose_freeze.py --sessions carbon*.log
python3 tools/diagnose_freeze.py --top 8 --context carbon.log   # detalhe
```

## 3. Resultado por sessão

Um log = uma sessão de jogo.

| sessão | duração | quadro p50 | GPU p50 | **apres. fps** | apresent. descartadas | **janelas sem apresentar** | **tempo parado** |
|---|---|---|---|---|---|---|---|
| carbon.log (20/10 17:17) | 42,6 min | 67,8 ms | 14,9 ms | 37,3 | 1 | 0 | 0 s |
| carbon.3.log (20/10 07:37) | 98,9 min | 67,5 ms | 14,8 ms | 23,3 | 5 | 0 | 0 s |
| **carbon.4.log (20/10 00:41)** | 416,1 min | 83,2 ms | 14,2 ms | **0,6** | 18 | **2** | **1.798 s** |
| carbon.5.log (19/10 19:41) | 300,4 min | 83,1 ms | 14,1 ms | 3,0 | 12 | 0 | 0 s |
| **carbon.6.log (19/10 16:21)** | 200,0 min | 316,9 ms | 18,7 ms | **0,8** | 16 | **3** | **1.711 s** |
| carbon.7.log (19/10 08:26) | 474,4 min | 150,4 ms | 18,7 ms | 2,2 | 16 | 0 | 0 s |
| carbon.8.log (19/10 07:21) | 65,1 min | 67,4 ms | 14,7 ms | 53,0 | 0 | 0 | 0 s |
| **carbon.9.log (19/10 05:59)** | 81,6 min | 72,2 ms | 32,1 ms | 20,2 | 12 | **13** | **1.211 s** |

Agregado: 147.497 linhas · quadro p50 **100,4 ms** (≈10 fps) · p90 334 ms ·
p99 869 ms · máx 2.216 ms · **50,8 % dos quadros acima de 100 ms** ·
**GPU p50 18,3 ms** (p90 31,8 ms).

### O que os números dizem

1. **A GPU não é o gargalo.** O trabalho real na GPU leva 14–32 ms, o que
   daria 30–67 fps só na GPU. O quadro completo leva 67–317 ms. A diferença
   (dezenas de ms) vive fora da GPU: no app, no desenho dos comandos, na
   fila de apresentação ou na espera.
2. **Nas sessões travadas o contador de apresentação congela.** Em
   `carbon.4.log` o motor ficou **1.798 s (30 minutos)** sem apresentar um
   único quadro, com o logWRITE ativo (563–831 linhas por janela).
3. **O log prova que o jogo estava vivo durante a travada**: a linha
   `[tiron] fotograma de 249.3 ms (GPU 45.3 reales…)` continua sendo gravada,
   e o `GPU 0.0 reales` de alguns momentos mostra a GPU ociosa — o problema
   não é a GPU travada, é o quadro não chegando na tela.
4. **As sessões ruins têm "apresentações descartadas"** (12–18 por sessão),
   com a mensagem do próprio motor:
   `VulkanPresenter: Presentation to the swapchain image has been dropped as
   the swapchain or the surface has become outdated`.
   Nas sessões boas: 0–5.

## 4. O mesmo bug na família de ports

Não é exclusividade do Carbon nem do aparelho. No mesmo autor (codepdbh), que
mantém vários ports com a **mesma base de launcher e o mesmo renderizador**:

| Port | Issue | Aparelho | Descrição |
|---|---|---|---|
| Carbon 360 | [#137](https://github.com/codepdbh/NFS-CARBON-360-ANDROID-RECOMP/issues/137) | Adreno 750 (Meizu 21) | "Progressive FPS Drop Followed by Complete Hang". FPS continua em 60, imagem parada. **Gravar tela evita o bug.** Sair e voltar ao app destrava. |
| Most Wanted | [#204](https://github.com/codepdbh/nfsmw-android/issues/204) | Adreno 830 (Xiaomi, Android 16) | "La imagen se congela" ao configurar **60 FPS ou mais**; o jogo segue rodando em segundo plano. |
| Most Wanted | [#1886](https://github.com/codepdbh/nfsmw-android/issues/1886) | PowerVR BXM | Congela na tela de carregamento de perfil. |
| Underground 2 | [#328](https://github.com/codepdbh/nfsu2-recompiled/issues/328) | Mali-G615 MC2 | "Congelamientos de varios segundos"; FPS volta ao normal depois; **melhora parcialmente ao reduzir a resolução**; persiste com driver do sistema **e** com PanVK; SIGSEGV no log. |

Volumes de issue por repositório: MW 2.484 · U2 1.121 · Carbon 167.

Ou seja: **a mesma classe de bug aparece em Adreno 650, 750 e 830, em Mali e em
PowerVR** → a causa não é um driver específico nem um aparelho. É da camada
compartilhada: **renderizador + caminho de apresentação (presenter) do ReXGlue**.


## 5. A métrica que fecha: o histograma de swaps

O motor loga, a cada ~20 s, a distribuição do tempo entre as entregas de
quadro (o "swap" para a tela):

```
[19:52:36.710] [nativo] C2: intervalos entre Swaps: <15 ms 0 | 15-18 0 | 18-25 0 | 25-30 0 |
30-36 600 | 36-50 0 | 50-60 0 | 60-75 0 | 75-100 0 | 100-150 0 | >=150 0; peor fotograma 35.4 ms
```

É a métrica honesta de ritmo: quantos quadros foram entregues e como se
distribuem. (O `fotograma de X ms` que aparece no log é só uma amostra de
quadros *notáveis* — por isso ele media ~10 fps mesmo quando o jogo roda
liso.)

### A/B no mesmo aparelho

| Sessão | fps alvo | swaps por 20 s | quadros lentos (>60 ms) por janela | pior quadro |
|---|---|---|---|---|
| 20/10 19:51 (perfil **Modo gravação**) | **30** | **600 · 600 · 600** | **0 · 0 · 0** | **34,9 · 35,0 · 35,4 ms** |
| 19-20/10 17:58 (perfil 60) | 60 | 701 · 1057 · 1170 · 1198 | 1 · 2 · 5 | 66,9 · 115,9 · 266,4 ms |
| 19-20/10 08:25 (perfil 60) | 60 | 1074 · 1126 · 1201 · 1151 | 9 · 0 · 0 | 167,1 · 49,6 · 18,6 ms |
| 19/10 19:39 (**sessão travada**) | 60 | 239 · 259 · 187 · 210 | **50 · 50 · 50 · 51** | 337,1 · 337,5 · 337,8 · 350,7 ms |

### O que isso prova

1. **A 30 fps o ritmo é perfeito**: 600 swaps em 20 s (30,0 fps exatos), nenhum
   quadro acima de 36 ms, nenhuma troca perdida. O jogo fica *fluido*.
2. **A 60 fps o ritmo quebra**: o motor tenta 60 (centenas de swaps abaixo de
   25 ms na mesma janela — ele "descarrega" quadro), o app não acompanha, e a
   fila satura. O log mostra `trabajos solapados en la GPU 8` nessa fase.
3. **A sessão travada é a ponta desse mesmo现象**: 187–259 swaps por 20 s
   (9–13 fps) com **50 quadros lentos em toda janela** e pior quadro travado em
   ~337 ms, de forma constante. Ou seja: o congelamento é a **fase extrema da
   fila saturada**, não um evento isolado.
4. Isso bate com a **issue #204 do port do Most Wanted** ("la imagen se congela
   cuando configuro los fotogramas por segundo a más de 60") — o mesmo
   sintoma, a mesma variável.

### Consequência prática (já aplicada no app)

O perfil **Estável** do launcher agora usa **30 fps** (era 60). Os perfis
"Modo gravação" e "Estável" são, na prática, o mesmo perfil neste aparelho:
o que resolve não é o nome, é **não pedir 60 fps**.

O que ainda falta testar (H1): 30 fps com `vulkan_async_skip_incomplete_frames`
desligado, e 30 fps com a surface recriada — porque a sessão de 30 fps ainda
pode travar em longo prazo, só que muito menos.


## 5.1 Modo B: motor saudável, tela parada (descoberto em 20/10, 21:05)

Uma sessão a **60 fps** congelou com o áudio tocando. O log dessa sessão
(`carbon.log`, 21:05:23 → 21:09:32) mostra o motor **perfeitamente saudável**:

| indicador | valor | leitura |
|---|---|---|
| quadros apresentados | 600 → **10.800** (+10.200) | o motor entregou quadro o tempo todo |
| aquisição sem espera | **100,0 %** em todas as amostras | a fila de apresentação nunca travou |
| apresentações descartadas | **0** | a surface não ficou desatualizada |
| ritmo (swaps/20 s) | 768 · 1019 · 967 · 717 · 1137 | 38–57 fps reais, com 832 swaps abaixo de 25 ms |
| pior quadro por janela | 132 · 35 · 35 · 51 · 99 ms | nada fora do normal |
| GPU real p50 | **14,5 ms** | GPU folgada |
| áudio | `1875 tramas con datos y 0 de silencio` | áudio normal |

**Ou seja: o jogo renderizava, apresentava e soava normalmente — e mesmo assim
a tela estava parada.** O congelamento, neste caso, está **na surface/display**,
não no motor. Isso explica (e confirma) as pistas do upstream:

- **sair e voltar resolve** → recria a surface, refaz o caminho de apresentação;
- **gravar tela evita** → o gravador força um caminho de composição diferente;
- **trocar driver não resolve** → não é o driver da GPU que entrega quadro.

### Os dois modos de falha

| | Modo A — motor para | Modo B — tela para (display) |
|---|---|---|
| Contador de apresentação | **para de crescer** | cresce normal |
| Aquisição sem espera | cai / passa a esperar | **100 %** |
| Apresentações descartadas | 12–18 por sessão | **0** |
| Ritmo real | 9–13 fps, 50 hitches por janela | 38–57 fps, sem hitches |
| Onde está o problema | fila/queue do motor | surface / composição do Android |
| Como a vigia detecta | **sim** (contador parado) | **não** (precisa de leitura de pixel ou do relato) |
| Correção | recriar a surface (o motor refaz a fila) | recriar a surface |

### O que a vigia (e o diagnóstico) conseguem e não conseguem

A vigia compara o contador de apresentação a cada 5 s: ela pega o **Modo A**.
No **Modo B** o motor está saudável, então o log não mostra nada de errado — e
a única evidência é a tela parada (e o áudio tocando). Por isso o diagnóstico
agora diz exatamente isso:

> "MOTOR ENTREGANDO QUADROS NORMALMENTE — se a imagem está parada, o problema é
> na surface/display: use *Reiniciar renderização*."

Detectar o Modo B automaticamente exigiria **ler pixels da tela** (PixelCopy
em cima da SurfaceView) e comparar com o contador do motor — é a próxima
frente de trabalho, e é cara (uma leitura por segundo).

### Recuperação: por que "reiniciar renderização" voltava pro launcher

`Activity.recreate()` fecha a Activity do jogo → o motor encerra a janela →
`Title terminated; hard-exiting process` → **o processo morre** e o Android
reabre o app pelo launcher. Não dá para reiniciar a Activity.

A correção (no app): **fazer o que o usuário faz com o gesto** — mandar a task
para o fundo (`moveTaskToBack`, que destrói a surface) e trazer de volta em
700 ms. O processo continua vivo, a surface é recriada, e o jogador volta
**para o jogo**, não para o launcher.

### 5.2 A surface morreu de vez: tela preta sem volta (21/10, 12:07–12:25)

O mesmo gesto, na situação errada, **quebra** o jogo. Sessão medida no log do
motor (`carbon.log`, 12:07:21 → 12:25:39):

| hora | evento |
|---|---|
| 12:07:37 | `Presentation to the swapchain image has been dropped as the swapchain or the surface has become outdated` — a surface ficou desatualizada |
| 12:11:34 | o motor começa a recriar a swapchain (`Created 2400x1080 swapchain`) |
| 12:19:51 | **laço de reconstrução**: 343 recriações de swapchain + 343 `Failed to submit command buffers` em 1 minuto; o motor renderiza (`anillo: 2220 dibujos`) mas **não apresenta nada** (`0 intercambios`, `GPU 0.0 reales`) |
| 12:20:08 | `Failed to create a swapchain` + **`Failed to create a Vulkan surface`** (1ª vez) |
| 12:25:35 e 12:25:39 | o dono aperta "Reiniciar renderização": a surface **não volta** (3 falhas no total) e a tela fica **preta para sempre** |

Diagnóstico: o motor não sabe recriar a surface; o truque de "fundo e volta"
**reforça** o laço (cada ida ao fundo destrói a surface de novo) e o
`CLEAR_TOP` do launcher também não resolve — fechar a Activity mata o processo
(`Title terminated; hard-exiting process`) e o jogo nem chega a reabrir.

**Sinais no log (para o diagnóstico automático ler):**
- `Failed to create a Vulkan surface` / `Failed to create a swapchain` → surface morta;
- `Failed to submit command buffers` em rajada + `Created … swapchain` repetido → laço de reconstrução;
- `0 intercambios` e `GPU 0.0 reales` nos quadros → o motor desenha, ninguém vê.

**Saída:** matar o processo e relançar o jogo (o relançamento é pedido ao
sistema antes do `exit(0)`, então volta sozinho em um processo novo). Implementado
no alpha.2: o painel de diagnóstico passa a avisar que a tela preta é esperada
e oferece **"Reabrir o jogo"**.

### 5.3 O patch do motor (nosso, sem depender de ninguem) — 21/10

O presenter do ReXGlue (SDK público, BSD-3) trata `VK_ERROR_DEVICE_LOST` nos caminhos de
aquisicao e de apresentacao, mas **nao** no de submissao: ali ele devolve
`kNotPresentedConnectionOutdated` e o motor fica recriando a swapchain (1.343 recriacoes em
6 min) ate a surface nao poder mais ser criada (tela preta, CPU em 100 %).

`tools/patch_engine.py` aplica no `librexruntime.so` que o app shipa o equivalente da
correcao de 3 linhas do fonte: um *code cave* de 8 bytes num trecho de zeros do `.text`
(`mov w21, #5` + `b <epilogue>`) e o branch do bloco de falha do submit passa a cair nele.
Com isso, a perda do device e reportada ao host em vez de virar laço de reconstrucao.

```
python3 tools/patch_engine.py      # aplica no app/src/main/jniLibs/arm64-v8a/librexruntime.so
```

O script confere os bytes esperados e a assinatura do presenter antes de patchear e
recusa se o binario for diferente do pinado (SHA256 em `tools/fetch_engine.sh`).

## 6. Hipóteses (e como testar cada uma)

| # | Hipótese | Teste | Ferramenta |
|---|---|---|---|
| **H1** | O *swapchain*/surface fica desatualizado e as frames são descartadas na apresentação (`Presentation ... dropped as the swapchain or the surface has become outdated`) — **Modo A** | Loop de 20 min com `vulkan_async_skip_incomplete_frames=false` e `render_target_path_vulkan` alternativo | `files/args.txt` (injeta `--flag=valor` na próxima sessão) |
| **H1b** | A **composição do Android** (SurfaceFlinger) para de aplicar as frames na tela, com o motor saudável — **Modo B** | Comparar o conteúdo da tela (PixelCopy) com o contador do motor; testar com o gravador de tela ligado | leitura de pixel (a implementar) |
| **H2** | Ritmo alvo alto (60 fps) satura a fila e derruba o ritmo | **medido**: 30 fps → 600/600 swaps e 0 lentos; 60 fps → hitches e 50 lentos na sessão travada | perfil Estável (30 fps) do app |
| **H3** | **Economia de energia do aparelho** limita CPU/GPU e derruba o ritmo | Desligar a economia de energia e repetir a mesma sessão | ajuste do sistema |
| **H2b** | `vulkan_async_skip_incomplete_frames` descartando quadro válido | A/B com o flag desligado | `files/args.txt` |
| **H4** | Verbosidade do log (5 MB por sessão, rotação de 20 arquivos) causa stalls de I/O | `log_level=warn` + `log_max_files=3` | cvar do motor |
| **H5** | Pipeline de shaders / cache | `--vulkan_pipeline_creation_threads=4` e recompilar a biblioteca de shaders | perfil "Qualidade" |
| **H6** | Surface precisa ser recriada para destravar | "Sair e voltar ao app" (reproduz o que o app já faz no botão **Reiniciar renderização**) | botão do app |

### Estado da H3 (descoberta durante a investigação)

O aparelho estava com **Economia de energia LIGADA** durante todas as sessões
analisadas (`settings get global low_power` = `1`). No Samsung isso limita o
desempenho de CPU/GPU e pode derrubar o ritmo. **É a primeira coisa a
eliminar**: repetir uma sessão com a economia desligada e comparar o
`apres. fps` medido.

> Curiosidade útil: a **gravação de tela evita o bug** (issue #137). O
> gravador força um caminho de composição diferente no Android, o que reforça
> H1 (problema no caminho de apresentação) e explica por que trocar de
> driver ou mexer na resolução não resolve.

## 7. Cvars do motor relevantes

Extraídos do código do renderizador (ReXGlue). O launcher passa esses flags
na linha de comando, e por isso **não exigem recompilar nada**:

| cvar | padrão | para quê |
|---|---|---|
| `log_level` | `info` | `trace`/`debug`/`info`/`warn`/`error`/`off` — **o maior custo hoje é o log verboso** |
| `log_noisy` | `false` | macros de alta frequência |
| `log_verbose` | `false` | liga `trace` |
| `log_max_file_size_mb` | `5` | tamanho por arquivo de log |
| `log_max_files` | `20` | arquivos rotacionados (hoje: 8 × 5 MB = 40 MB) |
| `vulkan_async_skip_incomplete_frames` | `true` | descarta quadro incompleto (H1) |
| `render_target_path_vulkan` | `fbo` | caminho do render target (H1) |
| `vulkan_dynamic_rendering` | `true` | o launcher força `false` por causa do Adreno |
| `vulkan_pipeline_creation_threads` | `-1` | threads de compilação de pipeline |
| `present_letterbox` | `true` | o launcher força `false` |
| `present_effect` | `bilinear` | filtro de apresentação (`fsr`, `nativeaa`…) |
| `present_dither` | `false` | dithering da saída |
| `host_present_from_non_ui_thread` | `true` | apresentação fora da thread de UI |
| `vulkan_submit_on_primary_buffer_end` | `true` | submissão no fim do buffer primário |

## 8. Mitigações já no launcher (alpha.1)

- **Aba Diagnóstico** (launcher): mostra o estado real da apresentação (quadros
  apresentados, fps, descartados, tamanho do log), o **ritmo real** (quadros
  entregues por segundo, quadros lentos, pior quadro) e diz se a imagem está
  travada agora.
- **Diagnóstico dentro do jogo**: botão **"Diagnóstico do congelamento"** nos
  *ajustes do touch control* (o gear do dpad virtual → editor → "Ajustes").
  Abre o mesmo painel por cima do jogo, com **Reiniciar renderização**.
  **Nada é desenhado sobre o jogo durante a partida** — nem botão, nem faixa.
- **Vigia silenciosa**: durante o jogo, um timer de 5 s lê o contador de
  apresentação do log. Se o log continua sendo escrito (motor vivo) mas o
  contador não avança por 20 s, a travada é **registrada** (com hora e
  duração) e aparece na próxima leitura do diagnóstico. Sem poluir a tela.
- **Botão "Reiniciar renderização"**: recria a Activity do jogo, o que recria a
  surface Vulkan — é exatamente o "sair e voltar" que resolve.
- **Perfis de desempenho**: *Estável (60 fps)*, *Leve (30 fps)* e *Qualidade*.
  O dono escolheu 60 fps por padrão: a 30 fps o ritmo fica perfeito, mas o
  congelamento aparece depois de um tempo de jogo nos dois, então o que
  resolve é a vigia, não o fps.

## 9. O que falta (alpha.2 em diante)

1. **Recuperação automática**: a vigia já detecta e registra a travada; falta
   resolver sozinho (hoje exige o toque do usuário em "Reiniciar renderização").
2. **Aplicar `log_level=warn` por padrão** (medir o ganho de I/O).
3. **Matriz de teste A/B** automatizada: `tools/bench_sessions.py`, rodando
   N sessões por configuração e comparando o `apres. fps`.
4. **Relatório de issue** com os números já preenchidos (o app já monta o
   diagnóstico; falta anexar a comparação entre sessões).
