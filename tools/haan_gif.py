"""Tekent de laadanimatie van Gallo: een haan die korrels oppikt en zo een laadbalk vult, en kraait als hij klaar is.

Uitvoer: app/src/main/res/raw/haan_laden.gif (640 x 200 px = 320 x 100 dp op het tv-scherm).
Opnieuw maken: python3 tools/haan_gif.py  (vereist Pillow)
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

W = 320          # eindmaat in pixels
S = 4            # supersampling voor zachte randen
MS = 70          # per beeld; 94 beelden = ruim 6,5 s per lus

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


TRACK = (26, 46, 56)


def rooster(head=0.0, beak=0.0, blink=False, flap=0.0, legs=(0.0, 0.0), tilt=0.0, bob=0.0):
    """Haan op transparante laag (W*S), gekanteld rond de voeten."""
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


def place(canvas, layer, x, y, scale):
    """Zet de haan met zijn voeten-midden (160,280) op (x,y) in het canvas (beide supersampled)."""
    size = int(W * S * scale)
    small = layer.resize((size, size), Image.LANCZOS)
    canvas.alpha_composite(small, (int(x * S - 160 * S * scale), int(y * S - 280 * S * scale)))


def save(frames, path, ms):
    """Eén gedeeld palet voor alle beelden, met de achtergrondkleur als doorzichtige index."""
    w, hgt = frames[0].size
    sheet = Image.new("RGB", (w, hgt * len(frames)))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, i * hgt))
    pal = sheet.quantize(colors=255, method=Image.MEDIANCUT, dither=Image.NONE)
    q = [f.quantize(palette=pal, dither=Image.NONE) for f in frames]
    q[0].save(path, save_all=True, append_images=q[1:], duration=ms, loop=0,
              transparency=q[0].getpixel((0, 0)), disposal=2)


def bar(d, x0, x1, y, frac):
    d.rounded_rectangle([p(x0, y - 4), p(x1, y + 4)], radius=4 * S, fill=TRACK)
    if frac > 0:
        d.rounded_rectangle([p(x0, y - 4), p(x0 + (x1 - x0) * frac, y + 4)], radius=4 * S, fill=CHAMPAGNE)


CW, CH, SC = 640, 200, 0.5
GROUND, BAR_Y = 168, 186
GRAINS = [160 + i * 46 for i in range(10)]  # 160 .. 574
BEAK_DX = (278 - 160) * SC  # snavelpunt bij het pikken, t.o.v. de voeten


def pik_frames():
    frames = []
    per = 8
    for k, gx in enumerate(GRAINS):
        start = (GRAINS[k - 1] if k else 120) - BEAK_DX
        end = gx - BEAK_DX
        for j in range(per):
            img = Image.new("RGBA", (CW * S, CH * S), BG + (255,))
            d = ImageDraw.Draw(img)
            if j < 4:  # lopen
                t = (j + 1) / 4
                x = start + (end - start) * t
                legs = (22 * math.sin(t * math.pi * 2), -22 * math.sin(t * math.pi * 2))
                pose = dict(legs=legs, bob=2 * abs(math.sin(t * math.pi * 2)))
                eaten = k
            else:  # pikken
                x = end
                t = (j - 4) / 3
                down = math.sin(t * math.pi)
                pose = dict(head=-68 * down, tilt=-14 * down)
                eaten = k + (1 if j >= 6 else 0)
            bar(d, 40, 600, BAR_Y, (GRAINS[eaten - 1] - 40) / 560 if eaten else 0)
            for g in GRAINS[eaten:]:
                ell(d, g, GROUND + 6, 5, 4, CHAMP_DARK)
            ell(d, x, GROUND + 9, 40, 4, SHADOW + (255,))
            place(img, rooster(**pose), x, GROUND + 8, SC)
            frames.append(img.convert("RGB").resize((CW, CH), Image.LANCZOS))
    # klaar: kraaien
    for j in range(14):
        img = Image.new("RGBA", (CW * S, CH * S), BG + (255,))
        d = ImageDraw.Draw(img)
        bar(d, 40, 600, BAR_Y, 1)
        c = math.sin(min(1, j / 4) * math.pi / 2) * (1 - smooth(10, 13, j))
        x = GRAINS[-1] - BEAK_DX
        ell(d, x, GROUND + 9, 40, 4, SHADOW + (255,))
        place(img, rooster(head=28 * c, beak=c * (0.8 + 0.2 * math.sin(j)), flap=-25 * c * abs(math.sin(j))), x, GROUND + 8, SC)
        frames.append(img.convert("RGB").resize((CW, CH), Image.LANCZOS))
    return frames


def main():
    out = Path(__file__).resolve().parent.parent / "app/src/main/res/raw/haan_laden.gif"
    out.parent.mkdir(parents=True, exist_ok=True)
    save(pik_frames(), out, MS)
    print(out, out.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
