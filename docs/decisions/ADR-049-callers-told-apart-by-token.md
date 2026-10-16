# ADR-049: Each worker holds its own token, and every call is checked against its caller

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-042 let one home token authenticate every caller until workers existed, and asked to revisit it when slice S3 issues per-worker tokens.
Section 9.1 puts every command in an authority class: read for anyone, worker for the worker of that task, primary for the primary session, hook for Claude Code hooks.
Workers run as the same operating-system user as the primary, so a token tells callers apart; it is not a secret from the user who owns the home.

**Decision:** At every launch the core draws a worker token of 32 random bytes, writes it owner-only to `state/workers/<task>-<n>/token` in the EDI home and keeps only its SHA-256 digest, in the worker's row (`WorkerTokens`).
The worker's tmux window carries the path of that file in `EDI_TOKEN_FILE`, never the token itself, and `edi` reads the token from that file when the variable is set and from `state/core.token` otherwise.
`ApiTokenFilter` turns the home token into the caller `Caller.Primary` and a token whose digest belongs to a worker that is not retired into `Caller.WorkerCaller`; any other token gets a 401.
Controllers take the `Caller` as an argument: `requirePrimary` refuses a worker with a 403 for `edi task add`, `task note`, `hold`, `release`, `decision raise` and `answer`, `dispatch`, `steer` and the migration dry run, and `requireWorker` refuses the primary for `edi status`, `inbox` and `inbox ack`.
Read commands, `edi publish-check` and `edi hook` stay open to both; a hook from a worker answers for that worker, and a hook from the primary is only guarded (ADR-058).
Every event records its real actor: `primary`, `worker:<task>#<n>` or `core`.

**Motivation:** A worker can report and read its own inbox but cannot add, hold, steer or dispatch work, so a confused or misled worker cannot act as the primary through the API.
The token never appears in a command line, a tmux environment or a log, because only its file path travels.

**Alternatives considered:**
- Trusting `EDI_ROLE` and `EDI_TASK_ID` from the caller's environment - any process could claim to be any worker.
- Passing the token in the tmux environment - visible to `tmux show-environment` and in the tmux client's arguments.
- Keeping tokens in memory only - a restarted core would no longer know its live workers.

**Consequences:**
- Good: the authority classes are enforced at one boundary, and the timeline shows which worker did what.
- Bad: each request by a token other than the home token costs one indexed read of the store.
- Bad: a worker can still read the home token from the home it shares with the primary; the guard keeps its file writes inside its copy (ADR-058), but a shell command is not contained.

**Revisit when:** Workers run as another user or on another machine, or the captain's console session arrives with slice S7.

**Sources:** EDI rewrite specification, sections 9.1 and 16; ADR-020, ADR-042; `application.worker.WorkerTokens`, `web.access.Caller`, `CallerArgument`, `ApiTokenFilter`, `AccessConfiguration`, `edi-cli` `Edi.tokenFile`.
