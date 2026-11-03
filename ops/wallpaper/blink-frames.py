import struct
import sys
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

WALLPAPER = (1920, 1072)
HALF_CLOSED = 0.5
CLOSED = 1.0
SEAM_DARKENING = 0.45

EYES = (
    dict(upper=[(1095, 652), (1120, 638), (1160, 624), (1200, 617), (1240, 619), (1270, 630), (1290, 660)],
         lower=[(1095, 653), (1120, 666), (1160, 679), (1200, 684), (1240, 684), (1270, 680), (1290, 670)],
         skin=56, lashes=28, ceiling=0),
    dict(upper=[(1490, 668), (1510, 652), (1540, 636), (1580, 626), (1620, 628), (1660, 644), (1685, 662)],
         lower=[(1490, 670), (1520, 680), (1560, 689), (1600, 689), (1640, 683), (1670, 673), (1685, 663)],
         skin=60, lashes=40, ceiling=586),
)


def main(face, resources):
    open_eyes = np.asarray(Image.open(face).convert("RGB")).astype(np.float32)
    frames = {name: wallpaper_sized(lidded(open_eyes, closing)) for name, closing in
              (("open", 0.0), ("half-closed", HALF_CLOSED), ("closed", CLOSED))}
    resources.mkdir(parents=True, exist_ok=True)
    frames["open"].save(resources / "face.png", optimize=True)
    left, top, right, bottom = changed_region(frames)
    for name in ("half-closed", "closed"):
        patch = resources / f"eyes-{name}.png"
        frames[name].crop((left, top, right, bottom)).save(patch, optimize=True)
        place(patch, left, top)


def place(patch, left, top):
    content = patch.read_bytes()
    after_header = 8 + 12 + struct.unpack(">I", content[8:12])[0]
    offset = b"oFFs" + struct.pack(">iiB", left, top, 0)
    chunk = struct.pack(">I", len(offset) - 4) + offset + struct.pack(">I", zlib.crc32(offset))
    patch.write_bytes(content[:after_header] + chunk + content[after_header:])


def lidded(face, closing):
    lidded_face = face.copy()
    if closing > 0:
        for eye in EYES:
            close_the_lid(face, lidded_face, eye, closing)
    return lidded_face


def close_the_lid(face, lidded_face, eye, closing):
    first, last = eye["upper"][0][0], eye["upper"][-1][0]
    for x in range(first, last + 1):
        upper, lower = curve(eye["upper"], x), curve(eye["lower"], x)
        travel = closing * (lower - upper)
        if travel >= 0.5:
            lower_the_column(face[:, x, :], lidded_face[:, x, :], eye, upper, travel)


def curve(points, x):
    xs, ys = zip(*points)
    return float(np.interp(x, xs, ys))


def lower_the_column(column, lidded_column, eye, upper, travel):
    edge = upper + travel
    crease = max(upper - eye["skin"], eye["ceiling"])
    rows = np.arange(int(crease), int(np.ceil(edge + 2)) + 1)
    stretched = crease + (rows - crease) * (upper - eye["lashes"] - crease) / (edge - eye["lashes"] - crease)
    source = np.where(rows < edge - eye["lashes"], stretched, rows - travel)
    below = np.floor(source).astype(int)
    weight = (source - below)[:, None]
    lidded_column[rows] = column[below] * (1 - weight) + column[below + 1] * weight
    seam = np.arange(int(round(edge)) - 1, int(round(edge)) + 3)
    lidded_column[seam] *= 1 - SEAM_DARKENING * min(1.0, travel / 20)


def wallpaper_sized(face):
    return Image.fromarray(np.clip(face, 0, 255).astype(np.uint8)).resize(WALLPAPER, Image.LANCZOS)


def changed_region(frames):
    open_eyes = np.asarray(frames["open"]).astype(int)
    changed = np.zeros(open_eyes.shape[:2], dtype=bool)
    for name in ("half-closed", "closed"):
        changed |= np.abs(np.asarray(frames[name]).astype(int) - open_eyes).max(axis=2) > 0
    rows, columns = np.nonzero(changed)
    return int(columns.min()), int(rows.min()), int(columns.max()) + 1, int(rows.max()) + 1


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
