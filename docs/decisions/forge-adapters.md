# The forge adapters run only an allow-list of gh and glab commands, under the account the registry binds, and push only to the registered repository

**Kind:** Architecture

## Context

Projects live on GitLab on the laptop and GitHub on both machines, so `Forge` has two production implementations.
EDI has no merge capability (I-03), which AT-03b asks to see as command allow-lists without a merge, asserted by a test.
On the laptop `gh` is logged in to two GitHub accounts, and on 14/10/2026 two deliveries failed because the active account decided which one a call used.
EDI pushes and opens drafts only on the repository registered for the task's project, whatever a remote, an account's permissions or ownership say (I-39).
A remote URL may carry a token, and no refusal may repeat a secret (I-32).

## Decision

`ForgeCommand` is the allow-list: `gh auth token`, `gh auth status`, `gh auth git-credential`, `gh pr list`, `gh pr view`, `gh pr create`, `glab api user`, `glab mr list`, `glab mr view` and `glab mr create`, each a program and its fixed leading words.
`ForgeCalls` is the only class in `adapter.forge` that starts a program, and runs only a `ForgeCommand` followed by flags and values; `GitHubForge` and `GitLabForge` read the tools' JSON output, and `Forges` picks one by the project's registered `kind` at the moment of the call.
Creating always passes `--draft`, and a view whose URL does not match the address rebuilt from the registered repository is refused; only an exact `merged` or `MERGED` counts as merged.
A project's `forge` entry is `{ kind, host, path, account }`.
`GitHubForge` reads the bound account's token with `gh auth token --hostname <host> --user <account>` before each call and passes it to that call alone as `GH_TOKEN`, while `ForgeCalls` withholds any inherited `GH_TOKEN` or `GITHUB_TOKEN`; `GitLabForge`, whose tool holds one login per host, checks with `glab api user` that the login is the bound account before it opens a draft.
`IsolatedCopies` binds the clone's git transport to a repository-local credential helper that asks `gh` for the bound account's token at call time.
`PublicationTargets` refuses a push from an isolated copy unless git's push URL, read as host and path whatever its scheme, names the registered host and path, and refuses one that touches `.github/workflows/` while the bound account lacks the `workflow` scope, naming the `gh auth refresh` to run.

## Why

The allow-list makes I-03 a property of the code that a test reads, and one runner means no forge call can bypass it.
The active `gh` account never decides who acts, so an account switch in another terminal cannot break or misdirect a delivery.
A worker, a tool or a stray command that points a copy elsewhere cannot publish EDI's work there, and the check needs nothing but the registry.

## Alternatives rejected

- The forges' HTTP APIs from Java - two more clients with their tokens and pagination, where the captain's tools already hold the logins.
- `gh auth switch` before each call - changes the captain's own terminal state.
- Checking the configured `remote.origin.url` - an `insteadOf` rewrite would carry the push elsewhere unseen.

## Consequences

- Good: AT-03b runs every adapter operation against recording stand-ins and finds no merge or push argument, and AT-43 refuses a copy whose origin points elsewhere.
- Bad: every GitHub call costs one extra local `gh auth token`, a new forge feature means a reviewed allow-list entry, and a project behind a mirror registers the mirror as its forge.
- Bad: the validation pipeline's own pushes do not use this binding ([docs/verification/no-mistakes.md](../verification/no-mistakes.md)).

## When to revisit

When a third forge is registered, `glab` supports several accounts per host, or a project publishes to more than one repository.

## How we got here

- 16/10/2026: slice S5 built both adapters on the allow-list, bound every call to the registry's account after the failed deliveries of 14/10/2026, and refused pushes to any other repository.
- 21/10/2026: the adapters learnt to list every request from a branch, merged ones included ([merge-following](merge-following.md)).
