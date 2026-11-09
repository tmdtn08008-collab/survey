#!/usr/bin/env python3
"""마검사 텍스처: 검날 / 룬 빛 / 금속 (검 모델용)."""
import os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..')
FX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures', 'fx')


def save(a, name):
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'RGBA').save(os.path.join(FX, name))


def steel():
    W, H = 16, 64
    u = (np.arange(W) + 0.5) / W
    v = (np.arange(H) + 0.5) / H
    uu, vv = np.meshgrid(u, v)
    rng = np.random.RandomState(3)
    n = rng.rand(H, W) * 0.06
    edge = np.clip(1 - np.minimum(uu, 1 - uu) * 6, 0, 1)          # 날 가장자리 밝게
    fuller = np.exp(-((uu - 0.5) / 0.12) ** 2)                      # 가운데 홈 어둡게
    out = np.zeros((H, W, 4), np.float32)
    base = np.array([0.20, 0.17, 0.27])
    for k in range(3):
        out[..., k] = base[k] * (1 - 0.55 * fuller) + edge * (0.55, 0.5, 0.68)[k] + n
    out[..., 3] = 1
    save(out, 'blade_steel.png')


def runes():
    W, H = 16, 64
    img = Image.new('L', (W * 4, H * 4), 0)
    d = ImageDraw.Draw(img)
    r = random.Random(11)
    y = 6
    while y < H * 4 - 10:
        # 가운데 줄에 각진 룬
        cx = W * 2
        kind = r.randint(0, 3)
        s = 7
        if kind == 0:
            d.line([(cx - s, y), (cx + s, y + 2 * s)], fill=255, width=3); d.line([(cx + s, y), (cx - s, y + 2 * s)], fill=255, width=3)
        elif kind == 1:
            d.line([(cx, y), (cx, y + 2 * s)], fill=255, width=3); d.line([(cx - s, y + s), (cx + s, y + s // 2)], fill=255, width=3)
        elif kind == 2:
            d.polygon([(cx, y), (cx + s, y + s), (cx, y + 2 * s), (cx - s, y + s)], outline=255, width=3)
        else:
            d.line([(cx - s, y), (cx, y + s), (cx + s, y), (cx, y + 2 * s)], fill=255, width=3)
        y += 2 * s + 8
    g = np.asarray(img.filter(ImageFilter.GaussianBlur(1.2)).resize((W, H), Image.LANCZOS), np.float32) / 255
    # 날 가장자리의 빛줄기
    u = (np.arange(W) + 0.5) / W
    e = np.clip(1 - np.minimum(u, 1 - u) * 10, 0, 1)[None, :] * np.ones((H, 1))
    a = np.clip(g * 1.4 + e * 0.8, 0, 1)
    out = np.zeros((H, W, 4), np.float32)
    for k, c in enumerate((0.78, 0.4, 1.0)):
        out[..., k] = c * a
    out[..., 3] = a
    save(out, 'blade_runes.png')


def metal():
    S = 16
    rng = np.random.RandomState(5)
    n = rng.rand(S, S) * 0.25 + 0.75
    out = np.zeros((S, S, 4), np.float32)
    for k in range(3):
        out[..., k] = n
    out[..., 3] = 1
    save(out, 'blade_metal.png')


if __name__ == '__main__':
    steel(); runes(); metal()
    print('done')
