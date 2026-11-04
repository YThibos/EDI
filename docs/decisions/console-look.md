# The console keeps the approved dark skin, bundles its fonts, and fits the sitrep page on one laptop screen

**Kind:** Design

## Context

Two sitrep prototypes were compared and the captain approved one skin as it was; dark-mode browser extensions are off on localhost, so the page needs only to be dark-proof.
The prototype loaded its fonts from a public font service, while no hosted third-party service is added and the laptop sits behind a corporate proxy.
On the captain's 1920 by 1080 laptop screen, at 100% and 125% scaling, the first layout needed scrolling to show what needs a decision, what is ongoing and what is charted next.

## Decision

The console uses the approved skin: deep-space navy, an ion-blue signal colour, amber for anything waiting on the captain, green for nominal and red for trouble, with Exo 2, Inter and JetBrains Mono; dark only, its colour tokens fixed in `edi-console/src/styles.css`.
Dark-proofing is the `color-scheme` and `darkreader-lock` meta tags, `data-theme="dark"` and `:root { color-scheme: dark }`, and nothing more.
The console is a zoneless Angular application of standalone components driven by signals; the sitrep page is composed of `ConsoleHeader`, `HudStrip`, `DecisionDeck` with `DecisionCardView` and `AnsweredCard`, `OngoingList`, `ChartedList`, `LandedList`, the waiting list and `PersonalityDesk`, every class prefixed `sr-`.
The personality desk opens from a Personality latch beside the presence buttons and sits above the HUD strip while it is open, pushing the glance down the page; its sliders take the ion-blue fill up to the thumb's centre over a line-coloured track, and below 761 pixels its sample moves under the sliders.
The browser tab shows EDI's mainframe hologram as an original drawing: a dotted sphere on a stem that flares to its base, with a flat ring just under the sphere, glowing ion-blue on a deep-navy tile.
`edi-console/public/favicon.svg` is the one drawing, sized to read at 16 pixels, whose dotted texture, latitude bands and stem rows are faint enough to merge into the glow there and show only at larger sizes.
Before every build `edi-console/render-icons.mjs` renders it with resvg into `favicon-32.png` and the 180-pixel `apple-touch-icon.png`, which `index.html` names beside the SVG; research boards stay in Lavish ([research-boards](research-boards.md)), so only the console's pages carry the icon.
The three font families are bundled from the open-source `@fontsource` packages, latin subsets of the weights used, and served by the core.
The page shows the HUD strip, then three columns side by side, needs your decision, ongoing and charted next, with recently landed below at full width; from 1200 CSS pixels wide the strip and columns fit the viewport's height, and a column taller than its share scrolls inside itself.
Narrower, the deck takes the left column and the lists stack on the right; below 761 pixels everything is one column, and at phone width the gutters drop to 16 pixels with no horizontal scroll.
The charted list has no dispatch picker, because the console's only writes are answers, re-checks, presence and the personality ([console](console.md)).

## Why

The captain approved the look as is, the meta tags cost nothing, and bundling keeps every request on the loopback port, so the console renders the same offline, behind the proxy and on the desktop.
A tab with the hologram is found among many at a glance, and PNGs rendered from the drawing at build time can never drift from it.
The captain reads the sitrep to see what waits on them and what is underway, so those sections belong in one glance at both scalings.

## Alternatives rejected

- The other prototype's look - not chosen.
- Fonts from the public font service - a third-party request on every load, and fallback fonts behind a proxy that blocks it.
- Hiding sections until asked for, or equal-height panels - hidden work is easy to miss, and a short list would sit in an empty panel.
- One component for the whole page - the deck and the lists would not be testable on their own.
- The game's own hologram image - a game asset, which no file of EDI may carry.
- PNG fallbacks exported by hand and committed - they drift from the drawing at its first change.
- A transparent icon without the tile - the ion-blue glow fades on a light browser tab.
- A second, simpler drawing for 16 pixels - two drawings to keep alike, when the faint detail already merges at that size.

## Consequences

- Good: one visual identity for the console and the research boards, and each part of the page has its own tests.
- Bad: no light theme, about twenty font files in the build, and a long decision scrolls inside its card.
- Bad: the console's build depends on resvg, a native binary per platform, for two small images.

## When to revisit

When the captain asks for a different look, reads the sitrep on a screen of another shape, or the work board joins the console.

## How we got here

- 15/10/2026: the captain approved the dark skin.
- 17/10/2026: slice S7 ported the prototype into Angular components with bundled fonts, in two columns.
- 21/10/2026: the page was relaid in three columns above what landed so it fits one laptop screen, checked at 1920 by 950, 1536 by 730, 1280 by 720 and 390 CSS pixels ([docs/images/sitrep/](../images/sitrep/)).
- 27/10/2026: the personality desk opens above the glance from a latch in the header (EDI-98).
- 28/10/2026: the captain asked for a browser-tab icon after EDI's mainframe hologram; an original drawing became the SVG favicon with PNGs rendered from it, checked in light and dark browser tabs ([docs/images/sitrep/](../images/sitrep/)) (EDI-116).
- 03/11/2026: the header gained Sitrep, Fleet and Week tabs in the mono face, the active one in ion, the Sitrep tab with the waiting count in an amber pill; Fleet and Week are `EmptyView` until EDI-61 and EDI-62 fill them (EDI-58).
