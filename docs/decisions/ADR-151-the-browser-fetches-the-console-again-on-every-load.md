# ADR-151: The browser fetches the console again on every load

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The core serves the console's files from the console's jar (ADR-018, ADR-110), and the reproducible build stamps every jar entry with the same date in 1980.
Spring sent that date as `Last-Modified` and no `Cache-Control`, so a browser judged `index.html` fresh for years by its heuristic, and a revalidation with `If-Modified-Since` was answered `304 Not Modified` even when the file had changed.
While the sitrep page's new layout was checked end to end (ADR-150), a browser that had loaded the console before kept showing the old layout from the new core until its cache was bypassed.

**Decision:** `application.yaml` sets `spring.web.resources.cache.use-last-modified` to `false` and `spring.web.resources.cache.cachecontrol.no-cache` to `true`, so every console file is sent with `Cache-Control: no-cache` and without `Last-Modified`, and the browser fetches it again on every load.

**Motivation:** After `edi update` the captain sees the new console at the next load, not whenever a browser's cache happens to expire.
Every request stays on the loopback port, so fetching the page, its two bundles and its fonts again costs a few milliseconds.

**Alternatives considered:**
- Long caching for the hashed bundles and no caching for `index.html` only - more configuration for a saving that does not show on loopback.
- An `ETag` from a content-hashing filter - the console's files would be hashed again on every request for the same result.
- Real modification times in the jar - the build would no longer be reproducible.

**Consequences:**
- Good: a console update shows at once, in every browser, without a forced reload.
- Bad: a browser that cached the console before this change keeps its copy until one reload that bypasses the cache.

**Revisit when:** The console is served from anywhere but the loopback port.

**Sources:** ADR-018, ADR-110, ADR-150; `edi-core/src/main/resources/application.yaml`; `ConsoleApiTest`.
