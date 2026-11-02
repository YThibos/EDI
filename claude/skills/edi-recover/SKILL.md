---
name: edi-recover
description: >-
  Owns recovery after workers die, stop on a usage limit or fill their context: the numbered outage list, one-at-a-time restarts and verification against real sources.
  Load it at session start when the digest lists dead workers, after a usage limit or a full context, or for edi recover.
---

# edi-recover

## One dead worker

1. `edi worker list` shows its health; judge it by the structured state, never by a pane.
2. `edi worker relaunch <task>` resumes the recorded session in the task's own isolated copy, with the model and effort its category gives now.
3. A worker that must stop is signalled with `edi worker stop <task>`; never type into its pane and never `/exit` it.

## A worker whose context is full

1. You raise it the moment its transcript shows the full context; `edi worker list` reads `context full since <time>`.
2. Never type into its pane and never resume it: every prompt fails the same way, and a resume reopens the full conversation.
3. `edi worker stop <task>`, then `edi worker relaunch <task>` starts a fresh session in the same copy, briefed with `handover.md` beside the brief.
4. Messages sent meanwhile wait in its inbox; the fresh session reads them first.

## Two or more dead workers

1. Run `edi recover`: you hold every dead worker's task, relaunch none and print one numbered list.
2. Show the list to the captain with each entry's outcome so far, what it waits on, its recorded model and effort, those its category gives now, and unpushed work.
3. Act only on the numbers the captain names, one at a time:
   - `edi recover <n> resume` resumes the recorded session;
   - `edi recover <n> fresh [--model <model>] [--effort <effort>]` starts a new one;
   - `edi recover <n> drop [--discard-work]` drops the task, only on the captain's word recorded first with `edi captain said --task <task> --message <n>`, or a dialog option they chose that opens with `drop` and names the task.
4. You refuse a relaunch while a usage limit still holds; answer the list once it has reset.

## After an outage

- Verify every entry that crossed the outage against the remote, its draft and the captain's checkouts before you report it; otherwise call it not verified.
- A changed boot of the machine means every worker from before it is dead, whatever its last status says.
- Board answers given while a scout was dead are on its entry; carry them to the scout once it runs.

## Usage limits

- A worker parked on a limit resumes on its own after the reset; you type its one resume line into an idle prompt.
- Never press a key into a limit, spend-cap or continue dialog; Escape only, and only when the captain asks.
- Credits stay off: you never raise a spend cap or accept an upgrade.
- When your own session stopped on a limit, the next session's digest says so and lists the parked work.
