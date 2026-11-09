#!/usr/bin/env python3
"""혈귀 텍스처: 핏줄/눈 (스킨 위), 박쥐 날개, 박쥐, 핏빛 달, 피의 창, 핏자국, 사슬."""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..')
FX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures', 'fx')
random.seed(13)


def save(a, name):
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'RGBA').save(os.path.join(FX, name))


def to_arr(img):
    return np.asarray(img, np.float32) / 255


def veins():
    """256x256 (스킨 64x64 배치의 4배 해상도 = 더 가는 핏줄): 팔/목/얼굴의 핏줄 + 빛나는 눈."""
    S = 256
    v = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(v)
    r = random.Random(4)

    def vein(x0, y0, x1, y1, n, w=2, depth=0):
        for _ in range(n):
            x, y = r.uniform(x0, x1), r.uniform(y0, y0 + (y1 - y0) * 0.4)
            pts = [(x, y)]
            ang = math.pi / 2 + r.uniform(-0.5, 0.5)
            for k in range(10):
                ang += r.uniform(-0.55, 0.55)
                x = min(max(x + math.cos(ang) * 4, x0), x1)
                y = min(max(y + math.sin(ang) * 4, y0), y1)
                pts.append((x, y))
                if depth == 0 and k in (3, 6) and r.random() < 0.6:
                    # 곁가지
                    bx, by, ba = x, y, ang + r.choice((-1, 1)) * r.uniform(0.6, 1.1)
                    bp = [(bx, by)]
                    for _ in range(4):
                        ba += r.uniform(-0.4, 0.4)
                        bx = min(max(bx + math.cos(ba) * 3.5, x0), x1)
                        by = min(max(by + math.sin(ba) * 3.5, y0), y1)
                        bp.append((bx, by))
                    d.line(bp, fill=170, width=1)
            d.line(pts, fill=255, width=w)
    # 오른팔 (40,16)-(56,32), 왼팔 (32,48)-(48,64), 몸통 (16,20)-(40,32), 머리 앞 (8,8)-(16,16), 머리 옆 (0,8)-(8,16),(16,8)-(24,16)
    for (x0, y0, x1, y1, n) in [(40, 20, 56, 32, 9), (32, 52, 48, 64, 9), (16, 22, 40, 32, 7), (0, 10, 8, 16, 3), (16, 10, 24, 16, 3), (24, 12, 32, 16, 3), (8, 13, 16, 16, 2)]:
        vein(x0 * 4, y0 * 4, x1 * 4, y1 * 4, n)
    # 얼굴: 눈 아래로 흐르는 핏자국
    for ex in (9, 13):
        d.line([(ex * 4 + 2, 13 * 4), (ex * 4 + 2, 15 * 4 + 3)], fill=255, width=2)
    vm = to_arr(v.filter(ImageFilter.GaussianBlur(0.5)))
    out = np.zeros((S, S, 4), np.float32)
    out[..., 0], out[..., 1], out[..., 2] = 0.42, 0.02, 0.05
    out[..., 3] = np.clip(vm * 1.2, 0, 0.85)
    save(out, 'blood_veins_skin.png')
    # 빛: 눈 (머리 앞면의 눈 자리) + 핏줄 은은하게 (가산, 미리 곱한 알파)
    g = np.zeros((S, S, 4), np.float32)
    for k, c in enumerate((1.0, 0.12, 0.15)):
        g[..., k] = c * vm * 0.3
    for (x, y) in [(9, 12), (10, 12), (13, 12), (14, 12)]:
        g[y * 4:y * 4 + 4, x * 4:x * 4 + 4, :3] = (1.0, 0.25, 0.2)
        g[y * 4 + 4:y * 4 + 8, x * 4:x * 4 + 4, :3] = (0.7, 0.05, 0.05)
    g[..., 3] = np.clip(g[..., :3].max(-1), 0, 1)
    save(g, 'blood_glow_skin.png')


def dash_icon():
    """Q 대시 아이콘을 핏빛으로 (textures/gui/dash_blood.png)."""
    gui = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures', 'gui')
    a = to_arr(Image.open(os.path.join(gui, 'dash.png')).convert('RGBA'))
    lum = a[..., :3].max(-1)
    sat = lum - a[..., :3].min(-1)
    out = a.copy()
    # 채도 있는 부분(보라)을 핏빛으로, 흰 부분은 그대로
    out[..., 0] = lum
    out[..., 1] = lum - sat * 0.95
    out[..., 2] = lum - sat * 0.9
    Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), 'RGBA').save(os.path.join(gui, 'dash_blood.png'))


def bat_wing():
    """박쥐 날개 (뿌리 = 왼쪽 아래, 끝 = 오른쪽 위), 찢어진 막."""
    S = 512
    img = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(img)
    root = (20, S - 60)
    elbow = (210, 170)
    tips = [(S - 15, 30), (S - 40, 210), (S - 120, 360), (S - 250, 470), (150, S - 10)]
    poly = [root, elbow, tips[0]]
    for i in range(len(tips) - 1):
        a, b = tips[i], tips[i + 1]
        mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        inward = ((mid[0] * 0.7 + root[0] * 0.3), (mid[1] * 0.7 + root[1] * 0.3))
        for k in range(1, 8):
            t = k / 8
            bx = (1 - t) ** 2 * a[0] + 2 * (1 - t) * t * inward[0] + t * t * b[0]
            by = (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * inward[1] + t * t * b[1]
            # 찢어진 자국
            if random.random() < 0.18:
                bx += random.uniform(-14, 14)
                by += random.uniform(-14, 14)
            poly.append((bx, by))
        poly.append(b)
    poly.append(root)
    d.polygon(poly, fill=255)
    m = to_arr(img.filter(ImageFilter.GaussianBlur(1.2)))
    x, y = np.meshgrid(np.arange(S), np.arange(S))
    dist = np.sqrt((x - root[0]) ** 2 + (y - root[1]) ** 2) / (S * 1.2)
    out = np.zeros((S, S, 4), np.float32)
    out[..., 0] = 0.18 + 0.3 * dist
    out[..., 1] = 0.01
    out[..., 2] = 0.03
    out[..., 3] = m * 0.9
    edge = to_arr(img.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.GaussianBlur(2.5)))
    bones = Image.new('L', (S, S), 0)
    bd = ImageDraw.Draw(bones)
    bd.line([root, elbow], fill=255, width=14)
    for t in tips:
        bd.line([elbow, t], fill=255, width=6)
    b = to_arr(bones.filter(ImageFilter.GaussianBlur(1.2)))
    for k, c in enumerate((0.12, 0.02, 0.03)):
        out[..., k] = out[..., k] * (1 - b) + c * b
    out[..., 3] = np.maximum(out[..., 3], b)
    out[..., 0] += edge * 0.6
    out[..., :3] *= (out[..., 3:4] > 0.004)
    save(out, 'blood_wing.png')
    glow = np.zeros((S, S, 4), np.float32)
    gl = np.clip(edge * 0.9 + b * 0.25, 0, 1)
    for k, c in enumerate((1.0, 0.1, 0.12)):
        glow[..., k] = c * gl
    glow[..., 3] = gl
    save(glow, 'blood_wing_glow.png')


def bat():
    """박쥐 실루엣 2프레임 (64x32: 왼쪽 날개 위, 오른쪽 날개 아래)."""
    W, H = 128, 64
    img = Image.new('RGBA', (W * 2, H), (0, 0, 0, 0))
    for f in range(2):
        layer = Image.new('L', (W, H), 0)
        d = ImageDraw.Draw(layer)
        cx, cy = W / 2, H / 2 + 2
        up = f == 0
        for s in (-1, 1):
            tip_y = cy - 26 if up else cy + 20
            pts = [(cx + s * 6, cy - 4), (cx + s * 30, tip_y), (cx + s * 58, tip_y + (6 if up else -10)),
                   (cx + s * 48, cy + (2 if up else 14)), (cx + s * 36, cy + (-2 if up else 10)), (cx + s * 24, cy + (6 if up else 12)), (cx + s * 8, cy + 6)]
            d.polygon(pts, fill=255)
        d.ellipse([cx - 8, cy - 10, cx + 8, cy + 10], fill=255)
        d.polygon([(cx - 7, cy - 8), (cx - 4, cy - 18), (cx - 1, cy - 9)], fill=255)
        d.polygon([(cx + 7, cy - 8), (cx + 4, cy - 18), (cx + 1, cy - 9)], fill=255)
        m = to_arr(layer.filter(ImageFilter.GaussianBlur(0.8)))
        a = np.zeros((H, W, 4), np.float32)
        a[..., 0], a[..., 1], a[..., 2] = 0.12, 0.01, 0.02
        a[..., 3] = m
        e = to_arr(layer.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.GaussianBlur(1)))
        a[..., 0] += e * 0.7
        for (ex, ey) in ((cx - 3, cy - 3), (cx + 3, cy - 3)):
            a[int(ey) - 1:int(ey) + 2, int(ex) - 1:int(ex) + 2, :] = (1, 0.2, 0.2, 1)
        img.paste(Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'RGBA'), (f * W, 0))
    img.save(os.path.join(FX, 'blood_bat.png'))


def moon():
    S = 256
    x, y = np.meshgrid(np.linspace(-1, 1, S), np.linspace(-1, 1, S))
    r = np.sqrt(x * x + y * y)
    rng = np.random.RandomState(7)
    n = np.zeros((S, S), np.float32)
    for k in range(18):
        cx, cy, cr = rng.uniform(-0.6, 0.6), rng.uniform(-0.6, 0.6), rng.uniform(0.05, 0.2)
        dd = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
        n += np.clip(1 - dd / cr, 0, 1) ** 2 * rng.uniform(0.2, 0.5)
    disc = np.clip((0.62 - r) * 40, 0, 1)
    shade = np.clip(1 - (x * 0.5 + y * 0.6 + 0.3), 0.35, 1.2)
    halo = np.exp(-((r - 0.6) / 0.22) ** 2) * (r > 0.55) * 0.8 + np.exp(-r * 2.2) * 0.25
    out = np.zeros((S, S, 4), np.float32)
    body = disc * shade * (1 - 0.45 * n)
    out[..., 0] = body * 1.0 + halo * 1.0
    out[..., 1] = body * 0.16 + halo * 0.12
    out[..., 2] = body * 0.14 + halo * 0.1
    out[..., 3] = np.clip(np.maximum(disc, halo), 0, 1)
    out[..., :3] *= 1  # 가산 합성용 (색 = 세기)
    save(out, 'blood_moon.png')


def spike():
    """피의 창 띠 (가로 = 둘레, 세로 = 아래→위)."""
    W, H = 16, 64
    v = np.linspace(0, 1, H)[:, None] * np.ones((1, W))
    u = np.linspace(0, 1, W)[None, :] * np.ones((H, 1))
    hi = np.exp(-((u - 0.3) / 0.12) ** 2)
    out = np.zeros((H, W, 4), np.float32)
    out[..., 0] = 0.25 + 0.55 * (1 - v) + 0.5 * hi
    out[..., 1] = 0.01 + 0.1 * hi
    out[..., 2] = 0.03 + 0.1 * hi
    out[..., 3] = 1
    save(out, 'blood_spike.png')


def pool():
    S = 128
    img = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(img)
    r = random.Random(3)
    d.ellipse([24, 24, S - 24, S - 24], fill=255)
    for _ in range(26):
        a = r.uniform(0, math.tau)
        dist = r.uniform(30, 58)
        rr = r.uniform(3, 10)
        cx, cy = S / 2 + math.cos(a) * dist, S / 2 + math.sin(a) * dist
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=255)
    m = to_arr(img.filter(ImageFilter.GaussianBlur(2)))
    out = np.zeros((S, S, 4), np.float32)
    out[..., 0], out[..., 1], out[..., 2] = 0.3, 0.0, 0.02
    out[..., 3] = np.clip(m * 1.2, 0, 0.92)
    save(out, 'blood_pool.png')


def chain():
    """사슬 고리 반복 (가로로 반복, 32x16)."""
    W, H = 64, 32
    img = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(img)
    d.ellipse([2, 8, 34, 24], outline=255, width=5)
    d.rectangle([30, 13, 62, 19], fill=255)
    m = to_arr(img.filter(ImageFilter.GaussianBlur(0.6)))
    out = np.zeros((H, W, 4), np.float32)
    out[..., 0], out[..., 1], out[..., 2] = 0.55, 0.06, 0.08
    out[..., 3] = m
    save(out, 'blood_chain.png')


def bleed_icon():
    """상태 효과 아이콘 18x18: 핏방울 (8배로 그려서 줄임)."""
    S = 18 * 8
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    def drop(cx, cy, r, fill):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill)
        d.polygon([(cx - r * 0.86, cy - r * 0.5), (cx, cy - r * 2.3), (cx + r * 0.86, cy - r * 0.5)], fill=fill)
    drop(S * 0.5, S * 0.64, S * 0.3, (20, 0, 2, 255))
    drop(S * 0.5, S * 0.64, S * 0.24, (200, 14, 34, 255))
    d.ellipse([S * 0.36, S * 0.52, S * 0.46, S * 0.66], fill=(255, 140, 150, 255))
    img = img.resize((18, 18), Image.LANCZOS)
    img.save(os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures', 'mob_effect', 'bleed.png'))


if __name__ == '__main__':
    veins(); bat_wing(); bat(); moon(); spike(); pool(); chain(); bleed_icon(); dash_icon()
    print('done')
