# Claude Code: hook events, flags and payloads

Verified on Claude Code 2.1.300 (`claude --version`) on WSL2 Ubuntu 24.04, 15/10/2026.
Claude Code releases often, so this verification is repeated whenever EDI's supervision relies on a newer version.

## Method

A settings file passed with `--settings` defined one command hook per event, each writing the JSON it received on standard input to a file.
Headless runs (`claude -p`) in a throwaway directory covered start-up, prompts, tool use, stop, failure and end, and a second run covered `--resume`.
An interactive session in tmux on a private socket (`tmux -L <private>`) covered the notification event and the end of a session whose tmux server is killed.
The runs used `claude-sonnet-5-5` at `low` effort, except the failure run, which named a model that does not exist.

## Flags

| Flag | Result on 2.1.300 |
|---|---|
| `--settings <file>` | Verified. Hooks defined in the file fired. The attribution block `{"commit": "", "pr": "", "sessionUrl": false}` in the same file was accepted: print mode silently ignores a settings file that fails validation, and the file's hooks fired. |
| `--session-id <uuid>` | Verified. Every payload and the run's result carried the given UUID. |
| `--resume <uuid>` | Verified. `SessionStart` arrived with `source: "resume"` and the same session id, and the session remembered the first run's tool call. |
| `--model <id>` | Verified. `claude-sonnet-5-5` ran; an unknown id ended the turn with `StopFailure` and `error: "model_not_found"`. |
| `--effort <level>` | Verified. `low` appears as `effort.level` in the tool and stop payloads. The help lists `low`, `medium`, `high`, `xhigh` and `max`, so EDI's ceiling of `high` (I-14) is EDI's own rule, not Claude Code's, and `xhigh` and `max` are the levels the captain's word for one task may lift it to. |
| `--name <name>` | Verified. Payloads carry `session_title` equal to the name. |
| `--append-system-prompt <text>` | Listed in `claude --help`. |
| `--append-system-prompt-file <file>` | Verified, but undocumented: `--help` names it only in the description of `--bare`, yet the model quoted a codeword that only the appended file contained. The primary can therefore load its anchor with this flag (section 10.1); if a release drops it, the fallback is `--append-system-prompt` with the anchor's text, which fits well within argument limits at 10,000 bytes. |

## Hook events

Every payload carried the four fields EDI relies on: `session_id`, `transcript_path`, `cwd` and `hook_event_name`.

| Event | Observed | Further fields |
|---|---|---|
| `SessionStart` | Yes, with `source: "startup"` on a new session and `source: "resume"` on `--resume`. | `source`, `session_title` |
| `UserPromptSubmit` | Yes. | `prompt`, `prompt_id`, `permission_mode`, `session_title` |
| `PreToolUse` (matcher `Bash`) | Yes. | `tool_name`, `tool_input` (`command`, `description`), `tool_use_id`, `prompt_id`, `permission_mode`, `effort` |
| `PostToolUse` (matcher `Bash`) | Yes. | as `PreToolUse`, plus `tool_response` (`stdout`, `stderr`, `interrupted`, `isImage`, `noOutputExpected`) and `duration_ms` |
| `Notification` | Yes, in the interactive session only, about 60 seconds after `Stop`. A permission-prompt notification was not provoked, because the session's permission mode allowed commands inside the workspace. | `message` ("Claude is waiting for your input"), `notification_type` (`idle_prompt`), `prompt_id`, `scratchpad_dir` |
| `Stop` | Yes. | `stop_hook_active`, `last_assistant_message`, `background_tasks`, `session_crons`, `prompt_id`, `permission_mode`, `effort` |
| `StopFailure` | Yes, on an API error (an unknown model). A usage-limit failure cannot be provoked on demand and was not observed. | `error` (`model_not_found`), `last_assistant_message`, `prompt_id`, `effort` |
| `SessionEnd` | Yes, at the end of a print run and when the tmux server of an interactive session was killed. | `reason` (`other` in both cases), `prompt_id` |

## Observations EDI's design depends on

- `--settings` merges with the working directory's `.claude/settings.local.json`: a `Stop` hook defined only there fired alongside the hooks from `--settings`, which is why EDI refuses to launch a worker in a worktree whose local settings define a hook (section 10.2).
- An interactive session in a new directory shows the workspace trust dialog before `SessionStart`, with "No, exit" selected; a blind Enter would end the session, which is why EDI pre-trusts worktrees and never presses a key into a dialog blind (I-28).
- Print mode skips the trust dialog.
- Killing the tmux server ended the session with `SessionEnd` and left no Claude Code process behind.
- No payload names the model; the transcript records it.

## Evidence

The raw payloads contain machine paths, so they are kept outside the repository; this document records every field EDI uses.

## Slice S3: the launch and the pane, on 2.1.301

Verified on Claude Code 2.1.301 on WSL2 Ubuntu 24.04, 16/10/2026, for the worker launcher ([worker-sessions](../decisions/worker-sessions.md)) and the pane probe ([steering](../decisions/steering.md)).

### Launch flags

| Flag | Result on 2.1.301 |
|---|---|
| `--disallowedTools <tools...>` and `--add-dir <directories...>` | Verified. Both take several values and stop at the next flag, so EDI always follows them with another flag. |
| `--` before the first prompt | Verified. Everything after it is the prompt, so a prompt that starts with a dash cannot be read as a flag. |
| `--append-system-prompt <text>` | Verified with a multi-line text passed as one argument. |
| `--dangerously-skip-permissions`, `--permission-mode <mode>` | Listed in `claude --help`; the captain's settings already skip the bypass warning. |

### What the pane shows

Each screen below was captured with `tmux capture-pane -p` in a 120 by 40 pane on a private socket; the fixtures in `edi-core/src/test/resources/pane-probe/` keep them, with workspace paths replaced.

- A folder Claude Code has never seen opens on the workspace trust dialog, "No, exit" selected, before any hook fires.
- For a linked git worktree, an entry with `hasTrustDialogAccepted` for the worktree's own path, or for its main checkout, suppresses the dialog; an entry for a directory above the worktrees does not.
- The idle composer is a line holding `❯` and a no-break space between two lines of `─`; with `--name`, the upper line carries the session's name near its end.
- While a turn runs, the composer stays on screen and the footer shows "esc to interrupt"; a finished turn leaves a summary line such as "✻ Cooked for 2s · done 08:48Z" above the composer, which is not a sign of work.
- The idle footer changes: "? for shortcuts" normally, "1 shell · ← for agents · ↓ to manage" while a background shell runs.
- `/exit` while a background shell runs opens a picker headed "Background work is running" with "Exit and stop tasks" selected.
- A shell command the permission rules do not allow opens a dialog with "Do you want to proceed?", numbered options and "Esc to cancel".
- A line typed with `send-keys -l` and submitted with Enter is sent at once, but a typed line that begins with the invisible separator U+2063 stays in the composer through any number of Enters; an argument prompt with that character is accepted.
- The usage-limit and spend-limit dialogs could not be provoked; their texts ("Stop and wait for limit to reset", "You've hit your monthly spend limit", "Adjust monthly spend limit", " to continue after reset") were read from the executable itself.

## Slice S4: sessions, background jobs and stop failures, on 2.1.301

Verified on Claude Code 2.1.301 on WSL2 Ubuntu 24.04, 16/10/2026, for the primary's hooks ([worker-sessions](../decisions/worker-sessions.md), [primary-role](../decisions/primary-role.md), [wakes-and-turn-end](../decisions/wakes-and-turn-end.md)), the relaunch and usage limits ([recovery](../decisions/recovery.md)).
A settings file with one recording hook per event drove two headless runs in a throwaway directory on `claude-haiku-5-5`, the second resuming the first.

### Sessions

- Every hook and every Bash command of a session carries `CLAUDE_CODE_SESSION_ID`, equal to the payload's `session_id` and to `--session-id`, and `CLAUDE_PID`, the process id of the session's own `claude`; hooks also get `CLAUDE_ENV_FILE` and `CLAUDE_PROJECT_DIR`.
- The executable sets `CLAUDE_CODE_SESSION_ID` from the current session for each command, and resets it when a conversation is cleared.
- `--resume <id>` with `--name`, `--settings` and a prompt after `--` kept the session id, fired `SessionStart` with `source: "resume"` and the name as `session_title`, and wrote into the same transcript file.
- When a background job finished, Claude Code submitted a prompt opening with `<task-notification>` and fired `UserPromptSubmit` for it, so every turn of the primary opens with `UserPromptSubmit`, whether the captain typed or a job ended.

### Stop

- `Stop` carries `background_tasks`, the session's in-flight background work: each entry has `id`, `type` (`shell` for a Bash job), `status` (`running` or `pending`), `description` and, for a shell job, `command`.
- A job that has finished is no longer listed.

### Stop failures and usage limits

- The executable lists the `StopFailure` errors `rate_limit`, `overloaded`, `authentication_failed`, `oauth_org_not_allowed`, `account_on_hold`, `verification_required`, `billing_error`, `invalid_request`, `model_not_found`, `server_error`, `max_output_tokens`, `cloud_credential_error` and `unknown`; `last_assistant_message` holds the error's text.
- Usage-limit messages open with "You've hit your" or "You've reached your", such as "You've hit your session limit · resets 2:10pm (Europe/Brussels)" or "You've hit your monthly spend limit.".
- Transcripts written by usage-limit stops on 2.1.270 to 2.1.299 end with an assistant record holding `isApiErrorMessage: true`, `error: "rate_limit"`, that text, and the reset in epoch seconds as `quotaLimits.resetsAt`, and from 2.1.296 also as `apiErrorParams.rate_limit_info.resetsAt`; Claude Code then appends metadata records such as `last-prompt` and `cost-state`.
- The 2.1.301 executable can also continue on its own after the reset ("Usage limit reached · continuing automatically"), so EDI types its resume line only into an idle prompt.

### Usage records

- Each assistant message's `usage` (`input_tokens`, `cache_read_input_tokens`, `cache_creation_input_tokens`, `output_tokens`) is repeated unchanged on every record of that message, one per content block, so EDI counts a message once by its id.

## Slice S8: hook context, the primary's system prompt and skills, on 2.1.302

Verified on Claude Code 2.1.302 on WSL2 Ubuntu 24.04, 17/10/2026, for the composed system prompt ([primary-startup-context](../decisions/primary-startup-context.md)), the return brief ([presence](../decisions/presence.md)) and the skills ([primary-anchor-and-skills](../decisions/primary-anchor-and-skills.md)).
Each check was a headless run on `claude-haiku-5-5` in a throwaway directory, asked to repeat codewords without using a tool.

### Hook context has a size limit

- A `SessionStart` hook answered `additionalContext` of 9,805 characters, and the model quoted codewords from its first and its last line.
- With 10,205 characters, the model quoted the first codeword and could not see the last: Claude Code kept the hook's output in a file and gave the model a short preview, as it does with any hook output over 10,000 characters.
- A larger context therefore reaches the model only as a preview, which is why EDI loads the captain's memory through the system prompt and keeps the digest and the return brief short.

### The primary's system prompt and skills

- `--append-system-prompt-file <file>` still works: the model quoted a codeword that only the appended file held.
- A skill in `<working directory>/.claude/skills/<name>/SKILL.md` is offered to a session started in that directory, and so is one whose directory is a symbolic link to a skill elsewhere, which is how `ops/install.sh` links EDI's skills into the EDI home.

## The status line, on 2.1.302 and 2.1.303

Recorded on Claude Code 2.1.302 on WSL2 Ubuntu 24.04, 18/10/2026, and run live on 2.1.303, 19/10/2026, for the status line ([primary-anchor-and-skills](../decisions/primary-anchor-and-skills.md)).
On 2.1.302 a `statusLine` command saved each JSON document it received; on 2.1.303 a settings file passed with `--settings` wired `edi statusline` with a `refreshInterval` of 60 seconds, against a running core, in a session on a private tmux server.
The fixtures in `edi-core/src/test/resources/statusline/` keep the recorded payloads, with paths and names replaced.

- The command ran once at session start, before any prompt, and again as the answer arrived; each run received one JSON document on standard input.
- At session start `context_window.current_usage` and `used_percentage` were `null` and both token totals 0; `rate_limits` was already there in the 2.1.302 session and absent until the first answer in the 2.1.303 one.
- After the first answer `current_usage` held `input_tokens`, `output_tokens`, `cache_creation_input_tokens` and `cache_read_input_tokens`, `prompt_cache` appeared, and `rate_limits.five_hour` and `seven_day` each held `used_percentage` and `resets_at` in epoch seconds.
- `model.display_name` and `effort.level` were in every payload, and `workspace.current_dir` equalled `cwd` in every recorded one.
- The bar kept the command's colours, and with the refresh it changed within a minute of the core stopping, with no event in the session.

## A full context, on 2.1.308

Verified on Claude Code 2.1.308 on WSL2 Ubuntu 24.04, 22/10/2026, for the full-context state ([supervision](../decisions/supervision.md)) and the fresh start that follows it ([recovery](../decisions/recovery.md)).
An interactive session on `claude-haiku-5-5`, with auto-compact off in the user settings as on the captain's machine, ran in a 120 by 40 pane on a private tmux server, with a settings file whose hooks each saved the JSON they received.
Pasting about 1.7 million characters into the composer and submitting it filled the context.

- Claude Code keeps at most 150,000 characters of a `CLAUDE.md` and warns about the rest, so a large memory file cannot fill the context on its own.
- The pane showed "Context limit reached · /compact or /clear to continue · auto-compact is off · /config to turn it on" below the prompt, then the idle composer.
- Only `SessionStart` and `UserPromptSubmit` fired: no `Stop`, no `StopFailure`, and in five minutes no `Notification`; the session of the 22/10/2026 incident did send one `idle_prompt` notification a minute after its context filled.
- The transcript recorded an assistant record with `isApiErrorMessage: true`, `error: "invalid_request"`, the model `<synthetic>` and the text "Prompt is too long", followed by a `system` record with `subtype: "turn_duration"` and the metadata records; the incident's transcript ends the same way.
- Claude Code decided this itself, in the same millisecond as the prompt, without an API call.
- Each further typed line fired `UserPromptSubmit` and failed the same way at once.
- `--resume` of the session with a prompt after `--` fired `SessionStart` with `source: "resume"` and `UserPromptSubmit`, and failed again with "Context limit reached", so a full session cannot be resumed.

## A session without a transcript, on 2.1.311

Verified on Claude Code 2.1.311 on WSL2 Ubuntu 24.04, 27/10/2026, for the fresh start that replaces a resume ([recovery](../decisions/recovery.md)).
An interactive session on a private tmux socket ran `claude --resume <uuid> -- "say hi"` with a session id that has no transcript in any session folder.

- Claude Code printed "No conversation found with session ID: <uuid>" and exited with 1 at once, without firing a hook, so a worker window running it closes.
- A worker stopped within seconds of its start leaves only the session's empty `tool-results` folder and no `<uuid>.jsonl`, as the incident of 27/10/2026 showed.

## A stop hook that cannot reach EDI, on 2.1.308 and 2.1.309

Read on Claude Code 2.1.308 from the transcripts of four workers whose `Stop` hook ran during core outages on 22/10/2026, and verified on 2.1.309 on WSL2 Ubuntu 24.04, 23/10/2026, for the missed-stop notice ([supervision](../decisions/supervision.md)).
An interactive session on a private tmux socket ran on `claude-haiku-5-5` with a settings file whose only hook, `Stop`, printed EDI's "core is not running" reason on standard error and exited with 1.

- The turn ended: the pane showed "Stop hook error occurred" above the idle prompt, and no hook fired again.
- The transcript recorded an `attachment` record of type `hook_non_blocking_error` with the hook's standard error and exit code, a `system` record with `subtype: "stop_hook_summary"` whose `hookErrors` held "Failed with non-blocking status code: " and that reason, with `preventedContinuation: false`, and then a `system` record with `subtype: "turn_duration"`.
- A stop EDI answered writes the same two records with an empty `hookErrors`; a stop EDI blocked puts its reason in `hookErrors` and goes on with an assistant record, with no `turn_duration`.

## The opening prompt, on 2.1.308

Verified on Claude Code 2.1.308 on WSL2 Ubuntu 24.04, 23/10/2026, for the primary's opening prompt ([opening-turn](../decisions/opening-turn.md)).
An interactive session on a private tmux socket ran `claude --settings <recording hooks> -- "Hello EDI, SITREP board, please."` on `claude-haiku-5-5` at `low` effort.

- The prompt was submitted at once, without a key pressed, and shown as the conversation's first prompt.
- `SessionStart` fired first with `source: "startup"`, then `UserPromptSubmit` with the prompt exactly as passed, then `Stop`.
- The `additionalContext` that the `UserPromptSubmit` hook answered reached the transcript, so a return brief given on that hook arrives with the first turn.
- The model loaded the `sitrep` skill and ran `edi sitrep --board`.

## Local settings that switch hooks off, on 2.1.312

Verified on Claude Code 2.1.312 in a Linux cloud sandbox, 28/10/2026, for the guard on a worker's `.claude` directory ([pre-tool-guard](../decisions/pre-tool-guard.md), [worker-sessions](../decisions/worker-sessions.md)).
A settings file passed with `--settings` defined a `SessionStart` hook that recorded its payload and a `PreToolUse` hook on `Bash` that recorded its payload and denied the call.
Headless runs (`claude -p`, `claude-haiku-5-5`, `--allowedTools Bash`) in throwaway directories asked the model to run `echo` and report what it printed.

| The run's directory held | The hooks from `--settings` |
|---|---|
| nothing | Fired: `SessionStart` recorded, the `Bash` call was denied. |
| `.claude/settings.local.json` with `{"disableAllHooks": true}` | Did not fire: nothing recorded, the `Bash` call ran. |
| `.claude/settings.json` with `{"disableAllHooks": true}` | Did not fire: nothing recorded, the `Bash` call ran. |
| `.claude/settings.local.json` with `{"disableAllHooks": true}`, and `"disableAllHooks": false` in the `--settings` file | Fired: `SessionStart` recorded, the `Bash` call was denied. |

- A settings file in the session's working directory can switch off every hook given with `--settings`, and a value the `--settings` file states for the same key wins over it.
- With `--allowedTools Write`, Claude Code held the model's `Write` of `.claude/settings.local.json` for a permission prompt; whether a file written in the middle of a session under `--dangerously-skip-permissions` takes effect in that session was not verified, because the sandbox's own permission check refused that run.
- User settings in `~/.claude/` were not tried, so as not to change the sandbox's own Claude Code; Claude Code documents them below the command line in precedence, as local and project settings are.

