#!/usr/bin/env python3
"""
몹 · 보스 그림: textures/entity/<몹>.png (사람 모양 64x64 / 화염 거인 128x128 골렘 모양) + 빛 텍스처,
보스 제단 블록 그림 + 모델 JSON, 생성 알 아이템 모델.
사용: python3 tools/gen_mob_art.py
"""
import json, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar')
ENT = os.path.join(ASSETS, 'textures', 'entity')
BLK = os.path.join(ASSETS, 'textures', 'block')


class Skin:
    def __init__(self, w=64, h=64, seed=1):
        self.im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.px = self.im.load()
        self.r = random.Random(seed)

    def rect(self, x, y, w, h, c, noise=8, a=255):
        for i in range(x, x + w):
            for j in range(y, y + h):
                n = self.r.randint(-noise, noise)
                self.px[i, j] = (max(0, min(255, c[0] + n)), max(0, min(255, c[1] + n)), max(0, min(255, c[2] + n)), a)

    def dot(self, x, y, c, a=255):
        self.px[x, y] = (c[0], c[1], c[2], a)

    def box(self, u, v, w, h, d, side, top=None, bottom=None, front=None, back=None, noise=8):
        """상자 UV (마인크래프트 순서): 위 (u+d, v), 아래 (u+d+w, v), 오른쪽 (u, v+d), 앞 (u+d, v+d), 왼쪽 (u+d+w, v+d), 뒤 (u+2d+w, v+d)."""
        self.rect(u + d, v, w, d, top or side, noise)
        self.rect(u + d + w, v, w, d, bottom or side, noise)
        self.rect(u, v + d, d, h, side, noise)
        self.rect(u + d, v + d, w, h, front or side, noise)
        self.rect(u + d + w, v + d, d, h, side, noise)
        self.rect(u + 2 * d + w, v + d, w, h, back or side, noise)

    def faces(self, u, v, w, h, d):
        """앞면 등의 시작 좌표: dict(front=(x,y), back=..., right=..., left=...)."""
        return {'front': (u + d, v + d), 'back': (u + 2 * d + w, v + d), 'right': (u, v + d), 'left': (u + d + w, v + d),
                'top': (u + d, v), 'bottom': (u + d + w, v)}

    def save(self, name, folder=ENT):
        os.makedirs(folder, exist_ok=True)
        self.im.save(os.path.join(folder, name + '.png'))


# 사람 모양 (좀비 모델 층: 왼팔·왼다리는 오른쪽을 뒤집어 씀) — 머리 (0,0) 8³, 몸 (16,16) 8x12x4, 팔 (40,16) 4x12x4, 다리 (0,16) 4x12x4, 모자 층 (32,0)
def biped(s, skin, shirt, pants, boots, sleeve=None, face=None):
    s.box(0, 0, 8, 8, 8, skin)
    s.box(16, 16, 8, 12, 4, shirt)
    s.box(40, 16, 4, 12, 4, sleeve or shirt)
    s.box(0, 16, 4, 12, 4, pants)
    for (u, v) in [(0, 20), (4, 20), (8, 20), (12, 20)]:
        s.rect(u, v + 9, 4, 3, boots)


def goblin():
    s = Skin(seed=21)
    SK, SKD = (96, 150, 60), (70, 112, 44)
    LEA, LEAD = (110, 74, 40), (80, 52, 28)
    biped(s, SK, LEA, LEAD, (60, 40, 24), sleeve=SK)
    # 얼굴: 큰 노란 눈, 뾰족한 이빨, 큰 코
    s.rect(8, 8, 8, 2, (40, 50, 24))      # 이마 머리카락
    for x in (9, 13):
        s.rect(x, 11, 2, 2, (250, 210, 40), 0)
        s.dot(x + (1 if x == 9 else 0), 12, (20, 20, 10))
    s.rect(11, 13, 2, 1, SKD, 0)
    s.rect(10, 14, 4, 1, (40, 20, 10), 0)
    s.dot(10, 15, (240, 240, 220)); s.dot(13, 15, (240, 240, 220))
    # 큰 귀 (옆면에 길게)
    for (u) in (0, 16):
        s.rect(u + 2, 10, 4, 3, SKD, 4)
        s.rect(u + 1, 9, 2, 2, SKD, 4)
    # 허리 끈 + 소매 걷음
    s.rect(20, 28, 8, 1, (150, 120, 70), 3)
    s.rect(16, 28, 4, 1, (150, 120, 70), 3)
    s.rect(28, 28, 4, 1, (150, 120, 70), 3)
    s.rect(32, 28, 8, 1, (150, 120, 70), 3)
    s.save('goblin')


def orc():
    s = Skin(seed=22)
    SK, SKD = (112, 128, 86), (84, 98, 62)
    FUR, LEA, MET = (120, 96, 70), (78, 56, 36), (150, 150, 158)
    biped(s, SK, LEA, (60, 46, 34), (40, 30, 22), sleeve=SK)
    s.rect(8, 8, 8, 2, (20, 18, 16))  # 이마
    s.rect(11, 0, 2, 8, (20, 18, 16), 4)  # 모히칸 (위)
    s.rect(11, 8, 2, 1, (20, 18, 16), 0)
    for x in (9, 13):
        s.rect(x, 11, 2, 1, (200, 40, 30), 0)
    s.rect(9, 10, 6, 1, SKD, 0)  # 눈썹
    s.rect(10, 13, 4, 1, (60, 40, 30), 0)
    s.rect(9, 14, 1, 2, (240, 236, 210), 0)  # 엄니
    s.rect(14, 14, 1, 2, (240, 236, 210), 0)
    # 털 망토 어깨 + 쇠 어깨받이 (오른팔)
    s.rect(20, 20, 8, 3, FUR, 12)
    s.rect(32, 20, 8, 4, FUR, 12)
    s.rect(44, 20, 4, 3, MET, 6)
    s.rect(40, 20, 4, 3, MET, 6)
    s.rect(48, 20, 4, 3, MET, 6)
    s.rect(44, 16, 4, 4, MET, 6)
    s.rect(20, 27, 8, 1, MET, 4)  # 허리 버클
    s.save('orc')


def skeleton_knight():
    s = Skin(seed=23)
    BONE, BONED = (226, 220, 200), (180, 172, 150)
    MAIL, PLATE, RUST = (128, 130, 136), (160, 164, 172), (130, 82, 50)
    biped(s, BONE, MAIL, MAIL, (70, 70, 76), sleeve=MAIL)
    # 해골 얼굴
    for x in (9, 13):
        s.rect(x, 11, 2, 2, (20, 16, 18), 0)
    s.rect(11, 13, 2, 1, (60, 50, 50), 0)
    for x in range(9, 15):
        s.dot(x, 14, BONED if x % 2 else (40, 34, 30))
    # 투구 (모자 층): 이마 + 옆 + 위
    for (u, v, w, h) in [(40, 0, 8, 8), (48, 0, 8, 8), (32, 8, 8, 8), (48, 8, 8, 8), (56, 8, 8, 8)]:
        s.rect(u, v, w, h, PLATE, 6)
    s.rect(40, 8, 8, 3, PLATE, 6)
    s.rect(43, 11, 2, 4, PLATE, 4)  # 코 보호대
    s.rect(32, 12, 8, 1, RUST, 6)
    # 가슴판 + 녹
    s.rect(21, 20, 6, 6, PLATE, 6)
    for _ in range(8):
        s.dot(21 + s.r.randrange(6), 20 + s.r.randrange(6), RUST)
    s.rect(20, 26, 8, 2, (90, 60, 40), 4)
    s.save('skeleton_knight')


def fallen_king():
    s = Skin(seed=24)
    ARM, ARMD, TRIM = (52, 52, 62), (34, 34, 42), (150, 26, 34)
    GOLD = (170, 130, 50)
    biped(s, ARM, ARM, ARMD, (24, 24, 30), sleeve=ARM)
    # 투구: 얼굴 전체 덮음, 가로 눈 틈 (눈 빛은 따로)
    s.rect(8, 8, 8, 8, ARM, 6)
    s.rect(9, 11, 6, 1, (8, 6, 8), 0)
    s.rect(11, 12, 2, 3, ARMD, 0)
    for x in range(9, 15, 2):
        s.dot(x, 14, (12, 10, 12))
    # 부서진 금관 (모자 층 띠 + 앞의 뿔)
    for (u, w) in [(32, 8), (40, 8), (48, 8), (56, 8)]:
        s.rect(u, 8, w, 2, GOLD, 10)
    for x in (41, 43, 45, 47):
        s.rect(x, 6, 1, 2, GOLD, 8)
    # 붉은 테두리와 가슴 문장
    s.rect(20, 20, 8, 1, TRIM, 6)
    s.rect(23, 21, 2, 5, TRIM, 6)
    s.rect(21, 22, 6, 1, TRIM, 6)
    # 찢어진 붉은 망토 (등)
    s.rect(32, 20, 8, 12, (110, 16, 24), 14)
    for x in range(32, 40):
        if s.r.random() < 0.5:
            s.px[x, 31] = (0, 0, 0, 0)
    # 팔·다리 금속 이음새
    for (u, v) in [(40, 20), (44, 20), (48, 20), (52, 20), (0, 20), (4, 20), (8, 20), (12, 20)]:
        s.rect(u, v + 5, 4, 1, ARMD, 2)
        s.rect(u, v, 4, 1, TRIM, 4)
    s.save('fallen_king')
    e = Skin(seed=25)
    e.rect(9, 11, 6, 1, (255, 60, 40), 0)
    e.dot(9, 11, (255, 200, 160)); e.dot(14, 11, (255, 200, 160))
    for x in (41, 43, 45, 47):
        e.rect(x, 6, 1, 1, (255, 70, 50), 0)
    e.save('fallen_king_eyes')


def frost_witch():
    # 플레이어 (얇은 팔) 모양: 팔 3픽셀 폭
    s = Skin(seed=26)
    SK = (206, 226, 240)
    ROBE, ROBED, SIL = (70, 130, 190), (40, 84, 140), (220, 236, 250)
    HAIR = (240, 248, 255)
    s.box(0, 0, 8, 8, 8, HAIR, front=SK)
    s.rect(8, 8, 8, 2, HAIR)
    s.rect(8, 10, 1, 6, HAIR); s.rect(15, 10, 1, 6, HAIR)
    for x in (10, 13):
        s.rect(x, 11, 1, 1, (90, 230, 255), 0)
    s.rect(11, 14, 2, 1, (120, 150, 200), 0)
    s.box(16, 16, 8, 12, 4, ROBE)
    s.rect(23, 20, 2, 12, SIL, 4)
    s.rect(20, 26, 8, 1, SIL, 4)
    # 오른팔 (얇은 팔: 40,16 / 3x12x4) · 왼팔 (32,48)
    s.box(40, 16, 3, 12, 4, ROBE)
    s.box(32, 48, 3, 12, 4, ROBE)
    for (u, v) in [(44, 29), (40, 29), (47, 29), (51, 29), (36, 61), (32, 61), (39, 61), (43, 61)]:
        s.rect(u, v, 3, 3, SK, 3)
    # 다리 = 긴 치마
    s.box(0, 16, 4, 12, 4, ROBED)
    s.box(16, 48, 4, 12, 4, ROBED)
    for (u, v) in [(4, 20), (20, 52)]:
        s.rect(u, v + 10, 4, 1, SIL, 3)
    # 겉옷 층 (자켓 32,32 / 소매)
    s.box(16, 32, 8, 12, 4, ROBED)
    for x in range(20, 28):
        for y in range(36, 48):
            if (x + y) % 5 == 0:
                s.dot(x, y, SIL)
    s.rect(23, 36, 2, 12, (0, 0, 0), 0, a=0)
    s.save('frost_witch')


def colossus():
    """철 골렘 뼈대 (128x128) 에 용암 바위."""
    s = Skin(128, 128, seed=27)
    g = Skin(128, 128, seed=28)
    ROCK, ROCKD = (58, 44, 40), (38, 28, 26)

    def lava_box(u, v, w, h, d):
        s.box(u, v, w, h, d, ROCK, top=ROCKD, noise=10)
        # 갈라진 틈: 아무 면이나 지그재그 선
        for face, (fx, fy) in s.faces(u, v, w, h, d).items():
            fw = w if face in ('front', 'back', 'top', 'bottom') else d
            fh = d if face in ('top', 'bottom') else h
            for _ in range(max(1, fw * fh // 40)):
                x, y = fx + s.r.randrange(fw), fy + s.r.randrange(fh)
                for _ in range(s.r.randint(3, 8)):
                    if fx <= x < fx + fw and fy <= y < fy + fh:
                        c = (255, s.r.randint(110, 170), 30)
                        s.dot(x, y, c)
                        g.dot(x, y, c)
                    x += s.r.choice((-1, 0, 1))
                    y += s.r.choice((0, 1, 1))

    lava_box(0, 0, 8, 10, 8)        # 머리
    lava_box(24, 0, 2, 4, 2)        # 코
    lava_box(0, 40, 18, 12, 11)     # 몸
    lava_box(0, 70, 9, 5, 6)        # 허리
    lava_box(60, 21, 4, 30, 6)      # 오른팔
    lava_box(60, 58, 4, 30, 6)      # 왼팔
    lava_box(37, 0, 6, 16, 5)       # 오른다리
    lava_box(60, 0, 6, 16, 5)       # 왼다리
    # 눈 (머리 앞면 = (8, 8) 에서 8x10)
    for x in (9, 13):
        for c in ((255, 220, 120),):
            s.rect(x, 12, 2, 2, c, 0)
            g.rect(x, 12, 2, 2, (255, 230, 150), 0)
    # 가슴 가운데 용암 심장
    s.rect(17, 55, 4, 4, (255, 150, 30), 10)
    g.rect(17, 55, 4, 4, (255, 170, 60), 10)
    s.save('ember_colossus')
    g.save('ember_colossus_glow')


def altar():
    """보스 제단 블록: 검은 돌 + 붉은 룬 (위), 금 테두리 (옆)."""
    r = random.Random(30)
    top = Image.new('RGBA', (16, 16))
    side = Image.new('RGBA', (16, 16))
    tp, sp = top.load(), side.load()
    for x in range(16):
        for y in range(16):
            n = r.randint(-8, 8)
            tp[x, y] = (34 + n, 26 + n, 32 + n, 255)
            sp[x, y] = (40 + n, 32 + n, 38 + n, 255)
    for i in range(16):
        tp[i, 0] = tp[i, 15] = tp[0, i] = tp[15, i] = (200, 160, 70, 255)
        sp[i, 0] = sp[i, 15] = (200, 160, 70, 255)
    # 위: 룬 원 + 가운데 마름모
    import math
    for k in range(48):
        a = k * math.pi * 2 / 48
        x, y = int(round(7.5 + math.cos(a) * 5.5)), int(round(7.5 + math.sin(a) * 5.5))
        tp[x, y] = (255, 60, 50, 255)
    for (x, y) in [(7, 4), (8, 4), (6, 5), (9, 5), (5, 6), (10, 6), (5, 9), (10, 9), (6, 10), (9, 10), (7, 11), (8, 11), (7, 7), (8, 8), (7, 8), (8, 7)]:
        tp[x, y] = (255, 90, 70, 255)
    # 옆: 붉은 룬 줄
    for x in range(2, 14, 3):
        for y in (6, 7, 8, 9):
            sp[x, y] = (230, 50, 40, 255)
        sp[x + 1, 7] = (255, 120, 90, 255)
    os.makedirs(BLK, exist_ok=True)
    top.save(os.path.join(BLK, 'boss_altar_top.png'))
    side.save(os.path.join(BLK, 'boss_altar_side.png'))
    models = os.path.join(ASSETS, 'models')
    os.makedirs(os.path.join(models, 'block'), exist_ok=True)
    os.makedirs(os.path.join(models, 'item'), exist_ok=True)
    json.dump({'parent': 'minecraft:block/cube_bottom_top', 'textures': {'top': 'abilitywar:block/boss_altar_top', 'bottom': 'abilitywar:block/boss_altar_side',
               'side': 'abilitywar:block/boss_altar_side'}}, open(os.path.join(models, 'block', 'boss_altar.json'), 'w'), indent=1)
    json.dump({'parent': 'abilitywar:block/boss_altar'}, open(os.path.join(models, 'item', 'boss_altar.json'), 'w'), indent=1)
    os.makedirs(os.path.join(ASSETS, 'blockstates'), exist_ok=True)
    json.dump({'variants': {'': {'model': 'abilitywar:block/boss_altar'}}}, open(os.path.join(ASSETS, 'blockstates', 'boss_altar.json'), 'w'), indent=1)


def eggs():
    models = os.path.join(ASSETS, 'models', 'item')
    for m in ['goblin', 'orc', 'skeleton_knight', 'wisp', 'fallen_king', 'ember_colossus', 'frost_witch', 'ancient_wyvern']:
        json.dump({'parent': 'minecraft:item/template_spawn_egg'}, open(os.path.join(models, m + '_spawn_egg.json'), 'w'))


if __name__ == '__main__':
    goblin(); orc(); skeleton_knight(); fallen_king(); frost_witch(); colossus(); altar(); eggs()
    print('done')
