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
| `state/` | the core's port, token, lock, logs and activity ledger, and the commit the installed build was built from |
| `.claude/skills/` | the skills `ops/install.sh` links in |

The session-start digest lists any of the machine profile, the personal configuration, the registry, the dispatch rules, the craftsmanship rules and the three startup memory files that the home still lacks.

## The machine profile

`config/machine.yaml` describes this machine:

- `workers` lists the models workers may run, strongest first, the tools they never get and their permission mode.
- `claude.logins` names each Claude login a project's workers may run on, with its configuration directory (see [Claude logins](#claude-logins)).
- `supervision.staleAfter` and `supervision.pauseResurface` tune the supervision round, PT20M and PT4H by default, and `supervision.questionReminder` sets how often a worker's unanswered question is raised to the primary again, PT10M by default (see [Workers](workers.md)).
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
- its `tracker`, `{ kind: jira|edi|github|none, projectKey: KEY }`;
- its `login`, the Claude login its workers run on, `default` when the entry names none (see [Claude logins](#claude-logins));
- `craftRules: true` when its briefs carry the craftsmanship rules;
- `nativeBuild`, the command of its local native build, when its briefs say when a change needs one ([local-native-builds](decisions/local-native-builds.md)).
- `ciRuns: false` when its forge's CI does not run, so its validation pipeline runs skip the CI step as well as the rebase step every run skips ([pipeline-steps](decisions/pipeline-steps.md)).

`edi project list` shows a project's login when it is not `default`.
A registry whose entry names a login the machine profile does not name is refused, naming the entry and the login.

## Claude logins

A Claude login is one Claude Code configuration directory: its credentials, its global configuration with the trust entries, its user settings, its skills and its transcripts ([claude-logins](decisions/claude-logins.md)).
The login named `default` is Claude Code's own directory, `~/.claude` with `~/.claude.json` beside it, unless the machine profile names a directory for it.
Every other login is named in the machine profile with its directory, and a registry entry runs its project's workers on it:

```yaml
# config/machine.yaml
claude:
  logins:
    hobby: { configDirectory: ~/.claude-hobby }

# config/projects.yaml
projects:
  - name: hobby-tool
    login: hobby
```

Every launch states its worker's directory itself: `CLAUDE_CONFIG_DIR` set in the worker's window for a named directory, and unset in the worker's command for Claude Code's own.
A `CLAUDE_CONFIG_DIR` exported in the shell that starts the core or the workers' tmux server decides nothing; to move the `default` login, name its directory under `claude.logins.default`.
The trust entry of a launch goes into the global configuration of the launch's login, and EDI writes nothing else into a login's directory.

A dispatch or a relaunch onto a login that is not ready is refused before a copy, a branch or a window exists.
A login is ready when `claude auth status` exits 0 for its directory and its global configuration, `.claude.json`, exists and records its first run as finished.
The refusal names the login, what it lacks and the command that fixes it.

A second login is set up once, by hand:

1. Finish its first run: `CLAUDE_CONFIG_DIR=~/.claude-hobby claude`, sign in, choose the theme and pass the first screens; a directory logged in without that run still stops a worker on the theme picker.
2. Link your own skills into it one by one, such as `ln -s ~/.claude/skills/no-mistakes ~/.claude-hobby/skills/no-mistakes`; never link the `skills` folder itself, because Claude Code keeps a folder of each account's own in it.
3. Link your user settings: `ln -s ~/.claude/settings.json ~/.claude-hobby/settings.json`, which carries the bypass-permissions acceptance, your permissions and your hooks.
4. Name the login in the machine profile and on the projects that run on it in the registry, as above.

`~/.claude/CLAUDE.md` needs no link: Claude Code loads it as project memory for any working directory under the home, whichever directory the session runs on.
A relaunch does not yet stay on the login it was launched on: until it does, a relaunch of a worker on another login than `default` starts fresh, with a handover, on the project's login.

## The dispatch rules

`config/dispatch-rules.json` holds the dispatch categories, each with the model and effort it runs on, and the project overrides ([dispatch-routing](decisions/dispatch-routing.md)).
A project override's `categories` names the categories it covers, and a task of any other category routes by its category; an override without `categories` covers every category.
The `pipeline` entry names the model and effort of the validation pipeline's agent, and EDI writes it into the pipeline's own configuration at every worker launch ([pipeline-agent](decisions/pipeline-agent.md)).
An effort in the file is `low`, `medium` or `high`; only the captain's word recorded on one task with `--for effort-above-high` lifts that task to `xhigh` or `max`, through `edi dispatch --override-effort`, never through the file.
`"researchFirst": true` on `implementation-unplanned` or `bugfix` plans that work in a research task first (see [Trackers and time](trackers-and-time.md)).

## The personal configuration

`config/personal.yaml` holds the address word EDI uses for the captain and the presence settings: `presence.autoQuietAfter` (PT15M; PT0S never turns quiet on silence, while a meeting still does), `presence.quietBatch` (PT30M) and `presence.calendarIgnore`, the calendar entries that never mark a meeting.

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
| `EDI_CHECKOUT` | `~/EDI` | the EDI repository `helloedi`, `edi up` and `edi update` use |
| `EDI_CORE` | `edi-core` next to `edi` | the core executable |
| `EDI_BIN` | `~/.local/bin` | where `ops/install.sh` installs the executables |
| `SERVER_PORT` | `5490` | the core's port |

The core's own setting `edi.pipeline.home` names the validation pipeline's home when it is not `~/.no-mistakes`, and `edi.workers.claude-home` names the home directory whose `.claude` and `.claude.json` are Claude Code's own directory when it is not the user's home.
