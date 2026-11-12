# Issue #8 — Configurações em tela própria

## Comportamento

- `SettingsActivity` interna, com toolbar Material 3 “Configurações”, seta voltar,
  seções roláveis e Salvar fixo no rodapé. O teclado redimensiona a tela.
- Testar conexão fica na seção Servidor e usa somente a seleção digitada.
  Salvar valida antes de ativar e usa a revisão capturada no início da edição.
  Verificar atualizações continua consultando `settings.active()`.
- Rascunho (ambiente, IP, porta, nome, base e revisão original) restaurado após
  recriação e após voltar/reabrir. Rascunho não ativa ambiente nem cadastra nome;
  Salvar o remove. Não se recaptura uma revisão mais nova ao restaurar.
- Retorno da tela não dispara uma segunda sincronização automática de nome:
  isso substituiria o token do cadastro iniciado por Salvar. Os demais inícios
  da tela principal mantêm a sincronização existente. A indicação de retorno
  também é preservada no estado salvo da Activity principal.
- Localização segue com aplicação imediata, independente de Salvar, usando o
  mesmo fluxo de permissões em duas etapas. Retorno das configurações do sistema
  atualiza a seção e reavalia o rastreamento.
- Respostas do updater não abrem confirmação/instalador com a Activity fora de
  STARTED, encerrando ou destruída. Se uma resposta chega enquanto a tela está
  parada, o botão é liberado para nova tentativa ao retornar.
- Alvos interativos com mínimo de 48dp; títulos das quatro seções preservam
  `accessibilityHeading`; feedbacks preservam live regions.

## Revisão manual do código e recursos

- Lidos `../AGENTS.md`, `docs/agents/` e `docs/ui-redesign.md`; sem chamadas
  gh/GitHub. Não há AGENTS.md na raiz deste worktree, glossário ou ADR pertinente.
- Conferidos os caminhos menu, cartão Localização, banner de nome e diálogo
  Servidor: todos continuam passando por `showSettings`, agora com Intent explícito.
- Conferidos handlers de teste, validação/cadastro com revisão e destino ativo
  do updater. `AppSettings`, modelos JVM, ciclo de eventos de telefone, fila,
  serviços e implementação do updater não foram alterados.
- Dois testes instrumentados novos cobrem recriação e sair/reabrir sem ativar
  o ambiente digitado. Não há controller JVM alterado que exija novo ciclo TDD.

## Build e testes executados

- Copiado `local.properties` do worktree principal antes do primeiro Gradle.
  O cache global recusou o lock; um cache local resolveu esse bloqueio.
- SDK original referenciado não estava acessível. O SDK em `C:/Android/Sdk`
  permite leitura, mas não instalar o Build Tools exigido. Plataforma android-35
  e licenças foram copiadas para `.gradle/android-sdk`; Gradle instalou ali
  Build Tools 35.0.0 e Platform Tools. Somente `local.properties` ignorado foi
  ajustado para esse SDK. Nenhuma configuração de build versionada mudou.
- JDK 17 (`C:/Program Files/Eclipse Adoptium/jdk-17.0.12.7-hotspot`), cache Gradle
  local (guardado em `.gradle/issue-8-home` após a execução) e `ANDROID_USER_HOME`
  em `.gradle/android`, todos os diretórios graváveis dentro do worktree.
- `gradlew.bat test assembleDebug assembleDebugAndroidTest`: **BUILD SUCCESSFUL**,
  85 tarefas executadas. **82 testes debug + 82 release = 164 execuções**, em
  15 suítes por variante; **0 falhas, 0 erros, 0 ignorados**.
- APK debug gerado: `app/build/outputs/apk/debug/app-debug.apk` (7.573.574 bytes).
  APK instrumentado compilado: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
  Compilação dos testes instrumentados não significa execução em dispositivo.
- `git diff --check` sem erros. Artefatos, SDK e caches ficam em diretórios
  ignorados; a migração não inclui `local.properties` ou arquivos temporários.
- Branch `fix/issue-8`. `git add` recusado ao criar
  `bina-client/.git/worktrees/-wt-client-8/index.lock` (`Permission denied`).
  Commit não criado; working tree pronta. Mensagem prevista:
  `Move configurações para tela própria`. Sem push.

## Verificação em aparelho pendente

O ADB desta sessão não conseguiu iniciar seu daemon (`could not read ok from
ADB Server`; o ADB novo também tentou criar `\\.android` sem permissão).
Portanto não se declara validação visual/manual em dispositivo,
nem execução dos testes instrumentados. Roteiro para concluir:

1. Abrir por cada entrada, usar seta e voltar do sistema. Conferir pilha sem
   duplicar a principal; digitar IP/porta/nome inválidos, girar e reabrir.
2. Em tela pequena, paisagem, fonte ampliada, teclado aberto e modo escuro,
   conferir rolagem, Salvar visível e alvos de toque. No TalkBack, navegar pelos
   quatro headings e ouvir feedback de validação/teste/atualização.
3. Testar destino local sem salvar: ambiente ativo e entrega de chamadas seguem
   no destino anterior. Salvar: ativar/cadastrar com a revisão original; simular
   alteração concorrente do nome no servidor durante a edição e durante rotação.
4. Verificar atualizações com produção ativa e local apenas digitado: consulta
   produção. Repetir após salvar local; conferir sem publicação, versão atual,
   atualização disponível, hash/assinatura inválidos e instalação autorizada.
5. Sair/girar/colocar em segundo plano durante consulta e download; nenhuma
   resposta antiga abre instalador. Retornar e repetir a operação.
6. Android 10/11/13: ligar localização, conceder/negar primeira etapa, conceder/
   adiar segunda, voltar das permissões do sistema e desligar. Conferir estado
   imediato da chave e ausência de pedidos repetidos ao girar.
7. Receber chamadas com Configurações aberta e fechada, inclusive offline;
   conferir fila/entrega durável, recuperação e notificação do monitoramento.

Risco residual: integração visual, permissões e instalação real dependem desse
roteiro e dos testes instrumentados em aparelho/emulador.
