# doc/09 — Drifts de freeroam: mapeamento e como forjar

Mapeado em 21/10/2026 cruzando o print do mapa com a tabela de áreas do save.

## O que é cada coisa

| Elemento | Onde | O que é |
|---|---|---|
| Tabela de áreas | `0x4DC`, 73 registros × 12 B | **um registro por evento de freeroam** (corridas e drifts) |
| Hexágono do mapa | 7 registros consecutivos da tabela | as 7 corridas/drifts de um hexágono do mapa |
| "N/7" na tela | — | quantos dos 7 do hexágono já foram concluídos |
| `owner_a` / `owner_b` | registro de área | o **crew** dono do record (`0xC49D46EF` = jogador) |
| ★ verde / laranja no mapa | — | o **crew** do evento: verde = Bushido, laranja = 21st Street |

Flags do registro: `0x40` visível · `0x01` tentada · `0x02` **vencida** ·
`0x20` do jogador · `0x04` rival. O jogo grava `0x66` quando o jogador vence.

### Crews (hashes de owner)

| Hash | Crew |
|---|---|
| `0x40693BA3` | **Bushido** (Kenji) |
| `0x27182028` | **21st Street** (Angie) |
| `0xA9382979` | Wolf |
| `0x10A2F397` | Darius |
| `0x97F3A210`, `0xCF59E641`, `0x3400C6F4`, `0x1AB75EC8` | outros do mesmo conjunto |

## As 5 missões pendentes (20/10) e as áreas forjadas

| missão | crew | hexágono | área forjada |
|---|---|---|---|
| Kings Park | Bushido | 6/7 | **3** |
| Kimei Temple | Bushido | 5/7 | **36** |
| Lookout Point | Bushido | 7/7 | **70** |
| Devil's Creek Pass | 21st Street | 2/7 | **13** |
| Knife's Edge | 21st Street | 1/7 | **64** |

## Receita

```bash
python3 tools/forjar_areas.py save.device saida.device 3 36 70 13 64
adb push saida.device \
  /sdcard/Android/data/com.nfscarbon.android/files/saveio/in/ALIAS_ANDERSON
# abrir o LAUNCHER (não o jogo): o SaveIO importa no onCreate
```

## Lições (custaram um mapa vermelho destravado)

1. **Só marque registros VISÍVEIS** (`flags & 0x40`). Os `0x04` (sem `0x40`) são
   eventos de região bloqueada: marcar um deles **destrava a região errada** —
   foi o que abriu o mapa vermelho (Carbon Canyon) numa primeira tentativa.
2. **Confirme o hexágono pelo contador**: o "N/7" da tela é a chave para achar
   qual registro é o evento (janela de 7 registros com N concluídos e o
   registro pendente do crew certo).
3. **O import só vale com o jogo fechado** — o motor reescreve o save e o
   patch se perde. O app já se protege (`SaveIO` ignora `saveio/in` com o jogo
   vivo).
4. **Confira o export depois do import** (`adb pull .../saveio/out/ALIAS_ANDERSON`
   e compare o md5 com o arquivo forjado) antes de mandar o usuário abrir o jogo.
5. **Backup antes de forjar**: `save_pre_forja.device` (estado original) fica em
   `/tmp/opencode/` e também em `/sdcard/Documents/nfs-carbon-backups/`.
