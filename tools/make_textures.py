"""Draws the octagon wheel textures (white, the mod tints them): one sector per ring pointing up, and the centre plate.

Every texture is a square canvas centred on the wheel centre, so the mod only has to rotate it by 45 degrees per sector.
Sizes are in GUI pixels; the PNGs have 2 texture pixels per GUI pixel, drawn 8x and smoothed down.
Run: python tools/make_textures.py  (writes src/client/resources/assets/beezzcraft/textures/gui/)
"""
import math, os
from PIL import Image, ImageDraw

# must match Wheel.java
CENTER = 20                                  # apothem of the centre plate
RINGS = [(23, 51), (54, 82), (85, 113)]      # inner and outer apothem of each ring
GAP = 1.2                                    # gap between neighbouring sectors (GUI px)
EDGE = 1.0                                   # border thickness (GUI px)
SS = 8                                       # drawn this much larger, then smoothed down
RES = 2                                      # texture pixels per GUI pixel (sharp at GUI scale 2)
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "client", "resources", "assets", "beezzcraft", "textures", "gui")
HALF = math.radians(22.5)


def canvas(r_out):
    size = int(math.ceil(r_out / math.cos(HALF))) * 2 + 4
    return size, Image.new("RGBA", (size * SS, size * SS), (0, 0, 0, 0))


def sector(a_in, a_out, size, shrink=0.0):
    """Trapezoid of one octagon sector pointing up, in texture pixels; shrink moves the edges inwards."""
    c = size * SS / 2
    a_in, a_out = a_in + shrink, a_out - shrink
    t = math.tan(HALF)
    side = GAP / 2 + shrink  # keep a gap to the neighbour sectors
    pts = []
    for a, sign in ((a_out, -1), (a_out, 1), (a_in, 1), (a_in, -1)):
        half_w = a * t - side / math.cos(HALF)
        pts.append((c + sign * half_w * SS, c - a * SS))
    return pts


def octagon(a, size, shrink=0.0):
    c = size * SS / 2
    r = (a - shrink) / math.cos(HALF)
    return [(c + r * SS * math.cos(math.radians(22.5 + 45 * i)), c + r * SS * math.sin(math.radians(22.5 + 45 * i))) for i in range(8)]


def save(img, size, name):
    img.resize((size * RES, size * RES), Image.LANCZOS).save(os.path.join(OUT, name))
    return size


def main():
    os.makedirs(OUT, exist_ok=True)
    sizes = {}
    for i, (a_in, a_out) in enumerate(RINGS, 1):
        size, fill = canvas(a_out)
        ImageDraw.Draw(fill).polygon(sector(a_in, a_out, size), fill=(255, 255, 255, 255))
        _, edge = canvas(a_out)
        d = ImageDraw.Draw(edge)
        d.polygon(sector(a_in, a_out, size), fill=(255, 255, 255, 255))
        d.polygon(sector(a_in, a_out, size, EDGE), fill=(0, 0, 0, 0))
        sizes[f"ring{i}"] = save(fill, size, f"ring{i}_fill.png")
        save(edge, size, f"ring{i}_edge.png")
    size, fill = canvas(CENTER)
    ImageDraw.Draw(fill).polygon(octagon(CENTER, size), fill=(255, 255, 255, 255))
    _, edge = canvas(CENTER)
    d = ImageDraw.Draw(edge)
    d.polygon(octagon(CENTER, size), fill=(255, 255, 255, 255))
    d.polygon(octagon(CENTER, size, EDGE), fill=(0, 0, 0, 0))
    sizes["center"] = save(fill, size, "center_fill.png")
    save(edge, size, "center_edge.png")
    print("canvas sizes (GUI px):", sizes)


main()
