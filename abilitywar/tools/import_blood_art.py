#!/usr/bin/env python3
"""
혈귀 스킬바: 사용자 그림(tools/art/blood_hud_original.webp, 진짜 투명 배경) -> tools/art/blood_hud_source.png
- 여백을 잘라냄
- 이름 칸의 글자가 '펄커'로 잘못 그려져 있어서 '혈귀'로 다시 씀 (같은 도트 글꼴 느낌: 흰 글자 + 두꺼운 검은 테두리)
- 대시(동그라미) 칸에 Q 글자를 숫자들과 같은 모양으로 넣음
그 다음 `python3 tools/gen_hud.py blood`.
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), '..')
SRC = os.path.join(ROOT, 'tools', 'art', 'blood_hud_original.webp')
DST = os.path.join(ROOT, 'tools', 'art', 'blood_hud_source.png')
HANGUL = '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc'
LATIN = '/usr/share/fonts/opentype/inter/Inter-Black.otf'
CROP = (53, 170, 1746, 754)


def pixel_text(text, font, size, bold, block, outline):
    """굵은 글꼴로 크게 그린 뒤 block 픽셀 단위로 깍둑 (도트 느낌) + 검은 테두리."""
    f = ImageFont.truetype(font, size)
    l, t, r, b = f.getbbox(text, stroke_width=bold)
    w, h = r - l + 8, b - t + 8
    m = Image.new('L', (w, h), 0)
    ImageDraw.Draw(m).text((4 - l, 4 - t), text, font=f, fill=255, stroke_width=bold, stroke_fill=255)
    sw, sh = max(1, w // block), max(1, h // block)
    small = m.resize((sw, sh), Image.BOX).point(lambda v: 255 if v > 120 else 0)
    big = small.resize((sw * block, sh * block), Image.NEAREST)
    big = big.crop(big.getbbox())
    pad = outline + 2
    W, H = big.size[0] + pad * 2, big.size[1] + pad * 2
    glyph = Image.new('L', (W, H), 0)
    glyph.paste(big, (pad, pad))
    ring = glyph.filter(ImageFilter.MaxFilter(outline * 2 + 1))
    out = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    out.paste((8, 2, 4, 255), (0, 0), ring)
    out.paste((255, 255, 255, 255), (0, 0), glyph)
    return out


def main():
    im = Image.open(SRC).convert('RGBA').crop(CROP)
    # 이름: '펄커' 자리 (338,104)-(494,184) 를 오른쪽 띠의 배경으로 덮고 '혈귀' 를 씀
    x0, y0, x1, y1 = 336, 100, 498, 188
    patch = im.crop((520, y0, 520 + (x1 - x0), y1))
    im.paste(patch, (x0, y0))
    t = pixel_text('혈귀', HANGUL, 84, 0, 3, 6)
    cy = (104 + 184) // 2
    im.alpha_composite(t, (340, cy - t.size[1] // 2))
    # 대시 칸 Q (숫자와 같은 높이)
    q = pixel_text('Q', LATIN, 64, 0, 4, 5)
    im.alpha_composite(q, (236 - q.size[0] // 2, 438 - q.size[1] // 2))
    im.save(DST)
    print('saved', DST, im.size)


if __name__ == '__main__':
    main()
