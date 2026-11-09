#!/usr/bin/env python3
"""
성가사(버퍼) 스킬바 원본 그림 (tools/art/cantor_hud_source.png) 생성 (금빛 + 남색 테마). 사용자 그림이 오면 교체.
공허 스킬바와 같은 배치(1907x825)라서 gen_hud.py 의 슬롯 좌표를 그대로 쓴다.
그 다음 `python3 tools/gen_hud.py` 로 HUD 텍스처/마스크/아이콘을 만든다.
"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), '..')
OUT = os.path.join(ROOT, 'tools', 'art', 'cantor_hud_source.png')
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
GOLD_HI = (255, 244, 214)
GOLD = (230, 180, 90)
BRONZE = (110, 70, 20)
DARK = (14, 14, 30)
EMBER = (255, 200, 90)
WHITE_HOT = (255, 250, 235)


# ------------------------------------------------------------------ 판 (프레임)
def plate(base, box, rim_w=24, horns=False):
    """금속 테두리 + 안쪽 배경."""
    sh = layer()
    ImageDraw.Draw(sh).polygon(octagon(*grow(box, rim_w + 10)), fill=(0, 0, 0, 200))
    base = over(base, glow(sh, 6))
    outer = poly_mask(octagon(*grow(box, rim_w + 4)))
    base = over(base, vgrad(outer, (60, 44, 14), (10, 12, 28), box[1] - rim_w, box[3] + rim_w))
    rim = poly_mask(octagon(*grow(box, rim_w)))
    base = over(base, vgrad(rim, GOLD_HI, BRONZE, box[1] - rim_w, box[3] + rim_w))
    inner = poly_mask(octagon(*grow(box, rim_w * 0.55)))
    base = over(base, vgrad(inner, (30, 34, 70), (10, 12, 30), box[1], box[3]))
    line = poly_mask(octagon(*grow(box, rim_w * 0.28)))
    base = over(base, vgrad(line, (255, 236, 180), (170, 120, 40), box[1], box[3]))
    # 비늘 무늬 테두리 (모서리 장식)
    d = layer()
    dd = ImageDraw.Draw(d)
    x0, y0, x1, y1, c = grow(box, rim_w * 0.78)
    for (px, py) in [(x0 + c * 0.5, y0 + c * 0.5), (x1 - c * 0.5, y0 + c * 0.5), (x0 + c * 0.5, y1 - c * 0.5), (x1 - c * 0.5, y1 - c * 0.5)]:
        s = rim_w * 0.55
        dd.polygon([(px, py - s), (px + s * 0.7, py), (px, py + s), (px - s * 0.7, py)], fill=(30, 30, 70, 255))
        s2 = s * 0.6
        dd.polygon([(px, py - s2), (px + s2 * 0.7, py), (px, py + s2), (px - s2 * 0.7, py)], fill=(150, 200, 255, 255))
    base = over(base, d)
    base = add(base, glow(d, 6), 0.5)
    return base


def slot_bg(base, box, hot=(60, 70, 140)):
    m = poly_mask(octagon(*box))
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    rad = max(box[2] - box[0], box[3] - box[1]) * 0.72
    base = over(base, radial(m, cx, cy - (box[3] - box[1]) * 0.08, rad, hot, (10, 12, 28)))
    # 은은한 비늘 무늬
    sc = layer()
    d = ImageDraw.Draw(sc)
    step = 34
    for j, y in enumerate(range(int(box[1]) - step, int(box[3]) + step, step // 2)):
        off = (step // 2) if j % 2 else 0
        for x in range(int(box[0]) - step + off, int(box[2]) + step, step):
            d.arc([x - step / 2, y - step / 2, x + step / 2, y + step / 2], 20, 160, fill=(200, 210, 255, 22), width=3)
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


GLOW = (255, 200, 90)
CORE = (255, 250, 230)
VALOR_C, AEGIS_C, HASTE_C = (255, 150, 60), (120, 200, 255), (130, 255, 140)


def taper_line(d, pts, w, head=0.5, fill=(255, 255, 255, 255), power=0.8):
    d.polygon(taper(pts, w, head=head, power=power), fill=fill)


def note_shape(d, x, y, s, fill=(255, 255, 255, 255)):
    """♪ 모양 (머리 중심 x,y, 크기 s)."""
    d.ellipse([x - s, y - s * 0.7, x + s, y + s * 0.7], fill=fill)
    d.rectangle([x + s * 0.6, y - s * 3.2, x + s, y], fill=fill)
    d.polygon([(x + s, y - s * 3.2), (x + s * 2.3, y - s * 2.2), (x + s * 2.1, y - s * 1.5), (x + s, y - s * 2.3)], fill=fill)


def wing_poly(cx, cy, s, side):
    """펼친 깃털 날개 (side = 1 오른쪽, -1 왼쪽)."""
    pts = [(cx, cy)]
    for i in range(7):
        t = i / 6
        ang = math.radians(-70 + 95 * t)
        L = s * (1.0 - 0.45 * t)
        pts.append((cx + side * math.cos(ang) * L, cy + math.sin(ang) * L))
        ang2 = math.radians(-70 + 95 * (t + 0.08))
        pts.append((cx + side * math.cos(ang2) * L * 0.78, cy + math.sin(ang2) * L * 0.78))
    pts.append((cx + side * s * 0.15, cy + s * 0.35))
    return pts


def icon_skill1(base, box, m):
    """용맹의 찬가: 지휘봉 + 퍼지는 소리의 파동 + 음표."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    wv = layer()
    d = ImageDraw.Draw(wv)
    for k, rr in enumerate((70, 115, 160)):
        d.arc([x0 + 40 - rr, y1 - 40 - rr, x0 + 40 + rr, y1 - 40 + rr], -85, 5, fill=(255, 255, 255, 255), width=12 - k * 3)
    base = lit(base, wv, m, core=(255, 236, 200), col=VALOR_C, g1=8, g2=22)
    bt = layer()
    d = ImageDraw.Draw(bt)
    taper_line(d, [(x0 + 30, y1 - 30), (x1 - 80, y0 + 80)], 9, head=0.15, power=0.4)
    star4(d, x1 - 80, y0 + 80, 30, (255, 255, 255, 255), thin=0.18)
    base = lit(base, bt, m, core=CORE, col=GLOW, g1=6, g2=16)
    nt = layer()
    d = ImageDraw.Draw(nt)
    note_shape(d, x1 - 55, y1 - 80, 15)
    note_shape(d, cx - 20, y0 + 80, 11)
    return lit(base, nt, m, core=CORE, col=VALOR_C, g1=5, g2=12, k=0.8)


def icon_skill2(base, box, m):
    """수호의 성역: 빛의 돔 + 무늬 + 가운데 빛기둥."""
    x0, y0, x1, y1, _ = box
    cx = (x0 + x1) / 2
    gy = y1 - 55
    R = 115
    dm = layer()
    d = ImageDraw.Draw(dm)
    d.pieslice([cx - R, gy - R, cx + R, gy + R], 180, 360, fill=(120, 180, 255, 80))
    base = over(base, clip_to(dm, m))
    rim = layer()
    d = ImageDraw.Draw(rim)
    d.arc([cx - R, gy - R, cx + R, gy + R], 180, 360, fill=(255, 255, 255, 255), width=8)
    for k in range(1, 4):
        rr = R * k / 4
        d.arc([cx - R, gy - rr, cx + R, gy + rr], 180, 360, fill=(255, 255, 255, 150), width=3)
    d.ellipse([cx - R - 10, gy - 14, cx + R + 10, gy + 14], outline=(255, 255, 255, 255), width=6)
    base = lit(base, rim, m, core=(230, 245, 255), col=AEGIS_C, g1=8, g2=22)
    bm = layer()
    taper_line(ImageDraw.Draw(bm), [(cx, y0 + 20), (cx, gy)], 34, head=0.5, fill=(255, 255, 255, 220))
    return lit(base, bm, m, core=CORE, col=GLOW, g1=8, g2=20, k=0.8)


def icon_skill3(base, box, m):
    """천상의 종: 빛나는 거대한 종 + 종소리 고리."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 + 10
    bell = layer()
    d = ImageDraw.Draw(bell)
    prof = [(0, -110), (28, -108), (46, -96), (56, -70), (60, -30), (68, 10), (86, 44), (104, 60), (0, 60)]
    pts = [(cx + x, cy + y) for x, y in prof] + [(cx - x, cy + y) for x, y in reversed(prof)]
    d.polygon(pts, fill=(255, 255, 255, 255))
    base = lit(base, bell, m, core=(255, 226, 140), col=GLOW, g1=8, g2=26)
    dt = layer()
    d = ImageDraw.Draw(dt)
    d.line([(cx - 80, cy + 40), (cx + 80, cy + 40)], fill=(150, 100, 20, 255), width=5)
    d.line([(cx - 52, cy - 50), (cx + 52, cy - 50)], fill=(150, 100, 20, 255), width=4)
    d.ellipse([cx - 14, cy + 52, cx + 14, cy + 80], fill=(120, 80, 20, 255))
    base = over(base, clip_to(dt, m))
    rg = layer()
    d = ImageDraw.Draw(rg)
    for sgn in (-1, 1):
        for k, rr in enumerate((130, 160)):
            a0, a1 = (-40, 40) if sgn > 0 else (140, 220)
            d.arc([cx - rr, cy - rr + 20, cx + rr, cy + rr + 20], a0, a1, fill=(255, 255, 255, 255), width=7 - k * 2)
    return lit(base, rg, m, core=(230, 255, 230), col=HASTE_C, g1=6, g2=16, k=0.8)


def icon_skill4(base, box, m):
    """응답송: 펼친 성가집 위로 내리꽂히는 심판의 빛."""
    x0, y0, x1, y1, _ = box
    cx = (x0 + x1) / 2
    by = y1 - 80
    bm = layer()
    taper_line(ImageDraw.Draw(bm), [(cx, y0 + 10), (cx, by)], 48, head=0.6, fill=(255, 255, 255, 230))
    base = lit(base, bm, m, core=CORE, col=GLOW, g1=10, g2=30)
    bk = layer()
    d = ImageDraw.Draw(bk)
    d.polygon([(cx, by + 6), (cx - 110, by - 14), (cx - 112, by + 40), (cx, by + 56)], fill=(255, 248, 225, 255))
    d.polygon([(cx, by + 6), (cx + 110, by - 14), (cx + 112, by + 40), (cx, by + 56)], fill=(255, 248, 225, 255))
    for k in range(4):
        d.line([(cx - 96, by - 2 + k * 10), (cx - 14, by + 14 + k * 10)], fill=(150, 110, 50, 255), width=2)
        d.line([(cx + 14, by + 14 + k * 10), (cx + 96, by - 2 + k * 10)], fill=(150, 110, 50, 255), width=2)
    d.line([(cx, by + 6), (cx, by + 56)], fill=(120, 80, 30, 255), width=4)
    base = over(base, clip_to(bk, m))
    rg = layer()
    ImageDraw.Draw(rg).ellipse([cx - 120, by - 30, cx + 120, by + 70], outline=(255, 255, 255, 255), width=5)
    return lit(base, rg, m, core=CORE, col=GLOW, g1=6, g2=16, k=0.7)


def icon_ultimate(base, box, m):
    """GLORIA: 거대한 광륜 + 두 쌍의 빛의 날개 + 쏟아지는 빛살."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 - 10
    rays = layer()
    d = ImageDraw.Draw(rays)
    for i in range(16):
        a = i / 16 * math.tau
        d.polygon([(cx, cy), (cx + math.cos(a - 0.06) * 260, cy + math.sin(a - 0.06) * 260), (cx + math.cos(a + 0.06) * 260, cy + math.sin(a + 0.06) * 260)], fill=(255, 255, 255, 90))
    base = lit(base, rays, m, core=(255, 220, 150), col=GLOW, g1=10, g2=30, k=0.3)
    wg = layer()
    d = ImageDraw.Draw(wg)
    for side in (-1, 1):
        d.polygon(wing_poly(cx + side * 16, cy + 10, 170, side), fill=(255, 255, 255, 255))
        d.polygon(wing_poly(cx + side * 12, cy + 50, 110, side), fill=(255, 255, 255, 230))
    base = lit(base, wg, m, core=(255, 252, 240), col=(255, 170, 60), g1=5, g2=12, k=0.6)
    ol = layer()
    d = ImageDraw.Draw(ol)
    for side in (-1, 1):
        d.line(wing_poly(cx + side * 16, cy + 10, 170, side) + [wing_poly(cx + side * 16, cy + 10, 170, side)[0]], fill=(150, 100, 30, 255), width=3)
    base = over(base, clip_to(ol, m))
    hl = layer()
    ImageDraw.Draw(hl).ellipse([cx - 70, cy - 120, cx + 70, cy - 70], outline=(255, 255, 255, 255), width=10)
    base = lit(base, hl, m, core=(255, 240, 180), col=GLOW, g1=6, g2=20, k=1.2)
    fg = layer()
    d = ImageDraw.Draw(fg)
    d.ellipse([cx - 16, cy - 56, cx + 16, cy - 24], fill=(60, 44, 20, 255))
    d.polygon([(cx - 22, cy - 20), (cx + 22, cy - 20), (cx + 34, cy + 90), (cx - 34, cy + 90)], fill=(60, 44, 20, 255))
    return over(base, clip_to(fg, m))


def icon_passive(base, box, m):
    """선율 전환: 세 가지 색 음표가 원을 그리며 돎."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    ar = layer()
    d = ImageDraw.Draw(ar)
    d.arc([cx - 70, cy - 70, cx + 70, cy + 70], 20, 330, fill=(255, 255, 255, 200), width=6)
    d.polygon([(cx + 66, cy - 30), (cx + 86, cy - 6), (cx + 52, cy - 4)], fill=(255, 255, 255, 220))
    base = lit(base, ar, m, core=CORE, col=GLOW, g1=4, g2=12, k=0.7)
    for k, col in enumerate((VALOR_C, AEGIS_C, HASTE_C)):
        a = -math.pi / 2 + k * math.tau / 3
        nt = layer()
        note_shape(ImageDraw.Draw(nt), cx + math.cos(a) * 48 - 8, cy + math.sin(a) * 48 + 14, 12)
        base = lit(base, nt, m, core=CORE, col=col, g1=4, g2=12)
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
    base = over(base, radial(im, 400, 193, 1300, (50, 56, 110), (10, 12, 28), 1.2))
    # 비늘 무늬 + 불꽃 줄기
    sc = layer()
    d = ImageDraw.Draw(sc)
    step = 30
    for j, y in enumerate(range(100, 290, 15)):
        off = step // 2 if j % 2 else 0
        for x in range(40 + off, 1880, step):
            d.arc([x - step / 2, y - step / 2, x + step / 2, y + step / 2], 20, 160, fill=(200, 210, 255, 20), width=2)
    base = over(base, clip_to(sc, im))
    fl = layer()
    d = ImageDraw.Draw(fl)
    for i in range(9):
        y = 150 + i * 13
        xs = 420 + i * 70
        p = bez((xs, y + 40), (xs + 300, y - 50 + (i % 3) * 30), (xs + 720, y + 10), 30)
        d.polygon(taper(p, 5 + (i % 3) * 2, head=0.4), fill=(255, 255, 255, 120))
    base = add(base, clip_to(glow(img(arr(fl) * np.array([1, 0.85, 0.45, 1], np.float32)), 6), im), 0.7)
    em = layer()
    embers(em, (300, 125, 1680, 262), 26, [(255, 248, 220, 255), (255, 200, 90, 255)], 4, 13, seed=11)
    base = add(base, glow(em, 3), 0.9)
    base = over(base, clip_to(em, im))
    # 왼쪽 장식: 용의 눈 보석
    gem = layer()
    d = ImageDraw.Draw(gem)
    d.polygon([(90, 150), (120, 193), (90, 236), (60, 193)], fill=(20, 24, 60, 255), outline=(255, 236, 180, 255), width=4)
    d.polygon([(90, 166), (98, 193), (90, 220), (82, 193)], fill=(255, 246, 220, 255))
    base = over(base, gem)
    g2 = layer()
    ImageDraw.Draw(g2).polygon([(90, 166), (98, 193), (90, 220), (82, 193)], fill=(255, 200, 90, 255))
    base = add(base, glow(g2, 12), 1.0)
    # 이름
    font = ImageFont.truetype(FONT, 96)
    tl = layer()
    d = ImageDraw.Draw(tl)
    d.text((150, 193), '성가사', font=font, anchor='lm', fill=(255, 255, 255, 255))
    base = add(base, glow(img(arr(tl) * np.array([1, 0.85, 0.45, 1], np.float32)), 10), 1.0)
    d2 = layer()
    ImageDraw.Draw(d2).text((150, 193), '성가사', font=font, anchor='lm', fill=(30, 20, 4, 255), stroke_width=10, stroke_fill=(30, 20, 4, 255))
    ImageDraw.Draw(d2).text((150, 193), '성가사', font=font, anchor='lm', fill=(255, 250, 235, 255), stroke_width=3, stroke_fill=(255, 250, 235, 255))
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
    col = a * np.array([1, 0.82, 0.4, 1], np.float32)
    base = add(base, glow(img(col), 10), 1.0)
    base = add(base, img(a * np.array([1, 0.95, 0.8, 0.85], np.float32)), 0.8)
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
    base = over(base, vgrad(hm, (255, 248, 225), (150, 110, 40), y0 - 130, y0 + 20))
    ol = layer()
    for side in (-1, 1):
        bx = x0 + c * 0.6 if side < 0 else x1 - c * 0.6
        by = y0 + c * 0.2
        p = bez((bx, by), (bx + side * 70, by - 40), (bx + side * 40, by - 120), 30)
        ImageDraw.Draw(ol).line(taper(p, 22, head=0.04, power=0.45) + [p[0]], fill=(40, 28, 6, 255), width=4)
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
        ImageDraw.Draw(tl).text((cx, cy), ch, font=font, anchor='mm', fill=(255, 200, 90, 255))
        base = add(base, glow(tl, 8), 0.7)
        t2 = layer()
        ImageDraw.Draw(t2).text((cx, cy), ch, font=font, anchor='mm', fill=(30, 20, 4, 255), stroke_width=11, stroke_fill=(30, 20, 4, 255))
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
    base, m = slot_bg(base, ub, hot=(80, 90, 170))
    base = icon_ultimate(base, ub, m)
    base = slot_vignette(base, ub, m)
    # 궁극기 둘레 불티
    em = layer()
    embers(em, (ub[0] - 60, ub[1] - 40, ub[2] + 50, ub[3] + 40), 22, [(255, 248, 225, 255), (255, 200, 90, 255)], 5, 18, seed=21)
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
    img(a).save(os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures', 'gui', 'card_cantor.png'))



if __name__ == '__main__':
    main()
