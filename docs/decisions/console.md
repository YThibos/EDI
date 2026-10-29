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
`application.yaml` sends every console file with `Cache-Control: no-cache` and without `Last-Modified`, so the browser fetches it again on every load.
`CaptainsBrowser` opens a sign-in link from `OpenConsole` without putting it on any command line: it writes an owner-only hand-off page under `state/console/` in the EDI home whose only content sends the browser on to the link, opens that page, and removes it once the link could no longer sign in, two minutes later (`HandOffPages`).
Beside Windows it opens the page through `cmd.exe /c start file://wsl.localhost/<distribution>/<path>` from `edi.console.windows-directory` (`/mnt/c`) when that exists, the distribution named by `edi.console.wsl-distribution`, which WSL's `WSL_DISTRO_NAME` fills; else through `xdg-open <path>` when `edi.console.desktop-display` names a display; an opener gets 15 seconds, and one still running then counts as opened and is never stopped.
`edi console` and `edi sitrep --board` print only the console's address when the browser opened, and the sign-in link itself when nothing did, since the captain then has no other way to sign in.
`SessionStart.opensTheSitrep` holds for the launch of a new session that holds the primary role, and `OpenTheSitrepAtSessionStart` then opens the console unless the profile sets `console.openSitrepAtSessionStart: false`; the digest names where the page is, never the link, and a request for the page from the session after its first prompt opens it again ([opening-turn](opening-turn.md)).
Research boards that scouts write stay in Lavish ([research-boards](research-boards.md)).

## Why

One origin and one port, no CORS, and answers going straight into the core's use cases, so an answer can never silently disappear after a reload.
A signal without a payload keeps the snapshot the only source of what the page shows, and a page that re-reads on reconnect cannot keep a stale view.
A process list shows only the hand-off page's path, so no other user of the machine reads a live link from it, and the link the transcript carries when nothing opened signs in once, within two minutes, from the captain's own terminal.
Opening the page from the core works however the session started, and the store's record of the session with Claude Code's `source` tells a new primary from the same one resumed or compacted.

## Alternatives rejected

- Keeping the sitrep board in the external tool - keeps the answer-parsing failure class.
- Pushing the whole document on every event, or polling - a page could show an older document, or read the store for nothing.
- Long caching of hashed bundles, or an `ETag` filter - more configuration for a saving loopback never shows.
- The link on the opener's command line, or through a helper's standard input - every user of the machine can read a process's arguments while the link lives, and a helper still hands the link to the browser on its command line.
- Leaving the link out of the transcript when nothing opened - the captain would have no way to sign in.
- `explorer.exe`, `xdg-open` first everywhere, or the opener chosen in the profile - fails even on success, opens a second Linux browser on WSLg, or a setting that can disagree with the machine.

## Consequences

- Good: an answer given anywhere shows on an open console within a moment, a console update shows at the next load, and the page appears without copying a link.
- Bad: any process running as the captain's own user, a worker included, can read the hand-off page while it lives, as it can read the home token; the page keeps the link from other users only.
- Bad: beside Windows the core needs the distribution's name, and without it the command prints the link instead of opening the browser; a core stopped within the two minutes leaves its hand-off page behind, holding a link that no longer signs in.
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
