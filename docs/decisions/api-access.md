# The core listens on loopback only, and every caller is told apart by its token or the captain's console session

**Kind:** Architecture

## Context

The core's API changes the fleet and answers the captain's decisions, and the captain's word is the authority for destructive and irreversible acts (I-06).
Any page open in the captain's browser could otherwise submit requests to a local port.
Workers run as the same operating-system user as the primary, so a token tells callers apart; it is not a secret from the user who owns the home.

## Decision

The core binds `127.0.0.1` only, on port 5490 from `server.port` in `edi-core`'s `application.yaml`, which `SERVER_PORT` overrides, and writes the bound port to `state/core.port`.
When another program holds the port, the core stops at start, and `CorePortTakenAnalyzer`, registered in `META-INF/spring.factories`, names the port and `SERVER_PORT` in its log.
At each start it draws a home token of 32 random bytes, writes it owner-only to `state/core.token` and keeps it in memory (`ApiToken`).
At each launch it draws a worker token, writes it owner-only to the worker's state directory, and keeps only its SHA-256 digest on the worker's row (`WorkerTokens`); the worker's window carries the file's path in `EDI_TOKEN_FILE`, never the token.
`ApiTokenFilter` decides on the decoded servlet path: the home token is `Caller.Primary`, or `Caller.Standby` when another session holds the primary role ([primary-role](primary-role.md)); a live worker's token is `Caller.WorkerCaller`; anything else gets a 401, and only `/api/health` and the console's files stay open.
Controllers take the `Caller`: `requirePrimary` refuses a worker the commands that add, hold, steer, dispatch or answer, and `requireWorker` refuses the primary `edi status` and the inbox; every event records its real actor.
The captain signs in to the console with a one-time link: `ConsoleAccess` issues 32 random bytes, keeps only their digest, and a link signs in once within two minutes; `POST /api/console/session` with the header `X-EDI-Console: 1` answers with the cookie `EDI-Console`, HttpOnly, SameSite=Strict and twelve hours long.
A request with an open session's cookie and no bearer token is `Caller.Captain`, which may read, answer decisions, ask for re-checks, set presence to present or a demo, and change the personality; a console request that changes state without the header gets a 403, and minting a link needs the home token.

## Why

No port is reachable from outside the machine, a confused or misled worker cannot act as the primary through the API, and no other page in the browser can submit as the captain.
A token never appears in a command line, a tmux environment or a log, because only its file path travels, and a link that works once for two minutes is safe to pass through a terminal.
Port 5490 spells E-D-I by the letters' places in the alphabet, so the captain remembers the console's address, and it lies below both the Linux ephemeral range (32768-60999 by default) and the Windows dynamic range (49152-65535), which a WSL machine with mirrored networking shares, so an outgoing connection never holds it when the core starts.

## Alternatives rejected

- No authentication until workers exist, or Spring Security - an open port that changes state, or a framework for one header comparison.
- Trusting `EDI_ROLE` from the environment, or the token in the tmux environment - any process could claim to be any worker, and `tmux show-environment` shows it.
- Port 47318 - hard to remember, and inside the Linux ephemeral range, so an outgoing connection could hold it at start.
- A port from the machine profile, or a random port - the console's address would differ between machines or starts, and `SERVER_PORT` already moves it where 5490 is taken.
- The home token in the console, or a session in memory only - any script on the page could act as the primary, or every restart would sign the captain out.

## Consequences

- Good: the authority classes are enforced at one boundary, and the timeline shows which worker did what.
- Bad: each request with a worker token costs one indexed read, and a worker can still read the home token from the home it shares, which the guard keeps from its shell and its `Read` ([pre-tool-guard](pre-tool-guard.md)).

## When to revisit

When workers run as another user or on another machine, or the core serves more than one person.

## How we got here

- 15/10/2026: slice S1 served the API under one home token, since only the primary called it.
- 16/10/2026: slice S3 gave each worker its own token and checked every call against its caller.
- 17/10/2026: slice S7 added the captain's console session; slice S8 added standby sessions.
- 27/10/2026: the console session may change the personality, as the primary may (EDI-98).
- 28/10/2026: the default port became 5490, outside the ephemeral ranges, and a taken port stops the core with a message that names it and `SERVER_PORT` (EDI-119).
