# Tools EDI drives

EDI is built only from open-source, standard tools, and drives the external programs below as they are; Claude Code is the only proprietary piece ([open-source-tools](decisions/open-source-tools.md)).
Each program is started only from the adapter that owns it ([ports-and-architecture-rules](decisions/ports-and-architecture-rules.md)), and [docs/verification](verification/) records what was verified on each one and on which version.

| Program | What EDI uses it for | Verified |
|---|---|---|
| `git` | clones, isolated copies, branches, hooks and the publish check's view of outgoing commits | - |
| `tmux` | the primary session, on the captain's default tmux server, and the workers, on EDI's own server `tmux -L edi` ([worker-sessions](decisions/worker-sessions.md)) | - |
| `claude` | Claude Code, for the primary session and every worker | [claude-code.md](verification/claude-code.md) |
| `gh` | drafts, merges and checks on GitHub, under the project's bound account | [gh.md](verification/gh.md) |
| `glab` | the same on GitLab | [glab.md](verification/glab.md) |
| `no-mistakes` | the validation pipeline a ship's worker runs before its draft opens; EDI reads its state and sets its agent's model and effort and the work key of its commits and titles | [no-mistakes.md](verification/no-mistakes.md) |
| `lavish-axi` | research boards, read through `lavish-axi poll` | [lavish.md](verification/lavish.md) |
| `gitleaks` | the publish check's secret scan | - |
| `powershell.exe`, `cmd.exe` | on WSL, Windows notifications, the Outlook calendar and opening the console in the browser | [windows.md](verification/windows.md) |

The JIRA tracker is reached over its REST API, not through a program ([jira.md](verification/jira.md)).
The assumptions the rewrite was built on, and how each was verified, are in [assumptions.md](verification/assumptions.md).

## The forge adapters

The forge adapters run an allow-list of calls, and every call for a project runs under the account its registry entry binds ([forge-adapters](decisions/forge-adapters.md)).

## Keeping the tools current

The `edi-upkeep` skill checks the versions of the external tools, and Renovate keeps EDI's own dependencies and pinned toolchain current (see [Development](development.md#dependency-upkeep)).
