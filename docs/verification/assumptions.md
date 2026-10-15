# The specification's assumptions, verified

Section 3.3 of EDI's rewrite specification lists the assumptions EDI is built on.
Slice S0 verified each one on the laptop on 15/10/2026; the desktop was not reachable from that machine.
An assumption that could not be verified was raised as a decision for the captain.

| # | Assumption | Outcome | Evidence |
|---|---|---|---|
| 1 | Both machines run WSL2 Ubuntu with systemd, so a user service can run the core. | Verified on the laptop; raised for the desktop. | WSL2 kernel 6.18, Ubuntu 24.04.5 LTS; process 1 is `systemd`, `/etc/wsl.conf` sets `[boot] systemd=true`, and `systemctl is-system-running` answers `running`. |
| 2 | Java 27 as GraalVM Community Edition, and Maven 3.9 or later, through mise on both machines; fall back to Java 25 only if no native toolchain exists for 27, and say so. | Not met for Java 27; the specification's fallback applies (ADR-003), raised for confirmation. Maven verified. | JDK 27 is generally available since 15/09/2026, but no GraalVM Community build exists on it (the newest is on JDK 25.0.4.1.1), Spring Boot 4.1.1 supports Java up to 26, and mise lists `graalvm-community` up to 25.0.2. EDI pins `graalvm-community-25.0.2` and Maven 3.10.0 through the wrapper. |
| 3 | Claude Code keeps its hook events, flags and JSON hook input. | Verified on Claude Code 2.1.300. | [claude-code.md](claude-code.md). |
| 4 | The validation pipeline stays an external tool with `axi run`, `axi respond` and `axi status --run`, opening draft merge requests on GitLab and draft pull requests on GitHub. | Verified on version 1.84.0. | The three commands exist with those flags. Drafts are configuration: `providers.github.draft_pull_requests: true` in this repository's `.no-mistakes.yaml` makes this slice's own pull request a draft. |
| 5 | The research-board tool stays external, and the sitrep board moves into the EDI console. | Verified. | The board tool's command answers with version 0.1.88; the console module exists (ADR-018). |
| 6 | The time-booking tool stays the only owner of worklog booking. | Not verifiable by a machine check; raised for confirmation as a statement of the settled design. | EDI contains no booking code and only emits the activity ledger in a later slice. |
| 7 | The EDI repository is private, owned by the captain's GitHub account, and holds no personal or employer data. | Private and ownership verified; the absence of personal data was checked for tracked files and raised for commit metadata. | `gh` reports the repository as private on the expected account. A scan of every tracked file for the laptop's employer names, hosts, account names and home paths finds nothing. Commit author identities come from the machine's git configuration and are raised as a decision. |
| 8 | The Java namespace `io.github.ythibos.edi` and the Maven group `io.github.ythibos`, which the captain may replace in S0. | Verified and kept. | The GitHub account that owns the repository is the namespace's basis (ADR-008). |
| 9 | No second captain, no machine beyond the two, and no state shared between the machines' EDI homes except the repository. | Not verifiable by a machine check; raised for confirmation as a scope statement. | Nothing in S0 shares state between homes. |
