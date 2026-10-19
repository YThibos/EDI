# ADR-110: The console ports the approved skin into Angular components and bundles its fonts

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 15.4: the builder ports the approved prototype's CSS tokens, typography and layout into the console's global stylesheet and the sitrep page's components, so the HTML structure becomes Angular templates and the look stays identical (ADR-019).
The prototype loaded Exo 2, Inter and JetBrains Mono from a public font service and fell back to Segoe UI and Cascadia Code without network.
No hosted third-party service is added (section 5.6), and the laptop works behind a corporate proxy.
The prototype's board also carried a dispatch picker for queued work, while `/sitrep` is read-only (section 15.2) and the console's only writes are answers and re-checks (ADR-108).

**Decision:** The console is a zoneless Angular application of standalone components driven by signals, with two routes: the sitrep page at `/` and the sign-in page at `/login`.
The sitrep page is composed of `ConsoleHeader` (the orbit-and-chevron mark, the `EDI` wordmark, `SITREP` as the page title, the stardate and the machine), `HudStrip`, `DecisionDeck` with `DecisionCardView` and `AnsweredCard`, `OngoingList`, `LandedList` and `ChartedList`, laid out as the prototype's two independent columns.
The tokens, the base page, badges, buttons, panels, rows, section signs and the shared card base live in the global stylesheet; each component keeps only its own rules, under the build's style budget, and every class takes the prefix `sr-`.
The deck deals one card at a time off the prototype's receding stack, open decisions first and the day's answers after them as green answered cards.
The charted list has no dispatch picker.
The three font families are bundled from the open-source `@fontsource` packages, latin subsets of the weights the skin uses only, and served by the core with the rest of the console.

**Motivation:** The prototype's look is kept exactly while every request stays on the loopback port, so the console renders the same offline, behind the proxy and on the desktop.

**Alternatives considered:**
- Loading the fonts from the public font service, as the prototype did - a third-party request on every load, and the fallback fonts behind a proxy that blocks it.
- Keeping the dispatch picker - dispatching is the primary's work, and the specification gives the console no dispatch.
- One component with the whole page - the deck, the card and the lists would not be testable on their own.

**Consequences:**
- Good: the page looks the same everywhere, and each part has its own tests.
- Bad: the console build carries about twenty font files, and their upgrades arrive through Renovate with the other console packages.

**Revisit when:** The captain asks for a different look, or the work board joins the console.

**Sources:** EDI rewrite specification, sections 5.6, 15.2 and 15.4; ADR-018, ADR-019; `edi-console/src/styles.css`, `edi-console/src/app/console/`, `edi-console/src/app/sitrep/`, `edi-console/src/app/sign-in/`, `edi-console/package.json`.
