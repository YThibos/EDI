# ADR-134: The core writes the primary's status line, and edi statusline pipes it to Claude Code

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The captain asked for a status bar that stays visible in the primary's Claude Code session, shaped after the captain's own status line script.
Its fields come in this order, separated by ` -- `: model and effort, context used, session tokens with the cached and uncached split, the 5-hour and the weekly usage limit each with its reset as weekday and 24-hour time, the branch of the current project with a mark for uncommitted changes, and the cache hit ratio last.
Labels are muted cyan, secondary text grey, nothing bright; every percentage is shaded dark green at 0 through dark amber at 50 to dark red at 100, the cache hit ratio the other way, and an absent value reads `--`.
Claude Code runs a `statusLine` command with the session's JSON on standard input, after events and on its `refreshInterval`, and shows the output only when the command exits 0 ([docs/verification/claude-code.md](../verification/claude-code.md)).
The command parses no JSON and the core renders text (ADR-043), and only `adapter.git` runs git (ADR-009).

**Decision:** `edi statusline` posts the statusLine JSON to `/api/statusline` and prints the core's line; while the core is not answering, it prints why and exits 0, so the bar says so instead of going blank.
`StatusLinePayload` reads the session from the payload (`ClaudeSessionStatus`, `SessionTokens`, `UsageLimit`), `StatusLineQuery` adds the checked-out branch of `workspace.current_dir`, or `cwd`, from one `git status --porcelain=v2 --branch` (`Git.checkedOutBranchAt`), and `StatusLineText` writes the line in 24-bit colour (`TerminalColour`, `Shade`) with the reset times in the core's time zone.
Session tokens are the session's input and output tokens; the cached and uncached split and the cache hit ratio come from the last call's `current_usage`, as in the captain's script.
Only the home token may ask for the line.
`claude/primary/settings.json` wires `edi statusline` with a `refreshInterval` of 60 seconds (ADR-135).

**Motivation:** The line is presentation like every other report, so it lives with the presenters and their tests, and the native command stays a pipe that starts in milliseconds.
The refresh keeps the branch and the usage limits current while the session is idle.

**Alternatives considered:**
- Keeping the captain's shell script - it lives outside EDI, needs `jq`, and cannot be tested with the core's presenters.
- Rendering in the command - the command would parse JSON and carry a presenter, which ADR-043 rejects.
- Reading the branch in the command - git would run outside the adapter that owns it.

**Consequences:**
- Good: the field order, the absent values and the colour bands are tested against recorded statusLine payloads, and the branch follows the session's working directory.
- Bad: every refresh costs one call to the core and one git process, and while the core is down the bar shows only why.

**Revisit when:** Claude Code changes the statusLine fields EDI reads, or the captain wants the bar in workers too.

**Sources:** The captain's feature request of 16/10/2026; ADR-009, ADR-043, ADR-135; [docs/verification/claude-code.md](../verification/claude-code.md); `edi-cli` `StatusLineCommand`; `web.statusline.StatusLineController`, `StatusLinePayload`, `StatusLineText`, `TerminalColour`, `Shade`; `application.statusline.StatusLineQuery`, `StatusLine`; `domain.statusline`; `domain.branch.CheckedOutBranch`; `adapter.git.Git`, `PorcelainStatus`; `claude/primary/settings.json`.
