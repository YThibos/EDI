# EDI-171 plan: EDI's wallpaper follows her state

This is a plan, not a decision: what EDI-171 shipped is recorded in [wallpaper](../../decisions/wallpaper.md), and this report explores what could follow.
It was written on 3 November 2026, and every path in it is as the code stood that day.

## 1. Where it stands

- The core draws `$EDI_HOME/state/wallpaper.png`, an animated PNG of EDI's face that blinks every `wallpaper.blinkEvery`, 15 seconds by default.
- `AnimatedPng` joins ready PNG frames at the byte level, so a new expression is new frames and a new loop of `Glimpse`s, and no image is drawn at run time.
- WezTerm shows the file as a background layer, plays it only while its window has focus, and reloads it when its configuration reloads.

## 2. What her face could tell

The core already knows each of these states; the wallpaper would only show them.

| Mood | When | What her face does | Source in the core |
|---|---|---|---|
| Calm | nothing waits and nothing runs | blinks, cyan visor, as today | `FleetSnapshot.isQuiet` |
| Working | workers run, nothing waits | cyan visor with a slow scan line, or the HUD lines flickering | `FleetSnapshot.ongoing` |
| Attention | a decision, a board, a worker's question or a review waits on the captain | static purple visor with faint question marks, mirrored as seen from outside, one to three by how many wait | `FleetSnapshot.waiting` (`WaitingOnTheCaptain`) |
| Bad news | a pipeline failed, a worker stopped, a cleanup was refused | amber visor with a single mirrored exclamation mark | the setbacks the turn report already lists |
| Quiet or away | presence is quiet or away | eyes half lidded, visor dimmed, blinking half as often | `PresenceQuery`, `PresenceMode` |
| Demo | a demo holds | the visor bright, the mirrored line "Command accepted" | presence's demo |
| Offline | the core is down | the face grey, still, the visor dark | the age of the mood file, read by WezTerm (section 4) |

One mood shows at a time, chosen in the order bad news, attention, working, quiet or away, calm, so what needs the captain wins over what merely happens.

![The attention mood: a purple visor with mirrored question marks](images/EDI-171-attention-visor.jpg)

The picture above is a quick procedural mock-up of the attention mood on the existing art: the cyan of the visor turned to purple, and three question marks and a short line written mirrored on the glass, since the visor faces her and the viewer reads it from behind.

## 3. How the art is made

- A. Procedural, as the blink is: a script recolours the visor by hue inside its frame, writes mirrored glyphs with a glow, and dims or greys the face; cheap, repeatable from `docs/images/EDI-face.jpg`, but limited to what a recolour and an overlay can do.
- B. Painted variants: the captain makes the closed eyes, the half-lidded eyes and the moods with an image model from the concept art, and a script cuts the eye and visor regions into patches; the best faces, but every change is a session of hand work.
- C. Both: procedural visor moods and glyphs, painted lids.

Recommended: C, starting with A for the visor moods, since the visor is a graphic the script can recolour cleanly, while the lids are where procedural art shows its seams.

## 4. How the terminal learns the mood

- A. One file, redrawn: the core redraws `state/wallpaper.png` for the mood whenever it changes, and WezTerm's watch list reloads it.
  Nothing new in `.wezterm.lua`, but every change reloads the whole configuration, the animation restarts, and a watch across the WSL boundary may miss the change.
- B. A mood file read by a small Lua module: the core keeps one animation per mood under `state/wallpaper/` and writes `state/wallpaper/mood` with the mood's name and the time, each round; a module the repository ships, such as `ops/wezterm/edi.lua`, which the captain's `.wezterm.lua` requires, reads it every two seconds with `wezterm.time.call_after` and swaps the layer through `window:set_config_overrides`.
  A change shows within two seconds without a reload, and a mood file older than a few minutes shows the offline face, which no other choice can.
- C. A user variable through the terminal: the primary session's terminal sends WezTerm's `SetUserVar` escape sequence, and the `user-var-changed` event swaps the layer for that pane only.
  Push and per pane, but it needs a spike to show that Claude Code lets the sequence through, and it is dark whenever the primary is not running.

Recommended: B, because it works across the WSL boundary without a file watch, it can show the core being down, and the Lua stays small and in the repository, while the captain's own file only requires it.

## 5. How the frames are composed

A mood changes the visor, which covers her left eye, so the blink under a purple visor is not the blink under a cyan one.

- A. One full face per mood, with its own blink patches: simplest, about 3 MB of resources per mood.
- B. One face, with the mood as a second WezTerm layer: a transparent PNG over the face, the same size, so `Cover` places both alike; tiny files, and the blink stays one animation, but a translucent purple over cyan looks muddier than a recolour.
- C. One face, with the mood as patch frames: the first frame of every loop is the plain face, so a cyan visor would flash for one frame at the start of each loop.

Recommended: A for the visor moods, as patches of the visor region are small enough that only the first frame of each mood is a full face; B is the fallback if the native image grows too much.

## 6. Slices

1. The mood in the domain: `WallpaperMood` chosen from the fleet snapshot and the presence by the order in section 2, with its tests; S.
2. The attention mood's art and loop: the purple visor, the mirrored question marks for one, two and three or more, the blink under it; S.
3. The mood file and the Lua module of section 4 B, with the guide's snippet updated and the offline face; M.
4. Bad news, working, quiet and demo, one slice each once the captain has seen the attention mood on the real terminal; XS each.
5. Painted lids, if the captain makes them, replacing the procedural ones; XS.

## 7. Questions for the captain

1. Which of the moods in section 2 are wanted, and is the order right?
2. Art: A, B or C of section 3; recommended C.
3. Delivery to the terminal: A, B or C of section 4; recommended B.
4. Should the question marks count what waits, or always be the same mark?
5. Should EDI blink more often in some moods, such as a double blink when attention has waited a while?
