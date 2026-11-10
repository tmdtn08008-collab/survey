#!/usr/bin/env python3
"""
능력별 스킬바 그림 -> HUD 텍스처/마스크/아이콘/레이아웃 JSON.
사용: python3 tools/gen_hud.py  (tools/art/<능력>_hud_source.png 를 읽음)

슬롯 좌표는 원본 그림 기준 안쪽 아이콘 영역 (x0, y0, x1, y1, 모서리깎기).
labels 는 그림에 박혀 있는 키 글자 위치 (키를 바꾸면 이 위에 새 글자를 덮어 그림).
"""
import json, os, sys
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar')
HUD_W = 640

LAYOUTS = {
    'void': {
        'slots': {
            'skill1': (97, 405, 372, 697, 14),
            'skill2': (428, 405, 705, 697, 14),
            'skill3': (760, 405, 1040, 697, 14),
            'skill4': (1092, 405, 1373, 697, 14),
            'ultimate': (1440, 362, 1848, 700, 22),
            'passive': (1183, 100, 1377, 272, 12),
        },
        'labels': {
            'skill1': (298, 598, 370, 694),
            'skill2': (630, 598, 703, 694),
            'skill3': (962, 598, 1038, 694),
            'skill4': (1295, 598, 1370, 694),
            'ultimate': (1786, 622, 1856, 702),
            'passive': (1078, 135, 1140, 225),
        },
        'accent': [0.75, 0.55, 1.0], 'accent2': [0.45, 0.2, 0.85],
    },
}
# 마검사: 사용자가 준 그림 (까만 배경, tools/import_hud_art.py --black)
LAYOUTS['blade'] = {
    'slots': {
        'skill1': (80, 382, 374, 688, 10),
        'skill2': (424, 382, 708, 688, 10),
        'skill3': (768, 382, 1054, 688, 10),
        'skill4': (1100, 382, 1396, 690, 10),
        'ultimate': (1458, 362, 1848, 700, 14),
        'passive': (1200, 60, 1410, 262, 10),
    },
    'labels': {
        'skill1': (300, 600, 356, 688),
        'skill2': (642, 600, 702, 688),
        'skill3': (982, 600, 1042, 688),
        'skill4': (1318, 604, 1382, 688),
        'ultimate': (1772, 608, 1842, 702),
        'passive': (1078, 122, 1142, 206),
    },
    'accent': [0.78, 0.5, 1.0], 'accent2': [0.45, 0.15, 0.85],
    'card': (1460, 365, 1770, 559),
    'trail': [230, 110, 1160, 245],
    'emblem': [118, 165, 120],
}
# 용기사: 사용자가 준 그림 (tools/import_hud_art.py 로 체크무늬 배경 제거). Q 대시 칸이 그림에 들어 있음.
# 모서리깎기 값이 음수면 원형 슬롯.
LAYOUTS['dragon'] = {
    'slots': {
        'skill1': (310, 313, 547, 586, 8),
        'skill2': (599, 313, 837, 586, 8),
        'skill3': (887, 313, 1126, 586, 8),
        'skill4': (1177, 313, 1415, 586, 8),
        'ultimate': (1471, 313, 1897, 586, 10),
        'passive': (1304, 62, 1480, 220, 8),
        'dash': (71, 381, 231, 539, -1),
    },
    'labels': {
        'skill1': (488, 505, 546, 586),
        'skill2': (776, 505, 836, 586),
        'skill3': (1062, 505, 1126, 586),
        'skill4': (1352, 505, 1414, 586),
        'ultimate': (1822, 505, 1890, 590),
        'passive': (1192, 105, 1252, 180),
        'dash': (200, 515, 272, 592),
    },
    'accent': [1.0, 0.62, 0.28], 'accent2': [0.9, 0.3, 0.08],
    'ult_crop': 'center',
    'card': (1478, 335, 1818, 548),
}

# 혈귀: 사용자가 준 그림 (진짜 투명 배경). tools/import_blood_art.py 가 여백을 자르고 이름 글자/Q 를 고쳐서 저장.
LAYOUTS['blood'] = {
    'slots': {
        'skill1': (290, 282, 470, 456, 8),
        'skill2': (508, 282, 718, 456, 8),
        'skill3': (768, 280, 952, 458, 8),
        'skill4': (1002, 280, 1196, 458, 8),
        'ultimate': (1268, 268, 1558, 466, 10),
        'passive': (1125, 80, 1255, 200, 8),
        'dash': (105, 302, 240, 438, -1),
    },
    'labels': {
        'skill1': (420, 402, 468, 466),
        'skill2': (665, 402, 712, 466),
        'skill3': (902, 404, 950, 466),
        'skill4': (1147, 404, 1194, 466),
        'ultimate': (1507, 432, 1546, 470),
        'passive': (1036, 100, 1094, 180),
        'dash': (212, 412, 262, 466),
    },
    'accent': [1.0, 0.28, 0.32], 'accent2': [0.75, 0.04, 0.1],
    'ult_crop': 'center',
    'card': (1270, 276, 1556, 455),
    'trail': [500, 95, 1030, 200],
    'emblem': [281, 149, 60],
}

# 성가사(버퍼): 사용자가 준 그림 (진짜 투명 배경, tools/art/cantor_hud_original.webp 를 그대로 PNG 로).
LAYOUTS['cantor'] = {
    'slots': {
        'skill1': (80, 405, 365, 688, 10),
        'skill2': (422, 405, 703, 688, 10),
        'skill3': (752, 405, 1038, 690, 10),
        'skill4': (1082, 405, 1372, 690, 10),
        'ultimate': (1435, 400, 1815, 715, 14),
        'passive': (1192, 106, 1384, 280, 10),
    },
    'labels': {
        'skill1': (300, 600, 362, 692),
        'skill2': (622, 600, 697, 692),
        'skill3': (960, 600, 1030, 694),
        'skill4': (1288, 600, 1358, 694),
        'ultimate': (1740, 618, 1814, 712),
        'passive': (1080, 135, 1155, 245),
    },
    'accent': [1.0, 0.85, 0.42], 'accent2': [0.45, 0.4, 0.95],
    'ult_crop': 'center',
    'card': (1440, 410, 1810, 641),
    'trail': [480, 100, 1040, 260],
    'emblem': [103, 197, 55],
}


def octagon(x0, y0, x1, y1, c):
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c), (x0, y0 + c)]


def main():
    only = sys.argv[1:]
    for ab, lay in LAYOUTS.items():
        if only and ab not in only:
            continue
        src = Image.open(os.path.join(ROOT, 'tools', 'art', ab + '_hud_source.png')).convert('RGBA')
        W, H = src.size
        hud_h = round(H * HUD_W / W)
        out_dir = os.path.join(ASSETS, 'textures', 'gui', 'hud')
        os.makedirs(out_dir, exist_ok=True)
        src.resize((HUD_W, hud_h), Image.LANCZOS).save(os.path.join(out_dir, ab + '_bar.png'))
        # 슬롯 마스크 (부드러운 가장자리)
        big = Image.new('L', (W, H), 0)
        d = ImageDraw.Draw(big)
        for (x0, y0, x1, y1, c) in lay['slots'].values():
            if c < 0:
                d.ellipse([x0, y0, x1, y1], fill=255)
            else:
                d.polygon(octagon(x0, y0, x1, y1, c), fill=255)
        mask = Image.new('RGBA', (W, H), (255, 255, 255, 0))
        mask.putalpha(big)
        mask.resize((HUD_W, hud_h), Image.LANCZOS).save(os.path.join(out_dir, ab + '_mask.png'))
        # 선택 화면 등에 쓰는 개별 아이콘 (64x64)
        for name, (x0, y0, x1, y1, c) in lay['slots'].items():
            if name == 'dash':
                continue
            w, h = x1 - x0, y1 - y0
            s = min(w, h)
            cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
            if w > h and lay.get('ult_crop') != 'center':  # 가로로 긴 슬롯(궁극기)은 오른쪽 아래 키 글자가 잘리지 않게 왼쪽 정사각형
                cx = x0 + s / 2 + 8
            ic = src.crop((int(cx - s / 2), int(cy - s / 2), int(cx + s / 2), int(cy + s / 2))).resize((64, 64), Image.LANCZOS)
            ic.save(os.path.join(ASSETS, 'textures', 'gui', f'{ab}_{name}.png'))
        if 'card' in lay:
            # 능력 선택 카드 (256x160)
            card = src.crop(lay['card']).resize((256, 160), Image.LANCZOS)
            bgc = Image.new('RGBA', card.size, (12, 4, 2, 255))
            bgc.alpha_composite(card)
            bgc.save(os.path.join(ASSETS, 'textures', 'gui', f'card_{ab}.png'))
        layout = {
            'texture': f'abilitywar:textures/gui/hud/{ab}_bar.png',
            'mask': f'abilitywar:textures/gui/hud/{ab}_mask.png',
            'width': W, 'height': H,
            'slots': {k: list(v) for k, v in lay['slots'].items()},
            'labels': {k: list(v) for k, v in lay['labels'].items()},
            'accent': lay['accent'], 'accent2': lay['accent2'],
        }
        for k in ('trail', 'emblem'):
            if k in lay:
                layout[k] = list(lay[k])
        os.makedirs(os.path.join(ASSETS, 'hud'), exist_ok=True)
        with open(os.path.join(ASSETS, 'hud', ab + '.json'), 'w') as f:
            json.dump(layout, f, indent=1)
        print(ab, (HUD_W, hud_h))


if __name__ == '__main__':
    main()
