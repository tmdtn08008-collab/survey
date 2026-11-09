#!/usr/bin/env python3
"""
마검사 스킬바 원본 그림 (tools/art/blade_hud_source.png) 생성 (보라 테마, 검 아이콘).
공허 스킬바와 같은 배치(1907x825)라서 gen_hud.py 의 슬롯 좌표를 그대로 쓴다.
그 다음 `python3 tools/gen_hud.py` 로 HUD 텍스처/마스크/아이콘을 만든다.
"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), '..')
OUT = os.path.join(ROOT, 'tools', 'art', 'blade_hud_source.png')
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
GOLD_HI = (236, 214, 255)
GOLD = (176, 120, 240)
BRONZE = (62, 26, 112)
DARK = (12, 4, 20)
EMBER = (176, 70, 255)
WHITE_HOT = (250, 240, 255)


# ------------------------------------------------------------------ 판 (프레임)
def plate(base, box, rim_w=24, horns=False):
    """금속 테두리 + 안쪽 배경."""
    sh = layer()
    ImageDraw.Draw(sh).polygon(octagon(*grow(box, rim_w + 10)), fill=(0, 0, 0, 200))
    base = over(base, glow(sh, 6))
    outer = poly_mask(octagon(*grow(box, rim_w + 4)))
    base = over(base, vgrad(outer, (34, 12, 56), (10, 3, 18), box[1] - rim_w, box[3] + rim_w))
    rim = poly_mask(octagon(*grow(box, rim_w)))
    base = over(base, vgrad(rim, GOLD_HI, BRONZE, box[1] - rim_w, box[3] + rim_w))
    inner = poly_mask(octagon(*grow(box, rim_w * 0.55)))
    base = over(base, vgrad(inner, (46, 16, 72), (14, 4, 24), box[1], box[3]))
    line = poly_mask(octagon(*grow(box, rim_w * 0.28)))
    base = over(base, vgrad(line, (220, 180, 255), (96, 44, 170), box[1], box[3]))
    # 비늘 무늬 테두리 (모서리 장식)
    d = layer()
    dd = ImageDraw.Draw(d)
    x0, y0, x1, y1, c = grow(box, rim_w * 0.78)
    for (px, py) in [(x0 + c * 0.5, y0 + c * 0.5), (x1 - c * 0.5, y0 + c * 0.5), (x0 + c * 0.5, y1 - c * 0.5), (x1 - c * 0.5, y1 - c * 0.5)]:
        s = rim_w * 0.55
        dd.polygon([(px, py - s), (px + s * 0.7, py), (px, py + s), (px - s * 0.7, py)], fill=(36, 10, 60, 255))
        s2 = s * 0.6
        dd.polygon([(px, py - s2), (px + s2 * 0.7, py), (px, py + s2), (px - s2 * 0.7, py)], fill=(200, 130, 255, 255))
    base = over(base, d)
    base = add(base, glow(d, 6), 0.5)
    return base


def slot_bg(base, box, hot=(78, 26, 132)):
    m = poly_mask(octagon(*box))
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    rad = max(box[2] - box[0], box[3] - box[1]) * 0.72
    base = over(base, radial(m, cx, cy - (box[3] - box[1]) * 0.08, rad, hot, (10, 3, 18)))
    # 은은한 비늘 무늬
    sc = layer()
    d = ImageDraw.Draw(sc)
    step = 34
    for j, y in enumerate(range(int(box[1]) - step, int(box[3]) + step, step // 2)):
        off = (step // 2) if j % 2 else 0
        for x in range(int(box[0]) - step + off, int(box[2]) + step, step):
            d.arc([x - step / 2, y - step / 2, x + step / 2, y + step / 2], 20, 160, fill=(200, 150, 255, 22), width=3)
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


def sword_poly(cx, cy, ang, length, width):
    """칼끝이 ang 방향인 검 (날 + 코등이 + 손잡이) 다각형들."""
    ca, sa = math.cos(ang), math.sin(ang)
    px, py = -sa, ca
    def P(a, b):
        return (cx + ca * a + px * b, cy + sa * a + py * b)
    blade = [P(0, -width / 2), P(length * 0.85, -width * 0.42), P(length, 0), P(length * 0.85, width * 0.42), P(0, width / 2)]
    guard = [P(-width * 0.3, -width * 1.6), P(width * 0.3, -width * 1.6), P(width * 0.3, width * 1.6), P(-width * 0.3, width * 1.6)]
    grip = [P(-length * 0.28, -width * 0.28), P(-width * 0.3, -width * 0.28), P(-width * 0.3, width * 0.28), P(-length * 0.28, width * 0.28)]
    return blade, guard, grip


def draw_sword(base, m, cx, cy, ang, length, width, k=1.0):
    blade, guard, grip = sword_poly(cx, cy, ang, length, width)
    dk = layer()
    d = ImageDraw.Draw(dk)
    d.polygon(blade, fill=(40, 30, 60, 255))
    d.polygon(guard, fill=(70, 50, 110, 255))
    d.polygon(grip, fill=(30, 14, 40, 255))
    base = over(base, clip_to(dk, m))
    rim = layer()
    dd = ImageDraw.Draw(rim)
    dd.line(blade + [blade[0]], fill=(255, 255, 255, 255), width=4)
    dd.line(guard + [guard[0]], fill=(255, 255, 255, 255), width=3)
    return lit(base, rim, m, core=(240, 220, 255), col=(170, 70, 255), g1=6, g2=18, k=k)


def slash_line(d, p0, p1, w, bend=0.0):
    mid = ((p0[0] + p1[0]) / 2 - (p1[1] - p0[1]) * bend, (p0[1] + p1[1]) / 2 + (p1[0] - p0[0]) * bend)
    d.polygon(taper(bez(p0, mid, p1), w, head=0.55), fill=(255, 255, 255, 255))


def figure(base, m, cx, cy, s, col=(16, 6, 24, 255), alpha=1.0):
    f = layer()
    d = ImageDraw.Draw(f)
    c = tuple(list(col[:3]) + [int(255 * alpha)])
    d.rectangle([cx - 14 * s, cy - 62 * s, cx + 14 * s, cy - 34 * s], fill=c)
    d.rectangle([cx - 16 * s, cy - 32 * s, cx + 16 * s, cy + 14 * s], fill=c)
    d.rectangle([cx - 29 * s, cy - 32 * s, cx - 18 * s, cy + 8 * s], fill=c)
    d.rectangle([cx + 18 * s, cy - 32 * s, cx + 29 * s, cy + 8 * s], fill=c)
    d.rectangle([cx - 15 * s, cy + 16 * s, cx - 2 * s, cy + 56 * s], fill=c)
    d.rectangle([cx + 2 * s, cy + 16 * s, cx + 15 * s, cy + 56 * s], fill=c)
    return over(base, clip_to(f, m))


def icon_skill1(base, box, m):
    """마검 발도: 칼집에서 뽑아 가로로 긋는 일섬."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    sp = layer()
    d = ImageDraw.Draw(sp)
    for i in range(6):
        y = y0 + 70 + i * 32
        L = rnd.uniform(60, 120)
        d.polygon(taper([(x1 - 20 - L, y), (x1 - 20, y)], 4, head=0.2), fill=(255, 255, 255, 150))
    base = lit(base, sp, m, core=(220, 190, 255), col=(150, 70, 255), g1=5, g2=12, k=0.5)
    base = draw_sword(base, m, x0 + 60, cy + 70, -0.12, 170, 20, 0.8)
    sl = layer()
    slash_line(ImageDraw.Draw(sl), (x0 + 20, cy + 10), (x1 - 15, cy - 50), 18, 0.08)
    base = lit(base, sl, m, g1=10, g2=30, k=1.2)
    st = layer()
    star4(ImageDraw.Draw(st), x1 - 60, cy - 42, 30, (255, 255, 255, 255), thin=0.16)
    return lit(base, st, m, g1=6, g2=16)


def icon_skill2(base, box, m):
    """파공참: 올려베어 날아가는 초승달 참격 + 공중 균열."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    cr = layer()
    d = ImageDraw.Draw(cr)
    pts = []
    for i in range(60):
        t = i / 59
        a = -1.25 + 2.5 * t
        pts.append((cx + 20 + math.cos(a) * 70 - 70, cy - 10 + math.sin(a) * 120))
    d.polygon(taper(pts, 26, head=0.5, power=0.7), fill=(255, 255, 255, 255))
    base = lit(base, cr, m, g1=10, g2=28)
    base = draw_sword(base, m, x0 + 60, y1 - 60, -1.05, 150, 18, 0.8)
    rf_ = layer()
    d = ImageDraw.Draw(rf_)
    pts = [(cx + 70, y0 + 30)]
    r = random.Random(4)
    for i in range(6):
        pts.append((pts[-1][0] + r.uniform(-18, 18), pts[-1][1] + 22))
    d.line(pts, fill=(255, 255, 255, 255), width=6)
    return lit(base, rf_, m, core=(250, 230, 255), col=(200, 90, 255), g1=6, g2=18)


def icon_skill3(base, box, m):
    """무형검무: 사방에서 베는 참격 + 잔상."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 + 10
    for i, (ox, a) in enumerate(((-70, 0.25), (70, 0.25))):
        base = figure(base, m, cx + ox, cy, 0.8, col=(140, 70, 220, 255), alpha=a)
    base = figure(base, m, cx, cy, 1.0)
    sl = layer()
    d = ImageDraw.Draw(sl)
    slash_line(d, (x0 + 20, cy - 70), (x1 - 20, cy + 30), 12, 0.05)
    slash_line(d, (x1 - 20, cy - 80), (x0 + 30, cy + 40), 12, -0.05)
    slash_line(d, (x0 + 25, cy - 20), (x1 - 25, cy - 30), 9, 0.02)
    slash_line(d, (cx + 10, y0 + 25), (cx - 10, y1 - 25), 9, 0.03)
    return lit(base, sl, m, g1=8, g2=24)


def icon_skill4(base, box, m):
    """마검 반격: 세운 검과 마력 방어진."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    ring = layer()
    d = ImageDraw.Draw(ring)
    for r_ in (100, 78):
        pts = [(cx + math.cos(a / 6 * math.tau + 0.5) * r_, cy + math.sin(a / 6 * math.tau + 0.5) * r_) for a in range(7)]
        d.line(pts, fill=(255, 255, 255, 255), width=5)
    for a in range(12):
        ang = a / 12 * math.tau
        d.line([(cx + math.cos(ang) * 82, cy + math.sin(ang) * 82), (cx + math.cos(ang) * 96, cy + math.sin(ang) * 96)], fill=(255, 255, 255, 200), width=3)
    base = lit(base, ring, m, core=(220, 190, 255), col=(150, 60, 255), g1=8, g2=22, k=0.8)
    base = draw_sword(base, m, cx, cy + 105, -math.pi / 2, 210, 24)
    sp = layer()
    star4(ImageDraw.Draw(sp), cx + 4, cy - 20, 52, (255, 255, 255, 255), thin=0.14)
    return lit(base, sp, m, g1=8, g2=24)


def icon_ultimate(base, box, m):
    """천지개벽: 하늘에서 내려꽂히는 거대한 마검 + 갈라지는 땅."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    # 하늘의 균열
    rf_ = layer()
    d = ImageDraw.Draw(rf_)
    r = random.Random(8)
    for k in range(4):
        x = x0 + 50 + k * 100 + r.uniform(-20, 20)
        pts = [(x, y0 + 10)]
        for i in range(5):
            pts.append((pts[-1][0] + r.uniform(-16, 16), pts[-1][1] + 20))
        d.line(pts, fill=(255, 255, 255, 255), width=4)
    base = lit(base, rf_, m, core=(240, 220, 255), col=(160, 70, 255), g1=5, g2=14, k=0.7)
    # 땅의 폭발
    ex = layer()
    d = ImageDraw.Draw(ex)
    d.ellipse([cx - 150, y1 - 70, cx + 150, y1 + 40], fill=(255, 255, 255, 255))
    base = lit(base, ex, m, core=(255, 230, 255), col=(190, 80, 255), g1=20, g2=50)
    cr = layer()
    d = ImageDraw.Draw(cr)
    for k in range(-3, 4):
        d.line([(cx, y1 - 30), (cx + k * 60, y1 + 10)], fill=(255, 255, 255, 255), width=5)
    base = lit(base, cr, m, g1=6, g2=16)
    # 거대한 검 (칼끝 아래)
    base = draw_sword(base, m, cx, y0 + 20, math.pi / 2, 260, 64, 1.2)
    return base


def icon_passive(base, box, m):
    """잔영보: 잔상을 남기며 사라지는 검사."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2 + 20, (y0 + y1) / 2 + 10
    for i, a in enumerate((0.18, 0.35)):
        base = figure(base, m, cx - 70 + i * 32, cy, 0.7, col=(170, 90, 255, 255), alpha=a)
    base = figure(base, m, cx, cy, 0.7)
    sp = layer()
    d = ImageDraw.Draw(sp)
    for i in range(5):
        y = cy - 40 + i * 18
        d.polygon(taper([(x0 + 10, y), (cx - 30, y)], 3, head=0.2), fill=(255, 255, 255, 170))
    base = lit(base, sp, m, core=(230, 210, 255), col=(160, 70, 255), g1=4, g2=10, k=0.7)
    return draw_sword(base, m, x0 + 40, cy + 40, -1.2, 70, 12, 0.7)


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
    base = over(base, radial(im, 400, 193, 1300, (52, 16, 90), (10, 3, 18), 1.2))
    # 비늘 무늬 + 불꽃 줄기
    sc = layer()
    d = ImageDraw.Draw(sc)
    step = 30
    for j, y in enumerate(range(100, 290, 15)):
        off = step // 2 if j % 2 else 0
        for x in range(40 + off, 1880, step):
            d.arc([x - step / 2, y - step / 2, x + step / 2, y + step / 2], 20, 160, fill=(200, 150, 255, 20), width=2)
    base = over(base, clip_to(sc, im))
    fl = layer()
    d = ImageDraw.Draw(fl)
    for i in range(9):
        y = 150 + i * 13
        xs = 420 + i * 70
        p = bez((xs, y + 40), (xs + 300, y - 50 + (i % 3) * 30), (xs + 720, y + 10), 30)
        d.polygon(taper(p, 5 + (i % 3) * 2, head=0.4), fill=(255, 255, 255, 120))
    base = add(base, clip_to(glow(img(arr(fl) * np.array([0.7, 0.35, 1, 1], np.float32)), 6), im), 0.7)
    em = layer()
    embers(em, (300, 125, 1680, 262), 26, [(240, 220, 255, 255), (190, 110, 255, 255)], 4, 13, seed=11)
    base = add(base, glow(em, 3), 0.9)
    base = over(base, clip_to(em, im))
    # 왼쪽 장식: 용의 눈 보석
    gem = layer()
    d = ImageDraw.Draw(gem)
    d.polygon([(90, 150), (120, 193), (90, 236), (60, 193)], fill=(36, 8, 60, 255), outline=(220, 180, 255, 255), width=4)
    d.polygon([(90, 166), (98, 193), (90, 220), (82, 193)], fill=(240, 200, 255, 255))
    base = over(base, gem)
    g2 = layer()
    ImageDraw.Draw(g2).polygon([(90, 166), (98, 193), (90, 220), (82, 193)], fill=(170, 80, 255, 255))
    base = add(base, glow(g2, 12), 1.0)
    # 이름
    font = ImageFont.truetype(FONT, 96)
    tl = layer()
    d = ImageDraw.Draw(tl)
    d.text((150, 193), '마검사', font=font, anchor='lm', fill=(255, 255, 255, 255))
    base = add(base, glow(img(arr(tl) * np.array([0.7, 0.35, 1, 1], np.float32)), 10), 1.0)
    d2 = layer()
    ImageDraw.Draw(d2).text((150, 193), '마검사', font=font, anchor='lm', fill=(20, 6, 34, 255), stroke_width=10, stroke_fill=(20, 6, 34, 255))
    ImageDraw.Draw(d2).text((150, 193), '마검사', font=font, anchor='lm', fill=(250, 240, 255, 255), stroke_width=3, stroke_fill=(250, 240, 255, 255))
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
    col = a * np.array([0.65, 0.3, 1, 1], np.float32)
    base = add(base, glow(img(col), 10), 1.0)
    base = add(base, img(a * np.array([0.85, 0.7, 1, 0.85], np.float32)), 0.8)
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
    base = over(base, vgrad(hm, (240, 232, 255), (90, 70, 130), y0 - 130, y0 + 20))
    ol = layer()
    for side in (-1, 1):
        bx = x0 + c * 0.6 if side < 0 else x1 - c * 0.6
        by = y0 + c * 0.2
        p = bez((bx, by), (bx + side * 70, by - 40), (bx + side * 40, by - 120), 30)
        ImageDraw.Draw(ol).line(taper(p, 22, head=0.04, power=0.45) + [p[0]], fill=(24, 10, 40, 255), width=4)
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
        ImageDraw.Draw(tl).text((cx, cy), ch, font=font, anchor='mm', fill=(180, 90, 255, 255))
        base = add(base, glow(tl, 8), 0.7)
        t2 = layer()
        ImageDraw.Draw(t2).text((cx, cy), ch, font=font, anchor='mm', fill=(14, 4, 24, 255), stroke_width=11, stroke_fill=(14, 4, 24, 255))
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
    base, m = slot_bg(base, ub, hot=(100, 36, 170))
    base = icon_ultimate(base, ub, m)
    base = slot_vignette(base, ub, m)
    # 궁극기 둘레 불티
    em = layer()
    embers(em, (ub[0] - 60, ub[1] - 40, ub[2] + 50, ub[3] + 40), 22, [(245, 230, 255, 255), (190, 110, 255, 255)], 5, 18, seed=21)
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
    img(a).save(os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures', 'gui', 'card_blade.png'))



if __name__ == '__main__':
    main()
