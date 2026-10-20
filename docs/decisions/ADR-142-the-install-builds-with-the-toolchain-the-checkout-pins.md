# ADR-142: The install builds with the toolchain the checkout pins, and refuses when it is not installed

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** `edi update` runs `ops/install.sh` in the checkout, which runs `./mvnw -Pnative verify` (ADR-130), and the native build needs a GraalVM as its `JAVA_HOME` (ADR-005).
The checkout's `mise.toml` pins GraalVM Community Edition (ADR-003), and `mise install` installs it.
`edi update` hands the install the caller's environment, and a shell outside the checkout carries the machine's own Java: from a shell in the home directory, a Temurin JDK was `JAVA_HOME`, and the native build stopped with "native-image is not installed in your JAVA_HOME".
The `edi-update` skill leaves machine tools to the captain.

**Decision:** `ops/install.sh` runs the build through `mise exec` in the checkout, so the build sees the toolchain the checkout's `mise.toml` pins, whatever the caller's directory and `JAVA_HOME`.
Before it builds, it refuses with exit code 1 and a message that names the checkout and `mise install` when mise is not on the `PATH`, or when `mise ls --local --missing` lists a pinned tool that is not installed; it never installs a tool itself.
`edi update` runs the script as before and reports its failure as for any failed install.

**Motivation:** The toolchain belongs to the checkout, so the script that builds the checkout chooses it, and `edi update` and `ops/install.sh` run by hand build with the same toolchain as CI, whose `jdx/mise-action` reads the same `mise.toml`.

**Alternatives considered:**
- `edi update` resolves the toolchain and hands the script its `JAVA_HOME` - `ops/install.sh` run by hand from another directory would still build with the caller's Java, and the command would have to know how the checkout builds.
- Letting `mise exec` install a missing tool - the update would download a JDK unasked, and machine tools are the captain's.
- Running `edi update` only from a shell in the checkout - the primary runs it from the EDI home, and nothing would stop the next call from elsewhere.

**Consequences:**
- Good: the install builds with the pinned GraalVM from any directory, and a missing toolchain is named before minutes of building and testing.
- Bad: the install needs mise on the `PATH` of whoever runs it, even where the caller's own Java would have done.

**Revisit when:** Releases are published as built executables (ADR-130), or the toolchain is pinned somewhere other than `mise.toml`.

**Sources:** ADR-003, ADR-005, ADR-130; `ops/install.sh`, `mise.toml`; `edi-cli` `InstallTest`.
