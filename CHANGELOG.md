# CHANGELOG

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).
Versões seguem [SemVer](https://semver.org/lang/pt-BR/).

## [Não publicado]

## [0.1.0-alpha.2] — 2026-10-21

### Adicionado
- `tools/zerar_drift.py` e `tools/rodada.sh`: zerar **drift de freeroam pelo
  nome** (forja no formato nativo que o jogo grava, recusa registro de mapa
  vermelho) e a rotina de identificação de missão por rodada (sync + diff do
  save). Ver `doc/09-difts-freeroam.md`.

### Corrigido
- **Tela preta depois de "Reiniciar renderização" (21/10).** Quando a surface
  Vulkan morre, o motor entra em laço tentando recriá-la (1.343 recriações de
  swapchain em 6 minutos, 1.341 "Failed to submit command buffers") e no fim
  responde "Failed to create a Vulkan surface" — aí a tela fica preta **para
  sempre**, porque não há surface para apresentar. Agora:
  - o diagnóstico reconhece a assinatura (`Failed to create a Vulkan surface` /
    `Failed to create a swapchain`) e diz que a tela preta é esperada;
  - "Reiniciar renderização" detecta a surface morta e não insiste no truque de
    "fundo e volta" (que só piora o laço): oferece **"Reabrir o jogo"**, que
    mata o processo e relança o jogo em um processo novo (a única saída real);
  - o botão "Reabrir o jogo" também aparece no painel de diagnóstico do
    launcher.
- **"Reiniciar renderização" não cai mais no launcher.** `recreate()` fecha a
  Activity do jogo, o motor encerra a janela e mata o processo
  (`Title terminated; hard-exiting process`), então o Android reabre o app pelo
  launcher. Agora o botão faz o mesmo gesto do usuário: manda a task para o
  fundo (destruindo a surface) e traz de volta em 700 ms — o processo
  continua vivo e o jogador volta **para o jogo**.
- **Painel de configurações rolável**: em paisagem o diálogo cortava as
  últimas opções (60 fps, driver, etc.) e não dava para ver o resto.
- Diagnóstico dentro do jogo: a distinção entre "o motor parou" e "o motor está
  saudável e a tela parou" aparece no veredito, com a ação certa para cada caso.

### Documentado
- **Modo B de congelamento** (20/10, sessão a 60 fps): o motor entregou
  10.800 quadros com 100% de aquisição sem espera, zero descartes, 38–57 fps
  reais e áudio normal — e a tela estava parada. O problema é a
  surface/display, não o motor. Isso explica por que "sair e voltar" resolve e
  por que gravar tela evita. Ver `doc/02-congelamento.md`, seção 5.1.

### Adicionado
- Diagnóstico do ritmo real: a aba Diagnóstico passa a ler o histograma de
  swaps do motor e mostra fps entregues, quadros lentos (>60 ms) e pior quadro
  da última janela.
- `tools/watch_freeze.py`: vigia de congelamento em tempo real por adb (para
  uso opcional em PC).

### Adicionado (continuação)
- **Diagnóstico dentro do jogo**: botão "Diagnóstico do congelamento" nos
  ajustes do touch control (o gear do dpad virtual abre o editor, e "Ajustes"
  abre as configurações dos controles). Abre o mesmo painel do launcher, por
  cima do jogo, com "Reiniciar renderização".
- **Vigia de congelamento silenciosa**: durante a partida, um timer de 5 s lê o
  contador de apresentação do log; se o motor para de entregar quadro, a
  travada é registrada (hora e duração) e aparece na próxima leitura.
  **Nada é desenhado sobre o jogo.**

### Mudado
- Perfis: **Estável (60 fps)** (escolha do dono) e **Leve (30 fps)** — a 30 fps
  o ritmo medido fica perfeito (600/600 swaps por 20 s), mas o congelamento
  aparece depois de um tempo de jogo nos dois, então o que resolve é a vigia.
- Textos do touch control e do editor de controles em português do Brasil.

## [0.1.0-alpha.1] — 2026-10-20

### Adicionado
- Identidade visual Dementech: cabeçalho, paleta (grafite + cromo + acento),
  ícone adaptativo com camada monocromática e logo gerado a partir do
  arquivo de marca (`tools/make_brand_assets.java`).
- Interface reescrita em português do Brasil, com três abas: Jogo, Diagnóstico
  e Créditos.
- Aba **Diagnóstico**: leitura do `carbon.log` do motor com quadros
  apresentados, fps de apresentação, último tempo de quadro, apresentações
  descartadas, tamanho do log e veredito de imagem travada.
- Botão **Reiniciar renderização** (recria a Activity do jogo e a surface
  Vulkan).
- **Perfis de desempenho**: Estável 720p, Modo gravação e Qualidade.
- `tools/diagnose_freeze.py`: análise do log nativo por sessão, com tabela de
  quadro/GPU/apresentação, janelas sem apresentação e avisos agregados.
- `tools/fetch_engine.sh`: baixa o motor oficial, valida o sha256 e extrai
  bibliotecas nativas e assets de shader.
- Documentação: README, `agents.md`, `requirements.md`, `specs.md` e `doc/`
  (visão, arquitetura, congelamento, build, guia visual, performance, testes,
  roadmap e ideias).

### Corrigido
- Layout do painel de opções do launcher original: a linha do renderizador
  ficava cortada sobre o texto de rodapé.
- Atualização e issue agora apontam para este repositório.

### Mudado
- Versão do app passa a `0.1.0-alpha.1`.
- `GameOptions`: rótulos de tela em pt-BR (os valores de cvar não mudam).

### Notas técnicas
- Build completo do launcher **no próprio celular** (Termux, ARM64, Android 13):
  Gradle 8.9 + AGP 8.7.3, SDK API 35, `aapt2` do Termux, sem NDK.
- Motor binário de terceiros usado como está, pinado por sha256; nenhum byte do
  jogo da Electronic Arts é distribuído.
