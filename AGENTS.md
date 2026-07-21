# AGENTS.md — plugin-plivo

Working context for AI agents and contributors touching this repository.

## What this is

A standalone Kestra plugin (`kestra-io/plugin-*` model) that integrates Plivo. Kestra does not accept integrations into its core repository; each integration ships as its own Gradle plugin JAR.

## Layout

- `io.kestra.plugin.plivo.AbstractPlivoConnection` — `Task` base holding `authId`/`authToken`, HTTP client config, and static helpers `basicAuthHeader` and `accountResourceUrl`.
- `io.kestra.plugin.plivo.message.AbstractMessageSend` / `Send` — SMS send.
- `io.kestra.plugin.plivo.message.Trigger` — polling trigger for inbound SMS.
- `io.kestra.plugin.plivo.call.MakeCall` — outbound voice call.

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

Phase 1 build. Compiles against `kestraVersion=1.3.13`, Java 21. NOT yet run through a live Plivo account or a full `./gradlew build` in CI — needs maintainer test + screenshots before any upstream catalog-adoption conversation.
