#!/usr/bin/env python3
"""
Ability War 플레이어 애니메이션 작성 스크립트 -> assets/abilitywar/player_animations/*.json
시간 단위: 틱(1/20초). 게임 안에서 F3+T 로 다시 불러올 수 있다.

[3인칭 본] head, body, right_arm, left_arm, right_leg, left_leg, root
  rot = ModelPart 각도(도) [pitch, yaw, roll]
        팔: 앞으로 = pitch -90, 위로 = -180 / 오른팔 yaw - = 안쪽, roll + = 바깥 (왼팔은 반대)
        팔/머리는 몸통에 붙어서 같이 움직인다 (몸통은 엉덩이 기준 회전)
        몸통: pitch + = 앞으로 숙임, yaw - = 왼쪽으로 비틈(오른어깨가 앞으로)
        다리: pitch - = 앞으로
  pos = [오른쪽, 위, 앞] 픽셀
  root(몸 전체): rot [앞숙임+, 왼쪽회전+, 오른쪽기울기+] / pos [오른쪽, 위, 앞] 픽셀 / root_pivot = 회전 중심 높이(px)
[1인칭] fp.right_arm / fp.left_arm : pos [오른쪽, 위, 앞] 픽셀 (어깨), rot [위로+, 왼쪽+, 비틀기]
        fp.camera rot [pitch, yaw, roll] (화면 흔들림), fp.fov (시야각 변화)
"""
import json, os, math

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'abilitywar', 'player_animations')
os.makedirs(OUT, exist_ok=True)
BONES = ['head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg', 'root']


def rnd(v):
    return [round(x, 2) for x in v]


def seq(frames, rest=None):
    """frames: [(t, pose, ease)], pose = {bone: rot} 또는 {bone: {'rot':..., 'pos':...}}
    한 프레임이라도 등장한 본은 모든 프레임에 키를 만든다(빠진 프레임은 rest 값)."""
    rest = rest or {}
    used = {}
    for t, pose, *e in frames:
        for b, v in pose.items():
            d = v if isinstance(v, dict) else {'rot': v}
            for ch in d:
                used.setdefault(b, set()).add(ch)
    bones = {}
    for b, chans in used.items():
        bones[b] = {}
        for ch in chans:
            keys = {}
            last = None
            for t, pose, *e in frames:
                v = pose.get(b)
                val = None
                if v is not None:
                    d = v if isinstance(v, dict) else {'rot': v}
                    val = d.get(ch)
                if val is None:
                    r = rest.get(b)
                    if r is not None:
                        rd = r if isinstance(r, dict) else {'rot': r}
                        val = rd.get(ch)
                if val is None:
                    val = last if last is not None else [0, 0, 0]
                last = val
                ease = e[0] if e else 'inOutSine'
                keys[str(round(t, 3))] = rnd(val) + [ease]
            bones[b][ch] = keys
    return bones


def fpseq(frames):
    """frames: [(t, {'R': (pos, rot), 'L': (pos, rot), 'cam': rot, 'fov': v, 'vR': 0/1, 'vL': 0/1}, ease)]"""
    out = {}
    for key, name in (('R', 'right_arm'), ('L', 'left_arm')):
        if any(key in f[1] for f in frames):
            pk, rk, last = {}, {}, None
            for t, p, *e in frames:
                v = p.get(key, last)
                last = v
                ease = e[0] if e else 'inOutSine'
                pk[str(round(t, 3))] = rnd(v[0]) + [ease]
                rk[str(round(t, 3))] = rnd(v[1]) + [ease]
            out[name] = {'pos': pk, 'rot': rk}
        vk = 'v' + key
        if any(vk in f[1] for f in frames):
            out.setdefault(name, {})
            keys, last = {}, 1
            for t, p, *e in frames:
                last = p.get(vk, last)
                keys[str(round(t, 3))] = [last, 0, 0, 'step']
            out[name]['vis'] = keys
    if any('cam' in f[1] for f in frames):
        keys, last = {}, [0, 0, 0]
        for t, p, *e in frames:
            last = p.get('cam', last)
            keys[str(round(t, 3))] = rnd(last) + [e[0] if e else 'inOutSine']
        out['camera'] = {'rot': keys}
    if any('fov' in f[1] for f in frames):
        keys, last = {}, 0
        for t, p, *e in frames:
            last = p.get('fov', last)
            keys[str(round(t, 3))] = [round(last, 2), 0, 0, e[0] if e else 'inOutSine']
        out['fov'] = keys
    return out


def clip(length, bones=None, fp=None, loop=False, blend_in=2, blend_out=3, **kw):
    c = {'length': length, 'loop': loop, 'blend_in': blend_in, 'blend_out': blend_out}
    c.update(kw)
    if bones:
        c['bones'] = bones
    if fp:
        c['fp'] = fp
    return c


def cyc(length, base, amp, phase=0.0, n=8, harm=None):
    """주기 키프레임: base + amp*sin(2πt/L + phase) (+ 2배 주파수 성분)."""
    out = {}
    for i in range(n + 1):
        a = 2 * math.pi * i / n + phase
        s = math.sin(a)
        v = [base[k] + amp[k] * s for k in range(3)]
        if harm:
            s2 = math.sin(2 * a + harm[1])
            v = [v[k] + harm[0][k] * s2 for k in range(3)]
        out[str(round(length * i / n, 3))] = rnd(v) + ['smooth']
    return out


def add(a, b):
    return [a[i] + b[i] for i in range(3)]


def lerp(a, b, t):
    return [a[i] + (b[i] - a[i]) * t for i in range(3)]


def write(name, anims):
    with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf-8') as f:
        json.dump({'animations': anims}, f, ensure_ascii=False, indent=1)
    print(name, len(anims))


# =====================================================================================
#                                     공허 (void)
# =====================================================================================
V = {}

# ---- 1인칭 기준 자세 -------------------------------------------------------------------
GR = ([6.5, -13.5, 7.5], [36, 6, 50])        # 가드 오른팔
GL = ([-6.5, -13.5, 6.5], [34, -6, -50])     # 가드 왼팔
JAB_R = ([4, -9, 14], [18, 10, 25])          # 오른 잽 (중앙으로 뻗음)
JAB_L = ([-4, -9, 14], [18, -10, -25])
PALM_L = ([-3, -9.5, 13], [22, -14, -10])    # 손바닥 앞으로
PALM_R = ([3.5, -9.5, 13], [24, 12, 15])
UP_R = ([4, -4, 7], [82, 12, 50])            # 머리 위로 든 오른팔
UP_L = ([-4, -4, 6.5], [82, -12, -50])
DOWN_R = ([3.5, -11, 12], [-12, 10, 50])     # 내리찍은 오른팔
DOWN_L = ([-3.5, -11, 12], [-12, -10, -50])
BACK_R = ([9, -16, 2], [16, -6, 72])         # 뒤로 당긴 오른팔 (충전)
CROSS_R = ([7, -11, 9.5], [50, 38, 62])      # 막기 X자
CROSS_L = ([-7, -11.5, 9], [48, -38, -62])
LOW_R = ([6, -17, 6], [12, 4, 50])           # 아래로 내린 팔
LOW_L = ([-6, -17, 5.5], [10, -4, -50])
HIDE_R = ([8, -26, 2], [0, 0, 50])           # 화면 밖
HIDE_L = ([-8, -26, 2], [0, 0, -50])

# ---- 3인칭 기준 자세 -------------------------------------------------------------------
GUARD = {
    'body': [3, 10, 0],
    'right_arm': [-42, -24, 6],
    'left_arm': [-58, 18, -8],
    'right_leg': [9, 6, 4],
    'left_leg': [-11, -4, -3],
}

# ================================================================ 이동
V['void.idle'] = clip(60, loop=True, bones={
    'body': {'rot': cyc(60, [3, 10, 0], [1.2, 0, 0])},
    'right_arm': {'rot': cyc(60, [-42, -24, 6], [3, 0, 1.5], phase=0.3)},
    'left_arm': {'rot': cyc(60, [-58, 18, -8], [3, 0, -1.5], phase=0.7)},
    'right_leg': {'rot': {'0': [9, 6, 4]}},
    'left_leg': {'rot': {'0': [-11, -4, -3]}},
    'head': {'rot': cyc(60, [0, 0, 0], [1.2, 0, 0], phase=1.0)},
    'root': {'pos': cyc(60, [0, 0, 0], [0, -0.25, 0], harm=([0, 0, 0], 0))},
}, fp={
    'right_arm': {'pos': cyc(60, GR[0], [0, 0.35, 0.1]), 'rot': cyc(60, GR[1], [1.5, 0, 0])},
    'left_arm': {'pos': cyc(60, GL[0], [0, 0.35, 0.1], phase=0.7), 'rot': cyc(60, GL[1], [1.5, 0, 0], phase=0.7)},
})

V['void.walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [5, 6, 0], [0, 5, 0], phase=math.pi)},
    'right_arm': {'rot': cyc(20, [-36, -18, 6], [20, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-48, 14, -6], [20, 0, 0])},
    'right_leg': {'rot': cyc(20, [0, 0, 2], [34, 0, 0])},
    'left_leg': {'rot': cyc(20, [0, 0, -2], [34, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, 0, 0], [0, 0, 0], harm=([0, 0.45, 0], -math.pi / 2))},
}, fp={
    'right_arm': {'pos': cyc(20, GR[0], [0.3, 0.7, 0.9], phase=math.pi), 'rot': cyc(20, GR[1], [3, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, GL[0], [0.3, 0.7, 0.9]), 'rot': cyc(20, GL[1], [3, 0, 0])},
})

V['void.run'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [20, 0, 0], [0, 9, 0], phase=math.pi, harm=([1.5, 0, 0], 0))},
    'right_arm': {'rot': cyc(20, [-30, -6, 10], [58, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-30, 6, -10], [58, 0, 0])},
    'right_leg': {'rot': cyc(20, [-6, 0, 2], [52, 0, 0], harm=([-8, 0, 0], 0))},
    'left_leg': {'rot': cyc(20, [-6, 0, -2], [52, 0, 0], phase=math.pi, harm=([-8, 0, 0], 0))},
    'head': {'rot': {'0': [-14, 0, 0]}},
    'root': {'pos': cyc(20, [0, 0.4, 0], [0, 0, 0], harm=([0, 1.1, 0], -math.pi / 2)), 'rot': {'0': [4, 0, 0]}},
}, fp={
    'right_arm': {'pos': cyc(20, [7, -15, 6], [0.5, 2.6, 3.2], phase=math.pi), 'rot': cyc(20, [26, 4, 52], [16, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, [-7, -15, 5.5], [0.5, 2.6, 3.2]), 'rot': cyc(20, [26, -4, -52], [16, 0, 0])},
    'camera': {'rot': cyc(20, [0, 0, 0], [0, 0, 0.6], harm=([0.5, 0, 0], 0))},
    'fov': {'0': [4, 0, 0]},
})

V['void.sneak'] = clip(50, loop=True, bones={
    'body': {'rot': cyc(50, [26, 12, 0], [1.5, 0, 0]), 'pos': {'0': [0, -2, 0]}},
    'right_arm': {'rot': cyc(50, [-58, -20, 8], [3, 0, 0], phase=0.4)},
    'left_arm': {'rot': cyc(50, [-70, 16, -8], [3, 0, 0], phase=0.9)},
    'right_leg': {'rot': {'0': [16, 8, 6]}, 'pos': {'0': [0, 0, -1]}},
    'left_leg': {'rot': {'0': [-22, -6, -5]}, 'pos': {'0': [0, 0, 1]}},
    'head': {'rot': {'0': [-12, 0, 0]}},
    'root': {'pos': {'0': [0, -1, 0]}},
}, fp={
    'right_arm': {'pos': cyc(50, [6, -15, 8], [0, 0.3, 0]), 'rot': cyc(50, [30, 8, 52], [1.5, 0, 0])},
    'left_arm': {'pos': cyc(50, [-6, -15, 7.5], [0, 0.3, 0], phase=0.6), 'rot': cyc(50, [28, -8, -52], [1.5, 0, 0], phase=0.6)},
    'fov': {'0': [-3, 0, 0]},
})

V['void.sneak_walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [26, 10, 0], [0, 4, 0]), 'pos': {'0': [0, -2, 0]}},
    'right_arm': {'rot': cyc(20, [-58, -18, 8], [8, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-70, 14, -8], [8, 0, 0])},
    'right_leg': {'rot': cyc(20, [4, 6, 6], [26, 0, 0])},
    'left_leg': {'rot': cyc(20, [-4, -6, -5], [26, 0, 0], phase=math.pi)},
    'head': {'rot': {'0': [-12, 0, 0]}},
    'root': {'pos': cyc(20, [0, -1, 0], [0, 0, 0], harm=([0, 0.3, 0], -math.pi / 2))},
}, fp={
    'right_arm': {'pos': cyc(20, [6, -15, 8], [0.2, 0.4, 0.6], phase=math.pi), 'rot': cyc(20, [30, 8, 52], [2, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, [-6, -15, 7.5], [0.2, 0.4, 0.6]), 'rot': cyc(20, [28, -8, -52], [2, 0, 0])},
    'fov': {'0': [-3, 0, 0]},
})

V['void.air'] = clip(24, loop=True, bones={
    'body': {'rot': cyc(24, [8, 4, 0], [2, 0, 0])},
    'right_arm': {'rot': cyc(24, [-60, -10, 38], [6, 0, 4])},
    'left_arm': {'rot': cyc(24, [-70, 10, -38], [6, 0, -4], phase=1)},
    'right_leg': {'rot': {'0': [22, 4, 6]}},
    'left_leg': {'rot': {'0': [-38, -4, -4]}},
}, fp={
    'right_arm': {'pos': cyc(24, [8, -12.5, 7], [0, 0.5, 0]), 'rot': cyc(24, [44, -4, 58], [3, 0, 0])},
    'left_arm': {'pos': cyc(24, [-8, -12.5, 6.5], [0, 0.5, 0], phase=1), 'rot': cyc(24, [42, 4, -58], [3, 0, 0], phase=1)},
})

V['void.block'] = clip(40, loop=True, blend_in=2, bones={
    'body': {'rot': cyc(40, [10, 0, 0], [1, 0, 0])},
    'right_arm': {'rot': cyc(40, [-112, -48, 0], [2, 0, 0])},
    'left_arm': {'rot': cyc(40, [-100, 48, 0], [2, 0, 0], phase=0.5)},
    'head': {'rot': {'0': [8, 0, 0]}},
    'right_leg': {'rot': {'0': [10, 4, 4]}},
    'left_leg': {'rot': {'0': [-12, -4, -4]}},
}, fp={
    'right_arm': {'pos': cyc(40, CROSS_R[0], [0, 0.2, 0]), 'rot': cyc(40, CROSS_R[1], [1, 0, 0])},
    'left_arm': {'pos': cyc(40, CROSS_L[0], [0, 0.2, 0], phase=0.5), 'rot': cyc(40, CROSS_L[1], [1, 0, 0], phase=0.5)},
    'fov': {'0': [-4, 0, 0]},
})

V['void.block_hit'] = clip(7, blend_in=0.5, blend_out=3, bones=seq([
    (0, {'body': [10, 0, 0], 'right_arm': [-112, -48, 0], 'left_arm': [-100, 48, 0], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}}),
    (1.5, {'body': [-4, 0, 0], 'right_arm': [-120, -40, 0], 'left_arm': [-108, 40, 0], 'root': {'pos': [0, 0, -1.5], 'rot': [-4, 0, 0]}}, 'outExpo'),
    (7, {'body': [10, 0, 0], 'right_arm': [-112, -48, 0], 'left_arm': [-100, 48, 0], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}}, 'outCubic'),
]), fp=fpseq([
    (0, {'R': CROSS_R, 'L': CROSS_L, 'cam': [0, 0, 0]}),
    (1.5, {'R': ([7, -10, 7], [56, 38, 62]), 'L': ([-7, -10.5, 6.5], [54, -38, -62]), 'cam': [-2.5, 0, 1.2]}, 'outExpo'),
    (7, {'R': CROSS_R, 'L': CROSS_L, 'cam': [0, 0, 0]}, 'outCubic'),
]))

# ================================================================ 평타 (3연타)
V['void.attack1'] = clip(8, blend_in=0.5, blend_out=2.5, bones=seq([
    (0, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8]}),
    (1.5, {'body': [6, -26, 0], 'right_arm': [-92, 6, 0], 'left_arm': [-62, 22, -10], 'head': [0, 8, 0]}, 'outExpo'),
    (4.5, {'body': [5, -20, 0], 'right_arm': [-88, 4, 0], 'left_arm': [-60, 20, -8], 'head': [0, 6, 0]}),
    (8, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'head': [0, 0, 0]}, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}),
    (1.5, {'R': JAB_R, 'L': ([-7, -12.5, 5.5], [38, -8, -50]), 'cam': [0.6, -0.8, -1.2], 'fov': -1.5}, 'outExpo'),
    (4, {'R': ([4.3, -9.3, 13.2], [19, 10, 27]), 'cam': [0.3, -0.4, -0.6]}),
    (8, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}, 'inOutSine'),
]))

V['void.attack2'] = clip(8, blend_in=0.5, blend_out=2.5, bones=seq([
    (0, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8]}),
    (1, {'body': [4, 24, 0], 'left_arm': [-80, -30, -30], 'right_arm': [-50, -30, 8]}, 'outQuad'),
    (2.5, {'body': [6, 34, 0], 'left_arm': [-92, 40, -8], 'right_arm': [-52, -32, 8], 'head': [0, -8, 0]}, 'outExpo'),
    (5, {'body': [5, 28, 0], 'left_arm': [-88, 34, -6], 'head': [0, -6, 0]}),
    (8, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'head': [0, 0, 0]}, 'inOutSine'),
]), fp=fpseq([
    (0, {'L': GL, 'R': GR, 'cam': [0, 0, 0]}),
    (1, {'L': ([-10, -12, 5], [30, -40, -80]), 'R': ([7, -13, 6.5], [38, 4, 52])}, 'outQuad'),
    (2.5, {'L': ([-3, -9.5, 12], [22, 26, -40]), 'cam': [0.4, 1.2, 1.4]}, 'outExpo'),
    (5, {'L': ([-2.5, -9.8, 11.5], [20, 28, -38]), 'cam': [0.2, 0.6, 0.7]}),
    (8, {'L': GL, 'R': GR, 'cam': [0, 0, 0]}, 'inOutSine'),
]))

V['void.attack3'] = clip(13, blend_in=0.5, blend_out=3, bones=seq([
    (0, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3]}),
    (2.5, {'body': [0, 30, 0], 'right_arm': [-20, -10, 14], 'left_arm': [-80, 16, -10], 'root': {'pos': [0, -0.5, -0.5], 'rot': [-2, 0, 0]}}, 'outCubic'),
    (4, {'body': [12, -34, 0], 'right_arm': [-96, 4, 0], 'left_arm': [-20, 10, -16], 'root': {'pos': [0, -1, 3], 'rot': [8, 0, 0]}, 'right_leg': [14, 6, 4], 'left_leg': [-24, -4, -3], 'head': [4, 10, 0]}, 'outExpo'),
    (8, {'body': [10, -28, 0], 'right_arm': [-92, 2, 0], 'left_arm': [-24, 10, -14], 'root': {'pos': [0, -1, 2.5], 'rot': [6, 0, 0]}}),
    (13, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3], 'head': [0, 0, 0]}, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}),
    (2.5, {'R': BACK_R, 'L': ([-5, -11, 10], [28, -10, -40]), 'cam': [-0.8, 1.0, 1.0], 'fov': 1}, 'outCubic'),
    (4, {'R': ([3.5, -8.5, 16], [16, 10, 22]), 'L': ([-8, -15, 4], [24, -4, -55]), 'cam': [1.5, -1.6, -2.4], 'fov': -5}, 'outExpo'),
    (8, {'R': ([3.8, -8.8, 15], [17, 10, 24]), 'cam': [0.7, -0.8, -1.2], 'fov': -3}),
    (13, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}, 'inOutSine'),
]))

# ================================================================ 1. 공허의 손아귀
V['void.skill1'] = clip(24, blend_in=1, blend_out=4, bones=seq([
    (0, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0]}}),
    (2.5, {'body': [8, -22, 0], 'right_arm': [-100, 8, -6], 'left_arm': [-30, 10, -18], 'head': [0, 10, 0], 'root': {'pos': [0, -0.5, 1.5]}}, 'outExpo'),
    (6, {'body': [6, -18, 0], 'right_arm': [-98, 6, -4], 'left_arm': [-30, 10, -18], 'root': {'pos': [0, -0.5, 1.2]}}),
    (9, {'body': [-6, 8, 0], 'right_arm': [-60, -18, 10], 'left_arm': [-40, 12, -18], 'head': [-4, 0, 0], 'root': {'pos': [0, 0, -1]}}, 'inCubic'),
    (14, {'body': [-4, 12, 0], 'right_arm': [-34, -24, 14], 'left_arm': [-52, 16, -12], 'root': {'pos': [0, 0, -0.8]}}, 'outQuad'),
    (24, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'head': [0, 0, 0], 'root': {'pos': [0, 0, 0]}}, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}),
    (2.5, {'R': PALM_R, 'L': LOW_L, 'cam': [0.5, -0.6, -0.8], 'fov': -2}, 'outExpo'),
    (6, {'R': ([3.2, -9.2, 13.5], [24, 12, 20])}),
    (9, {'R': ([7, -13, 4], [30, 10, 100]), 'cam': [-1.0, 0.6, 0.8], 'fov': 2}, 'inCubic'),
    (14, {'R': ([7.5, -14, 3], [28, 6, 95]), 'L': GL}, 'outQuad'),
    (24, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}, 'inOutSine'),
]))

V['void.skill1_punch'] = clip(14, blend_in=0.5, blend_out=4, bones=seq([
    (0, {'body': [-4, 12, 0], 'right_arm': [-34, -24, 14], 'left_arm': [-52, 16, -12], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3]}),
    (2, {'body': [14, 20, 0], 'right_arm': [-6, 0, 18], 'left_arm': [-70, 18, -10], 'root': {'pos': [0, -2, 0], 'rot': [4, 0, 0]}}, 'outQuad'),
    (4, {'body': [-12, -30, 0], 'right_arm': [-158, 10, -6], 'left_arm': [-30, 10, -20], 'root': {'pos': [0, 1.5, 2], 'rot': [-6, 0, 0]}, 'right_leg': [6, 4, 4], 'left_leg': [-20, -4, -3], 'head': [-14, 10, 0]}, 'outExpo'),
    (8, {'body': [-10, -26, 0], 'right_arm': [-150, 8, -4], 'left_arm': [-32, 10, -18], 'root': {'pos': [0, 1, 1.5], 'rot': [-4, 0, 0]}}),
    (14, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3], 'head': [0, 0, 0]}, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': ([7.5, -14, 3], [28, 6, 95]), 'L': GL, 'cam': [0, 0, 0], 'fov': 0}),
    (2, {'R': ([6, -19, 6], [4, 6, 60]), 'cam': [1.0, 0, 0], 'fov': 1}, 'outQuad'),
    (4, {'R': ([3, -8, 11], [62, 10, 28]), 'L': LOW_L, 'cam': [-3.0, -1.0, -2.0], 'fov': -6}, 'outExpo'),
    (8, {'R': ([3.2, -8.4, 10.6], [58, 10, 30]), 'cam': [-1.4, -0.5, -1.0], 'fov': -3}),
    (14, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}, 'inOutSine'),
]))

# ================================================================ 2. 공허 파동
V['void.skill2'] = clip(20, blend_in=1, blend_out=4, bones=seq([
    (0, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3], 'head': [0, 0, 0]}),
    (4, {'body': [6, 42, 0], 'right_arm': [16, 6, 24], 'left_arm': [-92, -26, -4], 'root': {'pos': [0, -1.8, -0.5], 'rot': [2, 0, 0]}, 'right_leg': [16, 8, 6], 'left_leg': [-18, -6, -5], 'head': [0, -26, 0]}, 'outCubic'),
    (9.5, {'body': [8, 46, 0], 'right_arm': [22, 6, 26], 'left_arm': [-94, -28, -4], 'root': {'pos': [0, -2.2, -0.8], 'rot': [3, 0, 0]}, 'head': [0, -28, 0]}, 'inOutSine'),
    (11, {'body': [14, -38, 0], 'right_arm': [-98, 6, 0], 'left_arm': [-14, 10, -22], 'root': {'pos': [0, -1.5, 4], 'rot': [10, 0, 0]}, 'right_leg': [18, 6, 4], 'left_leg': [-28, -4, -3], 'head': [6, 12, 0]}, 'outExpo'),
    (15, {'body': [12, -34, 0], 'right_arm': [-94, 4, 0], 'left_arm': [-18, 10, -20], 'root': {'pos': [0, -1.3, 3.5], 'rot': [8, 0, 0]}}),
    (20, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3], 'head': [0, 0, 0]}, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}),
    (4, {'R': BACK_R, 'L': PALM_L, 'cam': [-0.6, 1.6, 1.4], 'fov': 3}, 'outCubic'),
    (9.5, {'R': ([9.5, -16.5, 1], [14, -6, 74]), 'L': ([-3, -9.2, 13.4], [23, -14, -10]), 'cam': [-0.8, 1.8, 1.6], 'fov': 4}, 'inOutSine'),
    (11, {'R': ([3.2, -8.5, 17], [16, 10, 20]), 'L': ([-8, -16, 3], [20, -4, -56]), 'cam': [2.4, -2.2, -3.0], 'fov': -8}, 'outExpo'),
    (15, {'R': ([3.5, -8.8, 16], [17, 10, 22]), 'cam': [1.0, -1.0, -1.4], 'fov': -4}),
    (20, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}, 'inOutSine'),
]))

# ================================================================ 3. 공허 난무
V['void.skill3_dash'] = clip(4, blend_in=0.5, blend_out=1, bones=seq([
    (0, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0]}}),
    (2, {'body': [30, 0, 0], 'right_arm': [40, 0, 20], 'left_arm': [40, 0, -20], 'root': {'pos': [0, -2, 2]}, 'head': [-20, 0, 0]}, 'outExpo'),
    (4, {'body': [30, 0, 0], 'right_arm': [40, 0, 20], 'left_arm': [40, 0, -20], 'root': {'pos': [0, -2, 2]}}),
]), fp=fpseq([
    (0, {'R': GR, 'L': GL, 'fov': 0}),
    (2, {'R': HIDE_R, 'L': HIDE_L, 'fov': 10}, 'outExpo'),
    (4, {'R': HIDE_R, 'L': HIDE_L, 'fov': 10}),
]))


def punch_frames(t, right, hook=False):
    """난무용 펀치 한 번 (t 에 타격)."""
    if right:
        arm = 'right_arm'
        hit = [-94, -6 if not hook else -40, 0]
        body = [6, -26 if not hook else -36, 0]
    else:
        arm = 'left_arm'
        hit = [-94, 6 if not hook else 40, 0]
        body = [6, 26 if not hook else 36, 0]
    return [(t - 1.5, {'body': [4, 0, 0]}, 'inQuad'), (t, {arm: hit, 'body': body}, 'outExpo')]


s3 = [(0, {'body': [30, 0, 0], 'right_arm': [40, 0, 20], 'left_arm': [40, 0, -20], 'root': {'pos': [0, -2, 2], 'rot': [0, 0, 0]}, 'head': [0, 0, 0], 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3]})]
s3 += [(3, {'body': [4, 0, 0], 'right_arm': [-60, -24, 6], 'left_arm': [-64, 22, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}}, 'outCubic')]
s3 += [(6, {'body': [6, -26, 0], 'right_arm': [-94, -6, 0], 'left_arm': [-64, 22, -8]}, 'outExpo')]
s3 += [(7.5, {'body': [4, 0, 0], 'right_arm': [-56, -24, 6], 'left_arm': [-64, 22, -8]}, 'inQuad')]
s3 += [(9, {'body': [6, 28, 0], 'left_arm': [-94, 6, 0], 'right_arm': [-56, -24, 6]}, 'outExpo')]
s3 += [(10.5, {'body': [4, 0, 0], 'left_arm': [-60, 22, -8], 'right_arm': [-56, -24, 6]}, 'inQuad')]
s3 += [(12, {'body': [8, -38, 0], 'right_arm': [-92, -40, -10], 'left_arm': [-60, 22, -8]}, 'outExpo')]
s3 += [(13.5, {'body': [4, 0, 0], 'right_arm': [-56, -24, 6], 'left_arm': [-60, 22, -8]}, 'inQuad')]
s3 += [(15, {'body': [8, 38, 0], 'left_arm': [-92, 40, 10], 'right_arm': [-56, -24, 6]}, 'outExpo')]
s3 += [(18.5, {'body': [-14, 0, 0], 'right_arm': [-172, -10, 8], 'left_arm': [-172, 10, -8], 'root': {'pos': [0, 1.5, -0.5], 'rot': [-4, 0, 0]}, 'head': [-20, 0, 0]}, 'outCubic')]
s3 += [(21, {'body': [40, 0, 0], 'right_arm': [-58, -6, 4], 'left_arm': [-58, 6, -4], 'root': {'pos': [0, -2.5, 2], 'rot': [6, 0, 0]}, 'head': [6, 0, 0], 'right_leg': [18, 6, 6], 'left_leg': [-26, -4, -4]}, 'outExpo')]
s3 += [(25, {'body': [36, 0, 0], 'right_arm': [-56, -6, 4], 'left_arm': [-56, 6, -4], 'root': {'pos': [0, -2.3, 1.8], 'rot': [5, 0, 0]}})]
s3 += [(30, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'head': [0, 0, 0], 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3]}, 'inOutSine')]
V['void.skill3'] = clip(30, blend_in=1, blend_out=4, bones=seq(s3), fp=fpseq([
    (0, {'R': HIDE_R, 'L': HIDE_L, 'cam': [0, 0, 0], 'fov': 8}),
    (3, {'R': GR, 'L': GL, 'fov': 0}, 'outCubic'),
    (6, {'R': JAB_R, 'cam': [0.6, -0.8, -1.2], 'fov': -2}, 'outExpo'),
    (7.5, {'R': GR, 'cam': [0, 0, 0]}, 'inQuad'),
    (9, {'L': JAB_L, 'cam': [0.6, 0.8, 1.2], 'fov': -2}, 'outExpo'),
    (10.5, {'L': GL, 'cam': [0, 0, 0]}, 'inQuad'),
    (12, {'R': ([3, -9.5, 12], [20, -24, 40]), 'cam': [0.5, -1.2, -1.4], 'fov': -3}, 'outExpo'),
    (13.5, {'R': GR, 'cam': [0, 0, 0]}, 'inQuad'),
    (15, {'L': ([-3, -9.5, 12], [20, 24, -40]), 'cam': [0.5, 1.2, 1.4], 'fov': -3}, 'outExpo'),
    (18.5, {'R': UP_R, 'L': UP_L, 'cam': [-2.0, 0, 0], 'fov': 2}, 'outCubic'),
    (21, {'R': DOWN_R, 'L': DOWN_L, 'cam': [5.0, 0, 0], 'fov': -8}, 'outExpo'),
    (25, {'R': ([3.6, -11.2, 11.6], [-10, 10, 50]), 'L': ([-3.6, -11.2, 11.6], [-10, -10, -50]), 'cam': [2.0, 0, 0], 'fov': -4}),
    (30, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}, 'inOutSine'),
]))

V['void.skill3_air'] = clip(26, blend_in=1, blend_out=4, root_pivot=16, bones=seq([
    (0, {'body': [30, 0, 0], 'right_arm': [40, 0, 20], 'left_arm': [40, 0, -20], 'root': {'pos': [0, -2, 2], 'rot': [0, 0, 0]}, 'head': [0, 0, 0], 'right_leg': [20, 4, 6], 'left_leg': [-30, -4, -4]}),
    (3.5, {'body': [-18, 0, 0], 'right_arm': [-176, -10, 8], 'left_arm': [-176, 10, -8], 'root': {'pos': [0, 1, 0], 'rot': [-14, 0, 0]}, 'head': [-24, 0, 0], 'right_leg': [30, 4, 6], 'left_leg': [10, -4, -4]}, 'outCubic'),
    (5.5, {'body': [46, 0, 0], 'right_arm': [-50, -6, 4], 'left_arm': [-50, 6, -4], 'root': {'pos': [0, 0, 1], 'rot': [26, 0, 0]}, 'head': [10, 0, 0], 'right_leg': [24, 4, 6], 'left_leg': [6, -4, -4]}, 'outExpo'),
    (12, {'body': [40, 0, 0], 'right_arm': [-24, -4, 10], 'left_arm': [-24, 4, -10], 'root': {'pos': [0, 0, 0.5], 'rot': [30, 0, 0]}, 'head': [-30, 0, 0]}),
    (18, {'body': [36, 0, 0], 'right_arm': [-40, -6, 6], 'left_arm': [-40, 6, -6], 'root': {'pos': [0, -2, 1.5], 'rot': [6, 0, 0]}, 'head': [4, 0, 0], 'right_leg': [18, 6, 6], 'left_leg': [-26, -4, -4]}, 'outCubic'),
    (26, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'head': [0, 0, 0], 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3]}, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': HIDE_R, 'L': HIDE_L, 'cam': [0, 0, 0], 'fov': 8}),
    (3.5, {'R': UP_R, 'L': UP_L, 'cam': [-3, 0, 0], 'fov': 4}, 'outCubic'),
    (5.5, {'R': DOWN_R, 'L': DOWN_L, 'cam': [8, 0, 0], 'fov': -6}, 'outExpo'),
    (12, {'R': ([4, -12, 10], [-20, 10, 50]), 'L': ([-4, -12, 10], [-20, -10, -50]), 'cam': [6, 0, 0], 'fov': 6}),
    (18, {'R': DOWN_R, 'L': DOWN_L, 'cam': [4, 0, 0], 'fov': -6}, 'outCubic'),
    (26, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}, 'inOutSine'),
]))

# ================================================================ 4. 공허 반격
V['void.skill4'] = clip(17, blend_in=1, blend_out=3, bones=seq([
    (0, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0]}, 'head': [0, 0, 0]}),
    (2.5, {'body': [6, 26, 0], 'left_arm': [-96, 4, -4], 'right_arm': [-26, -10, 18], 'root': {'pos': [0, -1.5, 0]}, 'head': [4, -14, 0]}, 'outBack'),
    (9, {'body': [7, 28, 0], 'left_arm': [-98, 4, -4], 'right_arm': [-24, -10, 18], 'root': {'pos': [0, -1.7, 0]}, 'head': [4, -15, 0]}),
    (14, {'body': [6, 26, 0], 'left_arm': [-94, 4, -4], 'right_arm': [-26, -10, 18], 'root': {'pos': [0, -1.5, 0]}}),
    (17, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0]}, 'head': [0, 0, 0]}, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}),
    (2.5, {'L': PALM_L, 'R': LOW_R, 'cam': [0.5, 0.6, 0.6], 'fov': -3}, 'outBack'),
    (9, {'L': ([-3, -9.3, 13.2], [23, -14, -10]), 'R': LOW_R, 'fov': -4}),
    (14, {'L': PALM_L, 'R': LOW_R, 'cam': [0, 0, 0], 'fov': -3}),
    (17, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}, 'inOutSine'),
]))

V['void.skill4_success'] = clip(22, blend_in=0.5, blend_out=4, bones=seq([
    (0, {'body': [6, 26, 0], 'left_arm': [-96, 4, -4], 'right_arm': [-26, -10, 18], 'root': {'pos': [0, -1.5, 0], 'rot': [0, 0, 0]}, 'head': [4, -14, 0], 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3]}),
    (1.5, {'body': [-4, 30, 0], 'left_arm': [-110, 10, -10], 'root': {'pos': [0, -1, -1.5], 'rot': [-4, 0, 0]}}, 'outExpo'),
    (3.5, {'body': [4, 34, 0], 'right_arm': [-20, -6, 18], 'left_arm': [-80, 14, -10]}, 'inQuad'),
    (6, {'body': [8, -36, 0], 'right_arm': [-108, 4, -2], 'left_arm': [-30, 10, -18], 'root': {'pos': [0, -0.5, 2.5], 'rot': [4, 0, 0]}, 'head': [-6, 12, 0], 'left_leg': [-22, -4, -3]}, 'outExpo'),
    (9.5, {'body': [-16, -10, 0], 'right_arm': [-176, -6, 6], 'left_arm': [-150, 10, -6], 'root': {'pos': [0, 1, 1], 'rot': [-4, 0, 0]}, 'head': [-20, 0, 0]}, 'outCubic'),
    (13, {'body': [42, 0, 0], 'right_arm': [-56, -6, 4], 'left_arm': [-50, 6, -4], 'root': {'pos': [0, -2.5, 2.5], 'rot': [6, 0, 0]}, 'head': [8, 0, 0], 'right_leg': [18, 6, 6], 'left_leg': [-26, -4, -4]}, 'outExpo'),
    (17, {'body': [38, 0, 0], 'right_arm': [-54, -6, 4], 'left_arm': [-48, 6, -4], 'root': {'pos': [0, -2.3, 2.3], 'rot': [5, 0, 0]}}),
    (22, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'head': [0, 0, 0], 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3]}, 'inOutSine'),
]), fp=fpseq([
    (0, {'L': PALM_L, 'R': LOW_R, 'cam': [0, 0, 0], 'fov': -3}),
    (1.5, {'L': ([-3, -8, 10], [34, -14, -10]), 'cam': [-2.5, 0.5, 1.5], 'fov': 2}, 'outExpo'),
    (3.5, {'R': BACK_R, 'L': ([-4, -10, 11], [26, -10, -20]), 'cam': [-1, 1, 1]}, 'inQuad'),
    (6, {'R': ([3, -7.5, 15], [26, 10, 20]), 'L': LOW_L, 'cam': [1.5, -1.8, -2.5], 'fov': -7}, 'outExpo'),
    (9.5, {'R': UP_R, 'L': ([-5, -6, 6], [70, -12, -50]), 'cam': [-2.5, 0, 0], 'fov': 1}, 'outCubic'),
    (13, {'R': DOWN_R, 'L': DOWN_L, 'cam': [6, 0, 0], 'fov': -8}, 'outExpo'),
    (17, {'R': ([3.6, -11.2, 11.6], [-10, 10, 50]), 'L': ([-3.6, -11.2, 11.6], [-10, -10, -50]), 'cam': [2, 0, 0], 'fov': -4}),
    (22, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}, 'inOutSine'),
]))

# ================================================================ R. 사건의 지평선 (연출 70틱)
V['void.ult'] = clip(72, blend_in=2, blend_out=5, root_pivot=12, bones=seq([
    (0, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'head': [0, 0, 0], 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3]}),
    (8, {'body': [24, 0, 0], 'right_arm': [20, 10, 40], 'left_arm': [20, -10, -40], 'root': {'pos': [0, -2.5, 0], 'rot': [0, 0, 0]}, 'head': [20, 0, 0], 'right_leg': [14, 10, 10], 'left_leg': [-14, -10, -10]}, 'outCubic'),
    (14, {'body': [26, 0, 0], 'right_arm': [24, 10, 44], 'left_arm': [24, -10, -44], 'root': {'pos': [0, -2.8, 0]}, 'head': [22, 0, 0]}),
    (22, {'body': [-14, -6, 0], 'right_arm': [-176, -8, 10], 'left_arm': [-20, 0, -72], 'root': {'pos': [0, 4, 0], 'rot': [-4, 0, 0]}, 'head': [-38, 0, 0], 'right_leg': [8, 4, 6], 'left_leg': [-4, -4, -6]}, 'outExpo'),
    (34, {'body': [-12, -6, 0], 'right_arm': [-178, -8, 8], 'left_arm': [-24, 0, -76], 'root': {'pos': [0, 7, 0], 'rot': [-3, 0, 0]}, 'head': [-40, 0, 0], 'right_leg': [12, 4, 6], 'left_leg': [2, -4, -6]}),
    (46, {'body': [-10, -8, 0], 'right_arm': [-180, -6, 6], 'left_arm': [-22, 0, -80], 'root': {'pos': [0, 9, 0], 'rot': [-2, 0, 0]}, 'head': [-42, 0, 0]}),
    (50, {'body': [-16, 20, 0], 'right_arm': [-200, -10, 10], 'left_arm': [-40, 10, -60], 'root': {'pos': [0, 9.5, -0.5], 'rot': [-5, 0, 0]}, 'head': [-30, -10, 0]}, 'inQuad'),
    (53, {'body': [16, -36, 0], 'right_arm': [-100, 6, 0], 'left_arm': [-10, 10, -30], 'root': {'pos': [0, 8, 2], 'rot': [8, 0, 0]}, 'head': [0, 12, 0], 'right_leg': [20, 4, 6], 'left_leg': [-24, -4, -6]}, 'outExpo'),
    (64, {'body': [14, -34, 0], 'right_arm': [-98, 6, 0], 'left_arm': [-12, 10, -30], 'root': {'pos': [0, 4, 1.5], 'rot': [6, 0, 0]}}),
    (72, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'head': [0, 0, 0], 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3]}, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': GR, 'L': GL, 'fov': 0}),
    (8, {'R': LOW_R, 'L': LOW_L}),
    (22, {'R': UP_R, 'L': ([-9, -14, 5], [10, 10, -80])}, 'outExpo'),
    (50, {'R': UP_R}),
    (53, {'R': ([3, -8, 16], [20, 10, 20]), 'L': LOW_L}, 'outExpo'),
    (72, {'R': GR, 'L': GL, 'fov': 0}, 'inOutSine'),
]))

# ================================================================ T. 그림자 이동
V['void.shadow_enter'] = clip(10, blend_in=0.5, blend_out=0.5, root_pivot=2, bones=seq([
    (0, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'head': [0, 0, 0]}),
    (2, {'body': [18, 0, 0], 'right_arm': [-20, 0, 30], 'left_arm': [-20, 0, -30], 'root': {'pos': [0, 1, 0], 'rot': [0, 0, 0]}, 'head': [10, 0, 0]}, 'outQuad'),
    (8, {'body': [10, 0, 0], 'right_arm': [-170, 0, 10], 'left_arm': [-170, 0, -10], 'root': {'pos': [0, -32, 0], 'rot': [0, 40, 0]}, 'head': [-30, 0, 0]}, 'inCubic'),
    (10, {'body': [10, 0, 0], 'right_arm': [-170, 0, 10], 'left_arm': [-170, 0, -10], 'root': {'pos': [0, -40, 0], 'rot': [0, 50, 0]}}),
]), fp=fpseq([
    (0, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}),
    (2, {'R': ([7, -12, 8], [50, 0, 60]), 'L': ([-7, -12, 7.5], [50, 0, -60]), 'cam': [-2, 0, 0]}, 'outQuad'),
    (8, {'R': ([8, -6, 4], [80, 0, 50]), 'L': ([-8, -6, 4], [80, 0, -50]), 'cam': [0, 0, 6], 'fov': 14}, 'inCubic'),
    (10, {'R': ([8, -2, 2], [85, 0, 50]), 'L': ([-8, -2, 2], [85, 0, -50]), 'cam': [0, 0, 8], 'fov': 16}),
]))

V['void.shadow_exit'] = clip(14, blend_in=0.1, blend_out=4, root_pivot=2, bones=seq([
    (0, {'body': [10, 0, 0], 'right_arm': [-170, 0, 10], 'left_arm': [-170, 0, -10], 'root': {'pos': [0, -36, 0], 'rot': [0, -40, 0]}, 'head': [-20, 0, 0], 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}),
    (6, {'body': [-6, 0, 0], 'right_arm': [-30, 0, 50], 'left_arm': [-30, 0, -50], 'root': {'pos': [0, 1.5, 0], 'rot': [0, 0, 0]}, 'head': [-8, 0, 0], 'right_leg': [6, 0, 4], 'left_leg': [-6, 0, -4]}, 'outCubic'),
    (9, {'body': [6, 8, 0], 'right_arm': [-40, -20, 8], 'left_arm': [-54, 16, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'head': [0, 0, 0], 'right_leg': [9, 6, 4], 'left_leg': [-11, -4, -3]}, 'outBack'),
    (14, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}}, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': HIDE_R, 'L': HIDE_L, 'cam': [0, 0, -6], 'fov': 14}),
    (6, {'R': ([8.5, -13, 6], [48, -8, 70]), 'L': ([-8.5, -13, 5.5], [46, 8, -70]), 'cam': [-1.5, 0, 0], 'fov': 2}, 'outCubic'),
    (9, {'R': GR, 'L': GL, 'cam': [0, 0, 0], 'fov': 0}, 'outBack'),
    (14, {'R': GR, 'L': GL}),
]))

V['void.select'] = clip(26, blend_in=2, blend_out=5, bones=seq([
    (0, {'body': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'head': [0, 0, 0]}),
    (6, {'body': [10, 0, 0], 'right_arm': [-70, -40, 0], 'left_arm': [-70, 40, 0], 'head': [16, 0, 0]}, 'outCubic'),
    (12, {'body': [10, 0, 0], 'right_arm': [-74, -44, 0], 'left_arm': [-74, 44, 0], 'head': [18, 0, 0]}),
    (16, {'body': [-8, 0, 0], 'right_arm': [-60, 20, 70], 'left_arm': [-60, -20, -70], 'head': [-14, 0, 0]}, 'outExpo'),
    (26, {'body': [3, 10, 0], 'right_arm': [-42, -24, 6], 'left_arm': [-58, 18, -8], 'head': [0, 0, 0]}, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': LOW_R, 'L': LOW_L, 'cam': [0, 0, 0]}),
    (6, {'R': ([2.5, -10, 12], [30, 30, 30]), 'L': ([-2.5, -10, 12], [30, -30, -30])}, 'outCubic'),
    (12, {'R': ([2.2, -10, 12], [32, 32, 30]), 'L': ([-2.2, -10, 12], [32, -32, -30])}),
    (16, {'R': ([9, -11, 9], [40, -30, 80]), 'L': ([-9, -11, 9], [40, 30, -80]), 'cam': [-1.5, 0, 0]}, 'outExpo'),
    (26, {'R': GR, 'L': GL, 'cam': [0, 0, 0]}, 'inOutSine'),
]))

write('void', V)

# =====================================================================================
#                                  용기사 (dragon)
# =====================================================================================
import copy
D = {}

# ---- 1인칭 기준 자세 -------------------------------------------------------------------
DGR = ([7, -13.5, 7.5], [38, 4, 62])          # 가드: 발톱 세운 손
DGL = ([-7, -13.5, 7], [36, -4, -62])
SW_R0 = ([12, -9, 6], [48, -38, 82])          # 오른손 옆으로 벌림 (가로 할퀴기 준비)
SW_R1 = ([-1, -11, 12], [24, 42, 20])         # 오른손 왼쪽으로 쓸어냄
SW_L0 = ([-12, -9, 5.5], [48, 38, -82])
SW_L1 = ([1, -11, 12], [24, -42, -20])
DI_R0 = ([10, -3, 6], [88, -24, 70])          # 오른손 머리 위 (대각선 내려 할퀴기 준비)
DI_R1 = ([-0.5, -12, 11.5], [6, 30, 30])      # 왼쪽 아래로 내려 할큄
DI_L0 = ([-10, -3, 5.5], [88, 24, -70])
DI_L1 = ([0.5, -12, 11.5], [6, -30, -30])
GRAB_R = ([3.5, -9.5, 14.5], [22, 10, 40])    # 앞으로 움켜쥠
GRAB_L = ([-3.5, -9.5, 14], [22, -10, -40])
PULL_R = ([8, -13, 3], [32, 6, 82])           # 잡아 당김
PULL_L = ([-8, -13, 2.5], [32, -6, -82])
OUT_R = ([15, -12, 3], [18, -62, 90])         # 양팔 활짝 (날개 펼침)
OUT_L = ([-15, -12, 2.5], [18, 62, -90])
TRAIL_R = ([9, -20, 2], [-4, 0, 64])          # 돌진: 뒤로 끌리는 팔
TRAIL_L = ([-9, -20, 2], [-4, 0, -64])
SHIELD_L = ([-4, -10, 10], [50, -36, -52])    # 왼팔을 비스듬히 세워 비늘 방패
UPC_R = ([3, -4.5, 11], [76, 10, 30])         # 올려치기 끝


def P(**kw):
    """용기사 기본 자세 + 덮어쓰기 (root 는 pos/rot 일부만 줘도 됨)."""
    d = {
        'body': [10, 14, 0], 'head': [-6, -8, 0],
        'right_arm': [-50, -18, 18], 'left_arm': [-66, 22, -14],
        'right_leg': [10, 8, 5], 'left_leg': [-14, -6, -4],
        'root': {'pos': [0, -0.8, 0], 'rot': [0, 0, 0]},
    }
    for k, v in kw.items():
        if k == 'root':
            d['root'] = dict(d['root'], **v)
        else:
            d[k] = v
    return d


DK = P()


def F(R=DGR, L=DGL, cam=(0, 0, 0), fov=0):
    return {'R': R, 'L': L, 'cam': list(cam), 'fov': fov}


FG = F()

# ================================================================ 이동
D['dragon.idle'] = clip(60, loop=True, bones={
    'body': {'rot': cyc(60, [10, 14, 0], [1.6, 0, 0])},
    'head': {'rot': cyc(60, [-6, -8, 0], [1.4, 0, 0], phase=1.0)},
    'right_arm': {'rot': cyc(60, [-50, -18, 18], [3, 0, 2], phase=0.3, harm=([1.5, 0, 0], 0.5))},
    'left_arm': {'rot': cyc(60, [-66, 22, -14], [3, 0, -2], phase=0.8, harm=([1.5, 0, 0], 0.5))},
    'right_leg': {'rot': {'0': [10, 8, 5]}},
    'left_leg': {'rot': {'0': [-14, -6, -4]}},
    'root': {'pos': cyc(60, [0, -0.8, 0], [0, -0.3, 0])},
}, fp={
    'right_arm': {'pos': cyc(60, DGR[0], [0, 0.4, 0.1]), 'rot': cyc(60, DGR[1], [1.5, 0, 1.5])},
    'left_arm': {'pos': cyc(60, DGL[0], [0, 0.4, 0.1], phase=0.8), 'rot': cyc(60, DGL[1], [1.5, 0, -1.5], phase=0.8)},
})

D['dragon.walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [12, 8, 0], [0, 6, 0], phase=math.pi)},
    'head': {'rot': {'0': [-8, -4, 0]}},
    'right_arm': {'rot': cyc(20, [-44, -14, 18], [18, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-56, 16, -14], [18, 0, 0])},
    'right_leg': {'rot': cyc(20, [0, 0, 3], [34, 0, 0])},
    'left_leg': {'rot': cyc(20, [0, 0, -3], [34, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, -0.6, 0], [0, 0, 0], harm=([0, 0.5, 0], -math.pi / 2))},
}, fp={
    'right_arm': {'pos': cyc(20, DGR[0], [0.3, 0.7, 0.9], phase=math.pi), 'rot': cyc(20, DGR[1], [3, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, DGL[0], [0.3, 0.7, 0.9]), 'rot': cyc(20, DGL[1], [3, 0, 0])},
})

# 질주: 몸을 낮추고 양팔을 뒤로 끄는 맹수 달리기
D['dragon.run'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [30, 0, 0], [0, 8, 0], phase=math.pi, harm=([2, 0, 0], 0))},
    'head': {'rot': {'0': [-26, 0, 0]}},
    'right_arm': {'rot': cyc(20, [22, 4, 22], [16, 0, 4], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [22, -4, -22], [16, 0, -4])},
    'right_leg': {'rot': cyc(20, [-8, 0, 2], [56, 0, 0], harm=([-8, 0, 0], 0))},
    'left_leg': {'rot': cyc(20, [-8, 0, -2], [56, 0, 0], phase=math.pi, harm=([-8, 0, 0], 0))},
    'root': {'pos': cyc(20, [0, 0, 0], [0, 0, 0], harm=([0, 1.2, 0], -math.pi / 2)), 'rot': {'0': [6, 0, 0]}},
}, fp={
    'right_arm': {'pos': cyc(20, [8.5, -18, 4], [0.4, 1.6, 1.4], phase=math.pi), 'rot': cyc(20, [12, 2, 62], [8, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, [-8.5, -18, 3.5], [0.4, 1.6, 1.4]), 'rot': cyc(20, [12, -2, -62], [8, 0, 0])},
    'camera': {'rot': cyc(20, [0, 0, 0], [0, 0, 0.7], harm=([0.6, 0, 0], 0))},
    'fov': {'0': [5, 0, 0]},
})

D['dragon.sneak'] = clip(50, loop=True, bones={
    'body': {'rot': cyc(50, [32, 14, 0], [1.5, 0, 0])},
    'head': {'rot': {'0': [-22, -8, 0]}},
    'right_arm': {'rot': cyc(50, [-58, -16, 22], [3, 0, 0], phase=0.4)},
    'left_arm': {'rot': cyc(50, [-72, 18, -16], [3, 0, 0], phase=0.9)},
    'right_leg': {'rot': {'0': [18, 8, 7]}, 'pos': {'0': [0, 0, -1]}},
    'left_leg': {'rot': {'0': [-24, -6, -6]}, 'pos': {'0': [0, 0, 1]}},
    'root': {'pos': {'0': [0, -1.5, 0]}},
}, fp={
    'right_arm': {'pos': cyc(50, [6.5, -15, 8.5], [0, 0.3, 0]), 'rot': cyc(50, [32, 6, 62], [1.5, 0, 0])},
    'left_arm': {'pos': cyc(50, [-6.5, -15, 8], [0, 0.3, 0], phase=0.6), 'rot': cyc(50, [30, -6, -62], [1.5, 0, 0], phase=0.6)},
    'fov': {'0': [-3, 0, 0]},
})

D['dragon.sneak_walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [32, 10, 0], [0, 5, 0])},
    'head': {'rot': {'0': [-22, -6, 0]}},
    'right_arm': {'rot': cyc(20, [-58, -14, 22], [9, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-72, 16, -16], [9, 0, 0])},
    'right_leg': {'rot': cyc(20, [4, 6, 7], [26, 0, 0])},
    'left_leg': {'rot': cyc(20, [-4, -6, -6], [26, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, -1.5, 0], [0, 0, 0], harm=([0, 0.3, 0], -math.pi / 2))},
}, fp={
    'right_arm': {'pos': cyc(20, [6.5, -15, 8.5], [0.2, 0.4, 0.6], phase=math.pi), 'rot': cyc(20, [32, 6, 62], [2, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, [-6.5, -15, 8], [0.2, 0.4, 0.6]), 'rot': cyc(20, [30, -6, -62], [2, 0, 0])},
    'fov': {'0': [-3, 0, 0]},
})

D['dragon.air'] = clip(24, loop=True, bones={
    'body': {'rot': cyc(24, [10, 0, 0], [2, 0, 0])},
    'head': {'rot': {'0': [-8, 0, 0]}},
    'right_arm': {'rot': cyc(24, [-34, 6, 62], [8, 0, 6])},
    'left_arm': {'rot': cyc(24, [-34, -6, -62], [8, 0, -6], phase=0.6)},
    'right_leg': {'rot': {'0': [26, 4, 6]}},
    'left_leg': {'rot': {'0': [-34, -4, -5]}},
}, fp={
    'right_arm': {'pos': cyc(24, [10, -13, 6], [0, 0.6, 0]), 'rot': cyc(24, [40, -14, 72], [3, 0, 0])},
    'left_arm': {'pos': cyc(24, [-10, -13, 5.5], [0, 0.6, 0], phase=0.6), 'rot': cyc(24, [38, 14, -72], [3, 0, 0], phase=0.6)},
})

# 비늘 막기: 두 팔을 세워 얼굴 앞에서 교차
D['dragon.block'] = clip(40, loop=True, blend_in=2, bones={
    'body': {'rot': cyc(40, [16, 0, 0], [1, 0, 0])},
    'head': {'rot': {'0': [10, 0, 0]}},
    'right_arm': {'rot': cyc(40, [-118, -44, 6], [2, 0, 0])},
    'left_arm': {'rot': cyc(40, [-108, 46, -6], [2, 0, 0], phase=0.5)},
    'right_leg': {'rot': {'0': [12, 6, 6]}},
    'left_leg': {'rot': {'0': [-14, -6, -5]}},
    'root': {'pos': {'0': [0, -1, 0]}},
}, fp={
    'right_arm': {'pos': cyc(40, ([7, -11, 9.5]), [0, 0.2, 0]), 'rot': cyc(40, [52, 40, 66], [1, 0, 0])},
    'left_arm': {'pos': cyc(40, ([-7, -11.5, 9]), [0, 0.2, 0], phase=0.5), 'rot': cyc(40, [50, -40, -66], [1, 0, 0], phase=0.5)},
    'fov': {'0': [-4, 0, 0]},
})

D['dragon.block_hit'] = clip(7, blend_in=0.5, blend_out=3, bones=seq([
    (0, {'body': [16, 0, 0], 'right_arm': [-118, -44, 6], 'left_arm': [-108, 46, -6], 'root': {'pos': [0, -1, 0], 'rot': [0, 0, 0]}}),
    (1.5, {'body': [0, 0, 0], 'right_arm': [-126, -36, 6], 'left_arm': [-116, 38, -6], 'root': {'pos': [0, -1, -1.8], 'rot': [-4, 0, 0]}}, 'outExpo'),
    (7, {'body': [16, 0, 0], 'right_arm': [-118, -44, 6], 'left_arm': [-108, 46, -6], 'root': {'pos': [0, -1, 0], 'rot': [0, 0, 0]}}, 'outCubic'),
]), fp=fpseq([
    (0, {'R': ([7, -11, 9.5], [52, 40, 66]), 'L': ([-7, -11.5, 9], [50, -40, -66]), 'cam': [0, 0, 0]}),
    (1.5, {'R': ([7, -10, 7], [58, 40, 66]), 'L': ([-7, -10.5, 6.5], [56, -40, -66]), 'cam': [-2.5, 0, 1.2]}, 'outExpo'),
    (7, {'R': ([7, -11, 9.5], [52, 40, 66]), 'L': ([-7, -11.5, 9], [50, -40, -66]), 'cam': [0, 0, 0]}, 'outCubic'),
]))


# ================================================================ 평타 (사람 모습: 건틀릿 할퀴기 3연타)
def swipe(length, hit, arm, heavy=False, fp_from=None, fp_to=None, cam=(0.5, -1.2, -1.4), lunge=1.5, body_hit=None, arm_hit=None, arm_wind=None, body_wind=None, fov=-2):
    """한 번 할퀴기: hit 틱에 맞음."""
    right = arm == 'right_arm'
    sgn = 1 if right else -1
    wind = arm_wind or ([-96, 52 * sgn, 6 * sgn])
    strike = arm_hit or ([-84, -46 * sgn, -6 * sgn])
    bw = body_wind or [8, 26 * sgn, 0]
    bh = body_hit or [12, -32 * sgn, 0]
    other = 'left_arm' if right else 'right_arm'
    oth = [-40, 20 * sgn, -20 * sgn] if right else [-40, -20 * -sgn, 20 * -sgn]
    bones = seq([
        (0, DK),
        (hit * 0.45, P(body=bw, **{arm: wind, other: [-62, 18 * sgn if not right else 18, -14 if right else 14]}, root={'pos': [0, -1, -0.5]}), 'outQuad'),
        (hit, P(body=bh, head=[-4, 10 * sgn, 0], **{arm: strike, other: oth}, root={'pos': [0, -1.2, lunge]}), 'outExpo'),
        (hit + (length - hit) * 0.45, P(body=[bh[0] - 1, bh[1] * 0.85, 0], head=[-4, 8 * sgn, 0], **{arm: [strike[0] + 4, strike[1] * 0.9, strike[2]], other: oth}, root={'pos': [0, -1.1, lunge * 0.8]})),
        (length, DK, 'inOutSine'),
    ])
    fr0 = fp_from or (SW_R0 if right else SW_L0)
    fr1 = fp_to or (SW_R1 if right else SW_L1)
    oth_fp = ([-7.5, -14, 6], [32, -4, -62]) if right else ([7.5, -14, 6.5], [34, 4, 62])
    fp = fpseq([
        (0, dict(FG)),
        (hit * 0.45, {('R' if right else 'L'): fr0, ('L' if right else 'R'): oth_fp, 'cam': [-0.4, 0.8 * sgn, 0.8 * sgn], 'fov': 1}, 'outQuad'),
        (hit, {('R' if right else 'L'): fr1, 'cam': list(cam), 'fov': fov}, 'outExpo'),
        (hit + (length - hit) * 0.45, {('R' if right else 'L'): (fr1[0], [fr1[1][0] - 2, fr1[1][1] * 0.95, fr1[1][2]]), 'cam': [c * 0.5 for c in cam], 'fov': fov * 0.5}),
        (length, dict(FG), 'inOutSine'),
    ])
    return clip(length, bones=bones, fp=fp, blend_in=0.5, blend_out=2.5 if not heavy else 3)


D['dragon.attack1'] = swipe(7, 2.5, 'right_arm')
D['dragon.attack2'] = swipe(7, 2.5, 'left_arm', cam=(0.5, 1.2, 1.4))
D['dragon.attack3'] = clip(12, blend_in=0.5, blend_out=3, bones=seq([
    (0, DK),
    (3, P(body=[-12, 0, 0], head=[-18, 0, 0], right_arm=[-168, -14, 12], left_arm=[-168, 14, -12], root={'pos': [0, 0.5, -0.5], 'rot': [-4, 0, 0]}, right_leg=[6, 6, 5], left_leg=[-10, -6, -4]), 'outCubic'),
    (4.5, P(body=[36, 0, 0], head=[8, 0, 0], right_arm=[-46, -10, 6], left_arm=[-46, 10, -6], root={'pos': [0, -2.4, 2.5], 'rot': [6, 0, 0]}, right_leg=[18, 6, 6], left_leg=[-26, -4, -4]), 'outExpo'),
    (8, P(body=[32, 0, 0], head=[6, 0, 0], right_arm=[-44, -10, 8], left_arm=[-44, 10, -8], root={'pos': [0, -2.2, 2.2], 'rot': [5, 0, 0]}, right_leg=[18, 6, 6], left_leg=[-26, -4, -4])),
    (12, DK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(FG)),
    (3, {'R': UP_R, 'L': UP_L, 'cam': [-2, 0, 0], 'fov': 2}, 'outCubic'),
    (4.5, {'R': DOWN_R, 'L': DOWN_L, 'cam': [4.5, 0, 0], 'fov': -6}, 'outExpo'),
    (8, {'R': ([3.6, -11.2, 11.6], [-10, 10, 50]), 'L': ([-3.6, -11.2, 11.6], [-10, -10, -50]), 'cam': [2, 0, 0], 'fov': -3}),
    (12, dict(FG), 'inOutSine'),
]))

# ================================================================ 평타 (용화: 발톱)
D['dragon.claw1'] = swipe(7, 2.5, 'right_arm', arm_wind=[-150, 40, 24], arm_hit=[-40, -44, -4], body_wind=[0, 30, 0], body_hit=[22, -36, 0],
                          fp_from=DI_R0, fp_to=DI_R1, cam=(1.2, -1.6, -2.2), lunge=2.5, fov=-3)
D['dragon.claw2'] = swipe(7, 2.5, 'left_arm', arm_wind=[-150, -40, -24], arm_hit=[-40, 44, 4], body_wind=[0, -30, 0], body_hit=[22, 36, 0],
                          fp_from=DI_L0, fp_to=DI_L1, cam=(1.2, 1.6, 2.2), lunge=2.5, fov=-3)
D['dragon.claw3'] = clip(12, blend_in=0.5, blend_out=3, bones=seq([
    (0, DK),
    (3, P(body=[-16, 0, 0], head=[-22, 0, 0], right_arm=[-160, 30, 30], left_arm=[-160, -30, -30], root={'pos': [0, 1.2, -1], 'rot': [-6, 0, 0]}, right_leg=[4, 6, 5], left_leg=[-8, -6, -4]), 'outCubic'),
    (4.5, P(body=[40, 0, 0], head=[10, 0, 0], right_arm=[-38, -40, 4], left_arm=[-38, 40, -4], root={'pos': [0, -2.8, 3.5], 'rot': [8, 0, 0]}, right_leg=[20, 6, 6], left_leg=[-30, -4, -4]), 'outExpo'),
    (8, P(body=[36, 0, 0], head=[8, 0, 0], right_arm=[-36, -36, 6], left_arm=[-36, 36, -6], root={'pos': [0, -2.6, 3.2], 'rot': [7, 0, 0]}, right_leg=[20, 6, 6], left_leg=[-30, -4, -4])),
    (12, DK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(FG)),
    (3, {'R': DI_R0, 'L': DI_L0, 'cam': [-2.5, 0, 0], 'fov': 3}, 'outCubic'),
    (4.5, {'R': DI_R1, 'L': DI_L1, 'cam': [5.5, 0, 0], 'fov': -8}, 'outExpo'),
    (8, {'R': (DI_R1[0], [8, 28, 30]), 'L': (DI_L1[0], [8, -28, -30]), 'cam': [2.5, 0, 0], 'fov': -4}),
    (12, dict(FG), 'inOutSine'),
]))

# ================================================================ 1. 용조
DASH_POSE = P(body=[36, 0, 0], head=[-28, 0, 0], right_arm=[34, 6, 26], left_arm=[34, -6, -26], right_leg=[40, 0, 4], left_leg=[-44, 0, -4], root={'pos': [0, -2, 2], 'rot': [12, 0, 0]})


def skill1_frames(final):
    f = [
        (0, DK),
        (1.2, P(body=[24, 0, 0], head=[-18, 0, 0], right_arm=[10, 6, 22], left_arm=[10, -6, -22], root={'pos': [0, -2.4, -0.5], 'rot': [4, 0, 0]}, right_leg=[24, 0, 4], left_leg=[-24, 0, -4]), 'outQuad'),
        (2.5, DASH_POSE, 'outExpo'),
        (4, P(body=[14, 30, 0], head=[-6, -10, 0], right_arm=[-160, 44, 26], left_arm=[-50, 20, -26], root={'pos': [0, -1.2, 1.5], 'rot': [4, 0, 0]}), 'outCubic'),
        (5, P(body=[18, -30, 0], head=[-4, 12, 0], right_arm=[-36, -46, -6], left_arm=[-62, 22, -20], root={'pos': [0, -1.5, 2.5], 'rot': [6, 0, 0]}), 'outExpo'),
        (6.8, P(body=[14, -30, 0], head=[-6, 10, 0], right_arm=[-40, -40, -4], left_arm=[-160, -44, -26], root={'pos': [0, -1.2, 2], 'rot': [4, 0, 0]}), 'outCubic'),
        (8, P(body=[18, 30, 0], head=[-4, -12, 0], right_arm=[-56, -18, 20], left_arm=[-36, 46, 6], root={'pos': [0, -1.5, 2.8], 'rot': [6, 0, 0]}), 'outExpo'),
    ]
    return f + final


S1_FINAL = [
    (9.5, P(body=[6, 0, 0], head=[-8, 0, 0], right_arm=[-100, 14, 10], left_arm=[-100, -14, -10], root={'pos': [0, -1, 2], 'rot': [2, 0, 0]}), 'outCubic'),
    (11, P(body=[-14, 0, 0], head=[-14, 0, 0], right_arm=[-44, -14, 30], left_arm=[-44, 14, -30], root={'pos': [0, -1.4, -1], 'rot': [-6, 0, 0]}, right_leg=[-14, 6, 5], left_leg=[10, -6, -4]), 'outExpo'),
    (14, P(body=[-8, 6, 0], head=[-10, 0, 0], right_arm=[-48, -16, 28], left_arm=[-50, 16, -26], root={'pos': [0, -1.2, -0.8], 'rot': [-4, 0, 0]}, right_leg=[-10, 6, 5], left_leg=[6, -6, -4])),
    (19, DK, 'inOutSine'),
]
S1_FINAL_AIR = [
    (9.5, P(body=[30, 0, 0], head=[0, 0, 0], right_arm=[16, 8, 24], left_arm=[-60, 18, -18], root={'pos': [0, -2.6, 1.5], 'rot': [8, 0, 0]}, right_leg=[24, 6, 5], left_leg=[-24, -6, -4]), 'outCubic'),
    (11, P(body=[-20, -18, 0], head=[-30, 6, 0], right_arm=[-178, -8, 8], left_arm=[-40, 20, -30], root={'pos': [0, 2.5, 1], 'rot': [-10, 0, 0]}, right_leg=[0, 6, 5], left_leg=[-30, -6, -4]), 'outExpo'),
    (14, P(body=[-16, -14, 0], head=[-26, 4, 0], right_arm=[-172, -8, 10], left_arm=[-44, 20, -28], root={'pos': [0, 2, 0.8], 'rot': [-8, 0, 0]}, right_leg=[0, 6, 5], left_leg=[-26, -6, -4])),
    (19, DK, 'inOutSine'),
]


def skill1_fp(final):
    return fpseq([
        (0, dict(FG)),
        (1.2, {'R': TRAIL_R, 'L': TRAIL_L, 'cam': [1, 0, 0], 'fov': 2}, 'outQuad'),
        (2.5, {'R': TRAIL_R, 'L': TRAIL_L, 'cam': [2, 0, 0], 'fov': 12}, 'outExpo'),
        (4, {'R': DI_R0, 'L': ([-8, -14, 6], [30, -4, -62]), 'cam': [-0.5, 1, 1], 'fov': 4}, 'outCubic'),
        (5, {'R': DI_R1, 'cam': [1.2, -1.6, -2.2], 'fov': -2}, 'outExpo'),
        (6.8, {'R': ([3, -14, 9], [10, 20, 40]), 'L': DI_L0, 'cam': [-0.5, -1, -1], 'fov': 0}, 'outCubic'),
        (8, {'L': DI_L1, 'cam': [1.2, 1.6, 2.2], 'fov': -2}, 'outExpo'),
    ] + final)


D['dragon.skill1'] = clip(19, blend_in=1, blend_out=4, bones=seq(skill1_frames(S1_FINAL)), fp=skill1_fp([
    (9.5, {'R': GRAB_R, 'L': GRAB_L, 'cam': [0.5, 0, 0], 'fov': -2}, 'outCubic'),
    (11, {'R': PULL_R, 'L': PULL_L, 'cam': [-2.5, 0, 0], 'fov': 4}, 'outExpo'),
    (14, {'R': ([8, -13.5, 4], [32, 6, 78]), 'L': ([-8, -13.5, 3.5], [32, -6, -78]), 'cam': [-1, 0, 0], 'fov': 2}),
    (19, dict(FG), 'inOutSine'),
]))
D['dragon.skill1_air'] = clip(19, blend_in=1, blend_out=4, root_pivot=12, bones=seq(skill1_frames(S1_FINAL_AIR)), fp=skill1_fp([
    (9.5, {'R': LOW_R, 'L': ([-8, -14, 6], [30, -4, -62]), 'cam': [1.5, 0, 0], 'fov': 2}, 'outCubic'),
    (11, {'R': UPC_R, 'cam': [-6, -0.5, -1], 'fov': -6}, 'outExpo'),
    (14, {'R': ([3.2, -5, 10.5], [72, 10, 32]), 'cam': [-3, 0, 0], 'fov': -3}),
    (19, dict(FG), 'inOutSine'),
]))

# ================================================================ 2. 용익타
WING_FOLD = P(body=[26, 0, 0], head=[14, 0, 0], right_arm=[-60, -46, 10], left_arm=[-60, 46, -10], root={'pos': [0, -2.2, 0], 'rot': [2, 0, 0]}, right_leg=[16, 8, 6], left_leg=[-18, -6, -5])
WING_OPEN = P(body=[-14, 0, 0], head=[-24, 0, 0], right_arm=[-14, 0, 92], left_arm=[-14, 0, -92], root={'pos': [0, 0.5, -0.5], 'rot': [-6, 0, 0]}, right_leg=[6, 8, 8], left_leg=[-6, -6, -8])


def wing_strike(t, right):
    """날개로 앞을 후려침 (몸통을 비틀어 한쪽 날개를 앞으로)."""
    s = 1 if right else -1
    arm = 'right_arm' if right else 'left_arm'
    oth = 'left_arm' if right else 'right_arm'
    return [
        (t - 2, P(body=[2, 34 * s, 0], head=[-10, -14 * s, 0], **{arm: [-40, 40 * s, 80 * s], oth: [-20, 0, -80 * s]}, root={'pos': [0, -0.6, -1], 'rot': [0, 0, 0]}), 'outCubic'),
        (t, P(body=[20, -38 * s, 0], head=[-6, 14 * s, 0], **{arm: [-96, -44 * s, 10 * s], oth: [-30, 10 * s, -60 * s]}, root={'pos': [0, -1.6, 3], 'rot': [6, 0, 0]}, right_leg=[20, 6, 6], left_leg=[-28, -4, -4]), 'outExpo'),
        (t + 2.5, P(body=[18, -34 * s, 0], head=[-6, 12 * s, 0], **{arm: [-92, -40 * s, 10 * s], oth: [-32, 10 * s, -56 * s]}, root={'pos': [0, -1.5, 2.6], 'rot': [5, 0, 0]}, right_leg=[20, 6, 6], left_leg=[-28, -4, -4])),
    ]


def wing_strike_fp(t, right):
    s = 1 if right else -1
    a, o = ('R', 'L') if right else ('L', 'R')
    return [
        (t - 2, {a: (SW_R0 if right else SW_L0), o: (OUT_L if right else OUT_R), 'cam': [-0.8, 2 * s, 2 * s], 'fov': 3}, 'outCubic'),
        (t, {a: (SW_R1 if right else SW_L1), 'cam': [1.6, -2.6 * s, -3.2 * s], 'fov': -7}, 'outExpo'),
        (t + 2.5, {a: ((SW_R1 if right else SW_L1)[0], [22, 40 * s, 20 * s]), 'cam': [0.8, -1.2 * s, -1.5 * s], 'fov': -4}),
    ]


S2 = [(0, DK), (3, WING_FOLD, 'outCubic'), (4.2, WING_FOLD), (5, WING_OPEN, 'outExpo'), (7, P(body=[-8, 10, 0], head=[-16, 0, 0], right_arm=[-20, 10, 86], left_arm=[-18, -6, -88], root={'pos': [0, 0.2, -0.4], 'rot': [-4, 0, 0]}))]
S2_FP = [(0, dict(FG)), (3, {'R': ([3, -13, 9], [40, 40, 40]), 'L': ([-3, -13, 8.5], [40, -40, -40]), 'cam': [1.5, 0, 0], 'fov': -3}, 'outCubic'),
         (4.2, {'R': ([3, -13.2, 9], [40, 40, 40])}), (5, {'R': OUT_R, 'L': OUT_L, 'cam': [-3, 0, 0], 'fov': 8}, 'outExpo'), (7, {'cam': [-1.5, 0, 0], 'fov': 5})]
D['dragon.skill2'] = clip(17, blend_in=1, blend_out=4, bones=seq(S2 + wing_strike(9, True) + [(17, DK, 'inOutSine')]),
                          fp=fpseq(S2_FP + wing_strike_fp(9, True) + [(17, dict(FG), 'inOutSine')]))
D['dragon.skill2_double'] = clip(23, blend_in=1, blend_out=4, bones=seq(S2 + wing_strike(9, True) + wing_strike(14, False)[0:2] + [
    (17, P(body=[18, 34, 0], head=[-6, -12, 0], left_arm=[-92, 40, -10], right_arm=[-32, -10, 56], root={'pos': [0, -1.5, 2.6], 'rot': [5, 0, 0]}, right_leg=[20, 6, 6], left_leg=[-28, -4, -4])),
    (23, DK, 'inOutSine')]),
    fp=fpseq(S2_FP + wing_strike_fp(9, True) + wing_strike_fp(14, False) + [(23, dict(FG), 'inOutSine')]))

# ================================================================ 3. 용왕난무
D['dragon.skill3_dash'] = clip(4, blend_in=0.5, blend_out=1, bones=seq([
    (0, DK),
    (1.5, DASH_POSE, 'outExpo'),
    (4, DASH_POSE),
]), fp=fpseq([
    (0, dict(FG)),
    (1.5, {'R': TRAIL_R, 'L': TRAIL_L, 'cam': [2, 0, 0], 'fov': 14}, 'outExpo'),
    (4, {'R': TRAIL_R, 'L': TRAIL_L, 'fov': 14}),
]))


def fury(air):
    """서버 tt2 에 시작: 3 오른발톱, 6 왼발톱, 10~13 꼬리, 15 교차 내려찍기."""
    lean = 10 if air else 0
    legs_air = {'right_leg': [24, 4, 6], 'left_leg': [-30, -4, -5]} if air else {}
    def Q(**kw):
        d = P(**kw)
        if air:
            d.update({k: v for k, v in legs_air.items() if k not in kw})
            d['root'] = dict(d['root'], rot=add(d['root']['rot'], [lean, 0, 0]))
        return d
    f = [
        (0, Q(**{k: v for k, v in DASH_POSE.items()})),
        (1.5, Q(body=[12, 32, 0], head=[-6, -10, 0], right_arm=[-162, 44, 26], left_arm=[-56, 20, -20], root={'pos': [0, -1, 0.5]}), 'outCubic'),
        (3, Q(body=[18, -32, 0], head=[-4, 12, 0], right_arm=[-36, -46, -6], left_arm=[-62, 22, -20], root={'pos': [0, -1.4, 1.8]}), 'outExpo'),
        (4.5, Q(body=[12, -32, 0], head=[-6, 10, 0], right_arm=[-40, -40, -4], left_arm=[-162, -44, -26], root={'pos': [0, -1, 1]}), 'outCubic'),
        (6, Q(body=[18, 32, 0], head=[-4, -12, 0], right_arm=[-56, -18, 20], left_arm=[-36, 46, 6], root={'pos': [0, -1.4, 2]}), 'outExpo'),
        # 꼬리 후려치기: 몸을 돌려 꼬리로 쓸어냄
        (8, Q(body=[10, 30, 0], head=[-8, -20, 0], right_arm=[-40, -10, 40], left_arm=[-30, 20, -50], root={'pos': [0, -1.2, 1], 'rot': [0, -40, 0]}), 'outCubic'),
        (11, Q(body=[16, -20, 0], head=[-6, 40, 0], right_arm=[-20, 0, 70], left_arm=[-30, 0, -60], root={'pos': [0, -1.6, 1.2], 'rot': [4, 110, 0]}, right_leg=[-10, 8, 10], left_leg=[14, -6, -8]), 'outExpo'),
        (13, Q(body=[10, 0, 0], head=[-14, 10, 0], right_arm=[-120, -10, 20], left_arm=[-120, 10, -20], root={'pos': [0, -0.6, 1], 'rot': [0, 20, 0]}), 'inOutCubic'),
        # 교차 발톱 내려찍기
        (14, Q(body=[-16, 0, 0], head=[-24, 0, 0], right_arm=[-172, 22, 26], left_arm=[-172, -22, -26], root={'pos': [0, 1.2, 0.5], 'rot': [-6, 0, 0]}), 'outCubic'),
        (15, Q(body=[40, 0, 0], head=[10, 0, 0], right_arm=[-56, -46, 0], left_arm=[-56, 46, 0], root={'pos': [0, -2.8, 2.5], 'rot': [8 + (18 if air else 0), 0, 0]}, right_leg=[20, 6, 6], left_leg=[-28, -4, -4]), 'outExpo'),
        (20, Q(body=[36, 0, 0], head=[8, 0, 0], right_arm=[-54, -42, 2], left_arm=[-54, 42, -2], root={'pos': [0, -2.6, 2.2], 'rot': [6, 0, 0]}, right_leg=[20, 6, 6], left_leg=[-28, -4, -4])),
        (26, DK, 'inOutSine'),
    ]
    fp = [
        (0, {'R': TRAIL_R, 'L': TRAIL_L, 'cam': [0, 0, 0], 'fov': 10}),
        (1.5, {'R': DI_R0, 'L': ([-8, -14, 6], [30, -4, -62]), 'cam': [-0.5, 1, 1], 'fov': 2}, 'outCubic'),
        (3, {'R': DI_R1, 'cam': [1.2, -1.6, -2.2], 'fov': -2}, 'outExpo'),
        (4.5, {'R': ([3, -14, 9], [10, 20, 40]), 'L': DI_L0, 'cam': [-0.5, -1, -1], 'fov': 0}, 'outCubic'),
        (6, {'L': DI_L1, 'cam': [1.2, 1.6, 2.2], 'fov': -2}, 'outExpo'),
        (8, {'R': ([10, -15, 4], [24, -10, 80]), 'L': ([-10, -15, 3.5], [24, 10, -80]), 'cam': [0, -4, -3], 'fov': 2}, 'outCubic'),
        (11, {'cam': [0, 10, 6], 'fov': 6}, 'outExpo'),
        (13, {'R': ([5, -8, 8], [56, 16, 50]), 'L': ([-5, -8, 7.5], [56, -16, -50]), 'cam': [-1, 2, 0], 'fov': 2}, 'inOutCubic'),
        (14, {'R': ([4, -6.5, 8.5], [66, 22, 52]), 'L': ([-4, -6.5, 8], [66, -22, -52]), 'cam': [-3, 0, 0], 'fov': 3}, 'outCubic'),
        (15, {'R': ([-0.5, -12, 11], [-8, 30, 40]), 'L': ([0.5, -12, 11], [-8, -30, -40]), 'cam': [6 + (4 if air else 0), 0, 0], 'fov': -9}, 'outExpo'),
        (20, {'R': ([-0.2, -12, 10.6], [-6, 28, 40]), 'L': ([0.2, -12, 10.6], [-6, -28, -40]), 'cam': [2.5, 0, 0], 'fov': -4}),
        (26, dict(FG), 'inOutSine'),
    ]
    return clip(26, blend_in=1, blend_out=4, root_pivot=12 if air else 0, bones=seq(f), fp=fpseq(fp))


D['dragon.skill3'] = fury(False)
D['dragon.skill3_air'] = fury(True)

# 추가타: 불꽃 올려치기
D['dragon.skill3_extra'] = clip(12, blend_in=0.5, blend_out=4, root_pivot=12, bones=seq([
    (0, P(body=[36, 0, 0], head=[8, 0, 0], right_arm=[-54, -42, 2], left_arm=[-54, 42, -2], root={'pos': [0, -2.6, 2.2]})),
    (1.5, P(body=[30, 20, 0], head=[0, -6, 0], right_arm=[18, 8, 26], left_arm=[-60, 20, -20], root={'pos': [0, -2.8, 1.2], 'rot': [6, 0, 0]}, right_leg=[22, 6, 6], left_leg=[-24, -4, -4]), 'outCubic'),
    (3, P(body=[-22, -20, 0], head=[-32, 8, 0], right_arm=[-180, -6, 6], left_arm=[-36, 20, -34], root={'pos': [0, 3, 1.5], 'rot': [-10, 0, 0]}, right_leg=[0, 6, 5], left_leg=[-34, -6, -4]), 'outExpo'),
    (6, P(body=[-18, -16, 0], head=[-28, 6, 0], right_arm=[-174, -6, 8], left_arm=[-40, 20, -30], root={'pos': [0, 2.2, 1.2], 'rot': [-8, 0, 0]}, right_leg=[0, 6, 5], left_leg=[-30, -6, -4])),
    (12, DK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': ([-0.2, -12, 10.6], [-6, 28, 40]), 'L': ([0.2, -12, 10.6], [-6, -28, -40]), 'cam': [0, 0, 0], 'fov': 0}),
    (1.5, {'R': ([6, -19, 6], [4, 6, 60]), 'L': ([-8, -14, 6], [30, -4, -62]), 'cam': [1.5, 0, 0], 'fov': 2}, 'outCubic'),
    (3, {'R': UPC_R, 'cam': [-7, -0.5, -1.5], 'fov': -8}, 'outExpo'),
    (6, {'R': ([3.2, -5, 10.5], [72, 10, 32]), 'cam': [-3, 0, 0], 'fov': -4}),
    (12, dict(FG), 'inOutSine'),
]))

# ================================================================ 4. 역린 반격
STANCE = P(body=[16, 24, 0], head=[2, -16, 0], left_arm=[-104, 8, -4], right_arm=[-20, -12, 32], root={'pos': [0, -1.8, 0]}, right_leg=[14, 8, 7], left_leg=[-18, -6, -6])
D['dragon.skill4'] = clip(17, blend_in=1, blend_out=3, bones=seq([
    (0, DK),
    (2.5, STANCE, 'outBack'),
    (9, P(**dict(STANCE, body=[17, 26, 0], left_arm=[-106, 8, -4], root={'pos': [0, -2, 0]}))),
    (14, STANCE),
    (17, DK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(FG)),
    (2.5, {'L': SHIELD_L, 'R': ([7, -17, 5], [12, 4, 62]), 'cam': [0.5, 0.6, 0.6], 'fov': -3}, 'outBack'),
    (9, {'L': ([-4, -9.8, 10.2], [51, -36, -52]), 'fov': -4}),
    (14, {'L': SHIELD_L, 'cam': [0, 0, 0], 'fov': -3}),
    (17, dict(FG), 'inOutSine'),
]))

D['dragon.skill4_success'] = clip(23, blend_in=0.5, blend_out=4, root_pivot=0, bones=seq([
    (0, STANCE),
    (1.2, P(**dict(STANCE, left_arm=[-118, -40, -40], body=[4, 30, 0], root={'pos': [0, -1.2, -1.2], 'rot': [-4, 0, 0]})), 'outExpo'),
    (2.6, P(body=[8, 36, 0], head=[0, -12, 0], right_arm=[22, 10, 30], left_arm=[-80, 14, -20], root={'pos': [0, -2, 0]}), 'inQuad'),
    (4, P(body=[-8, -34, 0], head=[-14, 14, 0], right_arm=[-150, -16, 4], left_arm=[-30, 10, -26], root={'pos': [0, -0.5, 2.5], 'rot': [-2, 0, 0]}, left_leg=[-24, -6, -4]), 'outExpo'),
    # 등을 돌려 꼬리를 들어 올림 → 내려 찍기
    (8, P(body=[6, 0, 0], head=[-10, 0, 0], right_arm=[-30, 0, 40], left_arm=[-30, 0, -40], root={'pos': [0, -1, 1], 'rot': [0, 170, 0]}, right_leg=[8, 0, 6], left_leg=[-8, 0, -6]), 'inOutCubic'),
    (10, P(body=[-14, 0, 0], head=[-26, 0, 0], right_arm=[-60, 0, 50], left_arm=[-60, 0, -50], root={'pos': [0, 0.5, 1], 'rot': [-6, 178, 0]}), 'outCubic'),
    (11.5, P(body=[34, 0, 0], head=[12, 0, 0], right_arm=[-10, 0, 40], left_arm=[-10, 0, -40], root={'pos': [0, -2.2, 1], 'rot': [8, 182, 0]}, right_leg=[22, 0, 6], left_leg=[-26, 0, -6]), 'outExpo'),
    (15, P(body=[30, 0, 0], head=[10, 0, 0], right_arm=[-14, 0, 38], left_arm=[-14, 0, -38], root={'pos': [0, -2, 1], 'rot': [6, 180, 0]}, right_leg=[22, 0, 6], left_leg=[-26, 0, -6])),
    (20, P(root={'pos': [0, -0.8, 0], 'rot': [0, 360, 0]}), 'inOutCubic'),
    (23, P(root={'pos': [0, -0.8, 0], 'rot': [0, 360, 0]})),
]), fp=fpseq([
    (0, {'L': SHIELD_L, 'R': ([7, -17, 5], [12, 4, 62]), 'cam': [0, 0, 0], 'fov': -3}),
    (1.2, {'L': ([-9, -9, 9], [40, 40, -70]), 'cam': [-2.5, 0.5, 1.5], 'fov': 2}, 'outExpo'),
    (2.6, {'R': ([6, -19, 6], [4, 6, 60]), 'L': ([-8, -14, 6], [30, -4, -62]), 'cam': [0.5, 0, 0]}, 'inQuad'),
    (4, {'R': ([3, -5, 13], [70, 10, 26]), 'cam': [-4, -1.2, -2], 'fov': -7}, 'outExpo'),
    (8, {'R': HIDE_R, 'L': HIDE_L, 'cam': [0, 0, 0], 'fov': 4}, 'inOutCubic'),
    (10, {'cam': [-4, 0, 0], 'fov': 6}, 'outCubic'),
    (11.5, {'cam': [8, 0, 0], 'fov': -10}, 'outExpo'),
    (15, {'cam': [3, 0, 0], 'fov': -4}),
    (20, dict(FG), 'inOutCubic'),
    (23, dict(FG)),
]))
# 끝에서 360도 = 0도: 블렌드가 거꾸로 돌지 않게 마지막 자세를 그대로 유지
D['dragon.skill4_success']['blend_out'] = 0.1

D['dragon.skill4_fail'] = clip(12, blend_in=0.5, blend_out=4, bones=seq([
    (0, STANCE),
    (2.5, P(body=[4, 10, 0], head=[14, 0, 0], left_arm=[-50, 10, -16], right_arm=[-16, -8, 22], root={'pos': [0, -0.6, -0.6], 'rot': [-2, 0, 0]}), 'outCubic'),
    (5, P(body=[6, 10, 0], head=[10, 10, 0], left_arm=[-48, 10, -16], right_arm=[-18, -8, 22], root={'pos': [0, -0.6, -0.4]})),
    (7, P(body=[6, 10, 0], head=[10, -10, 0], left_arm=[-48, 10, -16], right_arm=[-18, -8, 22], root={'pos': [0, -0.6, -0.4]})),
    (12, DK, 'inOutSine'),
]), fp=fpseq([
    (0, {'L': SHIELD_L, 'R': ([7, -17, 5], [12, 4, 62]), 'cam': [0, 0, 0], 'fov': -3}),
    (2.5, {'L': LOW_L, 'R': LOW_R, 'cam': [2, 0, 0], 'fov': 0}, 'outCubic'),
    (5, {'cam': [1.5, 2, 0]}),
    (7, {'cam': [1.5, -2, 0]}),
    (12, dict(FG), 'inOutSine'),
]))

# ================================================================ R. 고룡 강림 (80틱, 42 도약, 56 브레스, 66 폭발)
CROUCH = P(body=[30, 0, 0], head=[22, 0, 0], right_arm=[16, -10, 44], left_arm=[16, 10, -44], root={'pos': [0, -3.2, 0]}, right_leg=[20, 12, 12], left_leg=[-20, -12, -12])
ROAR = P(body=[-22, 0, 0], head=[-40, 0, 0], right_arm=[-40, 20, 96], left_arm=[-40, -20, -96], root={'pos': [0, 0.5, 0], 'rot': [-6, 0, 0]}, right_leg=[6, 10, 12], left_leg=[-6, -10, -12])
D['dragon.ult'] = clip(80, blend_in=2, blend_out=5, root_pivot=12, bones=seq([
    (0, DK),
    (6, CROUCH, 'outCubic'),
    (16, P(**dict(CROUCH, right_arm=[20, -14, 48], left_arm=[20, 14, -48], root={'pos': [0, -3.5, 0], 'rot': [0, 0, 0]}))),
    (24, P(**dict(CROUCH, body=[34, 0, 0], head=[26, 0, 0], root={'pos': [0, -3.8, 0], 'rot': [0, 0, 0]}))),
    (27, ROAR, 'outExpo'),
    (40, P(**dict(ROAR, body=[-18, 0, 0], head=[-34, 0, 0], root={'pos': [0, 0.2, 0], 'rot': [-4, 0, 0]}))),
    (42, P(body=[30, 0, 0], head=[10, 0, 0], right_arm=[30, 0, 30], left_arm=[30, 0, -30], root={'pos': [0, -3, 0], 'rot': [4, 0, 0]}, right_leg=[24, 6, 8], left_leg=[-24, -6, -8]), 'inQuad'),
    (44, P(body=[-12, 0, 0], head=[-30, 0, 0], right_arm=[-176, -6, 12], left_arm=[-176, 6, -12], root={'pos': [0, 2, 0], 'rot': [-6, 0, 0]}, right_leg=[10, 4, 4], left_leg=[0, -4, -4]), 'outExpo'),
    (52, P(body=[4, 0, 0], head=[-12, 0, 0], right_arm=[-30, 0, 100], left_arm=[-30, 0, -100], root={'pos': [0, 1, 0], 'rot': [0, 0, 0]}, right_leg=[30, 4, 8], left_leg=[-20, -4, -8]), 'outCubic'),
    (56, P(body=[20, -30, 0], head=[16, 10, 0], right_arm=[-120, -10, 10], left_arm=[-30, 10, -70], root={'pos': [0, 1, 1], 'rot': [12, 0, 0]}, right_leg=[30, 4, 8], left_leg=[-30, -4, -8]), 'outExpo'),
    (66, P(body=[18, -28, 0], head=[18, 10, 0], right_arm=[-116, -10, 10], left_arm=[-32, 10, -68], root={'pos': [0, 0.5, 1], 'rot': [14, 0, 0]}, right_leg=[34, 4, 8], left_leg=[-26, -4, -8])),
    (72, P(body=[34, 0, 0], head=[10, 0, 0], right_arm=[-40, -20, 30], left_arm=[-40, 20, -30], root={'pos': [0, -2.6, 0.5], 'rot': [4, 0, 0]}, right_leg=[20, 8, 8], left_leg=[-24, -6, -6]), 'outBounce'),
    (80, DK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(FG)),
    (6, {'R': LOW_R, 'L': LOW_L, 'cam': [6, 0, 0], 'fov': -4}, 'outCubic'),
    (24, {'R': ([5, -15, 8], [20, 20, 40]), 'L': ([-5, -15, 7.5], [20, -20, -40]), 'cam': [8, 0, 0], 'fov': -6}),
    (27, {'R': OUT_R, 'L': OUT_L, 'cam': [-8, 0, 0], 'fov': 10}, 'outExpo'),
    (40, {'cam': [-6, 0, 0], 'fov': 8}),
    (42, {'R': LOW_R, 'L': LOW_L, 'cam': [4, 0, 0], 'fov': 0}, 'inQuad'),
    (44, {'R': UP_R, 'L': UP_L, 'cam': [-10, 0, 0], 'fov': 14}, 'outExpo'),
    (52, {'R': OUT_R, 'L': OUT_L, 'cam': [-2, 0, 0], 'fov': 8}, 'outCubic'),
    (56, {'R': ([3, -8, 16], [10, 10, 30]), 'L': ([-10, -14, 4], [20, 10, -80]), 'cam': [10, 0, 0], 'fov': -4}, 'outExpo'),
    (66, {'cam': [12, 0, 0], 'fov': -6}),
    (72, {'R': LOW_R, 'L': LOW_L, 'cam': [3, 0, 0], 'fov': -2}, 'outBounce'),
    (80, dict(FG), 'inOutSine'),
]))

# ================================================================ T. 용화
D['dragon.transform'] = clip(22, blend_in=1, blend_out=5, bones=seq([
    (0, DK),
    (5, P(body=[30, 0, 0], head=[26, 0, 0], right_arm=[-60, -60, 0], left_arm=[-60, 60, 0], root={'pos': [0, -2.8, 0]}, right_leg=[18, 10, 10], left_leg=[-18, -10, -10]), 'outCubic'),
    (6.5, P(body=[32, 0, 0], head=[28, 0, 0], right_arm=[-62, -62, 0], left_arm=[-62, 62, 0], root={'pos': [0, -3, 0]}, right_leg=[18, 10, 10], left_leg=[-18, -10, -10])),
    (8, ROAR, 'outExpo'),
    (16, P(**dict(ROAR, body=[-18, 0, 0], head=[-32, 0, 0], right_arm=[-36, 20, 90], left_arm=[-36, -20, -90]))),
    (22, DK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(FG)),
    (5, {'R': ([2.5, -12, 9], [44, 44, 44]), 'L': ([-2.5, -12, 8.5], [44, -44, -44]), 'cam': [4, 0, 0], 'fov': -5}, 'outCubic'),
    (6.5, {'R': ([2.3, -12.2, 9], [45, 45, 44]), 'cam': [5, 0, 0], 'fov': -6}),
    (8, {'R': OUT_R, 'L': OUT_L, 'cam': [-6, 0, 0], 'fov': 12}, 'outExpo'),
    (16, {'cam': [-3, 0, 0], 'fov': 6}),
    (22, dict(FG), 'inOutSine'),
]))

D['dragon.select'] = clip(26, blend_in=2, blend_out=5, bones=seq([
    (0, P(body=[0, 0, 0], head=[0, 0, 0], right_arm=[0, 0, 0], left_arm=[0, 0, 0], right_leg=[0, 0, 0], left_leg=[0, 0, 0], root={'pos': [0, 0, 0]})),
    (6, P(body=[20, 0, 0], head=[18, 0, 0], right_arm=[-60, -50, 0], left_arm=[-60, 50, 0], root={'pos': [0, -1.5, 0]}), 'outCubic'),
    (12, P(body=[22, 0, 0], head=[20, 0, 0], right_arm=[-62, -52, 0], left_arm=[-62, 52, 0], root={'pos': [0, -1.7, 0]})),
    (16, ROAR, 'outExpo'),
    (26, DK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': LOW_R, 'L': LOW_L, 'cam': [0, 0, 0]}),
    (6, {'R': ([2.5, -12, 9], [44, 44, 44]), 'L': ([-2.5, -12, 8.5], [44, -44, -44])}, 'outCubic'),
    (12, {'R': ([2.3, -12.2, 9], [45, 45, 44])}),
    (16, {'R': OUT_R, 'L': OUT_L, 'cam': [-2, 0, 0]}, 'outExpo'),
    (26, dict(FG), 'inOutSine'),
]))

write('dragon', D)

# =====================================================================================
#                                  마검사 (blade)
# =====================================================================================
# item 본 = 손 기준 검 각도 [pitch, yaw, roll]. 0 = 팔에 수직(팔을 내리면 칼끝이 앞), -85 = 팔의 연장선.
B = {}
IG = [-35, 0, 0]       # 가드: 칼끝이 앞-위
IX = [-85, 0, 0]       # 팔의 연장선 (크게 휘두를 때)
IR = [10, 180, 0]      # 칼집 쪽으로 뒤를 향함 (발도 자세)
# 1인칭 item: 0 = 칼끝이 위-앞, -70 = 팔의 연장선
FIG = [0, 0, 0]
FIX = [-70, 0, 0]
FIR = [60, 180, 0]

BGR = ([6.5, -13.5, 7.5], [34, 4, 56])        # 1인칭 가드 (검을 든 오른손)
BGL = ([-7, -15.5, 5], [20, -6, -60])          # 왼손은 낮게
B_SW_R0 = ([13, -10, 6], [30, -45, 85])        # 오른쪽으로 크게 벌림
B_SW_R1 = ([-4, -11, 11], [18, 50, 30])        # 왼쪽으로 쓸어냄
B_LOW_L = ([-5, -18, 6], [0, 30, 40])          # 왼쪽 아래 (역수)
B_UP = ([3, -3, 7], [80, 8, 40])               # 머리 위
B_DOWN = ([1, -12, 12], [-10, 6, 40])          # 내려침


def BP(**kw):
    d = {
        'body': [6, -14, 0], 'head': [-2, 12, 0],
        'right_arm': [-55, -8, 8], 'left_arm': [-22, 12, -16],
        'right_leg': [-10, 6, 4], 'left_leg': [12, -6, -4],
        'root': {'pos': [0, -0.6, 0], 'rot': [0, 0, 0]},
        'item': IG,
    }
    for k, v in kw.items():
        if k == 'root':
            d['root'] = dict(d['root'], **v)
        else:
            d[k] = v
    return d


BK = BP()


def BF(R=BGR, L=BGL, I=FIG, cam=(0, 0, 0), fov=0):
    return {'R': R, 'L': L, 'I': I, 'cam': list(cam), 'fov': fov}


def bfp(frames):
    """fpseq + 1인칭 item 채널."""
    out = fpseq([(t, {k: v for k, v in f.items() if k != 'I'}, *e) for t, f, *e in frames])
    keys, last = {}, [0, 0, 0]
    for t, f, *e in frames:
        last = f.get('I', last)
        keys[str(round(t, 3))] = rnd(last) + [e[0] if e else 'inOutSine']
    out['item'] = {'rot': keys}
    return out


BFG = BF()

# ================================================================ 이동
B['blade.idle'] = clip(60, loop=True, bones={
    'body': {'rot': cyc(60, [6, -14, 0], [1.2, 0, 0])},
    'head': {'rot': cyc(60, [-2, 12, 0], [1, 0, 0], phase=1)},
    'right_arm': {'rot': cyc(60, [-55, -8, 8], [2.5, 0, 0], phase=0.3)},
    'left_arm': {'rot': cyc(60, [-22, 12, -16], [2, 0, 0], phase=0.8)},
    'right_leg': {'rot': {'0': [-10, 6, 4]}},
    'left_leg': {'rot': {'0': [12, -6, -4]}},
    'root': {'pos': cyc(60, [0, -0.6, 0], [0, -0.25, 0])},
    'item': {'rot': cyc(60, IG, [2, 0, 1], phase=0.5)},
}, fp=bfp([(0, BFG), (30, BF(R=([6.5, -13.2, 7.6], [35, 4, 56]))), (60, BFG)]))
B['blade.idle']['loop'] = True

B['blade.walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [6, -10, 0], [0, 5, 0], phase=math.pi)},
    'head': {'rot': {'0': [-2, 10, 0]}},
    'right_arm': {'rot': cyc(20, [-50, -8, 8], [10, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-18, 10, -14], [16, 0, 0])},
    'right_leg': {'rot': cyc(20, [0, 0, 2], [32, 0, 0])},
    'left_leg': {'rot': cyc(20, [0, 0, -2], [32, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, -0.4, 0], [0, 0, 0], harm=([0, 0.45, 0], -math.pi / 2))},
    'item': {'rot': {'0': IG}},
}, fp=bfp([(0, BFG), (10, BF(R=([6.8, -13, 8], [37, 4, 56]))), (20, BFG)]))

# 질주: 검을 뒤로 늘어뜨림
B['blade.run'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [24, 0, 0], [0, 8, 0], phase=math.pi, harm=([1.5, 0, 0], 0))},
    'head': {'rot': {'0': [-20, 0, 0]}},
    'right_arm': {'rot': cyc(20, [32, 4, 16], [12, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-30, 6, -10], [50, 0, 0])},
    'right_leg': {'rot': cyc(20, [-6, 0, 2], [54, 0, 0], harm=([-8, 0, 0], 0))},
    'left_leg': {'rot': cyc(20, [-6, 0, -2], [54, 0, 0], phase=math.pi, harm=([-8, 0, 0], 0))},
    'root': {'pos': cyc(20, [0, 0.3, 0], [0, 0, 0], harm=([0, 1.1, 0], -math.pi / 2)), 'rot': {'0': [5, 0, 0]}},
    'item': {'rot': {'0': [-75, 0, 0]}},
}, fp=bfp([(0, BF(R=([9, -19, 3], [5, 0, 70]), L=([-7, -15, 5.5], [26, -4, -52]), I=FIX, fov=4)), (10, BF(R=([9, -18, 3.5], [8, 0, 70]), L=([-7, -13, 7], [30, -4, -52]), I=FIX, fov=4)),
                (20, BF(R=([9, -19, 3], [5, 0, 70]), L=([-7, -15, 5.5], [26, -4, -52]), I=FIX, fov=4))]))

B['blade.sneak'] = clip(50, loop=True, bones={
    'body': {'rot': cyc(50, [26, -14, 0], [1.5, 0, 0])},
    'head': {'rot': {'0': [-16, 12, 0]}},
    'right_arm': {'rot': cyc(50, [-62, -8, 10], [2, 0, 0], phase=0.4)},
    'left_arm': {'rot': cyc(50, [-30, 12, -16], [2, 0, 0], phase=0.9)},
    'right_leg': {'rot': {'0': [6, 6, 6]}, 'pos': {'0': [0, 0, -1]}},
    'left_leg': {'rot': {'0': [-20, -6, -5]}, 'pos': {'0': [0, 0, 1]}},
    'root': {'pos': {'0': [0, -1.5, 0]}},
    'item': {'rot': {'0': IG}},
}, fp=bfp([(0, BF(fov=-3)), (50, BF(fov=-3))]))

B['blade.sneak_walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [26, -10, 0], [0, 4, 0])},
    'head': {'rot': {'0': [-16, 10, 0]}},
    'right_arm': {'rot': cyc(20, [-60, -8, 10], [6, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-28, 10, -16], [8, 0, 0])},
    'right_leg': {'rot': cyc(20, [4, 6, 6], [24, 0, 0])},
    'left_leg': {'rot': cyc(20, [-4, -6, -5], [24, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, -1.5, 0], [0, 0, 0], harm=([0, 0.3, 0], -math.pi / 2))},
    'item': {'rot': {'0': IG}},
}, fp=bfp([(0, BF(fov=-3)), (20, BF(fov=-3))]))

B['blade.air'] = clip(24, loop=True, bones={
    'body': {'rot': cyc(24, [8, -6, 0], [2, 0, 0])},
    'right_arm': {'rot': cyc(24, [-40, 10, 40], [5, 0, 3])},
    'left_arm': {'rot': cyc(24, [-50, -10, -40], [5, 0, -3], phase=1)},
    'right_leg': {'rot': {'0': [22, 4, 6]}},
    'left_leg': {'rot': {'0': [-34, -4, -4]}},
    'item': {'rot': {'0': [-60, 0, 0]}},
}, fp=bfp([(0, BF(R=([9, -13, 6], [40, -10, 70]), I=[-30, 0, 0])), (24, BF(R=([9, -13, 6], [40, -10, 70]), I=[-30, 0, 0]))]))

# 막기: 검을 가로로 눕혀 앞을 막음
B['blade.block'] = clip(40, loop=True, blend_in=2, bones={
    'body': {'rot': cyc(40, [10, 0, 0], [1, 0, 0])},
    'head': {'rot': {'0': [6, 0, 0]}},
    'right_arm': {'rot': cyc(40, [-100, -38, 0], [2, 0, 0])},
    'left_arm': {'rot': cyc(40, [-95, 40, 0], [2, 0, 0], phase=0.5)},
    'right_leg': {'rot': {'0': [10, 4, 4]}},
    'left_leg': {'rot': {'0': [-12, -4, -4]}},
    'root': {'pos': {'0': [0, -0.8, 0]}},
    'item': {'rot': {'0': [0, -90, 0]}},
}, fp=bfp([(0, BF(R=([6, -10, 10], [50, 30, 60]), L=([-6, -10.5, 10], [48, -30, -60]), I=[0, -80, 0], fov=-4)),
           (40, BF(R=([6, -10, 10], [50, 30, 60]), L=([-6, -10.5, 10], [48, -30, -60]), I=[0, -80, 0], fov=-4))]))
B['blade.block_hit'] = clip(7, blend_in=0.5, blend_out=3, bones=seq([
    (0, {'body': [10, 0, 0], 'right_arm': [-100, -38, 0], 'left_arm': [-95, 40, 0], 'item': [0, -90, 0], 'root': {'pos': [0, -0.8, 0], 'rot': [0, 0, 0]}}),
    (1.5, {'body': [-4, 0, 0], 'right_arm': [-108, -32, 0], 'left_arm': [-103, 34, 0], 'item': [0, -90, 0], 'root': {'pos': [0, -0.8, -1.6], 'rot': [-4, 0, 0]}}, 'outExpo'),
    (7, {'body': [10, 0, 0], 'right_arm': [-100, -38, 0], 'left_arm': [-95, 40, 0], 'item': [0, -90, 0], 'root': {'pos': [0, -0.8, 0], 'rot': [0, 0, 0]}}, 'outCubic'),
]), fp=bfp([
    (0, BF(R=([6, -10, 10], [50, 30, 60]), L=([-6, -10.5, 10], [48, -30, -60]), I=[0, -80, 0])),
    (1.5, BF(R=([6, -9, 7.5], [56, 30, 60]), L=([-6, -9.5, 7.5], [54, -30, -60]), I=[0, -80, 0], cam=(-2.5, 0, 1.2)), 'outExpo'),
    (7, BF(R=([6, -10, 10], [50, 30, 60]), L=([-6, -10.5, 10], [48, -30, -60]), I=[0, -80, 0]), 'outCubic'),
]))


# ================================================================ 평타 (검 3연격)
def bswing(length, hit, wind, strike, body_w, body_h, item=IX, fp_w=B_SW_R0, fp_s=B_SW_R1, cam=(0.6, -1.4, -1.6), lunge=1.6, fov=-2, root_rot=(0, 0, 0)):
    bones = seq([
        (0, BK),
        (hit * 0.45, BP(body=body_w, right_arm=wind, item=item, root={'pos': [0, -0.8, -0.5]}), 'outQuad'),
        (hit, BP(body=body_h, right_arm=strike, item=item, left_arm=[-30, 20, -30], root={'pos': [0, -1.1, lunge], 'rot': list(root_rot)}), 'outExpo'),
        (hit + (length - hit) * 0.45, BP(body=[body_h[0] - 1, body_h[1] * 0.9, 0], right_arm=[strike[0] + 3, strike[1] * 0.95, strike[2]], item=item, left_arm=[-30, 20, -30], root={'pos': [0, -1.0, lunge * 0.8]})),
        (length, BK, 'inOutSine'),
    ])
    fp = bfp([
        (0, dict(BFG)),
        (hit * 0.45, BF(R=fp_w, I=FIX, cam=(-0.4, -cam[1] * 0.5, -cam[2] * 0.5), fov=1), 'outQuad'),
        (hit, BF(R=fp_s, I=FIX, cam=cam, fov=fov), 'outExpo'),
        (hit + (length - hit) * 0.45, BF(R=(fp_s[0], [fp_s[1][0] - 2, fp_s[1][1] * 0.95, fp_s[1][2]]), I=FIX, cam=[x * 0.5 for x in cam], fov=fov * 0.5)),
        (length, dict(BFG), 'inOutSine'),
    ])
    return clip(length, bones=bones, fp=fp, blend_in=0.5, blend_out=2.5)


# 1타: 오른쪽 → 왼쪽 가로베기
B['blade.attack1'] = bswing(8, 2.5, [-88, 55, 10], [-86, -55, -6], [6, 28, 0], [10, -34, 0])
# 2타: 왼쪽 아래 → 오른쪽 위 올려베기 (역수)
B['blade.attack2'] = bswing(8, 2.5, [-35, -45, -20], [-150, 45, 20], [8, -26, 0], [-6, 30, 0],
                            fp_w=B_LOW_L, fp_s=([12, -4, 7], [75, -40, 70]), cam=(-0.8, 1.4, 1.6))
# 3타: 머리 위에서 내려찍기
B['blade.attack3'] = clip(13, blend_in=0.5, blend_out=3, bones=seq([
    (0, BK),
    (3, BP(body=[-14, -6, 0], head=[-16, 6, 0], right_arm=[-175, -6, 6], left_arm=[-165, 16, -8], item=IX, root={'pos': [0, 0.4, -0.5], 'rot': [-4, 0, 0]}), 'outCubic'),
    (4.5, BP(body=[34, -10, 0], head=[8, 6, 0], right_arm=[-50, -8, 4], left_arm=[-50, 10, -6], item=[-50, 0, 0], root={'pos': [0, -2.4, 2.6], 'rot': [6, 0, 0]}, right_leg=[-24, 6, 6], left_leg=[18, -4, -4]), 'outExpo'),
    (8, BP(body=[30, -8, 0], head=[6, 6, 0], right_arm=[-48, -8, 4], left_arm=[-48, 10, -6], item=[-45, 0, 0], root={'pos': [0, -2.2, 2.2], 'rot': [5, 0, 0]}, right_leg=[-24, 6, 6], left_leg=[18, -4, -4])),
    (13, BK, 'inOutSine'),
]), fp=bfp([
    (0, dict(BFG)),
    (3, BF(R=B_UP, I=FIX, cam=(-2, 0, 0), fov=2), 'outCubic'),
    (4.5, BF(R=B_DOWN, I=FIX, cam=(4.5, 0, 0), fov=-6), 'outExpo'),
    (8, BF(R=([1, -12, 11.6], [-8, 6, 40]), I=FIX, cam=(2, 0, 0), fov=-3)),
    (13, dict(BFG), 'inOutSine'),
]))

# ================================================================ 1. 마검 발도
STANCE1 = BP(body=[22, 30, 0], head=[-14, -24, 0], right_arm=[-30, -62, 0], left_arm=[-18, 28, -6], item=IR,
             right_leg=[16, 10, 10], left_leg=[-30, -8, -6], root={'pos': [0, -2.8, 0], 'rot': [0, 0, 0]})
DRAWN = BP(body=[16, -38, 0], head=[-8, 30, 0], right_arm=[-92, 78, 0], left_arm=[-28, -20, -30], item=IX,
           right_leg=[-30, 8, 8], left_leg=[26, -6, -6], root={'pos': [0, -2.2, 3], 'rot': [4, 0, 0]})


def draw_clip(air):
    legs = {'right_leg': [24, 6, 8], 'left_leg': [-34, -6, -6]} if air else {}
    def Q(p):
        d = dict(p)
        d.update(legs)
        return d
    bones = seq([
        (0, BK),
        (2.5, Q(STANCE1), 'outCubic'),
        (5.5, Q(dict(STANCE1, root={'pos': [0, -3.0, -0.3], 'rot': [0, 0, 0]})), 'inOutSine'),
        (6.3, Q(DRAWN), 'outExpo'),
        (9, Q(dict(DRAWN, root={'pos': [0, -2.1, 2.6], 'rot': [3, 0, 0]}))),
        (11, Q(BP(body=[10, -20, 0], head=[-4, 10, 0], right_arm=[-40, 40, 26], left_arm=[-26, 12, -20], item=[-60, 0, 0], root={'pos': [0, -1.2, 1]})), 'outCubic'),
        (18, BK, 'inOutSine'),
    ])
    fp = bfp([
        (0, dict(BFG)),
        (2.5, BF(R=([-1, -17, 6], [6, 40, 50]), L=([-6, -18, 5], [6, -8, -50]), I=FIR, cam=(1.5, 2, 0), fov=-4), 'outCubic'),
        (5.5, BF(R=([-1.5, -17.5, 5.5], [5, 42, 50]), L=([-6, -18, 5], [6, -8, -50]), I=FIR, cam=(2, 2.5, 0), fov=-7), 'inOutSine'),
        (6.3, BF(R=([14, -10, 8], [22, -62, 85]), L=([-9, -18, 4], [10, 10, -60]), I=FIX, cam=(0, -4, -3), fov=14), 'outExpo'),
        (9, BF(R=([14, -10.5, 7.5], [20, -60, 85]), L=([-9, -18, 4], [10, 10, -60]), I=FIX, cam=(0, -2, -1.5), fov=6)),
        (11, BF(R=([10, -14, 7], [24, -20, 70]), I=[-40, 0, 0], cam=(0, 0, 0), fov=2), 'outCubic'),
        (18, dict(BFG), 'inOutSine'),
    ])
    return clip(18, blend_in=1, blend_out=4, root_pivot=12 if air else 12, bones=bones, fp=fp)


B['blade.skill1'] = draw_clip(False)
B['blade.skill1_air'] = draw_clip(True)

# ================================================================ 2. 파공참
LOW2 = BP(body=[24, 30, 0], head=[-10, -16, 0], right_arm=[30, 30, 16], left_arm=[-40, 20, -20], item=IX,
          right_leg=[14, 8, 6], left_leg=[-24, -6, -6], root={'pos': [0, -2.6, -0.4]})
UP2 = BP(body=[-18, -20, 0], head=[-26, 12, 0], right_arm=[-172, -14, 8], left_arm=[-50, 10, -40], item=IX,
         right_leg=[-6, 6, 4], left_leg=[10, -6, -4], root={'pos': [0, 0.8, 1.4], 'rot': [-6, 0, 0]})


def breaker_clip(windup, length):
    bones = seq([
        (0, BK),
        (windup * 0.7, LOW2, 'outCubic'),
        (windup, LOW2),
        (windup + 1.4, UP2, 'outExpo'),
        (windup + 4.5, dict(UP2, root={'pos': [0, 0.6, 1.2], 'rot': [-5, 0, 0]})),
        (length, BK, 'inOutSine'),
    ])
    fp = bfp([
        (0, dict(BFG)),
        (windup * 0.7, BF(R=([10, -20, 4], [-10, -10, 75]), L=([-7, -15, 5], [24, -6, -56]), I=FIX, cam=(2, -1, 0), fov=-3), 'outCubic'),
        (windup, BF(R=([10, -20.5, 3.6], [-12, -10, 75]), L=([-7, -15, 5], [24, -6, -56]), I=FIX, cam=(2.4, -1, 0), fov=-4)),
        (windup + 1.4, BF(R=([4, -3, 9], [86, 10, 45]), I=FIX, cam=(-6, 0.5, 1), fov=8), 'outExpo'),
        (windup + 4.5, BF(R=([4, -3.5, 8.6], [84, 10, 45]), I=FIX, cam=(-3, 0, 0), fov=4)),
        (length, dict(BFG), 'inOutSine'),
    ])
    return clip(length, blend_in=1, blend_out=4, bones=bones, fp=fp)


B['blade.skill2'] = breaker_clip(6, 16)
B['blade.skill2_fast'] = breaker_clip(2, 11)

# ================================================================ 3. 무형검무
VANISH = BP(body=[30, 0, 0], head=[-20, 0, 0], right_arm=[40, 10, 30], left_arm=[30, -10, -20], item=IX,
            right_leg=[20, 6, 6], left_leg=[-24, -6, -6], root={'pos': [0, -2.4, 0.5]})
B['blade.skill3_dash'] = clip(3, blend_in=0.5, blend_out=1, bones=seq([(0, BK), (1.5, VANISH, 'outExpo'), (3, VANISH)]),
                              fp=bfp([(0, dict(BFG)), (1.5, BF(R=([9, -21, 2], [-6, 0, 64]), L=([-9, -21, 2], [-6, 0, -64]), I=FIX, fov=10), 'outExpo'), (3, BF(R=([9, -21, 2], [-6, 0, 64]), L=([-9, -21, 2], [-6, 0, -64]), I=FIX, fov=10))]))

# 4방향 참격 (클립 t 2, 6, 10, 14 에 맞음)
S3_SWINGS = [
    # (준비 팔, 친 팔, 몸 준비, 몸 친, fp 준비, fp 친, cam)
    ([-88, 55, 10], [-86, -55, -6], [6, 28, 0], [10, -34, 0], B_SW_R0, B_SW_R1, (0.6, -1.4, -1.6)),
    ([-80, -50, -10], [-84, 55, 6], [6, -28, 0], [10, 34, 0], ([-10, -11, 7], [32, 40, -10]), ([12, -10, 10], [20, -50, 80]), (0.6, 1.4, 1.6)),
    ([-160, 40, 10], [-40, -40, 0], [-6, 20, 0], [24, -24, 0], ([12, -2, 6], [86, -20, 70]), ([-4, -15, 10], [-4, 30, 20]), (2.5, -1.2, -1.4)),
    ([-30, 10, 10], [-155, -10, 0], [20, 10, 0], [-10, -10, 0], ([8, -19, 5], [-6, -6, 70]), ([3, -3, 9], [84, 8, 42]), (-3, 0, 0)),
]


def dance_frames(start, swings):
    bf, ff = [], []
    for i, (w0, w1, b0, b1, f0, f1, cam) in enumerate(swings):
        t = start + i * 4
        bf += [(t - 1.6, BP(body=b0, right_arm=w0, item=IX, root={'pos': [0, -0.8, 0]}), 'inQuad'),
               (t, BP(body=b1, right_arm=w1, item=IX, left_arm=[-30, 20, -30], root={'pos': [0, -1.1, 1.2]}), 'outExpo')]
        ff += [(t - 1.6, BF(R=f0, I=FIX, cam=(0, 0, 0), fov=0), 'inQuad'),
               (t, BF(R=f1, I=FIX, cam=cam, fov=-3), 'outExpo')]
    return bf, ff


bf, ff = dance_frames(2, S3_SWINGS)
B['blade.skill3'] = clip(17, blend_in=0.5, blend_out=2, bones=seq([(0, VANISH)] + bf + [(17, BP(body=[10, -20, 0], right_arm=[-60, 20, 10], item=IX, root={'pos': [0, -1.0, 0.5]}), 'inOutSine')]),
                         fp=bfp([(0, BF(R=([9, -21, 2], [-6, 0, 64]), L=([-9, -21, 2], [-6, 0, -64]), I=FIX, fov=10))] + ff + [(17, BF(I=FIX), 'inOutSine')]))
bf, ff = dance_frames(2, [S3_SWINGS[2], S3_SWINGS[1]])
B['blade.skill3_cont'] = clip(9, blend_in=0.5, blend_out=2, bones=seq([(0, BP(body=[10, -20, 0], right_arm=[-60, 20, 10], item=IX))] + bf + [(9, BP(body=[10, -20, 0], right_arm=[-60, 20, 10], item=IX), 'inOutSine')]),
                              fp=bfp([(0, BF(I=FIX))] + ff + [(9, BF(I=FIX), 'inOutSine')]))
# 마무리: 위에서 내려찍기 (클립 t3 에 맞음)
B['blade.skill3_final'] = clip(12, blend_in=0.5, blend_out=4, root_pivot=12, bones=seq([
    (0, BP(body=[10, -20, 0], right_arm=[-60, 20, 10], item=IX, root={'pos': [0, -1.0, 0.5]})),
    (1.8, BP(body=[-18, 0, 0], head=[-22, 0, 0], right_arm=[-178, -4, 6], left_arm=[-170, 8, -6], item=IX, right_leg=[24, 4, 6], left_leg=[-10, -4, -4], root={'pos': [0, 1, 0], 'rot': [-8, 0, 0]}), 'outCubic'),
    (3, BP(body=[44, 0, 0], head=[10, 0, 0], right_arm=[-52, -4, 4], left_arm=[-48, 6, -4], item=IX, right_leg=[-20, 6, 6], left_leg=[22, -4, -4], root={'pos': [0, -2.8, 2.5], 'rot': [10, 0, 0]}), 'outExpo'),
    (7, BP(body=[40, 0, 0], head=[8, 0, 0], right_arm=[-50, -4, 4], left_arm=[-46, 6, -4], item=IX, right_leg=[-20, 6, 6], left_leg=[22, -4, -4], root={'pos': [0, -2.6, 2.2], 'rot': [8, 0, 0]})),
    (12, BK, 'inOutSine'),
]), fp=bfp([
    (0, BF(I=FIX)),
    (1.8, BF(R=B_UP, L=([-4, -4, 6.5], [82, -12, -50]), I=FIX, cam=(-3, 0, 0), fov=3), 'outCubic'),
    (3, BF(R=B_DOWN, L=([-3.5, -12, 11], [-12, -10, -50]), I=FIX, cam=(7, 0, 0), fov=-9), 'outExpo'),
    (7, BF(R=([1, -12, 11.5], [-8, 6, 40]), L=([-3.5, -12, 11], [-10, -10, -50]), I=FIX, cam=(3, 0, 0), fov=-4)),
    (12, dict(BFG), 'inOutSine'),
]))
B['blade.skill3_extra'] = bswing(9, 2.5, [-30, 10, 10], [-160, 30, 10], [20, 10, 0], [-14, -14, 0],
                                 fp_w=([8, -19, 5], [-6, -6, 70]), fp_s=([5, -2, 9], [86, -10, 50]), cam=(-4, -1, -1), fov=-5)

# ================================================================ 4. 마검 반격
GUARD4 = BP(body=[8, 10, 0], head=[4, -6, 0], right_arm=[-112, -28, 0], left_arm=[-100, 34, 0], item=[18, 0, 0],
            right_leg=[14, 8, 6], left_leg=[-16, -6, -5], root={'pos': [0, -1.4, 0]})
B['blade.skill4'] = clip(17, blend_in=1, blend_out=3, bones=seq([
    (0, BK), (2.5, GUARD4, 'outBack'), (9, dict(GUARD4, item=[20, 0, 0], root={'pos': [0, -1.6, 0], 'rot': [0, 0, 0]})), (14, GUARD4), (17, BK, 'inOutSine'),
]), fp=bfp([
    (0, dict(BFG)),
    (2.5, BF(R=([3, -9, 10], [62, 18, 20]), L=([-3, -10, 10], [58, -18, -20]), I=[20, 0, 0], cam=(0.5, 0, 0), fov=-3), 'outBack'),
    (9, BF(R=([3, -8.8, 10.2], [63, 18, 20]), L=([-3, -9.8, 10.2], [59, -18, -20]), I=[20, 0, 0], fov=-4)),
    (14, BF(R=([3, -9, 10], [62, 18, 20]), L=([-3, -10, 10], [58, -18, -20]), I=[20, 0, 0], fov=-3)),
    (17, dict(BFG), 'inOutSine'),
]))
B['blade.skill4_success'] = clip(13, blend_in=0.3, blend_out=4, bones=seq([
    (0, GUARD4),
    (1.2, BP(body=[0, -30, 0], head=[-4, 20, 0], right_arm=[-110, 60, 0], left_arm=[-60, 20, -20], item=[0, 0, 0], root={'pos': [0, -1, -1.2]}), 'outExpo'),
    (3, VANISH, 'inQuad'),
    (4.2, BP(body=[26, 24, 0], head=[-10, -14, 0], right_arm=[26, 24, 16], left_arm=[-40, 16, -20], item=IX, root={'pos': [0, -2.6, 0]}), 'outCubic'),
    (6, BP(body=[-20, -24, 0], head=[-24, 14, 0], right_arm=[-176, -10, 8], left_arm=[-40, 10, -40], item=IX, root={'pos': [0, 0.8, 1.6], 'rot': [-6, 0, 0]}), 'outExpo'),
    (9, BP(body=[-16, -20, 0], head=[-20, 12, 0], right_arm=[-172, -10, 8], left_arm=[-44, 10, -38], item=IX, root={'pos': [0, 0.6, 1.4], 'rot': [-5, 0, 0]})),
    (13, BK, 'inOutSine'),
]), fp=bfp([
    (0, BF(R=([3, -9, 10], [62, 18, 20]), L=([-3, -10, 10], [58, -18, -20]), I=[20, 0, 0])),
    (1.2, BF(R=([12, -8, 8], [50, -50, 80]), L=([-7, -15, 5], [24, -6, -56]), I=[0, 0, 0], cam=(-2.5, -2, -2), fov=4), 'outExpo'),
    (3, BF(R=([9, -21, 2], [-6, 0, 64]), L=([-9, -21, 2], [-6, 0, -64]), I=FIX, fov=10), 'inQuad'),
    (4.2, BF(R=([10, -20, 4], [-10, -10, 75]), I=FIX, cam=(2, 0, 0), fov=-2), 'outCubic'),
    (6, BF(R=([4, -3, 9], [86, 10, 45]), I=FIX, cam=(-6, 0.5, 1), fov=6), 'outExpo'),
    (13, dict(BFG), 'inOutSine'),
]))
B['blade.skill4_fail'] = clip(12, blend_in=0.5, blend_out=4, bones=seq([
    (0, GUARD4),
    (3, BP(body=[4, 10, 0], head=[12, 0, 0], right_arm=[-40, -10, 10], left_arm=[-20, 10, -10], item=[-20, 0, 0], root={'pos': [0, -0.6, -0.5]}), 'outCubic'),
    (6, BP(body=[6, 10, 0], head=[10, 10, 0], right_arm=[-38, -10, 10], left_arm=[-18, 10, -10], item=[-20, 0, 0], root={'pos': [0, -0.6, -0.4]})),
    (12, BK, 'inOutSine'),
]), fp=bfp([
    (0, BF(R=([3, -9, 10], [62, 18, 20]), L=([-3, -10, 10], [58, -18, -20]), I=[20, 0, 0])),
    (3, BF(R=([7, -17, 6], [12, 4, 60]), L=([-7, -18, 5], [10, -4, -60]), I=[-20, 0, 0], cam=(2, 0, 0)), 'outCubic'),
    (6, BF(R=([7, -17, 6], [12, 4, 60]), L=([-7, -18, 5], [10, -4, -60]), I=[-20, 0, 0], cam=(1.5, 2, 0))),
    (12, dict(BFG), 'inOutSine'),
]))

# ================================================================ R. 천지개벽 (72틱: 18~27 거대화, 32 도약, 46~50 내려베기)
RAISE = BP(body=[-14, 0, 0], head=[-34, 0, 0], right_arm=[-178, -6, 4], left_arm=[-176, 12, -4], item=IX,
           right_leg=[-6, 6, 6], left_leg=[8, -6, -6], root={'pos': [0, 0.3, 0], 'rot': [-4, 0, 0]})
B['blade.ult'] = clip(72, blend_in=2, blend_out=5, root_pivot=12, bones=seq([
    (0, BK),
    (8, RAISE, 'outCubic'),
    (18, dict(RAISE, body=[-18, 0, 0], head=[-40, 0, 0])),
    (26, dict(RAISE, body=[-10, 0, 0], head=[-30, 0, 0], root={'pos': [0, -0.6, 0], 'rot': [-2, 0, 0]}), 'outBack'),
    (31, dict(RAISE, body=[20, 0, 0], head=[-10, 0, 0], right_leg=[20, 6, 8], left_leg=[-20, -6, -8], root={'pos': [0, -2.6, 0], 'rot': [4, 0, 0]}), 'inQuad'),
    (33, dict(RAISE, right_leg=[30, 6, 8], left_leg=[10, -6, -8], root={'pos': [0, 1.5, 0], 'rot': [-6, 0, 0]}), 'outExpo'),
    (44, BP(body=[-26, 0, 0], head=[-30, 0, 0], right_arm=[-205, -6, 4], left_arm=[-200, 12, -4], item=IX, right_leg=[34, 6, 8], left_leg=[14, -6, -8], root={'pos': [0, 1, 0], 'rot': [-12, 0, 0]}), 'inOutSine'),
    (48, BP(body=[40, 0, 0], head=[14, 0, 0], right_arm=[-40, -4, 4], left_arm=[-40, 8, -4], item=IX, right_leg=[20, 6, 8], left_leg=[-30, -6, -8], root={'pos': [0, -1, 2], 'rot': [14, 0, 0]}), 'outExpo'),
    (58, BP(body=[36, 0, 0], head=[12, 0, 0], right_arm=[-40, -4, 4], left_arm=[-40, 8, -4], item=IX, right_leg=[20, 6, 8], left_leg=[-30, -6, -8], root={'pos': [0, -1.5, 2], 'rot': [12, 0, 0]})),
    (64, BP(body=[30, -10, 0], head=[8, 6, 0], right_arm=[-46, -6, 4], left_arm=[-30, 8, -10], item=IX, right_leg=[-20, 6, 6], left_leg=[20, -6, -6], root={'pos': [0, -2.6, 1], 'rot': [6, 0, 0]}), 'outBounce'),
    (72, BK, 'inOutSine'),
]), fp=bfp([
    (0, dict(BFG)),
    (8, BF(R=([4, -2, 6], [88, 4, 30]), L=([-4, -2, 5.5], [88, -4, -30]), I=FIX, cam=(-6, 0, 0), fov=6), 'outCubic'),
    (31, BF(R=([4, -2, 6], [88, 4, 30]), L=([-4, -2, 5.5], [88, -4, -30]), I=FIX, cam=(-4, 0, 0), fov=6)),
    (44, BF(R=([4, 0, 3], [100, 4, 30]), L=([-4, 0, 2.5], [100, -4, -30]), I=FIX, cam=(-8, 0, 0), fov=10), 'inOutSine'),
    (48, BF(R=B_DOWN, L=([-3.5, -12, 11], [-12, -10, -50]), I=FIX, cam=(10, 0, 0), fov=-8), 'outExpo'),
    (58, BF(R=([1, -12, 11.5], [-8, 6, 40]), L=([-3.5, -12, 11], [-10, -10, -50]), I=FIX, cam=(4, 0, 0), fov=-4)),
    (72, dict(BFG), 'inOutSine'),
]))

# ================================================================ T. 잔영보
B['blade.phantom_step'] = clip(9, blend_in=0.3, blend_out=3, bones=seq([
    (0, VANISH), (2, BP(body=[16, 20, 0], head=[-8, -10, 0], right_arm=[-30, 30, 30], left_arm=[-30, -10, -30], item=IX, root={'pos': [0, -1.8, -0.6]}), 'outExpo'),
    (9, BK, 'inOutSine'),
]), fp=bfp([
    (0, BF(R=([9, -21, 2], [-6, 0, 64]), L=([-9, -21, 2], [-6, 0, -64]), I=FIX, fov=12)),
    (2, BF(R=([10, -16, 5], [14, -10, 70]), I=FIX, cam=(0, 0, 3), fov=6), 'outExpo'),
    (9, dict(BFG), 'inOutSine'),
]))

B['blade.select'] = clip(28, blend_in=2, blend_out=5, bones=seq([
    (0, BP(body=[0, 0, 0], head=[0, 0, 0], right_arm=[0, 0, 0], left_arm=[0, 0, 0], right_leg=[0, 0, 0], left_leg=[0, 0, 0], item=[0, 0, 0], root={'pos': [0, 0, 0]})),
    (6, BP(right_arm=[-90, 40, 0], item=[-85, 0, 0]), 'outCubic'),
    (10, BP(right_arm=[-90, 40, 0], item=[-85, 180, 0]), 'linear'),
    (14, BP(right_arm=[-90, 40, 0], item=[-85, 360, 0]), 'linear'),
    (18, BP(body=[10, -30, 0], right_arm=[-60, -40, 20], item=[-60, 360, 0], root={'pos': [0, -1, 0.5]}), 'outExpo'),
    (28, BP(item=[-35, 360, 0]), 'inOutSine'),
]), fp=bfp([
    (0, BF(R=([6, -17, 6], [12, 4, 50]))),
    (6, BF(R=([10, -10, 9], [30, -30, 70]), I=FIX), 'outCubic'),
    (14, BF(R=([10, -10, 9], [30, -30, 70]), I=[-70, 360, 0]), 'linear'),
    (28, dict(BFG), 'inOutSine'),
]))
B['blade.select']['blend_out'] = 0.1

write('blade', B)

# =====================================================================================
#                                  혈귀 (blood)
# =====================================================================================
H = {}

# ---- 1인칭 기준 자세: 발톱을 앞으로 세운 낮은 손 ----------------------------------------
HGR = ([7.5, -14.5, 7], [30, 4, 60])
HGL = ([-7.5, -15, 6.5], [28, -4, -60])
H_SELF_L = ([-2.5, -12.5, 9.5], [36, 30, -20])    # 왼팔을 앞으로 내밀어 손목을 보임 (자해)
H_SELF_R0 = ([9, -9, 6], [56, -30, 80])           # 오른손 발톱을 치켜듦
H_SELF_R1 = ([1, -12, 10], [30, 36, 30])          # 손목을 긋고 지나감
H_THROW_R = ([3, -9.5, 15.5], [16, 6, 30])        # 손을 쭉 뻗음 (피의 실)
H_REEL_R = ([6, -11, 12], [24, 2, 46])            # 실을 쥔 채 끌려감
H_COCK_L = ([-11, -12, 3], [40, 30, -86])         # 왼손을 뒤로 젖혀 준비
H_X_R0 = ([8, -4, 6], [80, -26, 66])              # 교차 할퀴기 준비 (머리 위 바깥)
H_X_L0 = ([-8, -4, 5.5], [80, 26, -66])
H_X_R1 = ([-2, -13, 11], [0, 34, 24])             # 교차해 내려옴
H_X_L1 = ([2, -13, 11], [0, -34, -24])
H_PALM_R = ([4, -15, 13], [-14, 4, 40])           # 손바닥으로 땅을 짚음
H_PALM_L = ([-4, -15, 12.5], [-14, -4, -40])
H_RISE_R = ([4, -5, 12], [70, 4, 30])             # 손을 들어 올림 (피의 창을 끌어올림)
H_GRIP_L = ([-2, -10.5, 15], [20, -6, -30])       # 적의 멱살을 움켜쥠
H_CROSS_R = ([3.5, -10, 9.5], [48, 44, 56])       # 팔을 X자로 교차
H_CROSS_L = ([-3.5, -10.5, 9], [46, -44, -56])
H_CLUTCH_R = ([2, -15, 7], [26, 50, 30])          # 가슴을 움켜쥠
H_OPEN_R = ([14, -9, 5], [48, -50, 88])           # 두 팔을 활짝 (손바닥 위)
H_OPEN_L = ([-14, -9, 4.5], [48, 50, -88])


def HP(**kw):
    """혈귀 기본 자세: 몸을 숙인 포식자, 두 팔은 발톱을 세워 앞으로 늘어뜨림."""
    d = {
        'body': [14, 6, 0], 'head': [-12, -4, 0],
        'right_arm': [-30, -10, 22], 'left_arm': [-38, 14, -22],
        'right_leg': [8, 8, 6], 'left_leg': [-12, -6, -5],
        'root': {'pos': [0, -1.0, 0], 'rot': [0, 0, 0]},
    }
    for k, v in kw.items():
        if k == 'root':
            d['root'] = dict(d['root'], **v)
        else:
            d[k] = v
    return d


HK = HP()


def HF(R=HGR, L=HGL, cam=(0, 0, 0), fov=0):
    return {'R': R, 'L': L, 'cam': list(cam), 'fov': fov}


HFG = HF()

# ================================================================ 이동
H['blood.idle'] = clip(56, loop=True, bones={
    'body': {'rot': cyc(56, [14, 6, 0], [2.2, 0, 0])},
    'head': {'rot': cyc(56, [-12, -4, 0], [1.8, 3, 0], phase=1.0)},
    'right_arm': {'rot': cyc(56, [-30, -10, 22], [4, 0, 3], phase=0.3, harm=([2, 0, 1], 0.5))},
    'left_arm': {'rot': cyc(56, [-38, 14, -22], [4, 0, -3], phase=0.9, harm=([2, 0, -1], 0.5))},
    'right_leg': {'rot': {'0': [8, 8, 6]}},
    'left_leg': {'rot': {'0': [-12, -6, -5]}},
    'root': {'pos': cyc(56, [0, -1.0, 0], [0, -0.4, 0])},
}, fp={
    'right_arm': {'pos': cyc(56, HGR[0], [0, 0.5, 0.1]), 'rot': cyc(56, HGR[1], [2, 0, 2], harm=([1.5, 0, 0], 0.4))},
    'left_arm': {'pos': cyc(56, HGL[0], [0, 0.5, 0.1], phase=0.9), 'rot': cyc(56, HGL[1], [2, 0, -2], phase=0.9, harm=([1.5, 0, 0], 0.4))},
})

H['blood.walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [16, 4, 0], [0, 7, 0], phase=math.pi)},
    'head': {'rot': {'0': [-14, -2, 0]}},
    'right_arm': {'rot': cyc(20, [-26, -8, 22], [20, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-32, 10, -22], [20, 0, 0])},
    'right_leg': {'rot': cyc(20, [0, 0, 3], [34, 0, 0])},
    'left_leg': {'rot': cyc(20, [0, 0, -3], [34, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, -0.8, 0], [0, 0, 0], harm=([0, 0.5, 0], -math.pi / 2))},
}, fp={
    'right_arm': {'pos': cyc(20, HGR[0], [0.3, 0.8, 1.0], phase=math.pi), 'rot': cyc(20, HGR[1], [4, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, HGL[0], [0.3, 0.8, 1.0]), 'rot': cyc(20, HGL[1], [4, 0, 0])},
})

# 질주: 상체를 깊게 숙이고 두 팔을 뒤로 늘어뜨린 채 내달림
H['blood.run'] = clip(18, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(18, [38, 0, 0], [0, 7, 0], phase=math.pi, harm=([2.5, 0, 0], 0))},
    'head': {'rot': {'0': [-34, 0, 0]}},
    'right_arm': {'rot': cyc(18, [40, 6, 18], [12, 0, 4], phase=math.pi)},
    'left_arm': {'rot': cyc(18, [40, -6, -18], [12, 0, -4])},
    'right_leg': {'rot': cyc(18, [-10, 0, 2], [58, 0, 0], harm=([-8, 0, 0], 0))},
    'left_leg': {'rot': cyc(18, [-10, 0, -2], [58, 0, 0], phase=math.pi, harm=([-8, 0, 0], 0))},
    'root': {'pos': cyc(18, [0, -0.6, 0], [0, 0, 0], harm=([0, 1.2, 0], -math.pi / 2)), 'rot': {'0': [8, 0, 0]}},
}, fp={
    'right_arm': {'pos': cyc(18, [9, -19.5, 3.5], [0.4, 1.4, 1.2], phase=math.pi), 'rot': cyc(18, [4, 0, 66], [8, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(18, [-9, -19.5, 3], [0.4, 1.4, 1.2]), 'rot': cyc(18, [4, 0, -66], [8, 0, 0])},
    'camera': {'rot': cyc(18, [0, 0, 0], [0, 0, 0.8], harm=([0.7, 0, 0], 0))},
    'fov': {'0': [6, 0, 0]},
})

H['blood.sneak'] = clip(50, loop=True, bones={
    'body': {'rot': cyc(50, [34, 6, 0], [1.5, 0, 0])},
    'head': {'rot': {'0': [-26, -4, 0]}},
    'right_arm': {'rot': cyc(50, [-36, -10, 26], [3, 0, 0], phase=0.4)},
    'left_arm': {'rot': cyc(50, [-44, 14, -26], [3, 0, 0], phase=0.9)},
    'right_leg': {'rot': {'0': [16, 8, 7]}, 'pos': {'0': [0, 0, -1]}},
    'left_leg': {'rot': {'0': [-22, -6, -6]}, 'pos': {'0': [0, 0, 1]}},
    'root': {'pos': {'0': [0, -1.6, 0]}},
}, fp={
    'right_arm': {'pos': cyc(50, [7, -15.5, 8], [0, 0.3, 0]), 'rot': cyc(50, [26, 4, 62], [1.5, 0, 0])},
    'left_arm': {'pos': cyc(50, [-7, -16, 7.5], [0, 0.3, 0], phase=0.6), 'rot': cyc(50, [24, -4, -62], [1.5, 0, 0], phase=0.6)},
    'fov': {'0': [-3, 0, 0]},
})

H['blood.sneak_walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [34, 4, 0], [0, 5, 0])},
    'head': {'rot': {'0': [-26, -2, 0]}},
    'right_arm': {'rot': cyc(20, [-36, -8, 26], [10, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-44, 12, -26], [10, 0, 0])},
    'right_leg': {'rot': cyc(20, [4, 6, 7], [26, 0, 0])},
    'left_leg': {'rot': cyc(20, [-4, -6, -6], [26, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, -1.6, 0], [0, 0, 0], harm=([0, 0.3, 0], -math.pi / 2))},
}, fp={
    'right_arm': {'pos': cyc(20, [7, -15.5, 8], [0.2, 0.4, 0.6], phase=math.pi), 'rot': cyc(20, [26, 4, 62], [2, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, [-7, -16, 7.5], [0.2, 0.4, 0.6]), 'rot': cyc(20, [24, -4, -62], [2, 0, 0])},
    'fov': {'0': [-3, 0, 0]},
})

H['blood.air'] = clip(24, loop=True, bones={
    'body': {'rot': cyc(24, [12, 0, 0], [2, 0, 0])},
    'head': {'rot': {'0': [-10, 0, 0]}},
    'right_arm': {'rot': cyc(24, [-50, 10, 50], [8, 0, 6])},
    'left_arm': {'rot': cyc(24, [-50, -10, -50], [8, 0, -6], phase=0.6)},
    'right_leg': {'rot': {'0': [28, 4, 6]}},
    'left_leg': {'rot': {'0': [-30, -4, -5]}},
}, fp={
    'right_arm': {'pos': cyc(24, [10, -13, 6], [0, 0.6, 0]), 'rot': cyc(24, [40, -14, 72], [3, 0, 0])},
    'left_arm': {'pos': cyc(24, [-10, -13, 5.5], [0, 0.6, 0], phase=0.6), 'rot': cyc(24, [38, 14, -72], [3, 0, 0], phase=0.6)},
})

# 막기: 두 팔을 X자로 교차 (발톱이 바깥으로)
H_BLOCK = {'body': [18, 0, 0], 'head': [8, 0, 0], 'right_arm': [-112, -46, 8], 'left_arm': [-104, 48, -8], 'right_leg': [12, 6, 6], 'left_leg': [-14, -6, -5], 'root': {'pos': [0, -1.2, 0], 'rot': [0, 0, 0]}}
H['blood.block'] = clip(40, loop=True, blend_in=2, bones={
    'body': {'rot': cyc(40, [18, 0, 0], [1.2, 0, 0])},
    'head': {'rot': {'0': [8, 0, 0]}},
    'right_arm': {'rot': cyc(40, [-112, -46, 8], [2, 0, 0])},
    'left_arm': {'rot': cyc(40, [-104, 48, -8], [2, 0, 0], phase=0.5)},
    'right_leg': {'rot': {'0': [12, 6, 6]}},
    'left_leg': {'rot': {'0': [-14, -6, -5]}},
    'root': {'pos': {'0': [0, -1.2, 0]}},
}, fp={
    'right_arm': {'pos': cyc(40, H_CROSS_R[0], [0, 0.2, 0]), 'rot': cyc(40, H_CROSS_R[1], [1, 0, 0])},
    'left_arm': {'pos': cyc(40, H_CROSS_L[0], [0, 0.2, 0], phase=0.5), 'rot': cyc(40, H_CROSS_L[1], [1, 0, 0], phase=0.5)},
    'fov': {'0': [-4, 0, 0]},
})
H['blood.block_hit'] = clip(7, blend_in=0.5, blend_out=3, bones=seq([
    (0, H_BLOCK),
    (1.5, dict(H_BLOCK, body=[2, 0, 0], right_arm=[-120, -40, 8], left_arm=[-112, 42, -8], root={'pos': [0, -1.2, -1.8], 'rot': [-4, 0, 0]}), 'outExpo'),
    (7, H_BLOCK, 'outCubic'),
]), fp=fpseq([
    (0, {'R': H_CROSS_R, 'L': H_CROSS_L, 'cam': [0, 0, 0]}),
    (1.5, {'R': (add(H_CROSS_R[0], [0, 1, -2.5]), add(H_CROSS_R[1], [6, 0, 0])), 'L': (add(H_CROSS_L[0], [0, 1, -2.5]), add(H_CROSS_L[1], [6, 0, 0])), 'cam': [-2.5, 0, 1.2]}, 'outExpo'),
    (7, {'R': H_CROSS_R, 'L': H_CROSS_L, 'cam': [0, 0, 0]}, 'outCubic'),
]))


# ================================================================ 평타 (피의 발톱 3연격)
def hswipe(length, hit, right, wind, strike, body_w, body_h, fp_from, fp_to, cam, lunge=1.6, fov=-2):
    sgn = 1 if right else -1
    arm, other = ('right_arm', 'left_arm') if right else ('left_arm', 'right_arm')
    oth = [-30, 18 * sgn, -26 * sgn]
    bones = seq([
        (0, HK),
        (hit * 0.45, HP(body=body_w, **{arm: wind, other: oth}, root={'pos': [0, -1.1, -0.5]}), 'outQuad'),
        (hit, HP(body=body_h, head=[-8, 10 * sgn, 0], **{arm: strike, other: oth}, root={'pos': [0, -1.4, lunge]}), 'outExpo'),
        (hit + (length - hit) * 0.45, HP(body=[body_h[0] - 1, body_h[1] * 0.85, 0], head=[-8, 8 * sgn, 0], **{arm: add(strike, [4, -strike[1] * 0.1, 0]), other: oth}, root={'pos': [0, -1.3, lunge * 0.8]})),
        (length, HK, 'inOutSine'),
    ])
    a, o = ('R', 'L') if right else ('L', 'R')
    oth_fp = HGL if right else HGR
    fp = fpseq([
        (0, dict(HFG)),
        (hit * 0.45, {a: fp_from, o: oth_fp, 'cam': [-0.4, 0.8 * sgn, 0.8 * sgn], 'fov': 1}, 'outQuad'),
        (hit, {a: fp_to, 'cam': list(cam), 'fov': fov}, 'outExpo'),
        (hit + (length - hit) * 0.45, {a: (fp_to[0], [fp_to[1][0] - 2, fp_to[1][1] * 0.95, fp_to[1][2]]), 'cam': [c * 0.5 for c in cam], 'fov': fov * 0.5}),
        (length, dict(HFG), 'inOutSine'),
    ])
    return clip(length, bones=bones, fp=fp, blend_in=0.5, blend_out=2.5)


# 1타: 오른발톱 비스듬히 내려 할큄 / 2타: 왼발톱 반대로 / 3타: 두 손 내려찍기 (출혈)
H['blood.attack1'] = hswipe(7, 2.5, True, [-150, 36, 24], [-44, -42, -6], [2, 28, 0], [22, -34, 0], DI_R0, DI_R1, (1.0, -1.4, -2.0), lunge=2)
H['blood.attack2'] = hswipe(7, 2.5, False, [-150, -36, -24], [-44, 42, 6], [2, -28, 0], [22, 34, 0], DI_L0, DI_L1, (1.0, 1.4, 2.0), lunge=2)
H['blood.attack3'] = clip(12, blend_in=0.5, blend_out=3, bones=seq([
    (0, HK),
    (3, HP(body=[-14, 0, 0], head=[-22, 0, 0], right_arm=[-166, 26, 24], left_arm=[-166, -26, -24], root={'pos': [0, 0.8, -0.8], 'rot': [-5, 0, 0]}, right_leg=[4, 6, 5], left_leg=[-10, -6, -4]), 'outCubic'),
    (4.5, HP(body=[42, 0, 0], head=[8, 0, 0], right_arm=[-36, -36, 4], left_arm=[-36, 36, -4], root={'pos': [0, -2.8, 3.2], 'rot': [8, 0, 0]}, right_leg=[20, 6, 6], left_leg=[-30, -4, -4]), 'outExpo'),
    (8, HP(body=[38, 0, 0], head=[6, 0, 0], right_arm=[-34, -32, 6], left_arm=[-34, 32, -6], root={'pos': [0, -2.6, 3.0], 'rot': [7, 0, 0]}, right_leg=[20, 6, 6], left_leg=[-30, -4, -4])),
    (12, HK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(HFG)),
    (3, {'R': DI_R0, 'L': DI_L0, 'cam': [-2.5, 0, 0], 'fov': 3}, 'outCubic'),
    (4.5, {'R': DI_R1, 'L': DI_L1, 'cam': [5.5, 0, 0], 'fov': -8}, 'outExpo'),
    (8, {'R': (DI_R1[0], [8, 28, 30]), 'L': (DI_L1[0], [8, -28, -30]), 'cam': [2.5, 0, 0], 'fov': -4}),
    (12, dict(HFG), 'inOutSine'),
]))

# ================================================================ 1. 혈사포박
# 손을 뻗어 피의 실을 쏨 (서버 tt2 에 박힘) → 끌려감. 할퀴기는 서버가 닿는 순간 skill1_rip 을 보냄.
THROW = HP(body=[6, -24, 0], head=[-10, 18, 0], right_arm=[-94, -10, 4], left_arm=[-20, 20, -40], root={'pos': [0, -0.6, 1.2]}, right_leg=[-12, 6, 5], left_leg=[14, -6, -4])
REEL = HP(body=[30, -10, 0], head=[-24, 8, 0], right_arm=[-100, -6, 6], left_arm=[30, 30, -50], root={'pos': [0, -1.6, 1.5], 'rot': [10, 0, 0]}, right_leg=[34, 4, 6], left_leg=[-40, -4, -5])
REEL_AIR = HP(body=[0, -10, 0], head=[-40, 8, 0], right_arm=[-150, -6, 6], left_arm=[20, 30, -50], root={'pos': [0, 0, 1.5], 'rot': [-24, 0, 0]}, right_leg=[40, 4, 6], left_leg=[10, -4, -5])


def thread_clip(air):
    pull = REEL_AIR if air else REEL
    return clip(18, blend_in=0.5, blend_out=3, root_pivot=12 if air else 0, bones=seq([
        (0, HK),
        (1, HP(body=[10, 20, 0], head=[-10, -10, 0], right_arm=[-60, 40, 40], left_arm=[-30, 18, -26], root={'pos': [0, -1.2, -0.6]}), 'outQuad'),
        (2, THROW, 'outExpo'),
        (3.5, THROW),
        (5, pull, 'inOutCubic'),
        (12, pull),
        (18, HK, 'inOutSine'),
    ]), fp=fpseq([
        (0, dict(HFG)),
        (1, {'R': ([8, -10, 5], [50, -30, 70]), 'L': HGL, 'cam': [0, 1, 0.5], 'fov': 1}, 'outQuad'),
        (2, {'R': H_THROW_R, 'cam': [-1, -1, -1], 'fov': -3}, 'outExpo'),
        (3.5, {'R': H_THROW_R, 'cam': [0, 0, 0], 'fov': -2}),
        (5, {'R': H_REEL_R, 'L': H_COCK_L, 'cam': [-2 if air else 1, 0, 0], 'fov': 14}, 'inOutCubic'),
        (12, {'R': H_REEL_R, 'L': H_COCK_L, 'fov': 14}),
        (18, dict(HFG), 'inOutSine'),
    ]))


H['blood.skill1'] = thread_clip(False)
H['blood.skill1_air'] = thread_clip(True)
H['blood.skill1_miss'] = clip(10, blend_in=0.5, blend_out=3, bones=seq([
    (0, HK),
    (1, HP(body=[10, 20, 0], head=[-10, -10, 0], right_arm=[-60, 40, 40], root={'pos': [0, -1.2, -0.6]}), 'outQuad'),
    (2, THROW, 'outExpo'),
    (5, THROW),
    (7, HP(body=[4, 10, 0], right_arm=[-60, 10, 30], root={'pos': [0, -1, -0.4]}), 'inOutCubic'),
    (10, HK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(HFG)),
    (1, {'R': ([8, -10, 5], [50, -30, 70]), 'cam': [0, 1, 0.5]}, 'outQuad'),
    (2, {'R': H_THROW_R, 'cam': [-1, -1, -1], 'fov': -3}, 'outExpo'),
    (5, {'R': H_THROW_R, 'cam': [0, 0, 0], 'fov': -2}),
    (7, {'R': ([7, -12, 7], [40, 0, 60]), 'cam': [0.6, 0, 0], 'fov': 0}, 'inOutCubic'),
    (10, dict(HFG), 'inOutSine'),
]))


def rip_clip(air):
    """닿는 순간 (t0) 두 발톱 교차로 찢고 (t1.5) 적을 띄움."""
    lift = [-10, 0, 0] if air else [0, 0, 0]
    end = HP(body=[-10, 0, 0], head=[-24, 0, 0], right_arm=[-170, -10, 14], left_arm=[-40, 20, -40], root={'pos': [0, 1.5, 0.5], 'rot': lift}) if air else \
        HP(body=[36, 0, 0], head=[4, 0, 0], right_arm=[-30, -40, 4], left_arm=[-30, 40, -4], root={'pos': [0, -2.4, 2.2], 'rot': [6, 0, 0]}, right_leg=[20, 6, 6], left_leg=[-28, -4, -4])
    return clip(14, blend_in=0.6, blend_out=3, root_pivot=12 if air else 0, bones=seq([
        (0, HP(body=[-10, 0, 0], head=[-24, 0, 0], right_arm=[-160, 32, 26], left_arm=[-160, -32, -26], root={'pos': [0, 0.6, 0.4], 'rot': lift}), 'outCubic'),
        (1.5, HP(body=[40, 0, 0], head=[8, 0, 0], right_arm=[-34, -50, 0], left_arm=[-34, 50, 0], root={'pos': [0, -2.6, 2.6], 'rot': add([8, 0, 0], lift)}, right_leg=[20, 6, 6], left_leg=[-28, -4, -4]), 'outExpo'),
        (3.5, HP(body=[36, 0, 0], head=[6, 0, 0], right_arm=[-36, -46, 2], left_arm=[-36, 46, -2], root={'pos': [0, -2.4, 2.4], 'rot': add([6, 0, 0], lift)}, right_leg=[20, 6, 6], left_leg=[-28, -4, -4])),
        (6, end, 'outExpo' if air else 'inOutSine'),
        (14, HK, 'inOutSine'),
    ]), fp=fpseq([
        (0, {'R': H_X_R0, 'L': H_X_L0, 'cam': [-2.5, 0, 0], 'fov': 4}, 'outCubic'),
        (1.5, {'R': H_X_R1, 'L': H_X_L1, 'cam': [5, 0, 0], 'fov': -9}, 'outExpo'),
        (3.5, {'R': (H_X_R1[0], [4, 30, 26]), 'L': (H_X_L1[0], [4, -30, -26]), 'cam': [2.5, 0, 0], 'fov': -4}),
        (6, {'R': UPC_R, 'L': HGL, 'cam': [-5, 0, 0], 'fov': -4} if air else {'R': HGR, 'L': HGL, 'cam': [1, 0, 0], 'fov': -2}, 'outExpo' if air else 'inOutSine'),
        (14, dict(HFG), 'inOutSine'),
    ]))


H['blood.skill1_rip'] = rip_clip(False)
H['blood.skill1_rip_air'] = rip_clip(True)

# ================================================================ 2. 혈창분출
# 오른발톱으로 왼손목을 그어 피를 내고 (t3) 두 손바닥으로 땅을 침 (t5) → 서버 tt6/8/10 에 창이 앞으로 솟음
SELF0 = HP(body=[8, 16, 0], head=[14, -10, 0], left_arm=[-70, -20, 0], right_arm=[-140, -30, 40], root={'pos': [0, -0.8, -0.3]})
SELF1 = HP(body=[12, -14, 0], head=[16, 4, 0], left_arm=[-66, -24, 0], right_arm=[-66, 44, -10], root={'pos': [0, -1, 0]})
SLAM = HP(body=[54, 0, 0], head=[0, 0, 0], right_arm=[-62, -10, 10], left_arm=[-62, 10, -10], root={'pos': [0, -4.2, 1.5], 'rot': [6, 0, 0]}, right_leg=[-30, 10, 10], left_leg=[40, -10, -10])
H['blood.skill2'] = clip(16, blend_in=0.6, blend_out=4, bones=seq([
    (0, HK),
    (1.5, SELF0, 'outCubic'),
    (2.8, SELF1, 'outExpo'),
    (3.6, HP(body=[-6, 0, 0], head=[-20, 0, 0], right_arm=[-160, 10, 20], left_arm=[-160, -10, -20], root={'pos': [0, 0.4, 0]}), 'outCubic'),
    (5, SLAM, 'outExpo'),
    (11, dict(SLAM, body=[52, 0, 0], root={'pos': [0, -4.0, 1.4], 'rot': [6, 0, 0]})),
    (16, HK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(HFG)),
    (1.5, {'R': H_SELF_R0, 'L': H_SELF_L, 'cam': [2, 0.5, 0], 'fov': -3}, 'outCubic'),
    (2.8, {'R': H_SELF_R1, 'cam': [2.5, -1.5, -1.5], 'fov': -5}, 'outExpo'),
    (3.6, {'R': UP_R, 'L': UP_L, 'cam': [-3, 0, 0], 'fov': 3}, 'outCubic'),
    (5, {'R': H_PALM_R, 'L': H_PALM_L, 'cam': [12, 0, 0], 'fov': -6}, 'outExpo'),
    (11, {'R': H_PALM_R, 'L': H_PALM_L, 'cam': [9, 0, 0], 'fov': -4}),
    (16, dict(HFG), 'inOutSine'),
]))
# 1번 직후: 손목을 긋자마자 (t1.2) 한 손으로 땅을 치고 (t2.2) 들어 올리며 적 밑에서 솟구침 (서버 tt3)
H['blood.skill2_fast'] = clip(12, blend_in=0.5, blend_out=3, bones=seq([
    (0, SELF0),
    (1.2, SELF1, 'outExpo'),
    (2.2, HP(body=[46, 10, 0], head=[-4, -6, 0], right_arm=[-40, -8, 10], left_arm=[-50, 10, -20], root={'pos': [0, -3.4, 1.0], 'rot': [4, 0, 0]}, right_leg=[-24, 8, 8], left_leg=[30, -8, -8]), 'outExpo'),
    (3.4, HP(body=[-14, -10, 0], head=[-26, 6, 0], right_arm=[-176, -16, 14], left_arm=[-40, 16, -30], root={'pos': [0, 0.6, 0.6], 'rot': [-4, 0, 0]}), 'outExpo'),
    (7, HP(body=[-10, -8, 0], head=[-22, 4, 0], right_arm=[-170, -14, 14], left_arm=[-44, 16, -28], root={'pos': [0, 0.4, 0.5], 'rot': [-3, 0, 0]})),
    (12, HK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': H_SELF_R0, 'L': H_SELF_L, 'cam': [2, 0.5, 0], 'fov': -3}),
    (1.2, {'R': H_SELF_R1, 'cam': [2.5, -1.5, -1.5], 'fov': -5}, 'outExpo'),
    (2.2, {'R': H_PALM_R, 'L': HGL, 'cam': [9, 0, 0], 'fov': -4}, 'outExpo'),
    (3.4, {'R': H_RISE_R, 'cam': [-8, 0, 0], 'fov': 8}, 'outExpo'),
    (7, {'R': (H_RISE_R[0], [66, 4, 30]), 'cam': [-4, 0, 0], 'fov': 4}),
    (12, dict(HFG), 'inOutSine'),
]))

# ================================================================ 3. 흡혈난무
H_DASH = HP(body=[40, 0, 0], head=[-30, 0, 0], right_arm=[40, 6, 24], left_arm=[40, -6, -24], right_leg=[40, 0, 4], left_leg=[-44, 0, -4], root={'pos': [0, -2, 2], 'rot': [14, 0, 0]})
H['blood.skill3_dash'] = clip(4, blend_in=0.5, blend_out=1, bones=seq([
    (0, HK),
    (1.5, H_DASH, 'outExpo'),
    (4, H_DASH),
]), fp=fpseq([
    (0, dict(HFG)),
    (1.5, {'R': TRAIL_R, 'L': TRAIL_L, 'cam': [2, 0, 0], 'fov': 14}, 'outExpo'),
    (4, {'R': TRAIL_R, 'L': TRAIL_L, 'fov': 14}),
]))


def frenzy_frames():
    """서버 tt1 에 시작 → 할퀴기 tt3/6/9/12 = 클립 t2/5/8/11 (오, 왼, 오, 왼)."""
    f = [(0, H_DASH)]
    fp = [(0, {'R': TRAIL_R, 'L': TRAIL_L, 'cam': [0, 0, 0], 'fov': 10})]
    for i, t in enumerate((2, 5, 8, 11)):
        right = i % 2 == 0
        s = 1 if right else -1
        arm, oth = ('right_arm', 'left_arm') if right else ('left_arm', 'right_arm')
        f += [
            (t - 1.2, HP(body=[10, 32 * s, 0], head=[-8, -10 * s, 0], **{arm: [-160, 44 * s, 26 * s], oth: [-50, 20 * s, -20 * s]}, root={'pos': [0, -1, 0.5]}), 'outCubic'),
            (t, HP(body=[20, -34 * s, 0], head=[-6, 12 * s, 0], **{arm: [-34, -46 * s, -6 * s], oth: [-56, 22 * s, -20 * s]}, root={'pos': [0, -1.5, 1.8]}), 'outExpo'),
        ]
        a, o = ('R', 'L') if right else ('L', 'R')
        fp += [
            (t - 1.2, {a: DI_R0 if right else DI_L0, o: HGL if right else HGR, 'cam': [-0.5, 1 * s, 1 * s], 'fov': 2}, 'outCubic'),
            (t, {a: DI_R1 if right else DI_L1, 'cam': [1.4, -1.8 * s, -2.4 * s], 'fov': -3}, 'outExpo'),
        ]
    return f, fp


_f, _fp = frenzy_frames()
GRAB = HP(body=[24, -10, 0], head=[-14, 6, 0], left_arm=[-96, 18, 0], right_arm=[-30, -20, 40], root={'pos': [0, -1.6, 1.4]})
H['blood.skill3'] = clip(13, blend_in=0.5, blend_out=2, bones=seq(_f + [(13, GRAB, 'inOutCubic')]),
                         fp=fpseq(_fp + [(13, {'R': HGR, 'L': H_GRIP_L, 'cam': [0, 0, 0], 'fov': -2}, 'inOutCubic')]))

# 물어뜯어 흡혈: 왼손으로 멱살을 잡고 목덜미에 이빨을 박음 (서버 tt15~30 3틱마다 흡혈)
BITE = HP(body=[44, -16, 0], head=[22, 14, 0], left_arm=[-96, 30, 0], right_arm=[-60, -40, 30], root={'pos': [0, -2.4, 3.2], 'rot': [4, 0, 0]})
_bite = [(0, GRAB), (1.6, BITE, 'outExpo')]
_bite_fp = [(0, {'R': HGR, 'L': H_GRIP_L, 'cam': [0, 0, 0], 'fov': -2}), (1.6, {'R': ([10, -12, 8], [40, -30, 80]), 'L': ([-1, -11, 13], [20, -10, -30]), 'cam': [6, -3, -3], 'fov': -8}, 'outExpo')]
for k in range(5):
    t = 3.5 + k * 3
    _bite.append((t, dict(BITE, body=[48, -16, 0], head=[26, 14, 0], root={'pos': [0, -2.6, 3.4], 'rot': [5, 0, 0]}), 'outQuad'))
    _bite.append((t + 1.5, BITE, 'inOutSine'))
    _bite_fp.append((t, {'cam': [7.5, -3, -3.5], 'fov': -9}, 'outQuad'))
    _bite_fp.append((t + 1.5, {'cam': [6, -3, -3], 'fov': -8}, 'inOutSine'))
H['blood.skill3_drain'] = clip(17, blend_in=0.6, blend_out=2, bones=seq(_bite + [(17, BITE)]), fp=fpseq(_bite_fp + [(17, {'cam': [6, -3, -3], 'fov': -8})]))

# 들어 올려 내리찍기 (서버 tt31 에 시작, tt34 = 클립 t3 에 찍음)
H['blood.skill3_throw'] = clip(14, blend_in=0.8, blend_out=4, bones=seq([
    (0, BITE),
    (2, HP(body=[-20, 0, 0], head=[-30, 0, 0], right_arm=[-176, 20, 16], left_arm=[-176, -20, -16], root={'pos': [0, 1.2, 0], 'rot': [-6, 0, 0]}, right_leg=[6, 6, 5], left_leg=[-10, -6, -4]), 'outCubic'),
    (3, HP(body=[48, 0, 0], head=[10, 0, 0], right_arm=[-40, -24, 4], left_arm=[-40, 24, -4], root={'pos': [0, -3.2, 3], 'rot': [10, 0, 0]}, right_leg=[22, 6, 6], left_leg=[-30, -4, -4]), 'outExpo'),
    (8, HP(body=[44, 0, 0], head=[8, 0, 0], right_arm=[-38, -22, 6], left_arm=[-38, 22, -6], root={'pos': [0, -3, 2.8], 'rot': [8, 0, 0]}, right_leg=[22, 6, 6], left_leg=[-30, -4, -4])),
    (14, HK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': ([10, -12, 8], [40, -30, 80]), 'L': ([-1, -11, 13], [20, -10, -30]), 'cam': [6, -3, -3], 'fov': -8}),
    (2, {'R': UP_R, 'L': UP_L, 'cam': [-6, 0, 0], 'fov': 6}, 'outCubic'),
    (3, {'R': DOWN_R, 'L': DOWN_L, 'cam': [9, 0, 0], 'fov': -10}, 'outExpo'),
    (8, {'R': (DOWN_R[0], [-8, 10, 50]), 'L': (DOWN_L[0], [-8, -10, -50]), 'cam': [3, 0, 0], 'fov': -4}),
    (14, dict(HFG), 'inOutSine'),
]))

# ================================================================ 4. 혈막반격
VEIL = HP(body=[24, 0, 0], head=[12, 0, 0], right_arm=[-104, -50, 10], left_arm=[-98, 52, -10], root={'pos': [0, -2, 0]}, right_leg=[14, 10, 8], left_leg=[-16, -10, -8])
H['blood.skill4'] = clip(17, blend_in=1, blend_out=3, bones=seq([
    (0, HK),
    (2.5, VEIL, 'outBack'),
    (9, dict(VEIL, body=[26, 0, 0], root={'pos': [0, -2.2, 0], 'rot': [0, 0, 0]})),
    (14, VEIL),
    (17, HK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(HFG)),
    (2.5, {'R': H_CROSS_R, 'L': H_CROSS_L, 'cam': [1, 0, 0], 'fov': -4}, 'outBack'),
    (9, {'R': (H_CROSS_R[0], [50, 44, 56]), 'L': (H_CROSS_L[0], [48, -44, -56]), 'fov': -5}),
    (14, {'R': H_CROSS_R, 'L': H_CROSS_L, 'cam': [0, 0, 0], 'fov': -4}),
    (17, dict(HFG), 'inOutSine'),
]))
# 반격: 막이 터지며 팔을 활짝 (t1) → 공격자를 가리켜 움켜쥠 = 발밑에서 창 (t3) → 피를 끌어당김
BURST = HP(body=[-16, 0, 0], head=[-24, 0, 0], right_arm=[-40, 20, 100], left_arm=[-40, -20, -100], root={'pos': [0, 0.2, -0.8], 'rot': [-6, 0, 0]}, right_leg=[6, 10, 10], left_leg=[-6, -10, -10])
H['blood.skill4_success'] = clip(16, blend_in=0.4, blend_out=4, bones=seq([
    (0, VEIL),
    (1, BURST, 'outExpo'),
    (2.2, HP(body=[10, 24, 0], head=[-8, -14, 0], right_arm=[-40, 30, 50], left_arm=[-30, -10, -50], root={'pos': [0, -1, 0]}), 'inQuad'),
    (3, HP(body=[18, -30, 0], head=[-10, 14, 0], right_arm=[-100, -30, 0], left_arm=[-30, 10, -60], root={'pos': [0, -1.4, 1.8]}, right_leg=[-14, 6, 5], left_leg=[18, -6, -4]), 'outExpo'),
    (5, HP(body=[12, -24, 0], head=[-10, 10, 0], right_arm=[-120, -20, 0], left_arm=[-30, 10, -60], root={'pos': [0, -1.2, 1.4]}, right_leg=[-14, 6, 5], left_leg=[18, -6, -4])),
    (7, HP(body=[-4, -10, 0], head=[-16, 6, 0], right_arm=[-70, -10, 10], left_arm=[-80, 20, -10], root={'pos': [0, -0.8, 0]}), 'inOutCubic'),
    (12, HP(body=[-6, -10, 0], head=[-18, 6, 0], right_arm=[-66, -10, 12], left_arm=[-76, 20, -12], root={'pos': [0, -0.8, 0]})),
    (16, HK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': H_CROSS_R, 'L': H_CROSS_L, 'cam': [0, 0, 0], 'fov': -4}),
    (1, {'R': H_OPEN_R, 'L': H_OPEN_L, 'cam': [-4, 0, 0], 'fov': 10}, 'outExpo'),
    (2.2, {'R': ([7, -10, 6], [50, -20, 70]), 'L': HGL, 'cam': [-1, 0, 0], 'fov': 4}, 'inQuad'),
    (3, {'R': ([3, -8, 15], [40, 6, 20]), 'cam': [3, -1, -2], 'fov': -6}, 'outExpo'),
    (5, {'R': ([3, -6, 14], [56, 6, 20]), 'cam': [-1, 0, 0], 'fov': -3}),
    (7, {'R': ([4, -11, 11], [20, 10, 30]), 'L': ([-4, -11, 11], [20, -10, -30]), 'cam': [0, 0, 0], 'fov': 0}, 'inOutCubic'),
    (12, {'R': ([4, -11.4, 11], [22, 10, 30]), 'L': ([-4, -11.4, 11], [22, -10, -30])}),
    (16, dict(HFG), 'inOutSine'),
]))
H['blood.skill4_fail'] = clip(12, blend_in=0.5, blend_out=4, bones=seq([
    (0, VEIL),
    (2.5, HP(body=[4, 10, 0], head=[16, 0, 0], left_arm=[-30, 10, -16], right_arm=[-24, -8, 22], root={'pos': [0, -0.6, -0.6], 'rot': [-2, 0, 0]}), 'outCubic'),
    (5, HP(body=[6, 10, 0], head=[12, 10, 0], left_arm=[-28, 10, -16], right_arm=[-24, -8, 22], root={'pos': [0, -0.6, -0.4]})),
    (7, HP(body=[6, 10, 0], head=[12, -10, 0], left_arm=[-28, 10, -16], right_arm=[-24, -8, 22], root={'pos': [0, -0.6, -0.4]})),
    (12, HK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': H_CROSS_R, 'L': H_CROSS_L, 'cam': [0, 0, 0], 'fov': -4}),
    (2.5, {'R': LOW_R, 'L': LOW_L, 'cam': [2, 0, 0], 'fov': 0}, 'outCubic'),
    (5, {'cam': [1.5, 2, 0]}),
    (7, {'cam': [1.5, -2, 0]}),
    (12, dict(HFG), 'inOutSine'),
]))

# ================================================================ R. 혈월만개 (78틱)
# 0~10 심장을 움켜쥠 / 10 포효 / 12~28 날개로 떠오름 / 16 사슬 / 18~30 끌어당김 / 30~56 공중에서 흡혈 / 58 폭발 / 60 하강
CLUTCH = HP(body=[36, 0, 0], head=[30, 0, 0], right_arm=[-70, 40, 10], left_arm=[-50, -30, -10], root={'pos': [0, -2.6, 0]}, right_leg=[16, 10, 10], left_leg=[-16, -10, -10])
H_ROAR = HP(body=[-26, 0, 0], head=[-44, 0, 0], right_arm=[-30, 20, 100], left_arm=[-30, -20, -100], root={'pos': [0, 0.4, 0], 'rot': [-6, 0, 0]}, right_leg=[6, 10, 12], left_leg=[-6, -10, -12])
HOVER = HP(body=[-8, 0, 0], head=[-14, 0, 0], right_arm=[-40, 30, 70], left_arm=[-40, -30, -70], root={'pos': [0, 0, 0], 'rot': [-4, 0, 0]}, right_leg=[24, 4, 4], left_leg=[-6, -4, -4])
CHAIN = HP(body=[10, 0, 0], head=[-6, 0, 0], right_arm=[-90, -24, 30], left_arm=[-90, 24, -30], root={'pos': [0, 0, 1], 'rot': [2, 0, 0]}, right_leg=[24, 4, 4], left_leg=[-6, -4, -4])
PULLB = HP(body=[-14, 0, 0], head=[-10, 0, 0], right_arm=[-40, 30, 20], left_arm=[-40, -30, -20], root={'pos': [0, 0, -1], 'rot': [-6, 0, 0]}, right_leg=[20, 4, 4], left_leg=[-10, -4, -4])
FEAST = HP(body=[-24, 0, 0], head=[-40, 0, 0], right_arm=[-60, 20, 90], left_arm=[-60, -20, -90], root={'pos': [0, 0, 0], 'rot': [-10, 0, 0]}, right_leg=[26, 4, 4], left_leg=[-4, -4, -4])
GATHER = HP(body=[40, 0, 0], head=[24, 0, 0], right_arm=[-70, 50, 10], left_arm=[-70, -50, -10], root={'pos': [0, -1, 0], 'rot': [10, 0, 0]}, right_leg=[40, 8, 8], left_leg=[30, -8, -8])
NOVA = HP(body=[-30, 0, 0], head=[-40, 0, 0], right_arm=[-20, 30, 120], left_arm=[-20, -30, -120], root={'pos': [0, 0.5, 0], 'rot': [-10, 0, 0]}, right_leg=[10, 10, 12], left_leg=[-10, -10, -12])
LAND = HP(body=[36, 0, 0], head=[6, 0, 0], right_arm=[-30, -20, 40], left_arm=[-30, 20, -40], root={'pos': [0, -3, 0.5], 'rot': [4, 0, 0]}, right_leg=[20, 10, 10], left_leg=[-26, -8, -8])
_ult = [
    (0, HK),
    (5, CLUTCH, 'outCubic'),
    (9, dict(CLUTCH, body=[40, 0, 0], head=[34, 0, 0], root={'pos': [0, -2.9, 0], 'rot': [0, 0, 0]})),
    (11, H_ROAR, 'outExpo'),
    (15, dict(H_ROAR, body=[-20, 0, 0], head=[-36, 0, 0])),
    (16.5, CHAIN, 'outExpo'),
    (20, dict(CHAIN, right_arm=[-94, -20, 30], left_arm=[-94, 20, -30])),
    (24, PULLB, 'inOutCubic'),
    (29, dict(PULLB, body=[-12, 0, 0], right_arm=[-36, 34, 16], left_arm=[-36, -34, -16])),
    (33, FEAST, 'inOutCubic'),
]
for k in range(7):
    t = 36 + k * 3
    _ult.append((t, dict(FEAST, head=[-44, 0, 0], body=[-27, 0, 0]), 'outQuad'))
    _ult.append((t + 1.5, FEAST, 'inOutSine'))
_ult += [
    (57, GATHER, 'inCubic'),
    (58.5, NOVA, 'outExpo'),
    (62, dict(NOVA, body=[-24, 0, 0], head=[-32, 0, 0])),
    (67, LAND, 'outBounce'),
    (72, dict(LAND, body=[30, 0, 0], root={'pos': [0, -2.6, 0.4], 'rot': [3, 0, 0]})),
    (78, HK, 'inOutSine'),
]
_ult_fp = [
    (0, dict(HFG)),
    (5, {'R': H_CLUTCH_R, 'L': LOW_L, 'cam': [8, 0, 0], 'fov': -6}, 'outCubic'),
    (9, {'R': (H_CLUTCH_R[0], [28, 50, 30]), 'cam': [9, 0, 0], 'fov': -7}),
    (11, {'R': H_OPEN_R, 'L': H_OPEN_L, 'cam': [-10, 0, 0], 'fov': 12}, 'outExpo'),
    (15, {'cam': [-8, 0, 0], 'fov': 10}),
    (16.5, {'R': ([4, -9, 15], [20, 10, 24]), 'L': ([-4, -9, 14.5], [20, -10, -24]), 'cam': [0, 0, 0], 'fov': -4}, 'outExpo'),
    (20, {'cam': [1, 0, 0], 'fov': -4}),
    (24, {'R': PULL_R, 'L': PULL_L, 'cam': [-3, 0, 0], 'fov': 6}, 'inOutCubic'),
    (29, {'cam': [-2, 0, 0], 'fov': 5}),
    (33, {'R': H_OPEN_R, 'L': H_OPEN_L, 'cam': [-14, 0, 0], 'fov': 10}, 'inOutCubic'),
]
for k in range(7):
    t = 36 + k * 3
    _ult_fp.append((t, {'cam': [-15, 0, 0], 'fov': 11}, 'outQuad'))
    _ult_fp.append((t + 1.5, {'cam': [-14, 0, 0], 'fov': 10}, 'inOutSine'))
_ult_fp += [
    (57, {'R': ([3, -12, 8], [30, 40, 40]), 'L': ([-3, -12, 7.5], [30, -40, -40]), 'cam': [6, 0, 0], 'fov': -8}, 'inCubic'),
    (58.5, {'R': H_OPEN_R, 'L': H_OPEN_L, 'cam': [-8, 0, 0], 'fov': 16}, 'outExpo'),
    (62, {'cam': [-5, 0, 0], 'fov': 10}),
    (67, {'R': LOW_R, 'L': LOW_L, 'cam': [5, 0, 0], 'fov': -3}, 'outBounce'),
    (78, dict(HFG), 'inOutSine'),
]
H['blood.ult'] = clip(78, blend_in=2, blend_out=5, root_pivot=12, bones=seq(_ult), fp=fpseq(_ult_fp))

# ================================================================ T. 박쥐산개 (0~8 박쥐 떼 = 몸 안 보임, 8 에 나타나며 일어섬)
H['blood.swarm'] = clip(16, blend_in=0.5, blend_out=4, bones=seq([
    (0, LAND),
    (8, LAND),
    (11, HP(body=[20, 0, 0], head=[-14, 0, 0], right_arm=[-20, -10, 40], left_arm=[-20, 10, -40], root={'pos': [0, -1.6, 0], 'rot': [0, 0, 0]}), 'outCubic'),
    (16, HK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': HIDE_R, 'L': HIDE_L, 'cam': [0, 0, 0], 'fov': 18}),
    (8, {'R': HIDE_R, 'L': HIDE_L, 'cam': [3, 0, 0], 'fov': 14}),
    (11, {'R': ([9, -13, 6], [40, -30, 80]), 'L': ([-9, -13, 5.5], [40, 30, -80]), 'cam': [-1, 0, 0], 'fov': 4}, 'outCubic'),
    (16, dict(HFG), 'inOutSine'),
]))

H['blood.select'] = clip(26, blend_in=2, blend_out=5, bones=seq([
    (0, HP(body=[0, 0, 0], head=[0, 0, 0], right_arm=[0, 0, 0], left_arm=[0, 0, 0], right_leg=[0, 0, 0], left_leg=[0, 0, 0], root={'pos': [0, 0, 0]})),
    (6, CLUTCH, 'outCubic'),
    (10, dict(CLUTCH, body=[40, 0, 0], head=[34, 0, 0], root={'pos': [0, -2.9, 0], 'rot': [0, 0, 0]})),
    (13, H_ROAR, 'outExpo'),
    (20, dict(H_ROAR, body=[-20, 0, 0], head=[-34, 0, 0])),
    (26, HK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': LOW_R, 'L': LOW_L, 'cam': [0, 0, 0]}),
    (6, {'R': H_CLUTCH_R, 'L': LOW_L}, 'outCubic'),
    (13, {'R': H_OPEN_R, 'L': H_OPEN_L, 'cam': [-2, 0, 0]}, 'outExpo'),
    (26, dict(HFG), 'inOutSine'),
]))

write('blood', H)


# =====================================================================================
#                              성가사 (cantor) — 버퍼
# =====================================================================================
C = {}
# ---- 1인칭: 오른손 지휘봉 (앞으로), 왼손은 아래 -----------------------------------------
CGR = ([7.5, -13.5, 8], [30, 6, 55])
CGL = ([-7.5, -16, 6], [16, -4, -50])
C_UP_R = ([5, -3, 7], [86, 6, 40])          # 지휘봉을 하늘로
C_HI_R = ([10, -7, 6], [62, -30, 70])        # 오른쪽 위 (지휘 준비)
C_FL_R = ([1, -12, 13], [18, 34, 30])        # 앞으로 휘두름 (음표 발사)
C_LO_R = ([2, -16, 12], [-8, 20, 30])        # 아래로 쓸어내림
C_PT_R = ([3, -9, 15], [26, 4, 30])          # 앞을 가리킴
C_BOOK_L = ([-4, -11, 12], [36, -20, -30])   # 왼손을 앞으로 (책 쪽)
C_OPEN_R = ([14, -9, 5], [48, -50, 88])
C_OPEN_L = ([-14, -9, 4.5], [48, 50, -88])
C_CROSS_R = ([3, -10, 10], [44, 40, 50])


def CP(**kw):
    """성가사 기본 자세: 곧게 선 지휘자. 오른손은 지휘봉을 살짝 들고, 왼손은 편안히."""
    d = {
        'body': [2, 0, 0], 'head': [-4, 0, 0],
        'right_arm': [-34, -8, 10], 'left_arm': [-14, 8, -10],
        'right_leg': [3, 3, 2], 'left_leg': [-3, -3, -2],
        'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]},
    }
    for k, v in kw.items():
        if k == 'root':
            d['root'] = dict(d['root'], **v)
        else:
            d[k] = v
    return d


CK = CP()


def CF(R=CGR, L=CGL, cam=(0, 0, 0), fov=0):
    return {'R': R, 'L': L, 'cam': list(cam), 'fov': fov}


CFG = CF()

# ================================================================ 이동
C['cantor.idle'] = clip(64, loop=True, bones={
    'body': {'rot': cyc(64, [2, 0, 0], [1, 0, 0])},
    'head': {'rot': cyc(64, [-4, 0, 0], [1.5, 4, 0], phase=1.0)},
    # 지휘봉으로 박자를 젓는 듯한 작은 움직임
    'right_arm': {'rot': cyc(64, [-34, -8, 10], [5, 6, 3], phase=0.3, harm=([3, 0, 0], 0.5))},
    'left_arm': {'rot': cyc(64, [-14, 8, -10], [2, 0, -2], phase=0.9)},
    'right_leg': {'rot': {'0': [3, 3, 2]}},
    'left_leg': {'rot': {'0': [-3, -3, -2]}},
    'root': {'pos': cyc(64, [0, 0, 0], [0, -0.25, 0])},
}, fp={
    'right_arm': {'pos': cyc(64, CGR[0], [0.3, 0.5, 0.1]), 'rot': cyc(64, CGR[1], [4, 4, 2], harm=([2, 0, 0], 0.4))},
    'left_arm': {'pos': cyc(64, CGL[0], [0, 0.4, 0.1], phase=0.9), 'rot': cyc(64, CGL[1], [1.5, 0, -1.5], phase=0.9)},
})

C['cantor.walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [3, 0, 0], [0, 5, 0], phase=math.pi)},
    'head': {'rot': {'0': [-4, 0, 0]}},
    'right_arm': {'rot': cyc(20, [-30, -8, 10], [14, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-6, 6, -10], [24, 0, 0])},
    'right_leg': {'rot': cyc(20, [0, 0, 2], [32, 0, 0])},
    'left_leg': {'rot': cyc(20, [0, 0, -2], [32, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, 0, 0], [0, 0, 0], harm=([0, 0.45, 0], -math.pi / 2))},
}, fp={
    'right_arm': {'pos': cyc(20, CGR[0], [0.3, 0.7, 0.9], phase=math.pi), 'rot': cyc(20, CGR[1], [3, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, CGL[0], [0.3, 0.7, 0.9]), 'rot': cyc(20, CGL[1], [3, 0, 0])},
})

C['cantor.run'] = clip(18, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(18, [16, 0, 0], [0, 7, 0], phase=math.pi, harm=([1.5, 0, 0], 0))},
    'head': {'rot': {'0': [-14, 0, 0]}},
    'right_arm': {'rot': cyc(18, [-10, 0, 14], [44, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(18, [-10, 0, -14], [44, 0, 0])},
    'right_leg': {'rot': cyc(18, [-6, 0, 2], [54, 0, 0], harm=([-8, 0, 0], 0))},
    'left_leg': {'rot': cyc(18, [-6, 0, -2], [54, 0, 0], phase=math.pi, harm=([-8, 0, 0], 0))},
    'root': {'pos': cyc(18, [0, 0.3, 0], [0, 0, 0], harm=([0, 1.0, 0], -math.pi / 2)), 'rot': {'0': [4, 0, 0]}},
}, fp={
    'right_arm': {'pos': cyc(18, [8.5, -17, 4.5], [0.4, 1.4, 1.2], phase=math.pi), 'rot': cyc(18, [14, 0, 64], [8, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(18, [-8.5, -17.5, 4], [0.4, 1.4, 1.2]), 'rot': cyc(18, [12, 0, -64], [8, 0, 0])},
    'camera': {'rot': cyc(18, [0, 0, 0], [0, 0, 0.6], harm=([0.5, 0, 0], 0))},
    'fov': {'0': [5, 0, 0]},
})

C['cantor.sneak'] = clip(50, loop=True, bones={
    'body': {'rot': cyc(50, [24, 0, 0], [1.2, 0, 0])},
    'head': {'rot': {'0': [-18, 0, 0]}},
    'right_arm': {'rot': cyc(50, [-40, -8, 12], [3, 0, 0], phase=0.4)},
    'left_arm': {'rot': cyc(50, [-22, 8, -12], [3, 0, 0], phase=0.9)},
    'right_leg': {'rot': {'0': [10, 4, 4]}, 'pos': {'0': [0, 0, -1]}},
    'left_leg': {'rot': {'0': [-16, -4, -4]}, 'pos': {'0': [0, 0, 1]}},
    'root': {'pos': {'0': [0, -1.4, 0]}},
}, fp={
    'right_arm': {'pos': cyc(50, [7, -14.5, 8], [0, 0.3, 0]), 'rot': cyc(50, [30, 6, 55], [1.5, 0, 0])},
    'left_arm': {'pos': cyc(50, [-7, -16.5, 6], [0, 0.3, 0], phase=0.6), 'rot': cyc(50, [14, -4, -50], [1.5, 0, 0], phase=0.6)},
    'fov': {'0': [-3, 0, 0]},
})

C['cantor.sneak_walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [24, 0, 0], [0, 4, 0])},
    'head': {'rot': {'0': [-18, 0, 0]}},
    'right_arm': {'rot': cyc(20, [-40, -8, 12], [8, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-22, 8, -12], [10, 0, 0])},
    'right_leg': {'rot': cyc(20, [4, 4, 4], [24, 0, 0])},
    'left_leg': {'rot': cyc(20, [-4, -4, -4], [24, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, -1.4, 0], [0, 0, 0], harm=([0, 0.3, 0], -math.pi / 2))},
}, fp={
    'right_arm': {'pos': cyc(20, [7, -14.5, 8], [0.2, 0.4, 0.6], phase=math.pi), 'rot': cyc(20, [30, 6, 55], [2, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, [-7, -16.5, 6], [0.2, 0.4, 0.6]), 'rot': cyc(20, [14, -4, -50], [2, 0, 0])},
    'fov': {'0': [-3, 0, 0]},
})

C['cantor.air'] = clip(24, loop=True, bones={
    'body': {'rot': cyc(24, [4, 0, 0], [2, 0, 0])},
    'head': {'rot': {'0': [-8, 0, 0]}},
    'right_arm': {'rot': cyc(24, [-50, 10, 40], [6, 0, 6])},
    'left_arm': {'rot': cyc(24, [-40, -10, -44], [6, 0, -6], phase=0.6)},
    'right_leg': {'rot': {'0': [18, 4, 6]}},
    'left_leg': {'rot': {'0': [-22, -4, -5]}},
}, fp={
    'right_arm': {'pos': cyc(24, [10, -12, 6], [0, 0.6, 0]), 'rot': cyc(24, [44, -14, 70], [3, 0, 0])},
    'left_arm': {'pos': cyc(24, [-10, -14, 5.5], [0, 0.6, 0], phase=0.6), 'rot': cyc(24, [30, 14, -70], [3, 0, 0], phase=0.6)},
})

# 막기: 지휘봉과 팔을 가슴 앞에서 교차
C_BLK = {'body': [8, 0, 0], 'head': [6, 0, 0], 'right_arm': [-100, -40, 6], 'left_arm': [-94, 42, -6], 'right_leg': [8, 4, 4], 'left_leg': [-10, -4, -4], 'root': {'pos': [0, -0.8, 0], 'rot': [0, 0, 0]}}
C['cantor.block'] = clip(40, loop=True, blend_in=2, bones={
    'body': {'rot': cyc(40, [8, 0, 0], [1, 0, 0])},
    'head': {'rot': {'0': [6, 0, 0]}},
    'right_arm': {'rot': cyc(40, [-100, -40, 6], [2, 0, 0])},
    'left_arm': {'rot': cyc(40, [-94, 42, -6], [2, 0, 0], phase=0.5)},
    'right_leg': {'rot': {'0': [8, 4, 4]}},
    'left_leg': {'rot': {'0': [-10, -4, -4]}},
    'root': {'pos': {'0': [0, -0.8, 0]}},
}, fp={
    'right_arm': {'pos': cyc(40, C_CROSS_R[0], [0, 0.2, 0]), 'rot': cyc(40, C_CROSS_R[1], [1, 0, 0])},
    'left_arm': {'pos': cyc(40, ([-3, -10.5, 9.5]), [0, 0.2, 0], phase=0.5), 'rot': cyc(40, [42, -40, -50], [1, 0, 0], phase=0.5)},
    'fov': {'0': [-4, 0, 0]},
})
C['cantor.block_hit'] = clip(7, blend_in=0.5, blend_out=3, bones=seq([
    (0, C_BLK),
    (1.5, dict(C_BLK, body=[-6, 0, 0], right_arm=[-108, -34, 6], left_arm=[-102, 36, -6], root={'pos': [0, -0.8, -1.6], 'rot': [-4, 0, 0]}), 'outExpo'),
    (7, C_BLK, 'outCubic'),
]), fp=fpseq([
    (0, {'R': C_CROSS_R, 'L': ([-3, -10.5, 9.5], [42, -40, -50]), 'cam': [0, 0, 0]}),
    (1.5, {'R': (add(C_CROSS_R[0], [0, 1, -2.5]), add(C_CROSS_R[1], [6, 0, 0])), 'L': ([-3, -9.5, 7], [48, -40, -50]), 'cam': [-2.5, 0, 1.2]}, 'outExpo'),
    (7, {'R': C_CROSS_R, 'L': ([-3, -10.5, 9.5], [42, -40, -50]), 'cam': [0, 0, 0]}, 'outCubic'),
]))


# ================================================================ 평타: 지휘봉으로 음표를 날림 (2.5틱에 발사)
def conduct(length, hit, arm_w, arm_h, body_w, body_h, fp_w, fp_h, cam, fov=-1, lunge=0.6):
    bones = seq([
        (0, CK),
        (hit * 0.5, CP(body=body_w, right_arm=arm_w, left_arm=[-20, 10, -14], root={'pos': [0, 0, -0.3]}), 'outQuad'),
        (hit, CP(body=body_h, head=[-2, 6, 0], right_arm=arm_h, left_arm=[-24, 10, -14], root={'pos': [0, -0.2, lunge]}), 'outExpo'),
        (hit + (length - hit) * 0.5, CP(body=[body_h[0], body_h[1] * 0.8, 0], right_arm=add(arm_h, [4, 0, 0]), root={'pos': [0, -0.1, lunge * 0.6]})),
        (length, CK, 'inOutSine'),
    ])
    fp = fpseq([
        (0, dict(CFG)),
        (hit * 0.5, {'R': fp_w, 'cam': [-0.3, 0.5, 0.4], 'fov': 1}, 'outQuad'),
        (hit, {'R': fp_h, 'cam': list(cam), 'fov': fov}, 'outExpo'),
        (hit + (length - hit) * 0.5, {'R': (fp_h[0], add(fp_h[1], [-3, 0, 0])), 'cam': [x * 0.4 for x in cam], 'fov': fov * 0.4}),
        (length, dict(CFG), 'inOutSine'),
    ])
    return clip(length, bones=bones, fp=fp, blend_in=0.5, blend_out=2.5)


C['cantor.attack1'] = conduct(8, 2.5, [-150, 30, 30], [-70, -30, -4], [0, 18, 0], [6, -18, 0], C_HI_R, C_FL_R, (0.6, -1.0, -1.0))
C['cantor.attack2'] = conduct(8, 2.5, [-40, -40, -10], [-130, 20, 24], [4, -14, 0], [-2, 16, 0], C_LO_R, C_HI_R, (-0.8, 1.0, 1.0))
C['cantor.attack3'] = clip(14, blend_in=0.5, blend_out=3, bones=seq([
    (0, CK),
    (3, CP(body=[-10, 0, 0], head=[-20, 0, 0], right_arm=[-170, -10, 20], left_arm=[-160, 10, -20], root={'pos': [0, 0.6, -0.5], 'rot': [-4, 0, 0]}), 'outCubic'),
    (5, CP(body=[18, 0, 0], head=[0, 0, 0], right_arm=[-70, -20, 4], left_arm=[-70, 20, -4], root={'pos': [0, -0.8, 1.6], 'rot': [4, 0, 0]}, right_leg=[-12, 4, 4], left_leg=[14, -4, -4]), 'outExpo'),
    (9, CP(body=[14, 0, 0], head=[-2, 0, 0], right_arm=[-72, -18, 6], left_arm=[-72, 18, -6], root={'pos': [0, -0.6, 1.2], 'rot': [3, 0, 0]}, right_leg=[-10, 4, 4], left_leg=[12, -4, -4])),
    (14, CK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(CFG)),
    (3, {'R': C_UP_R, 'L': ([-5, -3, 6.5], [86, -6, -40]), 'cam': [-2, 0, 0], 'fov': 3}, 'outCubic'),
    (5, {'R': C_PT_R, 'L': ([-3, -9, 14.5], [26, -4, -30]), 'cam': [3.5, 0, 0], 'fov': -6}, 'outExpo'),
    (9, {'R': (C_PT_R[0], [22, 4, 30]), 'L': ([-3, -9.5, 14], [22, -4, -30]), 'cam': [1.5, 0, 0], 'fov': -3}),
    (14, dict(CFG), 'inOutSine'),
]))

# ================================================================ 1. 용맹의 찬가 (6틱에 파동)
C_RAISE = CP(body=[-12, 0, 0], head=[-28, 0, 0], right_arm=[-176, -10, 14], left_arm=[-90, 30, -20], root={'pos': [0, 0.4, 0], 'rot': [-4, 0, 0]})
C['cantor.skill1'] = clip(14, blend_in=0.8, blend_out=3, bones=seq([
    (0, CK),
    (4, C_RAISE, 'outCubic'),
    (5.2, dict(C_RAISE, right_arm=[-180, -10, 14], root={'pos': [0, 0.6, 0], 'rot': [-5, 0, 0]})),
    (6.2, CP(body=[20, 0, 0], head=[4, 0, 0], right_arm=[-30, -30, 30], left_arm=[-60, 40, -50], root={'pos': [0, -1.2, 1.0], 'rot': [4, 0, 0]}, right_leg=[-14, 6, 6], left_leg=[16, -6, -6]), 'outExpo'),
    (10, CP(body=[16, 0, 0], head=[0, 0, 0], right_arm=[-34, -26, 30], left_arm=[-58, 38, -48], root={'pos': [0, -1.0, 0.8], 'rot': [3, 0, 0]}, right_leg=[-12, 6, 6], left_leg=[14, -6, -6])),
    (14, CK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(CFG)),
    (4, {'R': C_UP_R, 'L': C_BOOK_L, 'cam': [-4, 0, 0], 'fov': 4}, 'outCubic'),
    (5.2, {'R': (add(C_UP_R[0], [0, 0.5, 0]), C_UP_R[1]), 'cam': [-5, 0, 0], 'fov': 5}),
    (6.2, {'R': C_LO_R, 'L': C_OPEN_L, 'cam': [5, 0, 0], 'fov': -8}, 'outExpo'),
    (10, {'R': (C_LO_R[0], add(C_LO_R[1], [4, 0, 0])), 'cam': [2, 0, 0], 'fov': -4}),
    (14, dict(CFG), 'inOutSine'),
]))

# ================================================================ 2. 수호의 성역 (6틱에 지휘봉을 땅에 꽂음)
C_KNEEL = CP(body=[30, 0, 0], head=[10, 0, 0], right_arm=[-40, -10, 6], left_arm=[-70, 30, -10], root={'pos': [0, -4.0, 0.8], 'rot': [2, 0, 0]}, right_leg=[-70, 6, 4], left_leg=[40, -4, -4])
C['cantor.skill2'] = clip(14, blend_in=0.8, blend_out=4, bones=seq([
    (0, CK),
    (3.5, CP(body=[-8, 0, 0], head=[-14, 0, 0], right_arm=[-150, -6, 10], left_arm=[-60, 20, -20], root={'pos': [0, 0.4, 0]}), 'outCubic'),
    (6, C_KNEEL, 'outExpo'),
    (10, dict(C_KNEEL, body=[28, 0, 0], root={'pos': [0, -3.8, 0.7], 'rot': [2, 0, 0]})),
    (14, CK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(CFG)),
    (3.5, {'R': C_UP_R, 'L': C_BOOK_L, 'cam': [-3, 0, 0], 'fov': 3}, 'outCubic'),
    (6, {'R': ([2, -14, 14], [-30, 10, 30]), 'L': ([-4, -12, 11], [20, -20, -30]), 'cam': [12, 0, 0], 'fov': -6}, 'outExpo'),
    (10, {'R': ([2, -14, 14], [-28, 10, 30]), 'cam': [10, 0, 0], 'fov': -4}),
    (14, dict(CFG), 'inOutSine'),
]))

# ================================================================ 3. 천상의 종 (3틱 하늘을 가리킴 → 6틱 대상에게 내림)
C['cantor.skill3'] = clip(12, blend_in=0.6, blend_out=3, bones=seq([
    (0, CK),
    (3, CP(body=[-14, 0, 0], head=[-34, 0, 0], right_arm=[-178, -4, 6], left_arm=[-30, 10, -30], root={'pos': [0, 0.5, 0], 'rot': [-5, 0, 0]}), 'outCubic'),
    (6, CP(body=[14, -10, 0], head=[-4, 6, 0], right_arm=[-86, -6, 4], left_arm=[-30, 16, -30], root={'pos': [0, -0.6, 1.2], 'rot': [3, 0, 0]}, right_leg=[-12, 4, 4], left_leg=[14, -4, -4]), 'outExpo'),
    (9, CP(body=[12, -8, 0], head=[-4, 6, 0], right_arm=[-84, -6, 4], left_arm=[-30, 16, -30], root={'pos': [0, -0.5, 1.0], 'rot': [2, 0, 0]})),
    (12, CK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(CFG)),
    (3, {'R': C_UP_R, 'cam': [-6, 0, 0], 'fov': 4}, 'outCubic'),
    (6, {'R': C_PT_R, 'cam': [3, 0, 0], 'fov': -4}, 'outExpo'),
    (9, {'R': (C_PT_R[0], [24, 4, 30]), 'cam': [1, 0, 0], 'fov': -2}),
    (12, dict(CFG), 'inOutSine'),
]))

# ================================================================ 4. 응답송 (반격 자세 16틱)
C_WARD = CP(body=[-4, 0, 0], head=[2, 0, 0], right_arm=[-110, -36, 10], left_arm=[-86, 30, -6], root={'pos': [0, -0.6, 0]}, right_leg=[8, 6, 6], left_leg=[-10, -6, -6])
C['cantor.skill4'] = clip(17, blend_in=1, blend_out=3, bones=seq([
    (0, CK),
    (2.5, C_WARD, 'outBack'),
    (9, dict(C_WARD, body=[-5, 0, 0], root={'pos': [0, -0.7, 0], 'rot': [0, 0, 0]})),
    (14, C_WARD),
    (17, CK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(CFG)),
    (2.5, {'R': C_CROSS_R, 'L': ([-4, -10.5, 13], [30, -14, -30]), 'cam': [0.5, 0, 0], 'fov': -4}, 'outBack'),
    (9, {'R': (C_CROSS_R[0], [46, 40, 50]), 'fov': -5}),
    (14, {'R': C_CROSS_R, 'cam': [0, 0, 0], 'fov': -4}),
    (17, dict(CFG), 'inOutSine'),
]))
C['cantor.skill4_success'] = clip(14, blend_in=0.4, blend_out=4, bones=seq([
    (0, C_WARD),
    (1.2, CP(body=[-16, 0, 0], head=[-20, 0, 0], right_arm=[-40, 20, 90], left_arm=[-40, -20, -90], root={'pos': [0, 0.2, -0.8], 'rot': [-5, 0, 0]}), 'outExpo'),
    (3, CP(body=[-10, 10, 0], head=[-30, -4, 0], right_arm=[-178, -10, 10], left_arm=[-50, 20, -40], root={'pos': [0, 0.4, 0], 'rot': [-4, 0, 0]}), 'outExpo'),
    (6, CP(body=[-8, 8, 0], head=[-26, -4, 0], right_arm=[-172, -10, 10], left_arm=[-50, 20, -40], root={'pos': [0, 0.3, 0]})),
    (9, CP(body=[4, 0, 0], head=[-4, 0, 0], right_arm=[-60, -20, 10], left_arm=[-60, 20, -10], root={'pos': [0, 0, 0]}), 'inOutCubic'),
    (14, CK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': C_CROSS_R, 'L': ([-4, -10.5, 13], [30, -14, -30]), 'cam': [0, 0, 0], 'fov': -4}),
    (1.2, {'R': C_OPEN_R, 'L': C_OPEN_L, 'cam': [-4, 0, 0], 'fov': 10}, 'outExpo'),
    (3, {'R': C_UP_R, 'L': CGL, 'cam': [-6, 0, 0], 'fov': 4}, 'outExpo'),
    (6, {'R': (C_UP_R[0], [82, 6, 40]), 'cam': [-4, 0, 0], 'fov': 3}),
    (9, {'R': ([4, -11, 11], [20, 10, 30]), 'L': ([-4, -11, 11], [20, -10, -30]), 'cam': [0, 0, 0], 'fov': 0}, 'inOutCubic'),
    (14, dict(CFG), 'inOutSine'),
]))
C['cantor.skill4_fail'] = clip(12, blend_in=0.5, blend_out=4, bones=seq([
    (0, C_WARD),
    (2.5, CP(body=[4, 0, 0], head=[14, 0, 0], left_arm=[-10, 8, -14], right_arm=[-16, -8, 14], root={'pos': [0, -0.3, -0.4], 'rot': [-2, 0, 0]}), 'outCubic'),
    (5, CP(body=[4, 0, 0], head=[10, 10, 0], left_arm=[-10, 8, -14], right_arm=[-16, -8, 14], root={'pos': [0, -0.2, -0.3]})),
    (7, CP(body=[4, 0, 0], head=[10, -10, 0], left_arm=[-10, 8, -14], right_arm=[-16, -8, 14], root={'pos': [0, -0.2, -0.3]})),
    (12, CK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': C_CROSS_R, 'L': ([-4, -10.5, 13], [30, -14, -30]), 'cam': [0, 0, 0], 'fov': -4}),
    (2.5, {'R': LOW_R, 'L': LOW_L, 'cam': [2, 0, 0], 'fov': 0}, 'outCubic'),
    (5, {'cam': [1.5, 2, 0]}),
    (7, {'cam': [1.5, -2, 0]}),
    (12, dict(CFG), 'inOutSine'),
]))

# ================================================================ R. GLORIA (76틱: 12~28 떠오름, 48 합창 폭발, 60 내려옴)
C_PRAY = CP(body=[10, 0, 0], head=[16, 0, 0], right_arm=[-80, 30, 0], left_arm=[-80, -30, 0], root={'pos': [0, -0.6, 0]})
C_SPREAD = CP(body=[-14, 0, 0], head=[-30, 0, 0], right_arm=[-60, 20, 96], left_arm=[-60, -20, -96], root={'pos': [0, 0.2, 0], 'rot': [-5, 0, 0]}, right_leg=[14, 2, 4], left_leg=[-4, -2, -4])
C_CONDUCT = CP(body=[-6, 0, 0], head=[-16, 0, 0], right_arm=[-140, -10, 30], left_arm=[-100, 20, -40], root={'pos': [0, 0, 0], 'rot': [-2, 0, 0]}, right_leg=[16, 2, 4], left_leg=[-6, -2, -4])
C_GATHER = CP(body=[20, 0, 0], head=[14, 0, 0], right_arm=[-80, 40, 10], left_arm=[-80, -40, -10], root={'pos': [0, -0.6, 0], 'rot': [6, 0, 0]}, right_leg=[30, 6, 6], left_leg=[20, -6, -6])
C_GLORIA = CP(body=[-26, 0, 0], head=[-40, 0, 0], right_arm=[-160, 10, 40], left_arm=[-160, -10, -40], root={'pos': [0, 0.6, 0], 'rot': [-8, 0, 0]}, right_leg=[10, 6, 8], left_leg=[-8, -6, -8])
C_LAND = CP(body=[26, 0, 0], head=[4, 0, 0], right_arm=[-30, -10, 30], left_arm=[-30, 10, -30], root={'pos': [0, -2.6, 0.4], 'rot': [3, 0, 0]}, right_leg=[16, 8, 8], left_leg=[-22, -6, -6])
_cu = [
    (0, CK),
    (6, C_PRAY, 'outCubic'),
    (11, dict(C_PRAY, head=[18, 0, 0], root={'pos': [0, -0.7, 0], 'rot': [0, 0, 0]})),
    (16, C_SPREAD, 'outExpo'),
    (26, dict(C_SPREAD, body=[-16, 0, 0], head=[-32, 0, 0])),
]
for k in range(5):
    t0 = 28 + k * 4
    _cu.append((t0, C_CONDUCT if k % 2 == 0 else dict(C_CONDUCT, right_arm=[-110, -30, 10], left_arm=[-120, 30, -30]), 'inOutSine'))
_cu += [
    (46, C_GATHER, 'inCubic'),
    (48.5, C_GLORIA, 'outExpo'),
    (58, dict(C_GLORIA, body=[-22, 0, 0], head=[-34, 0, 0])),
    (66, C_LAND, 'outBounce'),
    (71, dict(C_LAND, body=[20, 0, 0], root={'pos': [0, -2.0, 0.3], 'rot': [2, 0, 0]})),
    (76, CK, 'inOutSine'),
]
_cf = [
    (0, dict(CFG)),
    (6, {'R': ([3, -11, 9], [40, 40, 40]), 'L': ([-3, -11, 8.5], [40, -40, -40]), 'cam': [4, 0, 0], 'fov': -4}, 'outCubic'),
    (16, {'R': C_OPEN_R, 'L': C_OPEN_L, 'cam': [-10, 0, 0], 'fov': 12}, 'outExpo'),
    (26, {'cam': [-9, 0, 0], 'fov': 10}),
]
for k in range(5):
    t0 = 28 + k * 4
    _cf.append((t0, {'R': C_UP_R if k % 2 == 0 else C_HI_R, 'L': C_BOOK_L, 'cam': [-8, 0, 0], 'fov': 8}, 'inOutSine'))
_cf += [
    (46, {'R': ([3, -12, 8], [30, 40, 40]), 'L': ([-3, -12, 7.5], [30, -40, -40]), 'cam': [4, 0, 0], 'fov': -6}, 'inCubic'),
    (48.5, {'R': C_UP_R, 'L': ([-5, -3, 6.5], [86, -6, -40]), 'cam': [-12, 0, 0], 'fov': 16}, 'outExpo'),
    (58, {'cam': [-8, 0, 0], 'fov': 10}),
    (66, {'R': LOW_R, 'L': LOW_L, 'cam': [5, 0, 0], 'fov': -3}, 'outBounce'),
    (76, dict(CFG), 'inOutSine'),
]
C['cantor.ult'] = clip(76, blend_in=2, blend_out=5, root_pivot=12, bones=seq(_cu), fp=fpseq(_cf))

# ================================================================ T. 선율 전환 (지휘봉을 빙그르 돌림)
C['cantor.switch'] = clip(10, blend_in=0.5, blend_out=3, bones=seq([
    (0, CK),
    (2.5, CP(body=[-4, 10, 0], head=[-8, -6, 0], right_arm=[-150, 30, 30], left_arm=[-20, 10, -14], root={'pos': [0, 0.3, 0]}), 'outCubic'),
    (5, CP(body=[4, -10, 0], head=[-4, 6, 0], right_arm=[-90, -40, 10], left_arm=[-24, 10, -14], root={'pos': [0, 0, 0.3]}), 'inOutCubic'),
    (10, CK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(CFG)),
    (2.5, {'R': C_HI_R, 'cam': [-1, 0.6, 0.6], 'fov': 2}, 'outCubic'),
    (5, {'R': C_FL_R, 'cam': [0.6, -0.6, -0.6], 'fov': -1}, 'inOutCubic'),
    (10, dict(CFG), 'inOutSine'),
]))

C['cantor.select'] = clip(26, blend_in=2, blend_out=5, bones=seq([
    (0, CP(body=[0, 0, 0], head=[0, 0, 0], right_arm=[0, 0, 0], left_arm=[0, 0, 0], right_leg=[0, 0, 0], left_leg=[0, 0, 0], root={'pos': [0, 0, 0]})),
    (6, CP(body=[30, 0, 0], head=[20, 0, 0], right_arm=[-60, 40, 0], left_arm=[-20, 0, -20], root={'pos': [0, -0.5, 0]}), 'outCubic'),
    (12, CP(body=[32, 0, 0], head=[22, 0, 0], right_arm=[-62, 40, 0], left_arm=[-20, 0, -20], root={'pos': [0, -0.6, 0]})),
    (16, C_SPREAD, 'outExpo'),
    (26, CK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': LOW_R, 'L': LOW_L, 'cam': [0, 0, 0]}),
    (6, {'R': ([3, -11, 9], [40, 40, 40]), 'L': LOW_L}, 'outCubic'),
    (16, {'R': C_OPEN_R, 'L': C_OPEN_L, 'cam': [-2, 0, 0]}, 'outExpo'),
    (26, dict(CFG), 'inOutSine'),
]))

write('cantor', C)


# =====================================================================================
#                              흑마법사 (warlock)
# =====================================================================================
W = {}
WGR = ([8, -14, 6.5], [26, 2, 56])     # 1인칭: 손바닥을 앞으로 (불꽃)
WGL = ([-8, -14.5, 6], [24, -2, -56])
W_PUSH_R = ([4, -10, 15], [8, 6, 20])  # 앞으로 쭉 (광선/구체)
W_PUSH_L = ([-4, -10, 14.5], [8, -6, -20])
W_UP_R = ([5, -3, 8], [84, 6, 40])
W_UP_L = ([-5, -3, 7.5], [84, -6, -40])
W_GRASP_R = ([3, -8, 16], [30, 6, 10])
W_PULL_R = ([7, -12, 6], [40, -20, 70])
W_DOWN_R = ([3, -17, 12], [-30, 8, 30])


def WP(**kw):
    """흑마법사 기본 자세: 살짝 구부정, 두 손은 앞쪽 아래에서 불꽃을 쥐고 있음."""
    d = {
        'body': [8, 0, 0], 'head': [-6, 0, 0],
        'right_arm': [-28, -6, 14], 'left_arm': [-28, 6, -14],
        'right_leg': [4, 4, 3], 'left_leg': [-4, -4, -3],
        'root': {'pos': [0, -0.4, 0], 'rot': [0, 0, 0]},
    }
    for k, v in kw.items():
        if k == 'root':
            d['root'] = dict(d['root'], **v)
        else:
            d[k] = v
    return d


WK = WP()


def WF(R=WGR, L=WGL, cam=(0, 0, 0), fov=0):
    return {'R': R, 'L': L, 'cam': list(cam), 'fov': fov}


WFG = WF()

W['warlock.idle'] = clip(60, loop=True, bones={
    'body': {'rot': cyc(60, [8, 0, 0], [1.5, 0, 0])},
    'head': {'rot': cyc(60, [-6, 0, 0], [1.5, 5, 0], phase=1.0)},
    'right_arm': {'rot': cyc(60, [-28, -6, 14], [4, 3, 3], phase=0.3)},
    'left_arm': {'rot': cyc(60, [-28, 6, -14], [4, -3, -3], phase=1.2)},
    'right_leg': {'rot': {'0': [4, 4, 3]}},
    'left_leg': {'rot': {'0': [-4, -4, -3]}},
    'root': {'pos': cyc(60, [0, -0.4, 0], [0, -0.3, 0])},
}, fp={
    'right_arm': {'pos': cyc(60, WGR[0], [0.2, 0.5, 0.1]), 'rot': cyc(60, WGR[1], [2, 2, 2])},
    'left_arm': {'pos': cyc(60, WGL[0], [0.2, 0.5, 0.1], phase=1.2), 'rot': cyc(60, WGL[1], [2, -2, -2], phase=1.2)},
})
W['warlock.walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [9, 0, 0], [0, 5, 0], phase=math.pi)},
    'head': {'rot': {'0': [-7, 0, 0]}},
    'right_arm': {'rot': cyc(20, [-24, -6, 14], [16, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-24, 6, -14], [16, 0, 0])},
    'right_leg': {'rot': cyc(20, [0, 0, 2], [32, 0, 0])},
    'left_leg': {'rot': cyc(20, [0, 0, -2], [32, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, -0.3, 0], [0, 0, 0], harm=([0, 0.45, 0], -math.pi / 2))},
}, fp={
    'right_arm': {'pos': cyc(20, WGR[0], [0.3, 0.7, 0.9], phase=math.pi), 'rot': cyc(20, WGR[1], [3, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, WGL[0], [0.3, 0.7, 0.9]), 'rot': cyc(20, WGL[1], [3, 0, 0])},
})
W['warlock.run'] = clip(18, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(18, [22, 0, 0], [0, 7, 0], phase=math.pi, harm=([1.5, 0, 0], 0))},
    'head': {'rot': {'0': [-20, 0, 0]}},
    'right_arm': {'rot': cyc(18, [30, 4, 20], [14, 0, 4], phase=math.pi)},
    'left_arm': {'rot': cyc(18, [30, -4, -20], [14, 0, -4])},
    'right_leg': {'rot': cyc(18, [-6, 0, 2], [54, 0, 0], harm=([-8, 0, 0], 0))},
    'left_leg': {'rot': cyc(18, [-6, 0, -2], [54, 0, 0], phase=math.pi, harm=([-8, 0, 0], 0))},
    'root': {'pos': cyc(18, [0, 0, 0], [0, 0, 0], harm=([0, 1.0, 0], -math.pi / 2)), 'rot': {'0': [5, 0, 0]}},
}, fp={
    'right_arm': {'pos': cyc(18, [9, -19, 3.5], [0.4, 1.4, 1.2], phase=math.pi), 'rot': cyc(18, [6, 0, 64], [8, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(18, [-9, -19, 3], [0.4, 1.4, 1.2]), 'rot': cyc(18, [6, 0, -64], [8, 0, 0])},
    'camera': {'rot': cyc(18, [0, 0, 0], [0, 0, 0.6], harm=([0.5, 0, 0], 0))},
    'fov': {'0': [5, 0, 0]},
})
W['warlock.sneak'] = clip(50, loop=True, bones={
    'body': {'rot': cyc(50, [28, 0, 0], [1.2, 0, 0])},
    'head': {'rot': {'0': [-20, 0, 0]}},
    'right_arm': {'rot': cyc(50, [-36, -6, 16], [3, 0, 0], phase=0.4)},
    'left_arm': {'rot': cyc(50, [-36, 6, -16], [3, 0, 0], phase=0.9)},
    'right_leg': {'rot': {'0': [12, 4, 4]}, 'pos': {'0': [0, 0, -1]}},
    'left_leg': {'rot': {'0': [-18, -4, -4]}, 'pos': {'0': [0, 0, 1]}},
    'root': {'pos': {'0': [0, -1.5, 0]}},
}, fp={
    'right_arm': {'pos': cyc(50, [7.5, -15, 7.5], [0, 0.3, 0]), 'rot': cyc(50, [24, 2, 58], [1.5, 0, 0])},
    'left_arm': {'pos': cyc(50, [-7.5, -15.5, 7], [0, 0.3, 0], phase=0.6), 'rot': cyc(50, [22, -2, -58], [1.5, 0, 0], phase=0.6)},
    'fov': {'0': [-3, 0, 0]},
})
W['warlock.sneak_walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [28, 0, 0], [0, 4, 0])},
    'head': {'rot': {'0': [-20, 0, 0]}},
    'right_arm': {'rot': cyc(20, [-36, -6, 16], [8, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-36, 6, -16], [8, 0, 0])},
    'right_leg': {'rot': cyc(20, [4, 4, 4], [24, 0, 0])},
    'left_leg': {'rot': cyc(20, [-4, -4, -4], [24, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, -1.5, 0], [0, 0, 0], harm=([0, 0.3, 0], -math.pi / 2))},
}, fp={
    'right_arm': {'pos': cyc(20, [7.5, -15, 7.5], [0.2, 0.4, 0.6], phase=math.pi), 'rot': cyc(20, [24, 2, 58], [2, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, [-7.5, -15.5, 7], [0.2, 0.4, 0.6]), 'rot': cyc(20, [22, -2, -58], [2, 0, 0])},
    'fov': {'0': [-3, 0, 0]},
})
W['warlock.air'] = clip(24, loop=True, bones={
    'body': {'rot': cyc(24, [6, 0, 0], [2, 0, 0])},
    'head': {'rot': {'0': [-8, 0, 0]}},
    'right_arm': {'rot': cyc(24, [-44, 10, 44], [6, 0, 6])},
    'left_arm': {'rot': cyc(24, [-44, -10, -44], [6, 0, -6], phase=0.6)},
    'right_leg': {'rot': {'0': [20, 4, 6]}},
    'left_leg': {'rot': {'0': [-24, -4, -5]}},
}, fp={
    'right_arm': {'pos': cyc(24, [10, -12.5, 6], [0, 0.6, 0]), 'rot': cyc(24, [40, -14, 70], [3, 0, 0])},
    'left_arm': {'pos': cyc(24, [-10, -13, 5.5], [0, 0.6, 0], phase=0.6), 'rot': cyc(24, [38, 14, -70], [3, 0, 0], phase=0.6)},
})
W_BLK = {'body': [10, 0, 0], 'head': [6, 0, 0], 'right_arm': [-100, -42, 6], 'left_arm': [-96, 44, -6], 'right_leg': [8, 4, 4], 'left_leg': [-10, -4, -4], 'root': {'pos': [0, -0.8, 0], 'rot': [0, 0, 0]}}
W['warlock.block'] = clip(40, loop=True, blend_in=2, bones={
    'body': {'rot': cyc(40, [10, 0, 0], [1, 0, 0])},
    'head': {'rot': {'0': [6, 0, 0]}},
    'right_arm': {'rot': cyc(40, [-100, -42, 6], [2, 0, 0])},
    'left_arm': {'rot': cyc(40, [-96, 44, -6], [2, 0, 0], phase=0.5)},
    'right_leg': {'rot': {'0': [8, 4, 4]}},
    'left_leg': {'rot': {'0': [-10, -4, -4]}},
    'root': {'pos': {'0': [0, -0.8, 0]}},
}, fp={
    'right_arm': {'pos': cyc(40, ([3, -10, 10]), [0, 0.2, 0]), 'rot': cyc(40, [44, 40, 50], [1, 0, 0])},
    'left_arm': {'pos': cyc(40, ([-3, -10.5, 9.5]), [0, 0.2, 0], phase=0.5), 'rot': cyc(40, [42, -40, -50], [1, 0, 0], phase=0.5)},
    'fov': {'0': [-4, 0, 0]},
})
W['warlock.block_hit'] = clip(7, blend_in=0.5, blend_out=3, bones=seq([
    (0, W_BLK),
    (1.5, dict(W_BLK, body=[-4, 0, 0], right_arm=[-108, -36, 6], left_arm=[-104, 38, -6], root={'pos': [0, -0.8, -1.6], 'rot': [-4, 0, 0]}), 'outExpo'),
    (7, W_BLK, 'outCubic'),
]), fp=fpseq([
    (0, {'R': ([3, -10, 10], [44, 40, 50]), 'L': ([-3, -10.5, 9.5], [42, -40, -50]), 'cam': [0, 0, 0]}),
    (1.5, {'R': ([3, -9, 7.5], [50, 40, 50]), 'L': ([-3, -9.5, 7], [48, -40, -50]), 'cam': [-2.5, 0, 1.2]}, 'outExpo'),
    (7, {'R': ([3, -10, 10], [44, 40, 50]), 'L': ([-3, -10.5, 9.5], [42, -40, -50]), 'cam': [0, 0, 0]}, 'outCubic'),
]))


def wcast(length, hit, arm_w, arm_h, body_w, body_h, fp_w, fp_h, cam, other=None, fov=-1):
    o = other or {}
    bones = seq([
        (0, WK),
        (hit * 0.5, WP(body=body_w, right_arm=arm_w, **o, root={'pos': [0, -0.4, -0.4]}), 'outQuad'),
        (hit, WP(body=body_h, right_arm=arm_h, **o, root={'pos': [0, -0.6, 0.8]}), 'outExpo'),
        (hit + (length - hit) * 0.5, WP(body=[body_h[0], body_h[1] * 0.8, 0], right_arm=add(arm_h, [4, 0, 0]), **o, root={'pos': [0, -0.5, 0.5]})),
        (length, WK, 'inOutSine'),
    ])
    fp = fpseq([
        (0, dict(WFG)),
        (hit * 0.5, {'R': fp_w, 'cam': [-0.3, 0.5, 0.4], 'fov': 1}, 'outQuad'),
        (hit, {'R': fp_h, 'cam': list(cam), 'fov': fov}, 'outExpo'),
        (hit + (length - hit) * 0.5, {'R': (fp_h[0], add(fp_h[1], [-3, 0, 0])), 'cam': [x * 0.4 for x in cam], 'fov': fov * 0.4}),
        (length, dict(WFG), 'inOutSine'),
    ])
    return clip(length, bones=bones, fp=fp, blend_in=0.5, blend_out=2.5)


W['warlock.attack1'] = wcast(8, 2.5, [-60, 30, 40], [-92, -10, 4], [0, 16, 0], [8, -16, 0], ([10, -10, 6], [40, -30, 70]), W_PUSH_R, (0.6, -0.8, -0.8))
W['warlock.attack2'] = wcast(8, 2.5, [-40, -10, 10], [-90, 6, -4], [0, 10, 0], [6, 14, 0], ([2, -16, 8], [-10, 20, 30]), W_PUSH_R, (0.6, 0.8, 0.8),
                             other={'left_arm': [-92, 10, -4]})
W['warlock.attack3'] = clip(14, blend_in=0.5, blend_out=3, bones=seq([
    (0, WK),
    (3, WP(body=[-8, 0, 0], head=[-14, 0, 0], right_arm=[-40, 30, 50], left_arm=[-40, -30, -50], root={'pos': [0, 0.3, -0.6]}), 'outCubic'),
    (5, WP(body=[18, 0, 0], head=[0, 0, 0], right_arm=[-90, -14, 4], left_arm=[-90, 14, -4], root={'pos': [0, -0.8, 1.4], 'rot': [3, 0, 0]}, right_leg=[-12, 4, 4], left_leg=[14, -4, -4]), 'outExpo'),
    (9, WP(body=[14, 0, 0], right_arm=[-88, -14, 6], left_arm=[-88, 14, -6], root={'pos': [0, -0.6, 1.1], 'rot': [2, 0, 0]})),
    (14, WK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(WFG)),
    (3, {'R': ([12, -10, 4], [40, -40, 80]), 'L': ([-12, -10, 3.5], [40, 40, -80]), 'cam': [-2, 0, 0], 'fov': 3}, 'outCubic'),
    (5, {'R': W_PUSH_R, 'L': W_PUSH_L, 'cam': [3.5, 0, 0], 'fov': -6}, 'outExpo'),
    (9, {'R': (W_PUSH_R[0], [6, 6, 20]), 'L': (W_PUSH_L[0], [6, -6, -20]), 'cam': [1.5, 0, 0], 'fov': -3}),
    (14, dict(WFG), 'inOutSine'),
]))

# 1. 해골 소환: 땅을 짚었다가 (5) 손을 들어 올려 끌어냄 (8)
W_KNEEL = WP(body=[34, 0, 0], head=[10, 0, 0], right_arm=[-20, -10, 6], left_arm=[-40, 20, -20], root={'pos': [0, -3.6, 0.8]}, right_leg=[-60, 6, 4], left_leg=[36, -4, -4])
W['warlock.skill1'] = clip(16, blend_in=0.8, blend_out=4, bones=seq([
    (0, WK),
    (4, W_KNEEL, 'outCubic'),
    (6, dict(W_KNEEL, right_arm=[-14, -10, 6])),
    (8.5, WP(body=[-6, 0, 0], head=[-20, 0, 0], right_arm=[-160, -10, 14], left_arm=[-50, 20, -30], root={'pos': [0, 0.2, 0]}), 'outExpo'),
    (12, WP(body=[-4, 0, 0], head=[-16, 0, 0], right_arm=[-150, -10, 14], left_arm=[-48, 20, -30], root={'pos': [0, 0.1, 0]})),
    (16, WK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(WFG)),
    (4, {'R': W_DOWN_R, 'L': ([-6, -14, 9], [10, -10, -40]), 'cam': [12, 0, 0], 'fov': -4}, 'outCubic'),
    (6, {'R': (W_DOWN_R[0], [-34, 8, 30]), 'cam': [13, 0, 0]}),
    (8.5, {'R': W_UP_R, 'cam': [-6, 0, 0], 'fov': 6}, 'outExpo'),
    (12, {'R': (W_UP_R[0], [80, 6, 40]), 'cam': [-4, 0, 0], 'fov': 4}),
    (16, dict(WFG), 'inOutSine'),
]))
# 2. 사령 소환: 두 손을 천천히 들어 (8) 위로 확 (10)
W['warlock.skill2'] = clip(18, blend_in=0.8, blend_out=4, bones=seq([
    (0, WK),
    (8, WP(body=[-4, 0, 0], head=[-10, 0, 0], right_arm=[-80, 30, 30], left_arm=[-80, -30, -30], root={'pos': [0, -0.2, 0]}), 'inOutSine'),
    (10.5, WP(body=[-16, 0, 0], head=[-30, 0, 0], right_arm=[-170, 10, 20], left_arm=[-170, -10, -20], root={'pos': [0, 0.5, 0], 'rot': [-4, 0, 0]}), 'outExpo'),
    (14, WP(body=[-14, 0, 0], head=[-26, 0, 0], right_arm=[-164, 10, 22], left_arm=[-164, -10, -22], root={'pos': [0, 0.4, 0]})),
    (18, WK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(WFG)),
    (8, {'R': ([10, -8, 7], [60, -30, 70]), 'L': ([-10, -8, 6.5], [60, 30, -70]), 'cam': [-2, 0, 0], 'fov': 2}, 'inOutSine'),
    (10.5, {'R': W_UP_R, 'L': W_UP_L, 'cam': [-8, 0, 0], 'fov': 8}, 'outExpo'),
    (14, {'cam': [-6, 0, 0], 'fov': 6}),
    (18, dict(WFG), 'inOutSine'),
]))
# 3. 흑마법 광선: 두 손을 앞으로 모아 14틱 동안 쏨 (떨림)
_bm = [(0, WK), (2, WP(body=[-6, 20, 0], right_arm=[-60, 40, 30], left_arm=[-60, -10, -30], root={'pos': [0, -0.4, -0.8]}), 'outCubic')]
_bf = [(0, dict(WFG)), (2, {'R': ([10, -10, 5], [40, -30, 70]), 'L': ([-6, -12, 7], [30, 10, -60]), 'cam': [-1, 1, 0.5], 'fov': 2}, 'outCubic')]
for k in range(6):
    t = 3 + k * 2
    s = 1 if k % 2 == 0 else -1
    _bm.append((t, WP(body=[14, -4 + s, 0], head=[-2, 0, 0], right_arm=[-92 + s, -12, 2], left_arm=[-90 - s, 14, -2], root={'pos': [0, -0.8, 0.4 + 0.15 * s], 'rot': [2, 0, 0]}, right_leg=[-10, 4, 4], left_leg=[12, -4, -4]),
                'outExpo' if k == 0 else 'inOutSine'))
    _bf.append((t, {'R': (add(W_PUSH_R[0], [0, 0.15 * s, -0.4]), W_PUSH_R[1]), 'L': (add(W_PUSH_L[0], [0, -0.15 * s, -0.4]), W_PUSH_L[1]), 'cam': [1.2 + 0.4 * s, 0.3 * s, 0.4 * s], 'fov': -4},
                'outExpo' if k == 0 else 'inOutSine'))
_bm.append((18, WK, 'inOutSine'))
_bf.append((18, dict(WFG), 'inOutSine'))
W['warlock.skill3'] = clip(18, blend_in=0.6, blend_out=3, bones=seq(_bm), fp=fpseq(_bf))
# 4. 속박: 앞으로 손을 뻗어 움켜쥐고 (2) 아래로 끌어내림 (5)
W['warlock.skill4'] = clip(14, blend_in=0.5, blend_out=4, bones=seq([
    (0, WK),
    (2, WP(body=[4, -14, 0], right_arm=[-100, -14, 0], root={'pos': [0, -0.4, 0.8]}), 'outExpo'),
    (3.5, WP(body=[6, -14, 0], right_arm=[-104, -14, 0], root={'pos': [0, -0.4, 0.8]})),
    (5.5, WP(body=[20, -10, 0], head=[6, 4, 0], right_arm=[-30, -10, 20], root={'pos': [0, -1.2, 0]}, right_leg=[-10, 4, 4], left_leg=[12, -4, -4]), 'outExpo'),
    (9, WP(body=[18, -8, 0], head=[4, 4, 0], right_arm=[-32, -10, 20], root={'pos': [0, -1.1, 0]})),
    (14, WK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(WFG)),
    (2, {'R': W_GRASP_R, 'cam': [-1, -0.5, -0.5], 'fov': -3}, 'outExpo'),
    (3.5, {'R': (W_GRASP_R[0], [34, 6, 10])}),
    (5.5, {'R': W_DOWN_R, 'cam': [6, 0, 0], 'fov': -5}, 'outExpo'),
    (9, {'R': (W_DOWN_R[0], [-28, 8, 30]), 'cam': [3, 0, 0], 'fov': -2}),
    (14, dict(WFG), 'inOutSine'),
]))
# R. 영혼 강탈 (50틱): 손을 뻗음 → 천천히 움켜쥐며 들어 올림 → 26 확 잡아당김
W['warlock.ult'] = clip(50, blend_in=1, blend_out=5, bones=seq([
    (0, WK),
    (6, WP(body=[10, -16, 0], head=[-4, 8, 0], right_arm=[-96, -10, 0], left_arm=[-30, 20, -40], root={'pos': [0, -0.6, 0.8]}), 'outCubic'),
    (14, WP(body=[8, -16, 0], head=[-6, 8, 0], right_arm=[-104, -10, 0], left_arm=[-30, 20, -40], root={'pos': [0, -0.6, 0.8]})),
    (24, WP(body=[0, -14, 0], head=[-14, 8, 0], right_arm=[-140, -10, 0], left_arm=[-34, 20, -40], root={'pos': [0, -0.2, 0.6]}), 'inOutSine'),
    (27, WP(body=[-18, 20, 0], head=[-10, -10, 0], right_arm=[-40, 40, 30], left_arm=[-20, 20, -40], root={'pos': [0, 0, -1.4], 'rot': [-4, 0, 0]}, right_leg=[10, 4, 4], left_leg=[-14, -4, -4]), 'outExpo'),
    (36, WP(body=[-12, 16, 0], head=[-8, -8, 0], right_arm=[-60, 40, 20], left_arm=[-30, 20, -40], root={'pos': [0, -0.1, -1.0], 'rot': [-3, 0, 0]})),
    (50, WK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(WFG)),
    (6, {'R': W_GRASP_R, 'L': ([-9, -16, 5], [10, 0, -60]), 'cam': [0, 0, 0], 'fov': -4}, 'outCubic'),
    (14, {'R': (W_GRASP_R[0], [36, 6, 10]), 'fov': -5}),
    (24, {'R': ([3, -4, 14], [70, 6, 20]), 'cam': [-4, 0, 0], 'fov': -2}, 'inOutSine'),
    (27, {'R': W_PULL_R, 'cam': [3, 2, 2], 'fov': 8}, 'outExpo'),
    (36, {'R': (W_PULL_R[0], [44, -20, 70]), 'cam': [1, 1, 1], 'fov': 4}),
    (50, dict(WFG), 'inOutSine'),
]))
# T. 영혼 수집: 팔을 휘둘러 손바닥으로 끌어들임
W['warlock.collect'] = clip(12, blend_in=0.5, blend_out=3, bones=seq([
    (0, WK),
    (3, WP(body=[0, 20, 0], right_arm=[-80, 50, 40], left_arm=[-60, 10, -30], root={'pos': [0, -0.2, 0]}), 'outCubic'),
    (6, WP(body=[4, -14, 0], right_arm=[-90, -30, -10], left_arm=[-60, 10, -30], root={'pos': [0, -0.4, -0.3]}), 'inOutCubic'),
    (12, WK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(WFG)),
    (3, {'R': ([14, -10, 5], [40, -50, 80]), 'cam': [-0.5, 1, 0.6], 'fov': 3}, 'outCubic'),
    (6, {'R': ([1, -11, 11], [30, 30, 20]), 'cam': [0.5, -0.6, -0.4], 'fov': -2}, 'inOutCubic'),
    (12, dict(WFG), 'inOutSine'),
]))
W['warlock.select'] = clip(26, blend_in=2, blend_out=5, bones=seq([
    (0, WP(body=[0, 0, 0], head=[0, 0, 0], right_arm=[0, 0, 0], left_arm=[0, 0, 0], right_leg=[0, 0, 0], left_leg=[0, 0, 0], root={'pos': [0, 0, 0]})),
    (8, WP(body=[20, 0, 0], head=[20, 0, 0], right_arm=[-40, 30, 30], left_arm=[-40, -30, -30], root={'pos': [0, -0.8, 0]}), 'outCubic'),
    (14, WP(body=[-14, 0, 0], head=[-26, 0, 0], right_arm=[-120, 20, 50], left_arm=[-120, -20, -50], root={'pos': [0, 0.3, 0]}), 'outExpo'),
    (26, WK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': LOW_R, 'L': LOW_L, 'cam': [0, 0, 0]}),
    (8, {'R': WGR, 'L': WGL}, 'outCubic'),
    (14, {'R': W_UP_R, 'L': W_UP_L, 'cam': [-2, 0, 0]}, 'outExpo'),
    (26, dict(WFG), 'inOutSine'),
]))

write('warlock', W)


# =====================================================================================
#                              천사 (angel) — 힐러
# =====================================================================================
A = {}
AGR = ([7, -13, 8.5], [28, 4, 52])            # 1인칭: 오른손 손바닥 위로 빛 구슬
AGL = ([-7.5, -16, 6], [16, -4, -50])
A_PUSH_R = ([3, -10, 15], [10, 6, 24])        # 앞으로 내밂 (구슬 발사)
A_PUSH_L = ([-3, -10, 14.5], [10, -6, -24])
A_UP_R = ([6, -3, 7.5], [84, 8, 40])          # 하늘로
A_UP_L = ([-6, -3, 7], [84, -8, -40])
A_DOWN_R = ([12, -14, 6], [-6, -40, 80])      # 아래 바깥으로 쓸어냄 (손바닥 아래)
A_DOWN_L = ([-12, -14, 5.5], [-6, 40, -80])
A_PRAY_R = ([2, -11, 9], [40, 40, 30])        # 가슴 앞에 모음
A_PRAY_L = ([-2, -11, 8.5], [40, -40, -30])


def AP(**kw):
    """천사 기본 자세: 곧고 가볍게 선 자세, 오른손은 손바닥 위로 빛 구슬을 받침."""
    d = {
        'body': [0, 0, 0], 'head': [-4, 0, 0],
        'right_arm': [-34, -12, 8], 'left_arm': [-8, 6, -10],
        'right_leg': [2, 2, 1], 'left_leg': [-2, -2, -1],
        'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]},
    }
    for k, v in kw.items():
        if k == 'root':
            d['root'] = dict(d['root'], **v)
        else:
            d[k] = v
    return d


AK = AP()


def AF(R=AGR, L=AGL, cam=(0, 0, 0), fov=0):
    return {'R': R, 'L': L, 'cam': list(cam), 'fov': fov}


AFG = AF()

# ================================================================ 이동
A['angel.idle'] = clip(64, loop=True, bones={
    'body': {'rot': cyc(64, [0, 0, 0], [1, 0, 0])},
    'head': {'rot': cyc(64, [-4, 0, 0], [1.5, 4, 0], phase=1.0)},
    'right_arm': {'rot': cyc(64, [-34, -12, 8], [3, 2, 2], phase=0.3)},
    'left_arm': {'rot': cyc(64, [-8, 6, -10], [2, 0, -2], phase=0.9)},
    'right_leg': {'rot': {'0': [2, 2, 1]}},
    'left_leg': {'rot': {'0': [-2, -2, -1]}},
    'root': {'pos': cyc(64, [0, 0, 0], [0, -0.25, 0])},
}, fp={
    'right_arm': {'pos': cyc(64, AGR[0], [0.2, 0.5, 0.1]), 'rot': cyc(64, AGR[1], [2, 2, 2])},
    'left_arm': {'pos': cyc(64, AGL[0], [0, 0.4, 0.1], phase=0.9), 'rot': cyc(64, AGL[1], [1.5, 0, -1.5], phase=0.9)},
})
A['angel.walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [1, 0, 0], [0, 4, 0], phase=math.pi)},
    'head': {'rot': {'0': [-4, 0, 0]}},
    'right_arm': {'rot': cyc(20, [-32, -12, 8], [10, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-4, 6, -10], [24, 0, 0])},
    'right_leg': {'rot': cyc(20, [0, 0, 1], [30, 0, 0])},
    'left_leg': {'rot': cyc(20, [0, 0, -1], [30, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, 0, 0], [0, 0, 0], harm=([0, 0.4, 0], -math.pi / 2))},
}, fp={
    'right_arm': {'pos': cyc(20, AGR[0], [0.3, 0.6, 0.8], phase=math.pi), 'rot': cyc(20, AGR[1], [3, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, AGL[0], [0.3, 0.7, 0.9]), 'rot': cyc(20, AGL[1], [3, 0, 0])},
})
A['angel.run'] = clip(18, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(18, [14, 0, 0], [0, 7, 0], phase=math.pi, harm=([1.5, 0, 0], 0))},
    'head': {'rot': {'0': [-12, 0, 0]}},
    'right_arm': {'rot': cyc(18, [-8, 0, 14], [42, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(18, [-8, 0, -14], [42, 0, 0])},
    'right_leg': {'rot': cyc(18, [-6, 0, 2], [52, 0, 0], harm=([-8, 0, 0], 0))},
    'left_leg': {'rot': cyc(18, [-6, 0, -2], [52, 0, 0], phase=math.pi, harm=([-8, 0, 0], 0))},
    'root': {'pos': cyc(18, [0, 0.3, 0], [0, 0, 0], harm=([0, 1.0, 0], -math.pi / 2)), 'rot': {'0': [4, 0, 0]}},
}, fp={
    'right_arm': {'pos': cyc(18, [8.5, -17, 4.5], [0.4, 1.4, 1.2], phase=math.pi), 'rot': cyc(18, [14, 0, 64], [8, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(18, [-8.5, -17.5, 4], [0.4, 1.4, 1.2]), 'rot': cyc(18, [12, 0, -64], [8, 0, 0])},
    'camera': {'rot': cyc(18, [0, 0, 0], [0, 0, 0.6], harm=([0.5, 0, 0], 0))},
    'fov': {'0': [5, 0, 0]},
})
A['angel.sneak'] = clip(50, loop=True, bones={
    'body': {'rot': cyc(50, [24, 0, 0], [1.2, 0, 0])},
    'head': {'rot': {'0': [-18, 0, 0]}},
    'right_arm': {'rot': cyc(50, [-40, -10, 10], [3, 0, 0], phase=0.4)},
    'left_arm': {'rot': cyc(50, [-20, 8, -12], [3, 0, 0], phase=0.9)},
    'right_leg': {'rot': {'0': [10, 4, 4]}, 'pos': {'0': [0, 0, -1]}},
    'left_leg': {'rot': {'0': [-16, -4, -4]}, 'pos': {'0': [0, 0, 1]}},
    'root': {'pos': {'0': [0, -1.4, 0]}},
}, fp={
    'right_arm': {'pos': cyc(50, [7, -14, 8.5], [0, 0.3, 0]), 'rot': cyc(50, [28, 4, 52], [1.5, 0, 0])},
    'left_arm': {'pos': cyc(50, [-7, -16.5, 6], [0, 0.3, 0], phase=0.6), 'rot': cyc(50, [14, -4, -50], [1.5, 0, 0], phase=0.6)},
    'fov': {'0': [-3, 0, 0]},
})
A['angel.sneak_walk'] = clip(20, loop=True, stride=9.42, bones={
    'body': {'rot': cyc(20, [24, 0, 0], [0, 4, 0])},
    'head': {'rot': {'0': [-18, 0, 0]}},
    'right_arm': {'rot': cyc(20, [-40, -10, 10], [8, 0, 0], phase=math.pi)},
    'left_arm': {'rot': cyc(20, [-20, 8, -12], [10, 0, 0])},
    'right_leg': {'rot': cyc(20, [4, 4, 4], [24, 0, 0])},
    'left_leg': {'rot': cyc(20, [-4, -4, -4], [24, 0, 0], phase=math.pi)},
    'root': {'pos': cyc(20, [0, -1.4, 0], [0, 0, 0], harm=([0, 0.3, 0], -math.pi / 2))},
}, fp={
    'right_arm': {'pos': cyc(20, [7, -14, 8.5], [0.2, 0.4, 0.6], phase=math.pi), 'rot': cyc(20, [28, 4, 52], [2, 0, 0], phase=math.pi)},
    'left_arm': {'pos': cyc(20, [-7, -16.5, 6], [0.2, 0.4, 0.6]), 'rot': cyc(20, [14, -4, -50], [2, 0, 0])},
    'fov': {'0': [-3, 0, 0]},
})
# 걷기 모드에서 떨어질 때
A['angel.air'] = clip(24, loop=True, bones={
    'body': {'rot': cyc(24, [4, 0, 0], [2, 0, 0])},
    'head': {'rot': {'0': [-8, 0, 0]}},
    'right_arm': {'rot': cyc(24, [-46, 10, 40], [6, 0, 6])},
    'left_arm': {'rot': cyc(24, [-40, -10, -44], [6, 0, -6], phase=0.6)},
    'right_leg': {'rot': {'0': [16, 4, 6]}},
    'left_leg': {'rot': {'0': [-20, -4, -5]}},
}, fp={
    'right_arm': {'pos': cyc(24, [10, -12, 6], [0, 0.6, 0]), 'rot': cyc(24, [44, -14, 70], [3, 0, 0])},
    'left_arm': {'pos': cyc(24, [-10, -14, 5.5], [0, 0.6, 0], phase=0.6), 'rot': cyc(24, [30, 14, -70], [3, 0, 0], phase=0.6)},
})
# 비행 모드: 제자리에 떠 있음 (날갯짓에 맞춰 위아래로), 다리는 모아서 늘어뜨림
A['angel.fly'] = clip(10, loop=True, blend_in=3, bones={
    'body': {'rot': cyc(10, [4, 0, 0], [2, 0, 0], n=6)},
    'head': {'rot': {'0': [-6, 0, 0]}},
    'right_arm': {'rot': cyc(10, [-24, -4, 26], [4, 0, 6], phase=0.5, n=6)},
    'left_arm': {'rot': cyc(10, [-14, 4, -28], [4, 0, -6], phase=0.5, n=6)},
    'right_leg': {'rot': {'0': [12, 2, 3]}},
    'left_leg': {'rot': {'0': [20, -2, -2]}},
    'root': {'pos': cyc(10, [0, 0.4, 0], [0, 0.9, 0], phase=math.pi, n=6)},
}, fp={
    'right_arm': {'pos': cyc(10, [8, -14, 7], [0, 0.5, 0], phase=0.5, n=6), 'rot': cyc(10, [30, -8, 60], [2, 0, 4], phase=0.5, n=6)},
    'left_arm': {'pos': cyc(10, [-9, -16, 5], [0, 0.5, 0], phase=0.5, n=6), 'rot': cyc(10, [16, 8, -62], [2, 0, -4], phase=0.5, n=6)},
    'camera': {'rot': cyc(10, [0, 0, 0], [0.35, 0, 0], phase=math.pi, n=6)},
})
# 비행 중 이동: 몸을 앞으로 눕혀 미끄러지듯 (팔은 뒤로)
A['angel.fly_move'] = clip(20, loop=True, bones={
    'body': {'rot': {'0': [0, 0, 0]}},
    'head': {'rot': {'0': [-26, 0, 0]}},
    'right_arm': {'rot': cyc(20, [16, 0, 16], [4, 0, 2])},
    'left_arm': {'rot': cyc(20, [16, 0, -16], [4, 0, -2], phase=0.4)},
    'right_leg': {'rot': cyc(20, [8, 0, 2], [3, 0, 0], phase=1.0)},
    'left_leg': {'rot': cyc(20, [14, 0, -2], [3, 0, 0], phase=2.0)},
    'root': {'pos': {'0': [0, 3, 0]}, 'rot': cyc(20, [28, 0, 0], [2, 0, 0])},
}, fp={
    'right_arm': {'pos': cyc(20, [9.5, -19, 3], [0, 0.4, 0]), 'rot': cyc(20, [-4, 0, 64], [2, 0, 0])},
    'left_arm': {'pos': cyc(20, [-9.5, -19, 3], [0, 0.4, 0], phase=0.4), 'rot': cyc(20, [-4, 0, -64], [2, 0, 0], phase=0.4)},
    'fov': {'0': [6, 0, 0]},
})
# 막기: 두 손을 가슴 앞에 모아 빛을 세움
A_BLK = {'body': [6, 0, 0], 'head': [6, 0, 0], 'right_arm': [-100, -38, 6], 'left_arm': [-96, 40, -6], 'right_leg': [8, 4, 4], 'left_leg': [-10, -4, -4], 'root': {'pos': [0, -0.8, 0], 'rot': [0, 0, 0]}}
A['angel.block'] = clip(40, loop=True, blend_in=2, bones={
    'body': {'rot': cyc(40, [6, 0, 0], [1, 0, 0])},
    'head': {'rot': {'0': [6, 0, 0]}},
    'right_arm': {'rot': cyc(40, [-100, -38, 6], [2, 0, 0])},
    'left_arm': {'rot': cyc(40, [-96, 40, -6], [2, 0, 0], phase=0.5)},
    'right_leg': {'rot': {'0': [8, 4, 4]}},
    'left_leg': {'rot': {'0': [-10, -4, -4]}},
    'root': {'pos': {'0': [0, -0.8, 0]}},
}, fp={
    'right_arm': {'pos': cyc(40, ([3, -10, 10]), [0, 0.2, 0]), 'rot': cyc(40, [44, 40, 50], [1, 0, 0])},
    'left_arm': {'pos': cyc(40, ([-3, -10.5, 9.5]), [0, 0.2, 0], phase=0.5), 'rot': cyc(40, [42, -40, -50], [1, 0, 0], phase=0.5)},
    'fov': {'0': [-4, 0, 0]},
})
A['angel.block_hit'] = clip(7, blend_in=0.5, blend_out=3, bones=seq([
    (0, A_BLK),
    (1.5, dict(A_BLK, body=[-6, 0, 0], right_arm=[-108, -32, 6], left_arm=[-104, 34, -6], root={'pos': [0, -0.8, -1.6], 'rot': [-4, 0, 0]}), 'outExpo'),
    (7, A_BLK, 'outCubic'),
]), fp=fpseq([
    (0, {'R': ([3, -10, 10], [44, 40, 50]), 'L': ([-3, -10.5, 9.5], [42, -40, -50]), 'cam': [0, 0, 0]}),
    (1.5, {'R': ([3, -9, 7.5], [50, 40, 50]), 'L': ([-3, -9.5, 7], [48, -40, -50]), 'cam': [-2.5, 0, 1.2]}, 'outExpo'),
    (7, {'R': ([3, -10, 10], [44, 40, 50]), 'L': ([-3, -10.5, 9.5], [42, -40, -50]), 'cam': [0, 0, 0]}, 'outCubic'),
]))


# ================================================================ 평타: 빛 구슬을 띄워 보냄 (2.5틱)
def acast(length, hit, arm_w, arm_h, body_w, body_h, fp_w, fp_h, cam, other=None, fov=-1):
    o = other or {}
    bones = seq([
        (0, AK),
        (hit * 0.5, AP(body=body_w, right_arm=arm_w, **o, root={'pos': [0, 0, -0.3]}), 'outQuad'),
        (hit, AP(body=body_h, right_arm=arm_h, **o, root={'pos': [0, -0.2, 0.6]}), 'outExpo'),
        (hit + (length - hit) * 0.5, AP(body=[body_h[0], body_h[1] * 0.8, 0], right_arm=add(arm_h, [4, 0, 0]), **o, root={'pos': [0, -0.1, 0.4]})),
        (length, AK, 'inOutSine'),
    ])
    fp = fpseq([
        (0, dict(AFG)),
        (hit * 0.5, {'R': fp_w, 'cam': [-0.3, 0.4, 0.3], 'fov': 1}, 'outQuad'),
        (hit, {'R': fp_h, 'cam': list(cam), 'fov': fov}, 'outExpo'),
        (hit + (length - hit) * 0.5, {'R': (fp_h[0], add(fp_h[1], [-3, 0, 0])), 'cam': [x * 0.4 for x in cam], 'fov': fov * 0.4}),
        (length, dict(AFG), 'inOutSine'),
    ])
    return clip(length, bones=bones, fp=fp, blend_in=0.5, blend_out=2.5)


A['angel.attack1'] = acast(8, 2.5, [-50, 30, 30], [-96, -6, 4], [0, 14, 0], [4, -12, 0], ([10, -11, 6], [36, -30, 66]), A_PUSH_R, (0.4, -0.6, -0.6))
A['angel.attack2'] = acast(8, 2.5, [-30, -10, 10], [-30, -12, 8], [0, -10, 0], [4, 14, 0], ([2, -15, 8], [24, 4, 52]), ([6, -14, 10], [24, 4, 52]), (0.4, 0.6, 0.6),
                           other={'left_arm': [-94, 8, -4]})
A['angel.attack3'] = clip(12, blend_in=0.5, blend_out=3, bones=seq([
    (0, AK),
    (2.5, AP(body=[-6, 0, 0], head=[-10, 0, 0], right_arm=[-40, 30, 40], left_arm=[-40, -30, -40], root={'pos': [0, 0.3, -0.5]}), 'outCubic'),
    (4, AP(body=[10, 0, 0], head=[0, 0, 0], right_arm=[-92, -12, 4], left_arm=[-92, 12, -4], root={'pos': [0, -0.4, 1.0], 'rot': [2, 0, 0]}), 'outExpo'),
    (8, AP(body=[8, 0, 0], right_arm=[-88, -12, 6], left_arm=[-88, 12, -6], root={'pos': [0, -0.3, 0.8]})),
    (12, AK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(AFG)),
    (2.5, {'R': ([12, -10, 4], [40, -40, 80]), 'L': ([-12, -10, 3.5], [40, 40, -80]), 'cam': [-1.5, 0, 0], 'fov': 3}, 'outCubic'),
    (4, {'R': A_PUSH_R, 'L': A_PUSH_L, 'cam': [2.5, 0, 0], 'fov': -5}, 'outExpo'),
    (8, {'R': (A_PUSH_R[0], [8, 6, 24]), 'L': (A_PUSH_L[0], [8, -6, -24]), 'cam': [1, 0, 0], 'fov': -2}),
    (12, dict(AFG), 'inOutSine'),
]))

# 1. 치유의 빛: 가슴 앞에 모았다가 (3) 두 손을 아래 바깥으로 활짝 (5)
A['angel.skill1'] = clip(14, blend_in=0.6, blend_out=4, bones=seq([
    (0, AK),
    (3, AP(body=[-4, 0, 0], head=[-8, 0, 0], right_arm=[-70, 40, 20], left_arm=[-70, -40, -20], root={'pos': [0, 0.4, 0]}), 'outCubic'),
    (5.5, AP(body=[10, 0, 0], head=[10, 0, 0], right_arm=[-36, -10, 60], left_arm=[-36, 10, -60], root={'pos': [0, -1.0, 0]}, right_leg=[-8, 4, 4], left_leg=[10, -4, -4]), 'outExpo'),
    (9, AP(body=[8, 0, 0], head=[8, 0, 0], right_arm=[-34, -10, 56], left_arm=[-34, 10, -56], root={'pos': [0, -0.8, 0]})),
    (14, AK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(AFG)),
    (3, {'R': A_PRAY_R, 'L': A_PRAY_L, 'cam': [-2, 0, 0], 'fov': 2}, 'outCubic'),
    (5.5, {'R': A_DOWN_R, 'L': A_DOWN_L, 'cam': [5, 0, 0], 'fov': -4}, 'outExpo'),
    (9, {'R': (A_DOWN_R[0], [-4, -40, 80]), 'L': (A_DOWN_L[0], [-4, 40, -80]), 'cam': [3, 0, 0], 'fov': -2}),
    (14, dict(AFG), 'inOutSine'),
]))
# 2. 천사의 가호: 양팔을 천천히 벌려 (4) 하늘로 활짝 (6)
A['angel.skill2'] = clip(16, blend_in=0.6, blend_out=4, bones=seq([
    (0, AK),
    (4, AP(body=[2, 0, 0], head=[-6, 0, 0], right_arm=[-60, 20, 50], left_arm=[-60, -20, -50], root={'pos': [0, -0.4, 0]}), 'inOutSine'),
    (6.5, AP(body=[-14, 0, 0], head=[-30, 0, 0], right_arm=[-168, 10, 30], left_arm=[-168, -10, -30], root={'pos': [0, 0.6, 0], 'rot': [-3, 0, 0]}), 'outExpo'),
    (11, AP(body=[-12, 0, 0], head=[-26, 0, 0], right_arm=[-162, 10, 32], left_arm=[-162, -10, -32], root={'pos': [0, 0.5, 0]})),
    (16, AK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(AFG)),
    (4, {'R': ([12, -10, 6], [50, -30, 76]), 'L': ([-12, -10, 5.5], [50, 30, -76]), 'cam': [-1, 0, 0], 'fov': 2}, 'inOutSine'),
    (6.5, {'R': A_UP_R, 'L': A_UP_L, 'cam': [-7, 0, 0], 'fov': 7}, 'outExpo'),
    (11, {'cam': [-5, 0, 0], 'fov': 5}),
    (16, dict(AFG), 'inOutSine'),
]))
# 3. 축복: 오른손으로 대상을 가리키고 (3) 왼손을 하늘로, 7 에 오른손을 내려 빛을 떨어뜨림
A['angel.skill3'] = clip(16, blend_in=0.6, blend_out=4, bones=seq([
    (0, AK),
    (3, AP(body=[-2, -10, 0], head=[-8, 4, 0], right_arm=[-110, -10, 4], left_arm=[-170, -10, -16], root={'pos': [0, 0.2, 0.4]}), 'outCubic'),
    (6, AP(body=[-4, -12, 0], head=[-10, 4, 0], right_arm=[-118, -10, 4], left_arm=[-174, -10, -16], root={'pos': [0, 0.3, 0.4]})),
    (7.5, AP(body=[8, -8, 0], head=[2, 4, 0], right_arm=[-70, -10, 6], left_arm=[-150, -10, -20], root={'pos': [0, -0.4, 0.6]}), 'outExpo'),
    (11, AP(body=[6, -6, 0], head=[0, 4, 0], right_arm=[-72, -10, 6], left_arm=[-140, -10, -20], root={'pos': [0, -0.3, 0.5]})),
    (16, AK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(AFG)),
    (3, {'R': ([3, -7, 15], [34, 6, 24]), 'L': A_UP_L, 'cam': [-2, 0, 0], 'fov': 2}, 'outCubic'),
    (6, {'R': ([3, -6.5, 15], [38, 6, 24]), 'cam': [-2.5, 0, 0]}),
    (7.5, {'R': ([3, -11, 14], [4, 6, 24]), 'L': ([-6, -6, 8], [70, -8, -40]), 'cam': [3, 0, 0], 'fov': -3}, 'outExpo'),
    (11, {'cam': [1.5, 0, 0], 'fov': -1}),
    (16, dict(AFG), 'inOutSine'),
]))
# 4. 대치유: 두 손을 앞으로 모아 빛을 흘려보내고 (2~9, 떨림) 9 에 활짝
_hm = [(0, AK), (2, AP(body=[4, 0, 0], right_arm=[-92, -16, 4], left_arm=[-92, 16, -4], root={'pos': [0, -0.3, 0.4]}), 'outCubic')]
_hf = [(0, dict(AFG)), (2, {'R': A_PUSH_R, 'L': A_PUSH_L, 'cam': [-0.5, 0, 0], 'fov': -2}, 'outCubic')]
for k in range(3):
    tk = 3.5 + k * 2
    sg = 1 if k % 2 == 0 else -1
    _hm.append((tk, AP(body=[6, sg, 0], right_arm=[-94 + sg, -16, 4], left_arm=[-94 - sg, 16, -4], root={'pos': [0, -0.4, 0.5]})))
    _hf.append((tk, {'R': (add(A_PUSH_R[0], [0, 0.15 * sg, -0.3]), A_PUSH_R[1]), 'L': (add(A_PUSH_L[0], [0, -0.15 * sg, -0.3]), A_PUSH_L[1]), 'cam': [0.4 * sg, 0.2 * sg, 0], 'fov': -3}))
_hm += [
    (9.5, AP(body=[-8, 0, 0], head=[-12, 0, 0], right_arm=[-120, 20, 40], left_arm=[-120, -20, -40], root={'pos': [0, 0.4, 0]}), 'outExpo'),
    (13, AP(body=[-6, 0, 0], head=[-10, 0, 0], right_arm=[-116, 20, 38], left_arm=[-116, -20, -38], root={'pos': [0, 0.3, 0]})),
    (18, AK, 'inOutSine'),
]
_hf += [
    (9.5, {'R': ([12, -6, 7], [64, -30, 70]), 'L': ([-12, -6, 6.5], [64, 30, -70]), 'cam': [-3, 0, 0], 'fov': 6}, 'outExpo'),
    (13, {'cam': [-2, 0, 0], 'fov': 4}),
    (18, dict(AFG), 'inOutSine'),
]
A['angel.skill4'] = clip(18, blend_in=0.6, blend_out=4, bones=seq(_hm), fp=fpseq(_hf))

# R. 천상의 심판 (70틱): 기도 (0~8) → 날개를 펴며 떠오름 (8~22) → 하늘로 두 팔 (22~30) → 빛줄기가 쏟아지는 동안 유지 → 내려옴
_um = [
    (0, AK),
    (6, AP(body=[10, 0, 0], head=[24, 0, 0], right_arm=[-66, 44, 20], left_arm=[-66, -44, -20], root={'pos': [0, -0.4, 0]}), 'outCubic'),
    (10, AP(body=[10, 0, 0], head=[26, 0, 0], right_arm=[-68, 46, 20], left_arm=[-68, -46, -20], root={'pos': [0, -0.5, 0]})),
    (16, AP(body=[-6, 0, 0], head=[-16, 0, 0], right_arm=[-40, -10, 80], left_arm=[-40, 10, -80], right_leg=[10, 2, 3], left_leg=[18, -2, -2], root={'pos': [0, 0.6, 0]}), 'outCubic'),
    (24, AP(body=[-14, 0, 0], head=[-34, 0, 0], right_arm=[-160, 10, 34], left_arm=[-160, -10, -34], right_leg=[12, 2, 3], left_leg=[20, -2, -2], root={'pos': [0, 0.8, 0], 'rot': [-4, 0, 0]}), 'inOutCubic'),
]
for k in range(5):
    tk = 30 + k * 6.5
    _um.append((tk, AP(body=[-16, 0, 0], head=[-36, 0, 0], right_arm=[-166, 10, 30], left_arm=[-166, -10, -30], right_leg=[12, 2, 3], left_leg=[20, -2, -2], root={'pos': [0, 1.0, 0], 'rot': [-5, 0, 0]}), 'outExpo'))
    _um.append((tk + 3, AP(body=[-12, 0, 0], head=[-32, 0, 0], right_arm=[-158, 10, 36], left_arm=[-158, -10, -36], right_leg=[12, 2, 3], left_leg=[20, -2, -2], root={'pos': [0, 0.7, 0], 'rot': [-3, 0, 0]})))
_um += [
    (63, AP(body=[-10, 0, 0], head=[-26, 0, 0], right_arm=[-150, 10, 40], left_arm=[-150, -10, -40], root={'pos': [0, 0.6, 0]})),
    (70, AK, 'inOutSine'),
]
A['angel.ult'] = clip(70, blend_in=1, blend_out=5, bones=seq(_um), fp=fpseq([
    (0, dict(AFG)),
    (6, {'R': A_PRAY_R, 'L': A_PRAY_L, 'cam': [6, 0, 0], 'fov': -3}, 'outCubic'),
    (16, {'R': ([13, -10, 4], [40, -50, 86]), 'L': ([-13, -10, 3.5], [40, 50, -86]), 'cam': [-6, 0, 0], 'fov': 6}, 'outCubic'),
    (24, {'R': A_UP_R, 'L': A_UP_L, 'cam': [-14, 0, 0], 'fov': 10}, 'inOutCubic'),
    (63, {'R': A_UP_R, 'L': A_UP_L, 'cam': [-12, 0, 0], 'fov': 8}),
    (70, dict(AFG), 'inOutSine'),
]))
# T. 날개: 웅크렸다가 (4) 날개를 쳐서 펴짐 (7)
A['angel.mode'] = clip(14, blend_in=0.5, blend_out=4, bones=seq([
    (0, AK),
    (4, AP(body=[22, 0, 0], head=[10, 0, 0], right_arm=[-30, 20, 40], left_arm=[-30, -20, -40], right_leg=[-30, 4, 4], left_leg=[14, -4, -4], root={'pos': [0, -2.4, 0]}), 'outCubic'),
    (7, AP(body=[-10, 0, 0], head=[-14, 0, 0], right_arm=[-20, 0, 70], left_arm=[-20, 0, -70], right_leg=[10, 2, 3], left_leg=[16, -2, -2], root={'pos': [0, 1.2, 0]}), 'outExpo'),
    (14, AK, 'inOutSine'),
]), fp=fpseq([
    (0, dict(AFG)),
    (4, {'R': ([10, -16, 6], [20, -20, 70]), 'L': ([-10, -16, 5.5], [20, 20, -70]), 'cam': [4, 0, 0], 'fov': -3}, 'outCubic'),
    (7, {'R': ([14, -12, 3], [30, -50, 88]), 'L': ([-14, -12, 2.5], [30, 50, -88]), 'cam': [-4, 0, 0], 'fov': 6}, 'outExpo'),
    (14, dict(AFG), 'inOutSine'),
]))
A['angel.select'] = clip(26, blend_in=2, blend_out=5, bones=seq([
    (0, AP(body=[0, 0, 0], head=[0, 0, 0], right_arm=[0, 0, 0], left_arm=[0, 0, 0], right_leg=[0, 0, 0], left_leg=[0, 0, 0], root={'pos': [0, 0, 0]})),
    (8, AP(body=[16, 0, 0], head=[22, 0, 0], right_arm=[-66, 44, 20], left_arm=[-66, -44, -20], root={'pos': [0, -0.6, 0]}), 'outCubic'),
    (14, AP(body=[-12, 0, 0], head=[-24, 0, 0], right_arm=[-40, -10, 80], left_arm=[-40, 10, -80], root={'pos': [0, 0.5, 0]}), 'outExpo'),
    (26, AK, 'inOutSine'),
]), fp=fpseq([
    (0, {'R': LOW_R, 'L': LOW_L, 'cam': [0, 0, 0]}),
    (8, {'R': A_PRAY_R, 'L': A_PRAY_L}, 'outCubic'),
    (14, {'R': ([13, -10, 4], [40, -50, 86]), 'L': ([-13, -10, 3.5], [40, 50, -86]), 'cam': [-2, 0, 0]}, 'outExpo'),
    (26, dict(AFG), 'inOutSine'),
]))

write('angel', A)


# =====================================================================================
#                        공통 피격 반응 (능력 없는 플레이어 포함)
# =====================================================================================
C = {}
REST = {'body': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'head': [0, 0, 0], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}}

C['common.flinch'] = clip(7, blend_in=0.5, blend_out=3, bones=seq([
    (0, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [-10, 0, 6], 'left_arm': [-10, 0, -6], 'root': {'rot': [0, 0, 0]}}),
    (1.5, {'body': [-10, 8, 0], 'head': [-18, 10, 0], 'right_arm': [-30, 0, 30], 'left_arm': [-20, 0, -26], 'root': {'rot': [-4, 0, 0]}}, 'outExpo'),
    (7, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [-10, 0, 6], 'left_arm': [-10, 0, -6], 'root': {'rot': [0, 0, 0]}}, 'outCubic'),
]))
C['common.flinch2'] = clip(7, blend_in=0.5, blend_out=3, bones=seq([
    (0, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [-10, 0, 6], 'left_arm': [-10, 0, -6], 'root': {'rot': [0, 0, 0]}}),
    (1.5, {'body': [-10, -8, 0], 'head': [-18, -10, 0], 'right_arm': [-20, 0, 26], 'left_arm': [-30, 0, -30], 'root': {'rot': [-4, 0, 0]}}, 'outExpo'),
    (7, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [-10, 0, 6], 'left_arm': [-10, 0, -6], 'root': {'rot': [0, 0, 0]}}, 'outCubic'),
]))
C['common.stagger'] = clip(16, blend_in=0.5, blend_out=5, bones=seq([
    (0, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [-10, 0, 6], 'left_arm': [-10, 0, -6], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}),
    (2, {'body': [-20, 10, 0], 'head': [-34, 12, 0], 'right_arm': [-60, 20, 60], 'left_arm': [-50, -20, -50], 'root': {'pos': [0, 0, -2], 'rot': [-10, 0, 4]}, 'right_leg': [-16, 0, 6], 'left_leg': [10, 0, -6]}, 'outExpo'),
    (8, {'body': [-10, 6, 0], 'head': [-14, 6, 0], 'right_arm': [-30, 10, 30], 'left_arm': [-26, -10, -24], 'root': {'pos': [0, 0, -1], 'rot': [-5, 0, 2]}}, 'inOutSine'),
    (16, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [-10, 0, 6], 'left_arm': [-10, 0, -6], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}, 'inOutSine'),
]))
C['common.grabbed'] = clip(16, blend_in=1, blend_out=3, bones=seq([
    (0, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'root': {'rot': [0, 0, 0]}, 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}),
    (3, {'body': [20, 0, 0], 'head': [-26, 0, 0], 'right_arm': [-120, 10, 30], 'left_arm': [-110, -10, -30], 'root': {'rot': [16, 0, 0]}, 'right_leg': [30, 0, 4], 'left_leg': [14, 0, -4]}, 'outExpo'),
    (8, {'body': [24, 0, 0], 'head': [-30, 0, 0], 'right_arm': [-100, 20, 40], 'left_arm': [-130, -20, -40], 'root': {'rot': [20, 0, 0]}, 'right_leg': [36, 0, 4], 'left_leg': [20, 0, -4]}, 'inOutSine'),
    (12, {'body': [24, 0, 0], 'head': [-30, 0, 0], 'right_arm': [-130, 20, 40], 'left_arm': [-100, -20, -40], 'root': {'rot': [20, 0, 0]}}, 'inOutSine'),
    (16, {'body': [10, 0, 0], 'head': [-10, 0, 0], 'right_arm': [-60, 10, 20], 'left_arm': [-60, -10, -20], 'root': {'rot': [6, 0, 0]}}, 'inOutSine'),
]))
C['common.held'] = clip(34, blend_in=1, blend_out=4, bones=seq([
    (0, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}),
    (3, {'body': [8, 0, 0], 'head': [22, 0, 0], 'right_arm': [10, 0, 14], 'left_arm': [10, 0, -14], 'right_leg': [8, 0, 3], 'left_leg': [14, 0, -3]}, 'outCubic'),
    (18, {'body': [10, 0, 0], 'head': [26, 0, 0], 'right_arm': [12, 0, 18], 'left_arm': [12, 0, -18], 'right_leg': [10, 0, 3], 'left_leg': [16, 0, -3]}, 'inOutSine'),
    (34, {'body': [8, 0, 0], 'head': [22, 0, 0], 'right_arm': [10, 0, 14], 'left_arm': [10, 0, -14], 'right_leg': [8, 0, 3], 'left_leg': [14, 0, -3]}, 'inOutSine'),
]))
C['common.headsnap'] = clip(10, blend_in=0.3, blend_out=4, bones=seq([
    (0, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'root': {'rot': [0, 0, 0]}}),
    (1.2, {'body': [-22, 0, 0], 'head': [-50, 6, 0], 'right_arm': [-40, 0, 50], 'left_arm': [-40, 0, -50], 'root': {'rot': [-12, 0, 0]}}, 'outExpo'),
    (10, {'body': [-6, 0, 0], 'head': [-10, 0, 0], 'right_arm': [-10, 0, 12], 'left_arm': [-10, 0, -12], 'root': {'rot': [-3, 0, 0]}}, 'outCubic'),
]))
C['common.slammed'] = clip(36, blend_in=0.5, blend_out=6, root_pivot=1, bones=seq([
    (0, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [-20, 0, 20], 'left_arm': [-20, 0, -20], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}),
    (3, {'body': [0, 0, 0], 'head': [16, 0, 0], 'right_arm': [-150, 0, 40], 'left_arm': [-150, 0, -40], 'root': {'pos': [0, 0, -12], 'rot': [-86, 0, 0]}, 'right_leg': [-20, 0, 8], 'left_leg': [-8, 0, -8]}, 'outBounce'),
    (24, {'body': [0, 0, 0], 'head': [12, 0, 0], 'right_arm': [-160, 0, 50], 'left_arm': [-150, 0, -46], 'root': {'pos': [0, 0, -12], 'rot': [-88, 0, 0]}, 'right_leg': [-14, 0, 10], 'left_leg': [-4, 0, -10]}),
    (30, {'body': [30, 0, 0], 'head': [10, 0, 0], 'right_arm': [-40, 0, 20], 'left_arm': [-40, 0, -20], 'root': {'pos': [0, -2, -2], 'rot': [-20, 0, 0]}, 'right_leg': [-50, 0, 6], 'left_leg': [-10, 0, -6]}, 'inOutCubic'),
    (36, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}, 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}, 'outCubic'),
]))
C['common.launched'] = clip(26, blend_in=0.5, blend_out=5, root_pivot=14, bones=seq([
    (0, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'root': {'rot': [0, 0, 0]}, 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}),
    (2, {'body': [-26, 0, 0], 'head': [-30, 0, 0], 'right_arm': [-130, 0, 50], 'left_arm': [-120, 0, -50], 'root': {'rot': [-46, 0, 8]}, 'right_leg': [-50, 0, 10], 'left_leg': [-30, 0, -10]}, 'outExpo'),
    (10, {'body': [-20, 0, 0], 'head': [-20, 0, 0], 'right_arm': [-150, 0, 70], 'left_arm': [-100, 0, -40], 'root': {'rot': [-56, 0, -10]}, 'right_leg': [-30, 0, 10], 'left_leg': [-60, 0, -10]}, 'inOutSine'),
    (18, {'body': [-10, 0, 0], 'head': [-10, 0, 0], 'right_arm': [-110, 0, 40], 'left_arm': [-150, 0, -70], 'root': {'rot': [-40, 0, 8]}, 'right_leg': [-60, 0, 10], 'left_leg': [-30, 0, -10]}, 'inOutSine'),
    (26, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'root': {'rot': [0, 0, 0]}, 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}, 'inOutSine'),
]))
C['common.launched_up'] = clip(24, blend_in=0.5, blend_out=5, root_pivot=14, bones=seq([
    (0, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'root': {'rot': [0, 0, 0]}, 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}),
    (2, {'body': [-30, 0, 0], 'head': [-40, 0, 0], 'right_arm': [-170, 10, 30], 'left_arm': [-160, -10, -30], 'root': {'rot': [-28, 0, 6]}, 'right_leg': [20, 0, 8], 'left_leg': [34, 0, -8]}, 'outExpo'),
    (10, {'body': [-24, 0, 0], 'head': [-30, 0, 0], 'right_arm': [-150, 10, 60], 'left_arm': [-176, -10, -40], 'root': {'rot': [-36, 0, -6]}, 'right_leg': [30, 0, 10], 'left_leg': [16, 0, -10]}, 'inOutSine'),
    (17, {'body': [-12, 0, 0], 'head': [-16, 0, 0], 'right_arm': [-176, 10, 40], 'left_arm': [-140, -10, -60], 'root': {'rot': [-20, 0, 6]}, 'right_leg': [16, 0, 10], 'left_leg': [30, 0, -10]}, 'inOutSine'),
    (24, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'root': {'rot': [0, 0, 0]}, 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}, 'inOutSine'),
]))
C['common.launched_down'] = clip(20, blend_in=0.5, blend_out=4, root_pivot=14, bones=seq([
    (0, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'root': {'rot': [0, 0, 0]}, 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}),
    (2, {'body': [30, 0, 0], 'head': [30, 0, 0], 'right_arm': [-170, 0, 20], 'left_arm': [-170, 0, -20], 'root': {'rot': [70, 0, 0]}, 'right_leg': [20, 0, 6], 'left_leg': [30, 0, -6]}, 'outExpo'),
    (14, {'body': [30, 0, 0], 'head': [30, 0, 0], 'right_arm': [-176, 0, 24], 'left_arm': [-166, 0, -24], 'root': {'rot': [80, 0, 0]}}, 'inOutSine'),
    (20, {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'root': {'rot': [0, 0, 0]}, 'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]}, 'inOutSine'),
]))
cap = {'body': [26, 0, 0], 'head': [30, 0, 0], 'right_arm': [-70, -40, 10], 'left_arm': [-70, 40, -10], 'right_leg': [-80, 6, 4], 'left_leg': [-74, -6, -4]}
C['common.captured'] = clip(170, blend_in=3, blend_out=6, root_pivot=12, bones=seq(
    [(0, dict(REST, right_leg=[0, 0, 0], left_leg=[0, 0, 0]))] +
    [(6, dict(cap, root={'pos': [0, 0, 0], 'rot': [20, 0, 0]}), 'outCubic')] +
    [(6 + i * 10, dict(cap, root={'pos': [0, 0, 0], 'rot': [20 + 10 * math.sin(i), 90 * i, 12 * math.cos(i * 1.3)]}), 'linear') for i in range(1, 16)] +
    [(170, dict(REST, right_leg=[0, 0, 0], left_leg=[0, 0, 0]), 'inOutSine')]
))

# ---------------------------------------------------------------- 공통 대시 (Q) — 모든 능력
def dash_clip(tp_peak, fp_peak, cam_peak):
    rest = {'body': [0, 0, 0], 'head': [0, 0, 0], 'right_arm': [-30, 0, 8], 'left_arm': [-30, 0, -8],
            'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0], 'root': {'pos': [0, 0, 0], 'rot': [0, 0, 0]}}
    hold = {}
    for k, v in tp_peak.items():
        if isinstance(v, dict):
            hold[k] = {ck: [x * 0.9 for x in cv] for ck, cv in v.items()}
        else:
            hold[k] = [x * 0.9 for x in v]
    bones = seq([
        (0, rest),
        (1.5, tp_peak, 'outExpo'),
        (5.5, hold, 'linear'),
        (10, rest, 'inOutSine'),
    ], rest=rest)
    fp = fpseq([
        (0, {'R': GR, 'L': GL, 'cam': [0, 0, 0]}),
        (1.5, dict(fp_peak, cam=cam_peak), 'outExpo'),
        (5.5, dict({k: (v[0], v[1]) for k, v in fp_peak.items()}, cam=[c * 0.6 for c in cam_peak]), 'linear'),
        (10, {'R': GR, 'L': GL, 'cam': [0, 0, 0]}, 'inOutSine'),
    ])
    return clip(10, bones=bones, fp=fp, blend_in=0.5, blend_out=3, root_pivot=10)


C['common.dash_forward'] = dash_clip(
    {'body': [26, 0, 0], 'head': [-24, 0, 0], 'right_arm': [58, 0, 22], 'left_arm': [58, 0, -22],
     'right_leg': [42, 0, 4], 'left_leg': [-46, 0, -4], 'root': {'pos': [0, -1.5, 2], 'rot': [14, 0, 0]}},
    {'R': ([9, -21, 2], [-8, 0, 62]), 'L': ([-9, -21, 2], [-8, 0, -62])},
    [2.5, 0, 0])
C['common.dash_back'] = dash_clip(
    {'body': [-14, 0, 0], 'head': [12, 0, 0], 'right_arm': [-82, -22, 10], 'left_arm': [-92, 22, -10],
     'right_leg': [-28, 0, 4], 'left_leg': [16, 0, -4], 'root': {'pos': [0, 0.5, -2], 'rot': [-10, 0, 0]}},
    {'R': ([5, -10, 9], [42, 12, 42]), 'L': ([-5, -10.5, 8.5], [40, -12, -42])},
    [-2.5, 0, 0])
C['common.dash_left'] = dash_clip(
    {'body': [6, 0, -10], 'head': [0, 0, 8], 'right_arm': [-24, 0, 72], 'left_arm': [-40, 10, -18],
     'right_leg': [0, 0, 26], 'left_leg': [0, 0, -8], 'root': {'pos': [-2, -0.5, 0], 'rot': [4, 6, -18]}},
    {'R': ([9.5, -13, 6], [34, -12, 70]), 'L': ([-3.5, -12.5, 8], [30, -14, -40])},
    [0, 1.2, 5])
C['common.dash_right'] = dash_clip(
    {'body': [6, 0, 10], 'head': [0, 0, -8], 'right_arm': [-40, -10, 18], 'left_arm': [-24, 0, -72],
     'right_leg': [0, 0, 8], 'left_leg': [0, 0, -26], 'root': {'pos': [2, -0.5, 0], 'rot': [4, -6, 18]}},
    {'R': ([3.5, -12.5, 8], [30, 14, 40]), 'L': ([-9.5, -13, 6], [34, 12, -70])},
    [0, -1.2, -5])

write('common', C)
