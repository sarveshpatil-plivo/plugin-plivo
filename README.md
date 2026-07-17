<p align="center">
  <a href="https://www.plivo.com">Plivo</a> plugin for <a href="https://kestra.io">Kestra</a>
</p>

# Plivo Plugin for Kestra

A standalone [Kestra](https://kestra.io) plugin that connects workflows to the [Plivo](https://www.plivo.com) Messages and Voice APIs. It mirrors the structure of the official `kestra-io/plugin-twilio` plugin.

## Features

- **`io.kestra.plugin.plivo.message.Send`** — send an SMS through the Plivo Messages API (`POST /v1/Account/{authId}/Message/`).
- **`io.kestra.plugin.plivo.message.Trigger`** — poll the Plivo Messages API and start a flow on new inbound SMS.
- **`io.kestra.plugin.plivo.call.MakeCall`** — place an outbound voice call through the Plivo Voice API (`POST /v1/Account/{authId}/Call/`); the answer URL returns Plivo `<Speak>` XML.

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

### Trigger a flow on inbound SMS

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
