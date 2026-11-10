#!/usr/bin/env python3
"""천사 텍스처: 하얀 깃털 날개, 깃털 하나, 천상의 마법진, 빛줄기, 열정(공속) 아이콘, Q 대시 아이콘."""
import math, os, random, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_cantor_textures import FX, GUI, ICONS, save, to_arr  # noqa: E402


def wing():
    """하얀 깃털 날개 (뿌리 = 왼쪽 아래, 끝 = 오른쪽 위). 깃털 4줄을 뒤(칼깃)부터 앞(덮깃)으로 겹쳐 그림."""
    S = 512
    M = 6
    root = np.array([30.0, S - 52.0])
    tip = np.array([S - 14.0, 26.0])
    ctrl = root + (tip - root) * 0.42 + np.array([-30.0, -70.0])
    r = random.Random(7)

    def bone(t):
        return (1 - t) ** 2 * root + 2 * (1 - t) * t * ctrl + t * t * tip

    def bone_dir(t):
        dv = 2 * (1 - t) * (ctrl - root) + 2 * t * (tip - ctrl)
        return dv / np.linalg.norm(dv)

    def inside_len(b, dv, L):
        # 깃털 끝이 그림 밖으로 나가지 않게 길이를 줄임
        for _ in range(40):
            e = b + dv * L
            if M <= e[0] <= S - M and M <= e[1] <= S - M:
                return L
            L *= 0.95
        return L

    rows = [(16, 300, 26, 1.0), (15, 200, 25, 0.93), (13, 120, 22, 0.85), (11, 64, 18, 0.75)]
    alpha = Image.new('L', (S, S), 0)
    shade = Image.new('L', (S, S), 0)
    da, ds = ImageDraw.Draw(alpha), ImageDraw.Draw(shade)
    for ri, (n, flen, fw, reach) in enumerate(rows):
        for i in range(n):
            t = (i + 0.5) / n * reach
            b = bone(0.03 + t * 0.97)
            au = bone_dir(0.03 + t * 0.97)
            perp = np.array([-au[1], au[0]])
            if perp[1] < 0:
                perp = -perp
            k = (t / reach) ** 1.5
            dv = perp * (1 - k) + au * k * 1.3
            dv = dv / np.linalg.norm(dv)
            j = math.radians(r.uniform(-2.5, 2.5))
            dv = np.array([dv[0] * math.cos(j) - dv[1] * math.sin(j), dv[0] * math.sin(j) + dv[1] * math.cos(j)])
            L = inside_len(b, dv, flen * (0.5 + 0.65 * t / reach) * r.uniform(0.94, 1.05))
            nv = np.array([-dv[1], dv[0]])
            up, lo = [], []
            for q in range(20):
                u = q / 19
                wid = fw * math.sin(math.pi * min(1, u * 1.08)) ** 0.6 * (1 - 0.2 * u)
                up.append(tuple(b + dv * L * u + nv * wid))
                lo.append(tuple(b + dv * L * u - nv * wid * 0.6))
            poly = up + lo[::-1]
            da.polygon(poly, fill=255)
            # 깃털 면: 밝게, 가장자리와 깃대는 살짝 어둡게 (다음 깃털이 덮음)
            ds.polygon(poly, fill=255 - ri * 5, outline=226)
            ds.line([tuple(b + dv * L * 0.03), tuple(b + dv * L * 0.9)], fill=236, width=3)
    # 날개 뼈대: 뿌리에서 끝으로 가늘어지는 곡선
    for q in range(60):
        t0, t1 = q / 60, (q + 1) / 60
        w = int(30 * (1 - t0) + 7 * t0)
        p0, p1 = bone(t0), bone(t1)
        da.line([tuple(p0), tuple(p1)], fill=255, width=w)
        ds.line([tuple(p0), tuple(p1)], fill=246, width=w)
        da.ellipse([p0[0] - w / 2, p0[1] - w / 2, p0[0] + w / 2, p0[1] + w / 2], fill=255)
        ds.ellipse([p0[0] - w / 2, p0[1] - w / 2, p0[0] + w / 2, p0[1] + w / 2], fill=246)
    a = to_arr(alpha.filter(ImageFilter.GaussianBlur(0.8)))
    sh = to_arr(shade.filter(ImageFilter.GaussianBlur(1.8)))
    x, y = np.meshgrid(np.arange(S), np.arange(S))
    dist = np.clip(np.sqrt((x - root[0]) ** 2 + (y - root[1]) ** 2) / (S * 1.15), 0, 1)
    out = np.zeros((S, S, 4), np.float32)
    # 흰색 → 깃 끝으로 갈수록 아주 옅은 하늘빛
    out[..., 0] = (1.0 - 0.12 * dist) * sh
    out[..., 1] = (1.0 - 0.05 * dist) * sh
    out[..., 2] = 0.2 + 0.8 * sh
    out[..., 3] = a * (0.98 - 0.1 * dist)
    out[..., :3] *= (out[..., 3:4] > 0.004)
    # 게임 안에서는 작게 보이므로 미리 줄여서 계단 무늬를 없앰
    Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), 'RGBA').resize((256, 256), Image.LANCZOS).save(os.path.join(FX, 'angel_wing.png'))
    # 가장자리 빛 (미리 곱한 알파, 눈 레이어용)
    edges = to_arr(alpha.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.GaussianBlur(2.2)))
    gl = np.clip(edges * 1.4 + a * 0.12 * (1 - dist), 0, 1)
    g = np.zeros((S, S, 4), np.float32)
    for k, c in enumerate((0.85, 0.94, 1.0)):
        g[..., k] = c * gl
    g[..., 3] = gl
    Image.fromarray((np.clip(g, 0, 1) * 255).astype(np.uint8), 'RGBA').resize((256, 256), Image.LANCZOS).save(os.path.join(FX, 'angel_wing_glow.png'))


def feather():
    """깃털 하나 (세로, 아래 = 깃대 뿌리). 반투명 흰색."""
    W, H = 64, 128
    img = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(img)
    pts = []
    for i in range(24):
        t = i / 23
        y = 122 - t * 116
        wd = 22 * math.sin(math.pi * min(1, t * 1.05)) ** 0.7 * (1 - 0.2 * t)
        pts.append((32 + wd, y))
    d.polygon(pts + [(64 - px, py) for (px, py) in reversed(pts)], fill=255)
    # 깃가지 결
    r = random.Random(4)
    vein = Image.new('L', (W, H), 0)
    dv = ImageDraw.Draw(vein)
    for i in range(16):
        y = 110 - i * 6.5
        for s in (-1, 1):
            dv.line([(32, y), (32 + s * 20, y - 10 - r.uniform(0, 3))], fill=90, width=1)
    dv.line([(32, 126), (32, 8)], fill=160, width=2)
    a = to_arr(img.filter(ImageFilter.GaussianBlur(1.0)))
    v = to_arr(vein.filter(ImageFilter.GaussianBlur(0.6)))
    out = np.zeros((H, W, 4), np.float32)
    out[..., 0] = 1 - v * 0.35
    out[..., 1] = 1 - v * 0.3
    out[..., 2] = 1 - v * 0.15
    out[..., 3] = a * 0.95
    out[..., :3] *= (out[..., 3:4] > 0.004)
    save(out, os.path.join(FX, 'angel_feather.png'))


def circle():
    """천상의 마법진: 겹 원, 12갈래 빛살, 육망성 두 겹, 날개 문양, 작은 십자 글자 고리 (흰색, 가산용)."""
    S = 512
    img = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(img)
    c = S / 2
    for rr, w in ((250, 5), (238, 2), (204, 3), (196, 1), (150, 3), (96, 2), (40, 3)):
        d.ellipse([c - rr, c - rr, c + rr, c + rr], outline=255, width=w)
    # 글자 고리: 작은 십자와 점을 번갈아
    for i in range(36):
        a = i / 36 * math.tau
        x, y = c + math.cos(a) * 221, c + math.sin(a) * 221
        if i % 3 == 0:
            d.line([(x - 7, y), (x + 7, y)], fill=255, width=3)
            d.line([(x, y - 9), (x, y + 9)], fill=255, width=3)
        elif i % 3 == 1:
            d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=255)
        else:
            ca, sa = math.cos(a), math.sin(a)
            d.line([(x - ca * 7, y - sa * 7), (x + ca * 7, y + sa * 7)], fill=255, width=2)
    # 두 겹 육망성
    for rot in (0, math.pi / 6):
        for tri in range(2):
            pts = []
            for k in range(3):
                a = rot + tri * math.pi + k * math.tau / 3 - math.pi / 2
                pts.append((c + math.cos(a) * 196, c + math.sin(a) * 196))
            d.line(pts + [pts[0]], fill=255 if rot == 0 else 150, width=3 if rot == 0 else 2)
    # 12갈래 빛살 (안쪽 원 → 중간 원)
    for i in range(12):
        a = i / 12 * math.tau
        d.line([(c + math.cos(a) * 44, c + math.sin(a) * 44), (c + math.cos(a) * 146, c + math.sin(a) * 146)], fill=200, width=2)
    # 날개 문양 (중간 고리 위 6곳): 작은 V 깃털 세 줄
    for i in range(6):
        a = i / 6 * math.tau + math.pi / 6
        bx, by = c + math.cos(a) * 122, c + math.sin(a) * 122
        ca, sa = math.cos(a), math.sin(a)
        tx, ty = -sa, ca
        for k in range(3):
            L = 22 - k * 5
            o = (k - 1) * 7
            p0 = (bx + ca * o, by + sa * o)
            d.line([p0, (p0[0] + tx * L + ca * 6, p0[1] + ty * L + sa * 6)], fill=255, width=2)
            d.line([p0, (p0[0] - tx * L + ca * 6, p0[1] - ty * L + sa * 6)], fill=255, width=2)
    # 가운데 태양
    d.ellipse([c - 18, c - 18, c + 18, c + 18], fill=255)
    core = to_arr(img.filter(ImageFilter.GaussianBlur(0.8)))
    glow = to_arr(img.filter(ImageFilter.GaussianBlur(7)))
    v = np.clip(core + glow * 0.55, 0, 1)
    out = np.zeros((S, S, 4), np.float32)
    for k in range(3):
        out[..., k] = v
    out[..., 3] = v
    save(out, os.path.join(FX, 'angel_circle.png'))


def beam():
    """빛줄기 (u = 가로, v = 세로로 반복): 가운데 심 + 흐르는 결."""
    W, H = 64, 256
    xx = np.linspace(-1, 1, W)[None, :] * np.ones((H, 1))
    yy = np.linspace(0, 1, H)[:, None] * np.ones((1, W))
    core = np.exp(-(xx / 0.18) ** 2)
    halo = np.exp(-(xx / 0.55) ** 2) * 0.5
    r = np.random.RandomState(5)
    streaks = np.zeros((H, W), np.float32)
    for _ in range(14):
        x0 = r.uniform(-0.6, 0.6)
        ph = r.uniform(0, 1)
        f = r.choice([1, 2, 3])
        streaks += np.exp(-((xx - x0) / 0.05) ** 2) * (0.5 + 0.5 * np.sin((yy * f + ph) * math.tau)) * 0.35
    v = np.clip(core + halo + streaks * np.exp(-(xx / 0.7) ** 2), 0, 1)
    out = np.zeros((H, W, 4), np.float32)
    for k in range(3):
        out[..., k] = v
    out[..., 3] = v
    save(out, os.path.join(FX, 'angel_beam.png'))


def icon():
    """열정 (공격 속도): 금빛 겹 화살표 + 빛."""
    S = 18 * 8
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([10, 10, 134, 134], fill=(60, 36, 6, 255))
    for k in range(2):
        x = 30 + k * 40
        d.polygon([(x, 30), (x + 44, 72), (x, 114), (x + 18, 114), (x + 62, 72), (x + 18, 30)], fill=(255, 214, 110, 255))
    d.line([(24, 72), (122, 72)], fill=(255, 250, 230, 255), width=6)
    im.resize((18, 18), Image.LANCZOS).save(os.path.join(ICONS, 'fervor.png'))


def dash_icon():
    a = to_arr(Image.open(os.path.join(GUI, 'dash.png')).convert('RGBA'))
    lum = a[..., :3].max(-1)
    sat = lum - a[..., :3].min(-1)
    out = a.copy()
    out[..., 0] = lum - sat * 0.55
    out[..., 1] = lum - sat * 0.2
    out[..., 2] = lum
    save(out, os.path.join(GUI, 'dash_angel.png'))


if __name__ == '__main__':
    wing(); feather(); circle(); beam(); icon(); dash_icon()
    print('done')
