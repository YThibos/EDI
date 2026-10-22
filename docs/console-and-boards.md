# The console and research boards

## The EDI console

The core serves the EDI console on its own port, in the approved dark skin ([ADR-018](decisions/ADR-018-console-spa-served-by-the-core.md), [ADR-019](decisions/ADR-019-the-approved-skin.md), [ADR-152](decisions/ADR-152-the-sitrep-page-fits-one-laptop-screen.md)).
`edi console` opens it in the browser through a link that signs in once, within two minutes, and the session lasts twelve hours ([ADR-105](decisions/ADR-105-the-console-signs-the-captain-in-with-a-one-time-link.md)).

```sh
edi console                                         # open the console in the browser, signed in
edi sitrep --board                                  # print the sitrep, then open its page in the console
```

The sitrep page shows what needs the captain, what is ongoing and what is charted next side by side, in one glance on a laptop screen, with what landed this week below them, and follows every change as it happens ([ADR-107](decisions/ADR-107-the-sitrep-page-reads-one-document-from-the-fleet-snapshot.md), [ADR-109](decisions/ADR-109-the-console-stream-signals-each-commit.md)).

![The sitrep page in a 1536 by 730 viewport, a laptop screen at 125% scaling](images/sitrep/sitrep-1536x730.webp)

Every time the page shows is in the machine's own time zone, which the core names in the sitrep document, so the page and the command agree whatever zone the browser is in ([ADR-162](decisions/ADR-162-edi-shows-the-machines-local-time-and-stores-utc.md)).
The header clock reads as a stardate, such as "SD 2026.295 · 11:14 CEST", and on a screen 350 CSS pixels wide or narrower it keeps only the time.

Each decision card takes an option, the captain's own words, or both, and records them verbatim as the captain's answer; Reconcile asks the primary to re-check the decision against reality instead of answering it ([ADR-108](decisions/ADR-108-a-console-answer-is-the-captains-word-and-reconcile-asks-for-a-recheck.md)).
The console opens through `cmd.exe /c start` from `/mnt/c` on WSL, and through `xdg-open` on a Linux desktop; elsewhere, or when that fails, the command prints the sign-in link instead ([ADR-150](decisions/ADR-150-edi-opens-the-console-through-windows-or-the-linux-desktop.md)).

Every new primary session opens the sitrep page in the browser by itself, signed in, as `edi console` does ([ADR-151](decisions/ADR-151-a-new-primary-session-opens-the-sitrep-page.md)).
It opens once, at the launch of the session that holds the primary role, and not for a standby session, a worker, or the same primary resumed, cleared or compacted.
When the browser cannot open it, the session starts anyway, and its digest names the console's address and `edi console` as the way to open it.
`console.openSitrepAtSessionStart: false` in the machine profile switches it off.

## Research boards

A scout writes its Lavish board in its task's directory, opens it with `lavish-axi`, and has EDI listen to it ([ADR-111](decisions/ADR-111-research-boards-are-armed-through-edi-and-listened-to-by-the-core.md)):

```sh
edi board arm "$EDI_HOME/data/tasks/<task-id>/board.html" --task <task-id> --link <url> --questions 3 --card card.json
```

The link is the one `lavish-axi` printed when the scout opened the board, and the count is how many questions the round puts before the captain ([ADR-159](decisions/ADR-159-a-board-round-names-its-link-and-its-questions.md)).
A decision the board asks is a card on `--card`, in the format `edi decision raise` takes, with `question` naming the board question that answers it: the form's `data-lavish-question`, whose answer data is `{question, answer}`.
EDI raises the card on the scout's task and binds it to the board when it is armed, so the captain's choice there is his word for it, promote included ([ADR-170](decisions/ADR-170-a-boards-decision-card-is-raised-and-bound-when-the-board-is-armed.md)).
`--bind D58,D59` binds decisions the primary raised already, which the board names by number.
The count leaves out the card and the decisions the board binds, which wait as their own cards.
The board waits in the decision zone of the report, the sitrep page and the return brief until a round of the captain's answers arrives; arming it again posts a new round.
When the scout cannot arm it again, the primary posts the round instead:

```sh
edi board post "$EDI_HOME/data/tasks/<task-id>/board.html" --link <url> --questions 2
```

The core runs one `lavish-axi poll` per armed board and starts it again within 30 seconds if it stops ([ADR-112](decisions/ADR-112-the-lavish-result-reader-follows-toon.md)).
Each round of feedback becomes one message in the scout's inbox, and a prompt that names a bound decision, in its context data as `decision` or by its card's `question` (with `option` or `answer`), or in words that begin with the decision's number such as `D58 A, ...`, answers that decision as the captain's word ([ADR-113](decisions/ADR-113-a-board-round-reaches-its-worker-and-its-bound-decisions.md)).
A board stops listening when the captain ends its session or its task is cleaned up.
