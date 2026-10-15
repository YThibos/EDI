# ADR-018: The EDI console is an Angular SPA served by the core

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The old harness rendered its sitrep board as a page in an external board tool, and parsing answers back from that tool was a failure class of its own.
The captain asked: "will it be an SPA application served by SB (my pref.)?"

**Decision:** The EDI console is an Angular single-page application in the `edi-console` module, compiled at build time into static resources that `edi-core` serves on its loopback port.
Its sitrep page reads `GET /api/sitrep`, posts decision answers to `POST /api/decisions/{D}/answer` under the captain's console session, and refreshes on server-sent events from `/api/stream`.
Research boards that scouts write and iterate on with the captain stay in the external board tool.
Slice S0 ships only the console's skeleton, a page with the EDI wordmark, so the build, the packaging and the serving path are proven from the first commit.

**Motivation:** One origin and one port, no CORS, and board answers going straight into the core's use cases instead of through a parsed export.

**Alternatives considered:**
- Keep the sitrep board in the external board tool - keeps the answer-parsing failure class.

**Consequences:**
- Good: answer state is server-side, so an answer can never silently disappear after a reload.
- Bad: the build needs Node to compile the console (ADR-014 keeps it build-time only).

**Revisit when:** The console needs to run without the core.

**Sources:** EDI rewrite specification, sections 5.1, 15.3 and 15.5 (decision E10); `edi-console/`, `edi-core/pom.xml` (dependency on `edi-console`), `EdiCoreApplicationTest.servesTheConsole`.
