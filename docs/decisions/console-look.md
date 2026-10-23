# The console keeps the approved dark skin, bundles its fonts, and fits the sitrep page on one laptop screen

**Kind:** Design

## Context

Two sitrep prototypes were compared and the captain approved one skin as it was; dark-mode browser extensions are off on localhost, so the page needs only to be dark-proof.
The prototype loaded its fonts from a public font service, while no hosted third-party service is added and the laptop sits behind a corporate proxy.
On the captain's 1920 by 1080 laptop screen, at 100% and 125% scaling, the first layout needed scrolling to show what needs a decision, what is ongoing and what is charted next.

## Decision

The console uses the approved skin: deep-space navy, an ion-blue signal colour, amber for anything waiting on the captain, green for nominal and red for trouble, with Exo 2, Inter and JetBrains Mono; dark only, its colour tokens fixed in `edi-console/src/styles.css`.
Dark-proofing is the `color-scheme` and `darkreader-lock` meta tags, `data-theme="dark"` and `:root { color-scheme: dark }`, and nothing more.
The console is a zoneless Angular application of standalone components driven by signals; the sitrep page is composed of `ConsoleHeader`, `HudStrip`, `DecisionDeck` with `DecisionCardView` and `AnsweredCard`, `OngoingList`, `ChartedList`, `LandedList` and the waiting list, every class prefixed `sr-`.
The three font families are bundled from the open-source `@fontsource` packages, latin subsets of the weights used, and served by the core.
The page shows the HUD strip, then three columns side by side, needs your decision, ongoing and charted next, with recently landed below at full width; from 1200 CSS pixels wide the strip and columns fit the viewport's height, and a column taller than its share scrolls inside itself.
Narrower, the deck takes the left column and the lists stack on the right; below 761 pixels everything is one column, and at phone width the gutters drop to 16 pixels with no horizontal scroll.
The charted list has no dispatch picker, because the console's only writes are answers, re-checks and presence ([console](console.md)).

## Why

The captain approved the look as is, the meta tags cost nothing, and bundling keeps every request on the loopback port, so the console renders the same offline, behind the proxy and on the desktop.
The captain reads the sitrep to see what waits on them and what is underway, so those sections belong in one glance at both scalings.

## Alternatives rejected

- The other prototype's look - not chosen.
- Fonts from the public font service - a third-party request on every load, and fallback fonts behind a proxy that blocks it.
- Hiding sections until asked for, or equal-height panels - hidden work is easy to miss, and a short list would sit in an empty panel.
- One component for the whole page - the deck and the lists would not be testable on their own.

## Consequences

- Good: one visual identity for the console and the research boards, and each part of the page has its own tests.
- Bad: no light theme, about twenty font files in the build, and a long decision scrolls inside its card.

## When to revisit

When the captain asks for a different look, reads the sitrep on a screen of another shape, or the work board joins the console.

## How we got here

- 15/10/2026: the captain approved the dark skin.
- 17/10/2026: slice S7 ported the prototype into Angular components with bundled fonts, in two columns.
- 21/10/2026: the page was relaid in three columns above what landed so it fits one laptop screen, checked at 1920 by 950, 1536 by 730, 1280 by 720 and 390 CSS pixels ([docs/images/sitrep/](../images/sitrep/)).
