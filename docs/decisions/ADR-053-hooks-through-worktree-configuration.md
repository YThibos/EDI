# ADR-053: The commit-msg and pre-push hooks are installed through each worktree's own configuration

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Every EDI-created worktree installs a `commit-msg` hook that rejects `Co-authored-by`, a `Signed-off-by` naming a model, agent, Anthropic or tool, and "Generated with" lines (I-09), and a subject without the task's key (I-10).
Section 13.1 adds a `pre-push` hook beside it that runs `edi publish-check` on the outgoing commits (I-38), which ADR-032 places in slice S3.
The old harness installed its hooks path through `GIT_CONFIG_*` environment variables, which drop inherited configuration such as a repository's scoped credential helper; U-11 asks for per-worktree git configuration instead.

**Decision:** `WorktreeHooks` writes both hooks from `edi-core`'s `git-hooks/` templates into `edi-hooks/` inside the worktree's own git directory, readable and executable by their owner only, sets `extensions.worktreeConfig` on the clone and `core.hooksPath` with `git config --worktree` on the worktree, and does so again at every launch.
The `commit-msg` hook is POSIX shell with the work key filled in: the first line that is not a comment must be the key, the key and a space, or the key and a colon; any `Co-authored-by` line, any `Signed-off-by` line naming one of a list of models, agents and tools (Claude, Anthropic, OpenAI, Copilot, an agent, a bot, EDI and others), and any line that opens with "Generated with" after symbols are refused with one line on standard error.
The `pre-push` hook checks each pushed ref from the remote's commit, or from `origin/<base>` for a new branch, with `edi publish-check --range`, and refuses the push when `edi` is missing or the check fails.
`FixtureRepository` also clears `GIT_CONFIG_COUNT`, `GIT_CONFIG_KEY_*`, `GIT_CONFIG_VALUE_*` and `GIT_CONFIG_PARAMETERS`, because such variables outrank any repository configuration and would replace the hooks under test.

**Motivation:** The hooks live and die with the worktree, nothing in the working tree carries them, and every other setting of the clone still applies, which AT-09 proves with a scoped credential helper that still answers.
A shell hook keeps working when the core is down, so a commit is never blocked by EDI's availability.

**Alternatives considered:**
- `GIT_CONFIG_*` variables in the worker's environment - drops inherited configuration (U-11) and does not reach the captain's own commits in the copy.
- A hook calling the core - a commit would fail whenever the core is down.
- A hooks directory inside the working tree - the worker would see it as untracked files.

**Consequences:**
- Good: AT-09 and AT-10 run real commits in a real worktree.
- Bad: an environment that exports `GIT_CONFIG_*` with `core.hooksPath` overrides the hooks; EDI's own launches never do.

**Revisit when:** git changes how worktree configuration is enabled, or a project needs hooks of its own beside EDI's.

**Sources:** EDI rewrite specification, sections 4.2 (I-09, I-10), 13.1 and 21.3 (U-11); ADR-032; `application.dispatch.WorktreeHooks`, `edi-core/src/main/resources/git-hooks/`, `testing.FixtureRepository`.
