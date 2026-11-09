#!/usr/bin/env python3
"""
용기사 스킬바 원본 그림 (tools/art/dragon_hud_source.png) 생성.
공허 스킬바와 같은 배치(1907x825)라서 gen_hud.py 의 슬롯 좌표를 그대로 쓴다.
그 다음 `python3 tools/gen_hud.py` 로 HUD 텍스처/마스크/아이콘을 만든다.
"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), '..')
OUT = os.path.join(ROOT, 'tools', 'art', 'dragon_hud_source.png')
W, H = 1907, 825
FONT = '/usr/share/fonts/opentype/unifont/unifont.otf'
rnd = random.Random(7)

SLOTS = {
    'skill1': (97, 405, 372, 697, 14),
    'skill2': (428, 405, 705, 697, 14),
    'skill3': (760, 405, 1040, 697, 14),
    'skill4': (1092, 405, 1373, 697, 14),
    'ultimate': (1440, 362, 1848, 700, 22),
    'passive': (1183, 100, 1377, 272, 12),
}
LABELS = {
    'skill1': ((298, 598, 370, 694), '1'),
    'skill2': ((630, 598, 703, 694), '2'),
    'skill3': ((962, 598, 1038, 694), '3'),
    'skill4': ((1295, 598, 1370, 694), '4'),
    'ultimate': ((1786, 622, 1856, 702), 'R'),
    'passive': ((1078, 135, 1140, 225), 'T'),
}


# ------------------------------------------------------------------ 기본 도우미
def layer():
    return Image.new('RGBA', (W, H), (0, 0, 0, 0))


def arr(im):
    return np.asarray(im).astype(np.float32) / 255.0


def img(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'RGBA')


def over(base, top):
    return Image.alpha_composite(base, top)


def add(base, top, k=1.0):
    """가산 합성 (빛)."""
    b, t = arr(base), arr(top)
    a = t[..., 3:4] * k
    b[..., :3] = b[..., :3] + t[..., :3] * a
    b[..., 3:4] = np.maximum(b[..., 3:4], np.clip(a * 1.2, 0, 1))
    return img(b)


def glow(im, r, k=1.0):
    g = im.filter(ImageFilter.GaussianBlur(r))
    if k != 1.0:
        a = arr(g)
        a[..., 3] *= k
        g = img(a)
    return g


def octagon(x0, y0, x1, y1, c):
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c), (x0, y0 + c)]


def grow(box, e):
    x0, y0, x1, y1, c = box
    return (x0 - e, y0 - e, x1 + e, y1 + e, c + e * 0.42)


def poly_mask(pts):
    m = Image.new('L', (W, H), 0)
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return m


def vgrad(mask, top, bottom, y0, y1):
    """mask 안을 세로 그라데이션으로."""
    yy = np.linspace(0, 1, H)[:, None]
    t = np.clip((yy * H - y0) / max(1, (y1 - y0)), 0, 1)
    out = np.zeros((H, W, 4), np.float32)
    for k in range(3):
        out[..., k] = top[k] / 255 * (1 - t) + bottom[k] / 255 * t
    out[..., 3] = np.asarray(mask).astype(np.float32) / 255
    return img(out)


def radial(mask, cx, cy, rad, inner, outer, power=1.6):
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    t = np.clip(np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / rad, 0, 1) ** power
    out = np.zeros((H, W, 4), np.float32)
    for k in range(3):
        out[..., k] = inner[k] / 255 * (1 - t) + outer[k] / 255 * t
    out[..., 3] = np.asarray(mask).astype(np.float32) / 255
    return img(out)


def clip_to(lay, mask):
    a = arr(lay)
    a[..., 3] *= np.asarray(mask).astype(np.float32) / 255
    return img(a)


def bez(p0, p1, p2, n=48):
    pts = []
    for i in range(n):
        t = i / (n - 1)
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        pts.append((x, y))
    return pts


def taper(pts, wmax, head=0.6, tail=1.0, power=0.8):
    """곡선을 따라 가운데가 두꺼운 칼날 모양 다각형."""
    n = len(pts)
    left, right = [], []
    for i in range(n):
        a = pts[max(0, i - 1)]
        b = pts[min(n - 1, i + 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dy) or 1
        nx, ny = -dy / L, dx / L
        t = i / (n - 1)
        prof = math.sin(math.pi * min(1, t / head * 0.5 if t < head else 0.5 + (t - head) / (tail - head + 1e-6) * 0.5)) ** power
        w = wmax * prof
        left.append((pts[i][0] + nx * w, pts[i][1] + ny * w))
        right.append((pts[i][0] - nx * w, pts[i][1] - ny * w))
    return left + right[::-1]


def star4(d, cx, cy, r, col, thin=0.22):
    d.polygon([(cx, cy - r), (cx + r * thin, cy - r * thin), (cx + r, cy), (cx + r * thin, cy + r * thin),
               (cx, cy + r), (cx - r * thin, cy + r * thin), (cx - r, cy), (cx - r * thin, cy - r * thin)], fill=col)


def embers(lay, box, n, cols, rmin=4, rmax=14, seed=1):
    r = random.Random(seed)
    d = ImageDraw.Draw(lay)
    x0, y0, x1, y1 = box
    for _ in range(n):
        x, y = r.uniform(x0, x1), r.uniform(y0, y1)
        s = r.uniform(rmin, rmax)
        c = r.choice(cols)
        if r.random() < 0.55:
            star4(d, x, y, s, c)
        else:
            d.ellipse([x - s * 0.3, y - s * 0.3, x + s * 0.3, y + s * 0.3], fill=c)


# ------------------------------------------------------------------ 색
GOLD_HI = (255, 214, 140)
GOLD = (236, 140, 52)
BRONZE = (128, 46, 16)
DARK = (22, 6, 4)
EMBER = (255, 96, 24)
WHITE_HOT = (255, 244, 214)


# ------------------------------------------------------------------ 판 (프레임)
def plate(base, box, rim_w=24, horns=False):
    """금속 테두리 + 안쪽 배경."""
    sh = layer()
    ImageDraw.Draw(sh).polygon(octagon(*grow(box, rim_w + 10)), fill=(0, 0, 0, 200))
    base = over(base, glow(sh, 6))
    outer = poly_mask(octagon(*grow(box, rim_w + 4)))
    base = over(base, vgrad(outer, (40, 12, 6), (12, 3, 2), box[1] - rim_w, box[3] + rim_w))
    rim = poly_mask(octagon(*grow(box, rim_w)))
    base = over(base, vgrad(rim, GOLD_HI, BRONZE, box[1] - rim_w, box[3] + rim_w))
    inner = poly_mask(octagon(*grow(box, rim_w * 0.55)))
    base = over(base, vgrad(inner, (70, 22, 10), (24, 6, 3), box[1], box[3]))
    line = poly_mask(octagon(*grow(box, rim_w * 0.28)))
    base = over(base, vgrad(line, (255, 170, 80), (150, 52, 18), box[1], box[3]))
    # 비늘 무늬 테두리 (모서리 장식)
    d = layer()
    dd = ImageDraw.Draw(d)
    x0, y0, x1, y1, c = grow(box, rim_w * 0.78)
    for (px, py) in [(x0 + c * 0.5, y0 + c * 0.5), (x1 - c * 0.5, y0 + c * 0.5), (x0 + c * 0.5, y1 - c * 0.5), (x1 - c * 0.5, y1 - c * 0.5)]:
        s = rim_w * 0.55
        dd.polygon([(px, py - s), (px + s * 0.7, py), (px, py + s), (px - s * 0.7, py)], fill=(60, 14, 6, 255))
        s2 = s * 0.6
        dd.polygon([(px, py - s2), (px + s2 * 0.7, py), (px, py + s2), (px - s2 * 0.7, py)], fill=(255, 120, 40, 255))
    base = over(base, d)
    base = add(base, glow(d, 6), 0.5)
    return base


def slot_bg(base, box, hot=(122, 34, 12)):
    m = poly_mask(octagon(*box))
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    rad = max(box[2] - box[0], box[3] - box[1]) * 0.72
    base = over(base, radial(m, cx, cy - (box[3] - box[1]) * 0.08, rad, hot, (14, 3, 2)))
    # 은은한 비늘 무늬
    sc = layer()
    d = ImageDraw.Draw(sc)
    step = 34
    for j, y in enumerate(range(int(box[1]) - step, int(box[3]) + step, step // 2)):
        off = (step // 2) if j % 2 else 0
        for x in range(int(box[0]) - step + off, int(box[2]) + step, step):
            d.arc([x - step / 2, y - step / 2, x + step / 2, y + step / 2], 20, 160, fill=(255, 120, 60, 26), width=3)
    base = over(base, clip_to(sc, m))
    return base, m


def slot_vignette(base, box, m):
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    rad = max(box[2] - box[0], box[3] - box[1]) * 0.62
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    t = np.clip((np.maximum(np.abs(xx - cx) / ((box[2] - box[0]) / 2), np.abs(yy - cy) / ((box[3] - box[1]) / 2)) - 0.72) / 0.28, 0, 1)
    v = np.zeros((H, W, 4), np.float32)
    v[..., 3] = t ** 1.5 * 0.7 * np.asarray(m).astype(np.float32) / 255
    return over(base, img(v))


# ------------------------------------------------------------------ 아이콘
def lit(base, shape, m, core=WHITE_HOT, col=EMBER, g1=14, g2=34, k=1.0):
    """빛나는 모양: 넓은 번짐 + 좁은 번짐 + 하얀 심."""
    a = arr(shape)
    c = np.zeros_like(a)
    c[..., 0], c[..., 1], c[..., 2], c[..., 3] = col[0] / 255, col[1] / 255, col[2] / 255, a[..., 3]
    colored = img(c)
    base = add(base, clip_to(glow(colored, g2), m), 0.9 * k)
    base = add(base, clip_to(glow(colored, g1), m), 1.0 * k)
    cc = np.zeros_like(a)
    cc[..., 0], cc[..., 1], cc[..., 2], cc[..., 3] = core[0] / 255, core[1] / 255, core[2] / 255, a[..., 3]
    base = over(base, clip_to(img(cc), m))
    return base


def claw_set(d, cx, cy, ang, length, n=3, gap=40, w=13, bend=0.22):
    ca, sa = math.cos(ang), math.sin(ang)
    for i in range(n):
        o = (i - (n - 1) / 2) * gap
        L = length * (1.0 if i == (n - 1) // 2 else 0.86)
        p0 = (cx - ca * L / 2 - sa * o, cy - sa * L / 2 + ca * o)
        p2 = (cx + ca * L / 2 - sa * o, cy + sa * L / 2 + ca * o)
        mid = ((p0[0] + p2[0]) / 2 + sa * L * bend, (p0[1] + p2[1]) / 2 - ca * L * bend)
        d.polygon(taper(bez(p0, mid, p2), w, head=0.62), fill=(255, 255, 255, 255))


def icon_skill1(base, box, m):
    """용조: 돌진하며 할퀴는 세 줄 발톱 + 용혈 표식."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2 + 8, (y0 + y1) / 2 - 4
    # 속도선
    sp = layer()
    d = ImageDraw.Draw(sp)
    for i in range(7):
        y = y0 + 60 + i * 30
        L = rnd.uniform(50, 110)
        d.polygon(taper([(x0 + 18, y), (x0 + 18 + L, y)], 4, head=0.85), fill=(255, 255, 255, 170))
    base = lit(base, sp, m, core=(255, 200, 150), col=(255, 110, 40), g1=6, g2=14, k=0.6)
    # 어두운 그림자 발톱 (깊이감)
    shp = layer()
    claw_set(ImageDraw.Draw(shp), cx + 10, cy + 10, -0.95, 230, gap=52, w=20)
    base = over(base, clip_to(glow(img(arr(shp) * np.array([0, 0, 0, 0.7], np.float32)), 6), m))
    sh = layer()
    claw_set(ImageDraw.Draw(sh), cx, cy, -0.95, 230, gap=52, w=16)
    base = lit(base, sh, m, g1=10, g2=30)
    # 용혈 표식 (세로 동공 눈)
    mk = layer()
    d = ImageDraw.Draw(mk)
    ex, ey = x0 + 62, y0 + 62
    d.ellipse([ex - 30, ey - 30, ex + 30, ey + 30], outline=(255, 255, 255, 255), width=5)
    d.polygon([(ex, ey - 22), (ex + 7, ey), (ex, ey + 22), (ex - 7, ey)], fill=(255, 255, 255, 255))
    base = lit(base, mk, m, core=(255, 220, 160), col=(255, 60, 20), g1=6, g2=16, k=0.8)
    return base


def wing_poly(cx, cy, side, s):
    """용 날개 (side=1 오른쪽)."""
    root = (cx + side * 18 * s, cy + 10 * s)
    arm = (cx + side * 70 * s, cy - 62 * s)
    tip = (cx + side * 128 * s, cy - 48 * s)
    f1 = (cx + side * 132 * s, cy + 6 * s)
    f2 = (cx + side * 112 * s, cy + 48 * s)
    f3 = (cx + side * 72 * s, cy + 70 * s)
    pts = [root, arm, tip]
    # 막 가장자리 (오목한 곡선)
    for a, b in [(tip, f1), (f1, f2), (f2, f3), (f3, root)]:
        mid = ((a[0] + b[0]) / 2 - side * 16 * s, (a[1] + b[1]) / 2 - 4 * s)
        pts += bez(a, mid, b, 10)[1:]
    bones = [(arm, tip), (arm, f1), (arm, f2), (arm, f3), (root, arm)]
    return pts, bones


def icon_skill2(base, box, m):
    """용익타: 펼친 거대한 날개 + 돌풍."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 + 2
    # 돌풍 호
    gs = layer()
    d = ImageDraw.Draw(gs)
    for i, r in enumerate((60, 92, 124)):
        d.arc([cx - r * 1.1, cy + 40 - r * 0.45, cx + r * 1.1, cy + 40 + r * 0.45], 10, 170, fill=(255, 255, 255, 200 - i * 40), width=6 - i)
    base = lit(base, gs, m, core=(255, 220, 170), col=(255, 140, 50), g1=6, g2=16, k=0.7)
    for side in (-1, 1):
        pts, bones = wing_poly(cx, cy - 6, side, 1.0)
        mem = layer()
        ImageDraw.Draw(mem).polygon(pts, fill=(60, 12, 6, 255))
        base = over(base, clip_to(mem, m))
        # 막 안쪽 빛 (뒤에서 비치는 불빛)
        inner = layer()
        ImageDraw.Draw(inner).polygon(pts, fill=(255, 90, 30, 140))
        base = add(base, clip_to(glow(inner, 18), m), 0.6)
        rim = layer()
        dd = ImageDraw.Draw(rim)
        dd.line(pts + [pts[0]], fill=(255, 255, 255, 255), width=5)
        for a, b in bones:
            dd.line([a, b], fill=(255, 255, 255, 255), width=7)
        base = lit(base, rim, m, core=(255, 226, 170), col=(255, 110, 30), g1=7, g2=20, k=0.9)
    # 가운데 몸통 빛
    c = layer()
    ImageDraw.Draw(c).polygon([(cx, cy - 40), (cx + 16, cy + 10), (cx, cy + 60), (cx - 16, cy + 10)], fill=(255, 255, 255, 255))
    base = lit(base, c, m, g1=8, g2=24)
    return base


def icon_skill3(base, box, m):
    """용왕난무: 엇갈린 발톱 X + 휘감는 꼬리."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 - 6
    # 꼬리 소용돌이
    tl = layer()
    d = ImageDraw.Draw(tl)
    pts = []
    for i in range(80):
        t = i / 79
        a = -0.6 + t * 4.4
        r = 112 - t * 40
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r * 0.8))
    d.polygon(taper(pts, 10, head=0.25, power=0.6), fill=(255, 255, 255, 255))
    # 꼬리 가시
    for i in range(6, 70, 10):
        a = -0.6 + i / 79 * 4.4
        r = 112 - i / 79 * 40
        px, py = cx + math.cos(a) * r, cy + math.sin(a) * r * 0.8
        ox, oy = math.cos(a) * 22, math.sin(a) * 22 * 0.8
        d.polygon([(px - oy * 0.3, py + ox * 0.3), (px + ox, py + oy), (px + oy * 0.3, py - ox * 0.3)], fill=(255, 255, 255, 255))
    base = lit(base, tl, m, core=(180, 70, 40), col=(200, 40, 10), g1=8, g2=20, k=0.55)
    xs = layer()
    dd = ImageDraw.Draw(xs)
    claw_set(dd, cx, cy, -0.8, 236, gap=50, w=13)
    claw_set(dd, cx, cy, 0.8 + math.pi, 236, gap=50, w=13)
    base = lit(base, xs, m, g1=10, g2=30)
    # 교차점 섬광
    fl = layer()
    star4(ImageDraw.Draw(fl), cx, cy, 34, (255, 255, 255, 255), thin=0.16)
    base = lit(base, fl, m, g1=8, g2=26)
    return base


def icon_skill4(base, box, m):
    """역린 반격: 겹친 비늘 방패 + 반격 섬광."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    shield = [(cx, cy - 112), (cx + 92, cy - 76), (cx + 82, cy + 30), (cx, cy + 116), (cx - 82, cy + 30), (cx - 92, cy - 76)]
    sm = poly_mask(shield)
    base = over(base, vgrad(sm, (120, 36, 14), (36, 8, 4), cy - 112, cy + 116))
    # 비늘
    sc = layer()
    d = ImageDraw.Draw(sc)
    step = 40
    for j, y in enumerate(range(int(cy - 130), int(cy + 130), 26)):
        off = step // 2 if j % 2 else 0
        for x in range(int(cx - 120) + off, int(cx + 130), step):
            d.chord([x - step / 2, y - step * 0.55, x + step / 2, y + step * 0.55], 0, 180, fill=(80, 20, 8, 255), outline=(255, 150, 70, 255), width=3)
    sca = clip_to(sc, sm)
    base = over(base, sca)
    # 방패 테두리
    rim = layer()
    ImageDraw.Draw(rim).line(shield + [shield[0]], fill=(255, 255, 255, 255), width=8)
    base = lit(base, rim, m, core=GOLD_HI, col=(255, 130, 40), g1=8, g2=22)
    # 가운데 금 + 섬광
    cr = layer()
    d = ImageDraw.Draw(cr)
    d.line([(cx - 10, cy - 70), (cx + 8, cy - 28), (cx - 6, cy + 4), (cx + 14, cy + 48), (cx + 2, cy + 80)], fill=(255, 255, 255, 255), width=6)
    star4(d, cx + 2, cy - 10, 58, (255, 255, 255, 255), thin=0.14)
    base = lit(base, cr, m, g1=8, g2=26)
    return base


def flame_tongues(d, x0, y0, x1, y1, n, seed, col=(255, 255, 255, 255)):
    """(x0,y0)에서 오른쪽으로 퍼지는 불꽃 혀."""
    r = random.Random(seed)
    for i in range(n):
        t = (i + 0.5) / n
        ey = y0 + (t - 0.5) * (y1 - y0) * 2
        ex = x1 + r.uniform(-30, 20)
        mid = ((x0 + ex) / 2, (y0 + ey) / 2 + r.uniform(-25, 25))
        d.polygon(taper(bez((x0, y0), mid, (ex, ey), 30), r.uniform(10, 22), head=0.75, power=0.7), fill=col)


def icon_ultimate(base, box, m):
    """고룡 강림: 고룡의 머리와 화염 브레스."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    hx, hy = cx - 30, cy - 4
    # 뒤쪽 날개 (하나, 흐리게)
    pts, bones = wing_poly(hx - 120, hy - 10, -1, 1.15)
    wl = layer()
    ImageDraw.Draw(wl).polygon(pts, fill=(40, 9, 4, 255))
    base = over(base, clip_to(wl, m))
    r = layer()
    dd = ImageDraw.Draw(r)
    for a, b in bones:
        dd.line([a, b], fill=(255, 255, 255, 255), width=5)
    base = lit(base, r, m, core=(230, 120, 70), col=(170, 40, 12), g1=6, g2=14, k=0.45)
    # 브레스
    mx, my = hx + 78, hy + 18
    br = layer()
    d = ImageDraw.Draw(br)
    d.polygon([(mx, my - 6), (x1 + 30, my - 100), (x1 + 30, my + 120), (mx, my + 14)], fill=(255, 255, 255, 255))
    base = lit(base, br, m, core=(255, 150, 60), col=(255, 70, 14), g1=18, g2=44, k=0.8)
    tg = layer()
    flame_tongues(ImageDraw.Draw(tg), mx, my + 4, x1 + 10, my + 70, 9, 5)
    base = lit(base, tg, m, core=(255, 238, 190), col=(255, 120, 30), g1=8, g2=22)
    fl = layer()
    embers(fl, (mx + 60, my - 90, x1, my + 110), 22, [(255, 255, 255, 255), (255, 210, 120, 255)], 5, 14, seed=3)
    base = lit(base, fl, m, g1=4, g2=12, k=0.8)
    # 머리 (옆모습, 오른쪽, 입을 벌림)
    P = lambda x, y: (hx + x, hy + y)
    head = [P(-200, -6), P(-120, -44), P(-60, -66), P(-10, -72), P(18, -64), P(70, -50), P(118, -40), P(130, -28),
            P(124, -14), P(60, -4), P(44, 2), P(112, 34), P(108, 50), P(50, 44), P(-20, 52), P(-90, 66), P(-170, 96), P(-210, 80)]
    hm = poly_mask(head)
    base = over(base, vgrad(hm, (96, 26, 10), (26, 6, 3), hy - 80, hy + 100))
    # 비늘 결
    sc = layer()
    d = ImageDraw.Draw(sc)
    for j, yy in enumerate(range(int(hy - 70), int(hy + 100), 16)):
        off = 12 if j % 2 else 0
        for xx in range(int(hx - 220) + off, int(hx + 140), 24):
            d.arc([xx - 12, yy - 9, xx + 12, yy + 9], 20, 160, fill=(255, 140, 70, 60), width=2)
    base = over(base, clip_to(sc, hm))
    # 입 안 불빛
    mo = layer()
    ImageDraw.Draw(mo).polygon([P(44, 0), P(124, -12), P(110, 32)], fill=(255, 255, 255, 255))
    base = lit(base, mo, m, core=(255, 220, 140), col=(255, 90, 20), g1=8, g2=20)
    hr = layer()
    d = ImageDraw.Draw(hr)
    d.line(head + [head[0]], fill=(255, 255, 255, 255), width=6)
    # 뿔 (뒤로 휘어짐)
    for (bx, by, mx2, my2, ex, ey, w) in [(-40, -66, -110, -120, -190, -128, 17), (-90, -54, -150, -84, -220, -70, 12)]:
        d.polygon(taper(bez(P(bx, by), P(mx2, my2), P(ex, ey), 24), w, head=0.04, power=0.5), fill=(255, 255, 255, 255))
    # 목 가시
    for i in range(4):
        bx, by = -120 - i * 26, -40 + i * 12
        d.polygon([P(bx + 10, by), P(bx - 6, by - 30), P(bx - 12, by + 6)], fill=(255, 255, 255, 255))
    # 이빨
    for i in range(5):
        tx = 58 + i * 13
        d.polygon([P(tx, -6 + i * -1), P(tx + 5, 10), P(tx + 10, -7 - i)], fill=(255, 255, 255, 255))
        d.polygon([P(tx + 2, 38 - i * 2), P(tx + 7, 22 - i * 2), P(tx + 12, 40 - i * 2)], fill=(255, 255, 255, 255))
    base = lit(base, hr, m, core=(255, 226, 170), col=(255, 110, 30), g1=8, g2=22)
    # 눈
    ey_ = layer()
    ImageDraw.Draw(ey_).polygon([P(-4, -52), P(36, -46), P(4, -36)], fill=(255, 255, 255, 255))
    base = lit(base, ey_, m, core=(255, 255, 220), col=(255, 210, 40), g1=6, g2=20, k=1.3)
    return base


def icon_passive(base, box, m):
    """용화: 날개와 불꽃을 두른 용인."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 + 6
    s = 0.62
    for side in (-1, 1):
        pts, bones = wing_poly(cx, cy - 18, side, s)
        wl = layer()
        ImageDraw.Draw(wl).polygon(pts, fill=(60, 12, 6, 255))
        base = over(base, clip_to(wl, m))
        r = layer()
        dd = ImageDraw.Draw(r)
        dd.line(pts + [pts[0]], fill=(255, 255, 255, 255), width=4)
        for a, b in bones:
            dd.line([a, b], fill=(255, 255, 255, 255), width=4)
        base = lit(base, r, m, core=(255, 220, 160), col=(255, 100, 30), g1=5, g2=14, k=0.8)
    # 불꽃 오라
    au = layer()
    d = ImageDraw.Draw(au)
    for i in range(9):
        fx = cx - 52 + i * 13
        h = 30 + (i % 3) * 14
        d.polygon([(fx - 8, cy + 66), (fx, cy + 66 - h), (fx + 8, cy + 66)], fill=(255, 255, 255, 255))
    base = lit(base, au, m, core=(255, 220, 140), col=(255, 80, 20), g1=6, g2=16, k=0.9)
    # 블록 사람 (마인크래프트 체형)
    fig = layer()
    d = ImageDraw.Draw(fig)
    d.rectangle([cx - 15, cy - 62, cx + 15, cy - 32], fill=(30, 8, 4, 255))
    d.rectangle([cx - 17, cy - 30, cx + 17, cy + 16], fill=(30, 8, 4, 255))
    d.rectangle([cx - 31, cy - 30, cx - 19, cy + 12], fill=(30, 8, 4, 255))
    d.rectangle([cx + 19, cy - 30, cx + 31, cy + 12], fill=(30, 8, 4, 255))
    d.rectangle([cx - 16, cy + 18, cx - 2, cy + 58], fill=(30, 8, 4, 255))
    d.rectangle([cx + 2, cy + 18, cx + 16, cy + 58], fill=(30, 8, 4, 255))
    base = over(base, clip_to(fig, m))
    o = layer()
    d = ImageDraw.Draw(o)
    for r in [(cx - 15, cy - 62, cx + 15, cy - 32), (cx - 17, cy - 30, cx + 17, cy + 16), (cx - 31, cy - 30, cx - 19, cy + 12),
              (cx + 19, cy - 30, cx + 31, cy + 12), (cx - 16, cy + 18, cx - 2, cy + 58), (cx + 2, cy + 18, cx + 16, cy + 58)]:
        d.rectangle(r, outline=(255, 255, 255, 255), width=3)
    # 발톱
    for sx in (-25, 25):
        for k in (-5, 0, 5):
            d.line([(cx + sx + k, cy + 12), (cx + sx + k * 1.6, cy + 26)], fill=(255, 255, 255, 255), width=3)
    base = lit(base, o, m, core=(255, 210, 150), col=(255, 110, 40), g1=4, g2=10, k=0.8)
    eye = layer()
    d = ImageDraw.Draw(eye)
    d.rectangle([cx - 10, cy - 50, cx - 4, cy - 45], fill=(255, 255, 255, 255))
    d.rectangle([cx + 4, cy - 50, cx + 10, cy - 45], fill=(255, 255, 255, 255))
    base = lit(base, eye, m, core=(255, 255, 210), col=(255, 180, 40), g1=4, g2=10, k=1.2)
    return base


# ------------------------------------------------------------------ 위쪽 띠 (이름 + 패시브)
def banner(base):
    pts = [(70, 106), (1700, 106), (1760, 84), (1745, 128), (1870, 150), (1790, 196), (1880, 236), (1760, 258), (1700, 280),
           (70, 280), (36, 246), (36, 140)]
    sh = layer()
    ImageDraw.Draw(sh).polygon(pts, fill=(0, 0, 0, 200))
    base = over(base, glow(sh, 8))
    m = poly_mask(pts)
    base = over(base, vgrad(m, GOLD_HI, BRONZE, 84, 280))
    inner = [(84, 120), (1690, 120), (1720, 112), (1712, 140), (1800, 154), (1740, 196), (1806, 230), (1722, 248), (1690, 266),
             (84, 266), (54, 238), (54, 148)]
    im = poly_mask(inner)
    base = over(base, radial(im, 400, 193, 1300, (86, 24, 10), (16, 4, 2), 1.2))
    # 비늘 무늬 + 불꽃 줄기
    sc = layer()
    d = ImageDraw.Draw(sc)
    step = 30
    for j, y in enumerate(range(100, 290, 15)):
        off = step // 2 if j % 2 else 0
        for x in range(40 + off, 1880, step):
            d.arc([x - step / 2, y - step / 2, x + step / 2, y + step / 2], 20, 160, fill=(255, 120, 60, 22), width=2)
    base = over(base, clip_to(sc, im))
    fl = layer()
    d = ImageDraw.Draw(fl)
    for i in range(9):
        y = 150 + i * 13
        xs = 420 + i * 70
        p = bez((xs, y + 40), (xs + 300, y - 50 + (i % 3) * 30), (xs + 720, y + 10), 30)
        d.polygon(taper(p, 5 + (i % 3) * 2, head=0.4), fill=(255, 255, 255, 120))
    base = add(base, clip_to(glow(img(arr(fl) * np.array([1, 0.4, 0.12, 1], np.float32)), 6), im), 0.7)
    em = layer()
    embers(em, (300, 125, 1680, 262), 26, [(255, 220, 150, 255), (255, 130, 50, 255)], 4, 13, seed=11)
    base = add(base, glow(em, 3), 0.9)
    base = over(base, clip_to(em, im))
    # 왼쪽 장식: 용의 눈 보석
    gem = layer()
    d = ImageDraw.Draw(gem)
    d.polygon([(90, 150), (120, 193), (90, 236), (60, 193)], fill=(60, 10, 4, 255), outline=(255, 190, 110, 255), width=4)
    d.polygon([(90, 166), (98, 193), (90, 220), (82, 193)], fill=(255, 220, 120, 255))
    base = over(base, gem)
    g2 = layer()
    ImageDraw.Draw(g2).polygon([(90, 166), (98, 193), (90, 220), (82, 193)], fill=(255, 120, 30, 255))
    base = add(base, glow(g2, 12), 1.0)
    # 이름
    font = ImageFont.truetype(FONT, 96)
    tl = layer()
    d = ImageDraw.Draw(tl)
    d.text((150, 193), '용기사', font=font, anchor='lm', fill=(255, 255, 255, 255))
    base = add(base, glow(img(arr(tl) * np.array([1, 0.45, 0.12, 1], np.float32)), 10), 1.0)
    d2 = layer()
    ImageDraw.Draw(d2).text((150, 193), '용기사', font=font, anchor='lm', fill=(40, 8, 4, 255), stroke_width=10, stroke_fill=(40, 8, 4, 255))
    ImageDraw.Draw(d2).text((150, 193), '용기사', font=font, anchor='lm', fill=(255, 246, 228, 255), stroke_width=3, stroke_fill=(255, 246, 228, 255))
    base = over(base, d2)
    return base


def ult_ornaments(base, box):
    x0, y0, x1, y1, c = box
    # 뒤쪽 불꽃 혀
    fl = layer()
    d = ImageDraw.Draw(fl)
    for i in range(10):
        t = i / 9
        px = x0 + 10 + t * (x1 - x0 - 20)
        h = 50 + 40 * math.sin(t * math.pi) + rnd.uniform(-10, 10)
        d.polygon(taper(bez((px - 14, y0 - 6), (px + 16, y0 - h * 0.6), (px + 6, y0 - h), 16), 16, head=0.2, power=0.6), fill=(255, 255, 255, 255))
    a = arr(fl)
    col = a * np.array([1, 0.42, 0.1, 1], np.float32)
    base = add(base, glow(img(col), 10), 1.0)
    base = add(base, img(a * np.array([1, 0.7, 0.35, 0.85], np.float32)), 0.8)
    # 뿔 (위쪽 양 모서리)
    hl = layer()
    d = ImageDraw.Draw(hl)
    for side in (-1, 1):
        bx = x0 + c * 0.6 if side < 0 else x1 - c * 0.6
        by = y0 + c * 0.2
        p = bez((bx, by), (bx + side * 70, by - 40), (bx + side * 40, by - 120), 30)
        d.polygon(taper(p, 22, head=0.04, power=0.45), fill=(255, 255, 255, 255))
    hm = Image.fromarray((arr(hl)[..., 3] * 255).astype(np.uint8))
    base = over(base, glow(img(arr(hl) * np.array([0, 0, 0, 0.8], np.float32)), 5))
    base = over(base, vgrad(hm, (250, 226, 190), (120, 70, 40), y0 - 130, y0 + 20))
    ol = layer()
    for side in (-1, 1):
        bx = x0 + c * 0.6 if side < 0 else x1 - c * 0.6
        by = y0 + c * 0.2
        p = bez((bx, by), (bx + side * 70, by - 40), (bx + side * 40, by - 120), 30)
        ImageDraw.Draw(ol).line(taper(p, 22, head=0.04, power=0.45) + [p[0]], fill=(50, 16, 8, 255), width=4)
    base = over(base, ol)
    # 아래 가시
    sp = layer()
    d = ImageDraw.Draw(sp)
    for side in (-1, 1):
        bx = x0 + 20 if side < 0 else x1 - 20
        d.polygon([(bx, y1 - 10), (bx + side * 46, y1 + 44), (bx - side * 6, y1 + 22)], fill=(255, 255, 255, 255))
    sm = Image.fromarray((arr(sp)[..., 3] * 255).astype(np.uint8))
    base = over(base, vgrad(sm, GOLD_HI, BRONZE, y1 - 10, y1 + 44))
    return base


def labels(base):
    font = ImageFont.truetype(FONT, 112)
    for name, ((x0, y0, x1, y1), ch) in LABELS.items():
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        tl = layer()
        ImageDraw.Draw(tl).text((cx, cy), ch, font=font, anchor='mm', fill=(255, 140, 40, 255))
        base = add(base, glow(tl, 8), 0.7)
        t2 = layer()
        ImageDraw.Draw(t2).text((cx, cy), ch, font=font, anchor='mm', fill=(24, 6, 3, 255), stroke_width=11, stroke_fill=(24, 6, 3, 255))
        ImageDraw.Draw(t2).text((cx, cy), ch, font=font, anchor='mm', fill=(255, 255, 255, 255), stroke_width=4, stroke_fill=(255, 255, 255, 255))
        base = over(base, t2)
    return base


def main():
    base = layer()
    base = banner(base)
    icons = {'skill1': icon_skill1, 'skill2': icon_skill2, 'skill3': icon_skill3, 'skill4': icon_skill4,
             'ultimate': icon_ultimate, 'passive': icon_passive}
    for name in ['skill1', 'skill2', 'skill3', 'skill4', 'passive']:
        box = SLOTS[name]
        base = plate(base, box, 22 if name != 'passive' else 18)
        base, m = slot_bg(base, box)
        base = icons[name](base, box, m)
        base = slot_vignette(base, box, m)
    ub = SLOTS['ultimate']
    base = ult_ornaments(base, ub)
    base = plate(base, ub, 30)
    base, m = slot_bg(base, ub, hot=(150, 44, 14))
    base = icon_ultimate(base, ub, m)
    base = slot_vignette(base, ub, m)
    # 궁극기 둘레 불티
    em = layer()
    embers(em, (ub[0] - 60, ub[1] - 40, ub[2] + 50, ub[3] + 40), 22, [(255, 230, 160, 255), (255, 140, 50, 255)], 5, 18, seed=21)
    base = add(base, glow(em, 4), 0.9)
    base = over(base, em)
    base = labels(base)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    base.save(OUT)
    print('wrote', OUT)
    # 능력 선택 카드 (256x160): 궁극기 그림 부분
    x0, y0, x1, y1, _ = ub
    cw = (x1 - 50) - (x0 + 4)
    ch = cw / 1.6
    cy = (y0 + y1) / 2 - 26
    card = base.crop((int(x0 + 4), int(cy - ch / 2), int(x1 - 50), int(cy + ch / 2))).resize((256, 160), Image.LANCZOS)
    a = arr(card)
    yy, xx = np.mgrid[0:160, 0:256].astype(np.float32)
    v = np.clip(np.maximum(np.abs(xx - 128) / 128, np.abs(yy - 80) / 80) - 0.7, 0, 1) / 0.3
    a[..., :3] *= (1 - 0.6 * v[..., None] ** 1.5)
    a[..., 3] = 1
    img(a).save(os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures', 'gui', 'card_dragon.png'))
    # 대시 배지: 공통 보라 아이콘의 색상만 불꽃색으로 돌림
    gui = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures', 'gui')
    dash = Image.open(os.path.join(gui, 'dash.png')).convert('RGBA')
    alpha = dash.getchannel('A')
    hsv = np.asarray(dash.convert('RGB').convert('HSV')).astype(np.int32)
    hsv[..., 0] = (hsv[..., 0] + 82) % 256  # 보라(약 270도) -> 주황(약 25도)
    out = Image.fromarray(hsv.astype(np.uint8), 'HSV').convert('RGB')
    out.putalpha(alpha)
    out.save(os.path.join(gui, 'dash_dragon.png'))


if __name__ == '__main__':
    main()
