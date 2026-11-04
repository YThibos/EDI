# The console is an Angular single-page application the core serves, kept live by a signal stream and opened in the captain's browser by EDI

**Kind:** Architecture

## Context

The old harness rendered its sitrep board in an external board tool, and parsing answers back from it was a failure class of its own.
The captain wants the sitrep page at one glance, opened without hunting for a link in the terminal, signed in, at every new EDI session.
The core runs on WSL on the laptop and may run on a plain Linux desktop; only adapters start programs ([ports-and-architecture-rules](ports-and-architecture-rules.md)).
The reproducible build stamps every jar entry with the same date in 1980.

## Decision

The console is an Angular single-page application in `edi-console`, compiled into static resources that `edi-core` serves on its loopback port, with a sign-in page and the sitrep page ([console-look](console-look.md)).
The sitrep page reads `GET /api/sitrep` ([fleet-snapshot](fleet-snapshot.md)) and posts answers and re-checks under the captain's console session ([api-access](api-access.md), [captain-decisions](captain-decisions.md)).
Its header's Personality latch opens the personality desk above the sitrep, which reads and keeps the personality through `/api/personality`, the path `edi personality` takes, and reads it again on focus while no change waits to be kept ([personality](personality.md)).
`ConsoleStream` keeps one `SseEmitter` per open page and, on every `EventRecorded`, sends a `fleet` event carrying only the event's sequence; the page reads the sitrep again on every signal, on every reconnect and on focus, and every stream is completed when the core begins to shut down.
Each reading titles the browser tab `(N) SITREP · EDI` while N things wait on the captain, and `Attention` raises one browser notification, in EDI's first person, for each decision or review that was not on the page before, never for what it showed when it opened; it asks the browser's permission once, at the captain's first click on the page, remembers that it asked, and shows nothing without the permission.
`application.yaml` sends every console file with `Cache-Control: no-cache` and without `Last-Modified`, so the browser fetches it again on every load.
`OpenConsole` hands a sign-in link to the browser without putting it on any command line: `ConsoleHandOffs` keeps the link in the core's memory under a fresh random code and gives out the console's own address `http://127.0.0.1:<port>/hand-off/<code>`, which `CaptainsBrowser` opens.
That page answers its first request, within the link's two minutes, with a `303 See Other` to the sign-in link and `Cache-Control: no-store`, and every later request with a `404` saying it was opened already or has expired.
Beside Windows `CaptainsBrowser` opens the address through `cmd.exe /c start <address>` from `edi.console.windows-directory` (`/mnt/c`) when that exists, so Windows' default web browser opens it; else through `xdg-open <address>` when `edi.console.desktop-display` names a display; an opener gets 15 seconds, and one still running then counts as opened and is never stopped.
`edi console` and `edi sitrep --board` print only the console's address when the browser opened, and the sign-in link itself when nothing did, since the captain then has no other way to sign in.
`SessionStart.opensTheSitrep` holds for the launch of a new session that holds the primary role, and `OpenTheSitrepAtSessionStart` then opens the console unless the profile sets `console.openSitrepAtSessionStart: false`; the digest names where the page is, never the link, and a request for the page from the session after its first prompt opens it again ([opening-turn](opening-turn.md)).
Research boards that scouts write stay in Lavish ([research-boards](research-boards.md)).

## Why

One origin and one port, no CORS, and answers going straight into the core's use cases, so an answer can never silently disappear after a reload.
A signal without a payload keeps the snapshot the only source of what the page shows, and a page that re-reads on reconnect cannot keep a stale view.
An http address opens in the browser Windows names for http, the captain's default web browser, whatever program the `.html` file association names.
A process list shows only the hand-off address, which leads on once and only while the opener's own browser has not yet followed it, and the link the transcript carries when nothing opened signs in once, within two minutes, from the captain's own terminal.
Opening the page from the core works however the session started, and the store's record of the session with Claude Code's `source` tells a new primary from the same one resumed or compacted.

## Alternatives rejected

- Keeping the sitrep board in the external tool - keeps the answer-parsing failure class.
- Pushing the whole document on every event, or polling - a page could show an older document, or read the store for nothing.
- Long caching of hashed bundles, or an `ETag` filter - more configuration for a saving loopback never shows.
- The link on the opener's command line, or through a helper's standard input - every user of the machine can read a process's arguments while the link lives, and a helper still hands the link to the browser on its command line.
- An owner-only hand-off page in the EDI home opened as a `file://` address - Windows opens a `.html` file with the program its file association names, which can be another browser than the default web browser, and that browser then spends the link.
- Leaving the link out of the transcript when nothing opened - the captain would have no way to sign in.
- `explorer.exe`, `xdg-open` first everywhere, or the opener chosen in the profile - fails even on success, opens a second Linux browser on WSLg, or a setting that can disagree with the machine.

## Consequences

- Good: an answer given anywhere shows on an open console within a moment, a console update shows at the next load, and the page appears without copying a link.
- Bad: a user of the machine who reads the hand-off address from the process list and follows it before the captain's browser does gets the sign-in link; the captain's browser then shows that the page was opened already, so the theft does not go unnoticed.
- Bad: a core restarted within the two minutes forgets its hand-offs, and an address opened after the restart leads nowhere.
- Bad: the build needs Node to compile the console, a busy fleet makes every open page read the sitrep once per event, and a session start waits up to 15 seconds for the browser.

## When to revisit

When the console must run without the core, more than a few pages stay open, the console is served beyond loopback, or EDI runs on macOS.

## How we got here

- 15/10/2026: the captain chose a single-page application served by the core; slice S0 shipped its skeleton.
- 17/10/2026: slice S7 built the sitrep page, the stream and opening through the Windows start command.
- 21/10/2026: the sitrep page opens at every new primary session, `xdg-open` serves a Linux desktop, and a browser that kept showing an old layout led to no-cache serving.
- 23/10/2026: a fresh `edi up` leaves one sitrep page open, since the opening turn names the page its session's start opened (EDI-43).
- 27/10/2026: at the captain's request the primary's first typed prompt clears the session-start mark, so a `/sitrep board` always opens the page and a fresh `edi up` may leave two pages open (EDI-101).
- 27/10/2026: the personality desk joined the sitrep page behind a latch in its header (EDI-98).
- 29/10/2026: the sign-in link reaches the browser through a short-lived owner-only hand-off page instead of the opener's command line, and the transcript keeps the link only when nothing opened (EDI-127).
- 03/11/2026: Windows opened the hand-off page's `.html` file in Edge while the captain's default web browser is Vivaldi, so the hand-off moved to a once-only http address of the console and the core no longer needs the distribution's name (EDI-169).
- 03/11/2026: the captain keeps the sitrep page open as a panel and wanted to notice news without watching it, so the tab title counts what waits and a new decision or review raises a browser notification (EDI-63).
