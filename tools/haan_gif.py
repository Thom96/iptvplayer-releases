"""Tekent de laadanimatie van Gallo: een haan die wiebelt, knippert en kraait.

Uitvoer: app/src/main/res/raw/haan_laden.gif (320 x 320 px = 160 dp op het tv-scherm).
Opnieuw maken: python3 tools/haan_gif.py  (vereist Pillow)
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

W = 320          # eindmaat in pixels
S = 4            # supersampling voor zachte randen
FRAMES = 36
MS = 70          # per beeld; 36 x 70 ms = 2,5 s per lus

BG = (12, 28, 36)            # tussen Palette.background en backgroundTop, voor de randen
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


def smooth(a, b, t):
    if t <= a:
        return 0.0
    if t >= b:
        return 1.0
    x = (t - a) / (b - a)
    return x * x * (3 - 2 * x)


def rotate_layer(layer, angle, cx, cy, dx=0, dy=0):
    return layer.rotate(angle, resample=Image.BICUBIC, center=p(cx, cy), translate=(dx * S, dy * S))


def tail():
    # Sikkelveren vanaf de stuit, schuin naar achteren en omhoog, van donker naar licht.
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
    for k in range(3):  # slagpennen
        ell(d, 118 + k * 6, 200 + k * 4, 22, 10, CHAMP_DEEP)
    ell(d, 158, 186, 30, 20, CHAMP_DARK)
    return rotate_layer(layer, flap, 182, 184)


def head_layer(beak_open, blink):
    layer = Image.new("RGBA", (W * S, W * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    # Hals
    d.line([p(184, 168), p(204, 108)], fill=CHAMPAGNE, width=46 * S)
    ell(d, 184, 168, 23, 23, CHAMPAGNE)
    # Kam: drie rondjes, net als in het logo
    ell(d, 192, 78, 10, 10, RED)
    ell(d, 206, 70, 12, 12, RED)
    ell(d, 221, 78, 10, 10, RED)
    # Kop
    ell(d, 208, 102, 28, 27, CHAMPAGNE)
    # Snavel (boven en onder; onder zakt open)
    open_px = 14 * beak_open
    d.polygon([p(230, 94), p(258, 104), p(232, 108)], fill=BEAK)
    d.polygon([p(231, 108), p(254, 108 + open_px * 0.9), p(229, 116 + open_px * 0.3)], fill=BEAK_DARK)
    # Lel
    ell(d, 228, 124, 7, 11, RED)
    ell(d, 222, 128, 6, 9, RED_DARK)
    # Oog
    if blink:
        d.line([p(208, 97), p(222, 97)], fill=INK, width=3 * S)
    else:
        ell(d, 215, 96, 6, 6, INK)
        ell(d, 217, 94, 2, 2, SILVER)
    return layer


def frame(i):
    t = i / FRAMES
    crow = smooth(0.30, 0.42, t) * (1 - smooth(0.80, 0.92, t))
    beak = smooth(0.42, 0.48, t) * (1 - smooth(0.76, 0.82, t))
    beak *= 0.8 + 0.2 * math.sin(t * math.tau * 9)
    bob = 3 * math.sin(t * math.tau * 2) * (1 - crow)
    puff = -6 * crow
    blink = 0.12 <= t < 0.16 or 0.95 <= t
    flap = -28 * crow * (0.5 + 0.5 * math.sin(t * math.tau * 6))

    img = Image.new("RGB", (W * S, W * S), BG)
    d = ImageDraw.Draw(img)
    ell(d, 160, 284, 86 - crow * 6, 10, SHADOW)
    # Poten
    for x in (148, 176):
        d.line([p(x, 236), p(x, 278)], fill=BEAK_DARK, width=7 * S)
        d.line([p(x, 278), p(x + 16, 280)], fill=BEAK_DARK, width=6 * S)
        d.line([p(x, 278), p(x - 10, 281)], fill=BEAK_DARK, width=6 * S)

    body = Image.new("RGBA", (W * S, W * S), (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    body.alpha_composite(tail())
    ell(bd, 160, 200, 62, 46, CHAMPAGNE)
    ell(bd, 190, 188, 36, 40, CHAMPAGNE)  # borst
    body.alpha_composite(head_layer(beak, blink) if crow == 0 else
                         rotate_layer(head_layer(beak, blink), 28 * crow, 184, 168))
    body.alpha_composite(wing_layer(flap))
    body = body.transform(body.size, Image.AFFINE, (1, 0, 0, 0, 1, -(bob + puff) * S), resample=Image.BICUBIC)
    img.paste(body, (0, 0), body)

    # Kraaigolven vanuit de snavel
    if beak > 0.05:
        d = ImageDraw.Draw(img)
        th = math.radians(28 * crow)
        rx, ry = 262 - 184, 104 - 168  # snavelpunt t.o.v. het halsscharnier
        bx = 184 + rx * math.cos(th) + ry * math.sin(th)
        by = 168 - rx * math.sin(th) + ry * math.cos(th) + bob + puff
        for k in range(3):
            phase = (t * 4 + k / 3) % 1
            r = 8 + phase * 40
            a = int(255 * (1 - phase) * beak)
            col = tuple(int(BG[c] + (SILVER[c] - BG[c]) * a / 255) for c in range(3))
            d.arc([p(bx - r, by - r), p(bx + r, by + r)], start=-55, end=15, fill=col, width=4 * S)
    return img.resize((W, W), Image.LANCZOS)


def main():
    frames = [frame(i) for i in range(FRAMES)]
    # Eén gedeeld palet, met de achtergrondkleur als doorzichtige index.
    sheet = Image.new("RGB", (W, W * FRAMES))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, i * W))
    pal = sheet.quantize(colors=255, method=Image.MEDIANCUT, dither=Image.NONE)
    quant = [f.quantize(palette=pal, dither=Image.NONE) for f in frames]
    bg_index = quant[0].getpixel((0, 0))
    out = Path(__file__).resolve().parent.parent / "app/src/main/res/raw/haan_laden.gif"
    out.parent.mkdir(parents=True, exist_ok=True)
    quant[0].save(out, save_all=True, append_images=quant[1:], duration=MS, loop=0,
                  transparency=bg_index, disposal=2, optimize=False)
    print(out, out.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
