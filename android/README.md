# Cortex Android Client

Lightweight Android Kotlin/Compose client for Cortex. Connects remotely to the Node.js `server.js` and provides full functional parity with the HTML client.

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

## Architecture

- `network/`: WebSocketClient, ApiService (Retrofit for /workspaces)
- `data/`: CodexRepository
- `storage/`: SettingsStore (DataStore), TokenStore (EncryptedSharedPreferences), SessionCacheStore
- `service/`: CodexConnectionService (foreground service), ConnectionManager
- `ui/`: CodexViewModel, ConnectScreen, ChatScreen, SettingsSheet, HistoryDrawer, ActiveSessionsOverlay
- `commands/`: SlashCommandHandler
- `diff/`: DiffParser
