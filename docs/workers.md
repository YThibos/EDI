# Workers

A worker is a Claude Code session that does one task in its own isolated copy of the project.
EDI dispatches it, steers it, supervises it and recovers it; the worker reports back through the `edi` command and never addresses the captain.

## Dispatch

`edi dispatch` routes a task by its dispatch category, makes its isolated copy on a keyed branch from the fresh origin base, writes its brief, and launches a Claude Code worker on EDI's own tmux server with its own token, its hooks and attribution off ([ADR-025](decisions/ADR-025-dispatch-categories.md), [ADR-051](decisions/ADR-051-branch-names-from-the-work-key.md), [ADR-052](decisions/ADR-052-isolated-copies.md), [ADR-054](decisions/ADR-054-the-claude-launcher.md), [ADR-055](decisions/ADR-055-the-worker-brief.md)).
A dispatch reads the machine profile, the registry, the dispatch rules and the craftsmanship rules from the EDI home (see [Configuration](configuration.md)).
A dispatch beyond a project's `capacity` waits until a worker place on that project comes free ([ADR-060](decisions/ADR-060-project-capacity-defers-dispatches.md)).

```sh
edi brief fit-the-lamp --category implementation --spec - < spec.md   # the brief a dispatch would write
edi dispatch fit-the-lamp --category implementation --spec - < spec.md # isolated copy, brief, worker
edi dispatch rename-lamp --category mechanical --spec - --files src/Lamp.java < change.md
edi worker list                                     # serving workers, their route and last status
edi worker peek fit-the-lamp                        # the worker pane's last lines and what EDI reads in them
edi steer fit-the-lamp --resolves lamp-choice < answer.md
edi status working -- tests pass                    # a worker reporting, under the token EDI gave it
edi inbox                                           # a worker reading its messages
edi inbox ack 1
```

The worker's copy is `$EDI_HOME/worktrees/<project>/<task-id>`, its brief and report are in `$EDI_HOME/data/tasks/<task-id>/`, and its tmux window is `<task-id>` in session `workers` on `tmux -L edi`.

## Reporting and steering

Workers report with `edi status` and read `edi inbox`.
`edi steer` delivers a message at the worker's next stop or its idle prompt and proves it arrived ([ADR-056](decisions/ADR-056-steering-with-proof-of-delivery.md)).
A worker's `needs-decision` or `blocked` is addressed to EDI, so the sitrep page shows its task as with EDI, and once the worker acknowledges the steer that resolves its key the question no longer shows ([ADR-159](decisions/ADR-159-a-workers-question-is-edis-and-its-acknowledged-steer-settles-it.md)).

## Guards

- A pre-tool guard refuses pattern kills, tmux without a socket, forced or base-branch pushes and merges ([ADR-058](decisions/ADR-058-the-pre-tool-guard.md)), and keeps secrets from a shell ([ADR-119](decisions/ADR-119-the-guard-also-keeps-secrets-from-a-shell.md)).
- A mechanical task's done counts only once every file it changed is one its dispatch names and the project's `verify` command passes; otherwise an implementation worker takes over in the same copy ([ADR-059](decisions/ADR-059-the-mechanical-task-guard.md)).
- Each copy's `commit-msg` hook refuses agent trailers and subjects without the work key ([ADR-053](decisions/ADR-053-hooks-through-worktree-configuration.md)).
- EDI refuses any push from an isolated copy to a repository its project does not register ([ADR-086](decisions/ADR-086-publication-targets-are-the-registered-forge.md)).

## Supervision

A supervision round judges each worker from its hooks, its tmux server and the machine's boot ([ADR-061](decisions/ADR-061-the-supervision-round.md), [ADR-070](decisions/ADR-070-worker-health-from-structured-state.md), [ADR-071](decisions/ADR-071-the-boot-id-marks-a-machine-restart.md)).
It parks a worker that stopped on a usage limit and resumes it after the reset ([ADR-072](decisions/ADR-072-usage-limits-from-the-stop-failure-and-the-transcript.md)).
The machine profile's `supervision` section sets when an idle worker has waited too long and when a paused one resurfaces.

## Recovery

Two or more dead workers go on a numbered outage hold list that `edi recover` answers one number at a time, resuming the recorded session in the task's own copy ([ADR-073](decisions/ADR-073-relaunch-resumes-the-recorded-session.md), [ADR-074](decisions/ADR-074-the-outage-hold-list.md), [ADR-118](decisions/ADR-118-the-outage-hold-list-also-drops-a-task-on-the-captains-word.md)).

```sh
edi worker relaunch fit-lamp                        # resume a dead or stopped worker's session in its own copy
edi worker stop fit-lamp                            # signal the processes EDI recorded for the worker
edi recover                                         # put dead workers on the numbered outage hold list and show it
edi recover 2 resume                                # answer one number, as the captain named it
edi recover 4 fresh --effort medium
edi recover 5 drop                                  # drop an outage entry's task, on the captain's word
```
