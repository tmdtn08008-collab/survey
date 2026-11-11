#!/usr/bin/env python3
"""
1차 직업 (전사·마법사·탱커·힐러) + 수호기사 그림:
  textures/gui/<직업>_<슬롯>.png (64x64 스킬 아이콘), textures/gui/card_<직업>.png (256x160 선택 카드),
  textures/entity/job_master.png (전직 교관 64x64 피부).
사용: python3 tools/gen_class_art.py
"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar')
GUI = os.path.join(ASSETS, 'textures', 'gui')
ENT = os.path.join(ASSETS, 'textures', 'entity')
S = 256  # 아이콘을 크게 그린 뒤 줄임


def hexc(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], np.float32) / 255


def blur(a, r):
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
    return np.asarray(im.filter(ImageFilter.GaussianBlur(r)), np.float32) / 255


def mask(draw_fn, W=S, H=S):
    m = Image.new('L', (W, H), 0)
    draw_fn(ImageDraw.Draw(m))
    return np.asarray(m, np.float32) / 255


def radial(W, H, cx, cy, r):
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    return np.clip(1 - np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r, 0, 1)


# ============================================================ 모양
def rot(pts, cx, cy, ang):
    c, s = math.cos(ang), math.sin(ang)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]


def sword(d, cx, cy, ang, L=170, w=18, fill=255):
    tip = cy - L * 0.55
    base = cy + L * 0.22
    blade = [(cx, tip), (cx + w / 2, tip + w * 1.2), (cx + w / 2, base), (cx - w / 2, base), (cx - w / 2, tip + w * 1.2)]
    guard = [(cx - w * 2.2, base), (cx + w * 2.2, base), (cx + w * 2.2, base + w * 0.7), (cx - w * 2.2, base + w * 0.7)]
    grip = [(cx - w * 0.35, base + w * 0.7), (cx + w * 0.35, base + w * 0.7), (cx + w * 0.35, base + L * 0.28), (cx - w * 0.35, base + L * 0.28)]
    for p in (blade, guard, grip):
        d.polygon(rot(p, cx, cy, ang), fill=fill)
    pr = rot([(cx, base + L * 0.3)], cx, cy, ang)[0]
    d.ellipse([pr[0] - w * 0.6, pr[1] - w * 0.6, pr[0] + w * 0.6, pr[1] + w * 0.6], fill=fill)


def shield_pts(cx, cy, w, h, n=24):
    pts = []
    top = cy - h * 0.5
    for i in range(n + 1):
        v = i / n
        half = 1.0 if v < 0.45 else math.sqrt(max(0, 1 - (v - 0.45) / 0.55))
        pts.append((cx + half * w, top + v * h))
    for i in range(n, -1, -1):
        v = i / n
        half = 1.0 if v < 0.45 else math.sqrt(max(0, 1 - (v - 0.45) / 0.55))
        pts.append((cx - half * w, top + v * h))
    return pts


def shield(d, cx, cy, w, h, fill=255):
    d.polygon(shield_pts(cx, cy, w, h), fill=fill)


def cross(d, cx, cy, s, t, fill=255):
    d.rectangle([cx - t, cy - s, cx + t, cy + s], fill=fill)
    d.rectangle([cx - s, cy - t, cx + s, cy + t], fill=fill)


def star(d, cx, cy, r1, r2, n=4, ang=0, fill=255):
    pts = []
    for i in range(n * 2):
        r = r1 if i % 2 == 0 else r2
        a = ang + i * math.pi / n
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    d.polygon(pts, fill=fill)


def heart(d, cx, cy, s, fill=255):
    r = s * 0.3
    d.ellipse([cx - s * 0.55, cy - s * 0.45, cx - s * 0.55 + 2 * r * 1.05, cy - s * 0.45 + 2 * r], fill=fill)
    d.ellipse([cx + s * 0.55 - 2 * r * 1.05, cy - s * 0.45, cx + s * 0.55, cy - s * 0.45 + 2 * r], fill=fill)
    d.polygon([(cx - s * 0.55, cy - s * 0.12), (cx + s * 0.55, cy - s * 0.12), (cx, cy + s * 0.55)], fill=fill)


def flame(d, cx, cy, s, fill=255):
    """끝이 위로 뾰족한 불꽃 (물방울 곡선) + 양옆의 작은 불혀."""
    def drop(x0, y0, sc, lean):
        pts = []
        for i in range(61):
            t = i / 60 * 2 * math.pi
            x = math.sin(t) * (math.sin(t / 2) ** 1.6) * sc * 0.62
            y = -math.cos(t) * sc * 0.9
            x += lean * (1 - (y / (sc * 0.9) + 1) / 2) * sc * 0.25
            pts.append((x0 + x, y0 + y))
        d.polygon(pts, fill=fill)
    drop(cx, cy + s * 0.1, s, 0.4)
    drop(cx - s * 0.32, cy + s * 0.35, s * 0.45, -0.8)
    drop(cx + s * 0.34, cy + s * 0.3, s * 0.5, 0.9)


def snowflake(d, cx, cy, r, t, fill=255):
    for k in range(6):
        a = k * math.pi / 3
        x1, y1 = cx + math.cos(a) * r, cy + math.sin(a) * r
        d.line([(cx, cy), (x1, y1)], fill=fill, width=t)
        for f in (0.45, 0.72):
            bx, by = cx + math.cos(a) * r * f, cy + math.sin(a) * r * f
            for s in (-1, 1):
                aa = a + s * 0.75
                d.line([(bx, by), (bx + math.cos(aa) * r * 0.28, by + math.sin(aa) * r * 0.28)], fill=fill, width=max(2, t - 3))


def ring(d, cx, cy, r, t, fill=255):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=fill, width=t)


def spiral(d, cx, cy, r, turns, t, fill=255):
    pts = []
    for i in range(200):
        k = i / 199
        a = k * turns * 2 * math.pi
        rr = r * k
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.line(pts, fill=fill, width=t, joint='curve')


def arcs(d, cx, cy, r0, n, step, a0, a1, t, fill=255):
    for i in range(n):
        r = r0 + i * step
        d.arc([cx - r, cy - r, cx + r, cy + r], a0, a1, fill=fill, width=t)


def lock(d, cx, cy, s, fill=255):
    d.rounded_rectangle([cx - s * 0.5, cy - s * 0.05, cx + s * 0.5, cy + s * 0.6], radius=s * 0.1, fill=fill)
    d.arc([cx - s * 0.34, cy - s * 0.55, cx + s * 0.34, cy + s * 0.15], 180, 360, fill=fill, width=int(s * 0.14))
    d.line([(cx - s * 0.34, cy - s * 0.2), (cx - s * 0.34, cy)], fill=fill, width=int(s * 0.14))
    d.line([(cx + s * 0.34, cy - s * 0.2), (cx + s * 0.34, cy)], fill=fill, width=int(s * 0.14))


def person(d, cx, cy, s, fill=255):
    d.ellipse([cx - s * 0.16, cy - s * 0.62, cx + s * 0.16, cy - s * 0.3], fill=fill)
    d.polygon([(cx - s * 0.3, cy + s * 0.45), (cx - s * 0.22, cy - s * 0.22), (cx + s * 0.22, cy - s * 0.22), (cx + s * 0.3, cy + s * 0.45)], fill=fill)


def cracks(d, cx, cy, r, n, t, rng, fill=255):
    for k in range(n):
        a = k * 2 * math.pi / n + rng.uniform(-0.25, 0.25)
        x, y = cx, cy
        for j in range(5):
            a += rng.uniform(-0.45, 0.45)
            L = r / 5 * rng.uniform(0.8, 1.2)
            nx, ny = x + math.cos(a) * L, y + math.sin(a) * L * 0.45
            d.line([(x, y), (nx, ny)], fill=fill, width=max(2, int(t * (1 - j / 6))))
            x, y = nx, ny


# ============================================================ 아이콘 합성
def compose_icon(sym, col, col2, bg_dark, accent=None, seed=0, extra=None, halo=1.0):
    """sym: 0~1 마스크 (S x S). col: 주 색, col2: 밝은 쪽 색."""
    c, c2 = hexc(col), hexc(col2)
    dark = hexc(bg_dark)
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    # 배경: 가운데가 밝은 방사형 + 아래 어둡게
    rad = radial(S, S, S * 0.5, S * 0.45, S * 0.75)
    bg = dark[None, None, :] * (0.55 + 0.45 * (1 - yy / S))[..., None] + c[None, None, :] * (rad ** 1.6)[..., None] * 0.55
    # 빛살
    ang = np.arctan2(yy - S * 0.45, xx - S * 0.5)
    rays = (0.5 + 0.5 * np.cos(ang * 9 + seed)) ** 6 * rad * 0.35
    bg += c2[None, None, :] * rays[..., None]
    # 반짝이
    rng = random.Random(seed)
    sp = mask(lambda d: [d.ellipse([x - r, y - r, x + r, y + r], fill=int(rng.uniform(120, 255)))
                         for x, y, r in [(rng.uniform(10, S - 10), rng.uniform(10, S - 10), rng.uniform(1.5, 3.5)) for _ in range(40)]])
    bg += blur(sp, 1.2)[..., None] * c2[None, None, :] * 0.8
    out = bg
    # 무늬 빛 번짐
    glow = blur(sym, 14) * 1.3 * halo + blur(sym, 5) * 0.6
    out = out + glow[..., None] * c[None, None, :]
    # 무늬 채우기: 위는 흰빛 → 아래 주 색
    t = np.clip(yy / S, 0, 1)[..., None]
    fill = (np.ones(3)[None, None, :] * (1 - t) * 0.85 + c2[None, None, :] * 0.6 + c[None, None, :] * t * 0.5)
    edge = np.clip(sym - blur(sym, 2.5), 0, 1)
    fill = fill * (1 - edge[..., None] * 0.5)
    out = out * (1 - sym[..., None]) + fill * sym[..., None]
    if extra is not None:
        e_m, e_col = extra
        ec = hexc(e_col)
        out = out + blur(e_m, 8)[..., None] * ec[None, None, :] * 0.8
        out = out * (1 - e_m[..., None]) + (ec * 0.6 + 0.4)[None, None, :] * e_m[..., None]
    # 비네트 + 테두리
    vig = np.clip(1 - radial(S, S, S / 2, S / 2, S * 0.78), 0, 1) ** 1.5
    out = out * (1 - vig[..., None] * 0.65)
    frame = np.zeros((S, S), np.float32)
    frame[:6, :] = frame[-6:, :] = frame[:, :6] = frame[:, -6:] = 1
    inner = np.zeros((S, S), np.float32)
    inner[8:11, 8:-8] = inner[-11:-8, 8:-8] = inner[8:-8, 8:11] = inner[8:-8, -11:-8] = 1
    out = out * (1 - frame[..., None]) + (dark * 0.5)[None, None, :] * frame[..., None]
    out = out * (1 - inner[..., None] * 0.6) + c2[None, None, :] * inner[..., None] * 0.6
    img = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), 'RGB').convert('RGBA')
    return img


def save_icon(img, name):
    img.resize((64, 64), Image.LANCZOS).save(os.path.join(GUI, name + '.png'))


def locked_icon(col, bg, seed):
    def dr(d):
        lock(d, S / 2, S / 2 + 6, 120)
    sym = mask(dr) * 0.85
    img = compose_icon(sym, 0x8A8A8A, 0xB8B8B8, bg, seed=seed, halo=0.5)
    a = np.asarray(img, np.float32) / 255
    gray = a[..., :3].mean(-1, keepdims=True)
    a[..., :3] = gray * 0.7 + a[..., :3] * 0.3
    return Image.fromarray((a * 255).astype(np.uint8), 'RGBA')


# ============================================================ 직업별
def warrior_icons():
    col, col2, bg = 0xE8A048, 0xFFE0A0, 0x2A1206
    rng = random.Random(1)
    # 1 돌진 베기: 기울어진 검 + 바람줄
    def s1(d):
        sword(d, S * 0.55, S * 0.5, math.radians(45), 190, 20)
        for i in range(4):
            y = S * 0.3 + i * 28
            d.line([(S * 0.08, y + 40), (S * 0.38, y + 10)], fill=200, width=6)
    save_icon(compose_icon(mask(s1), col, col2, bg, seed=1), 'warrior_skill1')
    # 2 회오리 베기: 원형 칼바람 + 가운데 검
    def s2(d):
        arcs(d, S / 2, S / 2, 52, 3, 24, 200, 470, 9)
        sword(d, S / 2, S / 2, math.radians(-20), 140, 16)
    save_icon(compose_icon(mask(s2), col, col2, bg, seed=2), 'warrior_skill2')
    # 3 함성: 뿔나팔 모양 + 퍼지는 소리
    def s3(d):
        d.polygon([(S * 0.2, S * 0.45), (S * 0.45, S * 0.36), (S * 0.45, S * 0.64), (S * 0.2, S * 0.55)], fill=255)
        d.polygon([(S * 0.45, S * 0.3), (S * 0.56, S * 0.22), (S * 0.56, S * 0.78), (S * 0.45, S * 0.7)], fill=255)
        arcs(d, S * 0.52, S / 2, 40, 3, 26, -50, 50, 9)
    save_icon(compose_icon(mask(s3), col, col2, bg, seed=3), 'warrior_skill3')
    # T 재정비: 하트 + 십자
    def p(d):
        heart(d, S / 2, S * 0.48, 150)
    m = mask(p)
    cr = mask(lambda d: cross(d, S / 2, S * 0.47, 34, 11))
    save_icon(compose_icon(np.clip(m - cr, 0, 1), col, col2, bg, seed=4, extra=(cr, 0xFFF6DC)), 'warrior_passive')
    save_icon(locked_icon(col, bg, 5), 'warrior_skill4')
    save_icon(locked_icon(col, bg, 6), 'warrior_ultimate')


def mage_icons():
    col, col2, bg = 0x7AA8FF, 0xD8E8FF, 0x080E28
    # 1 화염구
    def s1(d):
        flame(d, S * 0.52, S * 0.48, 130)
    m = mask(s1)
    core = mask(lambda d: d.ellipse([S * 0.42, S * 0.52, S * 0.62, S * 0.72], fill=255))
    save_icon(compose_icon(m, 0xFF7A30, 0xFFD890, 0x2A0A04, seed=11, extra=(core * 0.8, 0xFFF0C0)), 'mage_skill1')
    # 2 서리 고리
    def s2(d):
        snowflake(d, S / 2, S / 2, 92, 12)
        ring(d, S / 2, S / 2, 108, 6)
    save_icon(compose_icon(mask(s2), 0xA8E8FF, 0xF0FCFF, 0x061A2A, seed=12), 'mage_skill2')
    # 3 순간이동: 소용돌이 + 별
    def s3(d):
        spiral(d, S / 2, S / 2, 100, 2.3, 11)
        star(d, S * 0.78, S * 0.24, 22, 6)
    save_icon(compose_icon(mask(s3), col, col2, bg, seed=13), 'mage_skill3')
    # T 마나 방패: 방울 + 문양
    def p(d):
        ring(d, S / 2, S / 2, 96, 12)
        star(d, S / 2, S / 2, 48, 14, n=4)
    save_icon(compose_icon(mask(p), col, col2, bg, seed=14), 'mage_passive')
    save_icon(locked_icon(col, bg, 15), 'mage_skill4')
    save_icon(locked_icon(col, bg, 16), 'mage_ultimate')


def tank_icons():
    col, col2, bg = 0x9AB4D0, 0xE8F2FF, 0x0C1420
    # 1 도발: 방패 + 붉은 느낌표
    sh = mask(lambda d: shield(d, S / 2, S / 2 + 8, 82, 190))
    ex = mask(lambda d: (d.rectangle([S / 2 - 12, S * 0.3, S / 2 + 12, S * 0.58], fill=255), d.ellipse([S / 2 - 13, S * 0.63, S / 2 + 13, S * 0.73], fill=255)))
    save_icon(compose_icon(np.clip(sh - ex, 0, 1), col, col2, bg, seed=21, extra=(ex, 0xFF4A3A)), 'tank_skill1')
    # 2 방패 돌격: 비스듬한 방패 + 속도선
    def s2(d):
        d.polygon(rot(shield_pts(S * 0.6, S / 2, 70, 170), S * 0.6, S / 2, math.radians(12)), fill=255)
        for i in range(5):
            y = S * 0.28 + i * 28
            d.line([(S * 0.06, y), (S * 0.34, y)], fill=210, width=7)
    save_icon(compose_icon(mask(s2), col, col2, bg, seed=22), 'tank_skill2')
    # 3 철벽: 벽돌 성벽
    def s3(d):
        for r in range(4):
            y0 = S * 0.3 + r * 34
            off = 0 if r % 2 == 0 else 27
            for c in range(-1, 5):
                x0 = S * 0.18 + c * 54 + off
                x0c, x1c = max(S * 0.16, x0), min(S * 0.84, x0 + 48)
                if x1c - x0c > 8:
                    d.rectangle([x0c, y0, x1c, y0 + 28], fill=255)
        for c in range(4):
            x0 = S * 0.18 + c * 46
            d.rectangle([x0, S * 0.2, x0 + 26, S * 0.3], fill=255)
    save_icon(compose_icon(mask(s3), col, col2, bg, seed=23), 'tank_skill3')
    # T 버티기: 방패 안의 하트
    sh = mask(lambda d: shield(d, S / 2, S / 2 + 6, 86, 196))
    ht = mask(lambda d: heart(d, S / 2, S * 0.47, 90))
    save_icon(compose_icon(np.clip(sh - ht, 0, 1), col, col2, bg, seed=24, extra=(ht, 0xFF7A8A)), 'tank_passive')
    save_icon(locked_icon(col, bg, 25), 'tank_skill4')
    save_icon(locked_icon(col, bg, 26), 'tank_ultimate')


def healer_icons():
    col, col2, bg = 0xA8F0B0, 0xF0FFF0, 0x06200E
    # 1 치유: 큰 십자
    save_icon(compose_icon(mask(lambda d: cross(d, S / 2, S / 2, 84, 28)), col, col2, bg, seed=31), 'healer_skill1')
    # 2 정화: 물방울 + 반짝임
    def s2(d):
        flame(d, S / 2, S * 0.5, 120)
        star(d, S * 0.24, S * 0.3, 24, 6)
        star(d, S * 0.78, S * 0.66, 18, 5)
    m = mask(s2)
    m = np.flipud(m).copy() if False else m
    save_icon(compose_icon(mask(lambda d: (d.polygon([(S / 2, S * 0.14), (S * 0.72, S * 0.55), (S / 2, S * 0.86), (S * 0.28, S * 0.55)], fill=0),
                                             d.ellipse([S * 0.28, S * 0.4, S * 0.72, S * 0.86], fill=255),
                                             d.polygon([(S / 2, S * 0.12), (S * 0.71, S * 0.55), (S * 0.29, S * 0.55)], fill=255),
                                             star(d, S * 0.22, S * 0.28, 26, 6), star(d, S * 0.8, S * 0.3, 18, 5))), 0x9CFFE0, 0xF0FFFA, 0x062018, seed=32), 'healer_skill2')
    # 3 축복: 광륜 + 별
    def s3(d):
        d.ellipse([S * 0.2, S * 0.2, S * 0.8, S * 0.42], outline=255, width=12)
        star(d, S / 2, S * 0.62, 70, 18, n=4)
    save_icon(compose_icon(mask(s3), 0xFFE08A, 0xFFF8DC, 0x261A04, seed=33), 'healer_skill3')
    # T 기도: 사람 + 빛살
    def p(d):
        person(d, S / 2, S * 0.6, 200)
        for k in range(7):
            a = math.radians(-160 + k * 23.3)
            d.line([(S / 2 + math.cos(a) * 70, S * 0.3 + math.sin(a) * 70), (S / 2 + math.cos(a) * 110, S * 0.3 + math.sin(a) * 110)], fill=230, width=7)
    save_icon(compose_icon(mask(p), col, col2, bg, seed=34), 'healer_passive')
    save_icon(locked_icon(col, bg, 35), 'healer_skill4')
    save_icon(locked_icon(col, bg, 36), 'healer_ultimate')


def guardian_icons():
    col, col2, bg = 0xF0C860, 0xFFF6DC, 0x1E1606
    rng = random.Random(9)

    def gshield(d, cx, cy, w, h):
        shield(d, cx, cy, w, h)

    emblem = lambda d, cx, cy, s: (d.rectangle([cx - s * 0.09, cy - s * 0.42, cx + s * 0.09, cy + s * 0.36], fill=0),
                                   d.rectangle([cx - s * 0.36, cy - s * 0.2, cx + s * 0.36, cy - s * 0.04], fill=0))
    # 1 도발의 함성: 방패 + 소리 고리
    def s1(d):
        arcs(d, S / 2, S / 2, 96, 2, 18, 0, 360, 7)
        gshield(d, S / 2, S / 2 + 6, 62, 150)
        emblem(d, S / 2, S / 2, 130)
    save_icon(compose_icon(mask(s1), col, col2, bg, seed=41), 'guardian_skill1')
    # 2 방패 돌진
    def s2(d):
        d.polygon(rot(shield_pts(S * 0.62, S / 2, 66, 170), S * 0.62, S / 2, math.radians(10)), fill=255)
        for i in range(5):
            y = S * 0.28 + i * 28
            d.line([(S * 0.04, y), (S * 0.36, y)], fill=210, width=8)
    save_icon(compose_icon(mask(s2), col, col2, bg, seed=42), 'guardian_skill2')
    # 3 수호의 결계: 반구 + 바닥 고리 + 안의 사람
    def s3(d):
        d.arc([S * 0.12, S * 0.22, S * 0.88, S * 1.0], 180, 360, fill=255, width=12)
        for k in range(1, 4):
            x = S * 0.12 + k * S * 0.19
            d.arc([x - 8, S * 0.25, x + 8, S * 0.62], 0, 360, fill=120, width=4)
        d.ellipse([S * 0.1, S * 0.56, S * 0.9, S * 0.68], outline=255, width=8)
        person(d, S / 2, S * 0.5, 120)
    save_icon(compose_icon(mask(s3), col, col2, bg, seed=43), 'guardian_skill3')
    # 4 대지 강타: 내려찍는 방패 + 갈라진 땅
    def s4(d):
        d.polygon(rot(shield_pts(S / 2, S * 0.38, 52, 128), S / 2, S * 0.38, math.radians(180)), fill=255)
        cracks(d, S / 2, S * 0.76, 110, 7, 10, rng)
        d.line([(S * 0.1, S * 0.76), (S * 0.9, S * 0.76)], fill=255, width=6)
    save_icon(compose_icon(mask(s4), col, col2, bg, seed=44), 'guardian_skill4')
    # R 금성철벽: 큰 방패 + 사방 빛살
    def u(d):
        for k in range(16):
            a = k * math.pi / 8
            d.line([(S / 2 + math.cos(a) * 92, S / 2 + math.sin(a) * 92), (S / 2 + math.cos(a) * 124, S / 2 + math.sin(a) * 124)], fill=220, width=8)
        gshield(d, S / 2, S / 2 + 6, 80, 192)
        emblem(d, S / 2, S / 2 - 4, 170)
    gem = mask(lambda d: d.ellipse([S / 2 - 14, S * 0.43 - 14, S / 2 + 14, S * 0.43 + 14], fill=255))
    save_icon(compose_icon(np.clip(mask(u) - gem, 0, 1), col, col2, bg, seed=45, extra=(gem, 0x8FD8FF), halo=1.3), 'guardian_ultimate')
    # T 수호 태세: 방패와 이어진 두 사람
    def p(d):
        gshield(d, S * 0.38, S / 2 + 4, 56, 140)
        person(d, S * 0.76, S * 0.6, 120)
        d.line([(S * 0.5, S * 0.42), (S * 0.7, S * 0.42)], fill=255, width=6)
    save_icon(compose_icon(mask(p), col, col2, bg, seed=46), 'guardian_passive')


# ============================================================ 선택 카드 (256x160)
def card(name, col, col2, bg, sym_fn, seed):
    W, H = 512, 320
    c, c2, dark = hexc(col), hexc(col2), hexc(bg)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    rad = radial(W, H, W * 0.5, H * 0.48, W * 0.55)
    out = dark[None, None, :] * (0.5 + 0.5 * (1 - yy / H))[..., None] + c[None, None, :] * (rad ** 1.8)[..., None] * 0.7
    ang = np.arctan2(yy - H * 0.48, xx - W * 0.5)
    out += c2[None, None, :] * ((0.5 + 0.5 * np.cos(ang * 12 + seed)) ** 8 * rad * 0.45)[..., None]
    # 바닥 빛
    floor = np.exp(-((yy - H * 0.86) / 18) ** 2) * np.exp(-((xx - W / 2) / (W * 0.35)) ** 2)
    out += c[None, None, :] * floor[..., None] * 0.6
    rng = random.Random(seed)
    sp = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(sp)
    for _ in range(90):
        x, y, r = rng.uniform(0, W), rng.uniform(0, H), rng.uniform(1.5, 4)
        d.ellipse([x - r, y - r, x + r, y + r], fill=int(rng.uniform(100, 255)))
    out += blur(np.asarray(sp, np.float32) / 255, 1.5)[..., None] * c2[None, None, :]
    m = Image.new('L', (W, H), 0)
    sym_fn(ImageDraw.Draw(m), W, H)
    sym = np.asarray(m, np.float32) / 255
    out += (blur(sym, 22) * 1.2 + blur(sym, 7) * 0.6)[..., None] * c[None, None, :]
    t = np.clip((yy - H * 0.15) / (H * 0.7), 0, 1)[..., None]
    fill = np.ones(3)[None, None, :] * (1 - t) * 0.8 + c2[None, None, :] * 0.5 + c[None, None, :] * t * 0.5
    edge = np.clip(sym - blur(sym, 3), 0, 1)
    fill = fill * (1 - edge[..., None] * 0.45)
    out = out * (1 - sym[..., None]) + fill * sym[..., None]
    vig = np.clip(1 - radial(W, H, W / 2, H / 2, W * 0.62), 0, 1) ** 1.4
    out = out * (1 - vig[..., None] * 0.7)
    img = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), 'RGB').convert('RGBA')
    img.resize((256, 160), Image.LANCZOS).save(os.path.join(GUI, f'card_{name}.png'))


def cards():
    def warrior(d, W, H):
        sword(d, W * 0.42, H * 0.52, math.radians(-28), 300, 30)
        sword(d, W * 0.58, H * 0.52, math.radians(28), 300, 30)

    def mage(d, W, H):
        d.polygon([(W * 0.5, H * 0.06), (W * 0.66, H * 0.5), (W * 0.34, H * 0.5)], fill=255)
        d.ellipse([W * 0.26, H * 0.46, W * 0.74, H * 0.6], fill=255)
        star(d, W * 0.5, H * 0.75, 40, 10)
        for k in range(3):
            star(d, W * (0.25 + k * 0.25), H * (0.25 if k != 1 else 0.9), 16, 4)

    def tank(d, W, H):
        shield(d, W * 0.5, H * 0.52, 95, 250)

    def healer(d, W, H):
        cross(d, W * 0.5, H * 0.5, 95, 30)
        d.ellipse([W * 0.36, H * 0.04, W * 0.64, H * 0.16], outline=255, width=10)

    def guardian(d, W, H):
        for k in range(14):
            a = k * math.pi / 7
            d.line([(W * 0.5 + math.cos(a) * 140, H * 0.5 + math.sin(a) * 140), (W * 0.5 + math.cos(a) * 175, H * 0.5 + math.sin(a) * 175)], fill=200, width=8)
        shield(d, W * 0.5, H * 0.52, 100, 260)
        d.rectangle([W * 0.5 - 10, H * 0.24, W * 0.5 + 10, H * 0.72], fill=0)
        d.rectangle([W * 0.5 - 50, H * 0.36, W * 0.5 + 50, H * 0.44], fill=0)

    card('warrior', 0xE8A048, 0xFFE0A0, 0x2A1206, warrior, 1)
    card('mage', 0x7AA8FF, 0xD8E8FF, 0x080E28, mage, 2)
    card('tank', 0x9AB4D0, 0xE8F2FF, 0x0C1420, tank, 3)
    card('healer', 0xA8F0B0, 0xF0FFF0, 0x06200E, healer, 4)
    # 수호기사 카드는 사용자 그림 (tools/gen_hud.py guardian)


# ============================================================ 전직 교관 피부 (64x64, 플레이어 모양)
def job_master_skin():
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = im.load()
    rng = random.Random(7)
    SKIN = (214, 170, 132)
    SKIN_D = (180, 136, 100)
    ROBE = (38, 34, 70)
    ROBE_D = (26, 22, 50)
    GOLD = (226, 184, 86)
    GOLD_D = (168, 128, 50)
    WHITE = (236, 232, 224)
    GRAY = (176, 172, 168)
    BOOT = (70, 46, 30)

    def rect(x, y, w, h, c, noise=8):
        for i in range(x, x + w):
            for j in range(y, y + h):
                n = rng.randint(-noise, noise)
                px[i, j] = (max(0, min(255, c[0] + n)), max(0, min(255, c[1] + n)), max(0, min(255, c[2] + n)), 255)

    def fill_box(u, v, w, h, d, side, top, bottom, front=None, back=None):
        """상자 UV: (u,v) 기준, w(가로) h(세로) d(깊이)."""
        rect(u + d, v, w, d, top)
        rect(u + d + w, v, w, d, bottom)
        rect(u, v + d, d, h, side)
        rect(u + d, v + d, w, h, front or side)
        rect(u + d + w, v + d, d, h, side)
        rect(u + d + w + d, v + d, w, h, back or side)

    # 머리 (0,0) 8x8x8: 회색 머리카락, 흰 수염
    fill_box(0, 0, 8, 8, 8, GRAY, GRAY, SKIN)
    rect(8, 8, 8, 8, SKIN)  # 얼굴
    rect(8, 8, 8, 2, GRAY)  # 앞머리
    rect(9, 11, 2, 1, (60, 50, 40), 0)  # 눈썹
    rect(13, 11, 2, 1, (60, 50, 40), 0)
    px[9, 12] = px[10, 12] = (240, 240, 240, 255)
    px[13, 12] = px[14, 12] = (240, 240, 240, 255)
    px[10, 12] = (40, 60, 110, 255)
    px[13, 12] = (40, 60, 110, 255)
    rect(11, 13, 2, 1, SKIN_D, 0)  # 코
    rect(9, 14, 6, 2, WHITE)  # 수염
    rect(10, 13, 1, 1, WHITE)
    rect(13, 13, 1, 1, WHITE)
    rect(0, 8, 8, 8, GRAY)
    rect(16, 8, 8, 8, GRAY)
    rect(24, 8, 8, 8, GRAY)
    rect(2, 13, 4, 3, WHITE)  # 옆 수염
    rect(18, 13, 4, 3, WHITE)
    # 모자 층 (32,0): 두건 (앞은 얼굴이 보이게 테두리만)
    for (u, v, w, h) in [(40, 0, 8, 8), (48, 0, 8, 8), (32, 8, 8, 8), (48, 8, 8, 8), (56, 8, 8, 8)]:
        rect(u, v, w, h, ROBE)
    rect(40, 8, 8, 2, ROBE)
    rect(40, 10, 1, 4, ROBE)
    rect(47, 10, 1, 4, ROBE)
    rect(40, 9, 8, 1, GOLD, 4)
    rect(32, 15, 8, 1, GOLD, 4)
    rect(48, 15, 8, 1, GOLD, 4)
    rect(56, 15, 8, 1, GOLD, 4)
    # 몸통 (16,16) 8x12x4: 겉옷 + 금 테두리 + 띠
    fill_box(16, 16, 8, 12, 4, ROBE, ROBE, ROBE, ROBE, ROBE)
    rect(23, 20, 2, 12, GOLD, 4)  # 앞 여밈 (가운데)
    rect(20, 26, 8, 1, GOLD_D, 2)  # 띠
    rect(23, 26, 2, 1, (240, 210, 110), 0)
    rect(20, 20, 8, 1, GOLD, 3)
    # 등에 문장
    rect(35, 22, 2, 5, GOLD, 3)
    rect(34, 23, 4, 1, GOLD, 3)
    # 오른팔 (40,16) 4x12x4
    fill_box(40, 16, 4, 12, 4, ROBE, ROBE, SKIN)
    rect(44, 29, 4, 2, GOLD, 3)
    rect(40, 29, 4, 2, GOLD, 3)
    rect(48, 29, 4, 2, GOLD, 3)
    rect(52, 29, 4, 2, GOLD, 3)
    for (u, v) in [(40, 31), (44, 31), (48, 31), (52, 31)]:
        rect(u, v, 4, 1, SKIN)
    # 왼팔 (32,48)
    fill_box(32, 48, 4, 12, 4, ROBE, ROBE, SKIN)
    for (u, v) in [(32, 61), (36, 61), (40, 61), (44, 61)]:
        rect(u, v, 4, 2, GOLD, 3)
        rect(u, v + 2, 4, 1, SKIN)
    # 다리: 겉옷 자락 + 장화
    fill_box(0, 16, 4, 12, 4, ROBE_D, ROBE_D, BOOT)
    fill_box(16, 48, 4, 12, 4, ROBE_D, ROBE_D, BOOT)
    for (u, v) in [(0, 20), (4, 20), (8, 20), (12, 20), (16, 52), (20, 52), (24, 52), (28, 52)]:
        rect(u, v + 7, 4, 1, GOLD, 3)
        rect(u, v + 9, 4, 3, BOOT)
    os.makedirs(ENT, exist_ok=True)
    im.save(os.path.join(ENT, 'job_master.png'))


def paladin_icons():
    col, col2, bg = 0xFFE7A0, 0xFFFDF4, 0x241A06
    # 1 심판의 일격: 땅에 꽂히는 검 + 십자 빛
    def s1(d):
        sword(d, S / 2, S * 0.42, math.radians(180), 190, 22)
        for k in range(4):
            a = k * math.pi / 2 + math.pi / 4
            d.line([(S / 2, S * 0.8), (S / 2 + math.cos(a) * 90, S * 0.8 + math.sin(a) * 40)], fill=220, width=8)
        d.ellipse([S * 0.2, S * 0.74, S * 0.8, S * 0.88], outline=255, width=7)
    save_icon(compose_icon(mask(s1), col, col2, bg, seed=51), 'paladin_skill1')
    # 2 신성한 방패: 십자 방패 + 빛 고리
    sh = mask(lambda d: (shield(d, S / 2, S / 2 + 6, 74, 176), ring(d, S / 2, S / 2, 112, 7)))
    cr = mask(lambda d: (d.rectangle([S / 2 - 11, S * 0.3, S / 2 + 11, S * 0.68], fill=255), d.rectangle([S / 2 - 42, S * 0.4, S / 2 + 42, S * 0.48], fill=255)))
    save_icon(compose_icon(np.clip(sh - cr, 0, 1), col, col2, bg, seed=52, extra=(cr, 0x9FD8FF)), 'paladin_skill2')
    # 3 정화의 땅: 바닥의 십자 마법진 + 솟는 빛
    def s3(d):
        d.ellipse([S * 0.08, S * 0.6, S * 0.92, S * 0.9], outline=255, width=8)
        d.polygon([(S * 0.5, S * 0.62), (S * 0.56, S * 0.75), (S * 0.5, S * 0.88), (S * 0.44, S * 0.75)], fill=255)
        d.polygon([(S * 0.24, S * 0.75), (S * 0.5, S * 0.71), (S * 0.76, S * 0.75), (S * 0.5, S * 0.79)], fill=255)
        for k in range(5):
            x = S * (0.22 + k * 0.14)
            d.line([(x, S * 0.68), (x, S * (0.18 + 0.08 * (k % 2)))], fill=200, width=7)
    save_icon(compose_icon(mask(s3), col, col2, bg, seed=53), 'paladin_skill3')
    # 4 빛의 사슬: 대각선 사슬 고리
    def s4(d):
        for k in range(6):
            cx, cy = S * (0.18 + k * 0.13), S * (0.82 - k * 0.13)
            if k % 2 == 0:
                d.ellipse([cx - 26, cy - 14, cx + 26, cy + 14], outline=255, width=9)
            else:
                d.ellipse([cx - 14, cy - 26, cx + 14, cy + 26], outline=255, width=9)
        star(d, S * 0.78, S * 0.22, 30, 8)
    save_icon(compose_icon(mask(s4), col, col2, bg, seed=54), 'paladin_skill4')
    # R 성검강림: 거대한 검 + 사방 빛살
    def u(d):
        for k in range(16):
            a = k * math.pi / 8
            d.line([(S / 2 + math.cos(a) * 60, S * 0.42 + math.sin(a) * 60), (S / 2 + math.cos(a) * 124, S * 0.42 + math.sin(a) * 124)], fill=190, width=7)
        sword(d, S / 2, S * 0.48, math.radians(180), 240, 30)
    save_icon(compose_icon(mask(u), col, col2, bg, seed=55, halo=1.4), 'paladin_ultimate')
    # T 성스러운 오라: 십자 + 둥근 빛살
    def p(d):
        cross(d, S / 2, S / 2, 70, 16)
        ring(d, S / 2, S / 2, 100, 8)
        for k in range(12):
            a = k * math.pi / 6
            d.line([(S / 2 + math.cos(a) * 108, S / 2 + math.sin(a) * 108), (S / 2 + math.cos(a) * 124, S / 2 + math.sin(a) * 124)], fill=230, width=7)
    save_icon(compose_icon(mask(p), col, col2, bg, seed=56), 'paladin_passive')


def paladin_card():
    def pal(d, W, H):
        for k in range(14):
            a = k * math.pi / 7
            d.line([(W * 0.5 + math.cos(a) * 120, H * 0.42 + math.sin(a) * 120), (W * 0.5 + math.cos(a) * 170, H * 0.42 + math.sin(a) * 170)], fill=200, width=8)
        sword(d, W * 0.5, H * 0.5, math.radians(180), 300, 34)
    card('paladin', 0xFFE7A0, 0xFFFDF4, 0x241A06, pal, 6)


def king_skin():
    """왕: 붉은 왕의 옷 + 흰 담비털 (검은 점) + 금 목걸이, 갈색 수염. 관은 3D 로 따로 그림."""
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = im.load()
    rng = random.Random(11)
    SKIN = (222, 178, 140)
    SKIN_D = (188, 142, 108)
    HAIR = (120, 78, 44)
    RED = (150, 20, 32)
    RED_D = (104, 12, 22)
    ERM = (240, 238, 232)
    GOLD = (232, 190, 80)
    PANTS = (60, 20, 70)
    BOOT = (60, 36, 22)

    def rect(x, y, w, h, c, noise=7):
        for i in range(x, x + w):
            for j in range(y, y + h):
                n = rng.randint(-noise, noise)
                px[i, j] = (max(0, min(255, c[0] + n)), max(0, min(255, c[1] + n)), max(0, min(255, c[2] + n)), 255)

    def box(u, v, w, h, d, side, top, bottom, front=None, back=None):
        rect(u + d, v, w, d, top)
        rect(u + d + w, v, w, d, bottom)
        rect(u, v + d, d, h, side)
        rect(u + d, v + d, w, h, front or side)
        rect(u + d + w, v + d, d, h, side)
        rect(u + d + w + d, v + d, w, h, back or side)

    def ermine(u, v, w, h):
        rect(u, v, w, h, ERM, 4)
        for _ in range(max(1, w * h // 6)):
            px[u + rng.randrange(w), v + rng.randrange(h)] = (30, 30, 34, 255)

    # 머리: 갈색 머리 + 수염
    box(0, 0, 8, 8, 8, HAIR, HAIR, SKIN)
    rect(8, 8, 8, 8, SKIN)
    rect(8, 8, 8, 2, HAIR)
    rect(9, 11, 2, 1, (70, 40, 20), 0)
    rect(13, 11, 2, 1, (70, 40, 20), 0)
    px[9, 12] = px[14, 12] = (240, 240, 240, 255)
    px[10, 12] = px[13, 12] = (60, 90, 40, 255)
    rect(11, 13, 2, 1, SKIN_D, 0)
    rect(9, 14, 6, 2, HAIR)
    rect(10, 13, 1, 1, HAIR)
    rect(13, 13, 1, 1, HAIR)
    for (u, w) in [(0, 8), (16, 8), (24, 8)]:
        rect(u, 8, w, 8, HAIR)
    rect(2, 13, 4, 3, HAIR)
    rect(18, 13, 4, 3, HAIR)
    # 몸: 붉은 옷 + 담비털 어깨 + 금 목걸이
    box(16, 16, 8, 12, 4, RED, RED, RED, RED, RED)
    ermine(20, 20, 8, 3)
    ermine(16, 20, 4, 3)
    ermine(28, 20, 4, 3)
    ermine(32, 20, 8, 3)
    for (x, y) in [(21, 23), (22, 24), (23, 25), (24, 25), (25, 24), (26, 23)]:
        px[x, y] = GOLD + (255,)
    rect(23, 26, 2, 2, (200, 30, 40), 0)
    rect(23, 23, 2, 9, RED_D, 3)
    rect(20, 28, 8, 1, GOLD, 3)
    rect(34, 24, 4, 6, RED_D, 3)  # 등의 망토 접힘
    # 팔: 붉은 소매 + 담비털 소매 끝
    box(40, 16, 4, 12, 4, RED, RED, SKIN)
    box(32, 48, 4, 12, 4, RED, RED, SKIN)
    for (u, v) in [(40, 29), (44, 29), (48, 29), (52, 29), (32, 61), (36, 61), (40, 61), (44, 61)]:
        ermine(u, v, 4, 2)
    for (u, v) in [(40, 31), (44, 31), (48, 31), (52, 31), (32, 63), (36, 63), (40, 63), (44, 63)]:
        rect(u, v, 4, 1, SKIN)
    # 다리: 보라 바지 + 장화 + 옷자락
    box(0, 16, 4, 12, 4, PANTS, PANTS, BOOT)
    box(16, 48, 4, 12, 4, PANTS, PANTS, BOOT)
    for (u, v) in [(0, 20), (4, 20), (8, 20), (12, 20), (16, 52), (20, 52), (24, 52), (28, 52)]:
        rect(u, v, 4, 4, RED)
        rect(u, v + 4, 4, 1, ERM, 3)
        rect(u, v + 8, 4, 4, BOOT)
    os.makedirs(ENT, exist_ok=True)
    im.save(os.path.join(ENT, 'king.png'))


if __name__ == '__main__':
    warrior_icons()
    mage_icons()
    tank_icons()
    healer_icons()
    # guardian_icons(): 수호기사는 사용자 그림 (tools/gen_hud.py guardian) 을 씀
    cards()
    job_master_skin()
    # paladin_icons() / paladin_card(): 성기사는 사용자 그림 (tools/gen_hud.py paladin) 을 씀
    king_skin()
    print('done')
