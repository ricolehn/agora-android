# Agora for Android

Native Android client for [Agora](https://github.com/ricolehn/agora), built with Kotlin, Jetpack Compose and Material 3.
It talks to the same backend as the web app (PWA) and mirrors its screens and rules, so members find their way around immediately.

## Features

| Area | What the app does |
|---|---|
| **Start** | Greeting with date, overdue or soon-due membership fee (nothing while all is paid), open duty requests with *Accept* / *Decline*, *Up next* (the next five appointments and events as a swipeable row), your upcoming duties, unread mentoring messages |
| **Finances (members)** | Fee status with monthly rate, requests (payment, standing order, status change, expense with receipts), request status, payment history |
| **Finances (treasurers)** | Cash balance, open requests (approve / reject with reason), searchable booking history with receipts, member list, member detail with payment booking, standing orders and status changes, record donations and expenses |
| **Events** | *Appointments* and *Events* tabs with the PWA's filter rules, search, highlight/cover cards, detail page with registration / waiting list, attendee list, duty roster (answer requests, managers add tasks, assign people or groups, remove entries), create / edit / delete events incl. cover image, target groups and recurring appointments |
| **Mentoring** | Conversations with unread badges, chat (live polling while open), end / reopen, find mentors and contact anonymously, mentor application / profile, review of applications for mentoring managers |
| **AI support** | Streaming answers rendered as Markdown, collapsible reasoning ("Denkprozess"), only visible with permission and when AI is enabled on the server |
| **Settings** | Profile picture (256×256 JPEG like the PWA), notification preferences, push setup, theme (system/light/dark), language (system/German/English), monthly fees (admins), password change, calendar subscription (iCal / Google Calendar), registration code |

Live updates: while the app is visible it listens to the server's `/api/stream` (SSE) and refreshes all data after every change, just like the PWA.

## Push notifications

Push runs through **Firebase Cloud Messaging** – nothing to install on the phone, and every Agora instance uses its own
Firebase project. The app contains **no** `google-services.json`:

1. After login the app asks the server `GET /api/push/fcm` for the public client config of the server's Firebase project
   (project id, app id, API key, sender id – read from the server's `google-services.json`).
2. It initialises Firebase with it at runtime (and again when the user switches to a server with another project),
   fetches its FCM token and registers it at `/api/push/fcm/subscribe`.
3. The backend sends every notification it sends as Web Push also as an FCM data message (same texts, same preferences:
   duties, events, messages, finances). The app shows it itself and opens the matching screen when tapped.

Server operators register an Android app with the package **`org.agora.app`** in their Firebase project and put its
`google-services.json` and the service account key into the data directory (see *Android App Push Notifications* in the
Agora README). Debug and release builds share the app id `org.agora.app`.
Without FCM (server not configured, device without Google services) the app falls back to a background check about every
15 minutes (WorkManager) for new duty requests, mentoring messages and events.

## Look & feel

The screens follow the PWA's design (`assets/style.css`): gradient brand name and buttons, pill tabs, heavy headings,
bordered cards, uppercase section headers and the white bottom navigation. The building blocks live in
`ui/components/AgoraDesign.kt` and `ui/components/Components.kt`, the colour tokens in `ui/theme/Theme.kt`.

Screens can be rendered without an emulator (Robolectric) against a running Agora test server with the seeded users:

```bash
./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots=http://127.0.0.1:4001 -PscreenshotsOut=/path/to/out
```

## Architecture

```
app/src/main/java/org/agora/app
├── AgoraApplication.kt      AppContainer (manual DI), Coil image loader sharing the authenticated OkHttp client
├── MainActivity.kt          splash, edge-to-edge, notification deep links
├── data/
│   ├── remote/AgoraApi.kt   OkHttp + kotlinx.serialization, Bearer token, error parsing, uploads, SSE & AI streams
│   ├── AgoraRepository.kt   every endpoint the app uses; treasurer write flows ported from the PWA
│   ├── AppStore.kt          single source of truth (auth state + member data), live updates
│   ├── local/SessionStore.kt DataStore: server, token, cached user, theme, push state
│   └── model/               API models with lenient serializers for legacy data (numbers as strings etc.)
├── push/                    FCM service, PushManager, notification channels, polling fallback
├── ui/                      Compose screens per feature (auth, home, finance, events, mentoring, ai, settings)
└── util/                    date/money formatting (de-DE like the PWA), image cropping
```

* All endpoints, payloads and rules follow the backend in the Agora repo.
* Status history changes use the exact algorithm of the PWA (`applyStatusChangeToHistory`), verified by unit tests whose expected values were generated from the PWA code.

## Building

Requirements: JDK 17 and the Android SDK (platform 35).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleRelease        # minified release build (configure signing first)
```

To try it against a local backend from the emulator, use the server address `http://10.0.2.2:<port>`.
