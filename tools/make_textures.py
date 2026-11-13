"""Draws the wheel textures (white or grey shades: the mod tints them) and the small icons.

Wheel parts are square canvases centred on the wheel centre, one sector pointing up, so the mod only rotates them by
45 degrees per sector. Sizes are in GUI pixels; the PNGs have 2 texture pixels per GUI pixel, drawn 8x and smoothed.
Run: python tools/make_textures.py  (writes src/client/resources/assets/beezzcraft/textures/gui/)
"""
import math, os
from PIL import Image, ImageDraw, ImageFilter

# must match Wheel.java
CENTER = 20                                  # apothem of the centre plate
RINGS = [(23, 51), (54, 82), (85, 113)]      # inner and outer apothem of each ring
GAP = 2.2                                    # gap between neighbouring sectors (GUI px)
ROUND = 4.5                                  # corner radius (GUI px)
EDGE = 1.0                                   # border thickness (GUI px)
GLOW = 5                                     # glow spread (GUI px)
SS = 8
RES = 2
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "client", "resources", "assets", "beezzcraft", "textures", "gui")
HALF = math.radians(22.5)


def canvas_size(apothem):
    return int(math.ceil(apothem / math.cos(HALF))) * 2 + 4 + 2 * GLOW


def new(size):
    return Image.new("RGBA", (size * SS, size * SS), (0, 0, 0, 0))


def rounded(points, r):
    """Polygon with every corner replaced by an arc of radius r (points in texture px)."""
    out = []
    n = len(points)
    for i in range(n):
        p0, p1, p2 = points[i - 1], points[i], points[(i + 1) % n]
        v1 = (p0[0] - p1[0], p0[1] - p1[1]); v2 = (p2[0] - p1[0], p2[1] - p1[1])
        l1, l2 = math.hypot(*v1), math.hypot(*v2)
        u1 = (v1[0] / l1, v1[1] / l1); u2 = (v2[0] / l2, v2[1] / l2)
        ang = math.acos(max(-1, min(1, u1[0] * u2[0] + u1[1] * u2[1])))
        d = min(r / math.tan(ang / 2), l1 / 2.1, l2 / 2.1)
        rr = d * math.tan(ang / 2)
        a = (p1[0] + u1[0] * d, p1[1] + u1[1] * d)
        b = (p1[0] + u2[0] * d, p1[1] + u2[1] * d)
        bis = (u1[0] + u2[0], u1[1] + u2[1]); bl = math.hypot(*bis)
        c = (p1[0] + bis[0] / bl * rr / math.sin(ang / 2), p1[1] + bis[1] / bl * rr / math.sin(ang / 2))
        a0, a1 = math.atan2(a[1] - c[1], a[0] - c[0]), math.atan2(b[1] - c[1], b[0] - c[0])
        da = (a1 - a0 + math.pi) % (2 * math.pi) - math.pi
        for k in range(9):
            t = a0 + da * k / 8
            out.append((c[0] + rr * math.cos(t), c[1] + rr * math.sin(t)))
    return out


def sector(a_in, a_out, size, shrink=0.0):
    c = size * SS / 2
    a_in, a_out = a_in + shrink, a_out - shrink
    t = math.tan(HALF)
    side = GAP / 2 + shrink
    pts = []
    for a, sign in ((a_out, -1), (a_out, 1), (a_in, 1), (a_in, -1)):
        half_w = a * t - side / math.cos(HALF)
        pts.append((c + sign * half_w * SS, c - a * SS))
    return rounded(pts, max(0.5, ROUND - shrink) * SS)


def octagon(a, size, shrink=0.0):
    c = size * SS / 2
    r = (a - shrink) / math.cos(HALF)
    pts = [(c + r * SS * math.cos(math.radians(22.5 + 45 * i)), c + r * SS * math.sin(math.radians(22.5 + 45 * i))) for i in range(8)]
    return rounded(pts, max(0.5, ROUND + 1 - shrink) * SS)


def save(img, size, name):
    img.resize((size * RES, size * RES), Image.LANCZOS).save(os.path.join(OUT, name))


def shaded(shape, size, a_in, a_out):
    """Filled shape, a little brighter towards its outer side: soft depth once tinted."""
    img = new(size)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).polygon(shape, fill=255)
    grad = Image.new("L", img.size, 0)
    gd = ImageDraw.Draw(grad)
    c = size * SS / 2
    for y in range(img.size[1]):
        dist = (c - y) / SS  # how far up from the centre
        t = 0 if a_out == a_in else max(0.0, min(1.0, (dist - a_in) / (a_out - a_in)))
        gd.line([(0, y), (img.size[0], y)], fill=int(205 + 50 * t))
    white = Image.merge("RGBA", (grad, grad, grad, mask))
    img.alpha_composite(white)
    return img


def outline(shape_outer, shape_inner, size):
    img = new(size)
    d = ImageDraw.Draw(img)
    d.polygon(shape_outer, fill=(255, 255, 255, 255))
    d.polygon(shape_inner, fill=(0, 0, 0, 0))
    return img


def glow(shape, size):
    """Soft halo around the shape (blurred at the final resolution: cheap)."""
    small = Image.new("L", (size * RES, size * RES), 0)
    k = RES / SS
    ImageDraw.Draw(small).polygon([(x * k, y * k) for x, y in shape], fill=255)
    halo = small.filter(ImageFilter.GaussianBlur(GLOW * RES / 2))
    out = Image.merge("RGBA", (Image.new("L", small.size, 255),) * 3 + (halo,))
    return out


def icons():
    """16x16 icons drawn at 8x: gear, hourglass, mouse buttons, wheel."""
    def canvas():
        return Image.new("RGBA", (16 * SS, 16 * SS), (0, 0, 0, 0))

    def put(img, name):
        img.resize((16 * RES, 16 * RES), Image.LANCZOS).save(os.path.join(OUT, name))

    s = SS
    # gear: 8 teeth around a ring
    g = canvas(); d = ImageDraw.Draw(g)
    c = 8 * s
    pts = []
    for i in range(16):
        r = 7.2 * s if i % 2 == 0 else 5.4 * s
        for j in (-1, 1):
            a = math.radians(i * 22.5 + j * 7)
            pts.append((c + r * math.cos(a), c + r * math.sin(a)))
    d.polygon(pts, fill=(255, 255, 255, 255))
    d.ellipse([c - 2.4 * s, c - 2.4 * s, c + 2.4 * s, c + 2.4 * s], fill=(0, 0, 0, 0))
    put(g, "icon_gear.png")
    # hourglass
    h = canvas(); d = ImageDraw.Draw(h)
    d.rounded_rectangle([3 * s, 1.5 * s, 13 * s, 3 * s], 0.7 * s, fill=(255, 255, 255, 255))
    d.rounded_rectangle([3 * s, 13 * s, 13 * s, 14.5 * s], 0.7 * s, fill=(255, 255, 255, 255))
    d.polygon([(4 * s, 3 * s), (12 * s, 3 * s), (8.7 * s, 8 * s), (12 * s, 13 * s), (4 * s, 13 * s), (7.3 * s, 8 * s)], fill=(255, 255, 255, 255))
    d.polygon([(5.6 * s, 4.2 * s), (10.4 * s, 4.2 * s), (8 * s, 7.3 * s)], fill=(0, 0, 0, 0))
    put(h, "icon_queue.png")
    # mouse with the left / right button lit, and with the wheel lit
    for name, part in (("mouse_left", "l"), ("mouse_right", "r"), ("mouse_wheel", "w")):
        m = canvas(); d = ImageDraw.Draw(m)
        body = [3.5 * s, 1 * s, 12.5 * s, 15 * s]
        d.rounded_rectangle(body, 4.2 * s, fill=(110, 116, 132, 255))
        d.rounded_rectangle([4.5 * s, 2 * s, 11.5 * s, 14 * s], 3.4 * s, fill=(40, 44, 54, 255))
        d.line([(8 * s, 2 * s), (8 * s, 7 * s)], fill=(110, 116, 132, 255), width=int(0.8 * s))
        d.line([(4.5 * s, 7 * s), (11.5 * s, 7 * s)], fill=(110, 116, 132, 255), width=int(0.8 * s))
        lit = (250, 204, 21, 255)
        if part == "l":
            d.pieslice([4.5 * s, 2 * s, 11.5 * s, 12 * s], 180, 270, fill=lit)
            d.rectangle([4.6 * s, 6 * s, 7.6 * s, 6.6 * s], fill=lit)
        elif part == "r":
            d.pieslice([4.5 * s, 2 * s, 11.5 * s, 12 * s], 270, 360, fill=lit)
            d.rectangle([8.4 * s, 6 * s, 11.4 * s, 6.6 * s], fill=lit)
        else:
            d.rounded_rectangle([7.1 * s, 3 * s, 8.9 * s, 6.4 * s], 0.9 * s, fill=lit)
        put(m, name + ".png")


def main():
    os.makedirs(OUT, exist_ok=True)
    for i, (a_in, a_out) in enumerate(RINGS, 1):
        size = canvas_size(a_out)
        shape = sector(a_in, a_out, size)
        save(shaded(shape, size, a_in, a_out), size, f"ring{i}_fill.png")
        save(outline(shape, sector(a_in, a_out, size, EDGE), size), size, f"ring{i}_edge.png")
        glow(shape, size).save(os.path.join(OUT, f"ring{i}_glow.png"))
    size = canvas_size(CENTER)
    shape = octagon(CENTER, size)
    save(shaded(shape, size, 0, 0), size, "center_fill.png")
    save(outline(shape, octagon(CENTER, size, EDGE), size), size, "center_edge.png")
    glow(shape, size).save(os.path.join(OUT, "center_glow.png"))
    # one big soft shadow under the whole wheel
    size = canvas_size(RINGS[-1][1])
    glow(octagon(RINGS[-1][1] - 2, size), size).save(os.path.join(OUT, "shadow.png"))
    icons()
    print("canvas sizes (GUI px):", {f"ring{i}": canvas_size(r[1]) for i, r in enumerate(RINGS, 1)}, "center", canvas_size(CENTER))


main()
