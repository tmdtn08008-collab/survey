#!/usr/bin/env python3
"""
2.9 야생 생명체 그림:
  textures/entity/<몹>.png — 사람 모양 (64x64), 인어 (64x64 꼬리), 흉포한 늑대 (64x32 늑대 뼈대), 골렘 (128x128 철 골렘 뼈대 + 빛)
  textures/fx/elder_*_azure.png (용왕) · elder_*_jade.png (비룡) — 고룡 그림의 색을 바꿈
  textures/item/<재료>.png (16x16) + 아이템 모델, 생성 알 모델
사용: python3 tools/gen_wild_art.py
"""
import colorsys
import json
import math
import os
import random

from PIL import Image

import gen_mob_art as M
from gen_mob_art import Skin, biped

ASSETS = M.ASSETS
FX = os.path.join(ASSETS, 'textures', 'fx')
ITEM = os.path.join(ASSETS, 'textures', 'item')
MODELS = os.path.join(ASSETS, 'models', 'item')


def hat(s, c, front=None, noise=8):
    """모자 층 (머리 위 0.5 두께, (32,0) 8x8x8)."""
    s.box(32, 0, 8, 8, 8, c, front=front, noise=noise)


def clear(s, x, y, w, h):
    for i in range(x, x + w):
        for j in range(y, y + h):
            s.px[i, j] = (0, 0, 0, 0)


def eyes(s, col, y=11, xs=(9, 13), w=2, h=1):
    for x in xs:
        s.rect(x, y, w, h, col, 0)


# ================================================================ 사람 모양
def goblin_lord():
    s = Skin(seed=41)
    SK, SKD = (84, 132, 52), (60, 98, 38)
    GOLD, GOLDD, RED = (230, 186, 60), (170, 126, 30), (150, 28, 30)
    biped(s, SK, (90, 60, 34), (60, 40, 24), (40, 28, 18), sleeve=SK)
    s.rect(8, 8, 8, 2, (34, 40, 20))
    for x in (9, 13):
        s.rect(x, 11, 2, 2, (255, 200, 30), 0)
        s.dot(x + (1 if x == 9 else 0), 12, (30, 10, 0))
    s.rect(11, 13, 2, 1, SKD, 0)
    s.rect(10, 14, 4, 1, (40, 18, 8), 0)
    s.dot(10, 15, (250, 250, 230)); s.dot(13, 15, (250, 250, 230))
    for u in (0, 16):
        s.rect(u + 2, 10, 4, 3, SKD, 4)
        s.rect(u + 1, 9, 2, 2, SKD, 4)
    # 금 왕관 (모자 층: 위 둘레 3줄 + 뾰족)
    for (u, v, w) in [(32, 8, 8), (40, 8, 8), (48, 8, 8), (56, 8, 8)]:
        s.rect(u, v, w, 2, GOLD, 10)
        for x in range(u, u + w, 2):
            s.dot(x, v + 2, GOLDD)
    for x in (41, 44, 46):
        s.dot(x, 9, RED)
    s.dot(43, 9, (60, 180, 255))
    s.rect(40, 0, 8, 8, (0, 0, 0), 0)
    clear(s, 40, 0, 8, 8)
    # 금빛 가슴판 + 붉은 망토 (뒤)
    s.rect(21, 20, 6, 5, GOLD, 12)
    s.rect(23, 21, 2, 3, RED, 4)
    s.rect(32, 20, 8, 12, RED, 10)
    s.rect(16, 20, 4, 2, (200, 190, 170), 6)
    s.rect(28, 20, 4, 2, (200, 190, 170), 6)
    s.rect(20, 28, 8, 1, GOLDD, 3)
    s.save('goblin_lord')


def orc_lord():
    s = Skin(seed=42)
    SK, SKD = (96, 112, 72), (70, 84, 52)
    IRON, IROND, BONE, RED = (90, 92, 100), (60, 62, 70), (226, 220, 196), (170, 24, 24)
    biped(s, SK, (64, 46, 30), (48, 36, 26), (30, 22, 16), sleeve=SK)
    for x in (9, 13):
        s.rect(x, 11, 2, 1, (255, 60, 30), 0)
    s.rect(9, 10, 6, 1, SKD, 0)
    s.rect(10, 13, 4, 1, (50, 30, 24), 0)
    s.rect(9, 14, 1, 2, BONE, 0); s.rect(14, 14, 1, 2, BONE, 0)
    # 붉은 전쟁 칠
    for y in range(9, 14):
        s.dot(10, y, RED); s.dot(13, y, RED)
    # 쇠 투구 (모자 층) + 뿔 자국
    for (u, v) in [(32, 8), (40, 8), (48, 8), (56, 8)]:
        s.rect(u, v, 8, 3, IRON, 8)
    s.rect(40, 0, 8, 8, IROND, 6)
    s.rect(48, 0, 8, 8, IROND, 6)
    for (x, y) in [(32, 8), (39, 8), (48, 8), (55, 8)]:
        s.rect(x, y, 1, 3, BONE, 0)
    # 해골 어깨받이 + 사슬 가슴띠
    for u in (40, 48):
        s.rect(u, 16, 4, 4, BONE, 6)
        s.rect(u + 1, 17, 1, 1, (30, 20, 20), 0); s.rect(u + 2, 17, 1, 1, (30, 20, 20), 0)
    s.rect(44, 20, 4, 4, BONE, 8)
    for i in range(8):
        s.dot(20 + i, 20 + i // 2 * 2 % 12, IRON)
        s.dot(20 + i, 21 + i, IRON)
    s.rect(20, 27, 8, 1, IRON, 4)
    s.rect(32, 20, 8, 8, (90, 70, 50), 14)  # 털 망토
    s.save('orc_lord')


def bandit():
    s = Skin(seed=43)
    SK, LEA, LEAD, RED = (196, 150, 112), (110, 76, 46), (80, 54, 32), (150, 30, 34)
    biped(s, SK, LEA, (70, 58, 46), (40, 30, 22), sleeve=LEA)
    eyes(s, (40, 30, 20))
    s.rect(9, 10, 6, 1, (90, 60, 40), 0)
    s.rect(8, 13, 8, 3, RED, 8)  # 두건 마스크 (얼굴 아래)
    for u in (0, 16, 24):
        s.rect(u, 13, 8, 3, RED, 8)
    # 갈색 두건 (모자 층)
    for (u, v) in [(32, 8), (48, 8), (56, 8)]:
        s.rect(u, v, 8, 8, LEAD, 10)
    s.rect(40, 0, 8, 8, LEAD, 10)
    s.rect(40, 8, 8, 2, LEAD, 10)
    # 가죽 갑옷 띠 + 칼집
    for i in range(8):
        s.dot(20 + i, 20 + i, (60, 40, 24))
    s.rect(20, 27, 8, 1, (40, 28, 16), 3)
    s.dot(24, 27, (200, 180, 90))
    s.save('bandit')


def thief():
    s = Skin(seed=44)
    SK, CL, CLD = (200, 160, 128), (44, 44, 52), (28, 28, 34)
    biped(s, SK, CL, CLD, (20, 20, 24), sleeve=CL)
    s.rect(8, 8, 8, 8, CLD, 4)  # 얼굴은 복면
    s.rect(9, 11, 6, 2, SK, 4)  # 눈 틈
    s.dot(10, 11, (30, 120, 60)); s.dot(13, 11, (30, 120, 60))
    for u in (0, 16, 24):
        s.rect(u, 8, 8, 8, CLD, 4)
    # 후드 (모자 층)
    for (u, v) in [(32, 8), (48, 8), (56, 8)]:
        s.rect(u, v, 8, 8, CL, 8)
    s.rect(40, 0, 8, 8, CL, 8)
    s.rect(40, 8, 8, 2, CL, 8)
    s.rect(40, 10, 1, 6, CL, 8); s.rect(47, 10, 1, 6, CL, 8)
    # 허리 주머니 띠 + 단검 끈
    s.rect(20, 27, 8, 1, (90, 64, 40), 3)
    s.rect(21, 28, 2, 2, (110, 80, 50), 3)
    s.rect(25, 28, 2, 2, (110, 80, 50), 3)
    for i in range(10):
        s.dot(27 - i // 2 * 1, 20 + i, (70, 50, 30))
    s.rect(32, 20, 8, 10, CLD, 6)  # 짧은 망토
    s.save('thief')


def pirate():
    s = Skin(seed=45)
    SK, COAT, COATD, SHIRT, SASH = (186, 132, 96), (36, 46, 96), (24, 30, 66), (220, 214, 196), (170, 30, 30)
    GOLD, BLACK = (220, 180, 70), (24, 22, 26)
    biped(s, SK, COAT, (70, 56, 44), (24, 20, 18), sleeve=COAT)
    s.rect(8, 8, 8, 2, (50, 34, 24))
    s.dot(13, 11, (30, 20, 14)); s.dot(14, 11, (30, 20, 14))  # 눈
    s.rect(9, 11, 3, 2, BLACK, 0)  # 안대
    for x in range(8, 16):
        s.dot(x, 10, BLACK) if x < 12 else None
    s.rect(9, 13, 6, 3, (60, 36, 22), 8)  # 수염
    s.rect(11, 13, 2, 1, (120, 60, 50), 0)
    # 삼각 모자 (모자 층: 위 + 둘레 넓게, 금 테)
    s.rect(40, 0, 8, 8, BLACK, 4)
    for (u, v) in [(32, 8), (40, 8), (48, 8), (56, 8)]:
        s.rect(u, v, 8, 3, BLACK, 4)
        s.rect(u, v + 2, 8, 1, GOLD, 6)
    s.rect(43, 9, 2, 1, (240, 240, 230), 0)  # 해골 표시
    # 흰 셔츠 앞 + 붉은 허리띠 + 금 단추
    s.rect(22, 20, 4, 8, SHIRT, 6)
    s.rect(20, 27, 8, 2, SASH, 6)
    for y in (21, 23, 25):
        s.dot(21, y, GOLD); s.dot(26, y, GOLD)
    s.rect(44, 28, 4, 4, SK, 6)  # 손
    s.rect(40, 26, 16, 1, GOLD, 4)  # 소매 끝 금 테
    s.save('pirate')


def cultist():
    s = Skin(seed=46)
    ROBE, ROBED, GOLD, EYE = (52, 20, 64), (34, 12, 42), (210, 170, 60), (220, 90, 255)
    biped(s, ROBED, ROBE, ROBE, ROBED, sleeve=ROBE)
    s.rect(8, 8, 8, 8, (26, 14, 30), 6)  # 그늘진 얼굴
    eyes(s, EYE)
    s.rect(10, 14, 4, 1, (60, 30, 60), 0)
    # 두건 (모자 층)
    for (u, v) in [(32, 8), (48, 8), (56, 8)]:
        s.rect(u, v, 8, 8, ROBE, 8)
    s.rect(40, 0, 8, 8, ROBE, 8)
    s.rect(40, 8, 8, 1, ROBE, 8)
    s.rect(40, 9, 1, 7, ROBE, 8); s.rect(47, 9, 1, 7, ROBE, 8)
    s.rect(44, 0, 0, 0, ROBE)
    # 가슴 금 인장 (눈 모양) + 가운데 줄
    s.rect(23, 21, 2, 1, GOLD, 0)
    s.rect(22, 22, 4, 1, GOLD, 0)
    s.dot(23, 22, (200, 40, 40)); s.dot(24, 22, (200, 40, 40))
    s.rect(23, 23, 2, 1, GOLD, 0)
    s.rect(23, 24, 2, 8, ROBED, 3)
    for (u, v) in [(4, 20), (8, 20), (12, 20)]:
        s.rect(u, v + 8, 4, 4, ROBED, 4)
    s.save('cultist')


def wraith():
    s = Skin(seed=47)
    A = 150
    PALE, PALED = (196, 222, 236), (130, 164, 190)

    def r(x, y, w, h, c, a=A, noise=10):
        s.rect(x, y, w, h, c, noise, a)

    for (u, v, w, h, d) in [(0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4)]:
        for (fx, fy), (fw, fh) in [((u + d, v), (w, d)), ((u + d + w, v), (w, d)), ((u, v + d), (d, h)), ((u + d, v + d), (w, h)),
                                   ((u + d + w, v + d), (d, h)), ((u + 2 * d + w, v + d), (w, h))]:
            r(fx, fy, fw, fh, PALE)
    # 아래로 갈수록 흐려지는 몸 (너덜너덜)
    for x in range(16, 40):
        for y in range(26, 32):
            if (x * 7 + y * 3) % 5 < y - 25:
                s.px[x, y] = (0, 0, 0, 0)
            else:
                p = s.px[x, y]
                s.px[x, y] = (p[0], p[1], p[2], max(40, A - (y - 24) * 18))
    # 다리는 없음 (투명)
    clear(s, 0, 16, 16, 16)
    # 텅 빈 눈 + 벌린 입
    for x in (9, 13):
        r(x, 10, 2, 3, (10, 20, 34), 230, 0)
    r(11, 14, 2, 2, (10, 20, 34), 220, 0)
    # 머리카락 (모자 층, 흘러내림)
    for (u, v) in [(32, 8), (48, 8), (56, 8)]:
        r(u, v, 8, 8, PALED, 120)
    r(40, 0, 8, 8, PALED, 120)
    r(40, 8, 8, 1, PALED, 120)
    s.save('wraith')


def fairy():
    s = Skin(seed=48)
    SK, DRESS, DRESSD, HAIR = (252, 222, 210), (120, 200, 120), (80, 160, 90), (255, 214, 110)
    biped(s, SK, DRESS, SK, (240, 140, 200), sleeve=SK)
    for x in (9, 13):
        s.rect(x, 11, 2, 2, (60, 160, 255), 0)
        s.dot(x, 11, (255, 255, 255))
    s.dot(11, 14, (240, 120, 140)); s.dot(12, 14, (240, 120, 140))
    s.dot(9, 13, (255, 170, 180)); s.dot(14, 13, (255, 170, 180))
    for u in (0, 8, 16, 24):
        s.rect(u, 8, 8, 2, HAIR, 8)
    s.rect(8, 0, 8, 8, HAIR, 8)
    for (u, v) in [(32, 8), (48, 8), (56, 8)]:
        s.rect(u, v, 8, 6, HAIR, 10)
    s.rect(40, 0, 8, 8, HAIR, 10)
    s.rect(40, 8, 8, 1, HAIR, 10)
    for x in (41, 43, 45):
        s.dot(x, 8, (255, 120, 200))  # 꽃 화관
    # 꽃잎 치마
    for (u, v) in [(16, 26), (20, 26), (28, 26), (32, 26)]:
        s.rect(u, v, 4 if u in (16, 28) else 8, 6, (250, 150, 210), 12)
    s.rect(20, 20, 8, 6, DRESS, 10)
    s.rect(22, 22, 4, 2, DRESSD, 4)
    s.save('fairy')


# ================================================================ 인어 (64x64: 꼬리 (0,32) 7x6x4 · (0,44) 5x6x3 · 지느러미 (24,44) 10x5x1)
def mermaid():
    s = Skin(seed=49)
    SK, HAIR, HAIRD = (234, 196, 170), (40, 150, 150), (24, 110, 116)
    TAIL, TAILD, FIN, SHELL = (40, 170, 160), (26, 120, 120), (120, 230, 220), (240, 160, 190)
    s.box(0, 0, 8, 8, 8, SK)
    s.box(16, 16, 8, 12, 4, SK)
    s.box(40, 16, 4, 12, 4, SK)
    for x in (9, 13):
        s.rect(x, 11, 2, 2, (30, 120, 200), 0)
        s.dot(x, 11, (230, 250, 255))
    s.rect(11, 14, 2, 1, (210, 110, 120), 0)
    for u in (0, 16, 24):
        s.rect(u, 8, 8, 8, HAIR, 12)
    s.rect(8, 8, 8, 2, HAIR, 10)
    s.rect(8, 0, 8, 8, HAIR, 10)
    for (u, v) in [(32, 8), (48, 8), (56, 8)]:
        s.rect(u, v, 8, 8, HAIRD, 12)
    s.rect(40, 0, 8, 8, HAIRD, 12)
    s.rect(40, 8, 8, 1, HAIRD, 12)
    # 조개 윗옷 + 진주 목걸이
    s.rect(20, 22, 3, 2, SHELL, 8)
    s.rect(25, 22, 3, 2, SHELL, 8)
    for x in range(20, 28, 2):
        s.dot(x, 21, (250, 250, 240))
    # 허리 비늘 시작
    s.rect(16, 30, 24, 2, TAIL, 10)

    def scales(u, v, w, h, d, c, cd):
        s.box(u, v, w, h, d, c, noise=10)
        for (fx, fy), (fw, fh) in [((u, v + d), (d, h)), ((u + d, v + d), (w, h)), ((u + d + w, v + d), (d, h)), ((u + 2 * d + w, v + d), (w, h))]:
            for y in range(fy, fy + fh):
                for x in range(fx, fx + fw):
                    if (x + (y // 2) % 2) % 2 == 0 and y % 2 == 0:
                        s.dot(x, y, cd)

    scales(0, 32, 7, 6, 4, TAIL, TAILD)
    scales(0, 44, 5, 6, 3, TAIL, TAILD)
    s.box(24, 44, 10, 5, 1, FIN, noise=14)
    for x in range(25, 35, 2):
        for y in range(45, 50):
            s.dot(x, y, (170, 250, 240))
    s.save('mermaid')


# ================================================================ 흉포한 늑대 (64x32 늑대 뼈대)
def dire_wolf():
    s = Skin(64, 32, seed=50)
    FUR, FURD, BELLY = (58, 56, 62), (36, 34, 40), (90, 86, 90)
    s.box(0, 0, 6, 6, 4, FUR, noise=12)         # 머리
    s.box(16, 14, 2, 2, 1, FURD, noise=6)       # 귀
    s.box(0, 10, 3, 3, 4, FURD, noise=8)        # 주둥이
    s.box(18, 14, 6, 9, 6, FUR, noise=14)       # 몸
    s.box(21, 0, 8, 6, 7, FURD, noise=16)       # 갈기
    s.box(0, 18, 2, 8, 2, FUR, noise=10)        # 다리
    s.box(9, 18, 2, 8, 2, FURD, noise=12)       # 꼬리
    # 붉은 눈 + 흉터
    s.dot(4, 6, (255, 40, 30)); s.dot(9, 6, (255, 40, 30))
    s.dot(5, 6, (120, 10, 10)); s.dot(8, 6, (120, 10, 10))
    s.rect(4, 14, 3, 1, (220, 220, 210), 0)    # 이빨 (주둥이 앞)
    for y in range(4, 9):
        s.dot(6, y, (120, 110, 110)) if y % 2 else None
    s.rect(24, 20, 6, 3, BELLY, 8)
    s.save('dire_wolf')


# ================================================================ 골렘 (128x128 철 골렘 뼈대)
GOLEM_BOXES = [(0, 0, 8, 10, 8), (24, 0, 2, 4, 2), (0, 40, 18, 12, 11), (0, 70, 9, 5, 6), (60, 21, 4, 30, 6), (60, 58, 4, 30, 6), (37, 0, 6, 16, 5), (60, 0, 6, 16, 5)]


def golem(name, base, dark, accent, rune, seed, moss=None):
    s = Skin(128, 128, seed=seed)
    g = Skin(128, 128, seed=seed + 1)
    for (u, v, w, h, d) in GOLEM_BOXES:
        s.box(u, v, w, h, d, base, top=dark, noise=12)
        for face, (fx, fy) in s.faces(u, v, w, h, d).items():
            fw = w if face in ('front', 'back', 'top', 'bottom') else d
            fh = d if face in ('top', 'bottom') else h
            # 이끼 / 금 테
            if moss:
                for _ in range(max(1, fw * fh // 14)):
                    x, y = fx + s.r.randrange(fw), fy + s.r.randrange(fh)
                    s.rect(x, y, min(2, fx + fw - x), 1, moss, 14)
            # 룬 선 (빛남)
            if face in ('front', 'back') and fw >= 4 and fh >= 6:
                x0 = fx + fw // 2
                for y in range(fy + 1, fy + fh - 1, 3):
                    for x in (x0 - 1, x0):
                        s.dot(x, y, rune); g.dot(x, y, rune)
                if fh >= 10:
                    for x in range(fx + 1, fx + fw - 1):
                        y = fy + fh // 2
                        s.dot(x, y, accent)
                        if x % 2 == 0:
                            g.dot(x, y, accent)
    # 눈 (머리 앞면 (8, 8) 8x10)
    for x in (9, 13):
        s.rect(x, 12, 2, 2, rune, 0)
        g.rect(x, 12, 2, 2, rune, 0)
    s.rect(9, 11, 6, 1, dark, 4)
    # 가슴 핵
    s.rect(17, 55, 4, 4, rune, 6)
    g.rect(17, 55, 4, 4, rune, 6)
    s.save(name)
    g.save(name + '_glow')


# ================================================================ 고룡 색 바꾸기
def hue_shift(src, dst, hue, sat=1.0, val=1.0, gold_belly=False):
    im = Image.open(os.path.join(FX, src + '.png')).convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s_, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            h = (h + hue) % 1.0 if not gold_belly else 0.12 + (h % 0.08)
            s_ = min(1, s_ * sat)
            v = min(1, v * val)
            rr, gg, bb = colorsys.hsv_to_rgb(h, s_, v)
            px[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)
    im.save(os.path.join(FX, dst + '.png'))


def dragons():
    for part in ('scales', 'membrane'):
        hue_shift('elder_' + part, 'elder_' + part + '_azure', 0.60, 1.0, 1.25)
        hue_shift('elder_' + part, 'elder_' + part + '_jade', 0.33, 0.9, 1.1)
    for part in ('glow', 'membrane_glow'):
        hue_shift('elder_' + part, 'elder_' + part + '_azure', 0.52, 1.0, 1.0)
        hue_shift('elder_' + part, 'elder_' + part + '_jade', 0.22, 1.0, 1.0)
    hue_shift('elder_belly', 'elder_belly_azure', 0, 1.1, 1.3, gold_belly=True)
    hue_shift('elder_belly', 'elder_belly_jade', 0.10, 0.8, 1.15)
    hue_shift('elder_horn', 'elder_horn_azure', 0, 1.6, 1.05, gold_belly=True)
    hue_shift('elder_horn', 'elder_horn_jade', 0, 1.0, 0.95)


# ================================================================ 아이템 그림 (16x16)
class Icon:
    def __init__(self, seed):
        self.im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        self.px = self.im.load()
        self.r = random.Random(seed)

    def put(self, x, y, c, a=255):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[x, y] = (max(0, min(255, c[0])), max(0, min(255, c[1])), max(0, min(255, c[2])), a)

    def shape(self, inside, light, dark, mid=None, outline=(20, 16, 20)):
        """inside(x,y) → 0..1 (중심에서 가까울수록 1). 왼쪽 위가 밝게."""
        mask = [[inside(x + 0.5, y + 0.5) for x in range(16)] for y in range(16)]
        for y in range(16):
            for x in range(16):
                if mask[y][x] <= 0:
                    near = any(0 <= x + dx < 16 and 0 <= y + dy < 16 and mask[y + dy][x + dx] > 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                    if near and outline:
                        self.put(x, y, outline)
                    continue
                k = min(1, max(0, (16 - x - y) / 22 + mask[y][x] * 0.35))
                c = [int(dark[i] + (light[i] - dark[i]) * k) for i in range(3)]
                n = self.r.randint(-6, 6)
                self.put(x, y, (c[0] + n, c[1] + n, c[2] + n))

    def save(self, name):
        os.makedirs(ITEM, exist_ok=True)
        self.im.save(os.path.join(ITEM, name + '.png'))
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'abilitywar:item/' + name}}, open(os.path.join(MODELS, name + '.json'), 'w'), indent=1)


def circle(cx, cy, r):
    return lambda x, y: 1 - math.hypot(x - cx, y - cy) / r


def drop(cx, cy, r):
    def f(x, y):
        dy = y - cy
        if dy < 0:
            w = r * (1 + dy / (r * 2.2))
            return 1 - abs(x - cx) / max(0.1, w) if w > 0 and dy > -r * 2.2 else -1
        return 1 - math.hypot(x - cx, dy) / r
    return f


def icons():
    i = Icon(1)  # 흉포한 송곳니: 휜 이빨
    i.shape(lambda x, y: 1 - abs(math.hypot(x - 3, y - 14) - 10) / (2.2 - max(0, (x - 3) / 9) * 1.6) if 2 < x < 13 and y < 14 else -1,
            (250, 246, 230), (170, 160, 140))
    i.save('dire_fang')
    i = Icon(2)  # 도적단의 인장: 붉은 밀랍 + 단검
    i.shape(circle(8, 8, 6.5), (210, 60, 50), (110, 20, 20))
    for k in range(-3, 4):
        i.put(8 + k, 8 - k, (230, 220, 200))
    i.put(5, 9, (120, 90, 40)); i.put(6, 10, (120, 90, 40))
    i.save('bandit_seal')
    i = Icon(3)  # 해적 금화: 금 동전 + 해골
    i.shape(circle(8, 8, 6.8), (255, 230, 120), (180, 120, 30), outline=(90, 60, 10))
    for (x, y) in [(7, 6), (9, 6), (6, 7), (10, 7), (7, 9), (8, 9), (9, 9), (8, 10)]:
        i.put(x, y, (120, 80, 20))
    i.save('pirate_doubloon')
    i = Icon(4)  # 광신도의 인장: 보랏빛 오각 + 눈
    i.shape(lambda x, y: 1 - max(abs(x - 8) * 0.95 + (y - 8) * 0.3, abs(y - 8) * 1.05) / 6.5, (150, 70, 200), (40, 10, 60))
    for x in range(6, 11):
        i.put(x, 8, (240, 210, 90))
    i.put(8, 8, (220, 40, 40)); i.put(7, 7, (240, 210, 90)); i.put(9, 7, (240, 210, 90)); i.put(7, 9, (240, 210, 90)); i.put(9, 9, (240, 210, 90))
    i.save('cult_sigil')
    i = Icon(5)  # 원혼의 정수: 푸른 혼불
    i.shape(drop(8, 10, 4.6), (230, 250, 255), (80, 150, 200), outline=(20, 50, 80))
    i.put(7, 9, (20, 40, 60)); i.put(9, 9, (20, 40, 60))
    i.save('wraith_essence')
    i = Icon(6)  # 요정 가루: 분홍 반짝이 무더기
    i.shape(lambda x, y: 1 - math.hypot((x - 8) / 6.5, (y - 12) / 3.2) if y > 8 else -1, (255, 200, 240), (200, 90, 180))
    for (x, y) in [(4, 5), (8, 3), (12, 6), (6, 8), (10, 8)]:
        i.put(x, y, (255, 255, 255)); i.put(x + 1, y, (255, 220, 250)); i.put(x - 1, y, (255, 220, 250)); i.put(x, y + 1, (255, 220, 250)); i.put(x, y - 1, (255, 220, 250))
    i.save('fairy_dust')
    i = Icon(7)  # 인어의 눈물: 청록 진주 방울
    i.shape(drop(8, 10, 4.8), (220, 255, 250), (40, 170, 170), outline=(10, 60, 70))
    i.put(6, 8, (255, 255, 255)); i.put(6, 9, (255, 255, 255))
    i.save('mermaid_tear')
    i = Icon(8)  # 비룡 비늘: 녹색 비늘
    i.shape(drop(8, 9, 6), (170, 240, 120), (40, 110, 50), outline=(10, 40, 20))
    for y in range(5, 14, 3):
        for x in range(4, 13):
            if (x + y) % 3 == 0:
                i.put(x, y, (30, 90, 40))
    i.save('drake_scale')
    i = Icon(9)  # 골렘 핵: 돌 구슬 + 청록 룬
    i.shape(circle(8, 8, 6.6), (170, 176, 170), (70, 76, 72))
    for (x, y) in [(8, 4), (8, 5), (8, 6), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (8, 10), (8, 11), (6, 6), (10, 10)]:
        i.put(x, y, (80, 255, 210))
    i.save('golem_core')
    i = Icon(10)  # 군주의 증표: 금 방패 + 붉은 띠
    i.shape(lambda x, y: (1 - abs(x - 8) / 6.2) if y < 8 else 1 - math.hypot((x - 8) / 6.2, (y - 8) / 6.5), (255, 220, 110), (170, 110, 30), outline=(70, 40, 10))
    for y in range(3, 14):
        i.put(8, y, (180, 30, 30)); i.put(7, y, (210, 50, 40))
    i.save('lord_emblem')
    i = Icon(11)  # 용왕의 역린: 푸른 비늘 + 금 테
    i.shape(drop(8, 9, 6.2), (150, 220, 255), (20, 60, 160), outline=(230, 180, 60))
    for y in range(5, 14, 3):
        for x in range(4, 13):
            if (x + y) % 3 == 0:
                i.put(x, y, (30, 50, 130))
    i.put(8, 11, (255, 230, 120)); i.put(7, 10, (255, 230, 120))
    i.save('dragon_king_scale')


def dungeon_guide():
    """던전 안내인 (플레이어 모델 64x64): 흉터 있는 늙은 모험가, 가죽 외투 · 붉은 목도리 · 지도 통 · 등불 벨트."""
    s = Skin(seed=60)
    SK, HAIR, COAT, COATD, SCARF, BELT, BOOT = (176, 130, 100), (150, 150, 150), (92, 64, 40), (64, 44, 28), (150, 32, 30), (52, 36, 22), (40, 30, 22)
    s.box(0, 0, 8, 8, 8, SK)
    s.rect(8, 8, 8, 2, HAIR, 6)
    for u in (0, 16, 24):
        s.rect(u, 8, 8, 3, HAIR, 8)
    s.rect(8, 0, 8, 8, HAIR, 8)
    for x in (9, 13):
        s.rect(x, 11, 2, 1, (40, 30, 24), 0)
    s.dot(12, 10, (120, 70, 60)); s.dot(12, 11, (120, 70, 60)); s.dot(12, 12, (120, 70, 60))  # 흉터
    s.rect(9, 13, 6, 3, (110, 104, 100), 6)  # 수염
    s.rect(11, 13, 2, 1, (90, 50, 40), 0)
    hat(s, (60, 44, 30))
    s.rect(40, 0, 8, 8, (70, 52, 34), 6)
    s.rect(40, 8, 8, 2, (60, 44, 30), 6)
    s.rect(40, 10, 8, 6, (0, 0, 0), 0)
    clear(s, 40, 10, 8, 6)
    for u in (32, 48, 56):
        clear(s, u, 10, 8, 6)
    # 외투 · 목도리 · 벨트
    s.box(16, 16, 8, 12, 4, COAT, front=COATD)
    s.rect(20, 20, 8, 2, SCARF, 8)
    s.rect(16, 20, 4, 2, SCARF, 8)
    s.rect(28, 20, 4, 2, SCARF, 8)
    s.rect(32, 20, 8, 2, SCARF, 8)
    s.rect(21, 22, 1, 6, SCARF, 6)
    s.rect(20, 27, 8, 1, BELT, 3)
    s.dot(24, 27, (230, 190, 70))
    s.rect(32, 21, 8, 8, (130, 100, 60), 8)  # 등의 지도 통
    for x in range(33, 39, 2):
        s.dot(x, 24, (90, 70, 40))
    # 팔 (오른 (40,16) · 왼 (32,48)) · 다리 (오른 (0,16) · 왼 (16,48))
    for (u, v) in ((40, 16), (32, 48)):
        s.box(u, v, 4, 12, 4, COAT)
        s.rect(u + 4, v + 14, 4, 2, SK, 4)
        s.rect(u, v + 12, 16, 1, COATD, 3)
    for (u, v) in ((0, 16), (16, 48)):
        s.box(u, v, 4, 12, 4, (70, 58, 44))
        for k in range(4):
            s.rect(u + k * 4, v + 13, 4, 3, BOOT, 4)
    s.save('dungeon_guide')


def eggs():
    for m in ['goblin_lord', 'orc_lord', 'bandit', 'thief', 'pirate', 'cultist', 'wraith', 'fairy', 'mermaid', 'dire_wolf', 'ancient_golem',
              'enhanced_golem', 'drake', 'dragon_king', 'dungeon_guide']:
        json.dump({'parent': 'minecraft:item/template_spawn_egg'}, open(os.path.join(MODELS, m + '_spawn_egg.json'), 'w'))


if __name__ == '__main__':
    goblin_lord(); orc_lord(); bandit(); thief(); pirate(); cultist(); wraith(); fairy(); mermaid(); dire_wolf()
    golem('ancient_golem', (112, 118, 104), (84, 90, 78), (150, 160, 140), (80, 255, 210), 51, moss=(70, 120, 50))
    golem('enhanced_golem', (34, 26, 44), (20, 14, 28), (255, 200, 90), (255, 170, 50), 53)
    dragons(); icons(); dungeon_guide(); eggs()
    print('done')
