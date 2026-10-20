# Ideia — Editor de save dentro do app

> Status: **ideia** (v0.3 do roadmap). A base técnica já está provada fora do
> app; falta portar a lógica para Java e desenhar a tela.

## O que já sabemos do formato

O save de carreira (`ALIAS_ANDERSON`) é um container **MC02** big-endian:

```
MC02 BE
├── cadeia de containers "22114455"
│   ├── HUFF  (32.768 B)  — blocos de recurso
│   └── JDLZ  (32.768 B e 5.128 B) — o perfil (texto) comprimido
├── tabela de áreas (0x4DC)  — 73 entradas × 12 B
│     vencedor = (owner_a, owner_b, flags) = (PLAYER, PLAYER, 0x42)
│     CC = 18 · LF = 58 · boss Skyline = 22
├── perfil comprimido: blocos JDLZ com o estado textual do jogador
│     (linhas "Unlocks 3391526480", cash, carros, tiers…)
├── MD5 do perfil em 0x3C-0x4B  = MD5(d[0x4C : 0x4C+0xFFF0])
└── 3 CRC da EA (tabela customizada, não a CRC-32 padrão)
```

Regras que a edição precisa respeitar:

1. Qualquer byte alterado entre `0x4C` e `0x1003C` **invalida o MD5** — recalcular.
2. Toda alteração no container exige **recalcular os 3 CRC da EA**.
3. Os *unlocks* são **IDs decimais com sinal**, derivados de djb2
   (`init 0xFFFFFFFF`) do nome interno da peça.
4. Registro de carro em `0x12148 + i*0x470` (slot 0 = motor, slot 1 = transmissão);
   pool de peças em `0x26AF4..0x2FF68`.
5. O jogo lê o save no boot; com o jogo aberto, ele sobrescreve no fim da
   sessão. **Editar sempre com o jogo fechado.**

## Por que no app

- Sem PC, sem `adb push` de arquivo, sem risco de mandar o save errado.
- Backup e restauração no próprio lugar.
- Diff antes/depois legível.

## Esboço da tela

```
┌─ SAVE ──────────────────────────────────────┐
│ Perfil: ANDERSON · 286.764 B · 20/10 19:55  │
│ [Dinheiro] [Peças] [Corridas] [Crews]       │
│                                              │
│  Desbloqueios            42 de 128            │
│  ▸ Metallic Paint        ✓                   │
│  ▸ Vinyl Azul            ✓                   │
│  ▸ Motor T1 Pro           ✓ (boss)            │
│                                              │
│  [Aplicar]  [Restaurar backup]  [Exportar]   │
└──────────────────────────────────────────────┘
```

## Regras de segurança (não negociáveis)

- **Backup automático** antes de qualquer escrita, com carimbo de data.
- **Nunca** escrever com o jogo em execução (o app detecta o `GameActivity`
  vivo e bloqueia).
- **Validação** antes de gravar: tamanho, MD5, CRC e um round-trip de
  descompressão/compressão JDLZ idêntico ao original.
- Se qualquer validação falhar: **não escreve** e mostra o motivo.
- O save edited é conferido contra a ferramenta de linha de comando (mesmo
  arquivo de entrada → bytes idênticos).

## O que falta decidir

1. **Nomes em português ou os internos?** (o jogo mostra os internos; o editor
   pode mostrar um mapa bonito e manter o interno no arquivo.)
2. **Quais itens editáveis primeiro?** Dinheiro e peças são fáceis; forjar
   corrida (precisa reescrever a tabela de áreas e marcar a vitória) é o
   delicado.
3. **Como dar nome ao save?** O jogo aceita vários perfis por pasta; o editor
   pode gerenciar a lista.
4. **A entrada do editor no menu** fica na aba Diagnóstico ou numa aba própria?
