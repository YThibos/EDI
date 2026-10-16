# EDI

EDI (Enhanced Development Intelligence) is a development harness for one developer across machines.
It gives the developer one conversational interface, launches Claude Code workers in isolated git worktrees, supervises them from structured events, and delivers their work as draft merge or pull requests that the developer merges.

The repository is at slice S3, workers, on top of the reports of slice S2, the domain and store of slice S1 and the foundations of slice S0.
The core keeps tasks, captain holds, decisions with their D numbers and an event log in its SQLite store and serves them over its authenticated API; the `edi` command reads and changes the backlog, and plans the import of the old harness's backlog as a dry run.
One fleet snapshot sorts the open work into decisions waiting on the captain, ongoing, parked and queued work, and `edi report` and `edi sitrep` print it as the boxed captain report.
`edi dispatch` routes a task by its dispatch category, makes its isolated copy on a keyed branch from the fresh origin base, writes its brief, and launches a Claude Code worker on EDI's own tmux server with its own token, its hooks and attribution off.
Workers report with `edi status` and read `edi inbox`; `edi steer` delivers a message at the worker's next stop or its idle prompt and proves it arrived; a pre-tool guard refuses pattern kills, tmux without a socket, forced or base-branch pushes and merges; each copy's `commit-msg` hook refuses agent trailers and subjects without the work key.

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

## Documentation

- [AGENTS.md](AGENTS.md): the rules for agents changing EDI.
- [docs/decisions](docs/decisions/README.md): every architecture and design decision, with its why.
- [docs/verification](docs/verification/): what was verified on the external tools EDI drives, and on which version.
