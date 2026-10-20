# specs.md — especificação por versão

Formato: cada versão traz o que entra, o que não entra e como se prova que
está pronta.

---

## 0.1.0-alpha.1 — rebrand, diagnóstico e perfis

**Entra**

- Identidade visual Dementech: logo, ícone adaptativo + monocromático,
  cabeçalho com marca/versão/assinatura, paleta e componentes em `Ui.java`.
- Interface reescrita em pt-BR, com três abas: **Jogo**, **Diagnóstico**,
  **Créditos**.
- Aba Diagnóstico:
  - lê `carbon.log` (últimos 512 KB) e extrai: contador de quadros
    apresentados, fps de apresentação, último tempo de quadro, apresentações
    descartadas, tamanho do log;
  - veredito: "Apresentação normal" ou "⚠ Sem apresentação há N s — imagem
    provavelmente travada" (limiar: 20 s);
  - botões **Atualizar leitura** e **Reiniciar renderização**.
- Perfis de desempenho: *Estável 720p*, *Modo gravação*, *Qualidade*.
- Ferramentas de linha de comando: `tools/diagnose_freeze.py`,
  `tools/fetch_engine.sh`, `tools/make_brand_assets.java`.
- Documentação completa (`doc/`), `agents.md`, `requirements.md`, `specs.md`.

**Não entra**

- Recuperação automática do congelamento (vira watchdog no alpha.2).
- Editor de save (v0.3).
- Troca do `applicationId` (v0.2).

**Prova de que está pronto**

1. `./gradlew :app:assembleRelease` conclui no celular (Termux, ARM64).
2. `adb install -r` por cima da versão anterior → save preservado.
3. As três abas renderizam em pt-BR, com o ícone Dementech no sistema.
4. `tools/diagnose_freeze.py --sessions` reproduz os mesmos números que a aba
   Diagnóstico mostra.
5. O log do motor continua sendo gerado normalmente após a instalação.

---

## 0.1.0-alpha.2 — watchdog de congelamento

**Entra**

- Sonda em background que lê o contador de apresentação do `carbon.log` a
  cada 5 s enquanto o jogo está em primeiro plano.
- Ao detectar ≥ 20 s sem apresentação, mostra aviso na Activity do jogo com
  as ações: *Recuperar agora* (recria a surface) e *Esperar*.
- Relatório da detecção anexado ao diagnóstico enviado por e-mail.

**Prova**: sessão artificial travada (log parado) → aviso aparece em ≤ 25 s e
a recuperação devolve a imagem.

---

## 0.2 — `applicationId` novo

**Entra**

- `applicationId` = `com.dementech.nfscarbon`.
- Migração do save e das preferências do `com.nfscarbon.android` para o novo
  pacote, na primeira execução.
- Tela de migração com progresso e botão de cancelar.

**Cuidado**: o nome da classe com métodos JNI **não** muda (o símbolo JNI
depende do nome da classe), só o `applicationId` e o namespace do código novo.

---

## 0.3 — editor de save

**Entra**

- Editor dentro do app: leitura do `ALIAS_ANDERSON`, lista de peças
  desbloqueadas, forja de corridas, ajuste de dinheiro, e um diff antes/depois.
- Backup automático antes de cada escrita.

**Base já provada** (fora do app): o formato MC02/JDLZ, o MD5 do perfil, os
três CRC da EA e a semântica da tabela de áreas estão documentados em
`doc/ideias/editor-de-save.md`.

**Prova**: o save editado pelo app é byte a byte equivalente ao gerado pela
ferramenta de linha de comando, e o jogo abre com ele.

---

## 1.0 — motor próprio

**Entra**

- Motor compilado a partir do código-fonte público do ReXGlue/port, com as
  correções do caminho de apresentação (a raiz provável do congelamento).

**Fora de escopo agora**: exige o dump do executável do Xbox 360, LLVM 20,
CMake 3.30 e uma máquina de build de verdade (o celular não serve para a
geração de código de 72 mil funções).
