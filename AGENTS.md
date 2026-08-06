# AGENTS.md — plugin-plivo

Working context for AI agents and contributors touching this repository.

## What this is

A standalone Kestra plugin (`kestra-io/plugin-*` model) that integrates Plivo. Kestra does not accept integrations into its core repository; each integration ships as its own Gradle plugin JAR.

## Layout

- `io.kestra.plugin.plivo.AbstractPlivoConnection` — `Task` base holding `authId`/`authToken`, HTTP client config, and static helpers `basicAuthHeader` and `accountResourceUrl`.
- `io.kestra.plugin.plivo.message.AbstractMessageSend` / `Send` — SMS send.
- `io.kestra.plugin.plivo.message.Trigger` — polling trigger for inbound SMS (metadata-only fallback).
- `io.kestra.plugin.plivo.call.MakeCall` — outbound voice call.
- `io.kestra.plugin.plivo.security.VerifySignature` — validates the Plivo V3 webhook signature so a flow can trust an inbound SMS/call POST received by the core `io.kestra.plugin.core.trigger.Webhook` trigger.

## Inbound webhooks (real-time path)

A Kestra plugin cannot register its own HTTP endpoint — the built-in `core.trigger.Webhook` owns HTTP ingress, and `RealtimeTriggerInterface` is for queue consumers, not HTTP. So the real-time inbound path is: core Webhook trigger receives Plivo's POST → `VerifySignature` confirms authenticity → act on `trigger.body` (SMS reply via `Send`, or answer a call by returning `<Response>` XML with `wait: true` + `responseContentType: text/xml`). See README for both example flows.

### Plivo V3 signature algorithm (used by `VerifySignature`)

Base string = `url + "?" + <params sorted by key, each key+value concatenated with no separators> + "." + nonce` (if params empty: `url + "." + nonce`). Signature = `base64(HMAC-SHA256(authToken, base))`. Compare against header `X-Plivo-Signature-Ma-V3` (also accept `X-Plivo-Signature-V3`); nonce header `X-Plivo-Signature-V3-Nonce`. `url` is the exact URL Plivo posted to. Verified live against real Plivo (inbound SMS round-trip) and unit-tested against a fixed vector.

## Plivo API contract

- Base: `https://api.plivo.com/v1/Account/{AUTH_ID}/`. HTTP Basic auth `AUTH_ID:AUTH_TOKEN`. Console: `https://cx.plivo.com`.
- **Send SMS** — `POST Message/`, JSON body `{src, dst, text}`. Success is **HTTP 202** (queued, not delivered). Response `{message, message_uuid: [...], api_id}`. Multiple recipients join with `<`.
- **List messages** — `GET Message/`; response `{api_id, meta, objects: [...]}`. The list objects do NOT include the message body/text. Inbound filter: `message_direction=inbound`; time window: `message_time__gt`.
- **Make call** — `POST Call/`, JSON body `{from, to, answer_url}`. Success is **HTTP 201**. Response `{message, request_uuid, api_id}`. The `answer_url` returns Plivo XML (`<Speak>`).

Do not change these status codes: SMS success is 202, Call success is 201.

## Trigger dedup caveat

`message.Trigger` windows inbound messages by `message_time__gt = now - interval` on each poll. This is a lookback window, not persisted cursor state, so messages arriving exactly on a polling boundary could in theory be reported twice or (with clock skew) missed. Downstream flows should treat `message_uuid` as the idempotency key. A future improvement is to persist the last-seen message time or uuid in the trigger context.

## Conventions

- Zero inline comments, matching peer plugin density.
- Credentials are task properties backed by Kestra secrets (`PLIVO_AUTH_ID` / `PLIVO_AUTH_TOKEN`); `authToken` is marked `secret = true`.
- Tests use WireMock with a `baseUrl()` override on a test subclass.

## Status

Compiles against `kestraVersion=1.3.13`, Java 21; `./gradlew build` passes lint + all tests locally (including `VerifySignatureTest` against a fixed signature vector). The signature algorithm is confirmed live against real Plivo via the sibling StackStorm pack's inbound-SMS round-trip (same algorithm). Send/MakeCall/Trigger still need a maintainer live run + screenshots before any upstream catalog-adoption conversation.
