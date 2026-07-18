# RaamsesAndroid — Agent Console for Android

Android Agent Console for the [RAAMSES](https://raamses.io) agent monitoring ecosystem.

Built with Jetpack Compose + Material 3. Dark OLED-optimized theme. Connects to any RAAMSES server over TCP, or runs standalone with mock data.

## Features

- **Gateway** — slash-command dispatch (`/agents`, `/approve`, `/pause`, `/status`, etc.)
- **Dashboard** — live agent status cards, work pulse, token usage, server health
- **Agent Detail** — objective tracking, evidence-backed completion, activity feed
- **Alerts** — severity-coded alerts with acknowledge support
- **Mock mode** — full demo with 4 agents and realistic data, zero setup

## Quick Start

1. Download the latest APK from [Releases](https://github.com/texsean/RaamsesAndroid/releases)
   or from [Actions](https://github.com/texsean/RaamsesAndroid/actions) → latest build → Artifacts
2. Install on Android 8+ device
3. Launch — mock data loads immediately
4. To connect to a real server: type `/connect <host>:42000` in the Gateway tab

## Building

Open in Android Studio, or:

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Auto-Build

Every push to `main` triggers a GitHub Actions workflow that builds the APK.
Download from Actions → latest run → Artifacts.

## Requirements

- Android 8.0+ (API 26)
- ~15MB install size
- Internet permission (for TCP server connection)

## Related

- [RAAMSES](https://github.com/texsean/Raamses) — main product repo
- [RaamsesServer](https://github.com/texsean/RaamsesServer) — server + protocol
- [raamses.io](https://raamses.io) — landing page

## License

Proprietary. See [RAAMSES license](https://github.com/texsean/Raamses/blob/master/LICENSE).
