# The core listens on loopback only, and every caller is told apart by its token or the captain's console session

**Kind:** Architecture

## Context

The core's API changes the fleet and answers the captain's decisions, and the captain's word is the authority for destructive and irreversible acts (I-06).
Any page open in the captain's browser could otherwise submit requests to a local port.
Workers run as the same operating-system user as the primary, so a token tells callers apart; it is not a secret from the user who owns the home.

## Decision

The core binds `127.0.0.1` only, on the machine profile's port (default 47318), and writes the bound port to `state/core.port`.
At each start it draws a home token of 32 random bytes, writes it owner-only to `state/core.token` and keeps it in memory (`ApiToken`).
At each launch it draws a worker token, writes it owner-only to the worker's state directory, and keeps only its SHA-256 digest on the worker's row (`WorkerTokens`); the worker's window carries the file's path in `EDI_TOKEN_FILE`, never the token.
`ApiTokenFilter` decides on the decoded servlet path: the home token is `Caller.Primary`, or `Caller.Standby` when another session holds the primary role ([primary-role](primary-role.md)); a live worker's token is `Caller.WorkerCaller`; anything else gets a 401, and only `/api/health` and the console's files stay open.
Controllers take the `Caller`: `requirePrimary` refuses a worker the commands that add, hold, steer, dispatch or answer, and `requireWorker` refuses the primary `edi status` and the inbox; every event records its real actor.
The captain signs in to the console with a one-time link: `ConsoleAccess` issues 32 random bytes, keeps only their digest, and a link signs in once within two minutes; `POST /api/console/session` with the header `X-EDI-Console: 1` answers with the cookie `EDI-Console`, HttpOnly, SameSite=Strict and twelve hours long.
A request with an open session's cookie and no bearer token is `Caller.Captain`, which may read, answer decisions and ask for re-checks; a console request that changes state without the header gets a 403, and minting a link needs the home token.

## Why

No port is reachable from outside the machine, a confused or misled worker cannot act as the primary through the API, and no other page in the browser can submit as the captain.
A token never appears in a command line, a tmux environment or a log, because only its file path travels, and a link that works once for two minutes is safe to pass through a terminal.

## Alternatives rejected

- No authentication until workers exist, or Spring Security - an open port that changes state, or a framework for one header comparison.
- Trusting `EDI_ROLE` from the environment, or the token in the tmux environment - any process could claim to be any worker, and `tmux show-environment` shows it.
- The home token in the console, or a session in memory only - any script on the page could act as the primary, or every restart would sign the captain out.

## Consequences

- Good: the authority classes are enforced at one boundary, and the timeline shows which worker did what.
- Bad: each request with a worker token costs one indexed read, and a worker can still read the home token from the home it shares, which the guard keeps from its shell ([pre-tool-guard](pre-tool-guard.md)).

## When to revisit

When workers run as another user or on another machine, or the core serves more than one person.

## How we got here

- 15/10/2026: slice S1 served the API under one home token, since only the primary called it.
- 16/10/2026: slice S3 gave each worker its own token and checked every call against its caller.
- 17/10/2026: slice S7 added the captain's console session; slice S8 added standby sessions.
