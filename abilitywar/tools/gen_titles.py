#!/usr/bin/env python3
"""
궁극기 연출 제목 그림 -> textures/gui/ult_title_<능력>.png
능력마다 글자 종류를 다르게: 용기사 = 한자(古龍降臨), 공허 = 영어(EVENT HORIZON).
"""
import os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), '..')
GUI = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures', 'gui')
KANJI = '/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf'
LATIN = '/usr/share/fonts/opentype/inter/Inter-Black.otf'


def text_mask(W, H, text, font, size, spacing, stroke, shear=0.0, yoff=0):
    f = ImageFont.truetype(font, size)
    widths = [f.getbbox(ch)[2] - f.getbbox(ch)[0] if ch != ' ' else size * 0.35 for ch in text]
    total = sum(widths) + spacing * (len(text) - 1)
    m = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(m)
    x = (W - total) / 2
    for ch, w in zip(text, widths):
        if ch != ' ':
            bb = f.getbbox(ch)
            d.text((x - bb[0], H / 2 + yoff), ch, font=f, anchor='lm', fill=255, stroke_width=stroke, stroke_fill=255)
        x += w + spacing
    if shear:
        m = m.transform(m.size, Image.AFFINE, (1, shear, -shear * H / 2, 0, 1, 0), Image.BICUBIC)
    return np.asarray(m, np.float32) / 255


def noise(W, H, scale, seed):
    r = np.random.RandomState(seed)
    small = r.rand(H // scale + 2, W // scale + 2).astype(np.float32)
    im = Image.fromarray((small * 255).astype(np.uint8)).resize((W, H), Image.BICUBIC)
    return np.asarray(im, np.float32) / 255


def blur(a, r):
    return np.asarray(Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r)), np.float32) / 255


def compose(W, H, mask, top, mid, bot, outline_col, glow_col, glow_r, seed, rough=0.0):
    if rough:
        n = noise(W, H, 6, seed) * 0.6 + noise(W, H, 2, seed + 1) * 0.4
        edge = blur(mask, 2.0)
        mask = np.clip((edge - 0.5 + (n - 0.5) * rough) * 6 + 0.5, 0, 1) * (blur(mask, 1) > 0.05)
    yy = np.linspace(0, 1, H)[:, None]
    ys = np.where(mask.max(1) > 0.5)[0]
    y0, y1 = (ys.min(), ys.max()) if len(ys) else (0, H)
    t = np.clip((yy * H - y0) / max(1, y1 - y0), 0, 1)
    fill = np.zeros((H, W, 3), np.float32)
    for k in range(3):
        a = np.where(t < 0.5, top[k] + (mid[k] - top[k]) * (t / 0.5), mid[k] + (bot[k] - mid[k]) * ((t - 0.5) / 0.5))
        fill[..., k] = a / 255
    # 윗면 하이라이트 (빗각)
    hl = np.clip(mask - np.roll(mask, 4, axis=0), 0, 1)
    fill += hl[..., None] * 0.35
    outline = np.clip(blur(mask, 1.2) * 0 + np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(9)), np.float32) / 255, 0, 1)
    glow = blur(outline, glow_r)
    out = np.zeros((H, W, 4), np.float32)
    # 바깥 빛
    for k in range(3):
        out[..., k] = glow_col[k] / 255
    out[..., 3] = glow * 0.85
    # 테두리
    a_o = outline
    for k in range(3):
        out[..., k] = out[..., k] * (1 - a_o) + outline_col[k] / 255 * a_o
    out[..., 3] = np.maximum(out[..., 3], a_o)
    # 글자
    for k in range(3):
        out[..., k] = out[..., k] * (1 - mask) + np.clip(fill[..., k], 0, 1) * mask
    out[..., 3] = np.maximum(out[..., 3], mask)
    return out


def save(a, name):
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'RGBA').save(os.path.join(GUI, name))
    print('wrote', name)


def dragon():
    W, H = 1024, 300
    m = text_mask(W, H, '古龍降臨', KANJI, 196, 30, 3, shear=-0.12)
    out = compose(W, H, m, (255, 250, 215), (255, 170, 60), (200, 40, 12), (34, 6, 2), (255, 90, 20), 16, 7, rough=0.35)
    # 위로 솟는 불티
    r = random.Random(3)
    sp = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(sp)
    for _ in range(60):
        x, y = r.uniform(120, W - 120), r.uniform(20, 120)
        s = r.uniform(2, 5)
        d.ellipse([x - s, y - s, x + s, y + s], fill=int(r.uniform(120, 255)))
    spa = np.asarray(sp.filter(ImageFilter.GaussianBlur(1.2)), np.float32) / 255
    a = out[..., 3]
    for k, c in enumerate((1.0, 0.75, 0.35)):
        out[..., k] = out[..., k] * (1 - spa) + c * spa
    out[..., 3] = np.maximum(a, spa)
    save(out, 'ult_title_dragon.png')


def void():
    W, H = 1024, 220
    m = text_mask(W, H, 'EVENT HORIZON', LATIN, 90, 10, 1)
    out = compose(W, H, m, (255, 255, 255), (214, 186, 255), (120, 60, 230), (14, 0, 34), (150, 90, 255), 14, 11)
    # 가운데를 가르는 가로 빛줄기 (사건의 지평선)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    line = np.exp(-((yy - H / 2) / 3.0) ** 2) * np.exp(-((xx - W / 2) / (W * 0.42)) ** 2)
    for k, c in enumerate((0.85, 0.75, 1.0)):
        out[..., k] = np.clip(out[..., k] + c * line * (1 - m) * 0.9, 0, 1)
    out[..., 3] = np.maximum(out[..., 3], line * 0.9)
    save(out, 'ult_title_void.png')


HANGUL = '/usr/share/fonts/opentype/unifont/unifont.otf'


def blade():
    """마검사: 한국어 (도트 글꼴을 크게, 마력 보라색)."""
    W, H = 1024, 260
    m = text_mask(W, H, '천지개벽', HANGUL, 176, 34, 6)
    out = compose(W, H, m, (255, 250, 255), (226, 170, 255), (120, 40, 210), (16, 2, 26), (170, 70, 255), 16, 13)
    # 가로로 가르는 참격선
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    ang = -0.12
    d = (yy - H / 2) * np.cos(ang) - (xx - W / 2) * np.sin(ang)
    line = np.exp(-(d / 2.4) ** 2) * np.exp(-((xx - W / 2) / (W * 0.46)) ** 2)
    for k, c in enumerate((1.0, 0.85, 1.0)):
        out[..., k] = np.clip(out[..., k] * (1 - line * 0.6) + c * line, 0, 1)
    out[..., 3] = np.maximum(out[..., 3], line)
    save(out, 'ult_title_blade.png')


def blood():
    """혈귀: 한자 血月滿開, 핏빛 + 아래로 흘러내리는 핏방울."""
    W, H = 1024, 300
    m = text_mask(W, H, '血月滿開', KANJI, 190, 34, 4, yoff=-26)
    # 글자 아랫면에서 흘러내리는 핏줄기
    r = random.Random(11)
    drip = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(drip)
    below = np.zeros_like(m)
    below[:-3] = m[3:]
    bottom = (m > 0.5) & (np.roll(m, -6, axis=0) < 0.5)
    ys, xs = np.where(bottom)
    idx = list(range(len(xs)))
    r.shuffle(idx)
    used = []
    for i in idx:
        x, y = int(xs[i]), int(ys[i])
        if any(abs(x - u) < 22 for u in used) or y > H - 70 or m[y + 8:y + 60, x].max() > 0.3:
            continue
        used.append(x)
        L = r.uniform(18, 70)
        w0 = r.uniform(3.5, 6.5)
        steps = 24
        for k in range(steps):
            t = k / (steps - 1)
            ww = w0 * (1 - 0.55 * t)
            yy = y + t * L
            d.ellipse([x - ww, yy - ww, x + ww, yy + ww], fill=255)
        bw = w0 * 1.25
        d.ellipse([x - bw, y + L - bw * 0.6, x + bw, y + L + bw * 1.4], fill=255)
        if len(used) > 26:
            break
    da = np.asarray(drip.filter(ImageFilter.GaussianBlur(0.8)), np.float32) / 255
    m = np.maximum(m, da)
    out = compose(W, H, m, (255, 170, 170), (200, 16, 36), (90, 0, 10), (18, 0, 2), (230, 20, 40), 18, 21, rough=0.25)
    # 뒤에 걸린 핏빛 달
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    rr = np.sqrt((xx - W / 2) ** 2 + (yy - H / 2 + 24) ** 2)
    moon = np.clip((150 - rr) / 3, 0, 1) * 0.0 + np.exp(-((rr - 140) / 6.0) ** 2) * 0.55
    for k, c in enumerate((1.0, 0.25, 0.3)):
        out[..., k] = out[..., k] * (1 - moon * (1 - out[..., 3])) + c * moon * (1 - out[..., 3])
    out[..., 3] = np.maximum(out[..., 3], moon)
    save(out, 'ult_title_blood.png')


def cantor():
    """성가사: 라틴어 GLORIA (세리프, 금빛) + 뒤로 퍼지는 빛줄기와 광륜."""
    W, H = 1024, 260
    SERIF = '/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf'
    m = text_mask(W, H, 'GLORIA', SERIF, 150, 26, 2, yoff=6)
    out = compose(W, H, m, (255, 255, 240), (255, 220, 120), (200, 130, 30), (40, 24, 4), (255, 210, 110), 16, 31)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    cx, cy = W / 2, H / 2 + 4
    ang = np.arctan2(yy - cy, xx - cx)
    rr = np.sqrt(((xx - cx) / 1.0) ** 2 + ((yy - cy) * 2.2) ** 2)
    rays = (0.5 + 0.5 * np.cos(ang * 18)) ** 6 * np.exp(-rr / 420) * 0.75
    halo = np.exp(-((np.sqrt((xx - cx) ** 2 + ((yy - cy + 4) * 3.2) ** 2) - 360) / 10) ** 2) * 0.6
    light = np.clip(rays + halo, 0, 1) * (1 - out[..., 3])
    for k, c in enumerate((1.0, 0.86, 0.5)):
        out[..., k] = out[..., k] * (1 - light) + c * light
    out[..., 3] = np.maximum(out[..., 3], light)
    save(out, 'ult_title_cantor.png')


def warlock():
    """흑마법사: 영어 SOUL HARVEST (창백한 녹색) + 금 간 글자 + 피어오르는 영혼 불꽃."""
    W, H = 1024, 220
    m = text_mask(W, H, 'SOUL HARVEST', LATIN, 92, 8, 1)
    # 금 간 자국
    r = random.Random(13)
    cr = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(cr)
    for _ in range(14):
        x, y = r.uniform(120, W - 120), r.uniform(70, 150)
        pts = [(x, y)]
        for k in range(4):
            pts.append((pts[-1][0] + r.uniform(-14, 14), pts[-1][1] + r.uniform(-12, 12)))
        d.line(pts, fill=255, width=3)
    crack = np.asarray(cr, np.float32) / 255
    out = compose(W, H, m, (230, 255, 240), (120, 240, 170), (20, 120, 80), (4, 14, 10), (60, 230, 150), 16, 41, rough=0.3)
    out[..., :3] = out[..., :3] * (1 - 0.8 * (crack * m)[..., None])
    sp = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(sp)
    for _ in range(40):
        x, y = r.uniform(80, W - 80), r.uniform(10, 70)
        s = r.uniform(2, 6)
        d.ellipse([x - s, y - s * 1.6, x + s, y + s], fill=int(r.uniform(120, 255)))
    spa = np.asarray(sp.filter(ImageFilter.GaussianBlur(1.6)), np.float32) / 255
    for k, c in enumerate((0.55, 1.0, 0.75)):
        out[..., k] = out[..., k] * (1 - spa) + c * spa
    out[..., 3] = np.maximum(out[..., 3], spa)
    save(out, 'ult_title_warlock.png')


if __name__ == '__main__':
    warlock()
    cantor()
    blood()
    dragon()
    void()
    blade()
