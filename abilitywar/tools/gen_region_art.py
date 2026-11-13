"""2.11 테마 지역 그림: 던전 입구 소용돌이 (움직이는 16x16 × 32장) + 거미 여왕 (거미 모델 64x32)."""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures')


def gate():
    frames = 32
    img = Image.new('RGBA', (16, 16 * frames))
    rnd = random.Random(7)
    stars = [(rnd.uniform(0, 16), rnd.uniform(0, 16), rnd.uniform(0, 1)) for _ in range(14)]
    for f in range(frames):
        t = f / frames
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                # 안쪽으로 감기는 소용돌이 팔 셋
                v = 0.5 + 0.5 * math.sin(a * 3 + r * 0.85 - t * 2 * math.pi)
                v2 = 0.5 + 0.5 * math.sin(a * 2 - r * 0.5 + t * 4 * math.pi)
                core = max(0.0, 1 - r / 9)
                k = 0.55 * v + 0.25 * v2 + 0.45 * core
                k = max(0.0, min(1.0, k))
                col = (int(24 + 150 * k ** 1.3), int(8 + 70 * k ** 2), int(48 + 207 * k), int(170 + 70 * k))
                img.putpixel((x, f * 16 + y), col)
        # 반짝이 (안쪽으로 빨려 들어감)
        for sx, sy, ph in stars:
            p = (ph + t) % 1.0
            px = 7.5 + (sx - 7.5) * (1 - p)
            py = 7.5 + (sy - 7.5) * (1 - p)
            ix, iy = int(px), int(py)
            if 0 <= ix < 16 and 0 <= iy < 16:
                img.putpixel((ix, f * 16 + iy), (255, 236, 255, 255))
    out = os.path.join(ROOT, 'block', 'dungeon_gate.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    with open(out + '.mcmeta', 'w') as fp:
        json.dump({'animation': {'frametime': 2, 'interpolate': True}}, fp)
    print('wrote', out)


def queen():
    rnd = random.Random(11)
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(64):
            n = rnd.random()
            img.putpixel((x, y), (int(26 + 22 * n), int(14 + 12 * n), int(32 + 26 * n), 255))

    def box(u, v, w, h, d, fn):
        faces = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
                 'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
        for name, (x0, y0, fw, fh) in faces.items():
            for yy in range(fh):
                for xx in range(fw):
                    c = fn(name, xx, yy, fw, fh)
                    if c is not None and 0 <= x0 + xx < 64 and 0 <= y0 + yy < 32:
                        img.putpixel((x0 + xx, y0 + yy), c)

    # 배 (body1): 등에 붉은 모래시계 + 테두리 줄무늬
    def body(face, x, y, w, h):
        if face == 'top':
            cx, cy = (w - 1) / 2, (h - 1) / 2
            ax = abs(x - cx)
            hour = ax <= 0.6 + abs(y - cy) * 0.55 and abs(y - cy) <= 4.2
            if hour:
                return (200 + rnd.randint(0, 40), 20, 30, 255)
            if x in (0, w - 1) or y in (0, h - 1):
                return (90, 30, 70, 255)
        if face in ('left', 'right', 'back') and y % 3 == 0:
            return (70, 24, 56, 255)
        return None
    box(0, 12, 10, 8, 12, body)

    # 머리: 앞에 붉은 눈 여덟
    def head(face, x, y, w, h):
        if face == 'front' and (x, y) in [(1, 2), (2, 2), (5, 2), (6, 2), (2, 4), (5, 4), (0, 3), (7, 3)]:
            return (255, 60, 60, 255)
        if face == 'front' and y >= 6 and x in (2, 5):
            return (200, 200, 190, 255)  # 송곳니
        return None
    box(32, 4, 8, 8, 8, head)

    # 다리: 검정 + 보라 띠
    def leg(face, x, y, w, h):
        if face in ('top', 'front', 'back', 'bottom') and x % 5 == 4:
            return (120, 40, 90, 255)
        return (20, 12, 22, 255) if x % 5 != 4 else None
    box(18, 0, 16, 2, 2, leg)
    out = os.path.join(ROOT, 'entity', 'spider_queen.png')
    img.save(out)
    print('wrote', out)


if __name__ == '__main__':
    gate()
    queen()
