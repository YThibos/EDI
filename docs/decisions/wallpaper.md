# The core draws EDI's wallpaper as an animated PNG in the EDI home, which the captain's terminal shows as its background

**Kind:** Architecture

## Context

The captain's terminal, WezTerm, shows a still picture of EDI's face behind the text, set in the captain's own `.wezterm.lua`, which stays outside EDI ([machine-and-person-specifics](machine-and-person-specifics.md)).
The captain asked for EDI to blink every so many seconds, the interval set in the personal configuration with the rest of her flavour, every 15 seconds by default, and for her expression to follow EDI's state later on.
WezTerm plays an animated GIF or PNG as a background layer while its window has focus, keeping each frame for the delay the file gives it, and reloads a picture whose file changed when its configuration reloads.
The core runs inside WSL on both machines, and WezTerm may run on the Windows side.
The native image carries only the resources it is told of, and has no image library of its own beyond what the JDK's desktop module brings.

## Decision

`EdiWallpaper` writes `$EDI_HOME/state/wallpaper.png` (`EdiHome.wallpaper`), an animated PNG that loops forever: the whole face with her eyes open, then the eyes half closed, closed and half closed again.
`Blink` in the domain holds how often EDI blinks, from `PT1S` to `PT10M`, and its `loop` of `Glimpse`s: the blink takes 170 milliseconds and the open eyes fill the rest of the interval.
`PersonalConfiguration.blink` reads it from the personal configuration's `wallpaper.blinkEvery`, `PT15S` when it is left out, and refuses a value that is not a duration in that range.
`DrawTheWallpaper` reads the setting once a minute (`edi.wallpaper.round`), and `EdiWallpaper` writes the file again only when the blink changed or the file is gone, through a temporary file and an atomic move; a refused setting keeps the last drawing and is logged once.
The frames are PNG resources under `wallpaper/` in the core, registered for the native image by `WallpaperArt`: the face at 1920 by 1072 pixels and two patches of the region around both eyes, each placing itself with a PNG `oFFs` chunk.
`AnimatedPng` joins them at the byte level: the face's image data becomes the first frame, each patch's image data a later frame over its region, with delays in centiseconds, so no image is decoded or encoded at run time.
`ops/wallpaper/blink-frames.py` drew the patches from `docs/images/EDI-face.jpg` by lowering each upper lid over its eye, and is run by hand when the art changes; it is not part of the build.
The captain's `.wezterm.lua` names the file and watches it with `wezterm.add_to_config_reload_watch_list`; [the wallpaper guide](../wallpaper.md) gives the snippet.

## Why

An animated file is all WezTerm needs: no WezTerm plugin, no Lua timer and no program on the Windows side, and the terminal keeps the frame timing.
The core already reads the personal configuration at run time and knows EDI's state, so the same writer can later draw her expression from that state.
Joining ready PNG frames at the byte level keeps image decoding out of the core and its native image, and the resources cost about 3 MB.
Patches over the eye region keep the animation barely larger than the face itself.
Writing only on a change keeps WezTerm from reloading its configuration every minute.

## Alternatives rejected

- An animated GIF - 256 colours band the chrome face.
- A blink timer in the captain's `.wezterm.lua` swapping two pictures - puts EDI's behaviour in a file EDI does not own, and reads the interval from a YAML file Lua has no parser for on every WezTerm version.
- Drawing the frames at run time with `java.awt` - brings the desktop module into the native image for a picture that never changes.
- Patching the delay into a committed animation - works for one interval, but leaves no way to compose expressions later.
- A full face for every frame - four times the size for the same picture.

## Consequences

- Good: changing how often EDI blinks is one line in the personal configuration, and a new expression is new frames and a new loop.
- Good: the animation is checked by tests that read its chunks back, and `ImageIO` reads its first frame as an ordinary PNG.
- Bad: the procedural lids are a first cut and show a little streaking under the visor during the 80 milliseconds her eyes are closed.
- Bad: the art matches `docs/images/EDI-face.jpg` only; another picture of EDI needs its eye curves measured in `blink-frames.py` and its frames drawn again.
- Bad: WezTerm blinks EDI only while its window has focus, and a watch across the WSL boundary may miss a new drawing until the configuration is reloaded by hand.

## When to revisit

When EDI's expression follows her state, when the face art changes, or when WezTerm plays animations without focus.

## How we got here

- 03/11/2026: the captain asked for EDI to blink on the terminal wallpaper every 15 seconds by default, set in the personal configuration, and for a plan to let her expression follow EDI's state (EDI-171).
