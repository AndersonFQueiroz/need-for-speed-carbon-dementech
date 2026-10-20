# doc/08 — Roadmap

## 0.1.0-alpha.1 — entregue (20/10/2026)

Rebrand, pt-BR, aba de diagnóstico, perfis de desempenho, ferramentas de
diagnóstico, documentação.

## 0.1.0-alpha.2 — vigia de congelamento

- Sonda em background lendo o contador de apresentação do `carbon.log`.
- Aviso na tela do jogo quando a imagem travar, com "Recuperar agora".
- Anexo de detecção no diagnóstico por e-mail.
- *Por quê*: hoje a recuperação exige que o usuário saiba o truque.

## 0.2 — identidade de pacote

- `applicationId` → `com.dementech.nfscarbon`, com migração do save e das
  preferências.
- *Por quê*: separar o fork do app original e poder publicar em loja.

## 0.3 — editor de save

- Leitura/escrita do `ALIAS_ANDERSON` dentro do app: peças desbloqueadas,
  dinheiro, forja de corridas, diff antes/depois, backup automático.
- Base técnica já provada fora do app (formato MC02/JDLZ, MD5 do perfil, CRC da
  EA, tabela de áreas). Ver `doc/ideias/editor-de-save.md`.

## 0.4 — correções de motor (via A/B)

- Aplicar `log_level=warn` por padrão, com interruptor "log detalhado".
- Medir e fixar o padrão de resolução/fps que minimizes as janelas sem
  apresentação.
- Reduzir `log_max_files` para 3.

## 1.0 — motor próprio

- Compilar o motor a partir do código-fonte público (ReXGlue + port), com
  correção do caminho de apresentação — a raiz provável do congelamento.
- Exige: dump do executável do Xbox 360, LLVM 20, CMake 3.30, máquina de
  build dedicada. Não é trabalho de sessão.

## Backlog de ideias

- Suporte a gamepad Bluetooth com mapeamento por jogo.
- Editor do layout de toque (o port já tem um; falta integrá-lo à identidade).
- Diagnóstico comparativo entre sessões (gráfico de `apres. fps` ao longo do
  tempo dentro do app).
- Semente (seed) de logs para anexar a issue com um clique.
- Tema claro/escuro (hoje é só escuro).
- Internacionalização (pt-BR, pt-PT, en).

## Como decidir a prioridade

Cada item entra na fila se responder a uma pergunta medida:

1. Reduz as **janelas sem apresentação**?
2. Reduz o **log** gerado?
3. Melhora o **tempo de abertura**?
4. Remove um **passo manual** do usuário?

Sem número, é opinião — e opinião não entra no roadmap.
