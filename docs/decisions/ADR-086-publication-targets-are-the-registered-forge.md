# ADR-086: A push from an isolated copy goes only to the repository its project registers

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-39: EDI pushes branches and opens drafts only on the `forge.path` registered for the task's project; a remote, an account's permissions or a repository's ownership never authorise a push elsewhere.
AT-43: a worktree whose `origin` was changed to another repository is refused at push and at `edi deliver`.
Git hands a `pre-push` hook the push URL after any `insteadOf` rewriting, which is where the push really goes.
A refusal never repeats a secret, and a remote URL may carry a token in its user part (I-32).

**Decision:** The `pre-push` hook passes git's push URL to `edi publish-check --remote-url`, and `PublishCheck.checkPush` asks `PublicationTargets` first.
For a repository inside `$EDI_HOME/worktrees/<project>/<task>`, the project must register a forge, and the push URL, read as host and path whatever its scheme (`https`, `ssh`, `git` or the `user@host:path` form, with any user and port dropped and `.git` trimmed), must name the registered host and path, compared without regard to case; otherwise the push is refused, naming the registered repository and the target's host and path only.
`edi deliver` checks the copy's own push URL, `git remote get-url --push origin`, the same way.
A repository outside EDI's worktrees, such as a captain's own checkout, keeps the publish guard alone.

**Motivation:** A worker, a tool or a stray command that points a copy elsewhere cannot publish EDI's work there, and the check needs nothing but the registry.

**Alternatives considered:**
- Checking the configured `remote.origin.url` - a rewrite would carry the push elsewhere unseen.
- Trusting the account's permissions - I-39 forbids inferring a target from them.

**Consequences:**
- Good: AT-43 refuses both paths, and the refusal prints no credential.
- Bad: a project whose clone talks to a mirror must register the mirror as its forge.

**Revisit when:** A project publishes to more than one repository.

**Sources:** EDI rewrite specification, I-32, I-39 and U-01 (AT-43); ADR-032, ADR-053; `application.publish.PublicationTargets`, `PublishCheck.checkPush`, `PublishGuard`, `domain.delivery.RegisteredForge.requirePublicationAt`, `RemoteAddress`, `ForgeRepository.isAt`, `git-hooks/pre-push`, `edi-cli` `PublishCheckCommand`.
