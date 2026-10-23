# doc/10 — Desbloqueios (unlocks) e recompensas

Descoberto em 23/10/2026 editando o save real. Fonte das listas de IDs:
`github.com/TheLastOfLogic/NFS-Carbon-Save-Editor` (`NFS Carbon/Read_Data_Table.cs`),
o mesmo editor C# documentado em `30 Conhecimento/_ferramentas/NFS-Carbon-Save/RELATORIO-SAVE-EDITOR.md`.

## A linha de unlocks (`3391526480:`)

Fica no bloco de texto da carreira (3º container JDLZ). Formato:

```
3391526480:  <total>  <u1>  <u2>  <u3>  id1  valor1  id2  valor2  ...
```

- `tabs[0]` = rótulo, `tabs[1]` = nº de pares, `tabs[2..4]` = desconhecidos (o editor não mexe),
  pares começam em `tabs[5]`, passo 2.
- **PRESENÇA = DESBLOQUEADO.** O `valor` (0 ou 1) não controla nada visível. Anexar o `id`
  libera o item; **remover bloqueia de novo**. Provado na prática: trocar 36 pares "flag 0" por
  IDs novos liberou os novos e **bloqueou os 36 que saíram** (o dono viu na hora).
- Anexar = `(id, 1)` no fim + `tabs[1] = (tokens-5)/2` (é o que o editor faz).
- O editor tem um ramo destrutivo (se passar de 1000 pares ele apaga tudo e reescreve) — não usar.

## De onde vêm os IDs

| Categoria | Origem | Tipo |
|---|---|---|
| Performance (motor/câmbio/suspensão/…), Visual (paint, wheel paint, vinyl, tint), Body (autosculpt/kits/rims) | arrays literais `uint` no editor | **ID literal**, não é hash de nome |
| Carros (stock, bonus, custom, cop, traffic, beta) | `Bin_Hash(nome)` — `h=0xFFFFFFFF; h=h*0x21+ch` | hash do nome interno minúsculo |

Listas prontas em `tools/unlock_ids.json` (performance 51, visual 88, body 255,
cars custom 22 / bonus 15 / stock 38 / cop 16 / traffic 35 / beta 2).

### Nunca anexar carro na mão
Anexar `Bin_Hash("challengern")` no lugar de um par existente **crashou o jogo** no load:
`[FATAL] Call to invalid or unregistered function at guest address 0x0` (libmain/rexruntime).
Carro de Car Lot entra pelo evento que o desbloqueia (ex.: Challenger Concept = vencer
"Desperation Ridge", Silverton Refinery; depois compra por 140.000).

## Crescer o bloco (o que parecia impossível)

O bloco de carreira tem **32768 B de texto, sem NUL de sobra** (o jogo fecha com uma linha de
zeros truncada). Para anexar pares é preciso crescer:

1. `dec` do container (`+0x04`) = **tamanho exato do texto** (ex.: 33268). Não precisa ser
   32768 nem múltiplo — o jogo aceita (provado in-game).
2. `+0x08` = **0x18 + len(comprimido)** (total do container; é isso que o walk lê).
3. Se o comprimido couber no payload antigo, mantém o arquivo do mesmo tamanho (pad NUL).
   Se não couber, anexar zeros no fim e atualizar `0x04` (tamanho) e `0x0C` (tamanho-0x2C).
   Depois dos 3 containers há **~58 KB de zeros livres**.
4. `update_profile_md5` + `update_hashes` (os 3 CRC da EA).

## Recompensas

- O prêmio de cada evento é um **pacote de peças** ("ENGINE/TRANSMISSION/SUSPENSION – TIER X
  STREET/PRO/RACING PACKAGE"), não carro. O painel mostra "PRIZE PACK – TIER X ... PACK".
- **Forjar a área NÃO entrega a recompensa** — o jogo só grava no fim da corrida (o
  "Transmission – Tier 3 Racing Package" do Journeyman's Bane não veio).
- O que já foi vencido/jogado aparece na linha de unlocks; o que faltou pode ser entregue
  anexando os IDs de `performance`/`visual`/`body` (sem carro).
- Reward Cards (linha `2268361667:`): `tabs[76]` = nº de cards normais, `tabs[338]` = online.
  Os prêmios dos 25 cards offline são rims, autosculpt, vinyls e os carros: Chevelle SS,
  Murciélago LP640, Police Civic/Interceptor/Rhino, Cross Corvette, Dump Truck, Eclipse 1999,
  Fire Truck, BMW M3 GTR (+ online: GTO, MR2, 911 GT3 RS, vinyls e rims BBS).

## Estado em 23/10/2026

- Save atual: `restore2.device` (md5 `161bc0b2…`) — 567 pares, bloco 33268 B.
- Entregue: **51/51 performance** (Tier 3 Racing incluso) e **88/88 visual**
  (tribal, race flag, paint, tint). Confirmado pelo dono in-game.
- Pendente: body/autosculpt (255) e carros dos cards — carros exigem plano à parte.

## Receita

```bash
python3 tools/unlocks.py save.device --list
python3 tools/unlocks.py save.device saida.device --add performance visual
# import: jogo fechado -> push em saveio/in/ALIAS_ANDERSON -> abrir o LAUNCHER -> conferir md5
```

## Lições

1. Presença = desbloqueado; nunca substituir/remover par da linha de unlocks.
2. Flag/valor do par não significa nada visível (o flip de 197 flags não mudou nada).
3. ID de peça/visual é literal; só carro usa `Bin_Hash` do nome.
4. Anexar carro na linha de unlocks crasha o jogo — carro se desbloqueia no evento.
5. O bloco pode crescer (dec = tamanho do texto); o save tem ~58 KB de zeros depois dos containers.
6. Sempre conferir o export (`saveio/out`) contra o arquivo forjado antes de mandar abrir o jogo.
