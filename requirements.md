# requirements.md — requisitos

Escopo: o **launcher** Dementech do *NFS Carbon 360* para Android.

Convenção: `RF` = funcional, `RNF` = não funcional, `CA` = critério de aceite.

## 1. Produto

| ID | Requisito | Prioridade | CA |
|---|---|---|---|
| RF-01 | O launcher inicia o jogo a partir de uma cópia do Xbox 360 já extraída | crítica | Com `default.xex`, `NFS/` e `Movies/` válidos, o botão JOGAR sobe o motor em menos de 60 s |
| RF-02 | Importar a cópia do jogo direto do armazenamento | crítica | A importação copia os arquivos para `/sdcard/NFSCARBON` e valida a presença dos três itens |
| RF-03 | Verificar a edição do executável antes de jogar | crítica | SHA-256 de `default.xex` diferente do suportado → mensagem clara, sem subir o motor |
| RF-04 | Gerar a biblioteca de shaders na primeira execução | alta | Sem a biblioteca, o app compila `nfscarbon_shaders.nfsp` e só então sobe o motor |
| RF-05 | Selecionar, importar e testar drivers Vulkan | alta | ZIP com `meta.json` é importado; "Testar driver" mostra GPU, Vulkan e conjuntos de descritores |
| RF-06 | Exibir e ajustar as opções do motor | alta | Cada opção alterada é aplicada na sessão seguinte (no JOGAR) |
| RF-07 | **Diagnosticar o congelamento dentro do app** | crítica | A aba Diagnóstico lê o log do motor e informa quadros apresentados, fps de apresentação, quadros descartados e se a imagem está travada agora |
| RF-08 | **Recuperar a renderização travada** | crítica | "Reiniciar renderização" recria a Activity do jogo (nova surface Vulkan) sem perder o save |
| RF-09 | **Perfis de desempenho** | média | Três perfis ajustam resolução, fps, vsync, antialiasing e compilação de shaders de uma vez |
| RF-10 | Enviar diagnóstico (log + resumo) | média | Gera um ZIP e abre o cliente de e-mail com o resumo preenchido |
| RF-11 | Procurar atualizações | baixa | Consulta a API do GitHub e oferece o APK da release mais recente |
| RF-12 | Editor de save dentro do app | média (v0.3) | Formato MC02/JDLZ understood; ver `doc/ideias/editor-de-save.md` |

## 2. Identidade

| ID | Requisito | CA |
|---|---|---|
| RI-01 | Ícone próprio, adaptativo (Android 8+) e com camada monocromática | Ícone do sistema mostra a marca Dementech; layer monochrome presente |
| RI-02 | Interface em pt-BR | Nenhum texto de interface em espanhol no launcher |
| RI-03 | Identidade monocroma (grafite + cromo) com um único acento | Paleta centralizada em `Ui.java`; nenhuma cor literal fora dali |
| RI-04 | Assinatura visível "Desenvolvido por Dementech" | Presente no cabeçalho do launcher |

## 3. Não funcionais

| ID | Requisito | CA |
|---|---|---|
| RNF-01 | Compilável no próprio celular (Termux, ARM64) | `./gradlew :app:assembleRelease` conclui com < 2 GB de RAM e sem NDK |
| RNF-02 | Motor pinado e verificável | `tools/fetch_engine.sh` valida o sha256 do APK do motor |
| RNF-03 | Não distribuir material da EA | Nenhum `default.xex`, `NFS/` ou `Movies/` no repositório |
| RNF-04 | Não perder save ao atualizar o launcher | Mesma chave de assinatura; `adb install -r` preserva `/data/user/0/<pkg>` |
| RNF-05 | Diagnóstico reproduzível fora do app | `tools/diagnose_freeze.py` produz o mesmo número que a aba Diagnóstico |
| RNF-06 | Sem crash no fluxo principal | JOGAR, voltar, trocar aba e reabrir não geram `FATAL` no logcat |
| RNF-07 | Sem dependência de rede para jogar | O jogo sobe offline; só a busca de atualização usa rede |
| RNF-08 | Tempo de abertura do launcher | Primeiro frame visível em menos de 1,5 s no aparelho de referência |

## 4. Fora do escopo (nesta fase)

- Recompilar o motor (C++/ReXGlue) — exige o dump do executável do Xbox 360 e
  toolchain LLVM 20; é o item `1.0` do roadmap.
- Tradução do texto do jogo (que vive nos arquivos do port).
- Suporte a outros ports (a base do launcher é compartilhada, mas só o Carbon
  é mantido aqui).
