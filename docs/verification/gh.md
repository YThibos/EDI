# The GitHub command: the calls EDI makes

Verified on gh 2.96.0 (`gh --version`) on WSL2 Ubuntu 24.04, 16/10/2026, with two accounts logged in to `github.com`.
Every call was read-only; `gh pr create` is exercised against a recording stand-in in the tests, never against the forge.

| Call | Result on 2.96.0 |
|---|---|
| `gh auth token --hostname github.com --user <account>` | Prints the named account's token whichever account is active. |
| `gh auth status --hostname github.com --json hosts` | Prints `{"hosts":{"github.com":[...]}}` with one object per account, carrying `login`, `active`, `state` and `scopes` as one comma-separated string such as `gist, read:org, repo, workflow`. |
| `gh pr view <n> --repo github.com/<owner>/<repo> --json number,url,state,isDraft,headRefName,baseRefName,headRefOid,mergeCommit,mergedAt` | Prints one object; `state` is `OPEN`, `MERGED` or `CLOSED`, `isDraft` a boolean, `mergeCommit` is `null` until the merge, then `{"oid": "<commit>"}`, and `mergedAt` is `null` until the merge, then a UTC moment such as `2026-10-21T12:37:04Z`; `mergedAt` verified on 22/10/2026 with `gh pr list --state all`. |
| `gh pr list --repo ... --head <branch> --state all --json ...` | Prints an array of the same objects for every pull request from that branch, open, merged or closed, and an empty array when there is none; verified again on 21/10/2026 on a merged pull request, which showed `MERGED`, `isDraft` false and its `mergeCommit`. |
| `--repo <host>/<owner>/<repo>` | Accepted with the host written out, so the registered host is named on every call. |
| `GH_TOKEN` in the environment | Used for the call instead of the stored login. |

EDI's adapter is `adapter.forge.GitHubForge` (ADR-079, ADR-080).
