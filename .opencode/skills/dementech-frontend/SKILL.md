---
name: dementech-frontend
description: Sistema de design e regras de UI do launcher Dementech (NFS Carbon 360). Use ao criar ou alterar telas, componentes, cores, textos ou ícones do launcher.
---

# Frontend Dementech

Launcher Android **Java**, UI **por código** (sem XML de layout, sem Android
Studio, sem Compose/Kotlin). Tudo passa por `app/src/main/java/com/nfscarbon/android/Ui.java`.

## Marca

Monocroma: prata cromada sobre preto, um único acento elétrico. Nada de
vermelho de console, nada de listras racing, nada de gradiente colorido.

| Token | Hex | Uso |
|---|---|---|
| `Ui.BLACK` | `#07070A` | fundo base |
| `Ui.SURFACE` | `#101017` | painel |
| `Ui.SURFACE_ALT` | `#17171F` | linha de opção |
| `Ui.CARD` | `#1C1C26` | cartão |
| `Ui.OUTLINE` | `#2C2C39` | borda 1 dp |
| `Ui.CHROME` | `#C9CFDD` | texto principal |
| `Ui.CHROME_DIM` | `#8A92A3` | texto secundário |
| `Ui.ACCENT` | `#5FD3F3` | acento (valores, links) |
| `Ui.DANGER` | `#E46A6A` | item faltando |
| `Ui.OK` | `#7BD88F` | item ok |
| `Ui.WARNING` | `#E5C07B` | alerta (imagem travada) |

Regra absoluta: **cor literal só dentro de `Ui.java`**.

## Tipografia

- Roboto do sistema; nenhuma fonte embutida.
- Seção: 11 sp, bold, `letterSpacing 0.22`, caixa alta (`Ui.section`).
- Wordmark: 22 sp, bold, `letterSpacing 0.30`.
- Apoio: 10–11 sp em `CHROME_DIM`.
- Valor de opção: 12 sp, bold, `ACCENT`, com `›` no fim.

## Componentes prontos

`Ui.card`, `Ui.heroCard`, `Ui.primaryButton` (cromado, 58 dp, texto escuro),
`Ui.ghostButton` (contorno, 46 dp), `Ui.row` (opção com valor à direita),
`Ui.chip`, `Ui.tab`, `Ui.divider`, `Ui.text`, `Ui.label`, `Ui.dp`.

## Regras de tela

- Paisagem, duas colunas: esquerda 1,02 (marca + JOGAR) e direita 1,10 (abas).
- Cabeçalho fixo: logo 62 dp + wordmark + versão/assinatura à direita.
- Abas: **Jogo**, **Diagnóstico**, **Créditos**.
- pt-BR em tudo. Rótulos curtos ("Testar driver").
- Números antes de adjetivos: "71,6 ms (14,0 fps)".
- Alerta de diagnóstico em `WARNING`, nunca em `DANGER`.
- Margens em múltiplos de 2 dp; raio 12–20 dp.

## Ícone

Gerado por `tools/make_brand_assets.java` (só `javax.imageio`): foreground com o
logo a 66 % da safe zone, camada `monochrome` com a silhueta branca, fundo em
gradiente grafite (`drawable/ic_launcher_background.xml`).

## Ao mexer na UI

1. Novos componentes → `Ui.java`, não no `MainActivity`.
2. Texto novo → pt-BR; se for valor de cvar, **não** traduzir (ver
   `agents.md`).
3. Validar com `adb install -r` e print nas três abas.
4. Guardar o print em `doc/img/` se for mudança visual relevante.
