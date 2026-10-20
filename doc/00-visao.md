# doc/00 — Visão

## Problema

O port *NFS Carbon 360* roda nativo em Android, mas o launcher que o acompanha
é:

- **em espanhol**, com hierarquia visual de ferramenta técnica;
- **sem diagnóstico**: quando a imagem trava, o usuário não tem nenhuma pista
  do que está acontecendo;
- **sem recuperação**: o consolo conhecido (sair do app e voltar) só funciona
  se o usuário souber dele;
- e com um **bug de layout** no painel de opções.

## Proposta

Um launcher com identidade própria, em pt-BR, que **mede o problema** em vez
de escondê-lo:

1. **Marca**: logo, paleta, ícone adaptativo, tipografia — leitura de marca em
   2 segundos, sem a estética de "fórmula de console".
2. **Diagnóstico dentro do app**: a aba Diagnóstico lê o log do motor e diz,
   em números, se a renderização está saudável.
3. **Recuperação em um toque**: recriar a surface (a surface recriada destrava
   a imagem).
4. **Perfis de desempenho**: combinações testadas, aplicadas em um toque.
5. **Base para o futuro**: editor de save, watchdog automático e, no limite,
   motor próprio compilado do código-fonte.

## Princípios

- **Medir antes de mexer.** Toda mudança de performance começa com número
  antes/depois (`tools/diagnose_freeze.py`).
- **O launcher não decide o motor.** Ele expõe, mede e explica; o motor segue
  sendo o motor.
- **Nada de dado da EA no repositório.** Nem jogo, nem `default.xex`, nem
  log com conteúdo de jogo.
- **Identidade no app, licença no repositório.** O usuário vê Dementech; as
  atribuições exigidas por GPL ficam em `THIRD_PARTY_NOTICES.md`.
- **Sem política de update.** O launcher só *oferece* atualização; a instalação
  é sempre uma ação conscious do usuário (e preserva o save porque a chave é a
  mesma).

## Público

Quem tem o port funcionando no celular e quer (a) uma interface decente em
português, (b) entender e evitar o congelamento, (c) contribute para a
correção do caminho de apresentação.
