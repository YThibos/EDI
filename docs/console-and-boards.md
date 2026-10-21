# The console and research boards

## The EDI console

The core serves the EDI console on its own port, in the approved dark skin ([ADR-018](decisions/ADR-018-console-spa-served-by-the-core.md), [ADR-019](decisions/ADR-019-the-approved-skin.md), [ADR-150](decisions/ADR-150-the-sitrep-page-fits-one-laptop-screen.md)).
`edi console` opens it in the browser through a link that signs in once, within two minutes, and the session lasts twelve hours ([ADR-105](decisions/ADR-105-the-console-signs-the-captain-in-with-a-one-time-link.md)).

```sh
edi console                                         # open the console in the browser, signed in
edi sitrep --board                                  # print the sitrep, then open its page in the console
```

The sitrep page shows what needs a decision, what is ongoing and what is charted next side by side, in one glance on a laptop screen, with what landed this week below them, and follows every change as it happens ([ADR-107](decisions/ADR-107-the-sitrep-page-reads-one-document-from-the-fleet-snapshot.md), [ADR-109](decisions/ADR-109-the-console-stream-signals-each-commit.md)).
Each decision card takes an option, the captain's own words, or both, and records them verbatim as the captain's answer; Reconcile asks the primary to re-check the decision against reality instead of answering it ([ADR-108](decisions/ADR-108-a-console-answer-is-the-captains-word-and-reconcile-asks-for-a-recheck.md)).
The console opens through `cmd.exe /c start` from `/mnt/c` on WSL; elsewhere, or when that fails, the command prints the sign-in link instead ([ADR-106](decisions/ADR-106-edi-opens-the-console-in-the-captains-browser.md)).

## Research boards

A scout writes its Lavish board in its task's directory, opens it with `lavish-axi`, and has EDI listen to it ([ADR-111](decisions/ADR-111-research-boards-are-armed-through-edi-and-listened-to-by-the-core.md)):

```sh
edi board arm "$EDI_HOME/data/tasks/<task-id>/board.html" --task <task-id> --bind D58,D59
```

The core runs one `lavish-axi poll` per armed board and starts it again within 30 seconds if it stops ([ADR-112](decisions/ADR-112-the-lavish-result-reader-follows-toon.md)).
Each round of feedback becomes one message in the scout's inbox, and a prompt that names a bound decision, in its context data as `decision` (with `option`) or in words that begin with the decision's number such as `D58 A, ...`, answers that decision as the captain's word ([ADR-113](decisions/ADR-113-a-board-round-reaches-its-worker-and-its-bound-decisions.md)).
A board stops listening when the captain ends its session or its task is cleaned up.
