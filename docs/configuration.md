# Configuration

Everything that differs between machines or people lives in the EDI home, never in the repository ([machine-and-person-specifics](decisions/machine-and-person-specifics.md)).
[examples/edi-home/](../examples/edi-home/) shows each file but the publish deny-list with placeholder values, the memory files included.

## The EDI home

| Path under `$EDI_HOME` | Holds |
|---|---|
| `config/machine.yaml` | the machine profile |
| `config/projects.yaml` | the project registry |
| `config/dispatch-rules.json` | the dispatch categories, the project overrides and the validation pipeline's agent |
| `config/personal.yaml` | the personal configuration |
| `config/publish-denylist` | the terms EDI never publishes |
| `memory/` | the captain's memory and the craftsmanship rules |
| `projects/<name>` | a project's clone, unless its registry entry names another |
| `worktrees/<project>/<task-id>` | a task's isolated copy |
| `data/tasks/<task-id>/` | a task's brief and report |
| `state/` | the core's port, token, lock, logs and activity ledger |
| `.claude/skills/` | the skills `ops/install.sh` links in |

The session-start digest lists any of the machine profile, the personal configuration, the registry, the dispatch rules, the craftsmanship rules and the three startup memory files that the home still lacks.

## The machine profile

`config/machine.yaml` describes this machine:

- `workers` lists the models workers may run, strongest first, the tools they never get and their permission mode.
- `supervision.staleAfter` and `supervision.pauseResurface` tune the supervision round, PT20M and PT4H by default (see [Workers](workers.md)).
- `tracker` says whether this machine reaches a JIRA instance or none, and `time` switches the activity ledger and the brief's `worked:` line (see [Trackers and time](trackers-and-time.md)).
- `memory.startupBudgetTokens` caps the startup memory, 18,000 by default.
- `notify.windowsToast: true` raises Windows notifications while the captain is quiet or away.
- `presence.calendar: outlook` turns presence quiet during a meeting in the classic Outlook calendar (see [Memory and presence](memory-and-presence.md)).
- `console.openSitrepAtSessionStart` opens the console's sitrep page in the browser at every new primary session, on by default; `false` switches it off (see [The console and research boards](console-and-boards.md)).

## The project registry

`config/projects.yaml` lists the projects EDI works on, and `edi project list` prints it ([task-intake](decisions/task-intake.md)).
A project's entry names:

- its `clone`, by default `$EDI_HOME/projects/<name>`;
- its `forge`, `{ kind: github|gitlab, host, path, account }`, and every forge call for the project runs under that account ([forge-adapters](decisions/forge-adapters.md));
- its `delivery`, `draft-review` (the default) or `local-branch` with a `captainCheckout` (see [Delivery](delivery.md));
- its `tracker`, `{ kind: jira|edi|none, projectKey: KEY }`;
- `craftRules: true` when its briefs carry the craftsmanship rules;
- `nativeBuild`, the command of its local native build, when its briefs say when a change needs one ([local-native-builds](decisions/local-native-builds.md)).

## The dispatch rules

`config/dispatch-rules.json` holds the dispatch categories, each with the model and effort it runs on, and the project overrides ([dispatch-routing](decisions/dispatch-routing.md)).
A project override's `categories` names the categories it covers, and a task of any other category routes by its category; an override without `categories` covers every category.
The `pipeline` entry names the model and effort of the validation pipeline's agent, and EDI writes it into the pipeline's own configuration at every worker launch ([pipeline-agent](decisions/pipeline-agent.md)).
An effort in the file is `low`, `medium` or `high`; only the captain's word recorded on one task with `--for effort-above-high` lifts that task to `xhigh` or `max`, through `edi dispatch --override-effort`, never through the file.
`"researchFirst": true` on `implementation-unplanned` or `bugfix` plans that work in a research task first (see [Trackers and time](trackers-and-time.md)).

## The personal configuration

`config/personal.yaml` holds the address word EDI uses for the captain and the presence settings: `presence.autoQuietAfter` (PT15M), `presence.quietBatch` (PT30M) and `presence.calendarIgnore`, the calendar entries that never mark a meeting.

Its `edi-personality:` section holds the personality EDI speaks with: humour, formality, verbosity, warmth, directness, initiative, depth, enthusiasm and flavour, each from 0 to 10, and under `demo:` the levels a demo overrides ([personality](decisions/personality.md)).
A scale the section leaves out takes its Balanced level, and a demo override left out is formality 7, humour 3, verbosity 5 and flavour 7.
Without the section EDI speaks Balanced and offers the presets in the next session.
`edi personality` shows the personality, and `/personality` changes it in the primary session, as the console's personality desk does ([console-and-boards](console-and-boards.md)); the core rewrites only the section and keeps the rest of the file as written.

```sh
edi personality                           # the personality, the address word and the bad-news levels
edi personality humour 2 formality 10     # changes two scales and keeps the change
edi personality plain                     # chooses a preset: balanced, plain or demo
```

## The memory files

`memory/profile.md`, `memory/machine.md` and `memory/learnings.md` are the captain's memory, loaded into the primary's system prompt at every start.
`memory/archive.md` is their cold tier, where `/stow` retires what no longer needs loading, and it is never loaded.
`memory/craft-rules.md` holds the craftsmanship rules for projects with `craftRules: true`.

## The publish deny-list

`config/publish-denylist` holds one term per line, matched whatever its case; a line that starts with `#` is a comment.
The publish check refuses outgoing commits that carry any of them (see [Delivery](delivery.md#the-publish-check)).

## Environment

| Variable | Default | Names |
|---|---|---|
| `EDI_HOME` | `~/.edi` | the EDI home |
| `EDI_CHECKOUT` | `~/EDI` | the EDI repository `edi up` and `edi update` use |
| `EDI_CORE` | `edi-core` next to `edi` | the core executable |
| `EDI_BIN` | `~/.local/bin` | where `ops/install.sh` installs the executables |
| `SERVER_PORT` | `47318` | the core's port |

The core's own setting `edi.pipeline.home` names the validation pipeline's home when it is not `~/.no-mistakes`.
