# ADR-042: One home token authenticates the API until callers are told apart

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-020 requires a bearer token on every API call, scoped per caller: the primary, each worker and each hook role.
In slice S1 only one kind of caller exists: the primary session or the captain's shell, through the `edi` command; workers arrive with slice S3 and hooks with slice S4.

**Decision:** At start-up the core draws a token of 32 random bytes from `SecureRandom`, writes it base64url-encoded to `$EDI_HOME/state/core.token` with owner-only permissions (`rw-------`, written to a temporary file and moved into place), and keeps it in memory (`ApiToken`).
`ApiTokenFilter` admits a call under `/api/` only with `Authorization: Bearer <token>`, compared in constant time; `/api/health` and the console stay open, and a refusal is a 401 with one plain sentence.
The command reads the port and the token from the home before each call (`CoreConnection`).
Every event a token holder causes is recorded with the actor `primary`.
Slice S3 adds the scoped tokens of workers and hooks, and the authority classes of section 9.1 then decide what each caller may do; until then every token holder may call every S1 command, including `edi decision answer`, whose captain-word evidence arrives with slices S4 and S8.

**Motivation:** No other local user and no browser page can call the API, and the mechanism the scoped tokens extend is in place from the first endpoint.

**Alternatives considered:**
- No authentication until workers exist - an open port that changes state.
- Spring Security - a framework for one header comparison.

**Consequences:**
- Good: a restarted core issues a fresh token, and the command picks it up on its next call.
- Bad: any process of the same user can read the token file, which is why scoped tokens must arrive before workers do.

**Revisit when:** Slice S3 issues per-worker tokens.

**Sources:** EDI rewrite specification, sections 7.3, 9.1 and 16; ADR-020; `web.access.ApiToken`, `ApiTokenFilter`, `AccessConfiguration`, `edi-cli` `CoreConnection`.
