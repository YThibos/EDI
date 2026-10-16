# ADR-080: Every forge call for a project runs under the account its registry entry binds

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** On the laptop `gh` is logged in to two GitHub accounts, and on 14/10/2026 two deliveries failed because the active account decided which one a call used (section 13.3).
`forge.account` in the project registry binds every forge call for that project to one account; `gh auth token --user <account>` gives that account's token at call time, and secrets are read at call time and never printed (I-32).
Git transport should use the same account through a per-repository credential helper, as the captain's own pinning tool does.
`.github/workflows/` changes need the `workflow` scope, and EDI checks it before a push that touches workflows instead of failing at the push.
`glab` holds one login per host, so it cannot choose an account per call.

**Decision:** A project's `forge` entry in `projects.yaml` is `{ kind, host, path, account }`; the host defaults to `github.com` for GitHub, and the account is a name of letters, digits, dots, dashes and underscores.
`GitHubForge` reads the bound account's token with `gh auth token --hostname <host> --user <account>` before each call and passes it to that call alone as `GH_TOKEN`, with prompts disabled; the token never enters an argument, a log or an event.
`ForgeCalls` withholds an inherited `GH_TOKEN`, `GITHUB_TOKEN` and their enterprise forms from every forge call, so a token exported in the core's environment can never stand in for the bound account.
`IsolatedCopies` binds the project clone's git transport for `https://<host>` to a repository-local credential helper that asks `gh` for the bound account's token at call time, after an empty entry that clears inherited helpers; linked worktrees share it.
`GitLabForge` checks with `glab api user` that the logged-in user is the bound account before it opens a draft, and refuses otherwise; reads run under the host's single login.
`PublicationTargets` refuses a push from an isolated copy whose outgoing commits touch `.github/workflows/` while the bound GitHub account lacks the `workflow` scope, naming the `gh auth refresh` the captain runs.

**Motivation:** The active `gh` account never decides who acts, so an account switch in another terminal cannot break or misdirect a delivery.

**Alternatives considered:**
- `gh auth switch` before each call - it changes the captain's own terminal state.
- Binding GitLab reads too - `glab` cannot pick an account, and a read under another login can only fail, never act.

**Consequences:**
- Good: deliveries on the work and personal accounts no longer depend on which one is active.
- Bad: every GitHub call costs one extra local `gh auth token`; the validation pipeline's own pushes do not use this binding, see `docs/verification/no-mistakes.md`.

**Revisit when:** `glab` supports several accounts per host, or the pipeline accepts a token per run.

**Sources:** EDI rewrite specification, sections 8.4, 13.3 and I-32 (backlog `fo-nm-gh-account-pin`); `domain.delivery.RegisteredForge`, `adapter.forge.GitHubForge`, `GitLabForge`, `adapter.registry.RegisteredForgeEntry`, `application.dispatch.IsolatedCopies`, `adapter.git.Git.bindCredentials`, `application.publish.PublicationTargets`.
