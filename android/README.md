# Cortex Android Client

Lightweight Android Kotlin clone of the web Cortex client. Connects remotely to the Node.js `server.js` and provides full functional parity with the HTML client.

## Features

- **Remote connection**: Configure server host and port (e.g. `10.0.2.2:3000` for emulator → localhost)
- **Auth**: Bearer token with optional "remember token" (off by default)
- **Workspace picker**: Fetched from `/workspaces` API
- **Chat**: Send messages, streaming responses, interrupt
- **Message queue**: FIFO queue when a turn is active
- **Thread management**: New, resume, archive, fork, rollback
- **Slash commands**: `/new`, `/threads`, `/resume`, `/model`, `/interrupt`, `/archive`, `/fork`, `/rollback`, `/diff`, `/plan`, `/skills`, `/mcp`, `/status`, `/settings`, `/approval`, `/sandbox`, `/quit`, `/help`, etc.
- **Permission cards**: Approve / For session / Deny
- **Message types**: User, agent (markdown), reasoning, exec, tool call, file diff, errors
- **Settings**: Model, effort, approval policy, sandbox (persisted)
- **Token usage & rate limits**: Status bar when available

## Requirements

- Android SDK (set `ANDROID_HOME` or `local.properties` with `sdk.dir`)
- Android Studio or Gradle 8.5+
- Kotlin 1.9+
- Target: minSdk 26, targetSdk 34

## Setup

1. Copy `local.properties.example` to `local.properties` and set `sdk.dir` to your Android SDK path.
2. Ensure the Cortex server is running (`npm start` in the project root).
3. For emulator: use host `10.0.2.2` and port `3000` to reach host machine's localhost.
4. For physical device: use your machine's LAN IP and port `3000`.
5. Debug builds allow cleartext HTTP; for release, prefer HTTPS/WSS.

## Build

```bash
./gradlew assembleDebug
```

## Run

Install the APK on a device/emulator and launch. Enter your auth token (from `WEBAPP_AUTH_TOKEN` in server `.env`), optionally set server/port, then Connect.

## Architecture

- `network/`: WebSocketClient, ApiService (Retrofit for /workspaces)
- `data/`: CodexRepository
- `storage/`: SettingsStore (DataStore), TokenStore (EncryptedSharedPreferences)
- `ui/`: CodexViewModel, ConnectScreen, ChatScreen, SettingsSheet, HistoryDrawer
- `commands/`: SlashCommandHandler
- `diff/`: DiffParser
