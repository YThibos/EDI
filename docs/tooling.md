# Tools EDI drives

EDI is built only from open-source, standard tools, and drives the external programs below as they are; Claude Code is the only proprietary piece ([ADR-029](decisions/ADR-029-open-source-standard-tools.md)).
Each program is started only from the adapter that owns it ([ADR-009](decisions/ADR-009-architecture-rules-as-tests.md)), and [docs/verification](verification/) records what was verified on each one and on which version.

| Program | What EDI uses it for | Verified |
|---|---|---|
| `git` | clones, isolated copies, branches, hooks and the publish check's view of outgoing commits | - |
| `tmux` | the primary session, on the captain's default tmux server, and the workers, on EDI's own server `tmux -L edi` ([ADR-013](decisions/ADR-013-interactive-workers-in-tmux.md)) | - |
| `claude` | Claude Code, for the primary session and every worker | [claude-code.md](verification/claude-code.md) |
| `gh` | drafts, merges and checks on GitHub, under the project's bound account | [gh.md](verification/gh.md) |
| `glab` | the same on GitLab | [glab.md](verification/glab.md) |
| `no-mistakes` | the validation pipeline a ship's worker runs before its draft opens; EDI only reads its state | [no-mistakes.md](verification/no-mistakes.md) |
| `lavish-axi` | research boards, read through `lavish-axi poll` | [lavish.md](verification/lavish.md) |
| `gitleaks` | the publish check's secret scan | - |
| `powershell.exe`, `cmd.exe` | on WSL, Windows notifications, the Outlook calendar and opening the console in the browser | [windows.md](verification/windows.md) |

The JIRA tracker is reached over its REST API, not through a program ([jira.md](verification/jira.md)).
The assumptions the rewrite was built on, and how each was verified, are in [assumptions.md](verification/assumptions.md).

## The forge adapters

The forge adapters run an allow-list of calls ([ADR-079](decisions/ADR-079-the-forge-adapters-run-an-allow-list.md)), and every call for a project runs under the account its registry entry binds ([ADR-080](decisions/ADR-080-forge-calls-run-under-the-bound-account.md)).

## Keeping the tools current

The `edi-upkeep` skill checks the versions of the external tools, and Renovate keeps EDI's own dependencies and pinned toolchain current (see [Development](development.md#dependency-upkeep)).
