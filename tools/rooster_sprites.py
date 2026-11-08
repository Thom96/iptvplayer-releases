"""Draws the poses of the Gallo rooster for the loading screen.

The app (ui/LoadingRooster.kt) animates them: the rooster walks along a row of grains and pecks one up for every
loading step that is done, which fills the progress bar underneath. When everything is loaded, he crows.

Output: app/src/main/res/drawable-nodpi/rooster_*.png, 160 x 160 px (80 dp), centre of the feet at (80, 140).
Regenerate: python3 tools/rooster_sprites.py  (requires Pillow)
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

W = 320          # drawing area of the rooster; the images are saved at half size (160 px = 80 dp)
S = 4            # supersampling for smooth edges

CHAMPAGNE = (232, 204, 148)
CHAMP_DARK = (204, 172, 112)
CHAMP_DEEP = (170, 136, 80)
BRONZE = (140, 104, 58)
RED = (188, 62, 46)
RED_DARK = (150, 44, 34)
BEAK = (240, 178, 74)
BEAK_DARK = (206, 140, 50)
SILVER = (237, 243, 242)
INK = (11, 26, 34)
SHADOW = (5, 14, 19)


def p(x, y):
    return (x * S, y * S)


def ell(d, cx, cy, rx, ry, fill):
    d.ellipse([p(cx - rx, cy - ry), p(cx + rx, cy + ry)], fill=fill)


def rotate_layer(layer, angle, cx, cy, dx=0, dy=0):
    return layer.rotate(angle, resample=Image.BICUBIC, center=p(cx, cy), translate=(dx * S, dy * S))


def tail():
    # Sickle feathers from the rump, angled back and up, from dark to light.
    layer = Image.new("RGBA", (W * S, W * S), (0, 0, 0, 0))
    for angle, length, col in [(-62, 58, BRONZE), (-38, 64, CHAMP_DEEP), (-14, 56, CHAMP_DARK), (10, 44, BRONZE)]:
        f = Image.new("RGBA", (W * S, W * S), (0, 0, 0, 0))
        ell(ImageDraw.Draw(f), 112 - length, 188, length, 13, col)
        layer.alpha_composite(rotate_layer(f, angle, 112, 188))
    return layer


def wing_layer(flap):
    layer = Image.new("RGBA", (W * S, W * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    ell(d, 150, 192, 44, 30, CHAMP_DARK)
    for k in range(3):  # flight feathers
        ell(d, 118 + k * 6, 200 + k * 4, 22, 10, CHAMP_DEEP)
    ell(d, 158, 186, 30, 20, CHAMP_DARK)
    return rotate_layer(layer, flap, 182, 184)


def head_layer(beak_open, blink):
    layer = Image.new("RGBA", (W * S, W * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    # Neck
    d.line([p(184, 168), p(204, 108)], fill=CHAMPAGNE, width=46 * S)
    ell(d, 184, 168, 23, 23, CHAMPAGNE)
    # Comb: three circles, like in the logo
    ell(d, 192, 78, 10, 10, RED)
    ell(d, 206, 70, 12, 12, RED)
    ell(d, 221, 78, 10, 10, RED)
    # Head
    ell(d, 208, 102, 28, 27, CHAMPAGNE)
    # Beak (upper and lower; the lower one drops open)
    open_px = 14 * beak_open
    d.polygon([p(230, 94), p(258, 104), p(232, 108)], fill=BEAK)
    d.polygon([p(231, 108), p(254, 108 + open_px * 0.9), p(229, 116 + open_px * 0.3)], fill=BEAK_DARK)
    # Wattle
    ell(d, 228, 124, 7, 11, RED)
    ell(d, 222, 128, 6, 9, RED_DARK)
    # Eye
    if blink:
        d.line([p(208, 97), p(222, 97)], fill=INK, width=3 * S)
    else:
        ell(d, 215, 96, 6, 6, INK)
        ell(d, 217, 94, 2, 2, SILVER)
    return layer



def rooster(head=0.0, beak=0.0, blink=False, flap=0.0, legs=(0.0, 0.0), tilt=0.0, bob=0.0):
    """Rooster on a transparent layer (W*S), tilted around the feet."""
    img = Image.new("RGBA", (W * S, W * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for x, a in zip((148, 176), legs):
        r = math.radians(a)
        fx, fy = x + 42 * math.sin(r), 236 + 42 * math.cos(r)
        d.line([p(x, 236), p(fx, fy)], fill=BEAK_DARK, width=7 * S)
        d.line([p(fx, fy), p(fx + 16, fy + 2)], fill=BEAK_DARK, width=6 * S)
        d.line([p(fx, fy), p(fx - 10, fy + 3)], fill=BEAK_DARK, width=6 * S)
    body = Image.new("RGBA", img.size, (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    body.alpha_composite(tail())
    ell(bd, 160, 200, 62, 46, CHAMPAGNE)
    ell(bd, 190, 188, 36, 40, CHAMPAGNE)
    body.alpha_composite(rotate_layer(head_layer(beak, blink), head, 184, 168))
    body.alpha_composite(wing_layer(flap))
    body = body.transform(body.size, Image.AFFINE, (1, 0, 0, 0, 1, -bob * S), resample=Image.BICUBIC)
    img.alpha_composite(body)
    if tilt:
        img = img.rotate(tilt, resample=Image.BICUBIC, center=p(160, 280))
    return img


POSES = {
    "stand": dict(),
    "blink": dict(blink=True),
    # Walking: four positions of one step, with a little hop.
    **{f"walk_{j}": dict(legs=(22 * math.sin((j + 1) / 4 * math.tau), -22 * math.sin((j + 1) / 4 * math.tau)),
                         bob=2 * abs(math.sin((j + 1) / 4 * math.tau))) for j in range(4)},
    # Pecking: halfway and all the way down.
    "peck_1": dict(head=-68 * 0.6, tilt=-14 * 0.6),
    "peck_2": dict(head=-68, tilt=-14),
    # Crowing: head back, beak open, wing up.
    "crow_1": dict(head=28, beak=0.8, flap=-10),
    "crow_2": dict(head=28, beak=1.0, flap=-25),
}


def main():
    out = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"
    for name, pose in POSES.items():
        layer = rooster(**pose)
        # Downscale with premultiplied alpha, otherwise the edges get a dark fringe.
        small = layer.convert("RGBa").resize((W // 2, W // 2), Image.LANCZOS).convert("RGBA")
        path = out / f"rooster_{name}.png"
        small.save(path, optimize=True)
        print(path.name, path.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
