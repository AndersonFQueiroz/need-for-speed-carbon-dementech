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

## 5. Hipóteses (e como testar cada uma)

| # | Hipótese | Teste | Ferramenta |
|---|---|---|---|
| **H1** | O *swapchain*/surface fica desatualizado e as frames são descartadas na apresentação (`Presentation ... dropped as the swapchain or the surface has become outdated`) | Loop de 20 min com `vulkan_async_skip_incomplete_frames=false` e `render_target_path_vulkan` alternativo | `files/args.txt` (injeta `--flag=valor` na próxima sessão) |
| **H2** | Ritmo alvo alto/vertical sync mal ajustado piora | `fps=30` e `vsync=false` (perfil "Modo gravação" do app) | perfis da aba Diagnóstico |
| **H3** | **Economia de energia do aparelho** limita CPU/GPU e derruba o ritmo | Desligar a economia de energia e repetir a mesma sessão | ajuste do sistema |
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

## 6. Cvars do motor relevantes

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

## 7. Mitigações já no launcher (alpha.1)

- **Aba Diagnóstico**: mostra o estado real da apresentação (quadros
  apresentados, fps, descartados, tamanho do log) e diz se a imagem está
  travada agora.
- **Botão "Reiniciar renderização"**: recria a Activity do jogo, o que recria a
  surface Vulkan — é exatamente o "sair e voltar" que resolve.
- **Perfis de desempenho** que aplicam combinações conservativeas
  (720p / 30–60 fps / vsync / compilação de shaders em segundo plano).

## 8. O que falta (alpha.2 em diante)

1. **Watchdog**: detectar a parada de apresentação dentro do app e recuperar
   sozinho (hoje exige o toque do usuário).
2. **Aplicar `log_level=warn` por padrão** (medir o ganho de I/O).
3. **Matriz de teste A/B** automatizada: `tools/bench_sessions.py`, rodando
   N sessões por configuração e comparando o `apres. fps`.
4. **Relatório de issue** com os números já preenchidos (o app já monta o
   diagnóstico; falta anexar a comparação entre sessões).
