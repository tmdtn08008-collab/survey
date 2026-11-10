#!/usr/bin/env python3
"""흑마법사 텍스처: 소환진, 영혼(도깨비불), 사슬, 흑마법서 페이지, 빛나는 눈(스킨 위), Q 대시 아이콘."""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures')
FX = os.path.join(ASSETS, 'fx')
GUI = os.path.join(ASSETS, 'gui')
random.seed(31)


def save(a, path):
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'RGBA').save(path)


def to_arr(img):
    return np.asarray(img, np.float32) / 255


def additive(img, glow_r=6, glow_k=0.5):
    core = to_arr(img.filter(ImageFilter.GaussianBlur(0.8)))
    glow = to_arr(img.filter(ImageFilter.GaussianBlur(glow_r)))
    v = np.clip(core + glow * glow_k, 0, 1)
    out = np.zeros(v.shape + (4,), np.float32)
    for k in range(3):
        out[..., k] = v
    out[..., 3] = v
    return out


def circle():
    """강령술 소환진: 겹 원 + 해골 문양 + 룬 + 오망성 (흰색, 가산용)."""
    S = 512
    img = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(img)
    c = S / 2
    for rr, w in ((250, 5), (236, 2), (196, 4), (150, 2), (74, 3)):
        d.ellipse([c - rr, c - rr, c + rr, c + rr], outline=255, width=w)
    # 오망성
    pts = [(c + math.cos(-math.pi / 2 + i * 4 * math.pi / 5) * 194, c + math.sin(-math.pi / 2 + i * 4 * math.pi / 5) * 194) for i in range(5)]
    d.line(pts + [pts[0]], fill=255, width=4)
    # 바깥 고리의 룬
    r = random.Random(4)
    for i in range(28):
        a = i / 28 * math.tau
        x, y = c + math.cos(a) * 216, c + math.sin(a) * 216
        k = r.randint(0, 3)
        if k == 0:
            d.line([(x - 6, y - 8), (x + 6, y + 8)], fill=255, width=3)
            d.line([(x - 6, y + 8), (x + 6, y - 8)], fill=255, width=3)
        elif k == 1:
            d.line([(x, y - 9), (x, y + 9)], fill=255, width=3)
            d.line([(x - 6, y - 3), (x + 6, y - 3)], fill=255, width=3)
        elif k == 2:
            d.ellipse([x - 6, y - 6, x + 6, y + 6], outline=255, width=3)
        else:
            d.polygon([(x, y - 9), (x + 7, y + 6), (x - 7, y + 6)], outline=255)
    # 꼭짓점의 작은 해골
    for (x, y) in pts:
        d.ellipse([x - 14, y - 16, x + 14, y + 10], outline=255, width=3)
        d.ellipse([x - 8, y - 8, x - 2, y - 2], fill=255)
        d.ellipse([x + 2, y - 8, x + 8, y - 2], fill=255)
        d.line([(x - 6, y + 10), (x - 6, y + 16)], fill=255, width=2)
        d.line([(x, y + 10), (x, y + 16)], fill=255, width=2)
        d.line([(x + 6, y + 10), (x + 6, y + 16)], fill=255, width=2)
    # 가운데 해골
    d.ellipse([c - 34, c - 40, c + 34, c + 22], outline=255, width=4)
    d.ellipse([c - 20, c - 18, c - 6, c - 4], fill=255)
    d.ellipse([c + 6, c - 18, c + 20, c - 4], fill=255)
    for k in (-12, 0, 12):
        d.line([(c + k, c + 22), (c + k, c + 36)], fill=255, width=3)
    save(additive(img), os.path.join(FX, 'warlock_circle.png'))


def soul():
    """영혼 (도깨비불): 아래가 둥글고 위로 흔들리며 가늘어지는 불꽃 + 두 눈 구멍."""
    W, H = 128, 192
    img = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(img)
    pts = []
    for i in range(60):
        t = i / 59
        y = H - 28 - t * (H - 44)
        w = 40 * (1 - t) ** 0.7 * (1 + 0.15 * math.sin(t * 9))
        x = W / 2 + math.sin(t * 5.5) * 12 * t
        pts.append((x, y, w))
    left = [(x - w, y) for x, y, w in pts]
    right = [(x + w, y) for x, y, w in reversed(pts)]
    d.ellipse([W / 2 - 40, H - 68, W / 2 + 40, H - 4], fill=255)
    d.polygon(left + right, fill=255)
    core = to_arr(img.filter(ImageFilter.GaussianBlur(3)))
    glow = to_arr(img.filter(ImageFilter.GaussianBlur(12)))
    v = np.clip(core * 0.85 + glow * 0.6, 0, 1)
    # 눈 구멍 (어둡게)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    for ex in (W / 2 - 14, W / 2 + 14):
        eye = np.exp(-(((xx - ex) / 7) ** 2 + ((yy - (H - 44)) / 9) ** 2))
        v = v * (1 - 0.85 * eye)
    out = np.zeros((H, W, 4), np.float32)
    for k in range(3):
        out[..., k] = v
    out[..., 3] = v
    save(out, os.path.join(FX, 'warlock_soul.png'))


def chain():
    """어둠의 사슬 (가로로 반복): 쇠고리 + 녹색 빛 테두리."""
    W, H = 64, 32
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([2, 6, 38, 26], outline=(60, 64, 70, 255), width=6)
    d.ellipse([2, 6, 38, 26], outline=(120, 130, 140, 255), width=2)
    d.rectangle([30, 13, 62, 19], fill=(60, 64, 70, 255))
    d.line([(30, 14), (62, 14)], fill=(130, 140, 150, 255), width=1)
    a = to_arr(img)
    g = to_arr(img.filter(ImageFilter.GaussianBlur(2)))
    a[..., 1] = np.clip(a[..., 1] + g[..., 3] * 0.25 * (1 - a[..., 3]), 0, 1)
    save(a, os.path.join(FX, 'warlock_chain.png'))


def book():
    """흑마법서 펼친 두 페이지: 검은 종이 + 빛나는 녹색 마법진/글자."""
    W, H = 256, 128
    img = Image.new('RGBA', (W, H), (24, 20, 30, 255))
    d = ImageDraw.Draw(img)
    r = random.Random(8)
    for px in (0, W // 2):
        d.rectangle([px + 4, 4, px + W // 2 - 5, H - 5], outline=(70, 160, 110, 255), width=2)
        cx, cy = px + W // 4, H / 2 - 8
        d.ellipse([cx - 30, cy - 30, cx + 30, cy + 30], outline=(90, 230, 150, 255), width=2)
        pts = [(cx + math.cos(-math.pi / 2 + i * 4 * math.pi / 5) * 28, cy + math.sin(-math.pi / 2 + i * 4 * math.pi / 5) * 28) for i in range(5)]
        d.line(pts + [pts[0]], fill=(90, 230, 150, 255), width=1)
        for row in range(3):
            y = H - 34 + row * 9
            x = px + 14
            while x < px + W // 2 - 16:
                w = r.randint(4, 12)
                d.line([(x, y), (x + w, y)], fill=(80, 200, 140, 255), width=2)
                x += w + 4
    a = to_arr(img)
    xx = np.arange(W)[None, :]
    a[..., :3] *= (1 - 0.4 * np.exp(-((xx - W / 2) / 10.0) ** 2))[..., None]
    save(a, os.path.join(FX, 'warlock_book.png'))


def eyes():
    """스킨 위에 덮는 빛 (가산, 미리 곱한 알파): 녹색으로 빛나는 눈."""
    S = 64
    g = np.zeros((S, S, 4), np.float32)
    for (x, y) in [(9, 12), (10, 12), (13, 12), (14, 12)]:
        g[y, x, :3] = (0.35, 1.0, 0.65)
    g[..., 3] = np.clip(g[..., :3].max(-1), 0, 1)
    save(g, os.path.join(FX, 'warlock_glow_skin.png'))


def dash_icon():
    a = to_arr(Image.open(os.path.join(GUI, 'dash.png')).convert('RGBA'))
    lum = a[..., :3].max(-1)
    sat = lum - a[..., :3].min(-1)
    out = a.copy()
    out[..., 0] = lum - sat * 0.8
    out[..., 1] = lum
    out[..., 2] = lum - sat * 0.4
    save(out, os.path.join(GUI, 'dash_warlock.png'))


if __name__ == '__main__':
    circle(); soul(); chain(); book(); eyes(); dash_icon()
    print('done')
