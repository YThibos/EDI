# EDI

EDI (Enhanced Development Intelligence) is a development harness for one developer across machines.
It gives the developer one conversational interface, launches Claude Code workers in isolated git worktrees, supervises them from structured events, and delivers their work as draft merge or pull requests that the developer merges.

The repository is at slice S0, the foundations: the build, the module skeletons, the architecture rules, the publish guard, the invariant catalogue, CI and dependency upkeep.
Nothing launches workers yet.

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

The core keeps its store, configuration and state in `EDI_HOME`, default `~/.edi`, listens on port 47318 unless `SERVER_PORT` says otherwise, and records the port it bound in `$EDI_HOME/state/core.port`.

## Documentation

- [AGENTS.md](AGENTS.md): the rules for agents changing EDI.
- [docs/decisions](docs/decisions/README.md): every architecture and design decision, with its why.
- [docs/verification](docs/verification/): what was verified on the external tools EDI drives, and on which version.
