# A project's workers run on the Claude login its registry entry names, and every launch states that login's configuration directory

**Kind:** Architecture

## Context

One Claude subscription carried every worker, and a captain with a second subscription wants some projects' work to spend that one instead.
A Claude login is one Claude Code configuration directory: its credentials, its global configuration with the trust entries, its user settings, its skills, its transcripts and its session state all sit under the directory `CLAUDE_CONFIG_DIR` names, and without the variable Claude Code uses `~/.claude`, with `~/.claude.json` beside it ([docs/verification/claude-code.md](../verification/claude-code.md)).
A tmux window takes its environment from the tmux server, not from the program that opens it, and the workers' server outlives restarts of the core, so a worker's login was decided by whichever shell first started that server, while the trust entry and the resume check followed the core's own `CLAUDE_CONFIG_DIR`.
A directory whose first run is unfinished stops an interactive session on the theme picker without firing a hook, so a worker launched on it showed only as a missed launch two minutes later.

## Decision

The machine profile maps each login's name to its configuration directory under `claude.logins` (`MachineProfile.claudeLogins`, `ClaudeLogins`), and the login named `default` needs no entry: it is Claude Code's own directory, unless the profile names a directory for it.
A registry entry's `login` names the login its project's workers run on, `default` when it names none (`ProjectEntry.login`), and a registry whose entry names a login the profile does not name is refused, naming the entry and the login; `edi project list` shows a login other than `default`.
`LaunchWorker` resolves the project's login into the launch (`WorkerRuntime.Login`), and every launch states the directory itself: `ClaudeCommandLine` sets `CLAUDE_CONFIG_DIR` in the window's environment for a named directory and unsets it in the worker's command for Claude Code's own, so no variable the core or the tmux server inherited decides a worker's login.
`ClaudeWorkspaceTrust` writes the launch's trust entry into the global configuration of the launch's login (`ClaudeLoginFiles`), and refuses a launch whose login has none.
A dispatch, a relaunch and a deferred launch first require the login to be ready (`WorkerRuntime.requireReady`, `ClaudeLoginReadiness`), before a copy, a branch or a window exists: its global configuration exists, records the first run as finished (`hasCompletedOnboarding`), and `claude auth status` on its directory exits 0.
`claude auth status` runs without the inherited `CLAUDE*` variables the worker's command withholds (`ClaudeCommandLine.withheldFromClaudeCode`), so it judges the environment the worker starts in.
Nothing but that exit code is read from Claude Code, and no credentials file is opened; the refusal names the login, what it lacks and the command that fixes it.
EDI creates nothing else in a login's directory: the captain finishes its first run once and links their own skills, one by one, and `settings.json` into it, as the configuration guide describes.
Every launch records its login on the worker by name, never its directory, and a resume runs on the recorded login, whose sessions folder `ClaudeSessions` searches for its transcript, while a dispatch and a fresh start take the login the project's registry entry names at that moment ([recovery](recovery.md)).
A worker's first hook proves its login: a transcript path outside the recorded login's directory stops the worker, and the event names both directories.
The core's setting `edi.workers.claude-home`, the user's home by default, names the home whose `.claude` and `.claude.json` are Claude Code's own directory, so a test names its directories through that setting or the machine profile.

## Why

A login's directory is a fact of the machine and which login carries a project is a fact of the project, so the path is written once in the profile, a misspelt name is refused when the registry is read, and a worker's login is what the files say, whichever shell started the core or the tmux server.
A login that is logged in is not yet ready, and a refusal at dispatch that names the fixing command costs the captain seconds, where a worker stuck on a first-run screen costs a missed launch and a guess.
Checking the global configuration before asking `claude auth status` keeps that check from writing the file it is about to judge, which Claude Code does on an empty directory.

## Alternatives rejected

- The directory in each registry entry - a path repeated per project, and a typo found only at launch.
- `default` as whatever directory the core inherited - one `export` in the shell that starts the core or the tmux server would move every worker without a word.
- A login chosen by the dispatch rules - they stay about model and effort, and a project's subscription is not a category's.
- One shared sessions folder for every login - two accounts' transcripts in one folder, each login's clean-up pruning the other's.
- EDI linking the skills and settings into a login's directory - EDI never creates another tool's configuration beyond the trust entry, and a link made at launch could replace what the captain keeps there.

## Consequences

- Good: a second subscription carries the projects the registry assigns to it, with the same harness, guard, hooks and supervision as any other worker.
- Good: an unready login is a refusal at dispatch with the fix in it, not a missed launch.
- Bad: a dispatch checks its login twice, once before its branch is reserved and once more at the launch, and a ship that plans its work in a research task first checks three times, so `claude auth status` adds about half a second per check while the launcher holds its lock; a relaunch and a deferred launch check once.
- Bad: a core started with `CLAUDE_CONFIG_DIR` exported no longer passes it on; the profile has to name the `default` login's directory instead.

## When to revisit

When Claude Code offers a login per session without a configuration directory, or a project needs more than one login.

## How we got here

- 10/11/2026: two research runs measured two configuration directories side by side, and the captain chose the profile's named logins, a login per registry entry, `default` as Claude Code's own directory and a readiness check at every dispatch (EDI-249, EDI-253).
- 10/11/2026: a worker records the login it was launched on, so a relaunch resumes its session on that login, and its first hook proves it (EDI-254).
