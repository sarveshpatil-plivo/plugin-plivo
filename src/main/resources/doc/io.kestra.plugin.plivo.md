# How to use the Plivo plugin

The Plivo plugin connects Kestra workflows to the Plivo Messages and Voice APIs. Use it to send SMS, trigger flows on inbound SMS, and place outbound voice calls.

## Authentication

Every task and trigger authenticates with HTTP Basic auth using your Plivo Auth ID and Auth Token. Find both on the [Plivo console](https://cx.plivo.com/?utm_source=github&utm_medium=oss&utm_campaign=plugin-plivo). Set `authId` and `authToken` on each task; store both as [secrets](https://kestra.io/docs/concepts/secret) and apply them globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks and triggers

`message.Send` sends an SMS through the Plivo Messages API. Set `from` (a Plivo number, short code, or alphanumeric sender ID), `to` (E.164; join multiple recipients with `<`), and `body`. It returns the `messageUuid` list and `apiId`.

`message.Trigger` polls the Plivo Messages API on `interval` and starts an execution whenever inbound messages received within the last polling window are found. The fetched message objects are available as `trigger.messages`.

`call.MakeCall` places an outbound voice call through the Plivo Voice API. Set `from`, `to`, and `answerUrl` — the `answerUrl` must return Plivo XML (for example a `<Speak>` element) that controls the call once answered. It returns the `requestUuid` and `apiId`.
