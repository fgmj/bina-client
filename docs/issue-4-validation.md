# Issue #4 — fila durável Android

## Implementação

- `DurableCalls` é o limite JVM para observação, persistência e entrega. Um snapshot
  versionado guarda o tracker por assinatura, identidade/destino da ligação e outbox.
  O arquivo fica em `getNoBackupFilesDir()/calls-v1.json`: sobrevive a processo e
  reboot, mas não é restaurado em outro aparelho por backup/transferência.
- Gravação protege o snapshot anterior com `.bak`, escreve e sincroniza o novo
  arquivo antes de remover o backup. Estado em memória só avança após o commit.
  Recuperação de uma substituição interrompida restaura o backup. Arquivo ilegível
  provoca erro visível, sem inicializar uma fila vazia por cima dos dados.
- Uma única gravação inclui a transição e o evento. `sourceType=android`, `sourceId`,
  `callId`, `eventId` e `occurredAt` UTC são aditivos ao envelope legado.
  `additionalData` continua string JSON; `data` também é capturada na ocorrência.
  Reenvios usam o mesmo JSON, sem atualizar horários, bateria ou localização.
- O destino e a origem são fixados no primeiro toque e persistidos para todo o
  ciclo. Eventos pendentes continuam destinados ao servidor original, mesmo se
  ele estiver inativo no seletor. Novas ligações usam a seleção atual. Nenhuma
  correlação por telefone ou entre Android e GoTo é feita.
- A tentativa é reservada em disco antes do HTTP, inclusive para morte durante
  envio. Falhas mantêm o evento e o erro; backoff dobra de 15 s até 1 h. Cada lote
  entrega até 20 eventos, preserva ordem por destino e permite que outros destinos
  avancem. Só HTTP 2xx remove a pendência; redirecionamentos não são seguidos.
- Uma confirmação perdida exige repetir a requisição: a identidade original
  permite a deduplicação do issue #4 do servidor. Eventos cuja confirmação foi
  persistida não são reenviados. Não há promessa de exatamente uma requisição HTTP.
- Broadcasts usam `goAsync()` e executor de captura; HTTP usa outro executor.
  JobScheduler persiste o agendamento, exige rede e controla tentativas. Um job
  periódico de 15 min recupera a janela entre commit/agendamento e a corrida de
  chegada de evento durante finalização de um job vazio. Android pode atrasá-los.
- A tela principal mostra resumo compacto da fila; tocar no status abre detalhes
  agrupados por destino, com quantidades, tentativas, erros e próxima tentativa.
  Falhas de armazenamento permanecem indicadas. O backlog não expande o cabeçalho
  para fora da tela.

## Ciclo e limites

- Mantidos recebida/atendida/encerrada, recebida/não atendida, número oculto,
  repetições, chamadas sucessivas e separação por assinatura.
- Reinício só do processo preserva o relógio monotônico da conversa. Após reboot
  (BOOT_COUNT diferente), um término não inventa duração monotônica. Um novo toque
  recebe identidade nova, deixando a ligação antiga incompleta se seu término não
  foi observado. BOOT_COUNT indisponível também impede afirmar duração de conversa.
- Eventos que o Android não entregou ao processo não podem ser reconstruídos.
  Chamada em espera na mesma assinatura e ausência de identificação da assinatura
  continuam com os limites de observação descritos em `EVENTOS_CHAMADA.md`.
- Não há descarte automático de pendências. Backlogs grandes aumentam custo de
  regravação do snapshot e uso de disco; falta de espaço precisa de ação do usuário.
  Falhas HTTP permanentes permanecem visíveis e bloqueiam a ordem naquele destino.
- Forçar parada, restrições de bateria/fabricante e permissões exigem aparelho real;
  não se garante execução durante force-stop. Reabrir o app agenda recuperação.

## Validação nesta sessão

Em 21/10/2026:

- **33 testes JUnit aprovados, zero falhas** no estado final do código: 10 do
  tracker, 2 de ambiente, 8 de HTTP/cadastro e 13 da fila/recuperação/reenvio.
  Incluem concorrência entre captura e rede, falha de disco sem avanço do ciclo,
  snapshot interrompido/incompleto, confirmação perdida e backoff limitado.
- RED observado: morte durante envio e novo toque após reboot falharam antes da
  correção (7 testes, 2 falhas); snapshot incompleto também falhou antes da correção
  (6 testes de armazenamento, 1 falha). GREEN final: os 33 cenários passaram.
- A compilação JVM isolada usou JavaCompiler/JSR 199 com file manager mantido até
  a saída do processo, seguida de `org.junit.runner.JUnitCore`. Esse harness
  temporário evita o erro de fechamento de arquivos ZIP do JDK neste sandbox;
  não substitui o build Android nem representa execução de testes via Gradle.
- **Integração real aprovada:** driver JVM com `DurableCalls` e `ServerApi` de
  produção, duas instâncias HTTP locais (`127.0.0.1:33444/33445`) do servidor já
  compilado, MariaDB 11.4.5 portátil próprio em `127.0.0.1:33344`, bancos novos
  `bina_test` e `bina_test_b`. Primeiro preservou 14 eventos com ambos os endpoints
  desligados, incluindo recuperação por novos processos/instâncias da fila.
  Após iniciar os servidores, descartou a confirmação do primeiro POST aceito em
  cada destino: o histórico tinha um evento em cada banco e a fila ainda tinha 14.
  Ao retomar, o histórico terminou com **12 eventos/5 ligações em A** e
  **2 eventos/1 ligação em B**, sem duplicação. Foram consultados `/api/eventos`
  e `/api/ligacoes`, conferindo `occurredAt` original, origem Android e isolamento
  inclusive ao trocar destino durante uma ligação. Fila confirmada permaneceu
  vazia após reabertura e não fez outro envio. O cenário usa o núcleo JVM;
  não valida rádio, JobScheduler nem interface em aparelho Android.
- Executados no fim `.\gradlew.bat test` e `.\gradlew.bat assembleDebug`, ambos
  com saída **1**. O cache global recusava seu lock; foram usados cache e SDK
  temporários permitidos. Depois de preparar SDK 35 e Build-Tools 35.0.0, `test`
  parou nas compilações debug/release por `AccessDeniedException` em
  `lifecycle-livedata-2.6.2-api.jar`. A última execução de `assembleDebug` parou
  em `validateSigningDebug`, com acesso negado a `debug.keystore.lock`; tentativas
  anteriores também encontraram a falha de acesso aos JARs na compilação.
  **Não há aprovação da suíte Gradle nem APK final confirmado.**
- `git diff --check`: sem erros. Não houve acesso a GitHub, push ou implantação.

## Commit

A branch já é `fix/issue-4`. A tentativa de `git add` foi recusada:

```text
Unable to create 'C:/ambdes/Projetos/bina/bina-client/.git/worktrees/-wt-client-4/index.lock': Permission denied
```

O perfil permite apenas leitura dos metadados Git e não permite elevação. Por isso
não foi criado commit; o working tree fica pronto para revisão. Mensagem prevista:
`Preserva e reenvia chamadas com fila durável`. Sem push.

## Arquivos alterados

- Núcleo e entrega: `DurableCalls.java`, `CallOutbox.java`, `CallDeliveryJob.java`,
  `IncomingCallTracker.java`, `ServerApi.java`.
- Integração Android: `CallReceiver.java`, `CallMonitorService.java`,
  `BootReceiver.java`, `MainActivity.java`, `SettingsController.java` e manifesto.
- Testes: `DurableCallsTest.java`, `DurableCallsRetryTest.java`,
  `DurableCallsStorageTest.java` e `ServerApiTest.java`.
- Documentação: este arquivo e `EVENTOS_CHAMADA.md`.

## Roteiro integrado em aparelho e servidor de teste

1. Usar dois servidores locais isolados com o issue #4 server e banco dedicado.
2. Sem rede, gerar recebida/atendida/encerrada, não atendida, oculto e sucessivas.
   Conferir pendências e horários; matar/reabrir processo e conferir recuperação.
3. Retomar rede e consultar `/api/ligacoes` e `/api/eventos`: uma ligação por
   `callId`, um registro por `eventId`, horários iguais aos da ocorrência.
4. Interromper a resposta depois do commit no servidor. Retomar: mesmo `eventId`,
   apenas um registro no histórico; reabrir após confirmação não produz novo POST.
5. Com pendências no servidor A, selecionar B (inclusive durante uma ligação).
   Conferir ciclo em A, novas ligações em B e nenhuma pendência de A recebida em B.
6. Reiniciar aparelho com pendências e durante chamada; conferir boot, retomada,
   duração desconhecida quando aplicável, tela bloqueada e permissões negadas.
   Registrar evidências visuais. A execução deste roteiro em aparelho está pendente;
   a integração JVM/HTTP/banco realizada acima não a substitui.
