#!/usr/bin/env python3
"""
상점 · 대장장이 그림:
  textures/item/{health_potion_small,health_potion,health_potion_large,enhance_stone,protection_scroll,promotion_stone}.png (16x16 도트)
  textures/entity/{apothecary,blacksmith}.png (64x64 NPC 피부)
  models/item/<위 아이템>.json, apothecary_spawn_egg.json, blacksmith_spawn_egg.json
사용: python3 tools/gen_shop_art.py
"""
import json, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar')
ITEM = os.path.join(ASSETS, 'textures', 'item')
ENT = os.path.join(ASSETS, 'textures', 'entity')
MODEL = os.path.join(ASSETS, 'models', 'item')

T = (0, 0, 0, 0)


def grid(rows, pal):
    """글자 그림 → 16x16. '.' = 투명."""
    im = Image.new('RGBA', (16, 16), T)
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.' and ch != ' ':
                px[x, y] = pal[ch]
    return im


def potion(size, liquid, light, dark, cap):
    """회복 물약: 작은 둥근 병 / 보통 병 / 큰 병 (금 마개)."""
    pal = {'o': (40, 26, 30, 255), 'g': (200, 220, 235, 255), 'G': (150, 170, 190, 255), 'l': liquid, 'L': light, 'd': dark,
           'c': cap[0], 'C': cap[1], 'w': (255, 255, 255, 255), 'h': (255, 210, 220, 255)}
    if size == 0:
        rows = ["................",
                "................",
                "................",
                "................",
                "......oooo......",
                "......cCCo......",
                "......oggo......",
                ".....oglLgo.....",
                "....oglLLlLo....",
                "....olwLLLlo....",
                "....olhLLlLo....",
                "....olllllldo...",
                "....oldllddo....",
                ".....odddddo....",
                "......oooooo....",
                "................"]
    elif size == 1:
        rows = ["................",
                "......oooo......",
                "......cCCo......",
                "......cCco......",
                "......oggo......",
                ".....oggGgo.....",
                "....ogLLLLlo....",
                "...oglwLLLLlo...",
                "...olhLLLLLlo...",
                "...olLLLLLLlo...",
                "...olLLLLLldo...",
                "...ollllllddo...",
                "...olllllddo....",
                "....odddddo.....",
                ".....ooooo......",
                "................"]
    else:
        rows = ["......oooo......",
                ".....ocCCco.....",
                ".....oCCCCo.....",
                "......oggo......",
                ".....oggggo.....",
                "....oggGGggo....",
                "...ogLLLLLLlo...",
                "..oglwLLLLLLlo..",
                "..olhLLLLLLLlo..",
                "..olLLLLLLLLlo..",
                "..olLLLLLLLLdo..",
                "..ollLLLLLlldo..",
                "..olllllllldo...",
                "...odllllddo....",
                "....oddddddo....",
                ".....oooooo....."]
    return grid(rows, pal)


def enhance_stone():
    pal = {'o': (16, 26, 60, 255), 'b': (60, 130, 230, 255), 'B': (120, 190, 255, 255), 'd': (30, 70, 160, 255),
           'w': (235, 250, 255, 255), 'c': (150, 230, 255, 255)}
    rows = ["................",
            ".........o......",
            "........obo.....",
            ".......obBdo....",
            "......obBwbdo...",
            ".....obBcBbdo...",
            "....obBBwBbddo..",
            "...obBcBBbbddo..",
            "...obBBBbbdddo..",
            "..obBBbbbbddo...",
            "..obBbbbbdddo...",
            "...obbbbdddo....",
            "....obbddddo....",
            ".....obdddo.....",
            "......oooo......",
            "................"]
    return grid(rows, pal)


def protection_scroll():
    pal = {'o': (70, 46, 22, 255), 'p': (236, 220, 170, 255), 'P': (250, 240, 205, 255), 'd': (196, 172, 120, 255),
           'r': (60, 110, 210, 255), 'R': (120, 170, 255, 255), 'l': (120, 96, 60, 255), 'g': (226, 184, 86, 255)}
    rows = ["................",
            "..oooooooooooo..",
            ".odPPPPPPPPPPdo.",
            ".oooooooooooooo.",
            "..opPPPPPPPPpo..",
            "..opllllllllpo..",
            "..opPPPPPPPPpo..",
            "..opllllllPPpo..",
            "..opPPPPPPPPpo..",
            "..opllPPRRRPpo..",
            "..opPPPRggRRpo..",
            "..opdPPRRRRdpo..",
            ".oooooooRRoooo..",
            ".odPPPPPrRPPdo..",
            "..oooooooooooo..",
            "................"]
    return grid(rows, pal)


def promotion_stone():
    pal = {'o': (50, 20, 60, 255), 'y': (255, 210, 80, 255), 'Y': (255, 240, 170, 255), 'd': (200, 120, 30, 255),
           'v': (170, 80, 230, 255), 'V': (220, 150, 255, 255), 'w': (255, 255, 255, 255)}
    rows = ["................",
            ".......oo.......",
            "......oYyo......",
            "......oYyo......",
            ".ooooovYyvooooo.",
            ".oYYyyvwVvyyddo.",
            "..oYyyVVVvyddo..",
            "...ooyvVvvydoo..",
            "....oyvvvvydo...",
            "...oyyvvvvyddo..",
            "...oYyoddoyddo..",
            "..oYyo....oddo..",
            "..oyo......odo..",
            "..oo........oo..",
            "................",
            "................"]
    return grid(rows, pal)


# ================================================================ NPC 피부 (64x64, 플레이어 모양)
def skin(name, seed, parts):
    im = Image.new('RGBA', (64, 64), T)
    px = im.load()
    rng = random.Random(seed)

    def rect(x, y, w, h, c, noise=6):
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

    parts(rect, box, px)
    os.makedirs(ENT, exist_ok=True)
    im.save(os.path.join(ENT, name + '.png'))


def apothecary(rect, box, px):
    """물약 상인: 은발 + 둥근 안경, 녹색 로브, 허리띠에 물약 병 (빨강 · 파랑)."""
    SKIN, SKIN_D = (226, 186, 150), (190, 146, 112)
    HAIR = (200, 200, 206)
    ROBE, ROBE_D, ROBE_L = (52, 104, 62), (34, 72, 42), (84, 140, 90)
    BELT = (96, 60, 30)
    box(0, 0, 8, 8, 8, HAIR, HAIR, SKIN)
    rect(8, 8, 8, 8, SKIN)
    rect(8, 8, 8, 2, HAIR)
    rect(8, 10, 1, 3, HAIR)
    rect(15, 10, 1, 3, HAIR)
    # 둥근 안경
    for x in (9, 10, 13, 14):
        px[x, 11] = (60, 50, 40, 255)
    px[9, 12] = px[10, 12] = px[13, 12] = px[14, 12] = (180, 220, 240, 255)
    px[11, 12] = px[12, 12] = (60, 50, 40, 255)
    rect(10, 14, 4, 2, (220, 220, 225), 3)  # 흰 수염
    for (u, w) in [(0, 8), (16, 8), (24, 8)]:
        rect(u, 8, w, 8, HAIR)
    # 몸: 녹색 로브 + 가죽 허리띠 + 물약 병
    box(16, 16, 8, 12, 4, ROBE, ROBE, ROBE_D, ROBE, ROBE_D)
    rect(23, 20, 2, 12, ROBE_L, 3)
    rect(20, 27, 8, 1, BELT, 2)
    rect(21, 28, 1, 2, (220, 50, 60), 0)
    rect(26, 28, 1, 2, (70, 120, 230), 0)
    rect(24, 21, 2, 2, (226, 184, 86), 0)
    # 팔 (로브 소매)
    box(40, 16, 4, 12, 4, ROBE, ROBE, SKIN)
    box(32, 48, 4, 12, 4, ROBE, ROBE, SKIN)
    for (u, v) in [(40, 30), (44, 30), (48, 30), (52, 30), (32, 62), (36, 62), (40, 62), (44, 62)]:
        rect(u, v - 2, 4, 2, SKIN)
    # 다리 (긴 로브 자락)
    box(0, 16, 4, 12, 4, ROBE_D, ROBE_D, (50, 34, 22))
    box(16, 48, 4, 12, 4, ROBE_D, ROBE_D, (50, 34, 22))
    for (u, v) in [(0, 20), (4, 20), (8, 20), (12, 20), (16, 52), (20, 52), (24, 52), (28, 52)]:
        rect(u, v + 10, 4, 2, (50, 34, 22))


def blacksmith(rect, box, px):
    """대장장이: 검은 머리 · 수염, 굵은 팔 (맨팔), 가죽 앞치마, 검은 장갑."""
    SKIN, SKIN_D = (206, 156, 118), (170, 120, 86)
    HAIR = (40, 30, 24)
    SHIRT = (120, 110, 100)
    APRON, APRON_D = (120, 74, 40), (88, 52, 26)
    GLOVE = (40, 34, 30)
    box(0, 0, 8, 8, 8, HAIR, HAIR, SKIN)
    rect(8, 8, 8, 8, SKIN)
    rect(8, 8, 8, 2, HAIR)
    px[9, 11] = px[10, 11] = px[13, 11] = px[14, 11] = (40, 30, 24, 255)  # 짙은 눈썹
    px[10, 12] = px[13, 12] = (40, 40, 40, 255)
    px[9, 12] = px[14, 12] = (240, 240, 240, 255)
    rect(8, 13, 8, 3, HAIR, 5)  # 수염
    rect(10, 14, 4, 1, (150, 80, 70), 0)
    for (u, w) in [(0, 8), (16, 8), (24, 8)]:
        rect(u, 8, w, 8, HAIR)
    rect(0, 13, 8, 3, HAIR)
    rect(16, 13, 8, 3, HAIR)
    # 몸: 회색 반팔 + 가죽 앞치마 (주머니 + 망치 고리)
    box(16, 16, 8, 12, 4, SHIRT, SHIRT, SHIRT, SHIRT, SHIRT)
    rect(20, 21, 8, 11, APRON, 5)
    rect(20, 20, 1, 1, APRON_D, 0)
    rect(27, 20, 1, 1, APRON_D, 0)
    rect(22, 26, 4, 3, APRON_D, 3)
    rect(20, 24, 8, 1, (70, 44, 22), 0)
    rect(24, 24, 1, 1, (190, 190, 200), 0)  # 쇠 고리
    # 팔: 맨팔 (굵음) + 검은 장갑
    box(40, 16, 4, 12, 4, SKIN, SHIRT, GLOVE)
    box(32, 48, 4, 12, 4, SKIN, SHIRT, GLOVE)
    for (u, v) in [(40, 20), (44, 20), (48, 20), (52, 20), (32, 52), (36, 52), (40, 52), (44, 52)]:
        rect(u, v, 4, 3, SHIRT)
        rect(u, v + 9, 4, 3, GLOVE)
    # 다리: 짙은 바지 + 쇠 장화
    box(0, 16, 4, 12, 4, (60, 52, 46), (60, 52, 46), (80, 80, 86))
    box(16, 48, 4, 12, 4, (60, 52, 46), (60, 52, 46), (80, 80, 86))
    for (u, v) in [(0, 20), (4, 20), (8, 20), (12, 20), (16, 52), (20, 52), (24, 52), (28, 52)]:
        rect(u, v + 8, 4, 4, (80, 80, 86))


def crystal(light, mid, dark, shine=(255, 255, 255, 255)):
    """마정석: 위아래로 긴 육각 보석 (등급 색)."""
    pal = {'o': (20, 16, 26, 255), 'L': light, 'm': mid, 'd': dark, 'w': shine}
    rows = ["................",
            ".......oo.......",
            "......oLmo......",
            ".....oLwmmo.....",
            "....oLwLmmdo....",
            "....oLLmmmdo....",
            "...oLLmmmmddo...",
            "...oLmmmmmddo...",
            "...oLmmmmmddo...",
            "...oLmmmmdddo...",
            "....oLmmmddo....",
            "....oLmmmddo....",
            ".....oLmddo.....",
            "......omdo......",
            ".......oo.......",
            "................"]
    return grid(rows, pal)


def chaos_shard():
    pal = {'o': (10, 6, 14, 255), 'a': (255, 80, 240, 255), 'b': (90, 255, 245, 255), 'k': (40, 20, 60, 255), 'w': (255, 255, 255, 255), 'g': (120, 120, 130, 255)}
    rows = ["................",
            ".........o......",
            "........oao.....",
            ".......oawko....",
            "...o..oakkbo....",
            "..obo.okbkko....",
            "..okbookkkao....",
            "...okbkkwkao....",
            "....okkkkbo.....",
            "....okakkko.....",
            "...okkkbkgo.....",
            "...oakkkko......",
            "....obkko.......",
            ".....oko........",
            "......o.........",
            "................"]
    return grid(rows, pal)


def coin():
    """G 동전 (16x16, GUI 용)."""
    pal = {'o': (90, 56, 10, 255), 'y': (255, 206, 70, 255), 'Y': (255, 236, 150, 255), 'd': (214, 150, 30, 255), 'G': (140, 86, 14, 255)}
    rows = ["................",
            ".....oooooo.....",
            "...ooYYYYyyoo...",
            "..oYYyyyyyyddo..",
            "..oYyyGGGGyydo..",
            ".oYyyGGyyyGyddo.",
            ".oYyGGyyyyyyddo.",
            ".oYyGGyyGGGyddo.",
            ".oYyGGyyyGGyddo.",
            ".oyyGGyyyGGyddo.",
            ".oyyyGGGGGyyddo.",
            "..oyyyyyyyyddo..",
            "..oddyyyyyddddo.",
            "...oodddddddoo..",
            ".....oooooo.....",
            "................"]
    return grid(rows, pal)


def main():
    os.makedirs(ITEM, exist_ok=True)
    os.makedirs(MODEL, exist_ok=True)
    red = ((220, 40, 56, 255), (255, 110, 120, 255), (150, 20, 36, 255))
    items = {
        'health_potion_small': potion(0, *red, ((150, 100, 60, 255), (190, 140, 90, 255))),
        'health_potion': potion(1, *red, ((150, 100, 60, 255), (190, 140, 90, 255))),
        'health_potion_large': potion(2, *red, ((226, 184, 86, 255), (255, 230, 140, 255))),
        'enhance_stone': enhance_stone(),
        'protection_scroll': protection_scroll(),
        'promotion_stone': promotion_stone(),
    }
    items.update({
        'crystal_good': crystal((170, 255, 170, 255), (80, 210, 90, 255), (30, 120, 50, 255)),
        'crystal_rare': crystal((170, 215, 255, 255), (70, 150, 255, 255), (30, 70, 170, 255)),
        'crystal_hero': crystal((225, 180, 255, 255), (170, 90, 255, 255), (90, 40, 160, 255)),
        'crystal_legendary': crystal((255, 236, 160, 255), (255, 180, 40, 255), (170, 100, 10, 255)),
        'crystal_mythic': crystal((255, 170, 160, 255), (240, 60, 60, 255), (140, 20, 30, 255)),
        'crystal_secret': crystal((190, 255, 240, 255), (50, 230, 200, 255), (20, 120, 120, 255), (255, 240, 160, 255)),
        'chaos_shard': chaos_shard(),
    })
    gui = os.path.join(ASSETS, 'textures', 'gui')
    os.makedirs(gui, exist_ok=True)
    coin().save(os.path.join(gui, 'coin.png'))
    for name, im in items.items():
        im.save(os.path.join(ITEM, name + '.png'))
        with open(os.path.join(MODEL, name + '.json'), 'w') as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "abilitywar:item/" + name}}, f)
    for egg in ('apothecary_spawn_egg', 'blacksmith_spawn_egg'):
        with open(os.path.join(MODEL, egg + '.json'), 'w') as f:
            json.dump({"parent": "minecraft:item/template_spawn_egg"}, f)
    skin('apothecary', 31, apothecary)
    skin('blacksmith', 37, blacksmith)
    # 미리보기 (scratch 용): 아이템 8배
    sheet = Image.new('RGBA', (16 * 8 * len(items), 16 * 8), (40, 40, 48, 255))
    for i, im in enumerate(items.values()):
        sheet.paste(im.resize((128, 128), Image.NEAREST), (i * 128, 0), im.resize((128, 128), Image.NEAREST))
    out = os.environ.get('PREVIEW')
    if out:
        sheet.save(out)
    print('ok', len(items), 'items, 2 skins')


if __name__ == '__main__':
    main()
