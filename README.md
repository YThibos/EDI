<p align="center">
  <img src="docs/images/EDI-face.jpg" alt="EDI" width="640">
</p>

# EDI

EDI (Enhanced Development Intelligence) is a development harness for one developer across machines.

## What is EDI and what does she do?

EDI is the one interface the developer talks to for all of their software work, and EDI calls that developer the captain.
The captain talks to one Claude Code session, the primary, and EDI does the rest:

- **Workers in isolated copies.**
  EDI never changes a project in the captain's own checkout.
  Each task gets a Claude Code worker that works on a keyed branch in its own isolated git worktree, briefed by EDI and supervised by it.
- **Scouts and ships.**
  A scout researches or designs and reports back; a ship changes a project and delivers the change.
  A scout's plan becomes a ship only on the captain's go.
- **Decisions.**
  Whatever is the captain's to choose becomes a numbered decision card, such as D58, which waits in every report until the captain answers it, in the chat or in the EDI console.
- **Draft-only delivery.**
  A ship's work is validated, pushed and opened as a draft merge or pull request, or brought into the captain's own checkout on request.
  EDI never merges; the captain merges every change.

Behind the primary runs one local core that keeps every task, decision and event in its own store, watches the workers, recovers them after a crash or a usage limit, and follows each draft until its work has landed.
The captain sees the whole fleet at a glance as a boxed report in the chat or as a live sitrep page in the browser.

## Getting started

### Requirements

EDI runs on Linux and is developed on WSL2 Ubuntu.

- [mise](https://mise.jdx.dev), which installs the toolchain the checkout pins in [mise.toml](mise.toml): GraalVM Community Edition for Java 25, the Node that builds the console, and gitleaks.
- A C compiler and the zlib headers, which GraalVM Native Image needs to build the native executables (`build-essential` and `zlib1g-dev` on Ubuntu).
- `git` and `tmux`.
- [Claude Code](https://github.com/anthropics/claude-code), signed in.
- The GitHub command `gh`, or the GitLab command `glab`, logged in to each account a project's forge names.
- [no-mistakes](https://github.com/kunchenguid/no-mistakes), the validation pipeline that opens a ship's draft.
- Optionally, [lavish-axi](https://github.com/kunchenguid/lavish-axi) for research boards, and on WSL, Windows PowerShell for notifications and the Outlook calendar.

[Tools EDI drives](docs/tooling.md) says what each tool is used for and on which version it was verified.

### Installation

Clone the repository to `~/EDI`, where `edi up` and `edi update` look for it by default, then install:

```sh
cd ~/EDI
mise install
ops/install.sh
```

`ops/install.sh` refuses to run until mise and the pinned toolchain are installed; it builds the native executables and runs every test with that toolchain, installs `edi` and `edi-core` in `~/.local/bin`, and links the skills into the EDI home at `~/.edi`.
Make sure `~/.local/bin` is on the `PATH`.

### Running

```sh
edi up                     # start the core unless it runs, then open the primary session in tmux
edi up --core-only         # start only the core
edi console                # open the EDI console in the browser, signed in
edi health                 # check that the core answers and the primary's edi wait is live
edi down                   # stop the core when no worker runs
edi update                 # pull the repository, build, test and install it, restart the core, re-read the anchor
```

Each new primary session opens the console's sitrep page in the browser, signed in; `console.openSitrepAtSessionStart: false` in the machine profile switches that off.
From then on the captain talks to the primary session, and every actor, the captain included, can use `edi --help` for the full command list.
In the primary session the captain also types these commands, each a skill of the same name:

```text
/sitrep [board]            the situation report, in the chat or as the console's sitrep page
/quiet [off]               no chat text while EDI carries on; off makes the captain present again
/present                   back to present, with a brief of what happened meanwhile
/afk <words>               away, with EDI carrying on within the mandate the words give
/demo [off]                hold present through automatic and calendar quiet for a demo; off ends it
/stow                      curate the captain's memory within its startup budget
/personality [settings]    show the personality EDI speaks with, or change it to a preset or new levels
/update                    update EDI itself
```

[Architecture](docs/architecture.md#the-core-at-run-time) explains where the core keeps its state and how one core runs per home.

#### The EDI console

The permanent EDI dashboard is the EDI console, which the core serves on its own machine at `http://127.0.0.1:<port>/`, where `<port>` is the one `edi health` reports, 47318 by default.
It shows the sitrep page: what needs a decision, what is ongoing and what is charted next, then what landed this week, kept current as the fleet changes.
It signs the captain in through a link that works once, so open it with `edi console` rather than from a bookmark.
It is reachable only on the machine whose core serves it, and only while that core runs.
Research boards are Lavish pages that `lavish-axi` opens on their own, outside the console.
[The console and research boards](docs/console-and-boards.md) describes both.

### First configuration to look at

The EDI home, `~/.edi` by default, holds everything that belongs to this machine and this captain.
[examples/edi-home/](examples/edi-home/) has an example of each file with placeholder values; copy it as a starting point and edit each file:

```sh
mkdir -p ~/.edi && cp -r --update=none examples/edi-home/. ~/.edi/
```

- `config/machine.yaml`, the machine profile: the models workers may run, the tools they never get, supervision timings, the tracker and time booking.
- `config/projects.yaml`, the projects registry: each project's clone, forge and account, base branch, delivery mode and tracker.
- `config/dispatch-rules.json`, the dispatch rules: the categories of work, with the model and effort each runs on, the project overrides, and the model and effort of the validation pipeline's agent.
- `memory/`, the captain's memory (`profile.md`, `machine.md`, `learnings.md`) loaded into the primary at every start, its cold tier `archive.md`, never loaded, and the craftsmanship rules (`craft-rules.md`).
- `config/publish-denylist`, the publish deny-list, which the example leaves out: terms that must never leave the machine in a commit, one per line.
- `config/personal.yaml`, the word EDI addresses the captain by, the personality EDI speaks with and the presence settings.

Then add the allow rule `Bash(edi *)` to the captain's own Claude Code settings; EDI's settings carry hooks and the status line only.
The primary's first session lists any file the home still lacks.
[Configuration](docs/configuration.md) describes every setting, and [Moving from the old harness](docs/migration.md) imports an existing home instead.

## Read more

- [Architecture](docs/architecture.md): the core, the command, the console and the Claude layer, and how the core runs.
- [Configuration](docs/configuration.md): the EDI home, every configuration file and the environment.
- [The backlog, decisions and reports](docs/backlog-and-decisions.md): tasks, holds, decision cards, the captain report and the captain's word.
- [Workers](docs/workers.md): dispatch, steering, guards, supervision and recovery.
- [Delivery](docs/delivery.md): delivery modes, the validation pipeline, drafts and merges, and the publish check.
- [Trackers and time](docs/trackers-and-time.md): work keys, JIRA, the activity ledger and research first.
- [The console and research boards](docs/console-and-boards.md): the sitrep page in the browser and Lavish boards.
- [The Claude layer](docs/claude-layer.md): the primary session, hooks, the status line and the skills.
- [Memory and presence](docs/memory-and-presence.md): the captain's memory and its budget, and present, quiet and away.
- [Moving from the old harness](docs/migration.md): importing the old home and adopting its open tasks.
- [Tools EDI drives](docs/tooling.md): the external programs and where each was verified.
- [Development](docs/development.md): building, testing, CI and dependency upkeep.
- [AGENTS.md](AGENTS.md): the rules for agents changing EDI.
- [claude/](claude/): the primary's anchor and settings, the worker's brief, settings and system prompt, and the skills.
- [Decision records](docs/decisions/README.md): every architecture and design decision, with its why.
- [Verification](docs/verification/): what was verified on the external tools EDI drives, and on which version.

## Credits

EDI stands on the ideas and the work of others.

### Ideas

- [firstmate](https://github.com/kunchenguid/firstmate), the open-source agent harness whose fork was EDI's predecessor.
  EDI is a full rewrite of that fork, keeping the operating model it taught - one conversational interface, explicit authority, refusal over guessing, never losing unlanded work, and durable records - and none of its code.
- [TOON](https://github.com/toon-format/toon), the format Lavish prints its results in; EDI reads it with its own small reader.

### Tools EDI drives

- [Claude Code](https://github.com/anthropics/claude-code), for the primary session and every worker.
- [no-mistakes](https://github.com/kunchenguid/no-mistakes), the validation pipeline every ship runs before its draft opens.
- [Lavish](https://github.com/kunchenguid/lavish-axi), the research boards scouts share with the captain.
- [gh-axi](https://github.com/kunchenguid/gh-axi), which the upkeep skill reads the open Renovate pull requests with.
- [git](https://git-scm.com), [tmux](https://github.com/tmux/tmux), the [GitHub CLI](https://cli.github.com) and the GitLab CLI `glab`.
- [gitleaks](https://github.com/gitleaks/gitleaks), the secret scan of the publish check.

### Built with

- [GraalVM Community Edition](https://www.graalvm.org) and its [Native Build Tools](https://github.com/graalvm/native-build-tools), [mise](https://mise.jdx.dev), [Apache Maven](https://maven.apache.org) and the [Maven Wrapper](https://maven.apache.org/tools/wrapper/).
- [Spring Boot](https://spring.io/projects/spring-boot), [picocli](https://picocli.info), [SQLite](https://sqlite.org) through the [Xerial driver](https://github.com/xerial/sqlite-jdbc), [Flyway](https://github.com/flyway/flyway) and [Jackson](https://github.com/FasterXML/jackson).
- [Angular](https://angular.dev), [RxJS](https://rxjs.dev), [Node.js](https://nodejs.org) through the [frontend-maven-plugin](https://github.com/eirslett/frontend-maven-plugin), and the [Fontsource](https://fontsource.org) packages of Exo 2, Inter and JetBrains Mono.
- [JUnit](https://junit.org), [AssertJ](https://assertj.github.io/doc/), [ArchUnit](https://www.archunit.org), [Vitest](https://vitest.dev) and [JaCoCo](https://www.jacoco.org/jacoco/).
- [Renovate](https://github.com/renovatebot/renovate), [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/) and [SonarQube Community Build](https://www.sonarsource.com/open-source-editions/sonarqube-community-edition/).
