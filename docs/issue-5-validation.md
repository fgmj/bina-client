# Issue #5 — segundo plano e recuperação

## Escopo e evidência

Matriz de qualificação proposta; nenhum modelo físico está homologado nesta
execução. Instalar não significa ter telefonia/segundo plano validados.
Não houve implantação em produção. A fila durável e as identidades do issue #4
continuam sendo o mecanismo de recuperação dos eventos já observados.

## Matriz de versões e aparelhos

- Android 7–11 (API 24–30): compatibilidade legada; qualificar Motorola Moto G
  com SIM real. API 24 é o mínimo do aplicativo.
- Android 12–12L (API 31–32): qualificar Samsung Galaxy A com One UI; verificar
  restrições de início de serviço e suspensão de aplicativos.
- Android 13 (API 33): qualificar Pixel e Galaxy A; testar notificações negadas,
  canal desativado e permissões de telefone negadas separadamente.
- Android 14–15 (API 34–35): qualificar Pixel e Galaxy A; validar tipo do serviço,
  boot após desbloqueio e recuperação após parada pelo usuário.
- Android 16 e posteriores: fora da homologação até execução da matriz; não
  presumir suporte porque a instalação foi permitida.
- Xiaomi/Redmi/HyperOS: qualificação adicional obrigatória de autoinício e bateria;
  não prometer equivalência com Pixel. Emulador não homologa rádio nem fabricante.

Todos os itens acima estão **pendentes em aparelho**. Registrar modelo, versão,
patch, fabricante, SIM/operadora, configuração de bateria e build do APK.

## Permissões e limites verificados na documentação oficial

Consulta em 21–22/10/2026:

- `READ_PHONE_STATE` e `READ_CALL_LOG`: captura existente exige ambas; a segunda
  é restrita e sua concessão pode depender do instalador/política do dispositivo.
  Não solicitar papel de discador apenas para contornar restrições.
- `FOREGROUND_SERVICE`: serviço com notificação. `POST_NOTIFICATIONS` (API 33+)
  controla visibilidade na gaveta, sem ser requisito para iniciar o serviço.
- Localização é opcional; não garante posição atual em segundo plano.
- `RECEIVE_BOOT_COMPLETED`: recuperação depois do desbloqueio, sem acesso à fila
  protegida por credenciais antes dele. Não usar direct boot com essa fila.
- Broadcast de telefonia declarado no manifesto é exceção aos limites de
  broadcasts implícitos. Manter um único receiver; não registrar outro na tela
  ou no serviço.
- Force-stop não é encerramento recuperável automaticamente. Reabrir pelo ícone;
  chamadas não observadas durante a parada não são reconstruídas.
- Jobs podem atrasar por Doze, economia de bateria, rede ou fabricante. Serviço
  ativo não comprova entrega ao servidor nem recebimento no monitor.

Fontes oficiais:

- [Exceções de broadcasts](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)
- [Restrições de início de serviço](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Tipos de serviço](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Force-stop no Android 15](https://developer.android.com/about/versions/15/behavior-changes-all)
- [Permissão de notificações](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Broadcast de telefonia](https://developer.android.com/reference/android/telephony/TelephonyManager#ACTION_PHONE_STATE_CHANGED)
- [Ciclo de vida do processo](https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner)

## Implementação

- Mantidos minSdk 24, compileSdk 35 e targetSdk 33 da base para preservar a
  compatibilidade, inclusive do updater. Não se declara homologação de versões
  recentes nem conformidade para publicação na Play Store. Uma migração de target
  exige também qualificar interface, instalação de atualizações e comportamento
  de todos os componentes; ela não foi feita implicitamente neste ticket.
- Serviço declara `specialUse`, `FOREGROUND_SERVICE_SPECIAL_USE` e justificativa
  no manifesto. API 34+ passa o tipo explicitamente a `startForeground`; versões
  anteriores usam a API compatível. Não usa `phoneCall`: o app não é discador
  padrão nem implementa chamadas próprias via ConnectionService. A classificação
  `specialUse` ainda depende da política de distribuição/revisão aplicável.
- `stopWithTask=false` explicita a independência da tarefa. `START_STICKY` é
  pedido de recuperação ao Android, sem prazo nem garantia de reinício.
- Falhas síncronas ao solicitar e falhas ao promover o serviço são tratadas e
  ficam disponíveis nos detalhes de recuperação. Estado ativo só é publicado
  depois da promoção bem-sucedida; a perda de permissão invalida esse estado.
- Boot após desbloqueio e `MY_PACKAGE_REPLACED` reagendam a fila e solicitam o
  serviço. Um observador único de `ProcessLifecycleOwner` na Application solicita
  o serviço e agenda a fila ao entrar no primeiro plano, inclusive por outra
  Activity. Foi adicionada a dependência explícita `lifecycle-process:2.8.7`;
  sair do primeiro plano não encerra a captura. Retornar das configurações com permissões de telefone concedidas
  também solicita retomada. Localização/notificações opcionais não atrasam esse
  início. Não se tenta iniciar serviço a cada broadcast de telefonia.
- O único receiver de telefonia continua sendo o manifesto. `CallReceiver`,
  `CallOutbox`, `CallDeliveryJob`, `DurableCalls` e tracker não foram alterados.
  O agendamento do updater permanece igual; a Application existente também
  registra o observador do monitoramento. Captura e entrega independem do serviço e da Activity; serviço
  parado não significa que todos os broadcasts ou jobs estejam indisponíveis.
- `MonitoringState.assess` é a interface JVM aprovada para permissões, serviço
  observado e alertas. A tela mostra estado resumido e a ação **Monitoramento e
  recuperação** abre permissões faltantes, alertas e última falha, com ações para
  retomar, permissões do app, notificações do aplicativo/canais e configurações de bateria.
- A restrição detectada usa `ActivityManager.isBackgroundRestricted` (API 28+);
  não detecta todas as políticas de fabricantes. Revisar autoinício e bateria
  manualmente continua necessário. Não é solicitada isenção direta de bateria.

## Implementação e fronteiras

- `ProcessLifecycleOwner` observa a visibilidade do processo: `ON_START` solicita
  retomada do serviço e da fila. `ON_STOP` não cancela captura nem entrega. Essa
  observação agrega Activities e não é prova de que o serviço ou servidor estão ativos.
  [Contrato AndroidX](https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner).
- Há um único `CallReceiver`, declarado no manifesto. Não se registra receptor
  de telefonia na Activity, no Application ou no serviço. Os broadcasts duplicados
  do Android continuam passando pela deduplicação durável existente do issue #4.
- `BOOT_COMPLETED` e `MY_PACKAGE_REPLACED` reagendam a fila e tentam iniciar o
  serviço. A falha de início fica visível na UI; não equivale à perda da fila.
  Aplicativos restritos podem ter o boot adiado até abertura manual.
- `MonitoringState.assess` é a seam JVM aprovada: permissões, serviço observado,
  notificações e restrição de bateria geram estado e orientação de recuperação.
  Notificações e localização opcionais não bloqueiam o início com telefone autorizado.
- A UI consulta novamente o estado e mostra a última falha do serviço; oferece
  retomada e configurações de permissões, notificações e bateria. Não promete
  recuperar eventos durante force-stop nem provar recebimento no monitor.
- Ambiente/nome, IDs/destinos da outbox e agendamentos únicos do updater permanecem
  nos componentes dos issues #3, #4 e #6. O tipo FGS `specialUse` descreve o
  monitoramento; não solicita papel de discador nem usa `phoneCall` indevidamente.

### Aceite adicional

Repetir abertura/rotação/retorno das configurações e minimizar várias vezes;
confirmar um evento por transição, sem novos receptores. Atualizar APK com fila
offline e conferir recuperação via `MY_PACKAGE_REPLACED`, nome/ambiente e updater.
Após negar permanentemente telefone, conceder pelas configurações e voltar ao app;
confirmar retomada sem exigir localização ou notificações. Com telefone negado e
notificações/bateria restritas, conferir que todos os alertas continuam acessíveis.

### Validação automatizada desta alteração

Os testes JVM verificam o contrato de estado e orientação de recuperação, inclusive
serviço parado, permissões revogadas e alertas combinados. Não exercitam modem,
Doze, broadcasts reais, reboot nem política de fabricante. Esses cenários dependem
do roteiro físico abaixo e continuam pendentes até registro de evidência.

Execução em 22/10/2026: ciclo TDD da seam com falha esperada e posterior sucesso
dos 5 testes. `gradlew clean test assembleDebug --no-configuration-cache
--max-workers=1` concluído com sucesso (60 tarefas executadas); suites debug e
release sem falhas. Sem execução de testes instrumentados ou homologação física.
O `local.properties` foi copiado do worktree principal antes do Gradle; o SDK
informado em `C:/ambdes/Android/Sdk` não existia nesta sessão. A validação usou
uma cópia isolada do SDK API 35 disponível, com SDK/cache em `.gradle/issue5`,
ignorados pelo Git. Os arquivos de configuração local não integram o commit.

## Roteiro obrigatório por aparelho

Usar servidor local com banco dedicado, monitor aberto e origem selecionada.
Executar cada cenário separadamente com recebida/atendida/encerrada e não atendida:

1. Minimizar: gerar chamadas com a tela principal ausente; conferir fila, servidor
   e abertura única no monitor.
2. Bloquear: repetir após bloqueio e período de inatividade; medir atraso real.
3. Remover dos recentes: repetir sem reabrir. Registrar se o fabricante encerrou
   o serviço e se broadcasts continuaram chegando; não confundir com force-stop.
4. Reiniciar: deixar pendências offline, reiniciar, desbloquear e receber chamada
   antes de abrir o app. Retomar rede; conferir IDs, horários e destino original.
5. Forçar parada nas configurações: registrar interrupção esperada. Reabrir pelo
   ícone, conferir estado e retomada das pendências; não inventar eventos perdidos.
6. Negar telefone, negar notificações, bloquear canal e restringir bateria
   separadamente; conferir indicação e caminhos de recuperação.
7. Perder confirmação HTTP após aceitação e retomar: mesmo eventId, um registro
   no servidor e nenhuma aba duplicada. Repetir troca de ambiente com pendências.

Resultado atual de cada cenário: **não executado; sem aparelho disponível nesta
execução**. Registrar evidência do APK, tela, IDs no servidor e efeito no monitor;
testes JVM não substituem esse aceite integrado.

## Validação executada em 22/10/2026

- TDD no limite JVM aprovado: primeiro RED por interface ausente, seguido de
  GREEN com 1 teste; cenário de notificações/bateria RED (1 falha em 2), depois
  GREEN com 2. Recuperação/permissão de registro de chamadas e serviço parado
  produziram 2 falhas em 4 testes antes da correção. A suíte Gradle encontrou
  ainda o alerta ocultado por permissões faltantes (1 falha em 42), corrigido
  preservando todos os alertas nos detalhes.
- **Gradle `test`: aprovado no estado final.** Relatórios XML confirmados:
  **42 debug + 42 release, zero falhas, zero erros, zero ignorados**. São 42
  cenários únicos: 37 existentes (ciclo, ambiente/HTTP, fila e updater) e 5 novos
  de estado/recuperação. A execução JVM isolada anterior passou com 41 testes;
  a contagem final de 42 foi validada pelo próprio Gradle nas duas variantes.
- **Gradle `assembleDebug`: aprovado**, 33 tarefas (19 executadas), APK em
  `app/build/outputs/apk/debug/app-debug.apk`. Assinado com chave debug temporária
  de validação; não usar esse APK para atualizar instalações existentes.
- O `local.properties` do checkout principal foi copiado conforme solicitado,
  mas apontava para `C:/Users/ferna/AppData/Local/Android/Sdk`, inacessível aqui;
  `C:/ambdes/Android/Sdk` também não estava disponível neste sandbox. O arquivo
  local ignorado foi ajustado para o SDK 35 temporário do issue #4.
- As tentativas normais de `test`/`assembleDebug` foram executadas e falharam
  por permissões: lock do cache global, fechamento de JAR pelo compilador
  (`lifecycle-livedata-2.6.2-api.jar`) e `debug.keystore.lock`.
- Para obter a validação final, usados `GRADLE_USER_HOME`/`ANDROID_USER_HOME`
  temporários e script externo ao repositório em
  `%TEMP%/bina-issue5-jvm/expanded-classpath.gradle`. O script extrai JARs da
  classpath/bootstrap para diretórios durante compilação, sem alterar os fontes
  ou dependências declaradas; desativa processadores de anotação (nenhum é
  declarado no projeto) e aponta a assinatura debug para uma chave temporária.
  Os comandos finais foram `gradlew.bat test` e `gradlew.bat assembleDebug`, ambos
  com `--no-configuration-cache --max-workers=2 -I <script>`. Não se afirma que
  os comandos sem esse contorno tenham passado neste sandbox.
- `git diff --check`: sem erros. Nenhum acesso a GitHub, push ou deploy.
- O ADB temporário falhou ao criar `\\.android` por acesso negado, mesmo com
  diretórios Android temporários configurados. Não houve teste instrumentado,
  captura de tela, telefonia física ou nova validação integrada servidor/monitor.
  A homologação da matriz permanece pendente; resultados JVM não a substituem.

## Arquivos e commit

- Configuração: `app/build.gradle` e `app/src/main/AndroidManifest.xml`.
- Integração Android em `app/src/main/java/br/com/bomgas/bina/`:
  `BootReceiver.java`, `CallMonitorService.java`, `MainActivity.java`,
  `SettingsController.java`, `UpdaterApplication.java`, `MonitoringRecovery.java`
  e `MonitoringState.java`.
- Interface: `app/src/main/res/layout/activity_main.xml`.
- Testes: `app/src/test/java/br/com/bomgas/bina/MonitoringStateTest.java`.
- Documentação: `EVENTOS_CHAMADA.md` e este arquivo. Total: 13 arquivos.

A branch permanece `fix/issue-5`. A tentativa de `git add` foi recusada com
`Unable to create .../.git/worktrees/-wt-client-5/index.lock: Permission denied`.
Não foi possível criar o commit neste perfil de acesso; o working tree está
pronto para revisão. Mensagem prevista: `Reforça monitoramento Android em segundo plano`.
Sem push.
