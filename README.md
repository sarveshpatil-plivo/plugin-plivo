<p align="center">
  <a href="https://www.plivo.com">Plivo</a> plugin for <a href="https://kestra.io">Kestra</a>
</p>

# Plivo Plugin for Kestra

A standalone [Kestra](https://kestra.io) plugin that connects workflows to the [Plivo](https://www.plivo.com) Messages and Voice APIs. It mirrors the structure of the official `kestra-io/plugin-twilio` plugin.

## Features

- **`io.kestra.plugin.plivo.message.Send`** — send an SMS through the Plivo Messages API (`POST /v1/Account/{authId}/Message/`).
- **`io.kestra.plugin.plivo.security.VerifySignature`** — validate the signature of an inbound Plivo webhook (SMS or call) received by the core Webhook trigger, so a flow only acts on POSTs that genuinely came from Plivo.
- **`io.kestra.plugin.plivo.message.Trigger`** — poll the Plivo Messages API and start a flow on new inbound SMS (metadata only, zero-infra fallback).
- **`io.kestra.plugin.plivo.call.MakeCall`** — place an outbound voice call through the Plivo Voice API (`POST /v1/Account/{authId}/Call/`); the answer URL returns Plivo `<Speak>` XML.

## Receiving inbound SMS and calls

There are two ways to react to inbound Plivo traffic.

**Webhook (recommended, real time, full payload).** Point your Plivo number's Message URL / Answer URL at a Kestra [core Webhook trigger](https://kestra.io/plugins/core/triggers/io.kestra.plugin.core.trigger.webhook). Plivo POSTs the full body — including the SMS `Text` and, for calls, the caller details — the moment a message or call arrives. Validate the request with `VerifySignature` before acting on it. A plugin cannot register its own HTTP endpoint in Kestra (the built-in Webhook trigger owns HTTP ingress; `RealtimeTriggerInterface` is for queue consumers, not HTTP), so the plugin's job here is the security check and the example flows below.

**Polling (`message.Trigger`, zero-infra fallback).** Requires no public endpoint, but the Plivo message-list API does **not** return the message body, so the polling trigger carries metadata only (`from`, `to`, uuid, state, time) — it cannot read the SMS `Text`. Use it only when you cannot expose a webhook.

## Authentication

All tasks use HTTP Basic auth with your Plivo **Auth ID** and **Auth Token**, available on the [Plivo console](https://cx.plivo.com/?utm_source=github&utm_medium=oss&utm_campaign=plugin-plivo). Store them as Kestra [secrets](https://kestra.io/docs/concepts/secret):

```
PLIVO_AUTH_ID=<your auth id>
PLIVO_AUTH_TOKEN=<your auth token>
```

## Examples

### Send an SMS

```yaml
id: send_sms
namespace: company.team

tasks:
  - id: send_sms
    type: io.kestra.plugin.plivo.message.Send
    authId: "{{ secret('PLIVO_AUTH_ID') }}"
    authToken: "{{ secret('PLIVO_AUTH_TOKEN') }}"
    from: "{{ secret('PLIVO_FROM_NUMBER') }}"
    to: "+15555550100"
    body: "Hello from Kestra."
```

### Inbound SMS over a webhook (recommended)

Set your Plivo number's **Message URL** (POST) to the webhook this flow exposes:
`https://<your-kestra-host>/api/v1/<tenant>/executions/webhook/company.team/inbound_sms/<key>`. Plivo delivers the full body, `VerifySignature` confirms it is authentic, then the flow replies with the message text.

```yaml
id: inbound_sms
namespace: company.team

tasks:
  - id: verify
    type: io.kestra.plugin.plivo.security.VerifySignature
    authToken: "{{ secret('PLIVO_AUTH_TOKEN') }}"
    url: "https://your-kestra-host/api/v1/main/executions/webhook/company.team/inbound_sms/plivoKey"
    params: "{{ trigger.body }}"
    signature: "{{ trigger.headers['x-plivo-signature-ma-v3'][0] ?? trigger.headers['x-plivo-signature-v3'][0] }}"
    nonce: "{{ trigger.headers['x-plivo-signature-v3-nonce'][0] }}"

  - id: reply
    type: io.kestra.plugin.plivo.message.Send
    authId: "{{ secret('PLIVO_AUTH_ID') }}"
    authToken: "{{ secret('PLIVO_AUTH_TOKEN') }}"
    from: "{{ trigger.body.To }}"
    to: "{{ trigger.body.From }}"
    body: "You said: {{ trigger.body.Text }}"

triggers:
  - id: webhook
    type: io.kestra.plugin.core.trigger.Webhook
    key: plivoKey
```

### Answer an inbound call over a webhook

Set your Plivo number's **Answer URL** (POST) to this flow's webhook. `wait: true` and `responseContentType: "text/xml"` make the webhook return Plivo XML synchronously, so the call is answered with your spoken message.

```yaml
id: inbound_call
namespace: company.team

tasks:
  - id: answer
    type: io.kestra.plugin.core.debug.Return
    format: "<Response><Speak>Thanks for calling. This call was answered by Kestra and Plivo.</Speak></Response>"

triggers:
  - id: webhook
    type: io.kestra.plugin.core.trigger.Webhook
    key: plivoCallKey
    wait: true
    returnOutputs: true
    responseContentType: "text/xml"
    responseBody: "{{ outputs.answer.value }}"
```

### Trigger a flow on inbound SMS (polling fallback, metadata only)

```yaml
id: on_inbound_sms
namespace: company.team

tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
    message: "{{ trigger.messages }}"

triggers:
  - id: inbound
    type: io.kestra.plugin.plivo.message.Trigger
    authId: "{{ secret('PLIVO_AUTH_ID') }}"
    authToken: "{{ secret('PLIVO_AUTH_TOKEN') }}"
    interval: PT1M
```

### Make a voice call

```yaml
id: make_call
namespace: company.team

tasks:
  - id: call
    type: io.kestra.plugin.plivo.call.MakeCall
    authId: "{{ secret('PLIVO_AUTH_ID') }}"
    authToken: "{{ secret('PLIVO_AUTH_TOKEN') }}"
    from: "{{ secret('PLIVO_FROM_NUMBER') }}"
    to: "+15555550100"
    answerUrl: "https://example.com/answer.xml"
```

The `answerUrl` must return Plivo XML, for example:

```xml
<Response>
  <Speak>Hello from Kestra and Plivo.</Speak>
</Response>
```

## Build

```
./gradlew build
```

## License

Apache License 2.0 — see [LICENSE](LICENSE).
