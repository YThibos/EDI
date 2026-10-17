<p align="center">
  <img src="docs/images/EDI-face.jpg" alt="EDI" width="640">
</p>

# EDI

EDI (Enhanced Development Intelligence) is a development harness for one developer across machines.
It gives the developer one conversational interface, launches Claude Code workers in isolated git worktrees, supervises them from structured events, and delivers their work as draft merge or pull requests that the developer merges.

The repository is at slice S6, tracker and time, on top of the delivery of slice S5, the supervision and recovery of slice S4, the workers of slice S3, the reports of slice S2, the domain and store of slice S1 and the foundations of slice S0.
The core keeps tasks, captain holds, decisions with their D numbers and an event log in its SQLite store and serves them over its authenticated API; the `edi` command reads and changes the backlog, and plans the import of the old harness's backlog as a dry run.
One fleet snapshot sorts the open work into decisions waiting on the captain, ongoing, parked and queued work, and `edi report` and `edi sitrep` print it as the boxed captain report.
`edi dispatch` routes a task by its dispatch category, makes its isolated copy on a keyed branch from the fresh origin base, writes its brief, and launches a Claude Code worker on EDI's own tmux server with its own token, its hooks and attribution off.
Workers report with `edi status` and read `edi inbox`; `edi steer` delivers a message at the worker's next stop or its idle prompt and proves it arrived; a pre-tool guard refuses pattern kills, tmux without a socket, forced or base-branch pushes and merges; each copy's `commit-msg` hook refuses agent trailers and subjects without the work key.
The primary and the workers answer every Claude Code hook: the session the captain typed in last is the primary and any other stands by, the primary's turn ends with the captain report when it changed state and with an `edi wait` running while work is in flight, and `edi wait` prints what needs the primary as soon as it happens.
A supervision round judges each worker from its hooks, its tmux server and the machine's boot, parks one that stopped on a usage limit and resumes it after the reset, and puts two or more dead workers on a numbered outage hold list that `edi recover` answers one number at a time, resuming the recorded session in the task's own copy.
A ship's brief names the exact validation pipeline call with the intent EDI wrote, craft rules included, and its done counts only once its draft is open, still a draft and at the copy's head, and its branch adds no SNAPSHOT version; work delivered to the captain's checkout arrives by a ref-only fetch or, on request, as an uncommitted patch.
EDI watches every open draft, follows a merge the forge reports by refreshing the clone before any follow-up work and cleaning up the task once its work is landed, and refuses any push from an isolated copy to a repository its project does not register.
Every task gets its work key at intake: the next EDI-native key of its project, a ticket the captain chooses or has created through a decision on a JIRA project, or the key it was given once its tracker confirms it; a JIRA ticket then joins the active sprint when work starts, links the draft, and moves to the after-merge status once the merge is followed.
EDI writes the activity ledger the time-booking tool books from, asks workers for the time they worked while the machine profile says so, and can plan unplanned work in a research task before an implementation worker builds it, measuring that pilot against unplanned ships until the captain decides.

## Parts

- `edi-core`: the Spring Boot application that owns EDI's state, listens on `127.0.0.1` only and serves the JSON API and the EDI console.
- `edi-cli`: the `edi` command every actor uses to talk to the core, compiled to a native executable.
- `edi-console`: the Angular single-page application the core serves.

## Build

The toolchain is pinned in `mise.toml`: GraalVM Community Edition for Java 25, the Node that builds the console, and gitleaks.

```sh
mise install
./mvnw verify               # the JVM build and every test
./mvnw test -Pfast          # domain, application and architecture tests only
./mvnw -Pnative verify      # both native executables, then the native smoke test
```

`./mvnw verify` reports the invariant catalogue's acceptance tests that later slices make pass as skipped.

## Run

```sh
EDI_HOME=/tmp/edi-home edi-core/target/edi-core      # after the native build
EDI_HOME=/tmp/edi-home edi-cli/target/edi health
```

The core keeps its store, configuration and state in `EDI_HOME`, default `~/.edi`, listens on port 47318 unless `SERVER_PORT` says otherwise, records the port it bound in `$EDI_HOME/state/core.port`, and writes a fresh API token to `$EDI_HOME/state/core.token` at every start; the command reads both.

## Use

```sh
edi project list                                    # the registry in $EDI_HOME/config/projects.yaml
edi task add --kind ship --project EDI --title "Fit the lamp" --key EDI-14
edi backlog --ready                                 # the queue, or only what nothing holds or blocks
edi task show fit-the-lamp                          # a task id, or the D number of its decision
edi hold fit-the-lamp --kind deferral --reason "after the reports" --until 2026-11-01
edi release fit-the-lamp
edi decision raise fit-the-lamp --card - < card.json   # prints the decision's D number
edi decision answer D58 --option A --text "D58, A"     # the captain's words, verbatim
edi decision list --open
edi report --turn - < turn.json                     # the boxed captain report that ends a turn
edi sitrep                                          # the same report, read-only and without a turn
edi publish-check --range origin/main..HEAD         # the publish guard over outgoing commits
edi migrate firstofficer --from /path/to/old/home --dry-run
```

Every command prints text, or the core's JSON with `--json`; a refusal is one sentence on standard error with exit code 1.
A card is `{"title", "context", "options": [{"key", "text"}], "recommend": {"key", "why"}}`.
A turn is `{"done": [{"projects", "text"}], "next": [{"projects", "text"}], "tasks": {"<task>": {"text", "state", "projects"}}}`, every part optional; inside tmux the report fits the pane it runs in.
`examples/edi-home/config/projects.yaml` shows the registry's shape with placeholder values.

### Workers

A dispatch reads three files of the EDI home besides the registry: `config/machine.yaml` lists the models workers may run, strongest first, the tools they never get and their permission mode; `config/dispatch-rules.json` holds the dispatch categories and the project overrides; and `memory/craft-rules.md` holds the craftsmanship rules for projects with `craftRules: true`.
`examples/edi-home/config/` has an example of each, and a project's clone lives at `$EDI_HOME/projects/<name>` unless its registry entry names another.

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

### Supervision and recovery

The primary session runs with `claude/primary/settings.json`, whose hooks call `edi hook <event>`; `config/machine.yaml` may set `supervision.staleAfter` and `supervision.pauseResurface`, PT20M and PT4H by default.

```sh
edi wait                                            # the primary, in the background: exits printing what needs it
edi worker relaunch fit-lamp                        # resume a dead or stopped worker's session in its own copy
edi worker stop fit-lamp                            # signal the processes EDI recorded for the worker
edi recover                                         # put dead workers on the numbered outage hold list and show it
edi recover 2 resume                                # answer one number, as the captain named it
edi recover 4 fresh --effort medium
```

A call made inside a Claude Code session names it, so a standby session's changes are refused; `edi wait` serves only the primary.

### Delivery

A project's registry entry names its forge, `{ kind: github|gitlab, host, path, account }`, and every forge call for it runs under that account; its `delivery` is `draft-review` (the default) or `local-branch` with a `captainCheckout`, and one task may ask for `--delivery local-apply`.
`gh` and `glab` must be logged in, the validation pipeline must be installed for draft reviews, and `edi.pipeline.home` names the pipeline's home when it is not `~/.no-mistakes`.

```sh
edi task add --kind ship --project EDI --title "Fit the lamp" --key EDI-14 --delivery local-apply
edi deliver fit-the-lamp                            # open the draft from the pushed branch when the pipeline could not
edi merged fit-the-lamp                             # the captain merged it: check the forge, refresh the clone, clean up
edi merged https://github.com/owner/repo/pull/7     # the same, by the draft's address
edi cleanup fit-the-lamp                            # retire a landed task's copy, branch and workers
```

`edi task show` names a ship's draft, its latest pipeline run and, once cleaned up, what it cost; a cleanup that finds unlanded work lists every uncommitted path and unlanded commit and changes nothing.

### Trackers and time

A project's registry entry names its tracker, `tracker: { kind: jira|edi|none, projectKey: KEY }`; the machine profile's `tracker` section says whether this machine reaches a JIRA instance (`kind: jira`, with its `baseUrl`, the keys file section holding the access token, the evergreen tickets, the workflow's statuses in their forward order, the `mergedStatus`, the `issueType` and the `epicLinkField`) or none (`kind: none`), and its `time` section switches the activity ledger (`fleetLedger`) and the brief's `worked:` line (`requireWorkedLine`).
`examples/edi-home/config/machine.yaml` shows a profile with JIRA; a machine without JIRA or time booking sets `tracker.kind: none` and leaves `time` out.

```sh
edi task add --kind ship --project hobby-tool --title "Fit the lamp"           # the next EDI-native key, such as HOBBY-4
edi task add --kind ship --project example-service --title "Fit the lamp" \
    --issue-type Story --epic EXAMPLE-9 --labels lighting --priority Major     # a decision: an open ticket or the drafted one
edi decision answer D59 --option B --text "D59 B"                              # the captain's answer creates and fills the ticket
edi dispatch fix-horn --category bugfix --cause-unknown                         # research first, when the dispatch rules switch it on
```

With `"researchFirst": true` on `implementation-unplanned` or `bugfix` in `config/dispatch-rules.json`, a ship's first dispatch adds and launches a research scout that writes the plan, and the ship waits for it; dispatching the ship again once the scout is done runs it as `implementation` with the plan in its brief.
The activity ledger is `$EDI_HOME/state/fleet-ledger.jsonl`.

## Documentation

- [AGENTS.md](AGENTS.md): the rules for agents changing EDI.
- [docs/decisions](docs/decisions/README.md): every architecture and design decision, with its why.
- [docs/verification](docs/verification/): what was verified on the external tools EDI drives, and on which version.
