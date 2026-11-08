# Necessidades de API do servidor (bina-cloud-server)

Evoluções que o Android já consome ou consumirá. Nenhum endpoint existente muda.
O client foi construído contra estes contratos de forma degradável: enquanto o
servidor não os suportar, nada quebra e os dados ficam guardados no aparelho.

## 1. `POST /api/localizacoes` — receber pontos de localização (prioridade: ALTA)

Issue: fgmj/bina-cloud-server#14 — "Receber localização periódica dos aparelhos Android".
Client: implementado em `LocationUpload` (contrato, testado em `LocationUploadTest`),
`LocationQueue` (fila offline) e `LocationUploadWorker` (envio).

### Requisição

```json
{
  "sourceType": "android",
  "sourceId": "id-persistente-do-aparelho",
  "versionCode": 17,
  "versionName": "1.3.3",
  "points": [
    {
      "pointId": "0b6f2c4e-6f0a-4a39-9d55-3d2f4f5e9a10",
      "latitude": -15.793889,
      "longitude": -47.882778,
      "accuracy": 25.0,
      "provider": "fused",
      "recordedAt": "2026-11-07T14:05:00.000Z",
      "batteryLevel": 80
    }
  ]
}
```

- `sourceId`/`sourceType`: os mesmos de `/api/origens` e `/api/eventos`.
- `points`: 1 a 100 itens, ordenados do mais antigo ao mais novo.
- `pointId`: UUID gerado no aparelho. **Chave de idempotência**: o mesmo ponto pode
  chegar mais de uma vez (resposta perdida, reenvio); o servidor deve ignorar repetidos.
- `recordedAt`: horário do fix (UTC, ISO-8601 com milissegundos). Pode ser de dias
  atrás quando o aparelho ficou offline (fila guarda até 7 dias).
- `accuracy` (metros) e `batteryLevel` (0–100) são opcionais: omitidos quando desconhecidos.
- `provider`: `fused`, `network` ou `gps` (texto livre, até 32 caracteres); opcional, omitido quando o sistema não informa.

### Respostas e como o client reage

| Resposta | Client |
|---|---|
| 2xx (sugestão: `202 {"accepted": N}`) | remove o lote da fila |
| 404, 405, 501 | entende "servidor sem suporte": mantém os pontos, pausa 6 h, mostra "Aguardando suporte do servidor" |
| 400, 413, 422 | descarta o lote (dado inválido não pode travar a fila) |
| 408, 429, 5xx, redirecionamentos, falha de rede | tenta de novo com backoff exponencial |

### Sugestões para o servidor
- Persistir por origem (`sourceIdentity`) com índice por `recordedAt`.
- Retenção configurável (LGPD): sugestão de 90 dias.
- Consulta para o portal: `GET /api/localizacoes?origem=<identity>&desde=<ISO>&ate=<ISO>`
  e "última posição por origem" para um mapa.
- Autenticação: o client hoje não envia credencial (igual a `/api/eventos` e `/api/origens`).
  Como coordenadas são dado pessoal, se o servidor passar a exigir token por aparelho,
  registrar aqui o contrato para o client acompanhar.
- Não usar o nome da origem como chave (mesma regra de `docs/origens.md`).

## 2. Propostas sem dependência no client (prioridade: MÉDIA/BAIXA)

Ainda não implementadas no Android; registradas para avaliação.

- **Heartbeat/saúde do aparelho** (MÉDIA): `POST /api/origens/:sourceId/saude` a cada
  15 min com bateria, rede, versão, estado do monitoramento e tamanho das filas. Permite
  ao portal alertar "aparelho sem sinal há 30 min". Hoje parte disso já pode ser inferida
  da chegada dos pontos de localização (que trazem bateria e versão).
- **Alerta de chamada perdida não retornada** (MÉDIA): somente servidor, a partir de
  `CALL_MISSED` sem ligação posterior do mesmo número.
- **Mapa/trilha por origem no portal** (BAIXA): depende do item 1.
