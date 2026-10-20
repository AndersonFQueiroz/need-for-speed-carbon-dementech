# doc/07 — Testes

## O que é testável hoje

O launcher é Java puro sobre componentes do Android; o motor é binário. Então o
teste se divide em três níveis:

1. **Lógica pura** (sem Android): parsing do log, conta de apresentação,
   detecção de janela travada. Testável com `javac`/JUnit local.
2. **App**: verificar telas, textos e fluxo com o aparelho conectado.
3. **Motor**: sessão real de jogo, medida pelo log.

## Ferramentas

| Comando | O que faz |
|---|---|
| `python3 tools/diagnose_freeze.py --sessions <logs>` | tabela por sessão |
| `python3 tools/diagnose_freeze.py --json <logs>` | saída para automação |
| `python3 tools/diagnose_freeze.py --top 8 --context carbon.log` | stalls com contexto |
| `tools/fetch_engine.sh` | valida o sha256 do motor (teste de integridade) |
| `java tools/make_brand_assets.java <logo> <logotipo> <out>` | gera os assets (teste de pipeline de marca) |

## Checklist de release

- [ ] `./gradlew :app:assembleRelease` conclui no celular
- [ ] `apksigner verify` confirma a assinatura esperada
- [ ] `adb install -r` por cima da versão anterior (save preservado)
- [ ] App abre sem `FATAL` no logcat (`adb logcat -d | grep -i nfscarbon`)
- [ ] As três abas renderizam em pt-BR
- [ ] Aba Diagnóstico mostra números (não "sem log")
- [ ] "Reiniciar renderização" volta a mostrar a imagem
- [ ] Perfil "Estável 720p" aplica e o jogo sobe com 1280×720
- [ ] Importar cópia do jogo funciona (ou a mensagem de erro é a correta)
- [ ] `tools/diagnose_freeze.py` reproduz os mesmos números da aba Diagnóstico
- [ ] Nenhum arquivo do jogo no repositório (`git ls-files | grep -E 'default.xex|NFS/'` vazio)
- [ ] `THIRD_PARTY_NOTICES.md` reflete o que mudou

## Teste do congelamento (manual, com o dono)

1. Deixar o jogo rodando em 720p/60 fps por ~20 min.
2. Se a imagem travar: **não** sair do app — abrir a aba Diagnóstico e ver o
   veredito ("sem apresentação há N s").
3. Testar "Reiniciar renderização" e conferir se a imagem volta.
4. Repetir com a **economia de energia desligada** (H3) e comparar o
   `apres. fps`.
5. Repetir com o perfil "Modo gravação" (H2) e com `log_level=warn` (H4).

Cada teste vira uma linha na tabela de `doc/02-congelamento.md`.

## Limites

- Não há testes automatizados de UI: o aparelho de referência é o único
  ambiente real (ARM64 + Adreno 650). Testes de UI emulador não valem, porque
  o problema é justamente do driver/path de apresentação.
- O motor não é testável sem a cópia do jogo do usuário.
