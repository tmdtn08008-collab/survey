#!/usr/bin/env python3
"""
능력별 스킬바 그림 -> HUD 텍스처/마스크/아이콘/레이아웃 JSON.
사용: python3 tools/gen_hud.py  (tools/art/<능력>_hud_source.png 를 읽음)

슬롯 좌표는 원본 그림 기준 안쪽 아이콘 영역 (x0, y0, x1, y1, 모서리깎기).
labels 는 그림에 박혀 있는 키 글자 위치 (키를 바꾸면 이 위에 새 글자를 덮어 그림).
"""
import json, os
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
    },
}


def octagon(x0, y0, x1, y1, c):
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c), (x0, y0 + c)]


def main():
    for ab, lay in LAYOUTS.items():
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
            d.polygon(octagon(x0, y0, x1, y1, c), fill=255)
        mask = Image.new('RGBA', (W, H), (255, 255, 255, 0))
        mask.putalpha(big)
        mask.resize((HUD_W, hud_h), Image.LANCZOS).save(os.path.join(out_dir, ab + '_mask.png'))
        # 선택 화면 등에 쓰는 개별 아이콘 (64x64)
        for name, (x0, y0, x1, y1, c) in lay['slots'].items():
            w, h = x1 - x0, y1 - y0
            s = min(w, h)
            cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
            if w > h:  # 가로로 긴 슬롯(궁극기)은 오른쪽 아래 키 글자가 잘리지 않게 왼쪽 정사각형
                cx = x0 + s / 2 + 8
            ic = src.crop((int(cx - s / 2), int(cy - s / 2), int(cx + s / 2), int(cy + s / 2))).resize((64, 64), Image.LANCZOS)
            ic.save(os.path.join(ASSETS, 'textures', 'gui', f'{ab}_{name}.png'))
        layout = {
            'texture': f'abilitywar:textures/gui/hud/{ab}_bar.png',
            'mask': f'abilitywar:textures/gui/hud/{ab}_mask.png',
            'width': W, 'height': H,
            'slots': {k: list(v) for k, v in lay['slots'].items()},
            'labels': {k: list(v) for k, v in lay['labels'].items()},
        }
        os.makedirs(os.path.join(ASSETS, 'hud'), exist_ok=True)
        with open(os.path.join(ASSETS, 'hud', ab + '.json'), 'w') as f:
            json.dump(layout, f, indent=1)
        print(ab, (HUD_W, hud_h))


if __name__ == '__main__':
    main()
