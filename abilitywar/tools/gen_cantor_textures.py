#!/usr/bin/env python3
"""성가사(버퍼) 텍스처: 빛의 깃털 날개, 성가집 페이지, 음표, 찬가 마법진, 종, 성역 무늬, 상태 효과 아이콘, Q 대시 아이콘."""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures')
FX = os.path.join(ASSETS, 'fx')
ICONS = os.path.join(ASSETS, 'mob_effect')
GUI = os.path.join(ASSETS, 'gui')
SERIF = '/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf'
SANS = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
random.seed(21)


def save(a, path):
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'RGBA').save(path)


def to_arr(img):
    return np.asarray(img, np.float32) / 255


def wing():
    """빛의 깃털 날개 (뿌리 = 왼쪽 아래, 끝 = 오른쪽 위). 깃털을 겹겹이."""
    S = 512
    m = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(m)
    root = np.array([30.0, S - 50.0])
    tip = np.array([S - 20.0, 40.0])
    r = random.Random(3)
    # 깃털 3줄: 칼깃(가장 길고 끝으로 갈수록 날개 끝 쪽을 향함) → 둘째깃 → 덮깃(짧음)
    arm = tip - root
    arm_u = arm / np.linalg.norm(arm)
    perp = np.array([-arm_u[1], arm_u[0]])
    if perp[1] < 0:
        perp = -perp
    rows = [(13, 260, 34, 1.0), (12, 170, 30, 0.88), (10, 95, 26, 0.75)]
    feathers = []
    for (n, flen, fw, reach) in rows:
        for i in range(n):
            t = (i + 0.5) / n
            base = root + arm * (0.05 + t * 0.95 * reach)
            # 바깥쪽(아래)을 향하다 날개 끝 근처에서는 날개 끝 방향으로 눕힘
            k = t ** 1.6
            dv = perp * (1 - k) + arm_u * k * 1.2
            dv = dv / np.linalg.norm(dv)
            ang_j = math.radians(r.uniform(-3, 3))
            dv = np.array([dv[0] * math.cos(ang_j) - dv[1] * math.sin(ang_j), dv[0] * math.sin(ang_j) + dv[1] * math.cos(ang_j)])
            L = flen * (0.45 + 0.75 * t) * r.uniform(0.92, 1.06)
            feathers.append((base, dv, L, fw))
    for (b, dv, L, w) in feathers:
        nv = np.array([-dv[1], dv[0]])
        pts = []
        for k in range(16):
            t = k / 15
            wid = w * math.sin(math.pi * min(1, t * 1.15)) ** 0.7 * (1 - 0.3 * t)
            c = b + dv * L * t
            pts.append(tuple(c + nv * wid))
        for k in range(15, -1, -1):
            t = k / 15
            wid = w * math.sin(math.pi * min(1, t * 1.15)) ** 0.7 * (1 - 0.3 * t)
            c = b + dv * L * t
            pts.append(tuple(c - nv * wid * 0.6))
        d.polygon(pts, fill=255)
    # 날개 뼈대 (윗변)
    d.line([tuple(root), tuple(root + (tip - root) * 0.5 + np.array([0, -40])), tuple(tip)], fill=255, width=26)
    mm = to_arr(m.filter(ImageFilter.GaussianBlur(1.0)))
    x, y = np.meshgrid(np.arange(S), np.arange(S))
    dist = np.clip(np.sqrt((x - root[0]) ** 2 + (y - root[1]) ** 2) / (S * 1.1), 0, 1)
    edges = to_arr(m.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.GaussianBlur(1.6)))
    out = np.zeros((S, S, 4), np.float32)
    out[..., 0] = 1.0
    out[..., 1] = 0.93 - 0.15 * dist
    out[..., 2] = 0.72 - 0.35 * dist
    out[..., :3] *= (0.75 + 0.25 * (1 - edges))[..., None]
    out[..., 3] = mm * (0.8 - 0.35 * dist)
    out[..., :3] *= (out[..., 3:4] > 0.004)
    save(out, os.path.join(FX, 'cantor_wing.png'))
    # 가산 빛 (미리 곱한 알파)
    gl = np.clip(edges * 0.9 + mm * 0.25 * (1 - dist), 0, 1)
    g = np.zeros((S, S, 4), np.float32)
    for k, c in enumerate((1.0, 0.85, 0.45)):
        g[..., k] = c * gl
    g[..., 3] = gl
    save(g, os.path.join(FX, 'cantor_wing_glow.png'))


def book():
    """성가집 펼친 두 페이지 (왼쪽 절반 / 오른쪽 절반): 양피지 + 금빛 오선 + 음표."""
    W, H = 256, 128
    img = Image.new('RGBA', (W, H), (238, 226, 196, 255))
    d = ImageDraw.Draw(img)
    r = random.Random(5)
    for px in (0, W // 2):
        d.rectangle([px + 4, 4, px + W // 2 - 5, H - 5], outline=(176, 128, 40, 255), width=2)
        for row in range(4):
            y0 = 18 + row * 26
            for k in range(5):
                d.line([(px + 12, y0 + k * 4), (px + W // 2 - 12, y0 + k * 4)], fill=(150, 110, 50, 255), width=1)
            for _ in range(5):
                nx = px + r.uniform(18, W // 2 - 20)
                ny = y0 + r.choice([0, 2, 4, 6, 8, 10, 12, 14, 16])
                d.ellipse([nx - 3, ny - 2, nx + 3, ny + 2], fill=(60, 36, 16, 255))
                d.line([(nx + 3, ny), (nx + 3, ny - 12)], fill=(60, 36, 16, 255), width=1)
    # 가운데 접힌 그림자
    a = to_arr(img)
    xx = np.arange(W)[None, :]
    shade = 1 - 0.35 * np.exp(-((xx - W / 2) / 10.0) ** 2)
    a[..., :3] *= shade[..., None]
    save(a, os.path.join(FX, 'cantor_book.png'))


def notes():
    """음표 두 가지 (♪ / ♫) 를 나란히: 가산 빛용 (미리 곱한 알파)."""
    S = 128
    img = Image.new('L', (S * 2, S), 0)
    d = ImageDraw.Draw(img)
    # ♪
    cx, cy = 52, 92
    d.ellipse([cx - 22, cy - 15, cx + 22, cy + 15], fill=255)
    d.rectangle([cx + 14, 18, cx + 22, cy], fill=255)
    d.polygon([(cx + 22, 18), (cx + 50, 40), (cx + 46, 56), (cx + 22, 38)], fill=255)
    # ♫
    ox = S
    for cx2 in (ox + 34, ox + 92):
        d.ellipse([cx2 - 18, 82, cx2 + 18, 106], fill=255)
        d.rectangle([cx2 + 10, 22, cx2 + 18, 94], fill=255)
    d.polygon([(ox + 44, 22), (ox + 110, 12), (ox + 110, 30), (ox + 44, 40)], fill=255)
    core = to_arr(img.filter(ImageFilter.GaussianBlur(1.2)))
    glow = to_arr(img.filter(ImageFilter.GaussianBlur(9)))
    v = np.clip(core + glow * 0.6, 0, 1)
    out = np.zeros((S, S * 2, 4), np.float32)
    for k in range(3):
        out[..., k] = v
    out[..., 3] = v
    save(out, os.path.join(FX, 'cantor_note.png'))


def circle():
    """찬가 마법진: 겹 원 + 오선 고리 + 음표 + 룬 (흰색, 가산용)."""
    S = 512
    img = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(img)
    c = S / 2
    for rr, w in ((248, 6), (232, 3), (176, 4), (120, 3), (60, 3)):
        d.ellipse([c - rr, c - rr, c + rr, c + rr], outline=255, width=w)
    # 오선 고리
    for k in range(5):
        rr = 190 + k * 8
        d.ellipse([c - rr, c - rr, c + rr, c + rr], outline=150, width=1)
    r = random.Random(9)
    for i in range(18):
        a = i / 18 * math.tau
        rr = 190 + r.choice([0, 8, 16, 24, 32])
        x, y = c + math.cos(a) * rr, c + math.sin(a) * rr
        d.ellipse([x - 7, y - 5, x + 7, y + 5], fill=255)
        tx, ty = x + math.cos(a + math.pi / 2) * 0, y
        d.line([(x + 6, y), (x + 6, y - 22)], fill=255, width=3)
    # 별 모양 (8각)
    pts = []
    for i in range(16):
        a = i / 16 * math.tau
        rr = 170 if i % 2 == 0 else 128
        pts.append((c + math.cos(a) * rr, c + math.sin(a) * rr))
    d.line(pts + [pts[0]], fill=255, width=3)
    # 안쪽 룬
    for i in range(12):
        a = i / 12 * math.tau
        x, y = c + math.cos(a) * 90, c + math.sin(a) * 90
        d.line([(x - 8, y - 8), (x + 8, y + 8)], fill=255, width=3)
        d.line([(x + 8, y - 8), (x, y)], fill=255, width=3)
    core = to_arr(img.filter(ImageFilter.GaussianBlur(0.8)))
    glow = to_arr(img.filter(ImageFilter.GaussianBlur(6)))
    v = np.clip(core + glow * 0.5, 0, 1)
    out = np.zeros((S, S, 4), np.float32)
    for k in range(3):
        out[..., k] = v
    out[..., 3] = v
    save(out, os.path.join(FX, 'cantor_circle.png'))


def bell():
    """종 표면 (u = 둘레, v = 위→아래): 금빛 금속 + 띠 무늬."""
    W, H = 64, 128
    yy = np.linspace(0, 1, H)[:, None] * np.ones((1, W))
    xx = np.linspace(0, 1, W)[None, :] * np.ones((H, 1))
    base = 0.75 + 0.25 * np.cos(xx * math.tau * 2)
    out = np.zeros((H, W, 4), np.float32)
    out[..., 0] = 1.0 * base
    out[..., 1] = (0.78 - 0.15 * yy) * base
    out[..., 2] = (0.32 - 0.12 * yy) * base
    for y0 in (0.18, 0.22, 0.78, 0.84, 0.9):
        band = np.exp(-((yy - y0) / 0.008) ** 2)
        out[..., :3] = out[..., :3] * (1 - 0.5 * band[..., None])
    # 띠 사이 음표 무늬
    img = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(img)
    for i in range(4):
        x = 8 + i * 16
        d.ellipse([x - 3, 92, x + 3, 97], fill=255)
        d.line([(x + 3, 94), (x + 3, 84)], fill=255, width=1)
    m = to_arr(img)
    out[..., :3] = out[..., :3] * (1 - 0.45 * m[..., None])
    out[..., 3] = 1
    save(out, os.path.join(FX, 'cantor_bell.png'))


def dome():
    """성역의 돔: 육각 무늬 + 위쪽으로 갈수록 옅어짐 (흰색, 가산/반투명 겸용)."""
    S = 256
    img = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(img)
    R = 16
    h = R * math.sqrt(3)
    for row in range(-1, int(S / h) + 2):
        for col in range(-1, int(S / (R * 3)) + 2):
            cx = col * R * 3 + (row % 2) * R * 1.5
            cy = row * h / 2
            pts = [(cx + R * math.cos(a), cy + R * math.sin(a)) for a in [k * math.pi / 3 for k in range(6)]]
            d.polygon(pts, outline=255)
    core = to_arr(img.filter(ImageFilter.GaussianBlur(0.6)))
    glow = to_arr(img.filter(ImageFilter.GaussianBlur(3)))
    v = np.clip(core * 0.9 + glow * 0.5, 0, 1)
    out = np.zeros((S, S, 4), np.float32)
    for k in range(3):
        out[..., k] = v
    out[..., 3] = v
    save(out, os.path.join(FX, 'cantor_dome.png'))


def icons():
    """상태 효과 아이콘 18x18 (8배로 그려서 줄임)."""
    S = 18 * 8

    def new():
        im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        return im, ImageDraw.Draw(im)

    def fin(im, name):
        im.resize((18, 18), Image.LANCZOS).save(os.path.join(ICONS, name + '.png'))

    # 용맹: 위로 솟은 검 + 불꽃색
    im, d = new()
    d.polygon([(72, 8), (88, 30), (82, 100), (62, 100), (56, 30)], fill=(30, 10, 0, 255))
    d.polygon([(72, 16), (82, 32), (78, 96), (66, 96), (62, 32)], fill=(255, 140, 70, 255))
    d.rectangle([40, 100, 104, 112], fill=(255, 210, 120, 255), outline=(30, 10, 0, 255), width=4)
    d.rectangle([64, 112, 80, 136], fill=(150, 80, 30, 255), outline=(30, 10, 0, 255), width=4)
    fin(im, 'valor')
    # 수호: 방패
    im, d = new()
    d.polygon([(72, 8), (128, 26), (122, 86), (72, 136), (22, 86), (16, 26)], fill=(10, 30, 60, 255))
    d.polygon([(72, 20), (116, 34), (110, 82), (72, 122), (34, 82), (28, 34)], fill=(110, 200, 255, 255))
    d.line([(72, 30), (72, 112)], fill=(230, 250, 255, 255), width=8)
    d.line([(40, 58), (104, 58)], fill=(230, 250, 255, 255), width=8)
    fin(im, 'aegis')
    # 신속: 날개
    im, d = new()
    for k in range(4):
        y = 30 + k * 22
        d.polygon([(20, y + 10), (120 - k * 14, y - 8), (124 - k * 14, y + 4), (24, y + 26)], fill=(10, 40, 10, 255))
        d.polygon([(28, y + 12), (116 - k * 14, y - 2), (116 - k * 14, y + 4), (30, y + 20)], fill=(130, 255, 150, 255))
    fin(im, 'haste')
    # 은총: 빛나는 십자
    im, d = new()
    d.ellipse([20, 20, 124, 124], fill=(70, 50, 10, 255))
    d.rectangle([60, 28, 84, 116], fill=(255, 240, 160, 255))
    d.rectangle([32, 58, 112, 82], fill=(255, 240, 160, 255))
    fin(im, 'grace')
    # 침묵: 음표에 빗금
    im, d = new()
    d.ellipse([40, 84, 84, 114], fill=(220, 220, 255, 255))
    d.rectangle([74, 22, 86, 100], fill=(220, 220, 255, 255))
    d.polygon([(86, 22), (116, 40), (112, 56), (86, 40)], fill=(220, 220, 255, 255))
    d.line([(20, 20), (124, 124)], fill=(30, 10, 40, 255), width=22)
    d.line([(20, 20), (124, 124)], fill=(255, 80, 90, 255), width=12)
    fin(im, 'silence')
    # 불협화음: 깨진 음표
    im, d = new()
    d.ellipse([28, 86, 72, 116], fill=(160, 140, 100, 255))
    d.rectangle([62, 24, 74, 102], fill=(160, 140, 100, 255))
    d.line([(78, 30), (96, 50), (84, 62), (110, 90)], fill=(255, 220, 120, 255), width=10)
    d.line([(96, 20), (112, 40)], fill=(255, 220, 120, 255), width=8)
    fin(im, 'dissonance')


def dash_icon():
    a = to_arr(Image.open(os.path.join(GUI, 'dash.png')).convert('RGBA'))
    lum = a[..., :3].max(-1)
    sat = lum - a[..., :3].min(-1)
    out = a.copy()
    out[..., 0] = lum
    out[..., 1] = lum - sat * 0.25
    out[..., 2] = lum - sat * 0.85
    save(out, os.path.join(GUI, 'dash_cantor.png'))


if __name__ == '__main__':
    wing(); book(); notes(); circle(); bell(); dome(); icons(); dash_icon()
    print('done')
