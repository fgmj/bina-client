# Issue #7 — verificação manual de atualizações

## Implementação

- Botão no diálogo de configurações, integrado pelo `SettingsController` que
  já é usado pela `MainActivity`. Consulta o ambiente **ativo salvo**, com
  explicação para salvar alterações do seletor antes de consultar outro destino.
- Estados Verificando/Baixando desabilitam novos cliques durante a operação.
  Rede roda fora da thread principal; consulta de metadados tem limite de 20 s.
- Mostra versão instalada atual, versão nova com confirmação de instalação,
  ausência de publicação (404/204) ou erro compreensível no próprio diálogo.
- `UpdateClient` compartilha consulta e download com `UpdateWorker`: canal,
  origem, limites, hash e validação de pacote/assinatura continuam comuns.
  Download é serializado com o worker e reutiliza `UpdateApk` e
  `UpdateInstallActivity`, inclusive autorização de fontes desconhecidas.
- `UpdatePolicy` permite repetir oferta somente na consulta manual explícita.
  Download confirmado persiste a maior versão oferecida sem reduzir o histórico.
- Respostas não abrem instalador depois de fechar o diálogo/destruir a Activity;
  troca de ambiente é verificada novamente antes de baixar e instalar.

## Validação nesta sessão

- Lidos `../AGENTS.md`, `docs/agents/`, validações dos issues #3 e #6 e skill TDD.
  Não existe `AGENTS.md` na raiz deste worktree. Sem chamadas gh/GitHub.
- Primeiro teste adicionado na seam `UpdatePolicyTest` antes da implementação;
  tentativa red pelo Gradle bloqueada antes de compilar pelo lock global.
  Portanto o ciclo red/green pelo Gradle não está comprovado.
- 5 testes novos: oferta manual repetida, versão igual/anterior, sem publicação,
  versão inválida e erro de rede. `UpdatePolicyTest`: **9 testes, 0 falhas**, via
  javac/JUnit 4.13.2 isolados, com os fontes reais de EnvironmentConfig/UpdatePolicy.
- Regressões JVM sem dependência HTTP: **39 testes, 0 falhas**, incluindo política,
  ambiente, telefonia, armazenamento/fila durável e estado de monitoramento.
- Tentativa adicional com todas as classes de teste: **47 testes, 6 falhas**.
  As 6 falhas de `ServerApiTest` ocorreram por `NoClassDefFoundError:
  android/util/Log`: o artefato OkHttp 5.1.0 disponível no cache é Android,
  sem runtime Android na execução Java isolada. Não é aprovação da suíte inteira.
  BuildConfig foi materializado apenas nessa execução descartável a partir de
  app/build.gradle (16 / 1.3.2); fontes de produção não foram substituídos.
- Copiado `local.properties` do worktree principal antes do Gradle. Ele aponta
  para `C:/Users/ferna/AppData/Local/Android/Sdk`; esse SDK e o caminho informado
  `C:/ambdes/Android/Sdk` não estão acessíveis nesta sessão.
- Executados `gradlew.bat test` e `gradlew.bat assembleDebug`: ambos bloqueados
  no lock de distribuição Gradle global (acesso negado), antes de compilar.
  Tentativa com Gradle instalado e cache local, offline: plugin
  foojay-resolver-convention 0.8.0 ausente no cache. Não há APK/build confirmado.
- `git diff --check` sem erros. Artefatos da validação ficam em `.gradle/`, ignorado.
- Branch `fix/issue-7`. `git add` dos oito arquivos foi recusado por acesso
  negado ao criar `.git/worktrees/-wt-client-7/index.lock`. Working tree pronta;
  commit previsto: `Adiciona verificação manual de atualizações`. Sem push.

## Validação pendente em aparelho

1. Conferir normal/verificando, endpoint offline/inválido e 404/204 no diálogo.
2. Conferir versão igual/anterior e versão nova com recusa/confirmação.
3. Repetir manualmente versão já oferecida; conferir que o worker não a repete.
4. Confirmar download válido, assinatura/hash divergentes, permissão de fontes
   desconhecidas e instalação; repetir com notificações negadas.
5. Fechar diálogo, girar aparelho e trocar ambiente durante consulta/download;
   nenhuma resposta antiga deve abrir instalador automaticamente.

Risco residual: UI, integração Android e instalação real não foram executadas;
testes JVM isolados não substituem build nem testes instrumentados.
