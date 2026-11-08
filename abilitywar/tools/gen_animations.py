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
write('common', C)
