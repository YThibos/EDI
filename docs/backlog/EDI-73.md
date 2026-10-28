# EDI-73 EDI's concept art in the repository

| Field | Value |
|---|---|
| Key | EDI-73 |
| Kind | ship, a change to the repository, on branch `feature/EDI-73` |
| Wave and order | wave 5, order 32 |
| Source | EDI-52 plan, slice 20 |
| Size | S |
| Blocked by | [EDI-89](EDI-89.md) |
| Status | needs-captain-machine: the two source images exist only on the captain's machine and are not in the repository. |
| Cloud session | not yet: see the status |

## Goal

EDI's two concept images go into the repository as base art for later use, with a third image of the face on a pure black background for a terminal wallpaper.

## Context

The captain has two concept arts of EDI, a face shot and a wider image, each 2752 by 1536 pixels and about 2.8 MB, and asked for them to be placed in the repository as base art for the console, the README and a terminal wallpaper.
The face shot is wanted on a black background.
The research is in [the EDI-52 plan](research/EDI-52-plan.md), slice 20.

## Acceptance criteria

- The two originals are committed under a folder such as `docs/art/`, as they are or re-encoded without visible loss if size matters, with a short README naming what each image is and stating their source as the captain's concept art.
- A third image is the face shot cut out on a pure black background, at a size fit for a terminal wallpaper.
- The black-background image has no trace of the bridge behind it at the edges of the hair and the shoulders, and reads well behind light terminal text at 1920 by 1080.
- `./mvnw verify` and the publish check pass; no tracked file names a person, an employer or a machine path.
- The captain's own terminal configuration is not touched; the report gives the `background` snippet he can paste.

## Requirements from decisions and answers

- D42 A and the captain's note of 26 October 2026: put the two concept arts somewhere in the repository for further use as base art; the face shot would be great with a black background.
- The terminal wallpaper is a `background` layer with an image file in the captain's own terminal configuration, with an opacity or tint that keeps text readable; that change stays outside EDI.

## Research and measurements

- Both images are 2752 by 1536 pixels, about 2.8 MB each.

## Not for the cloud session

The two source images exist only on the captain's machine and are not in the repository.
A cloud session cannot fetch them.
The item is listed as needs-captain-machine; if the captain adds the originals to the repository in a commit of his own, the remaining work (the README and the black-background cut-out) can move to a cloud session, and the item's status is then changed to open.

## Read first

- [console-look](../decisions/console-look.md), `README.md`, `edi-console/` assets, [machine-and-person-specifics](../decisions/machine-and-person-specifics.md).

## How to verify

- View each image at full size and at the wallpaper size.
- `./mvnw verify` and the publish check.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.
