# Slate

Slate is a small remote UI for `codex app-server`.

I use it to drive Codex from a browser or Android device until Codex has first-party remote control.

## What it is

- Browser UI in `web/index.html`
- Node/Express proxy in `server.js`
- WebSocket bridge to `codex app-server`
- Android client in `android/`

Server endpoints:

- serves `GET /`
- exposes `GET /health`
- exposes `GET /workspaces` for workspace selection
- exposes `POST /session` to mint a short-lived WebSocket session ticket
- proxies `GET /ws?sid=<ticket>` to a spawned `codex app-server`

## Requirements

- Node 18+
- `codex` installed and available on `PATH` (or set `CODEX_APP_SERVER_CMD`)

## Setup

```bash
npm ci
cp .env.example .env
```

Edit `.env` and keep it local:

- `WEBAPP_AUTH_TOKEN`: shared secret for HTTP auth and WS ticket creation
- `WORKSPACE_ROOT`: absolute path to the workspace root you want to expose
- `CODEX_APP_SERVER_CMD`: defaults to `codex app-server`
- `PORT`: defaults to `3000`

Optional settings:

- `CODEX_APP_SERVER_ARGS`
- `MAX_MSG_BYTES`
- `MAX_QUEUE`
- `KILL_IDLE_MS`
- `SESSION_LOG`
- `WS_TICKET_TTL_MS`

## Run

```bash
npm start
```

Then open `http://localhost:3000`.

## Auth flow

- HTTP uses `Authorization: Bearer $WEBAPP_AUTH_TOKEN`
- The web client calls `POST /session` to mint a short-lived WS ticket
- The browser connects with `ws://localhost:$PORT/ws?sid=<ticket>`
- The optional `workspace` query param must stay inside `WORKSPACE_ROOT`

## Android

There is also a Kotlin/Compose Android client in `android/`. Point it at your server, enter the same auth token, and connect. For emulator use, `10.0.2.2:3000` hits the host machine.

More details are in [android/README.md](android/README.md).

## Notes

- `session.log` is off by default. Enable it with `SESSION_LOG=1` only if you actually need the wire log.
- If you enable logging, treat `session.log` like sensitive data.
- `.env`, `android/local.properties`, and `session.log` should stay local and out of git.
