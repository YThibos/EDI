# The GitLab command: the calls EDI makes

Verified on glab 1.106.0 (`glab --version`) on WSL2 Ubuntu 24.04, 16/10/2026, against an on-premises GitLab.
Every call was read-only; `glab mr create` is exercised against a recording stand-in in the tests, never against the forge.

| Call | Result on 1.106.0 |
|---|---|
| `glab mr list -R https://<host>/<group>/<project> -F json` | Prints an array of merge requests; each carries `iid`, `web_url`, `state`, `draft`, `source_branch`, `target_branch`, `sha`, `merge_commit_sha` and `squash_commit_sha`; `work_in_progress` is absent. |
| `--source-branch <branch>` | Filters the list by source branch; the list holds open merge requests unless `--merged`, `--closed` or `--all` is given, and EDI passes `--all` to find a merged one too. |
| `state` | `opened` while open and `merged` after a merge; a merged request through a merge commit carries `merge_commit_sha` and an empty `squash_commit_sha`. |
| `web_url` | Ends with `/-/merge_requests/<iid>`, which the adapter rebuilds from the registered project and the number. |
| `glab mr view <iid> -R <url> -F json` | Prints one object with the same fields. |
| `glab api user --hostname <host>` | Prints the logged-in user, whose `username` the adapter compares with the bound account. |
| `-R <full URL>` | Accepted, so a call never depends on the directory it runs in. |

EDI's adapter is `adapter.forge.GitLabForge` (ADR-079, ADR-080).
