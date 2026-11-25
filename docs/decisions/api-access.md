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
The worker's window also carries `EDI_ROLE=worker`, `EDI_WORKER` and `EDI_TASK_ID`; where any of them is set, the `edi` command reads its token only from `EDI_TOKEN_FILE` and refuses, without calling the core, when that names no file, instead of falling back to the home token, and the core-down allowance of the pre-tool hook stays the primary's alone.
The pre-tool guard refuses the forms it reads of a worker's shell command that unsets or reassigns any of those four variables, or starts a command without them ([pre-tool-guard](pre-tool-guard.md)).
The core keeps a primary launch key of 32 random bytes owner-only in `state/primary-launch.key`, drawn once and kept across restarts (`PrimaryLaunchKey`), and hands it only to `edi up` through `POST /api/primary/launch`; `edi up` opens the primary's tmux session with it in `EDI_PRIMARY_LAUNCH_KEY` and with the home in `EDI_HOME`, so the primary never leans on the fallback wherever it runs, and `edi` sends it from there in the `EDI-Primary-Launch-Key` header.
`edi` never reads the key from the home, and `LocalPrograms.startDetached` leaves it out of the core's environment, so neither the core nor a worker it launches carries it.
`ApiTokenFilter` decides on the decoded servlet path: the home token with the launch key is `Caller.Primary`, or `Caller.Standby` when another session holds the primary role ([primary-role](primary-role.md)); the home token without it is `Caller.Terminal`; a live worker's token is `Caller.WorkerCaller`; anything else gets a 401, and only `/api/health` and the console's files stay open.
`Caller.requirePrimary` refuses a `Caller.Terminal` every command that changes the fleet, under `access.primary-session`, before anything is stored: a session start, every primary hook but the tool guard, a typed prompt, `edi wait`, a presence change, adding a task, a plan or a decision, answering one, every steer, drop, hold, dispatch, stop, relaunch, delivery and other change of the primary's, and `edi console`, whose sign-in link carries the captain's authority.
A `Caller.Terminal` keeps `edi up`, `edi health`, `edi down` and the reads, which `Caller.requireThePrimaryOrTheTerminal` lets through, and the tool guard still answers it; it also sends the firings of the rules the command applies, `POST /api/rules/fired`, which record a refusal and change nothing in the fleet.
Its refusal names the way back for a primary session opened before its launch key existed: reopening it with `edi up`, or resuming it with `edi up --resume` and its session id.
`edi` refuses to fall back to `~/.edi` when `EDI_HOME` is unset or blank and it runs inside `~/.edi/worktrees`, under `~/.no-mistakes`, or under a test runner, which `EDI_TEST_RUN`, set by the build's test JVMs, or another runner's own variable shows (`DefaultHome`); it names the reason and asks for `EDI_HOME`, and records no firing, since the only home it could write to is the one it refuses.
Controllers take the `Caller`: `requirePrimary` refuses a worker the commands that add, hold, steer, dispatch or answer, and `requireWorker` refuses the primary `edi status` and the inbox; every event records its real actor.
The captain signs in to the console with a one-time link: `ConsoleAccess` issues 32 random bytes, keeps only their digest, and a link signs in once within two minutes; `POST /api/console/session` with the header `X-EDI-Console: 1` answers with the cookie `EDI-Console-<port>`, named by the port the core serves, HttpOnly, SameSite=Strict and twelve hours long.
A browser keeps cookies by host and not by port, so a cookie named by the port keeps another core on `127.0.0.1`, such as a validation's throwaway core, from replacing the console's session when the captain signs in to it.
A call with a console cookie the core does not admit, because the session ended or another sign-in replaced it, gets a 401 that says so and names `edi console` as the way to sign in again, and the core logs each refused change with its reason.
A request with an open session's cookie and no bearer token is `Caller.Captain`, which may read, answer decisions, ask for re-checks, set presence to present, quiet, away with the mandate its form takes, or a demo, and change the personality; a console request that changes state without the header gets a 403, and minting a link needs the primary session's launch key.
`LoopbackHostFilter` runs before every other filter: a request whose `Host` is neither `127.0.0.1:<port>` nor `localhost:<port>`, for the port it reached, gets a 421 naming the two, and every response, a refusal included, carries `Content-Security-Policy: frame-ancestors 'none'` and `X-Frame-Options: DENY`.

## Why

No port is reachable from outside the machine, a confused or misled worker that drops its token file in a command the guard reads gets a refusal instead of the primary's authority, and no other page in the browser can submit as the captain.
Refusing in the client as well as in the guard means a worker must both clear every marker of its window and get past the guard before `edi` would use the home token; it raises the cost of a mistake, and does not stop a worker that sets out to do it.
A token never appears in a command line, a tmux environment or a log, because only its file path travels, and a link that works once for two minutes is safe to pass through a terminal.
A script, a test or a validation that reaches the real home, through a fallback or a home variable it was handed, holds the home token but not the launch key, so it cannot start or feed a primary session, change presence, add work or change anything else in the fleet, as one did on 13 November 2026, when a validation's end-to-end run started a made-up primary session, took the role, consumed the captain's return brief, switched presence and added a task with an open decision.
A page that reaches the core through DNS rebinding names its own host, so it gets a refusal instead of the console's files, and no page, not even one on another loopback port that is same-site and gets the console's cookie, can frame the console to steer the captain's click.
Port 5490 spells E-D-I by the letters' places in the alphabet, so the captain remembers the console's address, and it lies below both the Linux ephemeral range (32768-60999 by default) and the Windows dynamic range (49152-65535), which a WSL machine with mirrored networking shares, so an outgoing connection never holds it when the core starts.

## Alternatives rejected

- No authentication until workers exist, or Spring Security - an open port that changes state, or a framework for one header comparison.
- Trusting `EDI_ROLE` from the environment, or the token in the tmux environment - any process could claim to be any worker, and `tmux show-environment` shows it.
- Refusing the home token alone everything - the captain's terminal runs `edi up`, `edi health` and `edi down` with it, and reads the fleet.
- Keeping every other command for the captain's terminal, `edi dispatch` among them - a script or a test that reaches the real home could then steer, drop, hold, dispatch or stop the captain's workers; the captain dispatches from the primary session.
- Adopting a primary session opened before the launch key existed as launched - nothing tells it from a session started elsewhere, so the captain reopens it with `edi up` or resumes it with `edi up --resume`.
- A launch key drawn at every core start - `edi update` restarts the core under a running primary session, which would lose the key it was opened with.
- Recording the default-home refusal in the home's spool - it would write to the very home the run must not touch.
- Port 47318 - hard to remember, and inside the Linux ephemeral range, so an outgoing connection could hold it at start.
- A port from the machine profile, or a random port - the console's address would differ between machines or starts, and `SERVER_PORT` already moves it where 5490 is taken.
- The home token in the console, or a session in memory only - any script on the page could act as the primary, or every restart would sign the captain out.

## Consequences

- Good: the authority classes are enforced at one boundary, and the timeline shows which worker did what.
- Good: a task's whole record, its report, its brief, its board rounds and its worker's live pane reach the captain's console session and the primary, never a worker (`Caller.requireThePrimaryOrTheCaptain`).
- Bad: each request with a worker token costs one indexed read, and a worker can still read the home token from the home it shares, which the guard keeps from its shell and its `Read` ([pre-tool-guard](pre-tool-guard.md)).
- Bad: the Host check stops only a browser misled by DNS; any program on the machine names `127.0.0.1` freely, and a page on another loopback port still sends the console's cookie with its requests, which only the console header and the browser's same-origin policy keep from changing state or reading an answer.
- Bad: workers run as the captain's own user, so the client's refusal and the guard are a denylist, not a boundary: a script file the guard does not open, a shell builtin it does not read as an assignment, such as `read`, `printf -v` or a `for` loop, or a program that starts a command in a clean environment, such as `sudo` or a container runtime, can still clear `EDI_TOKEN_FILE` and every marker of the window together, and `edi` then falls back to the home token; only workers running as another user would close that (EDI-137, issue 96).
- Bad: the launch key sits in the primary's tmux environment, where `tmux show-environment` and any process the primary starts read it, and in the arguments of the `tmux new-session` that `edi up` runs, where `ps` reads it for as long as that client stays attached; it keeps an accident out, not a process that sets out to read it.
- Bad: after the update that brings the launch key, the running primary session has none, so its hooks, its wait and its additions are refused until the captain reopens it with `edi up` or resumes it with `edi up --resume`; `edi update` says so in its report.

## When to revisit

When workers run as another user or on another machine, or the core serves more than one person.

## How we got here

- 15/10/2026: slice S1 served the API under one home token, since only the primary called it.
- 16/10/2026: slice S3 gave each worker its own token and checked every call against its caller.
- 17/10/2026: slice S7 added the captain's console session; slice S8 added standby sessions.
- 27/10/2026: the console session may change the personality, as the primary may (EDI-98).
- 28/10/2026: the default port became 5490, outside the ephemeral ranges, and a taken port stops the core with a message that names it and `SERVER_PORT` (EDI-119).
- 28/10/2026: a worker's window no longer falls back to the home token when its token file variable is unset, and the guard keeps a worker's shell from unsetting or reassigning the variables that say who it is (EDI-122).
- 29/10/2026: the core refuses a request addressed to any host but its own loopback address and port, and forbids every page to frame it (EDI-126).
- 30/10/2026: the record says what the worker-identity guard reads and no more, after a review found forms it missed (EDI-138).
- 03/11/2026: the captain's console session may read a task's report and brief as a page, as the primary may, and no worker may (EDI-57).
- 03/11/2026: the console may set quiet and away too, away only with the words its form takes (EDI-64).
- 04/11/2026: a task's whole record, `GET /api/tasks/<id>`, is read by the captain's console session and the primary, and refused to a worker (EDI-59).
- 06/11/2026: a worker's live pane, `GET /api/tasks/<id>/live`, streams to the captain's console session and the primary, and is refused to a worker (EDI-60).
- 18/11/2026: a validation's end-to-end run reached the real core through the default home, so `edi` refuses that fallback in copies, validations and test runs, and the core asks the launch key of every act that starts or feeds a primary session or adds work (EDI-278).
- 19/11/2026: on the captain's answer to D138, the home token without the launch key keeps only `edi up`, `edi health`, `edi down`, the reads and the rule firings, `edi up --resume` reopens a primary session with the key, and `edi update` names it when the running primary has none (EDI-278).
- 19/11/2026: `edi console` signs the captain in, so it too needs the primary session, never the home token alone (EDI-278).
- 25/11/2026: the console's cookie is named by the core's port, so a sign-in to another core on the host no longer replaces it, and a refused console session says how to sign in again (EDI-291).
