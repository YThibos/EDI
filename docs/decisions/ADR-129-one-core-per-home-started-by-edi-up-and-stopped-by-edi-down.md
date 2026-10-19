# ADR-129: One core per home, started by edi up and stopped by edi down

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 7.3: `edi up` builds nothing; it starts the installed native `edi-core` as a background process if it is not running, waits for its health endpoint, then starts the primary session in tmux on the captain's own server; exactly one core runs per home, holding a lock on `state/core.pid`; `edi down` stops the core only when no worker runs, or with `--force-stop-workers` after the captain's word, and never kills a worker it did not record.
A worker keeps running in its own tmux window while the core is down, and a restart is a non-event (I-23).
The core writes a fresh token and opens the store at every start.

**Decision:** `CoreInstanceLock` takes an exclusive lock on `state/core.pid` and writes the core's process id before the store and the token are touched (`@DependsOn`), and a second core on the same home fails to start, naming the file.
`edi up` asks the core's health; when it does not answer, the command starts `edi-core` (from `EDI_CORE`, beside the `edi` executable, or on the PATH) detached with `setsid -f`, its output appended to `state/logs/core.log`, and waits up to a minute for it to answer.
It then asks the core for the primary's launch (ADR-121) and opens a tmux session of that name in the EDI home running `claude --settings ... --append-system-prompt-file ...` (`PrimaryWindow`): attached in a plain terminal, created detached and switched to when it runs inside tmux; `--core-only` stops after the core.
`edi down` asks the core to stop (`POST /api/core/stop`): `StopTheCore` refuses while workers run, counting only workers whose window runs and that have not handed their work over (`WorkerPlaces.running`), or with `--force-stop-workers` needs the captain's word on the home (ADR-115) and stops each of those through `StopWorker`, which signals only the processes EDI recorded; `CoreExit` then exits the core after its answer, and the command waits until the core no longer answers.
A stopping core takes no new work but lets a follower run or a scheduled round already under way finish (`EventFollowers.close`, `spring.task.scheduling.shutdown.await-termination`), so nothing writes to the store once the core has stopped.
`ops/edi-core.service` is an optional systemd user unit that restarts the core on failure; `edi up` finds such a core already running.

**Motivation:** The command owns what the core cannot do for itself, starting the core and opening the captain's terminal, while every decision about workers stays in the core.

**Alternatives considered:**
- The core opening the primary's tmux session - the core drives only its own `edi` tmux server, never the captain's.
- Relying on the port alone to keep one core per home - a second core on another port would overwrite the first one's token and port files.

**Consequences:**
- Good: a captain's `hellofo` alias can become `edi up`, and the native lifecycle test starts, refuses a second and stops a real core.
- Bad: a core started by `edi up` without systemd is not restarted if it crashes; `edi up` starts it again.

**Revisit when:** The captain's terminal starts EDI some other way, or systemd becomes the only way the core runs.

**Sources:** EDI rewrite specification, section 7.3, I-06, I-23, I-30; ADR-115, ADR-121; `CoreInstanceLock`, `CoreExit`, `EdiHome`, `application.follow.EventFollowers`, `adapter.store.StoreConfiguration`, `web.access.AccessConfiguration`, `application.lifecycle.StopTheCore`, `CoreStop`, `application.dispatch.WorkerPlaces`, `application.worker.StopWorker`, `web.lifecycle.CoreController`, `edi-cli` `UpCommand`, `DownCommand`, `LocalCore`, `LocalPrograms`, `PrimaryWindow`, `PrimaryLaunch`, `ops/edi-core.service`.
