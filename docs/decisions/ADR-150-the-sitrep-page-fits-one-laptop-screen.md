# ADR-150: The sitrep page fits one laptop screen, with three columns above what landed

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-110 laid the sitrep page out as the prototype's two independent columns: what needs a decision, what is ongoing and what landed recently on the left, what is charted next on the right.
On the captain's laptop screen of 1920 by 1080 pixels the page needed scrolling to show it all, at 125% display scaling and still at 100%.
The captain asked for everything at one glance, at least what needs a decision, what is ongoing and what is charted next, in that order from left to right, with what landed recently below them and free to need scrolling.
Without the browser's own bars that screen leaves a viewport of about 1920 by 950 CSS pixels at 100% and about 1536 by 730 at 125%.

**Decision:** This record supersedes ADR-110 and keeps its components, skin, fonts, deck and charted list, changed in the layout only.
The sitrep page shows the HUD strip on top, then three columns side by side: 01 needs your decision, 02 ongoing and 03 charted next; 04 recently landed follows below them at full width.
From 1200 CSS pixels wide, the strip and the columns together are at most the viewport's height below the header, and a column whose content is taller scrolls inside itself: an open decision scrolls inside its card, so the deck's stack and its navigation stay in view, and a list scrolls inside its panel.
The three columns share a header row and a body row through a subgrid, so their panels start on one line however their headings wrap; the decision column is a fifth wider than the other two.
The page fits by tightening rather than hiding: a 52-pixel header, single-line HUD tiles, smaller type steps and card padding, and row titles that wrap instead of being cut short.
Between 761 and 1199 CSS pixels the decision deck takes the left column and the ongoing and charted lists stack on the right; below that, everything is one column with 16-pixel gutters, as ADR-019 requires at phone width.

**Motivation:** The captain reads the sitrep to see what waits on them and what is underway, so those three sections belong in one glance at both scalings, and what already landed is the part they can scroll to.
Keeping the deck's navigation in view while a long decision scrolls keeps every card reachable without moving the page.

**Alternatives considered:**
- Hiding sections or rows until asked for - the captain asked for everything at one glance, and hidden work is easy to miss.
- Letting the whole page scroll above what landed - the reason for the change.
- Equal-height panels in the three columns - a short list would sit in an empty panel the height of the decision card.

**Consequences:**
- Good: on the laptop screen what needs a decision, what is ongoing and what is charted next are all in view without scrolling, and what landed recently starts just below them.
- Bad: a decision longer than the column scrolls inside its card, and in a viewport shorter than about 560 CSS pixels the strip and the columns keep a height of 480 pixels and the page scrolls again.

**Revisit when:** The captain reads the sitrep on a screen of a different shape, or a fifth section joins the page.

**Sources:** The captain's request of 21/10/2026; ADR-019, ADR-110; `edi-console/src/styles.css`, `edi-console/src/app/sitrep/sitrep-page.html`, `edi-console/src/app/sitrep/sitrep-page.css`, `edi-console/src/app/console/hud-strip.css`, `edi-console/src/app/console/decision-deck.css`, `edi-console/src/app/console/decision-card.css`, `edi-console/src/app/console/console-header.css`; the page at 1920 by 950, 1536 by 730, 1280 by 720 and 390 CSS pixels in `docs/images/sitrep/`.
