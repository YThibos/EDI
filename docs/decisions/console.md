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
`ConsoleStream` keeps one `SseEmitter` per open page and, on every `EventRecorded`, sends a `fleet` event carrying only the event's sequence; the page reads the sitrep again on every signal, on every reconnect and on focus, and every stream is completed when the core begins to shut down.
`application.yaml` sends every console file with `Cache-Control: no-cache` and without `Last-Modified`, so the browser fetches it again on every load.
`CaptainsBrowser` opens a sign-in link from `OpenConsole`: through `cmd.exe /c start` from `edi.console.windows-directory` (`/mnt/c`) when it exists, else through `xdg-open` when `edi.console.desktop-display` names a display; an opener gets 15 seconds, and one still running then counts as opened and is never stopped.
`edi console` and `edi sitrep --board` print only the console's address when the browser opened, and the sign-in link itself when nothing did.
`SessionStart.opensTheSitrep` holds for the launch of a new session that holds the primary role, and `OpenTheSitrepAtSessionStart` then opens the console unless the profile sets `console.openSitrepAtSessionStart: false`; the digest names where the page is, never the link.
Research boards that scouts write stay in Lavish ([research-boards](research-boards.md)).

## Why

One origin and one port, no CORS, and answers going straight into the core's use cases, so an answer can never silently disappear after a reload.
A signal without a payload keeps the snapshot the only source of what the page shows, and a page that re-reads on reconnect cannot keep a stale view.
Opening the page from the core works however the session started, and the store's record of the session with Claude Code's `source` tells a new primary from the same one resumed or compacted.

## Alternatives rejected

- Keeping the sitrep board in the external tool - keeps the answer-parsing failure class.
- Pushing the whole document on every event, or polling - a page could show an older document, or read the store for nothing.
- Long caching of hashed bundles, or an `ETag` filter - more configuration for a saving loopback never shows.
- `explorer.exe`, `xdg-open` first everywhere, or the opener chosen in the profile - fails even on success, opens a second Linux browser on WSLg, or a setting that can disagree with the machine.

## Consequences

- Good: an answer given anywhere shows on an open console within a moment, a console update shows at the next load, and the page appears without copying a link.
- Bad: the build needs Node to compile the console, a busy fleet makes every open page read the sitrep once per event, and a session start waits up to 15 seconds for the browser.

## When to revisit

When the console must run without the core, more than a few pages stay open, the console is served beyond loopback, or EDI runs on macOS.

## How we got here

- 15/10/2026: the captain chose a single-page application served by the core; slice S0 shipped its skeleton.
- 17/10/2026: slice S7 built the sitrep page, the stream and opening through the Windows start command.
- 21/10/2026: the sitrep page opens at every new primary session, `xdg-open` serves a Linux desktop, and a browser that kept showing an old layout led to no-cache serving.
