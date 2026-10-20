# doc/05 — Guia visual

## Marca

A marca é **monocroma**: prata cromada sobre preto absoluto, com uma figura
encapuzada e um motivo de torre/relógio. O app inteiro segue essa leitura —
nada de vermelho de console, nada de listras racing.

### Paleta

| Token | Hex | Uso |
|---|---|---|
| `Ui.BLACK` | `#07070A` | fundo base |
| `Ui.SURFACE` | `#101017` | painel principal |
| `Ui.SURFACE_ALT` | `#17171F` | linha de opção |
| `Ui.CARD` | `#1C1C26` | cartão |
| `Ui.OUTLINE` | `#2C2C39` | borda de 1 dp |
| `Ui.CHROME` | `#C9CFDD` | texto principal |
| `Ui.CHROME_DIM` | `#8A92A3` | texto secundário |
| `Ui.ACCENT` | `#5FD3F3` | acento elétrico (links, valores) |
| `Ui.DANGER` | `#E46A6A` | item faltando |
| `Ui.OK` | `#7BD88F` | item ok |
| `Ui.WARNING` | `#E5C07B` | alerta (imagem travada) |

Regra: **cor literal só em `Ui.java`**. Qualquer tela nova usa `Ui.*`.

### Tipografia

- Sistema (Roboto), sem fonte embutida: menos peso no APK e mais legibilidade.
- Título de seção: 11 sp, **bold**, `letterSpacing 0.22`, caixa alta.
- Wordmark "DEMENTECH": 22 sp, bold, `letterSpacing 0.30`.
- Texto de apoio: 10–11 sp, `CHROME_DIM`.
- Valores de opção: 12 sp, bold, `ACCENT`.

### Componentes

| Componente | Regra |
|---|---|
| Cartão | raio 18 dp, borda 1 dp `OUTLINE`, fundo `CARD` |
| Cartão herói | gradiente diagonal `#1A1A24 → #0C0C12`, raio 20 dp |
| Botão primário | gradiente cromado `#F2F5FA → #9AA3B5`, texto `BLACK` 19 sp bold, altura 58 dp |
| Botão secundário | fundo 8 % de branco, borda `OUTLINE`, raio 12 dp, altura 46 dp |
| Linha de opção | fundo `SURFACE_ALT`, raio 12 dp, título à esquerda e valor `ACCENT` à direita com `›` |
| Selo (chip) | texto 10 sp bold, fundo 13 % da cor, borda 40 % |
| Aba | cápsula; selecionada = fundo `CHROME` e texto `BLACK` |
| Divisor | 1 dp `OUTLINE`, margem vertical 10 dp |

### Layout

- Paisagem, duas colunas: **esquerda 1,02** (marca, estado do jogo, JOGAR) e
  **direita 1,10** (abas e conteúdo).
- Cabeçalho fixo no topo: logo 62 dp + wordmark + versão/assinatura à direita.
- Padding lateral 22 dp; painel direito com raio 20 dp sobre o fundo.
- Tudo com `dp()` real (densidade do aparelho).

### Ícone

- Adaptativo (API 26+): fundo em gradiente grafite, foreground com o logo a
  66 % da safe zone (66 dp de 108 dp), camada `monochrome` com a silhueta
  branca.
- Legado: 192×192 com fundo escuro e logo a 86 %.
- Geração: `tools/make_brand_assets.java` (só `javax.imageio`, roda no
  Termux).

### Regra de conteúdo

- pt-BR sempre; rótulos de opção curtos ("Testar driver", não "Testar driver
  Vulkan (GPU/Vulkan)").
- Números antes de adjetivos: "71,6 ms (14,0 fps)".
- Alerta de congelamento em `WARNING`, nunca em `DANGER` (não é erro, é
  diagnóstico).
