# One core runs per home, started by edi up, stopped by edi down, and replaced by edi update once the old one lets go

**Kind:** Design

## Context

`edi up` builds nothing: it starts the installed native core when it is not running and then the primary session in the captain's own tmux; exactly one core runs per home.
A worker keeps running in its own tmux window while the core is down, and a restart is a non-event (I-23).
The native build needs the GraalVM the checkout pins, while a shell outside the checkout carries the machine's own Java.
A stopping core stops answering at once but keeps its lock while in-flight requests and scheduled rounds finish, and the primary always holds an `edi wait` open for up to 25 seconds.
A process inherits the working directory of whatever started it, so a core restarted from a shell inside a project's checkout on a mounted filesystem stood in that checkout for hours, where it pinned the mount and would have kept the checkout from being moved; Java itself has no way to change a process's working directory.

## Decision

The core works in its home from the moment it starts, wherever it was started from: `EdiCoreApplication.main` registers `MoveIntoTheHome`, which, once the environment is prepared and before any bean, reads `edi.home`, makes it absolute and keeps that value for the rest of the start, creates the home for its owner alone when it is missing, and moves the process into it through a `chdir` downcall of the Foreign Function and Memory API (JEP 454, `OwnWorkingDirectory`); a core that cannot move refuses to start.
Only `main` moves, and not while Spring's ahead-of-time processing runs it to build the native image, so neither a test's Spring context nor the build moves its JVM or creates a home.
The native image of the core is built with `-H:+ForeignAPISupport`, unlocked as the experimental Native Image option it still is, `--enable-native-access=ALL-UNNAMED`, and the `chdir` downcall registered in a `reachability-metadata.json` of its own, beside the one Spring's ahead-of-time processing writes for the core and would otherwise replace.
Every child the core starts is given its working directory explicitly.
`edi health` reads the core's working directory from `/proc/<pid>/cwd`, for the process `state/core.pid` records, and names it when it is not the home, so a core started before it moved itself is seen; the core stays healthy, and it moves home when it restarts.
`CoreInstanceLock` takes an exclusive lock on `state/core.pid` and writes the core's process id before the store or the token is touched, so a second core on the same home fails to start, naming the file.
`edi up` starts `edi-core` detached with `setsid -f`, its output appended to `state/logs/core.log`, waits up to a minute for its health, asks for the primary's launch ([primary-startup-context](primary-startup-context.md)) and opens a tmux session of that name in the EDI home running `claude` with no prompt submitted, so it waits for the captain to type, and sets `set-titles` on and `set-titles-string` to `#S` for that session only, so the terminal's title is the session name (`PrimaryWindow`, [opening-turn](opening-turn.md)); `--core-only` stops after the core.
`edi down` asks the core to stop: `StopTheCore` refuses while workers run, or with `--force-stop-workers` needs the captain's word on the home and stops each recorded worker ([captains-word](captains-word.md)); a stopping core takes no new work but lets a running round finish.
A core counts as stopped once it has let go of `state/core.pid`: `CorePidFile.isHeld` tries the same lock, and `LocalCore.awaitItsStop` waits for it for at most 90 seconds, naming the process and file when it runs out.
`edi update` refuses a checkout that is not a clean clone, pulls fast-forward only, and runs `ops/install.sh`, which builds with `./mvnw -Pnative verify` through `mise exec` in the checkout, refusing first when mise or a pinned tool is missing, and installs `edi`, `edi-core` and the `helloedi` launcher in `EDI_BIN` and the skills' links into the EDI home, recording the commit it built in `state/installed-build`.
On success it stops the running core without stopping a worker, waits for it to let go of the home, starts the new core, and names the commit and the anchor to re-read; on failure the installed EDI and the running core stay as they were.
`helloedi`, from `ops/helloedi`, is the captain's way in: it checks that tmux, the checkout and `edi` are there, fetches the checkout, runs `edi update` from inside it when the recorded build does not yet contain `origin/main`, and ends in `edi up`; a failed fetch or update is reported and the installed EDI starts, and Ctrl-C stops it.
It updates EDI only, never the machine's own tools.
`ops/edi-core.service` is an optional systemd user unit that restarts the core on failure.

## Why

The command owns what the core cannot do for itself, starting the core and opening the captain's terminal, while every decision about workers stays in the core.
The lock is the very thing a new core checks, so waiting on it removes the race between stop and start instead of narrowing it.
The toolchain belongs to the checkout, so the script that builds the checkout chooses it, wherever it is run from.
EDI stands alone, so its launcher ships and installs with it, and the installed build, not the checkout's `HEAD`, says whether EDI is current: a `git pull` without an install leaves the checkout ahead of what runs.

## Alternatives rejected

- The core opening the primary's tmux session - the core drives only EDI's own server.
- The port alone as the one-core guard - a second core on another port would overwrite the token and port files.
- Waiting until the core stops answering, or until its recorded pid exits - the lock is still held, or the pid may be another program's after a reboot.
- Building inside the running core, or installing a JVM build only - the core would replace itself, or a broken native image would reach the machine.
- Letting `mise exec` install a missing tool - the update would download a JDK unasked.
- A shell function the captain's shell set-up sources - EDI would depend on a repository outside it, and a function works in one shell only.
- Comparing the checkout's `HEAD` with `origin/main` - a pulled but uninstalled checkout reads as current while an older build runs.
- Updating whenever the installed build is not exactly `origin/main` - a build from a branch ahead of it would be rebuilt at every start.
- Updating the machine's tools in the launcher - they belong to the machine's own set-up, not to EDI.
- Only the commands starting the core in its home - a core started by hand or by the systemd unit would still stand where it was started.
- Re-executing the core in its home - a second start-up, and a new process id for the systemd unit and the commands that wait for it.
- A JNI library for `chdir` - C code and a native library to build and ship for one call the Foreign Function and Memory API makes from Java.

## Consequences

- Good: one command takes EDI from a merged change to a running core, and a core that does not stop is named with the way back.
- Good: the core never pins a mount or a checkout as its working directory, and no child it starts inherits one.
- Bad: the native image needs an experimental Native Image option for one downcall, and Java's own `user.dir` can still name where the core was started, which is why the home is made absolute before the move.
- Bad: a core started without systemd is not restarted after a crash, a restart takes as long as the old core's shutdown, and the native build takes minutes.

## When to revisit

When releases are published as built executables, or something other than the command starts and stops the core.

## How we got here

- 17/10/2026: slice S8 built `edi up`, `edi down` and `edi update`.
- 19/10/2026: a native build failed from a shell outside the checkout, so the install builds through `mise exec`.
- 22/10/2026: `edi update` started the new core while the old one still held the home, leaving none running, so a stop is awaited on the lock (EDI-36).
- 23/10/2026: the primary opens with the captain's opening prompt already submitted (EDI-43).
- 27/10/2026: at the captain's request the primary opens with no prompt submitted and waits for the captain to type (EDI-101).
- 27/10/2026: EDI ships its own `helloedi`, which updates EDI when the installed build does not yet contain `origin/main` (EDI-102).
- 28/10/2026: at the captain's request the primary's tmux session titles the terminal with its name, so the terminal can tell an EDI session from any other (EDI-108).
- 13/11/2026: a core restarted from a shell inside a project's checkout kept standing there, so the core moves into its home at start and `edi health` names a core that works elsewhere (EDI-271).
