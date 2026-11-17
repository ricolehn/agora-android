<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/assets/logo-dark.svg" />
    <img src=".github/assets/logo.svg" width="110" alt="Agora Logo" />
  </picture>
  <h1>Agora for Android</h1>
</div>

Native Android client for [Agora](https://github.com/ricolehn/agora), built with Kotlin, Jetpack Compose and Material 3.
It talks to the same backend as the web app (PWA) and mirrors its screens and rules, so members find their way around immediately.

## Features

| Area | What the app does |
|---|---|
| **Start** | Greeting with date, overdue or soon-due membership fee (nothing while all is paid), open duty requests with *Accept* / *Decline*, *Up next* (the next five appointments and events as a swipeable row), your upcoming duties, unread mentoring messages |
| **Finances (members)** | Fee status with monthly rate (the fee of this month as the server works it out, server CALDERA), requests (payment, standing order, status change, expense with receipts), request status, payment history. After a fresh start of the fee accounts: the chip *Fresh start on …* for 60 days, and the day as a marked point in the history with what happened then (credit carried over / open fees dropped / all paid up) – the entries below it, the settled time before, quieter. A payment request dated before the fresh start says that it first pays off the old arrears |
| **Finances (treasurers)** | Cash balance, open requests (approve / reject with reason), searchable booking history with receipts (through API v2 on servers CALDERA and newer, see below), member list, member detail with payment booking, standing orders and status changes, record donations and expenses. Every booking of the history opens with its details; finance managers *Edit* it there like in the web app – amount, date, note or description, the donor or who issued an expense and its receipts (remove, add pictures) – or delete it after a confirmation. Booking a payment – deciding on a payment request, or moving a payment's date – before a member's fresh start says what it does: it first pays off the open fees dropped then |
| **Events** | *Appointments* and *Events* tabs with the PWA's filter rules, search, highlight/cover cards, detail page with registration / waiting list (with the minimum: *2 more needed (min. 5)* / *minimum of 5 reached*; a minimum above the places is refused before saving, as server CALDERA does), attendee list (guests without an account marked, their contact for the event's editors), duty roster (answer requests, managers add tasks, assign people or groups, remove entries), create / edit / delete events incl. cover image, target groups and recurring appointments |
| **Sharing** | Share icon right of every event's title and organiser: a sheet with the event's link to copy or to hand to Android's share sheet, and, for those who may edit the event, the switch *Visible without an account* (server 3.2 or newer) that lets people without an account open the link and sign up as guests in the browser |
| **Mentoring** | Conversations with unread badges, chat (live polling while open), end / reopen, find mentors and contact anonymously, mentor application / profile, review of applications for mentoring managers |
| **Extras** | One tab for the further offers in the web's order: the AI chat across the full width on top, then *Media*, *Songbook* and *Prayer requests* as tiles with flat tinted icons (each only for those who may use it) |
| **Media** *(server 3.3 BA SING SE)* | The community's Nextcloud folder as a library: folders and thumbnails (Nextcloud's previews in the server's fixed sizes, cached until the file changes), path above the list, sort by name or newest, back goes up a folder. Pictures open full screen (swipe, pinch or double tap to zoom; zoomed in, the largest preview takes over – never the original, so DNG, RAW or HEIC show too), PDFs page by page (Android's PdfRenderer), videos and music in the app's player (Media3, seekable), anything else in another app. Holding an entry selects; the selection downloads as one ZIP (or the single file) to a place you pick and, with write access, is deleted. With write access: upload files (streamed, with progress) and create folders. Members with *Extended media rights* set the rights of every folder (all members, groups, single people: read, write, no access) and see all folder rights at a glance |
| **AI support** | Streaming answers rendered as Markdown; the assistant's orb, a greeting by name with three suggestions, a line above the reply that says what the AI is doing right now, as in the web app – "Thinking · 4 s", then the title of its newest thought (the bold heading summarized thoughts put above every step, else its newest finished sentence: *Calculating the monthly expenses*) and, from server CALDERA on, what the server looks up for it (*Finding Nick among the members*, or *Looking up expenses 2019–2024*); every title stays long enough to be read and changes with a short fade, afterwards the line says "Thought · 12 s" ("Thought · looked up 2× · 12 s") – a reply without thoughts or lookups folds it away. A tap opens the steps in order – thoughts as markdown, the notes written on the way (no longer glued to the answer) and every lookup with what it found (*12 entries · €345.50*, *not allowed*) – as a window of a few lines that follows the newest step while the AI works (*Show more* opens them in full); says when a model only thought or the connection died (servers 3.3+ keep the stream alive while the model thinks); only visible with permission and when AI is enabled on the server |
| **Monthly fees** *(server CALDERA)* | *Settings → Monthly fees* for system admins and treasurers. **Fees over time:** the fees of this month as tiles (*Since …*), the change still to come, every change with the month it counts from, what it changed, who set it and what it is today (in effect, planned, replaced), and a log. System admins change the fees in two steps: the three amounts and the month they count from (next month is offered; a line says what the month means – later, now, or reaching back), then what it does as the server works it out (the months that cost something else, who owes how much more or less today, a warning when it reaches back before a fresh start) – only then it is saved, exactly as it was checked (changed by someone else in between: refused, checked again). Every change can be edited, moved or taken back; the fees from the beginning can be corrected. **Fresh start:** the days the fee accounts were started afresh with their state; system admins choose a day (past or future), see the numbers (open fees dropped / credit carried over, how many members) and confirm by typing the day again and their password – taking one back works the same way. Treasurers read both. Servers before CALDERA: the fee settings as before |
| **Settings** | Profile picture (256×256 JPEG like the PWA), notification preferences, push setup, theme (system/light/dark), language (system/German/English), monthly fees (see above), password change, calendar subscription (iCal / Google Calendar), registration code |

Fees: from server CALDERA on, the app reads the fees over time (`GET /fees` of API v2) and the fresh starts
(`GET /fresh-starts`), and the fee accounts carry what the server worked out (`monthlyFeeCents`, `freshStart`, …). Changes
go through the server's preview first (`POST /fees/preview`, `GET /fresh-starts/preview`) and are saved with what was
checked (`POST /fees[/remove]` with the preview's `version`, `POST /fresh-starts[/remove]` with the day twice and the
admin's password) – on a CALDERA beta without the whole of v2 through the old API's `/api/fees`, `/api/fee-reset` and
`/api/admin/...`. Older servers answer neither: the app then shows the fee settings as before. Through v2 the app always
shows the fees over time, also where no change of them was saved yet (an instance updated from a version before them).

### API v2 (server CALDERA)

From server CALDERA (3.4) on, the app goes through **API v2** (`/api/v2`, `docs/API_V2.md` in the Agora repository) for
everything that is data of the community: record ids, money in whole cents, days as `YYYY-MM-DD`, every list in pages
with a cursor, errors as `{ error: { code, message, field } }` in the app's language, and every change of the books
names the version of the data it is based on – refused with 409 instead of overwriting what someone else changed
meanwhile. The app asks the server when it connects and with every refresh (`GET /api/status` → `apiVersions: [1, 2]`,
then `GET /api/v2` → the resources) – before it loads anything while it goes the old way – and goes through v2 **all or
nothing**: only where the server offers every resource the app uses (`ApiV2.NEEDED`), so what the screens show always
comes through one API.

The books:

| What | Through API v2 |
|---|---|
| Fee accounts | `GET /members` – the fee account as the server works it out (`feeState`, `isDueSoon`, `hasActiveStandingOrder`, `currentStatus`, `freshStart` …) for the treasury's list and the start page; a member with its payments, standing orders and status periods from `GET /members/:id` |
| Payments | booked on the member's `memberVersion` (`POST /payments`), changed and deleted by their record (`PATCH` / `DELETE /payments/:id`) |
| Standing orders, status changes | `POST /standing-orders` (the server books its months up to today, as the web app does), ended (`PATCH`, also retroactively) or removed (`DELETE`); `POST /status-changes` – the server rewrites the history by the web app's rule |
| Donations, expenses | added, changed and deleted by their record with the `listVersion` of the list; receipts as a list (`receipts`, at most 20 – the form says when no more fit), uploaded and read through `/receipts` (also PDFs) |
| Requests | read, filed about the own record (`POST /requests` with its `memberId` and `notify`: the server tells the treasurers), and approved in one step (`POST /requests/:id/decision` with `book: true`: booked once as the web app books it – what cannot be booked as it is stays open), rejected once |
| History, report, cash balance | `GET /finance/ledger` – every booking newest first, a page at a time, searched by the server (`?q=`: who, description, day, amount); the cash balance from `GET /finance/summary` |

A change of a booking is made on the version read for it: a new booking simply goes on the books as they are now, a
change or deletion only while the entry is still what was opened – otherwise nothing is written and the history shows
what is stored now. The forms take no more than v2 does (notes and descriptions 5,000 characters, names 200, a
rejection's reason 500). The app asks for every answer in its language (`Accept-Language`, also from the old API).

Everything else:

| What | Through API v2 |
|---|---|
| Calendar | `GET /events` (page after page, with `createdByName`, `isFull`, `canEdit`, the share link's `shareToken` and the duty roster with its names for those who see it), created with its series (`recurrence`) and duties, changed, shared (`isPublic`) and deleted; signing up (`PUT` / `DELETE /events/:id/registration`), the participants (a guest as `guest:<id>`), pictures (`POST /events/images`), the order of service (`GET /events/:id` → `program`, `PUT /events/:id/program`), the personal calendar feed |
| Duty roster | asked (`POST /events/:id/duties`, `POST /duties/:id/request`), given to a group or person (`/assignment`), answered (`/answer`), taken and given back (`PUT` / `DELETE /duties/:id/claim`), notes, deleted; the duties asked of the person (`GET /duty-requests`), the people and groups it can go to (`/people`, `/groups`) |
| Own account | notifications, time zone (`PATCH /me`), the profile picture (`PUT /me/picture`, the others' from `/pictures`), the registration code (`/registration-code`: a new one is made by the server), reports to the admins (`POST /reports`) |
| Community | the songbook (`/songs`, with the CCLI licence; who manages it from `GET /me`), prayer requests (`/prayers`), mentoring (mentors, applications, the own profile, conversations – reading one marks what the other wrote as read, `POST /mentoring/threads/:id/read` – and their messages page after page) |
| Media library | `/media`, `/media/items` (the folder and its entries, page after page), previews, files, uploads, folders, deleting, ZIP downloads, rights |
| Fees and fresh starts | `GET /fees` (in cents; the settings page shows euros), previews and changes (`POST /fees/preview`, `/fees`, `/fees/remove` with the preview's version), the fresh starts (`/fresh-starts`, preview, set and take back with the admin's password) |

v2 checks every value and refuses what does not fit (`400 invalid`) instead of cutting it, as the old API did: the forms
stop where v2 does (an event's title 200, place 300, description 10,000 characters; an entry of the plan 80; a song's
title 120 and text 20,000; a mentor's profile 2,000 and a message 10,000 characters, at most 50 mentees; a report is
cut to the 4,000 characters the server takes).

Only what v2 leaves to the old API by design goes there: signing in and the account itself (`/api/auth/...`), push
(`/api/push/...`), the live updates (`/api/stream`), the AI assistant (`/api/ai/...`) and the instance's administration
(`/api/admin/...`). Servers before CALDERA, the betas of CALDERA whose v2 had only a part of this, and a CALDERA server
whose database upgrade waits (its previous version serves the instance) are asked the old way throughout; a v2 route
the server does not know switches back at once. An answer that says nothing about v2 keeps the API it was: a lost
connection while asking, a server that is starting (`503`), too many requests (`429`), an error in between (`5xx`). A
standing order read through v2 is changed through v2 only – should the server answer no v2 any more, nothing is written
and the card is read anew. A server that is starting or upgrading its database (`503`, `ready: false`) gets *try again
in a moment*.

The app's own calls can be checked against a real CALDERA server (`LiveServerTest`: the calendar, the community, the
media library and the fee settings read through v2 and the old API side by side; only a test instance: with *manage
finances* it books, changes and removes a donation, a payment and a standing order, and leaves the books as they were):

```bash
AGORA_LIVE_EMAIL=… AGORA_LIVE_PASSWORD=… ./gradlew testDebugUnitTest --tests '*LiveServerTest*' -PliveServer=https://beta.example.org
```

Bookings through the old API: an edited or deleted booking is looked up again in what is stored now, as the web app does (`saveEditedPayment`):
a payment in the member's record by its id (old ones without an id by their place, while it still holds the date and
amount that were opened), a donation or an expense in its list. Donations and expenses are saved with the version of the
list they were read with (`GET /api/db?…&raw=1`, `version` with `PUT /api/db`): a list someone else saved meanwhile is
read and changed again instead of overwritten, and a deleted entry really goes. Servers that know no version save the
list as before. A booking that is not there any more (deleted meanwhile, or an old one without an id that changed) is
left alone, and the history says so and shows what is stored now.

Live updates: while the app is visible it listens to the server's `/api/stream` (SSE) and refreshes what changed, just like
the PWA: events, mentoring and the media library on their own, prayer requests, songs and the open folder only on their
pages; anything else reloads everything. Profile pictures are only asked for members who have one, under an address with
their version that stays cached until the picture changes (`GET /api/profile/pictures`, server 3.3+; older servers: as
before).

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
│   ├── remote/AgoraApi.kt   OkHttp + kotlinx.serialization, Bearer token, error parsing (old API and v2), uploads, SSE & AI streams
│   ├── remote/ApiV2.kt      API v2 (server CALDERA): pages with a cursor, falls back when the server answers no v2
│   ├── AgoraRepository.kt   every endpoint the app uses, v2 where the server answers it; treasurer write flows ported from the PWA
│   ├── FinanceV2.kt         the books through API v2: fee accounts, versions, standing orders, receipts, the ledger
│   ├── CalendarV2.kt        the calendar and its duty roster through API v2
│   ├── CommunityV2.kt       songbook, prayer requests and mentoring through API v2
│   ├── AccountV2.kt         the own account's settings, pictures, registration code and reports through API v2
│   ├── MediaApi.kt          media library (Nextcloud through the server): listing, previews, streamed up/downloads, rights
│   ├── AppStore.kt          single source of truth (auth state + member data), live updates
│   ├── local/SessionStore.kt DataStore: server, token, cached user, theme, push state
│   └── model/               API models with lenient serializers for legacy data (numbers as strings etc.)
├── push/                    FCM service, PushManager, notification channels, polling fallback
├── ui/                      Compose screens per feature (auth, home, finance, events, mentoring, hub, ai, media, songbook, prayers, settings)
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
