# ADR-105: The console signs the captain in with a one-time link and keeps a cookie session

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 7.3: `edi console` opens `http://127.0.0.1:<port>/login#<one-time token>`, and the console exchanges the token for an HttpOnly, SameSite=Strict session cookie with the captain role, so an answer given in the console counts as the captain's own word (section 9.1).
Section 16: state-changing console requests also carry a custom header the core checks, so no other page in the browser can submit on the captain's behalf.
ADR-020 left console sessions to this slice, and ADR-049 tells callers apart by the token they present.

**Decision:** `ConsoleAccess` issues a sign-in link of 32 random bytes (`Tokens.drawn`) and keeps only its SHA-256 digest in `console_sign_in`; a link signs in once and only within two minutes (`SignInLink`).
`POST /api/console/session` takes the link's token with the header `X-EDI-Console: 1`, marks the link used and answers 204 with the cookie `EDI-Console`, which is HttpOnly, SameSite=Strict, scoped to `/` and lives twelve hours (`ConsoleSession`, `ConsoleSessionCookie`); a used, expired or unknown link gets a 401 that says to run `edi console` again.
`ApiTokenFilter` turns a request without a bearer token but with an open session's cookie into `Caller.Captain`, and refuses with a 403 any console request that changes state without the header; a bearer token always wins over a cookie.
The captain caller may read everything the read class allows, answer a decision and ask for a re-check (ADR-108); `requirePrimary`, `requireWorker` and `edi hook` refuse it, and opening the console again needs the home token (`Caller.requireTheHomeToken`), so a worker's token can never mint a console session through the API.
`POST /api/console/open` serves `edi console` and `edi sitrep --board`: it issues a link and opens it in the captain's browser (ADR-106).
Expired links and sessions are deleted whenever a new link is issued, and a session survives a restart of the core.
The single-page application serves `/login` itself, through `ConsolePages` forwarding to `index.html`.

**Motivation:** A link that works once for two minutes is safe to pass through a terminal or a transcript, and a cookie the page cannot read keeps the session out of reach of scripts, while SameSite=Strict and the custom header keep other sites from submitting with it.

**Alternatives considered:**
- Putting the home token in the console - any script on the page could read it and act as the primary.
- A session kept only in memory - every restart of the core would sign the captain out of an open console.
- A session that lasts until the browser closes - a console left open for days would keep acting as the captain.

**Consequences:**
- Good: the console holds no secret a script can read, and its answers are recorded as the captain's (`Actor.CAPTAIN`).
- Bad: a worker that reads the home token from the shared home could still open a console session, as ADR-049 already accepts for every home-token command.

**Revisit when:** The core serves more than one person, or workers run as another operating-system user.

**Sources:** EDI rewrite specification, sections 7.3, 9.1 and 16; ADR-020, ADR-049; `application.access.ConsoleAccess`, `Tokens`, `domain.console.SignInLink`, `ConsoleSession`, `web.access.ApiTokenFilter`, `Caller`, `ConsoleSessionCookie`, `web.console.ConsoleController`, `ConsolePages`, `adapter.store.ConsoleAccessStore`.
