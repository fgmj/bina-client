# Issue #6 — updater Android

Implementação na branch `fix/issue-6`, sem push. O AGENTS.md da raiz do worktree
não existe; foram consultados o AGENTS.md do workspace pai, `docs/agents/`,
`docs/issue-3-validation.md` e a skill TDD.

## Comportamento

- `UpdaterApplication` registra WorkManager periódico de 12 horas com rede
  conectada e backoff exponencial de 30 segundos. `MainActivity.onCreate`
  enfileira consulta única no início, inclusive se o processo já estava vivo
  pelo monitoramento. A agenda periódica é persistida pelo WorkManager.
- A cada execução captura o ambiente de `AppSettings`: URL base do ambiente,
  `/api/app/version?canal=dev` para local/dev e `canal=prod` para produção.
  Não troca a URL padrão do issue #3 pelo servidor DSV. Para usar o endpoint
  fornecido, selecionar local, endereço `https://bina-dsv.bomgas.com.br`, porta 443.
  Respostas 404/204 encerram silenciosamente, sem updater configurado.
- Compara exclusivamente versionCode com a versão instalada e a maior versão
  já oferecida naquele endpoint/canal. SharedPreferences com commit antes da
  notificação impede ofertas repetidas após reboot ou dispensa da notificação.
  Uma nova versão com código maior pode ser oferecida normalmente.
- APK é transmitido para `files/updates/download.part`, com limite de 256 MiB,
  timeout e descarte no finally. SHA-256 precisa conferir antes de mover para
  `update.apk`. Hash inválido ou arquivo incompatível gera backoff; não marca
  versão como oferecida. URL de download deve permanecer na mesma origem.
- Confere pacote, versionCode e certificados, incluindo histórico de rotação
  suportado no Android 28+. Assinatura divergente descarta o arquivo e mostra
  notificação discreta de problema, com explicação debug/release e registro no
  Logcat. Não propõe instalar esse arquivo. A mensagem também fica persistida.
- Notificação de baixa importância, silenciosa, apresenta versionName e ação
  Instalar. Com notificações negadas/bloqueadas, não consome a oferta. O toque
  verifica novamente ambiente, hash, pacote e assinatura fora da thread principal,
  solicita autorização de fontes desconhecidas quando necessária e abre o
  instalador com URI FileProvider e permissão temporária de leitura.
- `ServerApi` acrescenta versionCode/versionName ao cadastro de identidade e
  ao evento `TEST_CONNECTION`. Eventos de telefonia mantêm seu JSON original.
  Não cria evento de teste como efeito colateral do teste de ambiente do #3.
  CallReceiver e CallMonitorService não foram alterados.

## Testes e limitações desta execução

Foram escritos primeiro os testes das regras JVM, em ciclos de comparação,
não repetição e hash. A execução red/green pelo Gradle ficou bloqueada antes
de compilar: o cache global retorna acesso negado ao lock. Com GRADLE_USER_HOME,
ANDROID_USER_HOME e cache de projeto temporários, o bloqueio é SDK location
not found. Não há confirmação de zero falhas da suíte completa nem de APK gerado.

- 4 testes JVM novos: comparação, política de oferta, seleção de canal e
  vetores SHA-256 conhecido/corrompido/inválido.
- 2 testes instrumentados novos: URI content para APK privado e recusa de
  exposição de outros arquivos privados. Execução em dispositivo pendente.
- As primeiras tentativas JUnit isoladas encontraram AccessDeniedException nos
  JARs do cache e do temporário. Copiando somente as dependências para uma pasta
  descartável dentro do worktree, foi possível compilar e executar com javac/java:
  **23 testes JVM aprovados, 0 falhas** (4 novos do updater, 10 de chamadas,
  2 de ambiente e 7 de HTTP/cadastro). Para essa execução isolada, as duas
  constantes de BuildConfig foram materializadas a partir de app/build.gradle.
  Isso valida as regras e regressões JVM, não a geração Android de BuildConfig
  nem o build/manifest/WorkManager/instalador. Os artefatos de validação são
  descartáveis e não fazem parte dos arquivos do issue.
- `.\gradlew.bat test` foi executado e bloqueou no lock do cache global;
  com caches temporários, bloqueou por SDK location not found.
  `.\gradlew.bat assembleDebug` também foi executado e bloqueou por SDK location
  not found. Os dois comandos completos ainda precisam passar com SDK acessível.
- `git add` dos arquivos explícitos do issue foi recusado: Permission denied
  ao criar `bina-client/.git/worktrees/-wt-client-6/index.lock`. O commit
  `Implementa atualização segura por APK` não pôde ser criado. Working tree
  preservado na branch `fix/issue-6`, sem push.

## Validação pendente em aparelho

1. Rodar `gradlew.bat test`, `gradlew.bat assembleDebug` e
   `gradlew.bat connectedAndroidTest` com SDK/cache acessíveis.
2. Instalar versão antiga de teste, selecionar servidor DSV/porta 443 e publicar
   APK com mesma chave e versionCode superior. Conferir notificação, ação,
   permissão de fontes desconhecidas e instalador. Não usar keystore de produção.
3. Alterar hash/arquivo: confirmar descarte e ausência de ação de instalar.
   Publicar APK com chave diferente: conferir mensagem clara e Logcat Updater.
4. Dispensar oferta e reiniciar aparelho: o mesmo código não deve reaparecer.
   Publicar código superior: nova oferta deve aparecer.
5. Conferir canal sem manifesto (404), servidor offline, permissão de notificação
   negada, troca de ambiente durante download e durante permissão de instalação.
   Monitoramento deve continuar normalmente em todos os cenários.

WorkManager segue as restrições de energia/rede do Android: 12 horas é intervalo
de agendamento, não horário exato. A oferta é deliberadamente única por código
e ambiente; uma notificação dispensada não é recriada em todo boot. Persistir
antes de notificar prioriza não repetir a oferta; morte do processo nesse pequeno
intervalo pode impedir a exibição daquela oferta. O instalador Android continua
responsável pela autorização final e pelas restrições específicas do aparelho.
