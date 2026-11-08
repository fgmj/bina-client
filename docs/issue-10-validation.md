# Issue #10 — contraste no modo claro

## Escopo e diagnóstico

Auditoria estática dos cinco layouts (`activity_main`, `dialog_settings`,
`item_status_card`, `item_recent_call`, `log_item`), cores diurnas/noturnas,
temas, estilos inline e atribuições de cor em `MonitorScreen`, `ToneColors`,
`RecentCallsAdapter`, diálogos de configurações/recuperação/atualização e
notificações. Preservada a identidade Sinal de `ui-redesign.md`.

O overlay `ThemeOverlay.Bina.Toolbar`, baseado em Dark.ActionBar, define
`colorOnSurface` e `colorOnSurfaceVariant` brancos. Sem `popupTheme` separado,
o menu usa esse contexto sobre uma superfície clara. Não é um problema dos
pares de cores dos cartões, que já eram adequados.

## Pares corrigidos (tela → antes → depois)

- **Monitor → menu da toolbar** (Atualizar nome, Registro técnico,
  Monitoramento e recuperação, demais ações): branco `#FFFFFF` herdado do
  overlay sobre superfície clara (referência `#E9EFEF`: **1,16:1**) →
  `onSurface #161D1D` sobre `surface #E9EFEF`: **14,71:1**.
  Um popupTheme claro próprio redefine os papéis e as cores de texto Android;
  a toolbar continua branca sobre petróleo (**6,47:1**).
- **Monitor → textos secundários/ícones do mesmo menu**: papel
  `onSurfaceVariant` branco sobre cinza claro (**1,16:1**) →
  `#3F4949` sobre `#E9EFEF`: **7,99:1**. Inclui os papéis herdados,
  mesmo quando a versão atual do menu não exibe texto secundário.
- **Configurações → Atualizações → “Verificando…”/download em andamento**:
  botão temporariamente desabilitado usava seletores Material com texto
  `onSurface` a 38% e fundo a 12%. Sobre a superfície do diálogo `#E9EFEF`,
  composição aproximada `#899090 / #D0D6D6`: **2,21:1** →
  `onSecondaryContainer #051F21 / secondaryContainer #CCE8E9`: **13,30:1**.
  O botão continua desabilitado funcionalmente; o feedback e o rótulo de
  progresso continuam informando o estado. Ativado, mantém o par tonal.

As razões do menu antes da correção exemplificam a superfície cinza da
paleta; a cor efetiva herdada do popup pode variar com os recursos da
biblioteca/plataforma. As cores após a correção são explícitas.

## Demais telas e estados conferidos

Razões calculadas por luminância relativa sRGB WCAG:
`(LmaisClaro + 0,05) / (LmaisEscuro + 0,05)`, com composição alpha antes
do cálculo quando aplicável. Todos os pares abaixo passam inclusive o
limite de texto normal (4,5:1); números grandes também passam 3:1.

- Monitor, recentes (vazios ou preenchidos), versão, formulários e diálogos:
  `onSurface/background` **16,24:1**; sobre cartões **14,71:1**.
- Legendas, horários, log técnico, ajuda, feedback de conexão/erro/atualização:
  `onSurfaceVariant/background` **8,82:1**; sobre diálogo/cartão **7,99:1**.
- Títulos de seção, botões de texto/contorno e ações dos diálogos:
  `primary/background` **6,14:1**; sobre superfície de diálogo **5,56:1**.
- Campos endereço, porta e nome: entrada `onSurface`, rótulo/ajuda
  `onSurfaceVariant`, foco `primary`; erro `error #BA1A1A` sobre superfície
  de diálogo **5,56:1**. Rádios Produção/Local e switch usam texto de superfície.
- Aguardando, encerrada e fase desconhecida: `#161D1D/#E9EFEF` **14,71:1**.
- Chamando: `#2B1700/#FFB74D` **9,90:1**; em atendimento/OK:
  `#00210E/#B7F1C6` **13,41:1**; perdida/problema:
  `#410002/#FFDAD6` **13,26:1**.
- Monitoramento, servidor, fila e localização em todos os tons:
  OK **13,41:1**, neutro **13,24:1**, atenção **13,24:1**, problema **13,26:1**.
  `ToneColors` sempre seleciona fundo e texto do mesmo tom.
- Banner de permissões ausentes: problema **13,26:1**; serviço parado ou
  aparelho sem nome: atenção **13,24:1**, incluindo “Resolver”.
- “Retornar”: `onPrimary/primary` **6,47:1**, inclusive nos cartões coloridos.
- Notificações de monitoramento e atualização usam templates do Android,
  sem RemoteViews, cor de texto própria ou `setColorized(true)`. O sistema
  controla texto/fundo; `bina_brand` é apenas destaque. Nenhuma alteração.
- Recuperação, servidor, fila, nome, permissões e erros de instalação usam
  diálogos padrão do tema; não há texto branco fixo nessas superfícies.

## Isolamento e validação

- Correções efetivas somente em `values-notnight/styles.xml`. No escuro,
  `Widget.Bina.Toolbar` herda os mesmos itens originais de sua nova base;
  `Widget.Bina.CheckUpdates` é um alias do estilo tonal anterior.
  `values-night/colors.xml`, Activities, Services e todo Java inalterados.
- Sem lógica nova de seleção de cor/estado: correção exclusivamente em XML,
  dispensando novos testes JVM conforme o issue. Testes existentes preservados.
- `local.properties` copiado do worktree principal antes do Gradle e ignorado
  pelo Git. O caminho original não estava acessível; ajustado localmente para
  `C:/Android/Sdk`. O SDK disponível tem Build Tools 35.0.1, enquanto o padrão
  do plugin pede 35.0.0: um init script temporário selecionou 35.0.1 sem alterar
  o projeto. Cache Gradle e chave debug também foram copiados para caminhos
  graváveis por causa das restrições de acesso do ambiente.
- Gradle: **BUILD SUCCESSFUL** (`test assembleDebug --offline --console=plain
  -I %TEMP%/bina-issue10.init.gradle`, em 1m23s). Init script local selecionou
  Build Tools 35.0.1 e a cópia da chave debug; não integra o commit. Execução
  com JDK 17.0.12, `GRADLE_USER_HOME=.gradle/issue10-home` e
  `ANDROID_USER_HOME=%TEMP%/bina-issue10-android`.
- Testes: **82 debug + 82 release = 164 execuções**, **0 falhas, 0 erros,
  0 ignorados**. São os mesmos 82 casos executados nas duas variantes.
- APK gerado: `app/build/outputs/apk/debug/app-debug.apk`.
- `git diff --check` sem problemas; todos os XML bem formados e compilação
  dos recursos Android aprovada. Comparação dos itens herdados dos estilos
  noturnos com HEAD confirmou equivalência.
- Limitação: auditoria estática, sem capturas ou inspeção em aparelho/emulador.
  Templates de notificação e variações de fabricante precisam de conferência
  visual em dispositivo; não se declara validação visual realizada.
- Commit solicitado na branch já existente `fix/issue-10`: `git add` negado
  ao criar `bina-client/.git/worktrees/-wt-client-10/index.lock` (Permission
  denied). Alterações deixadas no working tree, sem commit e sem push.
