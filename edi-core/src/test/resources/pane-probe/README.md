# Pane probe fixtures

Each file is the visible screen of one Claude Code pane, as `tmux capture-pane -p` prints it, and `TmuxPaneProbeTest` checks the state `TmuxPaneProbe` reads from it.

## Captured from real panes

These were captured on Claude Code 2.1.301 in a 120 by 40 pane on a private tmux socket, on 16/10/2026.
Workspace paths were replaced with `/home/developer/.edi/worktrees/harness/probe-task`; nothing else was changed.

| File | What the pane showed | State |
|---|---|---|
| `trust-dialog.txt` | the workspace trust dialog of a folder Claude Code had never seen, with "No, exit" selected | `dialog-trust` |
| `idle-fresh-session.txt` | a new session waiting for its first prompt | `idle-prompt` |
| `busy-processing.txt` | the first moment of a turn, before any tool ran | `busy` |
| `busy-running-a-command.txt` | a turn running a shell command | `busy` |
| `idle-after-a-turn.txt` | the empty composer after two finished turns | `idle-prompt` |
| `idle-with-a-background-shell.txt` | the empty composer while a background shell runs, with a footer that names no shortcuts | `idle-prompt` |
| `idle-named-session.txt` | a worker EDI launched with `--name`, idle after its turn: the session name sits in the composer's top rule, captured in a 200 by 50 pane on EDI's worker socket | `idle-prompt` |
| `composer-holding-text.txt` | `/exit` typed into the composer and not yet sent | `unknown` |
| `exit-picker.txt` | the picker `/exit` opens while background work runs, with "Exit and stop tasks" selected | `dialog-exit-picker` |
| `permission-dialog.txt` | the permission dialog for a shell command | `dialog-permission` |

## Reconstructed from the installed binary

A usage limit and a spend limit cannot be reached on demand, so these three screens were assembled from the dialog texts the Claude Code 2.1.301 executable contains verbatim ("Stop and wait for limit to reset", "Upgrade your plan", "You've hit your monthly spend limit", "Adjust monthly spend limit", " to continue after reset"), laid out like the captured dialogs above.
They match the three shapes the old harness met in July and August 2026: a modal whose default is "Stop and wait", a spend-cap modal whose default is "Adjust monthly spend limit", and a "Press ⏎ to continue after reset" line above an idle composer.

| File | State |
|---|---|
| `limit-wait-dialog.txt` | `dialog-limit-wait` |
| `spend-cap-dialog.txt` | `dialog-spend-cap` |
| `continue-after-reset.txt` | `dialog-continue-after-reset` |
