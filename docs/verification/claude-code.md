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
| `--effort <level>` | Verified. `low` appears as `effort.level` in the tool and stop payloads. The help lists `low`, `medium`, `high`, `xhigh` and `max`, so EDI's ceiling of `high` (I-14) is EDI's own rule, not Claude Code's. |
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
