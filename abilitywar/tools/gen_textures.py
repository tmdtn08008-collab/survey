#!/usr/bin/env python3
"""Ability War 텍스처 생성기 (이펙트/아이콘). 실행: python3 tools/gen_textures.py"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageChops

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures')
FX = os.path.join(ROOT, 'fx')
GUI = os.path.join(ROOT, 'gui')
os.makedirs(FX, exist_ok=True)
os.makedirs(GUI, exist_ok=True)
rng = np.random.default_rng(1337)
random.seed(1337)


def save(arr, path):
    arr = np.clip(arr, 0, 1)
    img = Image.fromarray((arr * 255).astype(np.uint8), 'RGBA')
    img.save(path)


def grid(n, m=None):
    m = m or n
    y, x = np.mgrid[0:m, 0:n].astype(np.float32)
    return (x + 0.5) / n * 2 - 1, (y + 0.5) / m * 2 - 1


def white_alpha(a):
    h, w = a.shape
    out = np.ones((h, w, 4), np.float32)
    out[..., 3] = np.clip(a, 0, 1)
    return out


def value_noise(w, h, scale, octaves=4, seed=0, tile=True):
    r = np.random.default_rng(seed)
    total = np.zeros((h, w), np.float32)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        gw = max(2, int(scale * (2 ** o)))
        gh = max(2, int(scale * (2 ** o) * h / w))
        g = r.random((gh, gw)).astype(np.float32)
        img = Image.fromarray((g * 255).astype(np.uint8))
        if tile:
            big = Image.new('L', (gw * 3, gh * 3))
            for i in range(3):
                for j in range(3):
                    big.paste(img, (i * gw, j * gh))
            big = big.resize((w * 3, h * 3), Image.BICUBIC)
            arr = np.asarray(big, np.float32)[h:2 * h, w:2 * w] / 255
        else:
            arr = np.asarray(img.resize((w, h), Image.BICUBIC), np.float32) / 255
        total += arr * amp
        norm += amp
        amp *= 0.5
    return total / norm


# ---------------------------------------------------------------- basic sprites
def glow():
    x, y = grid(128)
    r = np.sqrt(x * x + y * y)
    a = np.exp(-(r ** 2) * 5.5) * (1 - np.clip(r, 0, 1) ** 4)
    save(white_alpha(a), os.path.join(FX, 'glow.png'))


def ring(name, width, soft, size=256):
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    a = np.exp(-((r - (0.92 - width)) / width) ** 2 * soft)
    a *= (r < 1.0)
    save(white_alpha(a), os.path.join(FX, name))


def shockwave():
    x, y = grid(256)
    r = np.sqrt(x * x + y * y)
    a = np.clip((r - 0.45) / 0.45, 0, 1) ** 2.2 * np.clip((1.0 - r) / 0.06, 0, 1)
    n = value_noise(256, 256, 6, 4, 3)
    a *= 0.75 + 0.5 * n
    save(white_alpha(a), os.path.join(FX, 'shockwave.png'))


def spark():
    x, y = grid(64)
    a = np.exp(-np.abs(x) * 14) * np.exp(-np.abs(y) * 2.2) + np.exp(-np.abs(y) * 14) * np.exp(-np.abs(x) * 2.2)
    r = np.sqrt(x * x + y * y)
    a += np.exp(-r * r * 30) * 1.2
    save(white_alpha(np.clip(a, 0, 1)), os.path.join(FX, 'spark.png'))


def flare():
    x, y = grid(128)
    r = np.sqrt(x * x + y * y)
    a = np.exp(-r * r * 22) * 1.3 + np.exp(-np.abs(y) * 40) * np.exp(-np.abs(x) * 1.6) * 0.8 + np.exp(-r * r * 4) * 0.25
    save(white_alpha(np.clip(a, 0, 1)), os.path.join(FX, 'flare.png'))


def smoke():
    x, y = grid(128)
    r = np.sqrt(x * x + y * y)
    n = value_noise(128, 128, 3, 5, 7)
    a = np.clip(1 - r * (0.9 + 0.5 * (n - 0.5) * 2), 0, 1) ** 1.6
    a *= 0.4 + 0.8 * n
    save(white_alpha(a), os.path.join(FX, 'smoke.png'))


def streak():
    x, y = grid(128, 32)
    a = np.exp(-(y ** 2) * 12) * np.clip(1 - np.abs(x), 0, 1) ** 0.7
    a *= np.clip((x + 1) * 1.5, 0, 1)
    save(white_alpha(a), os.path.join(FX, 'streak.png'))


def impact():
    size = 256
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    ang = np.arctan2(y, x)
    spikes = np.zeros_like(r)
    for i in range(18):
        a0 = rng.random() * math.tau
        wdt = 0.02 + rng.random() * 0.06
        ln = 0.5 + rng.random() * 0.5
        d = np.abs(np.angle(np.exp(1j * (ang - a0))))
        spikes = np.maximum(spikes, np.exp(-(d / wdt) ** 2) * np.clip(1 - r / ln, 0, 1))
    core = np.exp(-r * r * 30)
    a = np.clip(spikes * 1.1 + core * 1.3 + np.exp(-r * r * 6) * 0.25, 0, 1)
    save(white_alpha(a), os.path.join(FX, 'impact.png'))


def wisp():
    x, y = grid(64, 128)
    n = value_noise(64, 128, 2, 4, 11)
    xx = x + (n - 0.5) * 0.6 * (1 - y) * 0.5
    a = np.exp(-(xx ** 2) * 8) * np.clip((1 - y) / 2, 0, 1) ** 0.8 * np.clip((y + 1) * 2, 0, 1)
    save(white_alpha(a), os.path.join(FX, 'wisp.png'))


def shadow_pool():
    size = 256
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    ang = np.arctan2(y, x)
    n = value_noise(size, size, 5, 5, 19)
    edge = 0.62 + 0.18 * np.sin(ang * 7 + n * 6) * 0.5 + (n - 0.5) * 0.25
    a = np.clip((edge - r) / 0.25 + 0.5, 0, 1)
    a = a ** 1.2
    out = np.zeros((size, size, 4), np.float32)
    out[..., 3] = a
    # 가장자리 보랏빛
    rim = np.clip(1 - np.abs(r - edge + 0.08) / 0.1, 0, 1) * 0.6
    out[..., 0] = rim * 0.45
    out[..., 1] = rim * 0.15
    out[..., 2] = rim * 0.8
    save(out, os.path.join(FX, 'shadow_pool.png'))


def tendril():
    w, h = 256, 32
    x, y = grid(w, h)
    n = value_noise(w, h, 8, 4, 23)
    core = np.exp(-(y ** 2) * 18)
    edge = np.exp(-((np.abs(y) - 0.45) ** 2) * 60) * (0.6 + 0.6 * n)
    a = np.clip(core * 0.9 + edge * 0.9 + np.exp(-(y ** 2) * 3) * 0.3 * n, 0, 1)
    save(white_alpha(a), os.path.join(FX, 'tendril.png'))


def swirl():
    # 강착원반: u = 각도(가로 타일), v = 반지름 (0=안쪽)
    w, h = 1024, 128
    u = (np.arange(w, dtype=np.float32) + 0.5) / w
    v = (np.arange(h, dtype=np.float32) + 0.5) / h
    U, V = np.meshgrid(u, v)
    n1 = value_noise(w, h, 24, 5, 31)
    n2 = value_noise(w, h, 6, 4, 37)
    # 나선형 줄무늬 (반지름 따라 위상 이동)
    streaks = 0.5 + 0.5 * np.sin((U * 40 + V * 6 + n2 * 3) * math.tau)
    streaks = streaks ** 3
    base = (1 - V) ** 1.6
    inner = np.exp(-((V - 0.06) / 0.08) ** 2)
    a = np.clip(base * (0.35 + 0.65 * n1) * (0.5 + 0.7 * streaks) + inner * 0.9, 0, 1)
    a *= np.clip(V / 0.03, 0, 1) * np.clip((1 - V) / 0.25, 0, 1)
    out = np.zeros((h, w, 4), np.float32)
    # 색: 안쪽 흰보라 -> 바깥 짙은 보라/자홍
    t = V
    out[..., 0] = np.clip(1.0 - t * 0.35, 0, 1)
    out[..., 1] = np.clip(0.85 - t * 1.1, 0, 1)
    out[..., 2] = 1.0
    out[..., 3] = a
    save(out, os.path.join(FX, 'swirl.png'))


def rift():
    w, h = 128, 256
    x, y = grid(w, h)
    shape = np.abs(x) / np.clip(np.sqrt(np.clip(1 - y ** 2, 0, 1)) * 0.35 + 1e-3, 1e-3, 9)
    a = np.clip(1.2 - shape, 0, 1)
    glowa = np.exp(-shape ** 2 * 0.8) * np.clip(1 - np.abs(y), 0, 1)
    out = np.zeros((h, w, 4), np.float32)
    out[..., 0] = 0.75
    out[..., 1] = 0.55
    out[..., 2] = 1.0
    out[..., 3] = np.clip(glowa * 0.8 + a * 0.4, 0, 1)
    save(out, os.path.join(FX, 'rift.png'))


def crack():
    size = 512
    img = Image.new('L', (size, size), 0)
    d = ImageDraw.Draw(img)
    c = size / 2
    for i in range(14):
        ang = i / 14 * math.tau + random.uniform(-0.2, 0.2)
        x, y = c, c
        length = random.uniform(0.55, 0.95) * c
        steps = 12
        wdt = 7
        for s in range(steps):
            ang += random.uniform(-0.45, 0.45)
            nx = x + math.cos(ang) * length / steps
            ny = y + math.sin(ang) * length / steps
            d.line([(x, y), (nx, ny)], fill=255, width=max(1, int(wdt)))
            if random.random() < 0.3:
                ba = ang + random.choice([-1, 1]) * random.uniform(0.5, 1.0)
                bl = random.uniform(10, 40)
                d.line([(nx, ny), (nx + math.cos(ba) * bl, ny + math.sin(ba) * bl)], fill=200, width=max(1, int(wdt * 0.6)))
            x, y = nx, ny
            wdt *= 0.84
    img = img.filter(ImageFilter.GaussianBlur(1.2))
    a = np.asarray(img, np.float32) / 255
    xg, yg = grid(size)
    r = np.sqrt(xg * xg + yg * yg)
    a = np.clip(a * 1.4, 0, 1) * np.clip((1 - r) / 0.2, 0, 1)
    a = np.maximum(a, np.exp(-r * r * 18) * 0.8)
    save(white_alpha(a), os.path.join(FX, 'crack.png'))


# ---------------------------------------------------------------- magic circles
def glyph(d, cx, cy, s, seed, width):
    r = random.Random(seed)
    pts = []
    for _ in range(r.randint(3, 5)):
        pts.append((cx + r.uniform(-s, s), cy + r.uniform(-s, s)))
    d.line(pts, fill=255, width=width)
    if r.random() < 0.5:
        rr = s * r.uniform(0.2, 0.4)
        px, py = pts[r.randrange(len(pts))]
        d.ellipse([px - rr, py - rr, px + rr, py + rr], outline=255, width=width)


def magic_circle(name, variant):
    S = 1024
    img = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(img)
    c = S / 2
    def circ(rad, w):
        d.ellipse([c - rad, c - rad, c + rad, c + rad], outline=255, width=w)
    if variant == 0:
        circ(500, 10); circ(470, 4); circ(380, 6); circ(355, 3); circ(170, 5); circ(150, 3)
        # 룬 고리
        n = 36
        for i in range(n):
            a = i / n * math.tau
            glyph(d, c + math.cos(a) * 420, c + math.sin(a) * 420, 22, i * 7 + 3, 4)
        # 육각성
        for k in range(2):
            pts = []
            for i in range(3):
                a = i / 3 * math.tau + k * math.pi / 3 - math.pi / 2
                pts.append((c + math.cos(a) * 355, c + math.sin(a) * 355))
            d.polygon(pts, outline=255, width=5)
        # 내부 별
        pts = []
        for i in range(16):
            a = i / 16 * math.tau
            rr = 340 if i % 2 == 0 else 180
            pts.append((c + math.cos(a) * rr, c + math.sin(a) * rr))
        d.polygon(pts, outline=255, width=3)
        for i in range(12):
            a = i / 12 * math.tau
            x, y = c + math.cos(a) * 262, c + math.sin(a) * 262
            d.ellipse([x - 24, y - 24, x + 24, y + 24], outline=255, width=4)
            glyph(d, x, y, 12, 100 + i, 3)
        for i in range(8):
            a = i / 8 * math.tau
            d.line([(c + math.cos(a) * 150, c + math.sin(a) * 150), (c + math.cos(a) * 60, c + math.sin(a) * 60)], fill=255, width=4)
        circ(60, 6)
    else:
        circ(500, 6); circ(440, 14); circ(300, 4)
        n = 64
        for i in range(n):
            a = i / n * math.tau
            l0 = 450 if i % 4 else 300
            d.line([(c + math.cos(a) * 500, c + math.sin(a) * 500), (c + math.cos(a) * l0, c + math.sin(a) * l0)], fill=255, width=3)
        for i in range(24):
            a = i / 24 * math.tau
            glyph(d, c + math.cos(a) * 370, c + math.sin(a) * 370, 26, 500 + i, 4)
        pts = []
        for i in range(10):
            a = i / 10 * math.tau * 3
            pts.append((c + math.cos(a) * 300, c + math.sin(a) * 300))
        d.line(pts + [pts[0]], fill=255, width=4)
        circ(120, 8)
    img = img.filter(ImageFilter.GaussianBlur(1.0))
    a = np.asarray(img.resize((512, 512), Image.LANCZOS), np.float32) / 255
    blur = np.asarray(img.filter(ImageFilter.GaussianBlur(10)).resize((512, 512), Image.LANCZOS), np.float32) / 255
    a = np.clip(a * 1.25 + blur * 0.7, 0, 1)
    save(white_alpha(a), os.path.join(FX, name))


def rune_shield():
    S = 512
    img = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(img)
    c = S / 2
    for k, rad in enumerate([240, 200, 120]):
        pts = [(c + math.cos(i / 6 * math.tau + math.pi / 6) * rad, c + math.sin(i / 6 * math.tau + math.pi / 6) * rad) for i in range(6)]
        d.polygon(pts, outline=255, width=8 if k == 0 else 4)
    for i in range(18):
        a = i / 18 * math.tau
        glyph(d, c + math.cos(a) * 165, c + math.sin(a) * 165, 14, 900 + i, 3)
    for i in range(6):
        a = i / 6 * math.tau + math.pi / 6
        d.line([(c, c), (c + math.cos(a) * 240, c + math.sin(a) * 240)], fill=150, width=3)
    img = img.filter(ImageFilter.GaussianBlur(1.0))
    a = np.asarray(img.resize((256, 256), Image.LANCZOS), np.float32) / 255
    x, y = grid(256)
    r = np.sqrt(x * x + y * y)
    fill = np.clip(1 - r, 0, 1) ** 0.6 * 0.22
    blur = np.asarray(img.filter(ImageFilter.GaussianBlur(8)).resize((256, 256), Image.LANCZOS), np.float32) / 255
    a = np.clip(a * 1.2 + blur * 0.6 + fill, 0, 1)
    save(white_alpha(a), os.path.join(FX, 'rune_shield.png'))


# ---------------------------------------------------------------- GUI
def vignette():
    x, y = grid(256)
    r = np.sqrt(x * x * 0.8 + y * y)
    a = np.clip((r - 0.55) / 0.75, 0, 1) ** 1.7
    out = np.zeros((256, 256, 4), np.float32)
    out[..., 3] = a
    save(out, os.path.join(GUI, 'vignette.png'))


def icon_base(size=128):
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    out = np.zeros((size, size, 4), np.float32)
    # 짙은 보라 배경 + 안쪽 빛
    t = np.clip(r, 0, 1)
    out[..., 0] = 0.10 + 0.20 * (1 - t) ** 2
    out[..., 1] = 0.03 + 0.06 * (1 - t) ** 2
    out[..., 2] = 0.20 + 0.35 * (1 - t) ** 2
    out[..., 3] = 1
    n = value_noise(size, size, 4, 4, 77)
    out[..., :3] *= 0.8 + 0.4 * n[..., None]
    return out, x, y, r


def paste_glyph(out, mask, color, glow_amt=1.0):
    m = np.asarray(mask, np.float32) / 255
    g = np.asarray(mask.filter(ImageFilter.GaussianBlur(mask.size[0] / 22)), np.float32) / 255
    col = np.array(color, np.float32)
    out[..., :3] = out[..., :3] * (1 - g[..., None] * 0.5 * glow_amt) + col * g[..., None] * 0.9 * glow_amt
    out[..., :3] = out[..., :3] * (1 - m[..., None]) + np.array([1, 0.95, 1]) * m[..., None] * 0.95 + col * m[..., None] * 0.1
    return out


def icon(name, draw_fn, color=(0.72, 0.45, 1.0)):
    S = 128
    out, x, y, r = icon_base(S)
    mask = Image.new('L', (S * 4, S * 4), 0)
    d = ImageDraw.Draw(mask)
    draw_fn(d, S * 4)
    mask = mask.filter(ImageFilter.GaussianBlur(2)).resize((S, S), Image.LANCZOS)
    out = paste_glyph(out, mask, color)
    # 테두리 어둡게
    edge = np.clip((np.maximum(np.abs(x), np.abs(y)) - 0.86) / 0.14, 0, 1)
    out[..., :3] *= (1 - edge[..., None] * 0.7)
    img = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), 'RGBA').resize((64, 64), Image.LANCZOS)
    img.save(os.path.join(GUI, name))


def hand_open(d, cx, cy, s, ang=0, width=0):
    # 손바닥 + 손가락 (펼친 손)
    def rot(px, py):
        ca, sa = math.cos(ang), math.sin(ang)
        return (cx + px * ca - py * sa, cy + px * sa + py * ca)
    palm = [rot(-0.32 * s, 0.05 * s), rot(0.32 * s, 0.05 * s), rot(0.28 * s, 0.55 * s), rot(-0.28 * s, 0.55 * s)]
    d.polygon(palm, fill=255)
    for i, (fx, ln) in enumerate([(-0.24, 0.55), (-0.08, 0.68), (0.08, 0.65), (0.24, 0.52)]):
        x0, y0 = rot(fx * s, 0.08 * s)
        x1, y1 = rot(fx * s * 1.15, (0.08 - ln) * s)
        d.line([(x0, y0), (x1, y1)], fill=255, width=int(s * 0.13))
        d.ellipse([x1 - s * 0.065, y1 - s * 0.065, x1 + s * 0.065, y1 + s * 0.065], fill=255)
    tx, ty = rot(-0.33 * s, 0.35 * s)
    ex, ey = rot(-0.62 * s, 0.05 * s)
    d.line([(tx, ty), (ex, ey)], fill=255, width=int(s * 0.13))


def draw_s1(d, S):
    c = S / 2
    # 촉수가 뻗어 나가는 손
    hand_open(d, c - S * 0.12, c + S * 0.12, S * 0.42, ang=0.8)
    pts = []
    for i in range(40):
        t = i / 39
        pts.append((c - S * 0.02 + t * S * 0.42, c - S * 0.05 - t * S * 0.32 + math.sin(t * 9) * S * 0.04))
    d.line(pts, fill=255, width=int(S * 0.035))
    ex, ey = pts[-1]
    for k in range(3):
        a = -0.6 + k * 0.6
        d.line([(ex, ey), (ex + math.cos(a - 0.8) * S * 0.09, ey + math.sin(a - 0.8) * S * 0.09)], fill=255, width=int(S * 0.025))


def draw_s2(d, S):
    c = S / 2
    # 주먹 + 파동
    fx, fy, fs = c - S * 0.2, c + S * 0.05, S * 0.18
    d.rounded_rectangle([fx - fs, fy - fs * 0.9, fx + fs * 0.9, fy + fs * 0.9], radius=fs * 0.35, fill=255)
    for i in range(3):
        rr = S * (0.18 + i * 0.12)
        d.arc([c + S * 0.02 - rr, c + S * 0.05 - rr, c + S * 0.02 + rr, c + S * 0.05 + rr], start=-50, end=50, fill=255, width=int(S * 0.035))
    for i in range(5):
        y = c - S * 0.2 + i * S * 0.1
        d.line([(c - S * 0.45, y), (c - S * 0.36, y)], fill=200, width=int(S * 0.02))


def draw_s3(d, S):
    c = S / 2
    # 여러 주먹 잔상 + 아래 화살표
    for i in range(4):
        ox = c - S * 0.28 + i * S * 0.15
        oy = c - S * 0.22 + (i % 2) * S * 0.08
        fs = S * 0.085
        d.rounded_rectangle([ox - fs, oy - fs, ox + fs, oy + fs], radius=fs * 0.4, outline=255, width=int(S * 0.025))
    d.polygon([(c - S * 0.16, c + S * 0.05), (c + S * 0.16, c + S * 0.05), (c, c + S * 0.36)], fill=255)
    d.line([(c - S * 0.4, c + S * 0.4), (c + S * 0.4, c + S * 0.4)], fill=255, width=int(S * 0.03))
    for i in range(5):
        a = math.pi + i / 4 * math.pi
        d.line([(c, c + S * 0.38), (c + math.cos(a) * S * 0.22, c + S * 0.38 + math.sin(a) * S * 0.1)], fill=200, width=int(S * 0.02))


def draw_s4(d, S):
    c = S / 2
    # 육각 방패 + 반격 화살표
    pts = [(c + math.cos(i / 6 * math.tau + math.pi / 6) * S * 0.34, c + math.sin(i / 6 * math.tau + math.pi / 6) * S * 0.34) for i in range(6)]
    d.polygon(pts, outline=255, width=int(S * 0.04))
    pts2 = [(c + math.cos(i / 6 * math.tau + math.pi / 6) * S * 0.22, c + math.sin(i / 6 * math.tau + math.pi / 6) * S * 0.22) for i in range(6)]
    d.polygon(pts2, outline=200, width=int(S * 0.02))
    d.arc([c - S * 0.16, c - S * 0.16, c + S * 0.16, c + S * 0.16], start=200, end=500, fill=255, width=int(S * 0.04))
    ax, ay = c + S * 0.16 * math.cos(math.radians(140)), c + S * 0.16 * math.sin(math.radians(140))
    d.polygon([(ax - S * 0.07, ay - S * 0.02), (ax + S * 0.05, ay - S * 0.06), (ax, ay + S * 0.07)], fill=255)


def draw_ult(d, S):
    c = S / 2
    # 블랙홀: 검은 원 + 원반 고리
    for i in range(3):
        rr = S * (0.42 - i * 0.04)
        d.ellipse([c - rr, c - rr * 0.32, c + rr, c + rr * 0.32], outline=255, width=int(S * 0.02))
    d.ellipse([c - S * 0.2, c - S * 0.2, c + S * 0.2, c + S * 0.2], outline=255, width=int(S * 0.045))
    for i in range(10):
        a = i / 10 * math.tau
        r0, r1 = S * 0.26, S * 0.46
        pts = []
        for k in range(12):
            t = k / 11
            rr = r1 - (r1 - r0) * t
            aa = a + t * 1.4
            pts.append((c + math.cos(aa) * rr, c + math.sin(aa) * rr))
        d.line(pts, fill=150, width=int(S * 0.012))


def draw_passive(d, S):
    c = S / 2
    # 그림자 웅덩이에서 나오는 실루엣
    d.ellipse([c - S * 0.4, c + S * 0.22, c + S * 0.4, c + S * 0.42], fill=255)
    d.ellipse([c - S * 0.11, c - S * 0.36, c + S * 0.11, c - S * 0.14], outline=255, width=int(S * 0.04))
    d.polygon([(c - S * 0.18, c - S * 0.1), (c + S * 0.18, c - S * 0.1), (c + S * 0.12, c + S * 0.26), (c - S * 0.12, c + S * 0.26)], outline=255, width=int(S * 0.04))
    for i in range(4):
        x = c - S * 0.3 + i * S * 0.2
        d.line([(x, c + S * 0.24), (x + S * 0.03, c + S * 0.05)], fill=180, width=int(S * 0.02))


def draw_block(d, S):
    c = S / 2
    d.line([(c - S * 0.3, c + S * 0.3), (c + S * 0.3, c - S * 0.3)], fill=255, width=int(S * 0.09))
    d.line([(c + S * 0.3, c + S * 0.3), (c - S * 0.3, c - S * 0.3)], fill=255, width=int(S * 0.09))


def card_art():
    # 능력 선택 화면 카드
    W, H = 256, 160
    x, y = grid(W, H)
    out = np.zeros((H, W, 4), np.float32)
    n = value_noise(W, H, 5, 5, 99)
    r = np.sqrt((x * 1.6) ** 2 + (y * 1.0) ** 2)
    out[..., 0] = 0.05 + 0.25 * np.exp(-r * r * 2) + 0.05 * n
    out[..., 1] = 0.02 + 0.08 * np.exp(-r * r * 2)
    out[..., 2] = 0.10 + 0.45 * np.exp(-r * r * 2) + 0.08 * n
    out[..., 3] = 1
    # 블랙홀 그림
    core = np.exp(-((r - 0.35) / 0.05) ** 2) * 1.2
    disk = np.exp(-((y * 4) ** 2)) * np.clip(1 - np.abs(x), 0, 1) * (0.5 + 0.5 * n)
    hole = r < 0.32
    out[..., 0] += core * 0.8 + disk * 0.7
    out[..., 1] += core * 0.6 + disk * 0.3
    out[..., 2] += core * 1.0 + disk * 1.0
    out[..., :3][hole] = out[..., :3][hole] * 0.05
    save(out, os.path.join(GUI, 'card_void.png'))


if __name__ == '__main__':
    glow(); ring('ring.png', 0.035, 1.0); ring('ring_soft.png', 0.12, 1.0); shockwave(); spark(); flare(); smoke(); streak(); impact()
    wisp(); shadow_pool(); tendril(); swirl(); rift(); crack()
    magic_circle('magic_circle.png', 0); magic_circle('magic_circle2.png', 1); rune_shield()
    vignette(); card_art()
    # 공허 아이콘은 이제 tools/gen_hud.py 가 스킬바 그림에서 잘라 만든다 (아래 함수는 그림이 없는 능력용 예시)
    icon('void_block.png', draw_block)
    print('done')
