# The validation pipeline: run status, spend and account binding

Verified on no-mistakes 1.84.0 (`no-mistakes --version`) on WSL2 Ubuntu 24.04, 16/10/2026.
EDI reads the pipeline's state and never drives it; the worker drives it through `axi run` and `axi respond`.

## Method

`no-mistakes axi status` ran in a worktree whose branch had no run, in one whose branch had an active run, and with `--run <id>` for runs on other branches, completed and failed.
The pipeline's state database was opened read-only and its schema listed; no row was changed.
The daemon's process and its environment were inspected by variable name only, and the mirror repository the daemon pushes from by configuration key only.

## Run status

| Question | Result on 1.84.0 |
|---|---|
| What `axi status` prints for the current branch | A `run:` block at the top level: `id`, `branch`, `status`, `head`, `head_sha`, `verification_plan`, `pr`, `findings`, then the tables `steps[n]{step,status,findings,duration_ms}` and `active_steps[n]{step,status,active_for,round_active_for,last_activity,agent_pid,round}`, indented by two spaces for fields and four for table rows. |
| What it prints when the branch has no run | `current_branch`, `runs_on_current_branch: 0` and a `runs[n]{id,branch,status,head,pr}` table of other branches' runs, with no `run:` block. |
| What `--run <id>` prints for another branch | An `other_branch_run:` block with the same fields, and for a finished run top-level `outcome:` and `error:` lines. |
| Status words seen | `running`, `completed`, `failed`; the database's default is `pending`. |
| Quoting | Values that need it are double-quoted with backslash escapes, such as the `pr` URL and `last_activity`. |

`AxiStatus` reads only the `run:` block, and its fixtures under `edi-core/src/test/resources/no-mistakes/` are these outputs with the repository renamed.

## Spend

The state database is `state.sqlite` in the pipeline's home.
`repos.working_path` is the main checkout of the repository the run belongs to, and `runs` holds `repo_id` and `branch` for each run.
`agent_invocations` holds one row per agent call with `run_id`, `step_name`, `round`, `exit_status`, `input_tokens`, `output_tokens`, `cache_read_tokens`, `cache_creation_tokens` and the per-round deltas `delta_input_tokens`, `delta_output_tokens` and `delta_cache_read_tokens`; failed rounds have rows of their own.
On the runs inspected, every delta equalled its total, because each round started a fresh agent session.

## Agent route

Verified on no-mistakes 1.84.0, 23/10/2026, from `axi run --help`, the commented global configuration and the command lines of the running daemon's agent processes.

| Question | Result on 1.84.0 |
|---|---|
| How is the Claude Code agent given a model and an effort? | Only through `agent_args_override.claude` in the global `config.yaml`, a list of extra command-line words; the key is global-only, so a repository's `.no-mistakes.yaml` cannot set it. |
| Do `axi run --model` and `--effort` reach Claude Code? | No. They pin a profile for the Pi agent only, and conflict with native selection flags. |
| What does the agent run on without the key? | `claude -p` starts with no `--model` and no `--effort`, so it runs at the harness's global defaults. |
| Does an edit reach a run that has already started? | No. Two runs started before the key was edited went on starting agents without the new words after the edit. |
| Does a new run take an edit without a daemon restart? | Yes. A run started hours after the key was edited, under a daemon started before the edit, launched its agents as `claude --model claude-opus-5-5 --effort high -p ...`, the override's words placed before the pipeline's own. |
| Consequence for EDI | EDI writes `agent_args_override.claude` from the dispatch rules' `pipeline` entry at every worker launch ([pipeline-agent](../decisions/pipeline-agent.md)). |

## Account binding

| Question | Result on 1.84.0 |
|---|---|
| Does a `GH_TOKEN` set for one `axi run` reach the pipeline? | No. `axi run` hands the run to the daemon over its socket, and the daemon (`no-mistakes daemon run`, started once by its service) runs every step in its own environment; its environment held no `GH_TOKEN`, `GITHUB_TOKEN`, `GITLAB_TOKEN` or `GLAB_*` variable, and `axi run --help` offers no way to pass one. |
| How does the daemon push? | From its own mirror repository, whose `credential.https://github.com.helper` entries are an empty entry followed by `gh auth git-credential`, which answers with the active `gh` account. |
| Consequence for EDI | The pipeline publishes under whichever `gh` account is active when it pushes, so EDI's per-call account binding ([forge-adapters](../decisions/forge-adapters.md)) covers EDI's own forge calls and the clone's transport, and `edi deliver` covers a project whose pipeline cannot open the draft under the right account ([delivery](../decisions/delivery.md)). |
