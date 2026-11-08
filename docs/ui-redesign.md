# Redesign de UI/UX do Bina (branch `fix/ui-redesign`)

Documento de decisões. O usuário não estava disponível: as perguntas abaixo foram
feitas e respondidas pelo agente seguindo a skill **grilling** (árvore de decisões em
rodadas; cada rodada só contém perguntas cujos pré-requisitos já foram decididos).

## Restrições que guiaram tudo

- Mexer só na apresentação. Ciclo de eventos e fila durável (`CallReceiver`,
  `CallMonitorService`, `IncomingCallTracker`, `CallOutbox`, `CallDeliveryJob`,
  `DurableCalls`), updater (`UpdatePolicy`, `UpdateWorker`, `UpdateApk`,
  `UpdateInstallActivity`, `UpdateClient`), ambiente/nome (`EnvironmentConfig`,
  `AppSettings`, `SettingsController`, `ServerApi`) e o botão *Verificar atualizações*
  não podem regredir.
- Endpoints existentes não mudam. targetSdk 33, sem Compose, `gradlew test` verde.

## Rodada 1 — fundamentos

❓ **Q1 — Quem usa e como?** Atendentes não técnicos, o dia inteiro, com o celular
apoiado na mesa ao lado do PC. Olham de relance.
➡️ A tela principal deve ser legível a ~1 m: um único elemento dominante (a
chamada atual), estados com **ícone + texto + cor** (nunca só cor), texto base 16sp.

❓ **Q2 — Qual o elemento dominante da tela principal?** Hoje é um log técnico cinza.
➡️ Um **cartão de chamada** grande no topo: "Aguardando chamadas" quando ocioso;
número formatado em 40sp com fundo âmbar quando está chamando; verde quando em
atendimento; neutro com horário quando encerrada ("Última chamada"); vermelho com
horário e botão **Retornar** quando perdida (revisado na code-review: chamada perdida
exige ação, então ganha o tom de problema — sempre com ícone e texto, nunca só cor).

❓ **Q3 — Material 2 ou Material 3?** O projeto já depende de
`com.google.android.material:1.12.0`, que traz `Theme.Material3.*`.
➡️ Material 3 (`Theme.Material3.DayNight.NoActionBar` + `MaterialToolbar`). Sem nova
dependência, sem Compose.

❓ **Q4 — Dark mode: seguir o sistema ou ter chave no app?**
➡️ Seguir o sistema (DayNight). Uma chave a mais é configuração que o público não
procura; aparelhos de balcão costumam ficar no padrão claro.

❓ **Q5 — Configurações: Activity nova ou continuar diálogo?** A lógica de
salvar/testar/registrar/verificar atualizações vive em `SettingsController` e está
coberta por validações das issues #3 e #7.
➡️ **Manter o diálogo** (mesmos IDs e handlers), reestilizado com
`MaterialAlertDialogBuilder`, seções com título, `TextInputLayout`, cartões de
ambiente e botão tonal de atualizações. Migrar para Activity fica como pendência
— trocar o contêiner sem mexer na lógica exige uma refatoração própria.

❓ **Q6 — O log técnico some?** Ele é útil para suporte.
➡️ Sai da tela principal e vai para **Menu › Registro técnico** (diálogo com a mesma
lista, persistida no mesmo `LogPrefs`). A tela principal mostra **Últimas chamadas**
em linguagem humana.

❓ **Q7 — Nome do app no ícone: "Cloud bina"?**
➡️ "Bina". Curto, é como os usuários chamam. Não afeta pacote nem updater.

## Rodada 2 — dados e estados (dependem de Q2/Q6)

❓ **Q8 — De onde vem o número chamando, se `DurableCalls.record` só devolve a
descrição?**
➡️ Novo modelo puro `CallFeed` (JVM-testável) alimentado por `CallReceiver` **depois**
que `record` devolve um evento não nulo, numa chamada adicional isolada em
`try/catch` própria. Não altera o que é gravado nem entregue. O feed guarda a
chamada atual e as últimas 20, em `SharedPreferences` próprias (`call-feed`).
Fase derivada só para exibição: RINGING→"Chamando", OFFHOOK→"Em atendimento",
IDLE após atendimento→"Encerrada" (exibida como "Última chamada" no cartão e
"Atendida" na lista), IDLE sem atendimento→"Perdida".

❓ **Q9 — Como mostrar serviço, conexão e fila sem texto corrido?**
➡️ Três **cartões de status** lado a lado (Monitoramento, Servidor, Fila), cada um
com ícone, rótulo curto e tom (OK / Atenção / Problema / Neutro). Toque (≥48dp) abre
o detalhe que já existia: `MonitoringRecovery.show`, diálogo da fila (agora com
"Enviar agora"; o diálogo do Servidor tem "Testar conexão" e "Configurar"). A classificação fica em classes puras testadas:
`MonitoringState.level`, `ConnectionStatus.classify`, `QueueStatus`.

❓ **Q10 — E quando falta permissão ou o serviço parou?**
➡️ Um **banner de ação** vermelho/âmbar acima do cartão de chamada com um botão
"Resolver" — o estado que exige ação nunca fica escondido em um detalhe.

❓ **Q11 — Notificação do serviço?** Canal `call_monitor`, `IMPORTANCE_LOW`,
`START_STICKY` e ID 1 são comportamento do serviço.
➡️ Manter canal/importância/ID; mudar só a apresentação: ícone monocromático novo
(o atual é colorido e vira um borrão na barra), cor de destaque, título "Bina ativo",
texto com nome do aparelho e ambiente, `CATEGORY_SERVICE`, `BigTextStyle`.

❓ **Q12 — Frequência de atualização da tela?**
➡️ Mantida em 1,5 s (já existia); o broadcast `NEW_LOG` também força atualização
imediata do cartão de chamada.

## Rodada 3 — identidade (depende de Q3/Q4/Q9)

❓ **Q13 — Paleta?** O azul genérico atual (#1976D2) não diferencia estados.
➡️ Identidade **"Sinal"**: primária petróleo (associação com operação/logística,
calma para uso prolongado) e âmbar como cor de "chamando" (urgência sem alarme).
Verde = ok, vermelho = problema. Contrastes verificados (WCAG 2.1):

| Par (texto/fundo)            | Claro  | Escuro |
|------------------------------|--------|--------|
| onPrimary / primary          | 6,47   | 7,60   |
| onSurface / surface          | 16,24  | 14,42  |
| onSurfaceVariant / surface   | 8,82   | 10,88  |
| Chamando (âmbar)             | 9,90   | 7,89   |
| OK (verde)                   | 13,41  | 7,33   |
| Atenção                      | 13,24  | 7,23   |
| Problema                     | 13,26  | 7,24   |

Tudo ≥ 4,5 (AA); quase tudo ≥ 7 (AAA).

❓ **Q14 — Tipografia?**
➡️ Roboto do sistema (sem fonte baixada: funciona offline e não aumenta o APK).
Escala: número 40sp negrito com dígitos tabulares (`tnum`), títulos 22sp, corpo
16sp, legendas 14sp. Tudo em `sp` para respeitar o tamanho de fonte do aparelho.

❓ **Q15 — Ícones?**
➡️ Vetores Material Symbols (arredondados) desenhados em XML: telefone tocando,
chamada recebida, perdida, encerrada, nuvem ok/falha, fila, escudo do serviço,
localização, engrenagem. Ícone do launcher novo: monofone com ondas de sinal,
branco sobre petróleo, com camada monocromática para Android 13.

❓ **Q16 — Acessibilidade além do contraste?**
➡️ Alvos ≥48dp; `contentDescription` em ícones informativos e `importantForAccessibility=no`
nos decorativos; cartões de status anunciam "Monitoramento: ativo" (rótulo + valor);
cartão de chamada com `accessibilityLiveRegion=assertive` só quando está chamando;
número lido dígito a dígito via `TtsSpan`-equivalente (contentDescription espaçado).

## Rodada 4 — Missão 2: novas funcionalidades

❓ **Q17 — Rastreamento a cada 5 min: ligado por padrão?**
➡️ **Não. Opt-in** em Configurações › Localização. Localização contínua de funcionário
é dado pessoal (LGPD): exige ciência explícita. O texto explica para que serve.

❓ **Q18 — Mecanismo?** WorkManager tem período mínimo de 15 min; o app não usa Play
Services.
➡️ `LocationManager.requestLocationUpdates(provider, 5 min, 0 m, PendingIntent)` →
`LocationReceiver`. Provedor: `fused` (API 31+), senão `network`, senão `gps`.
Como o `CallMonitorService` já mantém o processo em primeiro plano, as atualizações
não sofrem o limite de "poucas por hora" de apps em segundo plano. Com
`ACCESS_BACKGROUND_LOCATION` concedida, também funcionam com a tela desligada.
Reboot/atualização: `BootReceiver` e a abertura do app reativam o pedido.
Não muda o tipo do serviço (`specialUse`) — evita mexer no serviço protegido.

❓ **Q19 — Fluxo de permissão em Android 10+?**
➡️ Duas etapas: (1) pede fina/aproximada com explicação; (2) explica "Permitir o
tempo todo" e pede `ACCESS_BACKGROUND_LOCATION` (no Android 11+ o sistema leva à
tela de configurações). Sem a etapa 2 o rastreio funciona com o app aberto e o
cartão mostra "Localização: só com o app aberto".

❓ **Q20 — Bateria?**
➡️ Precisão balanceada (sem forçar GPS), intervalo mínimo 5 min, descarte de pontos
com menos de 4,5 min do anterior, envio em lote (até 100 pontos) somente com rede,
via WorkManager. Nada de wakelock próprio.

❓ **Q21 — Offline?**
➡️ Fila durável `LocationQueue` em `noBackupFilesDir` (JSON, gravação atômica igual
à fila de chamadas), limite de 2 016 pontos (7 dias a cada 5 min); ao estourar,
descarta os mais antigos. Cada ponto tem `pointId` UUID → reenvio idempotente.

❓ **Q22 — E o servidor, que ainda não tem endpoint?**
➡️ Contrato proposto `POST /api/localizacoes` (ver `docs/api-needs.md`, issue no
server). Degradação: 404/405/501 → "servidor sem suporte", pontos mantidos, nova
tentativa em 6 h; a UI mostra "Aguardando suporte do servidor". 5xx/rede → retry
com backoff do WorkManager. 400/413/422 → lote descartado (dado inválido não pode
travar a fila). Nenhum endpoint existente muda.

❓ **Q23 — Outras funcionalidades de alto valor?**
➡️ Implementadas no client: **retornar chamada** (toque na chamada recente abre o
discador com o número — sem permissão nova, `ACTION_DIAL`); **copiar número**
(toque longo); **"Enviar agora"** na fila. Propostas para o server (issues):
heartbeat/saúde do aparelho (aparelho offline, bateria, versão), alerta de
chamada perdida não retornada, mapa/trilha por origem.

## Telas: antes × depois

### Monitor principal
**Antes:** ActionBar azul "Cloud bina"; quatro linhas de texto (ambiente, aparelho,
conexão + fila, monitoramento); três botões empilhados; log cinza ocupando a tela;
botão "Testar conexão" azul-claro com texto branco (contraste 2,6:1); versão.

**Depois:**
1. Toolbar petróleo "Bina" + subtítulo "Recepção · Produção"; ações: engrenagem
   (Configurações) e menu (Atualizar nome, Registro técnico, Monitoramento e
   recuperação).
2. Banner de ação (só quando necessário) com "Resolver".
3. Cartão de chamada (hero) com estados Aguardando / Chamando / Em atendimento /
   Encerrada / Perdida.
4. Três cartões de status: Monitoramento, Servidor, Fila (+ linha "Localização"
   quando o rastreio estiver ligado).
5. "Últimas chamadas": ícone por tipo, número formatado, horário, toque = retornar.
6. Rodapé com versão.

### Configurações (diálogo)
**Antes:** RadioButtons crus, EditTexts sem moldura, botão de atualizações solto.
**Depois:** seções "Servidor", "Este aparelho", "Localização", "Atualizações";
opções de ambiente (rádios Material com descrição no rótulo); campos com `TextInputLayout` e textos de ajuda;
endereço/porta escondidos quando "Produção"; botão tonal "Verificar atualizações"
com o mesmo comportamento; feedbacks com live region.

### Notificação
**Antes:** "Monitoramento de chamadas ativo / Toque para abrir o Bina", ícone
colorido. **Depois:** "Bina ativo — monitorando chamadas", "Recepção · Produção",
ícone monocromático, cor petróleo, categoria de serviço.

## Seams de teste (TDD)

Testes JVM em `app/src/test`, sem Robolectric:
- `MonitoringState` (existente): ganhou `summary()` (tom + rótulo curto), coberto em `StatusSummaryTest`.
- `ConnectionStatus`: texto de conexão persistido → tom/rótulo.
- `QueueStatus`: pendências → tom/rótulo.
- `CallFeed`: transições → chamada atual e recentes; serialização.
- `PhoneFormat`: formatação de números brasileiros para exibição.
- `LocationQueue`: limite, intervalo mínimo, lote, confirmação, persistência.
- `LocationUpload.classify`/`payload`: código HTTP → enviado / tentar de novo / sem suporte / descartar.

## Code review (skill code-review, eixos Standards e Spec)

Corrigido antes do commit final:
- `CallReceiver`: o feed de exibição passou a ser gravado **depois** de
  `CallOutbox.schedule` — a entrega não espera mais o `commit()` do feed. O arquivo
  agora é igual ao `main` mais uma linha no fim.
- `LocationQueue`: se a gravação em disco falhar, memória volta ao estado anterior
  (ponto não salvo não é enviado nem conta para o intervalo; confirmação não salva
  mantém o ponto). Teste `failedWriteLeavesMemoryMatchingDisk`.
- `LocationUploadWorker`: 400/413/422 também marcam "servidor com suporte" (o endpoint
  existe); `RuntimeException` (ex.: endereço local inválido) vira `failure()` em vez de
  retry infinito — o trabalho periódico de 15 min tenta de novo depois.
- `LocationUpload.payload`: `provider` omitido quando vazio e cortado em 32 caracteres.
- Android 11+: a tela de configurações do app só é aberta quando o sistema deixou de
  perguntar (`shouldShowRequestPermissionRationale == false`); um "agora não" é respeitado.
- Legendas dos cartões de status: 13sp → 14sp (Q14).

Avaliado e mantido (decisão registrada):
- "Escopo só apresentação" apontado para o rastreamento: o rastreamento é a Missão 2,
  pedida explicitamente; ele não toca fila de chamadas, entrega nem updater.
- A chave **Localização** aplica na hora (como uma chave do sistema), independente de
  Salvar/Cancelar do diálogo: o pedido de permissão precisa acontecer no gesto.
- "Enviar agora" chama `CallOutbox.schedule(…, 0)`, a mesma API que o `CallReceiver`
  usa a cada evento — sem comportamento novo na fila.
- Leituras pequenas na thread principal em `render()` (feed em `SharedPreferences`,
  fila de localização carregada uma vez): custo baixo; ver pendências.
- `ConnectionStatus.classify` lê o texto de conexão persistido por prefixo: é frágil
  a mudanças de mensagem no `SettingsController`, mas evita mexer na classe protegida.
  Testes em `StatusSummaryTest` travam os textos atuais.
- Envio de localização sem cabeçalho de autenticação: igual aos endpoints atuais
  (`/api/eventos`, `/api/origens`); registrado em `docs/api-needs.md` para o servidor.

## Pendências

- **Configurações em Activity própria** no lugar do diálogo — issue fgmj/bina-client#8.
- **Ícones legados do launcher** (PNG `mipmap-*dpi`, usados abaixo do API 26) na nova
  identidade — issue fgmj/bina-client#9.
- **Servidor**: `POST /api/localizacoes` — issue fgmj/bina-cloud-server#14. Até lá o
  client mostra "Aguardando suporte do servidor" e guarda até 7 dias de pontos.
- Mover para fora da thread principal as leituras de `render()` (feed e fila de
  localização) se aparecer jank em aparelhos fracos.
- Trocar o `ConnectionStatus` por um estado estruturado quando `SettingsController`
  puder ser refatorado.
- Rastreamento desligado com pontos ainda guardados e servidor sem suporte: os pontos
  só voltam a ser enviados quando o rastreio for religado ou o app enfileirar envio.
- Validação manual em aparelho (não feita neste ambiente): fluxo de permissão em
  Android 10, 11 e 13; modo escuro; TalkBack no cartão de chamada; notificação.
- Capturas de tela para o PR (precisam de aparelho/emulador).
