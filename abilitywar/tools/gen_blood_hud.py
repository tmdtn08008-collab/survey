#!/usr/bin/env python3
"""
혈귀 스킬바 원본 그림 (tools/art/blood_hud_source.png) 생성 (핏빛 테마). 사용자 그림이 오면 교체.
공허 스킬바와 같은 배치(1907x825)라서 gen_hud.py 의 슬롯 좌표를 그대로 쓴다.
그 다음 `python3 tools/gen_hud.py` 로 HUD 텍스처/마스크/아이콘을 만든다.
"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), '..')
OUT = os.path.join(ROOT, 'tools', 'art', 'blood_hud_source.png')
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
GOLD_HI = (255, 214, 214)
GOLD = (220, 90, 96)
BRONZE = (92, 10, 18)
DARK = (16, 2, 4)
EMBER = (255, 40, 60)
WHITE_HOT = (255, 236, 236)


# ------------------------------------------------------------------ 판 (프레임)
def plate(base, box, rim_w=24, horns=False):
    """금속 테두리 + 안쪽 배경."""
    sh = layer()
    ImageDraw.Draw(sh).polygon(octagon(*grow(box, rim_w + 10)), fill=(0, 0, 0, 200))
    base = over(base, glow(sh, 6))
    outer = poly_mask(octagon(*grow(box, rim_w + 4)))
    base = over(base, vgrad(outer, (50, 6, 10), (14, 2, 4), box[1] - rim_w, box[3] + rim_w))
    rim = poly_mask(octagon(*grow(box, rim_w)))
    base = over(base, vgrad(rim, GOLD_HI, BRONZE, box[1] - rim_w, box[3] + rim_w))
    inner = poly_mask(octagon(*grow(box, rim_w * 0.55)))
    base = over(base, vgrad(inner, (66, 8, 14), (18, 2, 4), box[1], box[3]))
    line = poly_mask(octagon(*grow(box, rim_w * 0.28)))
    base = over(base, vgrad(line, (255, 170, 170), (150, 20, 34), box[1], box[3]))
    # 비늘 무늬 테두리 (모서리 장식)
    d = layer()
    dd = ImageDraw.Draw(d)
    x0, y0, x1, y1, c = grow(box, rim_w * 0.78)
    for (px, py) in [(x0 + c * 0.5, y0 + c * 0.5), (x1 - c * 0.5, y0 + c * 0.5), (x0 + c * 0.5, y1 - c * 0.5), (x1 - c * 0.5, y1 - c * 0.5)]:
        s = rim_w * 0.55
        dd.polygon([(px, py - s), (px + s * 0.7, py), (px, py + s), (px - s * 0.7, py)], fill=(60, 6, 12, 255))
        s2 = s * 0.6
        dd.polygon([(px, py - s2), (px + s2 * 0.7, py), (px, py + s2), (px - s2 * 0.7, py)], fill=(255, 110, 120, 255))
    base = over(base, d)
    base = add(base, glow(d, 6), 0.5)
    return base


def slot_bg(base, box, hot=(110, 12, 22)):
    m = poly_mask(octagon(*box))
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    rad = max(box[2] - box[0], box[3] - box[1]) * 0.72
    base = over(base, radial(m, cx, cy - (box[3] - box[1]) * 0.08, rad, hot, (14, 2, 4)))
    # 은은한 비늘 무늬
    sc = layer()
    d = ImageDraw.Draw(sc)
    step = 34
    for j, y in enumerate(range(int(box[1]) - step, int(box[3]) + step, step // 2)):
        off = (step // 2) if j % 2 else 0
        for x in range(int(box[0]) - step + off, int(box[2]) + step, step):
            d.arc([x - step / 2, y - step / 2, x + step / 2, y + step / 2], 20, 160, fill=(255, 140, 140, 22), width=3)
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


BLOOD = (255, 30, 50)
CORE = (255, 210, 210)


def taper_line(d, pts, w, head=0.5, fill=(255, 255, 255, 255), power=0.8):
    d.polygon(taper(pts, w, head=head, power=power), fill=fill)


def bat_poly(cx, cy, s, flip=False, tilt=0.0):
    right = [(0.0, -0.18), (0.08, -0.42), (0.16, -0.2), (0.5, -0.42), (1.0, -0.28), (0.86, 0.02), (0.72, -0.06), (0.6, 0.14),
             (0.44, 0.0), (0.3, 0.18), (0.14, 0.04), (0.0, 0.32)]
    pts = right + [(-x, y) for (x, y) in reversed(right[1:-1])]
    ca, sa = math.cos(tilt), math.sin(tilt)
    out = []
    for x, y in pts:
        if flip:
            y = -y * 0.6
        out.append((cx + (x * ca - y * sa) * s, cy + (x * sa + y * ca) * s))
    return out


def drop_poly(cx, cy, r):
    pts = []
    for i in range(40):
        a = i / 40 * math.tau
        x, y = math.cos(a) * r, math.sin(a) * r
        if y < 0:
            k = -y / r
            x *= (1 - k) ** 0.9
            y *= 1 + 1.2 * k
        pts.append((cx + x, cy + y))
    return pts


def droplets(d, box, n, seed, rmin=4, rmax=10):
    r = random.Random(seed)
    x0, y0, x1, y1 = box
    for _ in range(n):
        x, y, s = r.uniform(x0, x1), r.uniform(y0, y1), r.uniform(rmin, rmax)
        d.polygon(drop_poly(x, y, s), fill=(255, 255, 255, 255))


def dark(base, m, shape_fn, col=(22, 2, 6, 255)):
    lay = layer()
    shape_fn(ImageDraw.Draw(lay), col)
    return over(base, clip_to(lay, m))


def figure(base, m, cx, cy, s, col=(20, 2, 6, 255), alpha=1.0):
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
    """혈사포박: 손끝에서 뻗는 피의 실이 적에게 갈고리처럼 박힘."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    base = figure(base, m, x1 - 62, cy + 20, 1.05)
    ol = layer()
    figure_rim = figure(layer(), Image.new('L', (W, H), 255), x1 - 62, cy + 20, 1.05, col=(255, 255, 255, 255))
    rim_a = arr(figure_rim)
    edge = np.clip(arr(glow(figure_rim, 3))[..., 3] * 2.2 - rim_a[..., 3] * 2, 0, 1)
    e = np.zeros_like(rim_a)
    e[..., 0] = e[..., 1] = e[..., 2] = 1
    e[..., 3] = edge
    base = lit(base, img(e), m, core=(255, 120, 130), col=BLOOD, g1=3, g2=10, k=0.6)
    th = layer()
    d = ImageDraw.Draw(th)
    p0, p2 = (x0 + 24, y1 - 46), (x1 - 70, cy - 8)
    pts = []
    for i in range(70):
        t = i / 69
        x = p0[0] + (p2[0] - p0[0]) * t
        y = p0[1] + (p2[1] - p0[1]) * t + math.sin(t * math.pi * 3.2) * 18 * (1 - t) - math.sin(t * math.pi) * 30
        pts.append((x, y))
    taper_line(d, pts, 9, head=0.7, power=0.5)
    ex, ey = p2
    for a in (-0.9, 0, 0.9):
        q = bez((ex - 6, ey), (ex + 22 * math.cos(a) + 10, ey + 22 * math.sin(a)), (ex + 6 + 30 * math.cos(a * 1.4), ey + 30 * math.sin(a * 1.4) + 12), 16)
        taper_line(d, q, 7, head=0.1, power=0.6)
    base = lit(base, th, m, core=CORE, col=BLOOD, g1=8, g2=24, k=1.1)
    dr = layer()
    droplets(ImageDraw.Draw(dr), (x0 + 50, cy + 10, x1 - 90, y1 - 30), 7, 3, 4, 9)
    return lit(base, dr, m, core=(255, 150, 160), col=BLOOD, g1=4, g2=10, k=0.7)


def icon_skill2(base, box, m):
    """혈창분출: 땅에서 솟아오르는 세 개의 피의 창."""
    x0, y0, x1, y1, _ = box
    gy = y1 - 50
    sp = layer()
    d = ImageDraw.Draw(sp)
    for i, (px, h, w) in enumerate(((x0 + 62, 110, 26), (x0 + 140, 170, 32), (x1 - 62, 230, 40))):
        d.polygon([(px - w, gy + 10), (px + w * 0.15, gy - h), (px + w, gy + 10)], fill=(70, 4, 12, 255))
        d.polygon([(px - w * 0.2, gy), (px + w * 0.15, gy - h), (px + w * 0.35, gy)], fill=(150, 10, 26, 255))
    base = over(base, clip_to(sp, m))
    rim = layer()
    d = ImageDraw.Draw(rim)
    for (px, h, w) in ((x0 + 62, 110, 26), (x0 + 140, 170, 32), (x1 - 62, 230, 40)):
        d.line([(px - w, gy + 10), (px + w * 0.15, gy - h), (px + w, gy + 10)], fill=(255, 255, 255, 255), width=4)
    base = lit(base, rim, m, core=CORE, col=BLOOD, g1=6, g2=18)
    gr = layer()
    d = ImageDraw.Draw(gr)
    d.ellipse([x0 + 10, gy - 6, x1 - 10, gy + 30], fill=(255, 255, 255, 255))
    base = lit(base, gr, m, core=(200, 20, 40), col=BLOOD, g1=10, g2=30, k=0.8)
    dr = layer()
    droplets(ImageDraw.Draw(dr), (x0 + 30, y0 + 30, x1 - 30, gy - 60), 9, 5, 4, 10)
    return lit(base, dr, m, core=(255, 150, 160), col=BLOOD, g1=4, g2=10, k=0.8)


def icon_skill3(base, box, m):
    """흡혈난무: 네 줄기 발톱 자국 + 송곳니."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    sl = layer()
    d = ImageDraw.Draw(sl)
    for k in range(-1, 3):
        o = k * 36 - 18
        taper_line(d, bez((x0 + 30 + o, y0 + 30), (cx + o - 20, cy - 10), (x1 - 70 + o, y1 - 40), 40), 11, head=0.45)
    base = lit(base, sl, m, core=CORE, col=BLOOD, g1=8, g2=24)
    fg = layer()
    d = ImageDraw.Draw(fg)
    for sx in (-1, 1):
        fx = cx + sx * 38
        d.polygon([(fx - 20, cy + 30), (fx + 20, cy + 30), (fx + sx * 4, cy + 120)], fill=(255, 255, 255, 255))
    base = lit(base, fg, m, core=(255, 245, 245), col=(255, 60, 80), g1=6, g2=14, k=0.8)
    dr = layer()
    d = ImageDraw.Draw(dr)
    d.polygon(drop_poly(cx - 34, cy + 138, 9), fill=(255, 255, 255, 255))
    d.polygon(drop_poly(cx + 42, cy + 146, 7), fill=(255, 255, 255, 255))
    return lit(base, dr, m, core=(255, 120, 130), col=BLOOD, g1=4, g2=10)


def icon_skill4(base, box, m):
    """혈막반격: 피의 막과 바깥으로 뻗는 가시."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    mem = layer()
    d = ImageDraw.Draw(mem)
    d.ellipse([cx - 92, cy - 92, cx + 92, cy + 92], fill=(120, 6, 20, 150))
    base = over(base, clip_to(mem, m))
    ring = layer()
    d = ImageDraw.Draw(ring)
    d.ellipse([cx - 92, cy - 92, cx + 92, cy + 92], outline=(255, 255, 255, 255), width=8)
    for a in range(10):
        ang = a / 10 * math.tau + 0.3
        ca, sa = math.cos(ang), math.sin(ang)
        px, py = -sa, ca
        d.polygon([(cx + ca * 88 + px * 12, cy + sa * 88 + py * 12), (cx + ca * 136, cy + sa * 136), (cx + ca * 88 - px * 12, cy + sa * 88 - py * 12)], fill=(255, 255, 255, 255))
    base = lit(base, ring, m, core=CORE, col=BLOOD, g1=8, g2=22)
    dr = layer()
    ImageDraw.Draw(dr).polygon(drop_poly(cx, cy + 22, 40), fill=(255, 255, 255, 255))
    return lit(base, dr, m, core=(255, 90, 100), col=BLOOD, g1=10, g2=26)


def icon_ultimate(base, box, m):
    """혈월만개: 핏빛 보름달 앞에 박쥐 날개를 펼친 혈귀와 박쥐 떼."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    moon = layer()
    ImageDraw.Draw(moon).ellipse([cx - 130, cy - 150, cx + 130, cy + 110], fill=(255, 255, 255, 255))
    base = lit(base, moon, m, core=(230, 40, 50), col=(255, 30, 40), g1=20, g2=60, k=1.1)
    cr = layer()
    d = ImageDraw.Draw(cr)
    r = random.Random(12)
    for _ in range(14):
        x, y, s = cx + r.uniform(-100, 100), cy - 20 + r.uniform(-100, 90), r.uniform(10, 26)
        if (x - cx) ** 2 + (y - cy + 20) ** 2 < 115 ** 2:
            d.ellipse([x - s, y - s, x + s, y + s], fill=(150, 10, 20, 120))
    base = over(base, clip_to(cr, m))
    def sil(d, col):
        # 날개를 펼친 혈귀
        d.polygon(bat_poly(cx, cy + 40, 175), fill=col)
        d.ellipse([cx - 16, cy - 30, cx + 16, cy + 2], fill=col)
        d.rectangle([cx - 18, cy, cx + 18, cy + 80], fill=col)
        d.rectangle([cx - 16, cy + 80, cx - 3, cy + 130], fill=col)
        d.rectangle([cx + 3, cy + 80, cx + 16, cy + 130], fill=col)
        rr = random.Random(5)
        for _ in range(9):
            bx, by = cx + rr.choice((-1, 1)) * rr.uniform(120, 190), cy + rr.uniform(-140, 40)
            d.polygon(bat_poly(bx, by, rr.uniform(16, 30), tilt=rr.uniform(-0.4, 0.4)), fill=col)
    base = dark(base, m, sil)
    eye = layer()
    d = ImageDraw.Draw(eye)
    d.ellipse([cx - 9, cy - 18, cx - 3, cy - 12], fill=(255, 255, 255, 255))
    d.ellipse([cx + 3, cy - 18, cx + 9, cy - 12], fill=(255, 255, 255, 255))
    base = lit(base, eye, m, core=(255, 220, 220), col=BLOOD, g1=4, g2=10)
    rain = layer()
    d = ImageDraw.Draw(rain)
    r = random.Random(9)
    for _ in range(26):
        x, y = r.uniform(x0, x1), r.uniform(y0, y1)
        d.line([(x, y), (x - 4, y + 26)], fill=(255, 255, 255, 140), width=3)
    return lit(base, rain, m, core=(255, 120, 130), col=BLOOD, g1=3, g2=8, k=0.6)


def icon_passive(base, box, m):
    """박쥐산개: 흩어져 날아가는 박쥐 떼."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    tr = layer()
    d = ImageDraw.Draw(tr)
    taper_line(d, bez((x0 + 10, y1 - 20), (cx - 20, cy + 30), (x1 - 30, y0 + 40), 40), 26, head=0.85, fill=(255, 255, 255, 120))
    base = lit(base, tr, m, core=(150, 10, 26), col=BLOOD, g1=10, g2=24, k=0.6)
    r = random.Random(14)
    bats = [(x0 + 46 + i * 30 + r.uniform(-8, 8), y1 - 44 - i * 24 + r.uniform(-10, 10), 22 + i * 7) for i in range(5)]
    def sil(d, col):
        for (bx, by, s) in bats:
            d.polygon(bat_poly(bx, by, s, tilt=-0.3), fill=col)
    base = dark(base, m, sil)
    rim = layer()
    d = ImageDraw.Draw(rim)
    for (bx, by, s) in bats:
        d.line(bat_poly(bx, by, s, tilt=-0.3) + [bat_poly(bx, by, s, tilt=-0.3)[0]], fill=(255, 255, 255, 255), width=3)
    return lit(base, rim, m, core=(255, 140, 150), col=BLOOD, g1=4, g2=10, k=0.8)


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
    base = over(base, radial(im, 400, 193, 1300, (70, 8, 14), (14, 2, 4), 1.2))
    # 비늘 무늬 + 불꽃 줄기
    sc = layer()
    d = ImageDraw.Draw(sc)
    step = 30
    for j, y in enumerate(range(100, 290, 15)):
        off = step // 2 if j % 2 else 0
        for x in range(40 + off, 1880, step):
            d.arc([x - step / 2, y - step / 2, x + step / 2, y + step / 2], 20, 160, fill=(255, 140, 140, 20), width=2)
    base = over(base, clip_to(sc, im))
    fl = layer()
    d = ImageDraw.Draw(fl)
    for i in range(9):
        y = 150 + i * 13
        xs = 420 + i * 70
        p = bez((xs, y + 40), (xs + 300, y - 50 + (i % 3) * 30), (xs + 720, y + 10), 30)
        d.polygon(taper(p, 5 + (i % 3) * 2, head=0.4), fill=(255, 255, 255, 120))
    base = add(base, clip_to(glow(img(arr(fl) * np.array([1, 0.2, 0.25, 1], np.float32)), 6), im), 0.7)
    em = layer()
    embers(em, (300, 125, 1680, 262), 26, [(255, 220, 220, 255), (255, 70, 80, 255)], 4, 13, seed=11)
    base = add(base, glow(em, 3), 0.9)
    base = over(base, clip_to(em, im))
    # 왼쪽 장식: 용의 눈 보석
    gem = layer()
    d = ImageDraw.Draw(gem)
    d.polygon([(90, 150), (120, 193), (90, 236), (60, 193)], fill=(50, 4, 8, 255), outline=(255, 170, 170, 255), width=4)
    d.polygon([(90, 166), (98, 193), (90, 220), (82, 193)], fill=(255, 200, 200, 255))
    base = over(base, gem)
    g2 = layer()
    ImageDraw.Draw(g2).polygon([(90, 166), (98, 193), (90, 220), (82, 193)], fill=(255, 40, 60, 255))
    base = add(base, glow(g2, 12), 1.0)
    # 이름
    font = ImageFont.truetype(FONT, 96)
    tl = layer()
    d = ImageDraw.Draw(tl)
    d.text((150, 193), '혈귀', font=font, anchor='lm', fill=(255, 255, 255, 255))
    base = add(base, glow(img(arr(tl) * np.array([1, 0.2, 0.25, 1], np.float32)), 10), 1.0)
    d2 = layer()
    ImageDraw.Draw(d2).text((150, 193), '혈귀', font=font, anchor='lm', fill=(24, 2, 4, 255), stroke_width=10, stroke_fill=(24, 2, 4, 255))
    ImageDraw.Draw(d2).text((150, 193), '혈귀', font=font, anchor='lm', fill=(255, 240, 240, 255), stroke_width=3, stroke_fill=(255, 240, 240, 255))
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
    col = a * np.array([1, 0.18, 0.22, 1], np.float32)
    base = add(base, glow(img(col), 10), 1.0)
    base = add(base, img(a * np.array([1, 0.6, 0.6, 0.85], np.float32)), 0.8)
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
    base = over(base, vgrad(hm, (255, 232, 232), (120, 30, 40), y0 - 130, y0 + 20))
    ol = layer()
    for side in (-1, 1):
        bx = x0 + c * 0.6 if side < 0 else x1 - c * 0.6
        by = y0 + c * 0.2
        p = bez((bx, by), (bx + side * 70, by - 40), (bx + side * 40, by - 120), 30)
        ImageDraw.Draw(ol).line(taper(p, 22, head=0.04, power=0.45) + [p[0]], fill=(30, 4, 6, 255), width=4)
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
        ImageDraw.Draw(tl).text((cx, cy), ch, font=font, anchor='mm', fill=(255, 50, 70, 255))
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
    base, m = slot_bg(base, ub, hot=(140, 14, 28))
    base = icon_ultimate(base, ub, m)
    base = slot_vignette(base, ub, m)
    # 궁극기 둘레 불티
    em = layer()
    embers(em, (ub[0] - 60, ub[1] - 40, ub[2] + 50, ub[3] + 40), 22, [(255, 230, 230, 255), (255, 70, 80, 255)], 5, 18, seed=21)
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
    img(a).save(os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures', 'gui', 'card_blood.png'))



if __name__ == '__main__':
    main()
