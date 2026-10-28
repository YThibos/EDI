# EDI-83 The anchor as an index

| Field | Value |
|---|---|
| Key | EDI-83 |
| Kind | ship, a change to the repository, on branch `feature/EDI-83` |
| Wave and order | wave 2, order 6 |
| Source | EDI-74 plan, step 9 |
| Size | S |
| Blocked by | [EDI-81](EDI-81.md) |
| Status | open |
| Cloud session | ok |

## Goal

The primary's anchor becomes an index: identity, character, voice, the captain's word, how a turn ends and skill triggers stay, and the hard rules shrink to their numbers with a few words each, marked where EDI refuses, held to the invariant catalogue by a test.

## Context

The anchor is `claude/primary/CLAUDE.md`, loaded at every primary start, with a budget of 10,000 bytes (8,847 at the time of the research).
It holds 39 hard rules, of which about 20 are already enforced by the core and pinned by an invariant test of the same number.
Four rules are worded differently in the anchor and in the invariant catalogue (I-04, I-06, I-28, I-32), and the same rule lives in up to seven places.
The research is in [the EDI-74 plan](research/EDI-74-plan.md), section 4.

## Acceptance criteria

- `claude/primary/CLAUDE.md` keeps identity, character, voice, the captain's word, turn ending and the skill triggers, and lists the hard rules as numbers with a few words each, marked where EDI refuses.
- Skills cite rule numbers instead of restating them (the list of restating skills is in section 4.3 of the plan): `edi-ship`, `edi-recover`, `edi-voice`, `stow`, `edi-personality`, `edi-upkeep`.
- A test holds every anchor number to its nested class in the invariant catalogue and their wordings to each other; it fails when the anchor names a rule the catalogue lacks, or the two wordings differ.
  This fixes the drift of I-04, I-06, I-28 and I-32.
- The anchor is under 7,000 bytes, and the anchor budget test (AT-35) holds.
- A test over `claude/skills` finds no skill that restates an anchor rule's text.
- `docs/decisions/primary-anchor-and-skills.md` gains a history line.

## Requirements from decisions and answers

- Q1 A (EDI-74 round 1): the anchor becomes an index, hard rules as numbers and a few words each, marked where EDI refuses, held to the catalogue by a test.
- Q2 A: project-specific rules live in the project registry's `shipBriefRules`, memory keeps only the why.
- A rule that governs every sentence or every turn stays in the anchor; a skill holds only what one recognisable trigger needs; a skill whose trigger is almost always is an anchor rule in the wrong place.
- The persona line and the register of the Voice section stay in the anchor (EDI-82 added them); only the translation table stays in `edi-voice`.
- I-39 is pinned by AT-43, because AT-39 is the native smoke test; the index must say which test pins which rule.
- Memory files are on the captain's machine and are out of reach; this item changes only repository files.

## Research and measurements

- 20 of 39 anchor rules are held by a guard or refusal and pinned by an invariant test of the same number; about 15 are partly held; the writing rules have no checker.
- The full map of each rule to its guard, its location in the code and the gap is in section 4.2 of the plan.
- The effort ceiling had seven homes and the no-trailer rule six; duplicates are listed in section 4.3.
- The primary loaded the voice skill once in 309 turns, which is why rules that govern every turn belong in the anchor.

## Read first

- `claude/primary/CLAUDE.md`, every `claude/skills/*/SKILL.md`, `edi-core/src/main/java/io/github/ythibos/edi/domain/primary/PrimaryPrompt.java`.
- `edi-core/src/test/java/io/github/ythibos/edi/invariant/` (the catalogue) and `claude/ClaudeLayerTest.java`.
- [primary-anchor-and-skills](../decisions/primary-anchor-and-skills.md), [invariant-catalogue](../decisions/invariant-catalogue.md), [primary-role](../decisions/primary-role.md), [captain-facing-voice](../decisions/captain-facing-voice.md).

## How to verify

- `./mvnw verify`; the anchor is a native-image-reachable resource if the core embeds it, so check `NativeImageInput` in [local-native-builds](../decisions/local-native-builds.md) and run the native quick build if it does.
- Run the new tests against a deliberately drifted anchor to see them fail, then restore it.
- Read the new anchor end to end as a fresh primary would: every rule number resolves, and nothing a primary needs at every turn moved out.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.
