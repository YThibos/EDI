# ADR-109: The console stream signals each commit, and the page reads the sitrep again

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 15.3: the page subscribes to `/api/stream` (server-sent events, section 5.8) and re-reads `GET /api/sitrep` the moment an event changes the fleet, plus on focus.
ADR-011 publishes every recorded event after its commit, and ADR-069 left the relay for an outward push to the first follow-up that needs one.

**Decision:** `ConsoleStream` keeps one `SseEmitter` per open page; on every `EventRecorded` after commit it sends each page a `fleet` event carrying the event's sequence, from a virtual thread, so a slow browser never holds the committing request.
A new stream first sends a comment with a reconnect time of three seconds, which commits the response so the page knows it is connected, and each stream ends after ten minutes, after which the browser reconnects by itself.
The page re-reads the sitrep on every `fleet` event, whenever the stream (re)connects, and when its window gains focus; it shows `live` while the stream is open and `reconnecting` while it is not.
When the core begins to shut down (`ContextClosedEvent`), every stream is completed, so an open console never holds the graceful shutdown.

**Motivation:** A signal without a payload keeps the snapshot the only source of what the page shows, and a page that re-reads on reconnect cannot keep a stale view after a missed signal, so the stream needs no relay of its own.

**Alternatives considered:**
- Pushing the whole document on every event - the page could show a document older than one it already read.
- Polling the sitrep on a timer - updates would lag and the store would be read for nothing while the fleet is quiet.
- The relay of ADR-011 for the stream - a missed signal costs only freshness until the next event or reconnect, never state.

**Consequences:**
- Good: an answer given in chat or on a board shows on an open console within a moment.
- Bad: a busy fleet makes every open page read the sitrep once per event.

**Revisit when:** More than a few pages stay open at once, or the page needs to know what changed.

**Sources:** EDI rewrite specification, sections 5.8 and 15.3; ADR-011, ADR-069; `web.console.ConsoleStream`, `edi-console` `FleetStream`, `SitrepPage`.
