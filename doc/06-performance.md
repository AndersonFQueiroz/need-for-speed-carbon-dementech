# doc/06 — Performance

## O que já sabemos (medido)

8 sessões, 40,3 MB de log, aparelho SM-G781B (Adreno 650):

- quadro p50 **100,4 ms** (≈10 fps) · p90 334 ms · p99 869 ms · máx 2.216 ms
- **50,8 %** dos quadros acima de 100 ms
- **GPU real p50 18,3 ms** (p90 31,8 ms) → a GPU sozinha daria 30–67 fps
- nas sessões travadas: **até 1.798 s sem apresentar quadro**, com o log ativo
- log: 5 MB por sessão, 8 arquivos rotacionados (**40 MB** no aparelho)

Detalhe e método: [`02-congelamento.md`](02-congelamento.md).

## Gargalos conhecidos

| # | Gargalo | Evidência | Palanca |
|---|---|---|---|
| 1 | **Apresentação** (path de surface/swapchain) | contador de apresentação para por minutos; `Presentation … dropped` | recriar a surface (botão do app) |
| 2 | **Fora da GPU** (app/draw/fila) | 50–300 ms de quadro com 15–45 ms de GPU | resolution, vsync, fps alvo, atalhos do renderer |
| 3 | **I/O de log** | 40 MB de log por 2 dias de jogo | `log_level=warn`, `log_max_files=3` |
| 4 | **Economia de energia do aparelho** | ligado durante todas as sessões | desligar e repetir (H3) |
| 5 | **Shaders** | primeira execução compila a biblioteca | cache já é reaproveitado; 2 threads |

## Cvars do motor que o launcher controla

(veja a tabela completa em `02-congelamento.md`)

| cvar | efeito | uso sugerido |
|---|---|---|
| `log_level` | `trace`…`off` | `warn` no dia a dia; `info` só para diagnosticar |
| `log_max_files` | rotação | 3 (hoje são 20) |
| `vulkan_async_skip_incomplete_frames` | descarta quadro incompleto | A/B para a H1 |
| `render_target_path_vulkan` | caminho do render target | A/B para a H1 |
| `vulkan_pipeline_creation_threads` | threads de pipeline | 2 (padrão) ou 4 |
| `present_effect` | filtro de saída | `bilinear` (padrão) |

## Perfis

| Perfil | resolução | fps | vsync | AA | shaders | Quando usar |
|---|---|---|---|---|---|---|
| **Estável 720p** | 1280×720 | 60 | ligado | 1 passe | 2 threads | padrão; prioriza estabilidade |
| **Modo gravação** | 1280×720 | 30 | desligado | 1 passe | 2 threads | com gravador de tela (evita o congelamento no upstream) |
| **Qualidade** | 1280×720 | 60 | ligado | original | 4 threads, foreground | visual; mais pausas |

## Como medir uma mudança

```bash
# 1. jogar ~15 min na configuração A
adb pull /sdcard/Android/data/com.nfscarbon.android/files/logs/carbon.log A.log
# 2. jogar ~15 min na configuração B
adb pull …/carbon.log B.log
# 3. comparar
python3 tools/diagnose_freeze.py --sessions A.log B.log
```

Métrica de decisão: **`apres. fps`** (quadros entregues ao display) e o
número de **janelas sem apresentação**. O quadro p50 do motor é ruído: ele
registra só quadros notáveis.

## Orçamentos

| Item | Alvo |
|---|---|
| Log total no aparelho | ≤ 10 MB |
| Tempo de abertura do launcher | ≤ 1,5 s |
| Quadro p50 (referência) | ≤ 34 ms (30 fps) |
| `apres. fps` médio por sessão | ≥ 30 |
| Janelas sem apresentação | 0 |
