# CHANGELOG

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).
Versões seguem [SemVer](https://semver.org/lang/pt-BR/).

## [Não publicado]

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
