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


if __name__ == '__main__':
    dragon()
    void()
