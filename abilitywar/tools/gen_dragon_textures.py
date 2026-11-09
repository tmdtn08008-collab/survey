#!/usr/bin/env python3
"""용기사 텍스처 생성: python3 tools/gen_dragon_textures.py"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
import importlib.util
spec = importlib.util.spec_from_file_location('gt', os.path.join(os.path.dirname(__file__), 'gen_textures.py'))
gt = importlib.util.module_from_spec(spec); spec.loader.exec_module(gt)
FX, GUI, grid, value_noise, save, white_alpha = gt.FX, gt.GUI, gt.grid, gt.value_noise, gt.save, gt.white_alpha
random.seed(77)


def wing():
    S = 512
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    root = (20, S - 60)
    # 손가락 뼈 끝점 (오른쪽 위로 펼쳐짐)
    tips = [(S - 20, 40), (S - 30, 200), (S - 90, 340), (S - 210, 450), (200, S - 15)]
    elbow = (230, 150)
    # 막(membrane): 손가락 끝 사이를 안쪽으로 오목하게
    poly = [root, elbow, tips[0]]
    for i in range(len(tips) - 1):
        a, b = tips[i], tips[i + 1]
        mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        inward = ((mid[0] + root[0] * 0.25) / 1.25, (mid[1] + root[1] * 0.25) / 1.25)
        for k in range(1, 6):
            t = k / 6
            bx = (1 - t) ** 2 * a[0] + 2 * (1 - t) * t * inward[0] + t * t * b[0]
            by = (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * inward[1] + t * t * b[1]
            poly.append((bx, by))
        poly.append(b)
    poly.append(root)
    mask = Image.new('L', (S, S), 0)
    ImageDraw.Draw(mask).polygon(poly, fill=255)
    m = np.asarray(mask.filter(ImageFilter.GaussianBlur(1.5)), np.float32) / 255
    x, y = np.meshgrid(np.arange(S), np.arange(S))
    dist = np.sqrt((x - root[0]) ** 2 + (y - root[1]) ** 2) / (S * 1.2)
    n = value_noise(S, S, 6, 5, 21)
    out = np.zeros((S, S, 4), np.float32)
    out[..., 0] = 0.32 + 0.55 * dist + 0.1 * n
    out[..., 1] = 0.05 + 0.25 * dist ** 1.5
    out[..., 2] = 0.04 + 0.04 * n
    out[..., 3] = m * (0.82 + 0.15 * n)
    # 가장자리 빛
    edge = np.asarray(mask.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.GaussianBlur(3)), np.float32) / 255
    out[..., 0] += edge * 0.9
    out[..., 1] += edge * 0.45
    out[..., 3] = np.maximum(out[..., 3], edge * 0.9)
    # 뼈
    bones = Image.new('L', (S, S), 0)
    bd = ImageDraw.Draw(bones)
    bd.line([root, elbow], fill=255, width=16)
    bd.line([elbow, tips[0]], fill=255, width=12)
    for t in tips[1:]:
        bd.line([elbow, t], fill=255, width=7)
    for t in tips:
        bd.ellipse([t[0] - 8, t[1] - 8, t[0] + 8, t[1] + 8], fill=255)
    b = np.asarray(bones.filter(ImageFilter.GaussianBlur(1.5)), np.float32) / 255
    for k, c in enumerate((0.95, 0.72, 0.45)):
        out[..., k] = out[..., k] * (1 - b) + c * b
    out[..., 3] = np.maximum(out[..., 3], b)
    # 핏줄
    veins = Image.new('L', (S, S), 0)
    vd = ImageDraw.Draw(veins)
    for i in range(40):
        a = random.choice(tips[1:])
        sx, sy = elbow[0] + (a[0] - elbow[0]) * random.random(), elbow[1] + (a[1] - elbow[1]) * random.random()
        ang = random.random() * math.tau
        pts = [(sx, sy)]
        for k in range(6):
            ang += random.uniform(-0.6, 0.6)
            sx += math.cos(ang) * 14
            sy += math.sin(ang) * 14
            pts.append((sx, sy))
        vd.line(pts, fill=160, width=2)
    v = np.asarray(veins.filter(ImageFilter.GaussianBlur(1)), np.float32) / 255 * m
    out[..., 0] += v * 0.6
    out[..., 1] += v * 0.3
    # 투명한 곳의 색은 0 (필터링 때 번짐 방지)
    out[..., :3] *= (out[..., 3:4] > 0.004)
    save(out, os.path.join(FX, 'dragon_wing.png'))
    # 빛나는 부분만 (가산 합성용, 미리 곱한 알파): 가장자리 + 뼈 + 핏줄
    g = np.clip(edge * 0.9 + b * 0.45 + v * 0.9, 0, 1)
    glow = np.zeros((S, S, 4), np.float32)
    for k, c in enumerate((1.0, 0.45, 0.12)):
        glow[..., k] = c * g
    glow[..., 3] = g
    save(glow, os.path.join(FX, 'dragon_wing_glow.png'))


def skin_layout_overlay():
    """플레이어 스킨(64x64) 배치로 비늘 + 빛나는 문양."""
    S = 64
    sc = np.zeros((S, S, 4), np.float32)
    gl = np.zeros((S, S, 4), np.float32)
    rng = np.random.default_rng(5)

    def scales(x0, y0, w, h, density=1.0):
        for yy in range(y0, y0 + h):
            for xx in range(x0, x0 + w):
                # 비늘 무늬: 엇갈린 반원
                lx, ly = (xx - x0), (yy - y0)
                off = (ly // 2) % 2
                cx = (lx + off) % 2
                v = 0.55 + 0.35 * (cx == 0) + rng.random() * 0.1
                sc[yy, xx] = [0.45 * v, 0.1 * v, 0.06 * v, 0.85 * density]

    def glow_line(pts):
        im = Image.new('L', (S * 8, S * 8), 0)
        ImageDraw.Draw(im).line([(x * 8, y * 8) for x, y in pts], fill=255, width=6)
        a = np.asarray(im.resize((S, S), Image.LANCZOS), np.float32) / 255
        gl[..., 0] = np.maximum(gl[..., 0], a)
        gl[..., 1] = np.maximum(gl[..., 1], a * 0.55)
        gl[..., 2] = np.maximum(gl[..., 2], a * 0.15)
        gl[..., 3] = np.maximum(gl[..., 3], a)

    # 오른팔 (40,16)-(56,32), 왼팔 (32,48)-(48,64): 아래 2/3 에 비늘
    scales(40, 20, 16, 12)
    scales(32, 52, 16, 12)
    # 몸통 옆/어깨 (16,20)-(40,32) 일부
    scales(16, 20, 4, 12, 0.8)
    scales(28, 20, 12, 12, 0.6)
    scales(20, 20, 8, 3, 0.7)
    # 다리 (0,16)-(16,32), (16,48)-(32,64) 아래쪽
    scales(0, 26, 16, 6, 0.8)
    scales(16, 58, 16, 6, 0.8)
    # 문양
    glow_line([(44, 21), (45, 25), (44, 29), (46, 31)])
    glow_line([(52, 21), (51, 26), (53, 31)])
    glow_line([(36, 53), (37, 57), (36, 61)])
    glow_line([(44, 53), (43, 58), (45, 62)])
    glow_line([(22, 21), (24, 25), (26, 21)])
    glow_line([(24, 25), (24, 31)])
    glow_line([(20, 26), (24, 28), (28, 26)])
    Image.fromarray((np.clip(sc, 0, 1) * 255).astype(np.uint8), 'RGBA').save(os.path.join(FX, 'dragon_scales_skin.png'))
    Image.fromarray((np.clip(gl, 0, 1) * 255).astype(np.uint8), 'RGBA').save(os.path.join(FX, 'dragon_glow_skin.png'))


def rune():
    S = 512
    img = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(img)
    c = S / 2
    d.ellipse([c - 230, c - 230, c + 230, c + 230], outline=255, width=10)
    d.ellipse([c - 200, c - 200, c + 200, c + 200], outline=200, width=4)
    for i in range(12):
        a = i / 12 * math.tau
        x0, y0 = c + math.cos(a) * 200, c + math.sin(a) * 200
        x1, y1 = c + math.cos(a) * 230, c + math.sin(a) * 230
        d.line([(x0, y0), (x1, y1)], fill=255, width=6)
    # 용의 눈 (세로 동공)
    d.ellipse([c - 140, c - 70, c + 140, c + 70], outline=255, width=10)
    d.ellipse([c - 26, c - 64, c + 26, c + 64], fill=255)
    # 발톱 세 줄
    for k in (-1, 0, 1):
        pts = [(c - 150 + k * 30, c + 110 + abs(k) * 10), (c + k * 20, c - 150), (c + 150 + k * 30, c + 110 + abs(k) * 10)]
        d.arc([c - 180 + k * 40, c - 180, c + 180 + k * 40, c + 180], start=200 + k * 8, end=250 + k * 8, fill=255, width=8)
    img = img.filter(ImageFilter.GaussianBlur(1.5))
    a = np.asarray(img.resize((256, 256), Image.LANCZOS), np.float32) / 255
    g = np.asarray(img.filter(ImageFilter.GaussianBlur(12)).resize((256, 256), Image.LANCZOS), np.float32) / 255
    save(white_alpha(np.clip(a * 1.2 + g * 0.6, 0, 1)), os.path.join(FX, 'dragon_rune.png'))


def flame():
    w, h = 64, 128
    x, y = grid(w, h)
    n = value_noise(w, h, 3, 4, 31)
    xx = x + (n - 0.5) * 0.7 * (1 - y) * 0.5
    body = np.exp(-(xx ** 2) * (3 + 6 * (y + 1))) * np.clip((1 - y) / 2, 0, 1) ** 0.6
    body *= np.clip((y + 1) * 1.2, 0, 1)
    tip = np.clip(1 - (y + 1) * 0.9, 0, 1)
    a = np.clip(body * (0.6 + 0.6 * n) - tip * 0.0, 0, 1)
    save(white_alpha(a), os.path.join(FX, 'flame.png'))


def fire_strip():
    w, h = 512, 128
    n1 = value_noise(w, h, 10, 5, 41)
    n2 = value_noise(w, h, 3, 4, 43)
    v = (np.arange(h, dtype=np.float32) + 0.5) / h
    V = np.tile(v[:, None], (1, w))
    core = np.exp(-((V - 0.5) / 0.22) ** 2)
    a = np.clip(core * (0.55 + 0.7 * n1) * (0.7 + 0.5 * n2), 0, 1)
    out = np.zeros((h, w, 4), np.float32)
    hot = np.clip(core * 1.3 * n1, 0, 1)
    out[..., 0] = 1.0
    out[..., 1] = 0.35 + 0.6 * hot
    out[..., 2] = 0.08 + 0.5 * hot ** 2
    out[..., 3] = a
    save(out, os.path.join(FX, 'fire_strip.png'))


def scale_shield():
    S = 512
    img = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(img)
    c = S / 2
    for ring in range(6):
        r = 40 + ring * 36
        n = 6 + ring * 6
        for i in range(n):
            a = i / n * math.tau + ring * 0.3
            x, y = c + math.cos(a) * r, c + math.sin(a) * r
            d.arc([x - 26, y - 26, x + 26, y + 26], start=math.degrees(a) + 120, end=math.degrees(a) + 240, fill=255, width=5)
    d.ellipse([c - 250, c - 250, c + 250, c + 250], outline=255, width=8)
    img = img.filter(ImageFilter.GaussianBlur(1.2))
    a = np.asarray(img.resize((256, 256), Image.LANCZOS), np.float32) / 255
    x, y = grid(256)
    r = np.sqrt(x * x + y * y)
    fill = np.clip(1 - r, 0, 1) ** 0.5 * 0.25 * (r < 1)
    g = np.asarray(img.filter(ImageFilter.GaussianBlur(8)).resize((256, 256), Image.LANCZOS), np.float32) / 255
    save(white_alpha(np.clip(a + g * 0.5 + fill, 0, 1)), os.path.join(FX, 'scale_shield.png'))


def tail_strip():
    w, h = 128, 32
    out = np.zeros((h, w, 4), np.float32)
    for yy in range(h):
        for xx in range(w):
            off = (yy // 8) % 2 * 4
            lx = (xx + off) % 8 - 4
            ly = yy % 8 - 4
            v = 0.55 + 0.4 * (1 - min(1, (lx * lx + ly * ly) / 16))
            out[yy, xx] = [0.5 * v, 0.12 * v, 0.07 * v, 1]
    spine = np.exp(-(((np.arange(h) - h / 2) / 2.5) ** 2))[:, None]
    out[..., 0] = np.clip(out[..., 0] + spine * 0.6, 0, 1)
    out[..., 1] = np.clip(out[..., 1] + spine * 0.3, 0, 1)
    save(out, os.path.join(FX, 'dragon_tail.png'))


def dragon_icon(name, fn, base=(0.24, 0.07, 0.04), col=(1.0, 0.55, 0.2)):
    S = 128
    x, y = grid(S)
    r = np.sqrt(x * x + y * y)
    out = np.zeros((S, S, 4), np.float32)
    t = np.clip(r, 0, 1)
    n = value_noise(S, S, 4, 4, 99)
    for k in range(3):
        out[..., k] = (base[k] + base[k] * 1.6 * (1 - t) ** 2) * (0.8 + 0.4 * n)
    out[..., 3] = 1
    mask = Image.new('L', (S * 4, S * 4), 0)
    fn(ImageDraw.Draw(mask), S * 4)
    mask = mask.filter(ImageFilter.GaussianBlur(2)).resize((S, S), Image.LANCZOS)
    out = gt.paste_glyph(out, mask, col)
    edge = np.clip((np.maximum(np.abs(x), np.abs(y)) - 0.86) / 0.14, 0, 1)
    out[..., :3] *= (1 - edge[..., None] * 0.7)
    Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), 'RGBA').resize((64, 64), Image.LANCZOS).save(os.path.join(GUI, name))


def claw_marks(d, S, n=3, ang=-0.7, cx=0.5, cy=0.5, L=0.62, w=0.05):
    c = (S * cx, S * cy)
    for i in range(n):
        o = (i - (n - 1) / 2) * S * 0.14
        pts = []
        for k in range(16):
            t = k / 15
            px = c[0] + math.cos(ang) * (t - 0.5) * S * L - math.sin(ang) * o + math.sin(t * math.pi) * S * 0.05
            py = c[1] + math.sin(ang) * (t - 0.5) * S * L + math.cos(ang) * o
            pts.append((px, py))
        for k in range(len(pts) - 1):
            ww = int(S * w * math.sin((k + 0.5) / (len(pts) - 1) * math.pi) + 2)
            d.line([pts[k], pts[k + 1]], fill=255, width=ww)


def d_s1(d, S):
    claw_marks(d, S, 3, -0.8, 0.56, 0.5)
    for i in range(4):
        y = S * (0.3 + i * 0.13)
        d.line([(S * 0.08, y), (S * 0.25, y)], fill=170, width=int(S * 0.025))


def d_s2(d, S):
    c = S / 2
    for side in (-1, 1):
        pts = [(c, c * 1.05), (c + side * S * 0.42, c - S * 0.32), (c + side * S * 0.36, c - S * 0.05), (c + side * S * 0.4, c + S * 0.12), (c + side * S * 0.26, c + S * 0.1), (c + side * S * 0.24, c + S * 0.28)]
        d.polygon(pts, outline=255, width=int(S * 0.03))
        d.line([(c, c * 1.05), (c + side * S * 0.42, c - S * 0.32)], fill=255, width=int(S * 0.04))
    d.polygon([(c - S * 0.07, c + S * 0.42), (c + S * 0.07, c + S * 0.42), (c, c + S * 0.2)], fill=255)


def d_s3(d, S):
    claw_marks(d, S, 3, -0.75, 0.5, 0.5, 0.7)
    claw_marks(d, S, 3, 0.75, 0.5, 0.5, 0.7)


def d_s4(d, S):
    c = S / 2
    for ring in range(3):
        r = S * (0.12 + ring * 0.1)
        n = 6 + ring * 4
        for i in range(n):
            a = i / n * math.tau
            x, y = c + math.cos(a) * r, c + math.sin(a) * r
            d.arc([x - S * 0.06, y - S * 0.06, x + S * 0.06, y + S * 0.06], start=math.degrees(a) + 120, end=math.degrees(a) + 240, fill=255, width=int(S * 0.018))
    d.ellipse([c - S * 0.42, c - S * 0.42, c + S * 0.42, c + S * 0.42], outline=255, width=int(S * 0.035))


def d_ult(d, S):
    c = S / 2
    # 용 머리 실루엣 + 브레스
    head = [(S * 0.12, S * 0.36), (S * 0.42, S * 0.24), (S * 0.56, S * 0.3), (S * 0.62, S * 0.42), (S * 0.5, S * 0.5), (S * 0.6, S * 0.58), (S * 0.4, S * 0.6), (S * 0.14, S * 0.52)]
    d.polygon(head, fill=255)
    d.polygon([(S * 0.36, S * 0.26), (S * 0.3, S * 0.1), (S * 0.44, S * 0.24)], fill=255)
    d.polygon([(S * 0.24, S * 0.32), (S * 0.14, S * 0.16), (S * 0.3, S * 0.3)], fill=255)
    for i in range(5):
        a = -0.35 + i * 0.17
        d.line([(S * 0.62, S * 0.48), (S * 0.62 + math.cos(a) * S * 0.36, S * 0.48 + math.sin(a) * S * 0.36)], fill=200, width=int(S * 0.04))


def d_passive(d, S):
    c = S / 2
    d.ellipse([c - S * 0.12, c - S * 0.36, c + S * 0.12, c - S * 0.12], outline=255, width=int(S * 0.035))
    d.polygon([(c - S * 0.16, c - S * 0.08), (c + S * 0.16, c - S * 0.08), (c + S * 0.12, c + S * 0.3), (c - S * 0.12, c + S * 0.3)], outline=255, width=int(S * 0.035))
    for side in (-1, 1):
        d.polygon([(c + side * S * 0.12, c - S * 0.05), (c + side * S * 0.46, c - S * 0.3), (c + side * S * 0.4, c + S * 0.05), (c + side * S * 0.2, c + S * 0.12)], outline=255, width=int(S * 0.03))
    for i in range(5):
        x = c - S * 0.2 + i * S * 0.1
        d.line([(x, c + S * 0.44), (x + S * 0.03, c + S * 0.32)], fill=200, width=int(S * 0.025))


def card():
    W, H = 256, 160
    x, y = grid(W, H)
    out = np.zeros((H, W, 4), np.float32)
    n = value_noise(W, H, 5, 5, 98)
    r = np.sqrt((x * 1.4) ** 2 + (y * 1.0) ** 2)
    out[..., 0] = 0.12 + 0.6 * np.exp(-r * r * 2) + 0.08 * n
    out[..., 1] = 0.03 + 0.22 * np.exp(-r * r * 2.5)
    out[..., 2] = 0.02 + 0.05 * n
    out[..., 3] = 1
    img = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), 'RGBA')
    m = Image.new('L', (W * 2, H * 2), 0)
    d_ult(ImageDraw.Draw(m), H * 2)
    m = m.filter(ImageFilter.GaussianBlur(2)).resize((H, H), Image.LANCZOS)
    glow = Image.new('RGBA', (H, H), (255, 190, 90, 255))
    img.paste(glow, ((W - H) // 2, 0), m)
    img.save(os.path.join(GUI, 'card_dragon.png'))


def _periodic_noise(S, cells, seed):
    """이음매 없는 값 노이즈 (S x S, cells 간격)."""
    rng = np.random.RandomState(seed)
    g = rng.rand(cells, cells).astype(np.float32)
    x = np.arange(S) / S * cells
    x0 = np.floor(x).astype(int)
    t = x - x0
    t = t * t * (3 - 2 * t)
    x1 = (x0 + 1) % cells
    x0 %= cells
    a = g[x0][:, x0] * (1 - t)[None, :] + g[x0][:, x1] * t[None, :]
    b = g[x1][:, x0] * (1 - t)[None, :] + g[x1][:, x1] * t[None, :]
    return a * (1 - t)[:, None] + b * t[:, None]


def elder_textures():
    """고룡 (직접 만든 모델) 텍스처: 비늘 / 비늘 틈 빛 / 배 / 뿔 / 날개막."""
    S = 64
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    # 비늘: 16px 간격, 줄마다 반칸 어긋남, 아래로 둥근 비늘
    best = np.full((S, S), 9.0, np.float32)
    shade = np.zeros((S, S), np.float32)
    for row in range(-1, 6):
        for col in range(-1, 6):
            cx = col * 16 + (8 if row % 2 else 0)
            cy = row * 13
            dx = ((xx - cx + S / 2) % S) - S / 2
            dy = ((yy - cy + S / 2) % S) - S / 2
            d = np.sqrt((dx / 9.5) ** 2 + (np.maximum(dy, 0) / 11.0) ** 2 + (np.minimum(dy, 0) / 6.0) ** 2)
            closer = d < best
            best = np.where(closer, d, best)
            shade = np.where(closer, np.clip(1 - dy / 12.0, 0, 1), shade)
    n = _periodic_noise(S, 8, 31)
    gap = np.clip((best - 0.82) / 0.18, 0, 1)
    body = (1 - gap)
    out = np.zeros((S, S, 4), np.float32)
    base = np.array([0.40, 0.07, 0.05]); hi = np.array([0.72, 0.18, 0.09]); dark = np.array([0.08, 0.015, 0.01])
    for k in range(3):
        c = base[k] + (hi[k] - base[k]) * (shade * 0.7 + 0.3 * n) * (1 - best * 0.5)
        out[..., k] = c * body + dark[k] * gap
    out[..., 3] = 1
    save(out, os.path.join(FX, 'elder_scales.png'))
    # 비늘 틈에서 새어 나오는 용암빛 (가산 합성용, 미리 곱한 알파)
    n2 = _periodic_noise(S, 4, 37)
    gl = np.clip((best - 0.7) / 0.22, 0, 1) * np.clip(n2 * 1.5 - 0.15, 0, 1)
    glow = np.zeros((S, S, 4), np.float32)
    for k, c in enumerate((1.0, 0.42, 0.1)):
        glow[..., k] = c * gl
    glow[..., 3] = gl
    save(glow, os.path.join(FX, 'elder_glow.png'))
    # 배: 가로 띠 판
    band = (yy % 16) / 16
    edge = np.clip(1 - np.abs(band - 0.5) * 2.2, 0, 1) ** 0.6
    n3 = _periodic_noise(S, 8, 41)
    belly = np.zeros((S, S, 4), np.float32)
    for k, (a, b) in enumerate(((0.30, 0.68), (0.10, 0.32), (0.05, 0.12))):
        belly[..., k] = a + (b - a) * edge * (0.85 + 0.3 * n3)
    belly[..., 3] = 1
    save(belly, os.path.join(FX, 'elder_belly.png'))
    # 뿔/뼈: 상아색, 고리 무늬 (세로로 반복)
    H2 = 64
    yy2, xx2 = np.mgrid[0:H2, 0:32].astype(np.float32)
    ring = 0.5 + 0.5 * np.cos(yy2 / H2 * np.pi * 2 * 6)
    n4 = _periodic_noise(64, 8, 43)[:, :32]
    horn = np.zeros((H2, 32, 4), np.float32)
    for k, (a, b) in enumerate(((0.55, 0.92), (0.45, 0.84), (0.32, 0.68))):
        horn[..., k] = a + (b - a) * (0.65 + 0.35 * ring) * (0.85 + 0.25 * n4)
    horn[..., 3] = 1
    save(horn, os.path.join(FX, 'elder_horn.png'))
    # 날개막: 반투명 가죽 + 핏줄
    M = 128
    n5 = _periodic_noise(M, 8, 47) * 0.6 + _periodic_noise(M, 32, 48) * 0.4
    veins = Image.new('L', (M, M), 0)
    vd = ImageDraw.Draw(veins)
    rr = random.Random(9)
    for i in range(26):
        x, y = rr.uniform(0, M), rr.uniform(0, M)
        ang = rr.uniform(0, math.tau)
        pts = [(x, y)]
        for k in range(10):
            ang += rr.uniform(-0.5, 0.5)
            x += math.cos(ang) * 7
            y += math.sin(ang) * 7
            pts.append((x, y))
        for ox in (-M, 0, M):
            for oy in (-M, 0, M):
                vd.line([(px + ox, py + oy) for px, py in pts], fill=200, width=2)
    v = np.asarray(veins.filter(ImageFilter.GaussianBlur(0.8)), np.float32) / 255
    mem = np.zeros((M, M, 4), np.float32)
    for k, (a, b) in enumerate(((0.30, 0.55), (0.05, 0.12), (0.03, 0.06))):
        mem[..., k] = a + (b - a) * n5 + v * (0.35 if k == 0 else 0.12)
    mem[..., 3] = 0.88 + 0.1 * n5
    save(mem, os.path.join(FX, 'elder_membrane.png'))
    mg = np.zeros((M, M, 4), np.float32)
    for k, c in enumerate((1.0, 0.4, 0.1)):
        mg[..., k] = c * v * 0.8
    mg[..., 3] = v * 0.8
    save(mg, os.path.join(FX, 'elder_membrane_glow.png'))


if __name__ == '__main__':
    wing(); skin_layout_overlay(); rune(); flame(); fire_strip(); scale_shield(); tail_strip(); elder_textures()
    # 스킬 아이콘/카드는 스킬바 그림에서 잘라 씀 (tools/gen_dragon_hud.py + tools/gen_hud.py dragon)
    print('done')
