#!/usr/bin/env python3
"""
가짜 투명 배경(회색/흰색 체크무늬가 그림에 박힌 것)을 지워서 HUD 원본 그림으로 저장.
사용: python3 tools/import_hud_art.py <입력 그림> tools/art/<능력>_hud_source.png [--black]
- --black: 체크무늬 대신 까만 배경인 그림
- 가장자리와 이어진 '채도 낮고 밝은' 픽셀 = 배경
- 그림 둘레의 붉은 빛 번짐은 채도에 따라 반투명으로 남김
"""
import sys
from collections import deque
import numpy as np
from PIL import Image, ImageFilter


def main(src, dst, black=False):
    im = np.asarray(Image.open(src).convert('RGB')).astype(np.float32)
    H, W, _ = im.shape
    mx, mn = im.max(2), im.min(2)
    sat = mx - mn
    if black:
        return main_black(im, dst)
    core = (sat > 55) | (mx < 175)
    bg = np.zeros((H, W), bool)
    cand = ~core
    q = deque()
    for x in range(W):
        for y in (0, H - 1):
            if cand[y, x] and not bg[y, x]:
                bg[y, x] = True
                q.append((y, x))
    for y in range(H):
        for x in (0, W - 1):
            if cand[y, x] and not bg[y, x]:
                bg[y, x] = True
                q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < H and 0 <= nx < W and cand[ny, nx] and not bg[ny, nx]:
                bg[ny, nx] = True
                q.append((ny, nx))
    fg = (~bg).astype(np.float32)
    # 가장자리를 조금 부드럽게
    soft = np.asarray(Image.fromarray((fg * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8)), np.float32) / 255
    # 배경 쪽의 빛 번짐: 채도로 투명도 추정, 그림 근처에서만
    near = np.asarray(Image.fromarray((fg * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(25)), np.float32) / 255
    glow_a = np.clip((sat - 22) / 110, 0, 0.8) * near * bg
    alpha = np.maximum(soft * (~bg | (soft > 0)), glow_a)
    alpha = np.maximum(alpha, fg)
    # 반투명 부분은 배경 회색(약 234)을 빼서 원래 색으로 되돌림
    g = 234.0
    a3 = np.clip(alpha, 1e-3, 1)[..., None]
    col = np.where(alpha[..., None] < 0.999, (im - (1 - a3) * g) / a3, im)
    col = np.clip(col, 0, 255)
    out = np.dstack([col, alpha * 255]).astype(np.uint8)
    Image.fromarray(out, 'RGBA').save(dst)
    print('saved', dst, (W, H))


def flood(cand):
    H, W = cand.shape
    bg = np.zeros((H, W), bool)
    q = deque()
    for x in range(W):
        for y in (0, H - 1):
            if cand[y, x] and not bg[y, x]:
                bg[y, x] = True
                q.append((y, x))
    for y in range(H):
        for x in (0, W - 1):
            if cand[y, x] and not bg[y, x]:
                bg[y, x] = True
                q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < H and 0 <= nx < W and cand[ny, nx] and not bg[ny, nx]:
                bg[ny, nx] = True
                q.append((ny, nx))
    return bg


def main_black(im, dst):
    """검은 배경 그림: 가장자리와 이어진 거의 검은 곳 = 배경, 그 둘레의 빛 번짐은 밝기로 반투명."""
    H, W, _ = im.shape
    mx = im.max(2)
    bg = flood(mx < 28)
    fg = (~bg).astype(np.float32)
    soft = np.asarray(Image.fromarray((fg * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8)), np.float32) / 255
    near = np.asarray(Image.fromarray((fg * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(21)), np.float32) / 255
    glow_a = np.clip((mx - 4) / 110, 0, 0.85) * near * bg
    alpha = np.maximum(np.maximum(soft, glow_a), fg)
    a3 = np.clip(alpha, 1e-3, 1)[..., None]
    col = np.where(alpha[..., None] < 0.999, im / a3, im)
    col = np.clip(col, 0, 255)
    out = np.dstack([col, alpha * 255]).astype(np.uint8)
    Image.fromarray(out, 'RGBA').save(dst)
    print('saved', dst, (W, H), '(black background)')


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    main(args[0], args[1], black='--black' in sys.argv)
