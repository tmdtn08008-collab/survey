#!/usr/bin/env python3
"""
꾸민 장비 그림 (tools/art/gear_decorated/*.webp) 에서 아이콘을 잘라 게임 아이템 그림으로 가져옴.
  - 그림은 tools/gen_gear.py 가 만든 한눈에 보는 그림과 같은 배치라서, 칸마다 원래 위치 근처를 찾아 맞춤.
  - 배경(어두운 칸 + 반짝이)은 지우고 32x32 로 줄임. 결과는 tools/art/gear_icons/<아이템>.png
  - 그 다음 python3 tools/gen_gear.py 를 실행하면 이 그림이 원래 그림 대신 들어감.
사용: python3 tools/import_gear_art.py [시트이름 ...]
"""
import json, math, os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gear_data as D  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
DEC = os.path.join(ROOT, 'tools', 'art', 'gear_decorated')
OUT = os.path.join(ROOT, 'tools', 'art', 'gear_icons')
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'textures', 'item', 'gear')
GEAR = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'abilitywar', 'gear', 'gear.json')


def load_gear():
    d = json.load(open(GEAR, encoding='utf-8'))
    return d['weapons'], d['armors']


def weapon_boxes(n):
    """gen_gear.sheet_weapons 와 같은 배치: (아이콘 상자 목록, 그림 크기)."""
    cols = 10 if n > 10 else 5
    cw, ch = 152, 150
    rows = (n + cols - 1) // cols
    W, H = cols * cw + 20, rows * ch + 80
    boxes = []
    for i in range(n):
        x, y = 10 + (i % cols) * cw, 70 + (i // cols) * ch
        boxes.append((x + (cw - 96) // 2, y + 6, 96))
    return boxes, (W, H)


def armor_boxes(n):
    cols, cw, ch = 5, 300, 118
    rows = (n + cols - 1) // cols
    W, H = cols * cw + 20, rows * ch + 80
    boxes = []
    for i in range(n):
        x, y = 10 + (i % cols) * cw, 70 + (i // cols) * ch
        boxes.append([(x + 16 + k * 68, y + 8, 64) for k in range(4)])
    return boxes, (W, H)


_BASE = {}


def own_icon(item_id):
    """생성기로 그린 원래 아이콘 (가져온 그림이 아님): 모양 기준."""
    if not _BASE:
        import gen_gear as GG
        for w in GG.build_weapons():
            _BASE[w['id']] = ('w', w, None)
        for a in GG.build_armors():
            for pk, pc in a['pieces'].items():
                _BASE[pc['id']] = ('a', a, pk)
        _BASE['_gg'] = GG
    GG = _BASE['_gg']
    kind, d, pk = _BASE[item_id]
    fr = GG.base_weapon_frames(d) if kind == 'w' else GG.base_armor_icon_frames(d, pk)
    return fr[0].astype(np.float32) / 255


def fg_map(img):
    """배경(어두운 칸)과 다른 정도: 밝기 + 채도."""
    mx = img.max(2)
    mn = img.min(2)
    return np.clip((mx - 0.16) * 1.6 + (mx - mn) * 0.6, 0, 1)


def resize_f(a, w, h, filt=Image.BILINEAR):
    return np.asarray(Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).resize((max(1, int(w)), max(1, int(h))), filt), np.float32) / 255


class Sheet:
    """꾸민 그림 하나: 원본 + 작업용으로 줄인 밝기 지도."""

    def __init__(self, path, icon_px):
        self.img = np.asarray(Image.open(path).convert('RGB'), np.float32) / 255
        # 아이콘이 약 48칸이 되게 줄여서 찾음
        self.k = 48.0 / icon_px
        H, W = self.img.shape[:2]
        small = resize_f(self.img, W * self.k, H * self.k, Image.BOX)
        self.fg = fg_map(small)

    def locate(self, cx, cy, size, mask32, search, scales=(0.9, 1.0, 1.1)):
        """(cx, cy, size) = 원본 좌표 예상 위치. 내 실루엣이 가장 잘 맞는 곳 (점수, x0, y0, 크기) 원본 좌표."""
        k = self.k
        fg = self.fg
        H, W = fg.shape
        best = None
        for sc in scales:
            sz = max(6, int(round(size * sc * k)))
            m = resize_f(mask32, sz, sz)
            msum, osum = m.sum() + 1e-6, (1 - m).sum() + 1e-6
            r = int(size * k * search)
            for dy in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    x0, y0 = int(cx * k - sz / 2 + dx), int(cy * k - sz / 2 + dy)
                    if x0 < 0 or y0 < 0 or x0 + sz > W or y0 + sz > H:
                        continue
                    patch = fg[y0:y0 + sz, x0:x0 + sz]
                    score = (patch * m).sum() / msum - (patch * (1 - m)).sum() / osum
                    if best is None or score > best[0]:
                        best = (score, x0 / k, y0 / k, sz / k)
        return best

    def extract(self, x0, y0, sz, mask32, grow=2):
        x0, y0, sz = int(round(x0)), int(round(y0)), int(round(sz))
        crop = self.img[y0:y0 + sz, x0:x0 + sz]
        small = resize_f(crop, 32, 32, Image.BOX)
        fs = resize_f(fg_map(crop), 32, 32, Image.BOX)
        m = mask32 > 0.5
        g = m.copy()
        for _ in range(grow):
            n = g.copy()
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                sh = np.zeros_like(g)
                sh[max(0, dy):32 + min(0, dy), max(0, dx):32 + min(0, dx)] = g[max(0, -dy):32 + min(0, -dy), max(0, -dx):32 + min(0, -dx)]
                n |= sh
            g = n
        alpha = ((fs > 0.42) & g) | (m & (fs > 0.2))
        out = np.zeros((32, 32, 4), np.float32)
        out[..., :3] = small
        out[..., 3] = alpha
        return out, small


def mean_color(icon):
    a = icon[..., 3] > 0.5
    return icon[..., :3][a].mean(0) if a.any() else np.zeros(3)


def hue_changed(o, n):
    """색상이 크게 바뀌었는지 (밝기만 바뀐 건 무시)."""
    import colorsys
    ho, so, vo = colorsys.rgb_to_hsv(*o)
    hn, sn, vn = colorsys.rgb_to_hsv(*n)
    dh = min(abs(ho - hn), 1 - abs(ho - hn)) * 360
    return (dh > 45 and so > 0.25 and sn > 0.25) or abs(vo - vn) > 0.35


def save_icon(arr, item_id):
    os.makedirs(OUT, exist_ok=True)
    Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), 'RGBA').save(os.path.join(OUT, item_id + '.png'))


def palette_of(icon):
    """아이콘 색에서 갑옷 색 (밝음/중간/어두움) 과 장식 색을 뽑음 (k-평균 2개)."""
    px = icon[..., :3][icon[..., 3] > 0.5]
    if len(px) < 8:
        return None
    lum = px.max(1)
    px = px[lum > 0.12]
    c = np.stack([px[np.argmin(px.sum(1))], px[np.argmax(px.sum(1))]])
    for _ in range(10):
        d = ((px[:, None, :] - c[None]) ** 2).sum(-1)
        lab = d.argmin(1)
        for k in range(2):
            if (lab == k).any():
                c[k] = px[lab == k].mean(0)
    cnt = [(lab == k).sum() for k in range(2)]
    base, trim = (c[0], c[1]) if cnt[0] >= cnt[1] else (c[1], c[0])

    def tri(col):
        col = np.clip(col, 0.05, 1) * 255
        return [[int(v) for v in np.clip(col * 1.3, 0, 255)], [int(v) for v in col], [int(v) for v in col * 0.55]]
    return {'mat': tri(base), 'trim': tri(trim)}


# ================================================================ 시트별 설정
# grid: 내 시트와 같은 배치 (비율만 다름). cells: (x0, y0, x1, y1, 줄, 칸) 원본 좌표의 칸 영역.
SHEETS = {
    'weapons_hero': ('weapon', 'hero', 'grid', None),
    'weapons_mythic': ('weapon', 'mythic', 'grid', None),
    'weapons_legendary': ('weapon', 'legendary', 'match', (12, 75, 1697, 916, 6, 10)),
    'weapons_secret': ('weapon', 'secret', 'cells', (34, 217, 1668, 890, 2, 5)),
    'weapons_unknown': ('weapon', 'unknown', 'cells', (28, 201, 1680, 844, 2, 5)),
    'armor_good': ('armor', 'good', 'grid', None),
    'armor_rare': ('armor', 'rare', 'grid', None),
    'armor_hero': ('armor', 'hero', 'grid', None),
    'armor_legendary': ('armor', 'legendary', 'grid', None),
    'armor_mythic': ('armor', 'mythic', 'grid', None),
    'armor_secret': ('armor', 'secret', 'cells', (29, 271, 2103, 610, 2, 6)),
    'armor_unknown': ('armor', 'unknown', 'cells', (30, 357, 1641, 767, 2, 4)),
}
PIECES = ['helmet', 'chestplate', 'leggings', 'boots']


def fit_grid(sh, items, cols, cell_h, cell_w):
    """items: [(행, 열, [(cx, cy, size, mask), ...])] 원본 좌표 예상값.
    줄마다 같은 세로 이동, 칸마다 같은 가로 이동을 찾아서 (그림이 내려갈수록 밀리는 것) 보정한 예상값을 돌려줌."""
    rows = sorted(set(r for r, c, _ in items))
    dys = {}
    for r in rows:
        best = None
        for dy in np.arange(-cell_h * 0.45, cell_h * 0.45 + 1e-6, max(1.0, cell_h * 0.03)):
            tot = 0
            for rr, cc, parts in items:
                if rr != r:
                    continue
                for (cx, cy, size, mask) in parts:
                    res = sh.locate(cx, cy + dy, size, mask, 0.06, (1.0,))
                    tot += res[0] if res else -1
            if best is None or tot > best[0]:
                best = (tot, dy)
        dys[r] = best[1]
    dxs = {}
    for c in range(cols):
        best = None
        for dx in np.arange(-cell_w * 0.3, cell_w * 0.3 + 1e-6, max(1.0, cell_w * 0.02)):
            tot = 0
            for rr, cc, parts in items:
                if cc != c:
                    continue
                for (cx, cy, size, mask) in parts:
                    res = sh.locate(cx + dx, cy + dys[rr], size, mask, 0.06, (1.0,))
                    tot += res[0] if res else -1
            if best is None or tot > best[0]:
                best = (tot, dx)
        dxs[c] = best[1] if best else 0
    return dys, dxs


def cell_boxes(spec):
    x0, y0, x1, y1, rows, cols = spec
    cw, ch = (x1 - x0) / cols, (y1 - y0) / rows
    return [(x0 + (i % cols) * cw, y0 + (i // cols) * ch, cw, ch) for i in range(rows * cols)]


def run(name, report):
    kind, grade, mode, spec = SHEETS[name]
    W, A = load_gear()
    path = os.path.join(DEC, name + '.webp')
    done = []
    if kind == 'weapon':
        items = [w['id'] for w in W if w['grade'] == grade]
        boxes, (Wm, Hm) = weapon_boxes(len(items))
        if mode == 'grid':
            probe = Image.open(path)
            sx, sy = probe.width / Wm, probe.height / Hm
            sh = Sheet(path, 96 * (sx + sy) / 2)
            cols = 10 if len(items) > 10 else 5
            masks = {wid: own_icon(wid)[..., 3] for wid in items}
            grid = [(i // cols, i % cols, [((x + s / 2) * sx, (y + s / 2) * sy, s * (sx + sy) / 2, masks[wid])]) for i, (wid, (x, y, s)) in enumerate(zip(items, boxes))]
            dys, dxs = fit_grid(sh, grid, cols, 150 * sy, 152 * sx)
            for i, (wid, (x, y, s)) in enumerate(zip(items, boxes)):
                r, c = i // cols, i % cols
                sc, bx, by, bs = sh.locate((x + s / 2) * sx + dxs[c], (y + s / 2) * sy + dys[r], s * (sx + sy) / 2, masks[wid], 0.1)
                icon, _ = sh.extract(bx, by, bs, masks[wid])
                save_icon(icon, wid)
                done.append((wid, sc))
        else:
            cells = cell_boxes(spec)
            sh = Sheet(path, cells[0][2] * 0.6)
            if mode == 'cells':
                pairs = list(zip(items, cells))
            else:
                # 칸 수가 다름 (전설 60칸 / 무기 50개): 모양 + 색이 가장 비슷한 칸끼리 짝지음
                scores = []
                for ii, wid in enumerate(items):
                    own = own_icon(wid)
                    mask = own[..., 3]
                    oc = mean_color(own)
                    for jj, (cx, cy, cw, ch) in enumerate(cells):
                        sc, bx, by, bs = sh.locate(cx + cw / 2, cy + ch * 0.42, cw * 0.62, mask, 0.12, (0.95, 1.05))
                        icon, _ = sh.extract(bx, by, bs, mask)
                        col = np.abs(mean_color(icon) - oc).sum()
                        scores.append((sc - col * 0.35, ii, jj))
                scores.sort(reverse=True)
                used_i, used_j, pairs = set(), set(), []
                for sc, ii, jj in scores:
                    if ii in used_i or jj in used_j:
                        continue
                    used_i.add(ii)
                    used_j.add(jj)
                    pairs.append((items[ii], cells[jj], sc, jj))
                pairs = [(p[0], p[1]) for p in sorted(pairs, key=lambda p: items.index(p[0]))]
            for wid, (cx, cy, cw, ch) in pairs:
                mask = own_icon(wid)[..., 3]
                sc, bx, by, bs = sh.locate(cx + cw / 2, cy + ch * 0.42, cw * 0.62, mask, 0.18, (0.8, 0.9, 1.0, 1.1, 1.2))
                icon, _ = sh.extract(bx, by, bs, mask)
                save_icon(icon, wid)
                done.append((wid, sc))
    else:
        sets = [a for a in A if a['grade'] == grade]
        palettes = {}
        if mode == 'grid':
            boxes, (Wm, Hm) = armor_boxes(len(sets))
            probe = Image.open(path)
            sx, sy = probe.width / Wm, probe.height / Hm
            sh = Sheet(path, 64 * (sx + sy) / 2)
            masks = {st['pieces'][pk]['id']: own_icon(st['pieces'][pk]['id']) for st in sets for pk in PIECES}
            grid = []
            for i, (st, bx4) in enumerate(zip(sets, boxes)):
                parts = [((x + s / 2) * sx, (y + s / 2) * sy, s * (sx + sy) / 2, masks[st['pieces'][pk]['id']][..., 3]) for pk, (x, y, s) in zip(PIECES, bx4)]
                grid.append((i // 5, i % 5, parts))
            dys, dxs = fit_grid(sh, grid, 5, 118 * sy, 300 * sx)
            for i, (st, bx4) in enumerate(zip(sets, boxes)):
                r, c = i // 5, i % 5
                for pk, (x, y, s) in zip(PIECES, bx4):
                    pid = st['pieces'][pk]['id']
                    own = masks[pid]
                    sc, bx, by, bs = sh.locate((x + s / 2) * sx + dxs[c], (y + s / 2) * sy + dys[r], s * (sx + sy) / 2, own[..., 3], 0.1)
                    icon, _ = sh.extract(bx, by, bs, own[..., 3])
                    save_icon(icon, pid)
                    done.append((pid, sc))
                    if pk == 'chestplate' and hue_changed(mean_color(own), mean_color(icon)):
                        # 색이 많이 바뀐 세트는 착용 갑옷 색도 맞춤
                        pal = palette_of(icon)
                        if pal:
                            palettes[st['id']] = pal
        else:
            cells = cell_boxes(spec)
            sh = Sheet(path, cells[0][2] * 0.2)
            for st, (cx, cy, cw, ch) in zip(sets, cells):
                chest = None
                for k, pk in enumerate(PIECES):
                    pid = st['pieces'][pk]['id']
                    mask = own_icon(pid)[..., 3]
                    ex = cx + cw * (0.17 + k * 0.215)
                    sc, bx, by, bs = sh.locate(ex, cy + ch * 0.38, cw * 0.2, mask, 0.35, (0.8, 0.9, 1.0, 1.1, 1.2))
                    icon, _ = sh.extract(bx, by, bs, mask)
                    save_icon(icon, pid)
                    done.append((pid, sc))
                    if pk == 'chestplate':
                        chest = icon
                # 새로 그린 세트는 착용 갑옷 색도 아이콘에 맞춤
                pal = palette_of(chest)
                if pal:
                    palettes[st['id']] = pal
        if palettes:
            pf = os.path.join(OUT, 'palettes.json')
            cur = json.load(open(pf)) if os.path.exists(pf) else {}
            cur.update(palettes)
            json.dump(cur, open(pf, 'w'), indent=1)
    worst = sorted(done, key=lambda d: d[1])[:3]
    report.append((name, len(done), [(w, round(s, 2)) for w, s in worst]))


def main():
    names = sys.argv[1:] or list(SHEETS)
    report = []
    for n in names:
        run(n, report)
        print(report[-1], flush=True)


if __name__ == '__main__':
    main()
