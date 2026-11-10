#!/usr/bin/env python3
"""
흑마법사 스킬바 원본 그림 (tools/art/warlock_hud_source.png) 생성 (녹색 + 보라 테마). 사용자 그림이 오면 교체.
공허 스킬바와 같은 배치(1907x825)라서 gen_hud.py 의 슬롯 좌표를 그대로 쓴다.
그 다음 `python3 tools/gen_hud.py` 로 HUD 텍스처/마스크/아이콘을 만든다.
"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), '..')
OUT = os.path.join(ROOT, 'tools', 'art', 'warlock_hud_source.png')
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
GOLD_HI = (210, 255, 230)
GOLD = (90, 200, 140)
BRONZE = (20, 60, 40)
DARK = (8, 6, 12)
EMBER = (90, 255, 160)
WHITE_HOT = (235, 255, 245)


# ------------------------------------------------------------------ 판 (프레임)
def plate(base, box, rim_w=24, horns=False):
    """금속 테두리 + 안쪽 배경."""
    sh = layer()
    ImageDraw.Draw(sh).polygon(octagon(*grow(box, rim_w + 10)), fill=(0, 0, 0, 200))
    base = over(base, glow(sh, 6))
    outer = poly_mask(octagon(*grow(box, rim_w + 4)))
    base = over(base, vgrad(outer, (20, 40, 30), (8, 6, 12), box[1] - rim_w, box[3] + rim_w))
    rim = poly_mask(octagon(*grow(box, rim_w)))
    base = over(base, vgrad(rim, GOLD_HI, BRONZE, box[1] - rim_w, box[3] + rim_w))
    inner = poly_mask(octagon(*grow(box, rim_w * 0.55)))
    base = over(base, vgrad(inner, (26, 16, 36), (8, 6, 14), box[1], box[3]))
    line = poly_mask(octagon(*grow(box, rim_w * 0.28)))
    base = over(base, vgrad(line, (190, 255, 220), (40, 140, 90), box[1], box[3]))
    # 비늘 무늬 테두리 (모서리 장식)
    d = layer()
    dd = ImageDraw.Draw(d)
    x0, y0, x1, y1, c = grow(box, rim_w * 0.78)
    for (px, py) in [(x0 + c * 0.5, y0 + c * 0.5), (x1 - c * 0.5, y0 + c * 0.5), (x0 + c * 0.5, y1 - c * 0.5), (x1 - c * 0.5, y1 - c * 0.5)]:
        s = rim_w * 0.55
        dd.polygon([(px, py - s), (px + s * 0.7, py), (px, py + s), (px - s * 0.7, py)], fill=(30, 16, 40, 255))
        s2 = s * 0.6
        dd.polygon([(px, py - s2), (px + s2 * 0.7, py), (px, py + s2), (px - s2 * 0.7, py)], fill=(150, 90, 255, 255))
    base = over(base, d)
    base = add(base, glow(d, 6), 0.5)
    return base


def slot_bg(base, box, hot=(50, 30, 80)):
    m = poly_mask(octagon(*box))
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    rad = max(box[2] - box[0], box[3] - box[1]) * 0.72
    base = over(base, radial(m, cx, cy - (box[3] - box[1]) * 0.08, rad, hot, (8, 6, 12)))
    # 은은한 비늘 무늬
    sc = layer()
    d = ImageDraw.Draw(sc)
    step = 34
    for j, y in enumerate(range(int(box[1]) - step, int(box[3]) + step, step // 2)):
        off = (step // 2) if j % 2 else 0
        for x in range(int(box[0]) - step + off, int(box[2]) + step, step):
            d.arc([x - step / 2, y - step / 2, x + step / 2, y + step / 2], 20, 160, fill=(160, 255, 200, 22), width=3)
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


GLOW = (90, 255, 160)
CORE = (225, 255, 240)
VIO = (150, 90, 255)


def taper_line(d, pts, w, head=0.5, fill=(255, 255, 255, 255), power=0.8):
    d.polygon(taper(pts, w, head=head, power=power), fill=fill)


def skull(d, cx, cy, s, fill=(255, 255, 255, 255), hole=(0, 0, 0, 0)):
    d.ellipse([cx - s, cy - s * 1.05, cx + s, cy + s * 0.75], fill=fill)
    d.rectangle([cx - s * 0.6, cy + s * 0.5, cx + s * 0.6, cy + s * 1.1], fill=fill)
    d.ellipse([cx - s * 0.62, cy - s * 0.3, cx - s * 0.12, cy + s * 0.22], fill=hole)
    d.ellipse([cx + s * 0.12, cy - s * 0.3, cx + s * 0.62, cy + s * 0.22], fill=hole)
    d.polygon([(cx, cy + s * 0.3), (cx - s * 0.12, cy + s * 0.5), (cx + s * 0.12, cy + s * 0.5)], fill=hole)
    for k in (-0.3, 0, 0.3):
        d.line([(cx + s * k, cy + s * 0.75), (cx + s * k, cy + s * 1.1)], fill=hole, width=max(2, int(s * 0.08)))


def ghost(d, cx, cy, s, fill=(255, 255, 255, 255)):
    d.ellipse([cx - s, cy - s, cx + s, cy + s * 0.6], fill=fill)
    pts = [(cx - s, cy), (cx + s, cy)]
    for i in range(7):
        t = i / 6
        pts.append((cx + s - 2 * s * t, cy + s * (1.4 if i % 2 == 0 else 1.0)))
    d.polygon(pts, fill=fill)


def icon_skill1(base, box, m):
    """해골 소환: 빛나는 눈의 해골 + 교차한 뼈."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 - 10
    bn = layer()
    d = ImageDraw.Draw(bn)
    for sgn in (-1, 1):
        d.line([(cx - 110 * sgn, cy + 110), (cx + 110 * sgn, cy - 40)], fill=(255, 255, 255, 255), width=18)
        for (ex, ey) in ((cx - 110 * sgn, cy + 110), (cx + 110 * sgn, cy - 40)):
            d.ellipse([ex - 14, ey - 14, ex + 14, ey + 14], fill=(255, 255, 255, 255))
    base = lit(base, bn, m, core=(230, 236, 230), col=(120, 200, 160), g1=4, g2=12, k=0.5)
    sk = layer()
    skull(ImageDraw.Draw(sk), cx, cy + 6, 70, fill=(236, 240, 230, 255), hole=(0, 0, 0, 0))
    base = over(base, clip_to(sk, m))
    ey = layer()
    d = ImageDraw.Draw(ey)
    for ex in (cx - 26, cx + 26):
        d.ellipse([ex - 14, cy - 8, ex + 14, cy + 20], fill=(255, 255, 255, 255))
    return lit(base, ey, m, core=(200, 255, 220), col=GLOW, g1=6, g2=18, k=1.2)


def icon_skill2(base, box, m):
    """사령 소환: 소환진에서 솟아오르는 영혼들."""
    x0, y0, x1, y1, _ = box
    cx = (x0 + x1) / 2
    gy = y1 - 60
    ci = layer()
    d = ImageDraw.Draw(ci)
    d.ellipse([cx - 120, gy - 26, cx + 120, gy + 26], outline=(255, 255, 255, 255), width=6)
    d.ellipse([cx - 90, gy - 18, cx + 90, gy + 18], outline=(255, 255, 255, 160), width=3)
    base = lit(base, ci, m, core=CORE, col=GLOW, g1=6, g2=18)
    gh = layer()
    d = ImageDraw.Draw(gh)
    ghost(d, cx - 70, gy - 70, 34)
    ghost(d, cx + 70, gy - 90, 30)
    ghost(d, cx, gy - 150, 42)
    base = lit(base, gh, m, core=(220, 255, 235), col=GLOW, g1=8, g2=22, k=0.9)
    ey = layer()
    d = ImageDraw.Draw(ey)
    for (gx, gyy, s) in ((cx - 70, gy - 70, 34), (cx + 70, gy - 90, 30), (cx, gy - 150, 42)):
        for ex in (gx - s * 0.35, gx + s * 0.35):
            d.ellipse([ex - s * 0.15, gyy - s * 0.25, ex + s * 0.15, gyy + s * 0.15], fill=(10, 30, 20, 255))
    return over(base, clip_to(ey, m))


def icon_skill3(base, box, m):
    """흑마법 광선: 손에서 뻗는 녹색 광선 + 감기는 나선."""
    x0, y0, x1, y1, _ = box
    cy = (y0 + y1) / 2
    bm = layer()
    line = [(x0 + 40 + (x1 - x0 - 50) * t / 29, cy + 30 - 60 * t / 29) for t in range(30)]
    taper_line(ImageDraw.Draw(bm), line, 30, head=0.08, power=0.3)
    base = lit(base, bm, m, core=(235, 255, 240), col=GLOW, g1=10, g2=28)
    sp = layer()
    d = ImageDraw.Draw(sp)
    pts = []
    for i in range(80):
        t = i / 79
        x = x0 + 40 + (x1 - x0 - 50) * t
        y = cy + 30 - 60 * t + math.sin(t * 18) * 26
        pts.append((x, y))
    d.line(pts, fill=(255, 255, 255, 255), width=5)
    base = lit(base, sp, m, core=(220, 200, 255), col=VIO, g1=4, g2=12, k=0.9)
    hd = layer()
    d = ImageDraw.Draw(hd)
    d.ellipse([x0 + 10, cy + 6, x0 + 70, cy + 66], fill=(30, 20, 40, 255))
    return over(base, clip_to(hd, m))


def icon_skill4(base, box, m):
    """속박: X 자로 교차한 어둠의 사슬 + 룬 고리."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    rg = layer()
    ImageDraw.Draw(rg).ellipse([cx - 110, cy - 110, cx + 110, cy + 110], outline=(255, 255, 255, 255), width=6)
    base = lit(base, rg, m, core=CORE, col=GLOW, g1=6, g2=18, k=0.8)
    ch = layer()
    d = ImageDraw.Draw(ch)
    for sgn in (-1, 1):
        for i in range(8):
            t = i / 7
            x = cx - 120 * sgn + 240 * sgn * t
            y = cy - 120 + 240 * t
            if i % 2 == 0:
                d.ellipse([x - 22, y - 14, x + 22, y + 14], outline=(170, 180, 190, 255), width=8)
            else:
                d.ellipse([x - 14, y - 22, x + 14, y + 22], outline=(130, 140, 150, 255), width=8)
    base = over(base, clip_to(ch, m))
    gl = layer()
    d = ImageDraw.Draw(gl)
    d.ellipse([cx - 22, cy - 22, cx + 22, cy + 22], fill=(255, 255, 255, 255))
    return lit(base, gl, m, core=(220, 255, 235), col=GLOW, g1=6, g2=18)


def icon_ultimate(base, box, m):
    """영혼 강탈: 땅에서 솟은 거대한 어둠의 손이 빛나는 영혼을 움켜쥠."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 + 20
    fingers = ((-120, -40), (-62, -120), (0, -140), (62, -120), (120, -50))
    hd = layer()
    d = ImageDraw.Draw(hd)
    d.polygon([(cx - 130, y1), (cx + 130, y1), (cx + 100, cy + 30), (cx - 100, cy + 30)], fill=(16, 8, 24, 255))
    for fx, top in fingers:
        p = bez((cx + fx * 0.8, cy + 40), (cx + fx * 1.35, cy + top * 0.5), (cx + fx * 0.55, cy + top), 20)
        d.polygon(taper(p, 24, head=0.1, power=0.5), fill=(16, 8, 24, 255))
    base = over(base, clip_to(hd, m))
    rim = layer()
    d = ImageDraw.Draw(rim)
    for fx, top in fingers:
        p = bez((cx + fx * 0.8, cy + 40), (cx + fx * 1.35, cy + top * 0.5), (cx + fx * 0.55, cy + top), 20)
        d.line(p, fill=(255, 255, 255, 255), width=3)
    base = lit(base, rim, m, core=(180, 120, 255), col=VIO, g1=4, g2=12, k=0.8)
    sl = layer()
    d = ImageDraw.Draw(sl)
    ghost(d, cx, cy - 60, 46)
    base = lit(base, sl, m, core=(230, 255, 240), col=GLOW, g1=10, g2=30, k=1.1)
    ey = layer()
    d = ImageDraw.Draw(ey)
    for ex in (cx - 16, cx + 16):
        d.ellipse([ex - 7, cy - 72, ex + 7, cy - 56], fill=(10, 30, 20, 255))
    return over(base, clip_to(ey, m))


def icon_passive(base, box, m):
    """영혼 수집: 사방에서 모여드는 작은 영혼들."""
    x0, y0, x1, y1, _ = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    ar = layer()
    d = ImageDraw.Draw(ar)
    for i in range(6):
        a = i / 6 * math.tau
        p0 = (cx + math.cos(a) * 82, cy + math.sin(a) * 82)
        p1 = (cx + math.cos(a + 0.6) * 40, cy + math.sin(a + 0.6) * 40)
        d.line([p0, p1, (cx, cy)], fill=(255, 255, 255, 160), width=4)
    base = lit(base, ar, m, core=CORE, col=GLOW, g1=4, g2=12, k=0.6)
    gh = layer()
    d = ImageDraw.Draw(gh)
    for i in range(6):
        a = i / 6 * math.tau
        ghost(d, cx + math.cos(a) * 80, cy + math.sin(a) * 80, 13)
    d.ellipse([cx - 26, cy - 26, cx + 26, cy + 26], fill=(255, 255, 255, 255))
    return lit(base, gh, m, core=(230, 255, 240), col=GLOW, g1=6, g2=16)


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
    base = over(base, radial(im, 400, 193, 1300, (36, 22, 52), (8, 6, 12), 1.2))
    # 비늘 무늬 + 불꽃 줄기
    sc = layer()
    d = ImageDraw.Draw(sc)
    step = 30
    for j, y in enumerate(range(100, 290, 15)):
        off = step // 2 if j % 2 else 0
        for x in range(40 + off, 1880, step):
            d.arc([x - step / 2, y - step / 2, x + step / 2, y + step / 2], 20, 160, fill=(160, 255, 200, 20), width=2)
    base = over(base, clip_to(sc, im))
    fl = layer()
    d = ImageDraw.Draw(fl)
    for i in range(9):
        y = 150 + i * 13
        xs = 420 + i * 70
        p = bez((xs, y + 40), (xs + 300, y - 50 + (i % 3) * 30), (xs + 720, y + 10), 30)
        d.polygon(taper(p, 5 + (i % 3) * 2, head=0.4), fill=(255, 255, 255, 120))
    base = add(base, clip_to(glow(img(arr(fl) * np.array([0.45, 1, 0.7, 1], np.float32)), 6), im), 0.7)
    em = layer()
    embers(em, (300, 125, 1680, 262), 26, [(220, 255, 235, 255), (90, 255, 160, 255)], 4, 13, seed=11)
    base = add(base, glow(em, 3), 0.9)
    base = over(base, clip_to(em, im))
    # 왼쪽 장식: 용의 눈 보석
    gem = layer()
    d = ImageDraw.Draw(gem)
    d.polygon([(90, 150), (120, 193), (90, 236), (60, 193)], fill=(20, 10, 30, 255), outline=(190, 255, 220, 255), width=4)
    d.polygon([(90, 166), (98, 193), (90, 220), (82, 193)], fill=(220, 255, 235, 255))
    base = over(base, gem)
    g2 = layer()
    ImageDraw.Draw(g2).polygon([(90, 166), (98, 193), (90, 220), (82, 193)], fill=(90, 255, 160, 255))
    base = add(base, glow(g2, 12), 1.0)
    # 이름
    font = ImageFont.truetype(FONT, 96)
    tl = layer()
    d = ImageDraw.Draw(tl)
    d.text((150, 193), '흑마법사', font=font, anchor='lm', fill=(255, 255, 255, 255))
    base = add(base, glow(img(arr(tl) * np.array([0.45, 1, 0.7, 1], np.float32)), 10), 1.0)
    d2 = layer()
    ImageDraw.Draw(d2).text((150, 193), '흑마법사', font=font, anchor='lm', fill=(4, 14, 8, 255), stroke_width=10, stroke_fill=(4, 14, 8, 255))
    ImageDraw.Draw(d2).text((150, 193), '흑마법사', font=font, anchor='lm', fill=(255, 250, 235, 255), stroke_width=3, stroke_fill=(255, 250, 235, 255))
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
    col = a * np.array([0.4, 1, 0.65, 1], np.float32)
    base = add(base, glow(img(col), 10), 1.0)
    base = add(base, img(a * np.array([0.8, 1, 0.9, 0.85], np.float32)), 0.8)
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
    base = over(base, vgrad(hm, (225, 255, 240), (40, 110, 70), y0 - 130, y0 + 20))
    ol = layer()
    for side in (-1, 1):
        bx = x0 + c * 0.6 if side < 0 else x1 - c * 0.6
        by = y0 + c * 0.2
        p = bez((bx, by), (bx + side * 70, by - 40), (bx + side * 40, by - 120), 30)
        ImageDraw.Draw(ol).line(taper(p, 22, head=0.04, power=0.45) + [p[0]], fill=(6, 24, 14, 255), width=4)
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
        ImageDraw.Draw(tl).text((cx, cy), ch, font=font, anchor='mm', fill=(90, 255, 160, 255))
        base = add(base, glow(tl, 8), 0.7)
        t2 = layer()
        ImageDraw.Draw(t2).text((cx, cy), ch, font=font, anchor='mm', fill=(4, 14, 8, 255), stroke_width=11, stroke_fill=(4, 14, 8, 255))
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
    base, m = slot_bg(base, ub, hot=(60, 30, 100))
    base = icon_ultimate(base, ub, m)
    base = slot_vignette(base, ub, m)
    # 궁극기 둘레 불티
    em = layer()
    embers(em, (ub[0] - 60, ub[1] - 40, ub[2] + 50, ub[3] + 40), 22, [(225, 255, 240, 255), (90, 255, 160, 255)], 5, 18, seed=21)
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
    img(a).save(os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures', 'gui', 'card_warlock.png'))



if __name__ == '__main__':
    main()
