# Workers

A worker is a Claude Code session that does one task in its own isolated copy of the project.
EDI dispatches it, steers it, supervises it and recovers it; the worker reports back through the `edi` command and never addresses the captain.

## Dispatch

`edi dispatch` routes a task by its dispatch category, makes its isolated copy on a keyed branch from the fresh origin base, writes its brief, and launches a Claude Code worker on EDI's own tmux server with its own token, its hooks and attribution off ([dispatch-routing](decisions/dispatch-routing.md), [work-keys-and-branches](decisions/work-keys-and-branches.md), [isolated-copies](decisions/isolated-copies.md), [worker-sessions](decisions/worker-sessions.md), [worker-brief](decisions/worker-brief.md)).
A dispatch reads the machine profile, the registry, the dispatch rules and the craftsmanship rules from the EDI home (see [Configuration](configuration.md)).
A dispatch beyond a project's `capacity` waits until a worker place on that project comes free ([dispatch-routing](decisions/dispatch-routing.md)).
A dispatch whose brief would run past 120 lines is refused, naming its longest section, and `edi brief --category` refuses it the same way ([worker-brief](decisions/worker-brief.md)).

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
`edi steer` delivers a message at the worker's next stop or its idle prompt and proves it arrived ([steering](decisions/steering.md)).
A worker's `done` is refused while a message waits unacknowledged in its inbox ([delivery](decisions/delivery.md)).
A worker's `needs-decision` or `blocked` is addressed to EDI, so the sitrep page shows its task as with EDI, and once the worker acknowledges the steer that resolves its key the question no longer shows ([fleet-snapshot](decisions/fleet-snapshot.md)).

## Guards

- A pre-tool guard refuses pattern kills, tmux without a socket, forced or base-branch pushes and merges, commits, pushes and merges that skip the git hooks, and keeps secrets from a shell and from `Read` ([pre-tool-guard](decisions/pre-tool-guard.md)).
- A mechanical task's done counts only once every file it changed is one its dispatch names and the project's `verify` command passes; otherwise an implementation worker takes over in the same copy ([mechanical-tasks](decisions/mechanical-tasks.md)).
- Each copy's `commit-msg` hook refuses agent trailers and subjects without the work key ([isolated-copies](decisions/isolated-copies.md)).
- EDI refuses any push from an isolated copy to a repository its project does not register ([forge-adapters](decisions/forge-adapters.md)).

## Supervision

A supervision round judges each worker from its hooks, its tmux server and the machine's boot ([supervision](decisions/supervision.md)).
It parks a worker that stopped on a usage limit and resumes it after the reset ([recovery](decisions/recovery.md)).
It reads each working worker's transcript for a full context, which Claude Code reports through no hook, and wakes the primary at once; a steer to such a worker waits in its inbox for the fresh session ([supervision](decisions/supervision.md)).
It reads each busy worker's transcript for a turn that ended while EDI could not take its stop hook, such as during a core outage, marks the worker idle, sends it a message to carry on so the steers that waited reach it, and wakes the primary ([supervision](decisions/supervision.md)).
The machine profile's `supervision` section sets when an idle worker has waited too long, when a paused one resurfaces, and how often an unanswered question is raised again.
A worker's `needs-decision` or `blocked` comes back to the primary as a `question-unanswered` wake every `supervision.questionReminder` (ten minutes) until a steer that resolves its key is sent, except while the captain is away; `edi health` lists each open question with its age.

## Recovery

Two or more dead workers go on a numbered outage hold list that `edi recover` answers one number at a time, resuming the recorded session in the task's own copy ([recovery](decisions/recovery.md)).
A worker whose context is full, or whose session left no transcript, cannot be resumed, so `edi worker relaunch` and `edi recover <n> resume` start it afresh in its own copy and say so; every fresh start writes `handover.md` beside the brief with the branch, the commits, the uncommitted changes, the pipeline, the plan and the last status ([recovery](decisions/recovery.md)).

```sh
edi worker relaunch fit-lamp                        # resume a dead or stopped worker's session in its own copy, or start a full context afresh
edi worker stop fit-lamp                            # signal the processes EDI recorded for the worker
edi recover                                         # put dead workers on the numbered outage hold list and show it
edi recover 2 resume                                # answer one number, as the captain named it
edi recover 4 fresh --effort medium
edi recover 5 drop                                  # drop an outage entry's task, on the captain's word
```
