# The wallpaper

EDI keeps an animated wallpaper of her face, which a terminal such as WezTerm shows behind the text ([wallpaper](decisions/wallpaper.md)).
The core draws it at `$EDI_HOME/state/wallpaper.png`, an animated PNG of EDI's face at 1920 by 1072 pixels in which she blinks.

## How often she blinks

The personal configuration's `wallpaper.blinkEvery` says how often EDI blinks, as a duration from `PT1S` to `PT10M`, every 15 seconds by default (see [Configuration](configuration.md#the-personal-configuration)).

```yaml
wallpaper:
  blinkEvery: PT15S
```

The core reads the setting once a minute and draws the wallpaper again only when it changed or the file is gone.
A setting it cannot read keeps the last drawing, and the core's log names the reason once.

## Showing it in WezTerm

The captain's own `.wezterm.lua` names the file; EDI never changes that file.
The snippet below darkens the face so the text stays readable, and asks WezTerm to reload its configuration, and so the picture, whenever the core draws it again.

```lua
local wezterm = require 'wezterm'
local config = wezterm.config_builder()

local edi_wallpaper = wezterm.home_dir .. '/.edi/state/wallpaper.png'
wezterm.add_to_config_reload_watch_list(edi_wallpaper)

config.background = {
  { source = { Color = 'black' }, width = '100%', height = '100%' },
  {
    source = { File = edi_wallpaper },
    height = 'Cover',
    horizontal_align = 'Center',
    vertical_align = 'Middle',
    hsb = { brightness = 0.3 },
  },
}

return config
```

- When WezTerm runs on Windows and EDI inside WSL, the path is the WSL one seen from Windows, such as `\\wsl.localhost\<distribution>\home\<user>\.edi\state\wallpaper.png`.
- WezTerm plays an animated background only while its window has focus, so EDI blinks while the captain works in the window and holds still otherwise.
- If WezTerm does not notice a new drawing, which a watch across the WSL boundary may miss, `Ctrl+Shift+R` reloads the configuration.

## The art

The frames are bundled in the core under `edi-core/src/main/resources/wallpaper/`: `face.png`, the whole face with her eyes open, and `eyes-half-closed.png` and `eyes-closed.png`, the region around both eyes, each carrying its place on the face in a PNG `oFFs` chunk.
`ops/wallpaper/blink-frames.py` drew them from `docs/images/EDI-face.jpg` by lowering each upper lid over its eye, and draws them again with Python 3, Pillow and NumPy:

```sh
python3 ops/wallpaper/blink-frames.py docs/images/EDI-face.jpg edi-core/src/main/resources/wallpaper
```

The core joins the frames into the animation itself, so changing how often EDI blinks needs no image tool at all.
