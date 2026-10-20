# doc/03 — Licenças

Resumo. O texto que vale é [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).

## O que é do Dementech (MIT)

- `app/src/main/java/com/nfscarbon/android/Ui.java` (identidade e componentes)
- `app/src/main/java/com/nfscarbon/android/FreezeReport.java` (diagnóstico)
- o cabeçalho, a aba de diagnóstico, os perfis de desempenho e a reescrita em
  pt-BR de `MainActivity`
- `tools/` (diagnóstico, build, marca)
- `doc/`, `README.md`, `agents.md`, `requirements.md`, `specs.md`
- a identidade visual (`assets/brand/`, `app/src/main/res/`)

## O que é de terceiros (mantém a licença original)

| Parte | Licença |
|---|---|
| Launcher original (Java em `com/nfscarbon/android/`, exceto os arquivos acima) | GPL-3.0-only |
| `org/libsdl/app/*` (Java do SDL3) | zlib |
| Motor nativo (`.so`) | ReXGlue/Xenia — binário de terceiros, não versionado |
| `default.xex`, `NFS/`, `Movies/` | © Electronic Arts — **não** versionados |

## Por que a GPL fica registrada

O launcher original é GPL-3.0. Works derivados de código GPL precisam manter
os avisos de copyright e a licença — num repositório público isso não é
detalhe: é a condição para que a distribuição seja legal. Por isso:

- cada arquivo derivado mantém o cabeçalho
  `SPDX-License-Identifier: GPL-3.0-only` e a linha de origem;
- `THIRD_PARTY_NOTICES.md` lista arquivo por arquivo a origem;
- o app **não** mostra créditos de terceiros na interface (a GPL não exige
  tela de créditos — exige o aviso junto com o código).

Se um dia o launcher for reescrito do zero, sem copiar código do upstream, essa
seção deixa de aplicar ao código novo.

## Regras de ouro

- **Nunca** publicar material da Electronic Arts (jogo, executável, vídeos).
- **Nunca** remover os avisos de copyright dos arquivos GPL.
- **Sempre** manter a divergência entre o que é MIT e o que é GPL explícita.
