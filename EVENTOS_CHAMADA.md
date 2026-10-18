# Monitoramento Android de chamadas recebidas

Esta alteração registra o ciclo observado de chamadas recebidas e mantém um serviço em primeiro plano com notificação. Não conclui as tarefas de evolução publicadas no GitHub.

## Eventos

- `CALL_RECEIVED`: primeiro toque observado na linha.
- `CALL_ANSWERED_INCOMING`: primeira transição de uma chamada recebida para OFFHOOK.
- `CALL_MISSED`: linha volta a IDLE sem atendimento observado.
- `CALL_ENDED`: término depois de atendimento observado; duração em segundos desde o primeiro OFFHOOK, usando relógio monotônico.
- `TEST_CONNECTION`: evento de teste do botão existente; não confirma a disponibilidade do portal nem implementa o novo fluxo de configuração.

Transmissões repetidas não geram novo evento nem reiniciam o tempo. O estado é separado por assinatura/SIM, quando o sistema fornece esse identificador. Um IDLE de outra assinatura não encerra as demais. Chamadas realizadas não recebem um novo fluxo nesta alteração.

## Contrato legado

O POST mantém `description`, `deviceId`, `eventType` e `additionalData`. O campo `additionalData` é uma **string contendo JSON**, não um objeto JSON externo. Dentro dessa string são enviados `numero`, `data`, `receivingNumber`, `batteryLevel`, `location`, `duration` (número em segundos) e `durationFormatted`.

Exemplo do formato externo, abreviado:

```json
{
  "description": "Chamada encerrada - Duração: 00:12",
  "deviceId": "identificador-do-aparelho",
  "eventType": "CALL_ENDED",
  "additionalData": "{\"numero\":\"1130047717\",\"data\":\"17/10/2026 15:00:00\",\"receivingNumber\":\"11999990000\",\"duration\":12,\"durationFormatted\":\"00:12\"}"
}
```

Número oculto é representado por string vazia; não dispara a ponte local. A disponibilidade do telefone receptor, localização e estado depende das permissões e dos dados fornecidos pelo aparelho. Bateria e localização preservam o comportamento preexistente.

## Serviço e permissões

- `READ_PHONE_STATE` e `READ_CALL_LOG` são necessários para iniciar o serviço e processar os eventos.
- `FOREGROUND_SERVICE` permite o serviço com notificação; `POST_NOTIFICATIONS` é solicitado a partir do Android 13.
- O serviço começa após a concessão das permissões de telefone e tenta reiniciar no boot. Não é exportado para outros aplicativos.
- Existe somente um receptor de telefonia, declarado no manifesto. O serviço não registra outro receptor.
- Localização é opcional; sua negativa não impede o início do monitoramento telefônico.

## Endpoint de desenvolvimento

O debug aceita a propriedade Gradle `binaApiUrl` para definir o endpoint completo de eventos, por exemplo:

```powershell
.\gradlew.bat assembleDebug -PbinaApiUrl=http://192.168.1.10:8080/api/eventos
```

Sem a propriedade, debug e release usam o endpoint de produção existente. Release sempre usa produção. Essa opção de build não substitui a seleção de ambiente na interface prevista nas tarefas futuras.

A ponte local existente continua usando `%SAVED_IP%` e `%PHONE_NUMBER%`, caso já esteja configurada. Não há novo fluxo de configuração nem exclusividade de destino nesta alteração.

## Limitações e validação

- O estado fica em memória e o envio é assíncrono, sem fila durável nem reenvio garantido. Morte do processo pode perder estado ou eventos; a entrega durável permanece nas tarefas futuras.
- O broadcast resume o estado da linha; não identifica individualmente ligações simultâneas na mesma assinatura, como chamada em espera. Sem identificador de assinatura, existe somente uma linha de estado desconhecida. Não se presume a identidade de outra assinatura para completar eventos sem identificador.
- O início e a duração representam transições observadas, não leitura exata do histórico da operadora.
- Segundo plano, boot, permissões, número oculto e dual SIM ainda exigem validação em aparelhos. Não se garante recuperação após forçar parada; o usuário pode precisar reabrir o aplicativo.
- Esta alteração mantém targetSdk 33. Atualização de SDK alvo e adequação aos tipos/restrições atuais de serviços fazem parte da modernização futura.
- Os testes JVM cobrem recebida/atendida/encerrada, não atendida, transmissões repetidas, duas assinaturas, número oculto e chamadas sucessivas. Build e testes não substituem validação de telefonia em aparelho.

Referências: [broadcast de telefonia](https://developer.android.com/reference/android/telephony/TelephonyManager#ACTION_PHONE_STATE_CHANGED) e [início de serviço em primeiro plano](https://developer.android.com/develop/background-work/services/fgs/launch).
