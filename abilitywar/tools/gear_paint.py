"""RPG 장비 그림: 무기 아이콘 (32x32 픽셀 아트), 갑옷 아이콘, 착용 갑옷 텍스처 (128x64).

무기는 손잡이(왼쪽 아래) → 끝(오른쪽 위) 대각선 축 위의 (s, t) 좌표로 모양을 정의한다.
s = 축 방향 거리, t = 축에 수직 (+ 는 왼쪽 위 = 빛이 오는 쪽).
"""
import math
import random
import numpy as np

S = 32
P0 = np.array([3.5, 28.5])
P1 = np.array([28.5, 3.5])
AX = (P1 - P0) / np.linalg.norm(P1 - P0)
NX = np.array([-0.70710678, -0.70710678])
_yy, _xx = np.mgrid[0:S, 0:S].astype(np.float32)
_px, _py = _xx + 0.5 - P0[0], _yy + 0.5 - P0[1]
SS = _px * AX[0] + _py * AX[1]
TT = _px * NX[0] + _py * NX[1]


def c3(c, k=1.0):
    return np.array([min(255, c[0] * k), min(255, c[1] * k), min(255, c[2] * k)], np.float32)


def mix(a, b, k):
    return np.array(a, np.float32) * (1 - k) + np.array(b, np.float32) * k


def in_poly(pts, s=SS, t=TT):
    """(s, t) 다각형 안쪽 마스크 (홀짝 규칙)."""
    inside = np.zeros(s.shape, bool)
    n = len(pts)
    for i in range(n):
        s1, t1 = pts[i]
        s2, t2 = pts[(i + 1) % n]
        cond = (t1 > t) != (t2 > t)
        with np.errstate(divide='ignore', invalid='ignore'):
            xs = s1 + (t - t1) * (s2 - s1) / (t2 - t1 + 1e-9)
        inside ^= cond & (s < xs)
    return inside


class Canvas:
    def __init__(self):
        self.rgb = np.zeros((S, S, 3), np.float32)
        self.a = np.zeros((S, S), np.float32)
        self.part = np.zeros((S, S), np.int16)  # 0 = 비어 있음
        self.next_id = 1

    def put(self, mask, color):
        """mask 위치에 색 (color: (S,S,3) 배열 또는 (3,))."""
        col = np.broadcast_to(np.asarray(color, np.float32), (S, S, 3)) if np.ndim(color) == 1 else color
        self.rgb[mask] = col[mask]
        self.a[mask] = 1
        self.part[mask] = self.next_id
        self.next_id += 1


def shade3(pal, light, dark):
    """3단 음영: light/dark 마스크에 따라 밝음/중간/어두움."""
    out = np.broadcast_to(np.array(pal[1], np.float32), (S, S, 3)).copy()
    out[light] = pal[0]
    out[dark] = pal[2]
    return out


def cyl(pal, t, center, half):
    """원통 음영 (축 방향 막대): 위쪽(+t) 밝음, 아래쪽 어두움."""
    rel = (t - center) / max(0.3, half)
    return shade3(pal, rel > 0.3, rel < -0.35)


# ------------------------------------------------------------------ 무기 부품
def grip(cv, s0, s1, pal, w=1.25, wrap=None):
    m = (SS >= s0) & (SS <= s1) & (np.abs(TT) <= w)
    col = cyl(pal, TT, 0, w)
    if wrap is not None:
        band = ((np.floor(SS * 0.75) % 2) == 0) & m
        col[band] = col[band] * 0.78
    cv.put(m, col)
    return m


def pommel(cv, s, pal, kind, r=1.5, gem=None):
    if kind == 'diamond':
        m = (np.abs(SS - s) + np.abs(TT)) <= r * 1.2
    elif kind == 'ring':
        d = np.sqrt((SS - s) ** 2 + TT ** 2)
        m = (d <= r + 0.2) & (d >= r - 0.9)
    else:
        m = ((SS - s) ** 2 + TT ** 2) <= r * r
    cv.put(m, shade3(pal, (TT > 0.2) & m, (TT < -0.4) & m))
    if gem is not None and kind != 'ring':
        g = ((SS - s) ** 2 + TT ** 2) <= 0.55
        cv.put(g, c3(gem))


def guard(cv, s, pal, kind, half=4.2, thick=1.9, gem=None):
    ds = SS - s
    if kind == 'curved_up':  # 끝이 칼날 쪽으로 휨
        off = 0.08 * TT ** 2
        m = (ds - off >= 0) & (ds - off <= thick) & (np.abs(TT) <= half)
    elif kind == 'curved_down':
        off = -0.08 * TT ** 2
        m = (ds - off >= -0.3) & (ds - off <= thick) & (np.abs(TT) <= half)
    elif kind == 'disc':
        m = (ds >= 0) & (ds <= thick) & (np.abs(TT) <= half * 0.75)
        m |= ((ds - thick * 0.5) ** 2 + TT ** 2) <= (half * 0.55) ** 2
        m &= ds <= thick + 0.4
        m &= ds >= -0.4
    elif kind == 'winged':
        m = (ds >= 0) & (ds <= thick) & (np.abs(TT) <= half)
        # 날개처럼 칼날 쪽으로 뻗은 두 갈래
        for sg in (-1, 1):
            m |= in_poly([(s, sg * half * 0.5), (s + 3.2, sg * (half + 1.2)), (s + 1.2, sg * (half + 0.6)), (s + thick, sg * half * 0.4)])
    elif kind == 'cross':
        m = (ds >= 0) & (ds <= thick) & (np.abs(TT) <= half)
        for sg in (-1, 1):
            m |= ((ds - thick * 0.5) ** 2 + (TT - sg * half) ** 2) <= 1.3
    else:  # straight
        m = (ds >= 0) & (ds <= thick) & (np.abs(TT) <= half)
    cv.put(m, shade3(pal, m & (ds < thick * 0.45), m & (ds > thick * 0.75)))
    if gem is not None:
        g = ((ds - thick * 0.5) ** 2 + TT ** 2) <= 0.7
        cv.put(g, c3(gem))
    return m


def blade(cv, s0, s1, pal, w=1.9, taper=5.0, fuller=True, curve=0.0, single=False, serrate=False, wavy=0.0):
    ds = SS - s0
    L = s1 - s0
    center = curve * (np.clip(ds / L, 0, 1) ** 2) * -2.2
    wl = np.full(SS.shape, w, np.float32)
    k = np.clip((s1 - SS) / taper, 0, 1)
    wl = wl * np.sqrt(k)
    if wavy:
        wl = wl + wavy * np.sin(ds * 1.4) * (k > 0.6)
    wr = wl.copy()
    if single:  # 외날 (태도): 등 쪽은 얇게
        wl = np.minimum(wl, w * 0.55 + 0 * wl)
    rel = TT - center
    m = (SS >= s0) & (SS <= s1) & (rel <= wl) & (rel >= -wr)
    if serrate:
        m &= ~((rel < -wr + 0.6) & ((np.floor(SS * 1.1) % 2) == 0) & (SS < s1 - taper))
    light = rel > wl * 0.25
    dark = rel < -wr * 0.45
    col = shade3(pal, light, dark)
    # 칼날 가장자리 빛 (한쪽 날)
    edge = m & (rel > wl - 0.75)
    col[edge] = mix(pal[0], (255, 255, 255), 0.35)
    if fuller:
        f = m & (np.abs(rel) < 0.45) & (SS > s0 + 1) & (SS < s1 - taper - 0.5)
        col[f] = mix(pal[1], pal[2], 0.55)
    cv.put(m, col)
    return m, center


def shaft(cv, s0, s1, pal, w=1.2, rings=None, ringpal=None):
    m = (SS >= s0) & (SS <= s1) & (np.abs(TT) <= w)
    col = cyl(pal, TT, 0, w)
    if rings:
        for rs in rings:
            r = m & (np.abs(SS - rs) < 0.7)
            col[r] = cyl(ringpal, TT, 0, w)[r] * 1.05
    cv.put(m, col)
    return m


def poly_part(cv, pts, pal, edge_light=None):
    m = in_poly(pts)
    sc = np.mean([p[0] for p in pts])
    tc = np.mean([p[1] for p in pts])
    light = (TT - tc) > 0.8
    dark = (TT - tc) < -1.2
    col = shade3(pal, light, dark)
    if edge_light is not None:
        e = m & edge_light
        col[e] = mix(pal[0], (255, 255, 255), 0.4)
    cv.put(m, col)
    return m


def orb(cv, s, t, r, color):
    d2 = (SS - s) ** 2 + (TT - t) ** 2
    m = d2 <= r * r
    col = np.broadcast_to(c3(color), (S, S, 3)).copy()
    col[m & ((TT - t) < -r * 0.3)] = c3(color, 0.62)
    hl = m & (((SS - s - r * 0.15) ** 2 + (TT - t - r * 0.45) ** 2) <= (r * 0.38) ** 2)
    col[hl] = mix(color, (255, 255, 255), 0.75)
    cv.put(m, col)
    return m


# ------------------------------------------------------------------ 무기 전체
def paint_weapon(kind, mat, hand, gem, tier, rng, extra=None):
    """tier: 0 일반 ~ 7 ?. 반환: (S,S,4) uint8 프레임 목록."""
    cv = Canvas()
    gem_c = gem
    trim = mat if tier < 4 else ((255, 236, 140), (240, 190, 60), (170, 110, 20))
    if extra and extra.get('trim'):
        trim = extra['trim']
    gkinds = ['straight', 'curved_up', 'curved_down', 'cross', 'disc']
    if tier >= 3:
        gkinds = ['winged', 'curved_up', 'cross', 'winged']
    gk = rng.choice(gkinds)
    pk = rng.choice(['round', 'diamond', 'round', 'ring'])
    wv = rng.uniform(-0.25, 0.3)
    has_gem = gem is not None and tier >= 1
    blade_mask = None
    center = 0
    if kind == 'sword':
        pommel(cv, 1.6, trim, pk, r=1.9, gem=gem_c if has_gem else None)
        grip(cv, 2.6, 9.6, hand, wrap=True)
        blade_mask, center = blade(cv, 11.2, 34.6, mat, w=2.45 + wv, taper=rng.uniform(4.0, 6.5), fuller=rng.random() < 0.7, serrate=tier == 0 and rng.random() < 0.3)
        guard(cv, 9.6, trim, gk, half=rng.uniform(4.6, 5.6) + tier * 0.15, gem=gem_c if has_gem and tier >= 2 else None)
    elif kind == 'greatsword':
        pommel(cv, 1.4, trim, pk, r=2.0, gem=gem_c if has_gem else None)
        grip(cv, 2.4, 10.4, hand, w=1.35, wrap=True)
        blade_mask, center = blade(cv, 12.4, 35.0, mat, w=3.4 + wv, taper=rng.uniform(3.0, 5.0), fuller=True, wavy=0.5 if rng.random() < 0.25 else 0)
        guard(cv, 10.4, trim, gk, half=6.4 + tier * 0.15, thick=2.0, gem=gem_c if has_gem else None)
    elif kind == 'dagger':
        pommel(cv, 5.2, trim, pk, r=1.7, gem=gem_c if has_gem else None)
        grip(cv, 6.0, 12.0, hand, w=1.2, wrap=True)
        blade_mask, center = blade(cv, 13.6, 29.0, mat, w=2.3 + wv * 0.5, taper=rng.uniform(5, 8), fuller=rng.random() < 0.5, curve=rng.choice([0, 0, 0.8]))
        guard(cv, 12.0, trim, rng.choice(['straight', 'curved_up', 'cross']), half=4.0, thick=1.6, gem=gem_c if has_gem and tier >= 2 else None)
    elif kind == 'katana':
        pommel(cv, 1.4, trim, 'round', r=1.4)
        grip(cv, 2.0, 10.6, hand, w=1.25, wrap=True)
        blade_mask, center = blade(cv, 12.4, 35.2, mat, w=1.9, taper=3.4, fuller=False, curve=1.0, single=True)
        guard(cv, 10.6, trim, 'disc', half=4.0, thick=1.6, gem=gem_c if has_gem and tier >= 2 else None)
    elif kind == 'rapier':
        pommel(cv, 2.4, trim, 'round', r=1.6, gem=gem_c if has_gem else None)
        grip(cv, 3.4, 9.8, hand, w=1.15, wrap=True)
        blade_mask, center = blade(cv, 11.0, 35.4, mat, w=1.15, taper=4, fuller=False)
        # 손을 감싸는 고리 가드
        d = np.sqrt((SS - 8.0) ** 2 + (TT - 2.0) ** 2)
        ring = (d <= 4.2) & (d >= 2.9) & (TT > -0.6)
        cv.put(ring, shade3(trim, ring & (TT > 2.5), ring & (TT < 0.8)))
        guard(cv, 10.0, trim, 'cross', half=3.8, thick=1.4, gem=gem_c if has_gem and tier >= 2 else None)
    elif kind == 'axe':
        shaft(cv, 0.8, 31.6, HANDLE_WOOD if hand is None else hand, rings=[3.0] if tier >= 1 else None, ringpal=trim)
        head = [(20.0, -1.2), (26.4, -1.2), (27.6, -4.0), (31.4, -8.4), (30.0, -11.4), (24.0, -12.6), (17.8, -11.6), (15.8, -8.4), (19.0, -4.0)]
        if rng.random() < 0.4:  # 수염 도끼
            head = [(21.0, -1.2), (27.0, -1.2), (28.4, -3.6), (31.4, -6.4), (30.8, -10.6), (24.0, -12.4), (16.4, -12.0), (16.8, -9.6), (20.4, -5.0)]
        hm = in_poly(head)
        rel = np.sqrt((SS - 23.4) ** 2 + (TT + 1.0) ** 2)
        poly_part(cv, head, mat, edge_light=rel > 9.8)
        if rng.random() < 0.45 or tier >= 3:  # 양날
            back = [(21.0, 1.2), (25.8, 1.2), (26.8, 3.0), (28.6, 6.0), (23.4, 7.4), (18.2, 6.0), (20.0, 3.0)]
            poly_part(cv, back, mat, edge_light=np.sqrt((SS - 23.4) ** 2 + (TT - 1.0) ** 2) > 5.4)
        else:
            poly_part(cv, [(20.5, 1.2), (26.5, 1.2), (25.4, 4.0), (21.6, 4.0)], trim)
        bnd = hm & (TT > -2.6)
        cv.rgb[bnd] = c3(trim[1]) if tier >= 2 else cv.rgb[bnd] * 0.85
        blade_mask = hm
    elif kind == 'hammer':
        shaft(cv, 0.8, 29.0, hand if hand else HANDLE_WOOD, w=1.25, rings=[2.6, 19.0], ringpal=trim)
        hw = rng.uniform(5.6, 6.6)
        head = [(21.6, -hw), (30.2, -hw), (31.0, -hw + 1.0), (31.0, hw - 1.0), (30.2, hw), (21.6, hw), (20.8, hw - 1.0), (20.8, -hw + 1.0)]
        hm = poly_part(cv, head, mat)
        band = hm & (np.abs(SS - 25.9) < 1.0)
        cv.rgb[band] = c3(trim[1])
        for sg in (-1, 1):
            face = hm & (np.abs(TT - sg * (hw - 0.6)) < 0.6)
            cv.rgb[face] = c3(mat[0] if sg > 0 else mat[2])
        if has_gem:
            g = ((SS - 25.9) ** 2 + TT ** 2) <= 1.8
            cv.put(g, c3(gem_c))
        blade_mask = hm
    elif kind == 'mace':
        shaft(cv, 0.8, 24.0, hand if hand else HANDLE_WOOD, w=1.25, rings=[2.6, 21.0], ringpal=trim)
        n = rng.choice([6, 8])
        for i in range(n):
            a = i * math.tau / n
            ps, pt = 28.0 + math.cos(a) * 6.4, math.sin(a) * 6.4
            sp = in_poly([(28.0 + math.cos(a - 0.4) * 3.8, math.sin(a - 0.4) * 3.8), (ps, pt), (28.0 + math.cos(a + 0.4) * 3.8, math.sin(a + 0.4) * 3.8)])
            cv.put(sp, shade3(trim, sp & (TT > 1), sp & (TT < -1)))
        head = ((SS - 28.0) ** 2 + TT ** 2) <= 4.4 ** 2
        cv.put(head, shade3(mat, head & (TT > 0.8), head & (TT < -1.0)))
        if has_gem:
            g = ((SS - 28.0) ** 2 + TT ** 2) <= 1.6
            cv.put(g, c3(gem_c))
        blade_mask = head
    elif kind == 'spear':
        shaft(cv, 0.4, 25.0, hand if hand else HANDLE_WOOD, w=1.1, rings=[22.6], ringpal=trim)
        ds = SS - 23.6
        wprof = 3.0 * np.sin(np.clip(ds / 11.8, 0, 1) * math.pi) ** 0.75 + 0.8 * (ds < 1.4)
        hm = (ds >= 0) & (ds <= 11.8) & (np.abs(TT) <= wprof)
        cv.put(hm, shade3(mat, hm & (TT > 0.4), hm & (TT < -0.6)))
        mid = hm & (np.abs(TT) < 0.5) & (ds > 1.5) & (ds < 10)
        cv.rgb[mid] = c3(mat[0])
        if tier >= 2:  # 날개 달린 창날
            for sg in (-1, 1):
                wing = in_poly([(23.4, sg * 1.0), (26.6, sg * 4.8), (24.6, sg * 4.4), (22.6, sg * 1.2)])
                cv.put(wing, c3(trim[1]))
        blade_mask = hm
    elif kind == 'scythe':
        shaft(cv, 0.4, 33.2, hand if hand else HANDLE_WOOD, w=1.1, rings=[2.0, 30.6], ringpal=trim)
        outer = [(33.2, -0.8), (33.2, -5.0), (31.0, -9.4), (27.4, -12.6), (22.0, -14.2), (16.6, -14.2)]
        inner = [(16.6, -14.2), (21.6, -11.4), (25.4, -8.6), (27.8, -5.2), (28.8, -0.8)]
        pts = outer + inner
        hm = in_poly(pts)
        col = shade3(mat, np.zeros_like(hm), np.zeros_like(hm))
        # 바깥쪽 날은 밝게
        rel = np.sqrt((SS - 19) ** 2 + (TT + 0) ** 2)
        col[hm & (rel > 14.8)] = mix(mat[0], (255, 255, 255), 0.3)
        col[hm & (rel < 12.4)] = c3(mat[2])
        cv.put(hm, col)
        blade_mask = hm
    elif kind == 'staff':
        shaft(cv, 0.4, 27.0, hand if hand else HANDLE_WOOD, w=1.15, rings=[8.0, 25.4], ringpal=trim)
        # 구슬을 감싼 갈퀴
        for sg in (-1, 1):
            cl = in_poly([(25.4, sg * 0.8), (28.0, sg * 4.6), (32.0, sg * 4.8), (34.6, sg * 2.8), (33.2, sg * 2.6), (31.4, sg * 3.4), (28.8, sg * 3.2), (26.2, sg * 0.2)])
            cv.put(cl, shade3(trim, cl & (TT > 0), cl & (TT < 0)))
        orb(cv, 30.4, 0, 3.4, gem_c or (200, 220, 255))
        blade_mask = ((SS - 30.4) ** 2 + TT ** 2) <= 3.4 ** 2
    elif kind == 'wand':
        m = (SS >= 5) & (SS <= 25) & (np.abs(TT) <= 1.2 - (SS - 5) * 0.012)
        cv.put(m, cyl(hand if hand else HANDLE_WOOD, TT, 0, 0.9))
        g = (SS >= 5) & (SS <= 12) & (np.abs(TT) <= 1.3)
        col = cyl(trim, TT, 0, 1.3)
        band = g & ((np.floor(SS * 0.8) % 2) == 0)
        col[band] = col[band] * 0.8
        cv.put(g, col)
        pommel(cv, 4.4, trim, 'round', r=1.6)
        cr = in_poly([(23.6, 0), (27.4, 2.8), (34.6, 0), (27.4, -2.8)])
        cc = gem_c or (200, 220, 255)
        col = np.broadcast_to(c3(cc), (S, S, 3)).copy()
        col[cr & (TT < 0)] = c3(cc, 0.65)
        col[cr & (TT > 0.8) & (SS > 26) & (SS < 30)] = mix(cc, (255, 255, 255), 0.7)
        cv.put(cr, col)
        for sg in (-1, 1):
            p = in_poly([(23.0, sg * 0.6), (25.0, sg * 3.4), (26.0, sg * 3.0), (24.4, sg * 0.6)])
            cv.put(p, c3(trim[1]))
        blade_mask = cr
    return finish_weapon(cv, mat, gem_c, tier, rng, blade_mask, extra)


HANDLE_WOOD = ((150, 104, 62), (106, 72, 40), (66, 42, 22))


def outline(rgb, a, dark):
    """실루엣 가장자리 픽셀을 어둡게 (마인크래프트 아이템 느낌)."""
    m = a > 0.5
    edge = np.zeros_like(m)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        sh = np.zeros_like(m)
        ys = slice(max(0, dy), S + min(0, dy))
        yd = slice(max(0, -dy), S + min(0, -dy))
        xs = slice(max(0, dx), S + min(0, dx))
        xd = slice(max(0, -dx), S + min(0, -dx))
        sh[yd, xd] = m[ys, xs]
        edge |= m & ~sh
    out = rgb.copy()
    out[edge] = out[edge] * 0.42 + np.array(dark, np.float32) * 0.2
    return out


def aura(a, radius):
    """실루엣 바깥쪽 빛 번짐 마스크 (0..1)."""
    m = (a > 0.5).astype(np.float32)
    H, W = m.shape
    pad = np.pad(m, radius)
    acc = np.zeros_like(m)
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            d = math.hypot(dx, dy)
            if d == 0 or d > radius + 0.3:
                continue
            sh = pad[radius + dy:radius + dy + H, radius + dx:radius + dx + W]
            acc = np.maximum(acc, sh * (1 - (d - 1) / radius))
    return acc * (1 - m)


def finish_weapon(cv, mat, gem, tier, rng, blade_mask, extra):
    rgb = outline(cv.rgb, cv.a, mat[2])
    a = cv.a.copy()
    accent = np.array(gem if gem else mat[0], np.float32)
    if extra and extra.get('accent'):
        accent = np.array(extra['accent'], np.float32)
    # 희귀 이상: 칼날에 룬 (강조색 점)
    if tier >= 2 and blade_mask is not None:
        idx = np.argwhere(blade_mask & (np.abs(TT) < 0.6))
        if len(idx):
            for i in rng.sample(range(len(idx)), min(len(idx), 2 + tier)):
                y, x = idx[i]
                rgb[y, x] = mix(accent, (255, 255, 255), 0.35)
    # 시크릿: 칼날에 별
    if extra and extra.get('stars') and blade_mask is not None:
        idx = np.argwhere(blade_mask)
        for i in rng.sample(range(len(idx)), min(len(idx), 7)):
            y, x = idx[i]
            rgb[y, x] = (255, 255, 255)
    frames = []
    nfr = 1 if tier < 5 else 8
    for f in range(nfr):
        r2, a2 = rgb.copy(), a.copy()
        if tier >= 4:  # 전설 이상: 빛 번짐
            rad = 1 if tier == 4 else 2
            g = aura(a, rad)
            pulse = 1.0 if nfr == 1 else 0.75 + 0.25 * math.sin(f / nfr * math.tau)
            ga = g * (0.55 if tier == 4 else 0.7) * pulse
            sel = ga > 0.02
            r2[sel] = prism(f, nfr) if tier >= 8 else accent
            a2[sel] = ga[sel]
        if nfr > 1 and tier != 7:
            # 빛줄기가 칼날을 따라 지나감
            pos = -4 + f / nfr * 44
            band = np.exp(-((SS - pos) / 2.2) ** 2) * (a > 0.5)
            r2 = r2 + (255 - r2) * band[..., None] * 0.65
        if tier == 7:
            r2, a2 = glitch(r2, a2, f, rng)
        out = np.zeros((S, S, 4), np.float32)
        out[..., :3] = np.clip(r2, 0, 255)
        out[..., 3] = np.clip(a2, 0, 1) * 255
        frames.append(out.astype(np.uint8))
    return frames


def prism(f, nfr):
    """인피니티 등급: 프레임마다 색이 바뀌는 무지개빛."""
    import colorsys
    r, g, b = colorsys.hsv_to_rgb((f / nfr) % 1.0, 0.55, 1.0)
    return np.array((r * 255, g * 255, b * 255), np.float32)


def glitch(rgb, a, f, rng):
    r = random.Random(f * 7919 + 13)
    out, oa = rgb.copy(), a.copy()
    for _ in range(3 + f % 3):
        y = r.randrange(S)
        h = r.randint(1, 3)
        dx = r.choice([-3, -2, -1, 1, 2, 3])
        out[y:y + h] = np.roll(out[y:y + h], dx, 1)
        oa[y:y + h] = np.roll(oa[y:y + h], dx, 1)
    # 색 어긋남
    shift = np.roll(out[..., 0], 1 if f % 2 else -1, 1)
    out[..., 0] = np.where(oa > 0.5, shift, out[..., 0])
    if f % 4 == 1:
        m = oa > 0.5
        out[m] = 255 - out[m] * 0.6
    return out, oa


# ================================================================== 갑옷 아이콘 (32x32)
def _grid():
    return np.mgrid[0:S, 0:S].astype(np.float32)


def poly_xy(pts):
    yy, xx = _grid()
    return in_poly([(p[0], p[1]) for p in pts], xx + 0.5, yy + 0.5)


def pattern(style, xx, yy, seed=0):
    """스타일 무늬 (0 = 기본, 음수 = 어둡게, 양수 = 밝게)."""
    p = np.zeros_like(xx)
    if style == 'chain':
        p = np.where(((xx.astype(int) + yy.astype(int)) % 2 == 0), 0.15, -0.25)
    elif style in ('scale', 'dragon'):
        sy = (yy // 3).astype(int)
        sx = ((xx + (sy % 2) * 1.5) // 3).astype(int)
        cy = sy * 3 + 1.0
        cx = sx * 3 + 1.5 - (sy % 2) * 1.5
        d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
        p = np.where(d > 1.3, -0.3, 0.1)
    elif style == 'leather':
        p = np.where((xx.astype(int) % 5 == 0) & (yy.astype(int) % 2 == 0), -0.25, 0)
    elif style == 'robe':
        p = np.where(yy.astype(int) % 4 == 0, -0.1, 0)
    elif style == 'crystal':
        p = np.where(((xx - yy) % 6) < 1.0, 0.35, np.where(((xx + yy) % 7) < 1.0, -0.2, 0))
    elif style == 'bone':
        p = np.where(yy.astype(int) % 3 == 0, -0.3, 0.05)
    elif style == 'assassin':
        p = np.where(((xx + yy * 2) % 6) < 1.0, -0.2, 0)
    return p


def paint_armor_icon(piece, style, mat, trim, gem, tier, rng):
    yy, xx = _grid()
    if piece == 'helmet':
        if style in ('robe',):
            body = [(6, 26), (6, 13), (9, 7), (16, 4), (23, 7), (26, 13), (26, 26), (21, 26), (21, 15), (16, 12), (11, 15), (11, 26)]
            hole = [(11, 26), (11, 16), (16, 13), (21, 16), (21, 26)]
        else:
            body = [(5, 25), (5, 13), (8, 7), (16, 3), (24, 7), (27, 13), (27, 25), (21, 25), (21, 17), (11, 17), (11, 25)]
            hole = [(11, 25), (11, 17), (21, 17), (21, 25)]
        m = poly_xy(body)
    elif piece == 'chestplate':
        body = [(2, 7), (9, 4), (12, 7), (20, 7), (23, 4), (30, 7), (30, 15), (25, 15), (25, 28), (7, 28), (7, 15), (2, 15)]
        if style == 'robe':
            body = [(2, 7), (9, 4), (12, 7), (20, 7), (23, 4), (30, 7), (30, 17), (25, 17), (26, 30), (6, 30), (7, 17), (2, 17)]
        m = poly_xy(body)
        neck = poly_xy([(12, 7), (20, 7), (18, 11), (14, 11)])
        m &= ~neck
    elif piece == 'leggings':
        body = [(7, 4), (25, 4), (26, 29), (19, 29), (17, 12), (15, 12), (13, 29), (6, 29)]
        m = poly_xy(body)
    else:  # boots
        m = poly_xy([(4, 12), (12, 12), (12, 22), (14, 23), (14, 28), (3, 28), (3, 23)]) | poly_xy([(18, 12), (26, 12), (26, 22), (28, 23), (28, 28), (17, 28), (17, 23)])
        m = m | poly_xy([(4, 12), (12, 12), (12, 22), (14, 23), (14, 28), (3, 28), (3, 23)])
    base = np.broadcast_to(np.array(mat[1], np.float32), (S, S, 3)).copy()
    # 원통형 음영: 왼쪽 위 밝게
    cx = 16
    k = np.clip((xx - cx) / 14, -1, 1)
    shade = 1.12 - 0.28 * (k + 1) / 2 - 0.12 * (yy / S)
    pat = pattern(style, xx, yy)
    rgb = base * (shade + pat)[..., None]
    # 장식 (테두리 / 중앙선 / 띠)
    tr = np.array(trim[1], np.float32)
    deco = np.zeros_like(m)
    if piece == 'helmet':
        deco = poly_xy([(15, 3), (17, 3), (17, 17), (15, 17)]) if style in ('knight', 'plate', 'angel', 'demon', 'dragon') else deco
        deco |= poly_xy([(5, 13), (27, 13), (27, 14.5), (5, 14.5)]) & (tier >= 2)
    elif piece == 'chestplate':
        if style in ('plate', 'knight', 'angel', 'demon', 'crystal', 'dragon'):
            deco = poly_xy([(15, 11), (17, 11), (17, 27), (15, 27)])
        deco |= poly_xy([(7, 24), (25, 24), (25, 25.5), (7, 25.5)])
        if style == 'robe':
            deco = poly_xy([(14, 11), (18, 11), (18, 30), (14, 30)])
    elif piece == 'leggings':
        deco = poly_xy([(7, 4), (25, 4), (25, 7), (7, 7)])
    else:
        deco = poly_xy([(3, 23), (14, 23), (14, 24.5), (3, 24.5)]) | poly_xy([(17, 23), (28, 23), (28, 24.5), (17, 24.5)])
    deco &= m
    rgb[deco] = tr * shade[deco][..., None] * 1.05
    # 스타일 특징
    if style in ('dragon', 'demon') and piece == 'helmet':
        for sg in (-1, 1):
            horn = poly_xy([(16 + sg * 7, 8), (16 + sg * 13, 1), (16 + sg * 9, 9)]) if sg > 0 else poly_xy([(16 + sg * 7, 8), (16 + sg * 9, 9), (16 + sg * 13, 1)])
            rgb[horn] = np.array(trim[0] if style == 'dragon' else (40, 30, 40), np.float32)
            m = m | horn
    if style == 'angel' and piece == 'helmet':
        for sg in (-1, 1):
            wing = poly_xy([(16 + sg * 10, 10), (16 + sg * 15, 4), (16 + sg * 14, 12)]) if sg > 0 else poly_xy([(16 + sg * 10, 10), (16 + sg * 14, 12), (16 + sg * 15, 4)])
            rgb[wing] = (250, 250, 255)
            m = m | wing
    if gem is not None and tier >= 2:
        gp = {'helmet': (16, 10), 'chestplate': (16, 15), 'leggings': (16, 5.5), 'boots': (8, 18)}[piece]
        g = ((xx + 0.5 - gp[0]) ** 2 + (yy + 0.5 - gp[1]) ** 2) <= 2.2
        g &= m
        rgb[g] = np.array(gem, np.float32)
        rgb[g & (yy + 0.5 < gp[1])] = mix(gem, (255, 255, 255), 0.5)
    a = m.astype(np.float32)
    rgb = outline(np.clip(rgb, 0, 255), a, mat[2])
    frames = []
    nfr = 1 if tier < 5 else 8
    accent = np.array(gem if gem else trim[0], np.float32)
    for f in range(nfr):
        r2, a2 = rgb.copy(), a.copy()
        if tier >= 4:
            g = aura(a, 1 if tier == 4 else 2)
            pulse = 1.0 if nfr == 1 else 0.75 + 0.25 * math.sin(f / nfr * math.tau)
            ga = g * (0.5 if tier == 4 else 0.65) * pulse
            sel = ga > 0.02
            r2[sel] = prism(f, nfr) if tier >= 8 else accent
            a2[sel] = ga[sel]
        if nfr > 1 and tier != 7:
            pos = -6 + f / nfr * 50
            band = np.exp(-(((xx + yy) * 0.7 - pos) / 2.5) ** 2) * (a > 0.5)
            r2 = r2 + (255 - r2) * band[..., None] * 0.55
        if tier == 7:
            r2, a2 = glitch(r2, a2, f, rng)
        out = np.zeros((S, S, 4), np.float32)
        out[..., :3] = np.clip(r2, 0, 255)
        out[..., 3] = np.clip(a2, 0, 1) * 255
        frames.append(out.astype(np.uint8))
    return frames


# ================================================================== 착용 갑옷 텍스처 (64x32 배치를 2배 = 128x64)
# (x, y, 가로, 세로, 깊이): 상자 펼친 그림의 원점
HEAD = (0, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
ARM = (40, 16, 4, 12, 4)
LEG = (0, 16, 4, 12, 4)


def faces(box):
    """상자 펼친 그림의 면: 이름 → (x0, y0, x1, y1)."""
    x, y, w, h, d = box
    return {
        'top': (x + d, y, x + d + w, y + d), 'bottom': (x + d + w, y, x + d + 2 * w, y + d),
        'right': (x, y + d, x + d, y + d + h), 'front': (x + d, y + d, x + d + w, y + d + h),
        'left': (x + d + w, y + d, x + 2 * d + w, y + d + h), 'back': (x + 2 * d + w, y + d, x + 2 * d + 2 * w, y + d + h),
    }


SC = 2  # 2배 해상도


def paint_armor_layers(style, mat, trim, gem, tier, rng):
    W, H = 64 * SC, 32 * SC
    L1 = np.zeros((H, W, 4), np.float32)
    L2 = np.zeros((H, W, 4), np.float32)
    E1 = np.zeros((H, W, 4), np.float32)  # 빛나는 부분 (전설 이상)
    E2 = np.zeros((H, W, 4), np.float32)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    pat = pattern(style, xx / SC * 1.0, yy / SC * 1.0)
    base = np.array(mat[1], np.float32)
    trimc = np.array(trim[1], np.float32)
    glow = np.array(gem if gem else trim[0], np.float32)
    seed = rng.random()

    def fill(img, rect, y_from=0.0, y_to=1.0, col=None, shade_top=1.08, patt=True):
        x0, y0, x1, y1 = [v * SC for v in rect]
        hh = y1 - y0
        ya, yb = int(y0 + hh * y_from), int(y0 + hh * y_to)
        if yb <= ya:
            return
        c = base if col is None else col
        region = (slice(ya, yb), slice(x0, x1))
        ny = (yy[region] - y0) / max(1, hh)
        sh = shade_top - 0.18 * ny
        p = pat[region] if patt else 0
        rgbv = c[None, None, :] * (sh + p)[..., None]
        img[region][..., :3] = np.clip(rgbv, 0, 255)
        img[region][..., 3] = 255

    def border(img, rect, col, y_from=0.0, y_to=1.0, w=1):
        x0, y0, x1, y1 = [v * SC for v in rect]
        hh = y1 - y0
        ya, yb = int(y0 + hh * y_from), int(y0 + hh * y_to)
        for (sy, sx) in ((slice(ya, ya + w), slice(x0, x1)), (slice(yb - w, yb), slice(x0, x1)),
                         (slice(ya, yb), slice(x0, x0 + w)), (slice(ya, yb), slice(x1 - w, x1))):
            img[sy, sx][..., :3] = img[sy, sx][..., :3] * 0.55
        return

    def band(img, rect, frac, thick, col):
        x0, y0, x1, y1 = [v * SC for v in rect]
        y = int(y0 + (y1 - y0) * frac)
        img[y:y + thick, x0:x1, :3] = col
        img[y:y + thick, x0:x1, 3] = 255

    def vline(img, rect, xfrac, thick, col, y_from=0.0, y_to=1.0):
        x0, y0, x1, y1 = [v * SC for v in rect]
        x = int(x0 + (x1 - x0) * xfrac - thick / 2)
        ya, yb = int(y0 + (y1 - y0) * y_from), int(y0 + (y1 - y0) * y_to)
        img[ya:yb, x:x + thick, :3] = col
        img[ya:yb, x:x + thick, 3] = 255

    def dot(img, rect, fx, fy, r, col):
        x0, y0, x1, y1 = [v * SC for v in rect]
        cx, cy = x0 + (x1 - x0) * fx, y0 + (y1 - y0) * fy
        m = ((xx - cx) ** 2 + (yy - cy) ** 2) <= r * r
        img[m, :3] = col
        img[m, 3] = 255
        return m

    # ---------------- 투구 (L1 머리)
    hf = faces(HEAD)
    for k, rect in hf.items():
        fill(L1, rect)
        border(L1, rect, None)
    front = hf['front']
    # 얼굴 부분: 스타일별로 비움 / 눈 틈
    x0, y0, x1, y1 = [v * SC for v in front]
    if style in ('robe', 'leather', 'assassin', 'angel'):
        # 얼굴이 드러남 (테두리만)
        L1[y0 + 3 * SC:y1, x0 + 1 * SC:x1 - 1 * SC, 3] = 0
        if style == 'assassin':  # 복면: 아래 절반 가림
            fill(L1, (front[0] + 1, front[1] + 5, front[2] - 1, front[3]), col=base * 0.8)
    else:
        # 눈 틈 + 가운데 코 가리개
        L1[y0 + 3 * SC:y0 + 4 * SC, x0 + 1 * SC:x1 - 1 * SC, :3] = 20
        if style in ('knight', 'plate', 'chain', 'crystal'):
            vline(L1, front, 0.5, SC, trimc, 0.2, 0.9)
        if style == 'chain':
            L1[y0 + 4 * SC:y1 - 1 * SC, x0 + 2 * SC:x1 - 2 * SC, 3] = 0
    band(L1, hf['front'], 0.0, SC, trimc)
    for k in ('right', 'left', 'back'):
        band(L1, hf[k], 0.0, SC, trimc)
    if tier >= 3:
        vline(L1, hf['top'], 0.5, SC, trimc)
    # 볏 / 뿔 / 날개 (모자 겹 층 = 32,0 영역): 간단히 머리 윗면 장식
    if gem is not None and tier >= 2:
        m = dot(L1, hf['front'], 0.5, 0.18, 1.2 * SC * 0.7, np.array(gem, np.float32))
        if tier >= 4:
            E1[m, :3] = glow
            E1[m, 3] = 255

    # ---------------- 흉갑 (L1 몸통 + 팔)
    bf = faces(BODY)
    for k, rect in bf.items():
        fill(L1, rect)
        border(L1, rect, None)
    if style == 'robe':
        vline(L1, bf['front'], 0.5, 2 * SC, trimc)
        band(L1, bf['front'], 0.58, SC, trimc * 0.8)
    elif style in ('plate', 'knight', 'angel', 'demon', 'crystal', 'dragon'):
        vline(L1, bf['front'], 0.5, SC, trimc * 1.05, 0.05, 0.75)
        band(L1, bf['front'], 0.0, SC, trimc)
        band(L1, bf['front'], 0.75, SC, trimc)
        band(L1, bf['back'], 0.75, SC, trimc)
        if style == 'knight':  # 가슴 문장
            m = dot(L1, bf['front'], 0.5, 0.35, 2.2 * SC, trimc)
            dot(L1, bf['front'], 0.5, 0.35, 1.2 * SC, np.array(gem if gem else mat[0], np.float32))
    elif style == 'leather':
        band(L1, bf['front'], 0.78, SC, trimc)
        vline(L1, bf['front'], 0.3, SC // 2 + 1, np.array(mat[2], np.float32), 0.1, 0.75)
    elif style == 'assassin':
        for i in range(3):
            band(L1, bf['front'], 0.2 + i * 0.25, SC // 2 + 1, np.array(mat[2], np.float32))
    elif style == 'chain':
        band(L1, bf['front'], 0.85, SC, trimc)
    elif style == 'bone':
        for i in range(4):
            band(L1, bf['front'], 0.15 + i * 0.18, SC // 2 + 1, np.array(mat[0], np.float32))
    if gem is not None and tier >= 2 and style != 'knight':
        m = dot(L1, bf['front'], 0.5, 0.3, 1.4 * SC * 0.7, np.array(gem, np.float32))
        if tier >= 4:
            E1[m, :3] = glow
            E1[m, 3] = 255
    af = faces(ARM)
    for k, rect in af.items():
        fill(L1, rect)
        border(L1, rect, None)
    # 어깨받이 + 손목
    for k in ('front', 'back', 'left', 'right'):
        band(L1, af[k], 0.0, 2 * SC if style in ('plate', 'knight', 'demon', 'dragon', 'angel') else SC, trimc)
        band(L1, af[k], 0.82, SC, trimc * 0.9)
    if style in ('robe',):
        # 넓은 소매 끝
        for k in ('front', 'back', 'left', 'right'):
            band(L1, af[k], 0.7, SC, trimc)
    # ---------------- 장화 (L1 다리 아래쪽)
    lf = faces(LEG)
    for k, rect in lf.items():
        if k in ('top',):
            continue
        if k == 'bottom':
            fill(L1, rect, col=np.array(mat[2], np.float32))
            continue
        fill(L1, rect, 0.58, 1.0)
        band(L1, rect, 0.58, SC, trimc)
        band(L1, rect, 0.92, SC, np.array(mat[2], np.float32) * 0.8)
    # ---------------- 각반 (L2 몸통 아래 + 다리)
    for k, rect in bf.items():
        if k == 'top':
            continue
        if k == 'bottom':
            fill(L2, rect)
            continue
        fill(L2, rect, 0.62, 1.0)
        band(L2, rect, 0.62, SC + 1, trimc)
    if gem is not None and tier >= 3:
        m = dot(L2, bf['front'], 0.5, 0.72, 1.1 * SC * 0.7, np.array(gem, np.float32))
        if tier >= 4:
            E2[m, :3] = glow
            E2[m, 3] = 255
    for k, rect in lf.items():
        if k == 'top':
            continue
        fill(L2, rect, 0.0, 0.8)
        border(L2, rect, None, 0.0, 0.8)
        if k == 'front':
            band(L2, rect, 0.42, SC, trimc)  # 무릎받이
    if style == 'robe':
        for k in ('front', 'back', 'left', 'right'):
            band(L2, lf[k], 0.0, SC, trimc)
    # 전설 이상: 가장자리 장식선이 빛남
    if tier >= 4:
        for img, em in ((L1, E1), (L2, E2)):
            tm = (np.abs(img[..., :3] - trimc[None, None, :]).sum(-1) < 30) & (img[..., 3] > 0)
            k = 0.5 if tier == 4 else 0.85
            em[tm, :3] = glow * k + trimc * (1 - k)
            em[tm, 3] = 255 * (0.5 if tier == 4 else 0.8)
    if tier == 7:
        for img in (L1, L2):
            for _ in range(10):
                y = rng.randrange(H)
                img[y:y + 2] = np.roll(img[y:y + 2], rng.choice([-4, -2, 2, 4]), 1)
    # 별 무늬 (시크릿 별 계열)
    return [x.clip(0, 255).astype(np.uint8) for x in (L1, L2, E1, E2)]


# ================================================================ 가져온 아이콘 애니메이션
def animate_icon(img, tier, rng):
    """가져온 32x32 아이콘: 신화·시크릿은 빛줄기가 지나가는 8장, ? 는 깨지는 8장, 나머지는 1장."""
    if tier < 5:
        return [img]
    rgb = img[..., :3].astype(np.float32)
    a = img[..., 3].astype(np.float32) / 255
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    frames = []
    for f in range(8):
        r2, a2 = rgb.copy(), a.copy()
        if tier == 7:
            r2, a2 = glitch(r2, a2, f, rng)
        else:
            pos = -6 + f / 8 * 50
            band = np.exp(-(((xx + (S - yy)) * 0.7 - pos) / 2.5) ** 2) * (a > 0.5)
            r2 = r2 + (255 - r2) * band[..., None] * 0.55
        out = np.zeros((S, S, 4), np.float32)
        out[..., :3] = np.clip(r2, 0, 255)
        out[..., 3] = np.clip(a2, 0, 1) * 255
        frames.append(out.astype(np.uint8))
    return frames
