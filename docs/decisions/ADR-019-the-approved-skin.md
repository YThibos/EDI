# ADR-019: The approved dark skin

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Two sitrep prototypes were compared; the captain chose one: "This skin is perfect."
He added that dark-mode browser extensions are always off on localhost: "It was just to mention that the skinning should be dark-proof."

**Decision:** The console uses the approved skin: deep-space navy, an ion-blue signal colour, amber for anything waiting on the captain, green for nominal, red for trouble; Exo 2, Inter and JetBrains Mono with Segoe UI and Cascadia Code fallbacks; dark only.
The colour tokens are fixed:

| Token | Hex | Use |
|---|---|---|
| `--void` | `#060a17` | page background |
| `--deep` | `#0a1126` | background gradient top |
| `--panel` / `--panel-raised` | `#0d1630` / `#111d3d` | panel gradient |
| `--panel-sunk` | `#0a1229` | tiles, option rows, inputs |
| `--line` / `--line-bright` | `#1c2a52` / `#2b3f7a` | borders |
| `--text-hi` / `--text` | `#eaf0ff` / `#b9c6ea` | titles / body |
| `--text-dim` / `--text-faint` | `#8391bd` / `#65739f` | secondary / meta |
| `--ion` | `#56c8ff` | primary signal, buttons, links, focus |
| `--nebula` | `#8f7dff` | background glow accent |
| `--amber` | `#ffb84d` | decisions, waiting on the captain |
| `--green` | `#3ee0a1` | working, done, landed, all clear |
| `--red` | `#ff6479` | failed, needs repair |
| `--blue` | `#7aa7ff` | other run states |

Dark-proofing is the `color-scheme` and `darkreader-lock` meta tags, `data-theme="dark"` and `:root { color-scheme: dark }`, and nothing more.
At phone width the page is one column with 16 px gutters and no horizontal scroll.

**Motivation:** The captain approved this look as is; the two meta tags cost nothing and keep the page dark-proof.

**Alternatives considered:**
- The other prototype's look - not chosen by the captain.
- Engineering around dark-mode extensions - unnecessary on localhost.

**Consequences:**
- Good: one visual identity for the console and the research boards.
- Bad: no light theme.

**Revisit when:** The captain asks for a different look.

**Sources:** EDI rewrite specification, sections 2 and 15.4 (decisions D-look, D56, D57); `edi-console/src/index.html`, `edi-console/src/styles.css`.
