# Issue #3 — ambiente e nome no Android

## Correção do stub JVM em 21/10/2026

- `ServerApiTest` substitui `com.sun.net.httpserver.HttpServer` por um stub
  HTTP/1.1 interno baseado em `java.net.ServerSocket`, sem nova dependência.
  Preserva os seis testes existentes, verifica POST e acrescenta um teste
  explícito de porta ocupada. O stub usa portas efêmeras, timeout e propaga
  falhas da thread para o JUnit.
- Executados `.\gradlew.bat test` e `.\gradlew.bat assembleDebug`: ambos
  bloqueados pelo acesso negado ao lock do cache global. Repetidos com
  `GRADLE_USER_HOME` e `ANDROID_USER_HOME` temporários: ambos bloqueados por
  `SDK location not found`, antes de compilar/executar testes. Não há confirmação
  de zero falhas nem de APK compilado nesta execução.
- A tentativa adicional JVM isolada (`gradlew.bat -p` no projeto temporário,
  `test --offline`) também parou antes dos testes, por `AccessDeniedException`
  ao ler `okio-jvm-3.15.0.jar` no cache temporário.
- Branch atual: `fix/issue-3`. `git add` do teste recusado com
  `.git/index.lock: Permission denied`; alteração mantida na working tree.
  Mensagem de commit prevista: `Corrige stub HTTP dos testes JVM`. Sem push.

Escopo: histórias 1, 2, 24, 25 e 26 da especificação principal disponível no
workspace em `../docs/SPEC_EVOLUCAO_BINA.md`. Contrato de nomes consultado em
`../bina-cloud-server/docs/origens.md` (dependência bina-cloud-server#5).
Consulta ao issue pelo GitHub CLI retornou HTTP 401; critérios conferidos com a
especificação local e a solicitação de continuação. Sem push, deploy ou chamada
de validação à produção.

## Comportamento

- Produção inicia com `https://bina.fernandojunior.com.br/api/eventos`, sem alteração
  do endpoint padrão. A seleção em tempo de execução substitui o antigo override
  de debug `binaApiUrl`; essa propriedade não altera mais o destino dos eventos.
- Configuração permite escolher produção/local, editar host e porta (1–65535),
  testar antes de salvar e cadastrar o nome (1–255 caracteres). HTTP é o padrão
  local; `https://host` e IPv6 entre colchetes são aceitos. Caminhos, credenciais e
  portas embutidas no campo de endereço são recusados.
- Teste faz GET real em `/api/origens`, exige sucesso HTTP e uma lista JSON,
  com timeout total de 10 segundos. Não cria `TEST_CONNECTION`, não usa ping e
  não segue redirecionamentos. Falhas são exibidas sem mudar o ambiente ativo.
- SharedPreferences mantém seleção, endereço, porta, identidade estável e, por
  servidor, nome, revisão e resultado da última conexão. A tela principal mostra
  esses dados e o estado observado do serviço/permissões, sem prometer conexão
  contínua a partir de um teste passado.
- O receiver captura um destino imutável por evento. A ponte HTTP local antiga
  deixou de ser disparada. Os campos legados e a string JSON `additionalData`
  permanecem; `sourceType=android` e `sourceId=deviceId` são aditivos.
- Sincronização ao abrir/retornar e o botão de atualizar enviam cadastro sem revisão,
  adotando o nome canônico. Edição explícita usa a revisão conhecida ao abrir a
  configuração. Revisão desatualizada adota a resposta, sem repetir a edição.
  Tokens por solicitação impedem que respostas antigas substituam edições recentes.
  Um nome salvo offline fica identificado como não confirmado; não há reenvio
  automático de edição com revisão nova. Ao sincronizar, prevalece o cadastro atual.

## Cobertura e limites

### Retomada em 20/10/2026

- Revisados configuração, persistência, contrato HTTP e integração do receiver.
  `IncomingCallTracker.java` e seus 10 testes permanecem iguais a `a777111`.
- Removidos os campos BuildConfig e o override `binaApiUrl` sem uso; o destino
  padrão continua definido em `EnvironmentConfig` e protegido por teste JVM.
- Executados `.\gradlew.bat test` e `.\gradlew.bat assembleDebug`. Uma execução
  de `assembleDebug` passou (33 tarefas, 11 executadas), antes da limpeza do
  Gradle. As tentativas do estado final foram bloqueadas por permissões do cache
  global; usando `GRADLE_USER_HOME` e `ANDROID_USER_HOME` temporários, o bloqueio
  passou a ser SDK indisponível no caminho de `local.properties`.
- Contagem atual: **0 testes executados nesta retomada**, 18 cenários JVM
  disponíveis (10 existentes + 8 novos), 4 instrumentados não executados.
  Relatórios antigos não foram usados como aprovação. Não há red/green
  executável nem build confirmado do estado final; validação continua pendente
  em ambiente com SDK e cache acessíveis.
- Mudanças anteriores de IDE, APK de release e `gradle.properties` ficam fora
  do commit do issue.
- O commit solicitado não pôde ser criado: `git add` retornou `Unable to create
  .git/index.lock: Permission denied`. Esta sessão permite somente leitura em
  `.git`; os arquivos permanecem no working tree da branch `fix/issue-3`.

### Registro da execução interrompida

Foram escritos 8 testes JVM novos (18 cenários únicos com os 10 existentes) e
4 testes instrumentados. As tentativas de execução da suíte Android pararam antes
dos testes por SDK indisponível. A alternativa JVM em projeto temporário também
parou na compilação por `AccessDeniedException` em dependências do cache (Okio e
anotações). Portanto não há contagem de testes JUnit aprovados nesta sessão;
os ciclos red/green ficam sem confirmação executável. Isso precisa ser resolvido
antes de considerar os critérios de validação completos.

- A suíte JVM inclui os 10 cenários existentes de `IncomingCallTrackerTest`, sem
  alterar o ciclo de chamadas do commit `a777111`.
- Novos cenários JVM cobrem validação, destinos capturados, envelope legado por
  HTTP local, teste sem eventos, erros HTTP/redirecionamentos, servidor indisponível,
  nome, revisão e adoção do nome canônico.
- Quatro testes instrumentados cobrem SharedPreferences reais, identidade,
  isolamento entre servidores, resposta antiga e cadastro não confirmado. Usam o
  armazenamento do APK de teste e não limpam preferências do usuário.
- `gradlew.bat test`, `assembleDebug` e `connectedAndroidTest` devem ser executados
  com SDK Android acessível. Nesta sessão o SDK de `local.properties` não está
  disponível; resultados antigos de `app/build/` não contam como execução atual.
- A tentativa TCP ao servidor de desenvolvimento em `127.0.0.1:8080` falhou.
  Os testes HTTP usam servidores efêmeros de contrato, não validam MariaDB nem
  substituem integração com o servidor completo.
- Não há evidência nova de tela nesta sessão: SDK/ADB indisponíveis. Não usar
  imagens antigas do workspace como evidência desta implementação.

## Roteiro pendente em aparelho/emulador

1. Iniciar servidor de desenvolvimento com cadastro de origens e banco dedicado.
2. Abrir configuração: testar host/porta inválidos e servidor desligado; confirmar
   erro compreensível. Testar endereço válido sem salvar; ambiente ativo não muda.
3. Selecionar local, informar nome e salvar. Capturar tela principal e configuração,
   conferir cadastro no servidor, fechar/reabrir e confirmar persistência.
4. Renomear no servidor, atualizar nome no app e conferir adoção. Abrir edição no
   app, renomear novamente no servidor e salvar a edição antiga: nome atual do
   servidor deve prevalecer; somente nova edição explícita pode alterá-lo.
5. Usar dois destinos locais isolados para simular a alternância sem tocar produção.
   Gerar recebida/atendida/encerrada e recebida/não atendida; conferir destino único,
   payload legado e ausência de requisições à ponte antiga. Repetir após reabrir.
6. Conferir permissões negadas, serviço ativo/inativo, tela pequena, teclado e rotação;
   registrar screenshots. A matriz de telefonia/segundo plano continua exigindo
   aparelhos reais. Fila durável e recuperação de pendências não fazem parte deste issue.

## Verificação direta executada

Um smoke compilado com javac e executado com java passou: 24 verificações de
produção/local, host/porta inválidos e ciclo recebida-atendida-encerrada, incluindo
duplicata e duração. Não substitui os testes JUnit nem o build Android.
