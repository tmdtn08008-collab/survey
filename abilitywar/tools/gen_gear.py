#!/usr/bin/env python3
"""
RPG 장비 생성: 무기 470개 + 갑옷 세트 470개 (부위 4개씩).
  일반·좋음·희귀 각 100, 영웅·전설·신화 각 50, 시크릿·? 각 10.
만드는 것:
  data/abilitywar/gear/gear.json        (능력치 정의, 모드가 시작할 때 읽음)
  assets/abilitywar/textures/item/gear/  (아이콘 32x32, 신화 이상은 반짝이는 애니메이션)
  assets/abilitywar/textures/models/armor/gear/  (착용 갑옷 128x64 + 빛나는 층)
  assets/abilitywar_gear/lang/ko_kr.json, en_us.json  (이름, 설명)
  tools/art/gear_sheets/*.png            (한눈에 보는 그림)
사용: python3 tools/gen_gear.py [--no-tex]
"""
import json, math, os, random, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gear_data as D  # noqa: E402
import gear_paint as P  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
TEX_ITEM = os.path.join(RES, 'assets', 'abilitywar', 'textures', 'item', 'gear')
TEX_ARMOR = os.path.join(RES, 'assets', 'abilitywar', 'textures', 'models', 'armor', 'gear')
LANG = os.path.join(RES, 'assets', 'abilitywar_gear', 'lang')
DATA = os.path.join(RES, 'data', 'abilitywar', 'gear')
SHEETS = os.path.join(ROOT, 'tools', 'art', 'gear_sheets')
FONT = '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc'

TIER = {g[0]: i for i, g in enumerate(D.GRADES)}
# --only <등급>: 그 등급 그림만 새로 그림
ONLY = sys.argv[sys.argv.index('--only') + 1] if '--only' in sys.argv else None


def josa(word, a, b):
    """받침 있으면 a, 없으면 b (이/가, 을/를, 은/는)."""
    ch = word.strip()[-1]
    if '가' <= ch <= '힣':
        return a if (ord(ch) - 0xAC00) % 28 else b
    return b


def r1(x):
    return round(x, 3)


# ================================================================ 능력치
ATK = [4.0, 5.5, 7.0, 9.0, 11.5, 14.0, 17.0, 20.0, 30.0]
ABIL = [0.03, 0.06, 0.10, 0.15, 0.21, 0.28, 0.36, 0.45, 0.65]
BONUS = {
    'max_health': [1, 2, 2, 3, 4, 5, 6, 8, 10],
    'move_speed': [0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.09, 0.1, 0.115],
    'cooldown': [0.02, 0.03, 0.04, 0.05, 0.07, 0.09, 0.11, 0.13, 0.15],
    'lifesteal': [0.01, 0.015, 0.02, 0.03, 0.04, 0.05, 0.06, 0.08, 0.1],
    'ult_charge': [0.05, 0.08, 0.1, 0.14, 0.18, 0.22, 0.28, 0.35, 0.42],
    'armor': [1, 1, 2, 2, 3, 3, 4, 5, 6],
    'crit_chance': [0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.092],
    'crit_damage': [0.05, 0.08, 0.12, 0.16, 0.2, 0.25, 0.32, 0.4, 0.48],
    'knockback_resistance': [0.03, 0.05, 0.07, 0.1, 0.13, 0.16, 0.2, 0.25, 0.3],
    'luck': [1, 1, 1, 1, 2, 2, 3, 3, 3],
    'ability_damage': [0.02, 0.03, 0.04, 0.05, 0.07, 0.09, 0.11, 0.14, 0.17],
    'damage_reduction': [0.01, 0.015, 0.02, 0.025, 0.03, 0.04, 0.05, 0.06, 0.07],
}
INT_STATS = {'max_health', 'armor', 'luck'}
W_BONUS_KEYS = ['max_health', 'move_speed', 'cooldown', 'lifesteal', 'ult_charge', 'armor', 'crit_chance', 'crit_damage', 'knockback_resistance', 'luck']
A_BONUS_KEYS = ['max_health', 'ability_damage', 'cooldown', 'move_speed', 'crit_chance', 'lifesteal', 'damage_reduction', 'ult_charge', 'knockback_resistance']
N_BONUS_W = [None, 1, 2, 2, 3, 3, 4, 4, 5]
N_BONUS_A = [0, None, 1, 1, 2, 2, 3, 3, 4]


def bonus_value(key, t, rng):
    v = BONUS[key][t] * rng.uniform(0.8, 1.2)
    if key in INT_STATS:
        return max(1, int(round(v)))
    return r1(v)


def pick_bonus(keys, n, t, rng, exclude=()):
    pool = [k for k in keys if k not in exclude]
    rng.shuffle(pool)
    return {k: bonus_value(k, t, rng) for k in pool[:n]}


def weapon_effect(t, mat_key, rng, forced=None):
    if t < 3:
        return []
    key = forced or D.MAT_EFFECT.get(mat_key) or rng.choice(D.EFFECT_KEYS)
    return [make_effect(key, t, rng)]


def make_effect(key, t, rng):
    chance = [0, 0, 0, 0.15, 0.2, 0.25, 0.3, 0.32, 0.368][t]
    secs = [0, 0, 0, 3, 4, 5, 6, 6, 7][t]
    power = {'lightning': [0, 0, 0, 3, 4, 6, 8, 9, 10], 'vampire': [0, 0, 0, 5, 8, 11, 15, 18, 21], 'holy': [0, 0, 0, 1, 2, 2, 3, 4, 5],
             'execute': [0, 0, 0, 30, 45, 60, 80, 90, 104], 'chain': [0, 0, 0, 40, 50, 60, 75, 80, 92], 'explode': [0, 0, 0, 2, 3, 4, 6, 7, 8]}.get(key, [0] * 9)[t]
    if key == 'haste':
        secs = [0, 0, 0, 1, 1, 1.5, 2, 2, 2.3][t]
    return {'id': key, 'chance': r1(chance * rng.uniform(0.9, 1.1)), 'seconds': secs, 'power': power}


# ================================================================ 무기 목록
def build_weapons():
    out = []
    rng = random.Random(1234)
    for gkey, gko, gen, count, gcol in D.GRADES:
        t = TIER[gkey]
        items = []
        if gkey in ('common', 'good', 'rare', 'hero'):
            mats, prefixes = D.W_MATS[gkey], D.W_PREFIX[gkey]
            crng = random.Random('combo-w-' + gkey)
            # 종류마다 고르게: 종류별로 (접두어, 재료) 조합을 섞어 차례로 하나씩 뽑음
            per_kind = {}
            for kind in D.WEAPON_ORDER:
                combos = [(p, m) for p in prefixes for m in mats if not (kind in D.WOODEN_TYPES and m[2] in ('stone', 'flint'))]
                crng.shuffle(combos)
                per_kind[kind] = combos
            used_pm = {}
            i = 0
            while len(items) < count:
                kind = D.WEAPON_ORDER[i % len(D.WEAPON_ORDER)]
                i += 1
                lst = per_kind[kind]
                # 같은 종류 안에서는 재료가 겹치지 않게 우선
                cnt = used_pm.setdefault(kind, set())
                pick = next((c for c in lst if c[1][0] not in cnt), lst[0])
                lst.remove(pick)
                cnt.add(pick[1][0])
                if len(cnt) >= len(mats):
                    cnt.clear()
                pfx, m = pick
                tko, ten = D.WEAPON_TYPES[kind][:2]
                items.append(dict(kind=kind, mat=m[2], hand=m[3], gem=m[4], ko=f'{pfx[0]} {m[0]} {tko}', en=f'{pfx[1]} {m[1]} {ten}'))
        elif gkey == 'legendary':
            for i, (eko, een, mat, hand, gem) in enumerate(D.LEGEND_EPITHETS):
                for k in range(2):
                    kind = D.WEAPON_ORDER[(i * 5 + k * 7) % len(D.WEAPON_ORDER)]
                    tko, ten = D.WEAPON_TYPES[kind][:2]
                    items.append(dict(kind=kind, mat=mat, hand=hand, gem=gem, ko=f'{eko}의 {tko}', en=f'{ten} of {een}', epithet=eko))
        elif gkey == 'mythic':
            for (ko, en, kind, mat, hand, gem) in D.MYTHIC_WEAPONS:
                items.append(dict(kind=kind, mat=mat, hand=hand, gem=gem, ko=ko, en=en))
        elif gkey == 'secret':
            for (ko, en, kind, mat, hand, gem, lko, len_) in D.SECRET_WEAPONS:
                items.append(dict(kind=kind, mat=mat, hand=hand, gem=gem, ko=ko, en=en, lore_ko=lko, lore_en=len_))
        elif gkey == 'arvantia':
            for (ko, en, kind, mat, hand, gem, lko, len_) in D.ARVANTIA_WEAPONS:
                items.append(dict(kind=kind, mat=mat, hand=hand, gem=gem, ko=ko, en=en, lore_ko=lko, lore_en=len_))
        else:
            for i, (kind, mat) in enumerate(D.UNKNOWN_WEAPONS):
                items.append(dict(kind=kind, mat=mat, hand='dark', gem='void', ko=f'??? #{i + 1:02d}', en=f'??? #{i + 1:02d}'))
        for n, it in enumerate(items[:count]):
            kind = it['kind']
            tko, ten, fatk, spd, fab, crit0, critd0 = D.WEAPON_TYPES[kind]
            irng = random.Random(f'w-{gkey}-{n}')
            atk = ATK[t] * fatk * irng.uniform(0.92, 1.08)
            stats = {
                'attack_damage': round(atk, 1),
                'attack_speed': round(4.0 + spd, 2),
                'ability_damage': r1(ABIL[t] * fab * irng.uniform(0.85, 1.15)),
                'crit_chance': r1(crit0 + t * 0.012 * irng.uniform(0.7, 1.3)),
            }
            if critd0 + t * 0.03 > 0.01:
                stats['crit_damage'] = r1(critd0 + t * 0.03)
            nb = N_BONUS_W[t] if N_BONUS_W[t] is not None else irng.choice([0, 1])
            bonus = pick_bonus(W_BONUS_KEYS, nb, t, irng)
            for k, v in bonus.items():
                stats[k] = r1(stats.get(k, 0) + v)
            if gkey == 'secret':
                effects = [make_effect(D.MAT_EFFECT.get(it['mat'], 'chain'), t, irng), make_effect(irng.choice(D.EFFECT_KEYS), t, irng)]
                if effects[0]['id'] == effects[1]['id']:
                    effects[1] = make_effect('lightning' if effects[0]['id'] != 'lightning' else 'chain', t, irng)
            elif gkey == 'unknown':
                effects = [{'id': 'chaos', 'chance': 0.35, 'seconds': 5, 'power': 8}]
            elif gkey == 'arvantia':
                keys = [D.MAT_EFFECT.get(it['mat'], 'chain'), 'lightning', 'vampire', 'execute', 'holy', 'void', 'chain', 'explode']
                keys = list(dict.fromkeys(keys))
                irng.shuffle(keys)
                effects = [make_effect(k, t, irng) for k in keys[:3]]
            else:
                effects = weapon_effect(t, it['mat'], irng)
            wid = f'w_{gkey}_{n + 1:03d}'
            out.append(dict(id=wid, grade=gkey, type=kind, mat=it['mat'], hand=it['hand'], gem=it['gem'], ko=it['ko'], en=it['en'],
                            stats=stats, effects=effects, lore_ko=it.get('lore_ko'), lore_en=it.get('lore_en'), epithet=it.get('epithet')))
    return out


# ================================================================ 갑옷 목록
PROT = [7, 11, 15, 18, 20, 22, 24, 26, 32]
SPLIT = {'helmet': 0.16, 'chestplate': 0.38, 'leggings': 0.3, 'boots': 0.16}
STYLE_PROT = {'robe': 0.75, 'leather': 0.85, 'assassin': 0.85, 'bone': 0.95, 'chain': 1.0, 'scale': 1.0, 'crystal': 0.95,
              'plate': 1.1, 'knight': 1.12, 'dragon': 1.08, 'angel': 1.0, 'demon': 1.05}
STYLE_WORD = {'knight': ('기사', 'Knight'), 'dragon': ('용린', 'Dragonscale'), 'angel': ('천익', 'Seraph'), 'demon': ('마갑', 'Demonplate'),
              'assassin': ('그림자', 'Shadow'), 'robe': ('법의', 'Vestment'), 'plate': ('판금', 'Plate'), 'crystal': ('수정', 'Crystal'),
              'scale': ('비늘', 'Scale'), 'leather': ('가죽', 'Leather'), 'chain': ('사슬', 'Chain'), 'bone': ('뼈', 'Bone')}


def piece_names(style):
    if style in D.PIECE_NAMES:
        return D.PIECE_NAMES[style]
    return [(p[1], p[2]) for p in D.PIECES]


def build_armors():
    out = []
    for gkey, gko, gen, count, gcol in D.GRADES:
        t = TIER[gkey]
        sets = []
        if gkey in ('common', 'good', 'rare', 'hero'):
            mats, prefixes = D.A_MATS[gkey], D.A_PREFIX[gkey]
            crng = random.Random('combo-a-' + gkey)
            # 재료마다 고르게: 재료별로 접두어를 섞어 돌아가며 뽑음
            per_mat = {m[0]: [p for p in prefixes] for m in mats}
            for v in per_mat.values():
                crng.shuffle(v)
            i = 0
            while len(sets) < count:
                m = mats[i % len(mats)]
                i += 1
                if not per_mat[m[0]]:
                    continue
                pfx = per_mat[m[0]].pop()
                sets.append(dict(style=m[2], mat=m[3], trim=m[4], ko=f'{pfx[0]} {m[0]}', en=f'{pfx[1]} {m[1]}', mode='prefix'))
        elif gkey == 'legendary':
            for i, (eko, een, mat, hand, gem) in enumerate(D.LEGEND_EPITHETS):
                for k in range(2):
                    style = D.LEGEND_ARMOR_STYLE[(i + k * 3) % len(D.LEGEND_ARMOR_STYLE)]
                    sw = STYLE_WORD[style]
                    sets.append(dict(style=style, mat=mat, trim='gold', gem=gem, ko=f'{eko}의 {sw[0]}', en=f'{sw[1]} of {een}', mode='of', base_en=een, word_en=sw[1], epithet=eko))
        elif gkey == 'mythic':
            for (ko, en, style, mat, trim) in D.MYTHIC_ARMORS:
                sets.append(dict(style=style, mat=mat, trim=trim, ko=f'{ko}의', en=en, mode='myth', name_ko=ko))
        elif gkey == 'secret':
            for (ko, en, style, mat, gem, lko, len_) in D.SECRET_ARMORS:
                sets.append(dict(style=style, mat=mat, trim='gold', gem=gem, ko=ko, en=en, mode='secret', lore_ko=lko, lore_en=len_))
        elif gkey == 'arvantia':
            for (ko, en, style, mat, gem, lko, len_) in D.ARVANTIA_ARMORS:
                sets.append(dict(style=style, mat=mat, trim='gold', gem=gem, ko=ko, en=en, mode='secret', lore_ko=lko, lore_en=len_))
        else:
            for i, mat in enumerate(D.UNKNOWN_ARMORS):
                sets.append(dict(style=D.UNKNOWN_STYLES[i], mat=mat, trim='void', gem='void', ko=f'??? #{i + 1:02d}', en=f'??? #{i + 1:02d}', mode='unknown'))
        for n, st in enumerate(sets[:count]):
            irng = random.Random(f'a-{gkey}-{n}')
            style = st['style']
            sid = f'a_{gkey}_{n + 1:03d}'
            total = PROT[t] * STYLE_PROT.get(style, 1.0) * irng.uniform(0.95, 1.05)
            gem = st.get('gem')
            if gem is None and t >= 2:
                gem = irng.choice(list(D.GEMS))
            pieces = {}
            pn = piece_names(style)
            for pi, (pkey, _, _) in enumerate(D.PIECES):
                prot = max(1, int(round(total * SPLIT[pkey])))
                stats = {'armor': prot}
                tough = [0, 0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5][t]
                if tough:
                    stats['toughness'] = tough
                if t >= 4:
                    stats['knockback_resistance'] = r1([0, 0, 0, 0, 0.025, 0.05, 0.075, 0.1, 0.125][t])
                nb = N_BONUS_A[t] if N_BONUS_A[t] is not None else irng.choice([0, 1])
                for k, v in pick_bonus(A_BONUS_KEYS, nb, t, irng).items():
                    stats[k] = r1(stats.get(k, 0) + v)
                pko, pen = pn[pi]
                if st['mode'] == 'prefix':
                    ko, en = f"{st['ko']} {pko}", f"{st['en']} {pen}"
                elif st['mode'] == 'of':
                    ko, en = f"{st['ko']} {pko}", f"{st['word_en']} {pen} of {st['base_en']}"
                elif st['mode'] == 'myth':
                    ko, en = f"{st['ko']} {pko}", f"{pen} of {st['en']}"
                elif st['mode'] == 'secret':
                    ko, en = f"{st['ko']} — {pko}", f"{st['en']} {pen}"
                else:
                    ko, en = f"{st['ko']} ({pko})", f"{st['en']} ({pen})"
                pieces[pkey] = dict(id=f'{sid}_{pkey}', ko=ko, en=en, stats=stats)
            # 세트 효과
            set_stats, set_fx = {}, []
            if t >= 2:
                set_stats['max_health'] = [0, 0, 2, 4, 6, 8, 10, 12, 14][t]
                if style in ('robe', 'crystal', 'angel'):
                    set_stats['ability_damage'] = r1([0, 0, 0.04, 0.06, 0.08, 0.1, 0.13, 0.16, 0.19][t])
                elif style in ('assassin', 'leather'):
                    set_stats['crit_chance'] = r1([0, 0, 0.03, 0.04, 0.05, 0.07, 0.09, 0.1, 0.115][t])
                else:
                    set_stats['damage_reduction'] = r1([0, 0, 0.02, 0.03, 0.04, 0.05, 0.06, 0.08, 0.1][t])
            if t >= 4:
                set_fx.append(D.STYLE_SET_EFFECT.get(style, 'regen'))
            if gkey == 'secret':
                extra = irng.choice([k for k in D.SET_EFFECTS if k not in set_fx])
                set_fx.append(extra)
            if gkey == 'arvantia':
                pool = [k for k in D.SET_EFFECTS if k not in set_fx]
                irng.shuffle(pool)
                set_fx += pool[:2]
            if gkey == 'unknown':
                set_fx = ['chaos']
            if st['mode'] == 'prefix':
                sko, sen = f"{st['ko']} 세트", f"{st['en']} Set"
            elif st['mode'] == 'of':
                sko, sen = f"{st['ko']} 세트", f"{st['word_en']} Set of {st['base_en']}"
            elif st['mode'] == 'myth':
                sko, sen = f"{st['name_ko']}의 갑주", f"Armor of {st['en']}"
            else:
                sko, sen = f"{st['ko']}", f"{st['en']}"
            out.append(dict(id=sid, grade=gkey, style=style, mat=st['mat'], trim=st['trim'], gem=gem, ko=sko, en=sen, pieces=pieces,
                            set_stats=set_stats, set_effects=set_fx, lore_ko=st.get('lore_ko'), lore_en=st.get('lore_en'), epithet=st.get('epithet')))
    return out


# ================================================================ 설명 문구
LORE_W = {
    'common': [('흔히 볼 수 있는 {t}.', 'A {t} you can find anywhere.'), ('초보 모험가에게 어울린다.', 'Suits a beginner adventurer.'),
               ('손때가 묻어 있다.', 'Worn smooth by many hands.'), ('없는 것보다는 낫다.', 'Better than nothing.')],
    'good': [('잘 만든 {t}. 믿고 쓸 만하다.', 'A well-made {t}. Reliable.'), ('대장장이의 솜씨가 느껴진다.', "You can feel the smith's skill."),
             ('균형이 잘 잡혀 있다.', 'Well balanced.')],
    'rare': [('희미한 마력이 흐른다.', 'A faint magic flows through it.'), ('오래된 문양이 새겨져 있다.', 'Old patterns are engraved on it.'),
             ('쥐면 손끝이 따뜻해진다.', 'Your fingertips warm when you hold it.')],
    'hero': [('수많은 전장을 함께한 {t}.', 'A {t} that has seen countless battles.'), ('영웅의 이야기가 서려 있다.', "A hero's tale lingers in it."),
             ('휘두를 때마다 바람이 울린다.', 'The wind sings with every swing.')],
    'legendary': [('{e}{j} 쓰던 {t}.', 'The {t} once wielded by {e}.'), ('{e}의 전설과 함께 전해진다.', 'Passed down with the legend of {e}.')],
    'mythic': [('신화에 이름을 남긴 무기.', 'A weapon whose name lives on in myth.'), ('세상이 처음 생겼을 때부터 있었다고 한다.', 'Said to have existed since the world began.'),
               ('신들조차 탐냈다.', 'Even the gods coveted it.')],
    'unknown': [('정체를 알 수 없다. 쥘 때마다 다른 느낌이 든다.', 'Its nature is unknown. It feels different every time you hold it.'),
                ('이 세계의 물건이 아닌 것 같다.', 'It does not seem to belong to this world.')],
}
LORE_A = {
    'common': [('평범한 방어구.', 'Ordinary protection.'), ('여기저기 기운 자국이 있다.', 'Patched here and there.')],
    'good': [('튼튼하게 만들어졌다.', 'Sturdily made.'), ('몸에 잘 맞는다.', 'It fits well.')],
    'rare': [('가벼우면서 단단하다.', 'Light yet tough.'), ('은은한 마력이 몸을 감싼다.', 'A soft magic surrounds the wearer.')],
    'hero': [('영웅의 어깨를 지켜 온 갑옷.', "Armor that has guarded a hero's shoulders."), ('상처 하나하나가 훈장이다.', 'Every scar is a medal.')],
    'legendary': [('{e}의 갑옷.', 'The armor of {e}.'), ('{e}{j} 마지막 전투에서 입었다고 한다.', 'Said to have been worn by {e} in the final battle.')],
    'mythic': [('신화 속 영웅이 입던 갑주.', 'Armor worn by a hero of myth.'), ('입는 순간 전설의 무게가 느껴진다.', 'You feel the weight of legend the moment you wear it.')],
    'unknown': [('입으면 몸의 윤곽이 흐려진다.', "The wearer's outline blurs."), ('누가 만들었는지 아무도 모른다.', 'No one knows who made it.')],
}


def lore(table, gkey, rng, tname=('', ''), epithet=None):
    if gkey not in table:
        return None, None
    ko, en = rng.choice(table[gkey])
    e = epithet or ''
    return ko.format(t=tname[0], e=e, j=josa(e, '이', '가') if e else ''), en.format(t=tname[1].lower(), e='them')


# ================================================================ 그림
def save_frames(frames, path):
    if len(frames) == 1:
        Image.fromarray(frames[0], 'RGBA').save(path)
        return
    h = frames[0].shape[0]
    strip = np.concatenate(frames, axis=0)
    Image.fromarray(strip, 'RGBA').save(path)
    with open(path + '.mcmeta', 'w') as f:
        json.dump({'animation': {'frametime': 2, 'interpolate': False}}, f)


def pal(key):
    if key in D.MAT:
        return D.MAT[key]
    return D.HANDLE.get(key, D.MAT['iron'])


ICON_OVERRIDE = os.path.join(ROOT, 'tools', 'art', 'gear_icons')


def base_weapon_frames(w):
    """생성기로 그린 무기 아이콘 (프레임 목록)."""
    t = TIER[w['grade']]
    rng = random.Random('wp-' + w['id'])
    extra = {'stars': w['grade'] == 'secret' and w['mat'] in ('stariron', 'void', 'obsidian', 'abyss', 'moon')}
    return P.paint_weapon(w['type'], pal(w['mat']), D.HANDLE.get(w['hand'], D.HANDLE['leather']), D.GEMS.get(w['gem']) if w['gem'] else None, t, rng, extra)


def base_armor_icon_frames(a, pkey):
    t = TIER[a['grade']]
    gem = D.GEMS.get(a['gem']) if a['gem'] else None
    rng = random.Random('ap-' + a['pieces'][pkey]['id'])
    return P.paint_armor_icon(pkey, a['style'], pal(a['mat']), pal(a['trim']), gem, t, rng)


def override_frames(item_id, tier):
    """tools/art/gear_icons/<아이템>.png (꾸민 그림에서 가져온 아이콘) 이 있으면 그것 (신화 이상은 반짝임 애니메이션)."""
    path = os.path.join(ICON_OVERRIDE, item_id + '.png')
    if not os.path.exists(path):
        return None
    img = np.asarray(Image.open(path).convert('RGBA')).astype(np.uint8)
    return P.animate_icon(img, tier, random.Random('ov-' + item_id))


def armor_palettes():
    path = os.path.join(ICON_OVERRIDE, 'palettes.json')
    return json.load(open(path)) if os.path.exists(path) else {}


def write_textures(weapons, armors):
    os.makedirs(TEX_ITEM, exist_ok=True)
    os.makedirs(TEX_ARMOR, exist_ok=True)
    if ONLY:
        weapons = [w for w in weapons if w['grade'] == ONLY]
        armors = [a for a in armors if a['grade'] == ONLY]
    else:
        for f in os.listdir(TEX_ITEM):
            os.remove(os.path.join(TEX_ITEM, f))
        for f in os.listdir(TEX_ARMOR):
            os.remove(os.path.join(TEX_ARMOR, f))
    icons = {}
    n_over = 0
    for w in weapons:
        frames = override_frames(w['id'], TIER[w['grade']])
        n_over += frames is not None
        frames = frames or base_weapon_frames(w)
        save_frames(frames, os.path.join(TEX_ITEM, w['id'] + '.png'))
        icons[w['id']] = frames[0]
    pals = armor_palettes()
    for a in armors:
        t = TIER[a['grade']]
        gem = D.GEMS.get(a['gem']) if a['gem'] else None
        trim = pal(a['trim'])
        mat = pal(a['mat'])
        if a['id'] in pals:
            # 꾸민 그림에서 새로 그린 세트: 착용 갑옷 색을 아이콘에 맞춤
            mat = tuple(tuple(c) for c in pals[a['id']]['mat'])
            trim = tuple(tuple(c) for c in pals[a['id']]['trim'])
        for pkey, pc in a['pieces'].items():
            frames = override_frames(pc['id'], t)
            n_over += frames is not None
            frames = frames or base_armor_icon_frames(a, pkey)
            save_frames(frames, os.path.join(TEX_ITEM, pc['id'] + '.png'))
            icons[pc['id']] = frames[0]
        rng = random.Random('al-' + a['id'])
        l1, l2, e1, e2 = P.paint_armor_layers(a['style'], mat, trim, gem, t, rng)
        Image.fromarray(l1, 'RGBA').save(os.path.join(TEX_ARMOR, a['id'] + '_1.png'))
        Image.fromarray(l2, 'RGBA').save(os.path.join(TEX_ARMOR, a['id'] + '_2.png'))
        if t >= 4:
            # 눈(eyes) 레이어용: 미리 곱한 알파
            for img, suf in ((e1, '_1e'), (e2, '_2e')):
                f = img.astype(np.float32)
                f[..., :3] *= f[..., 3:4] / 255
                Image.fromarray(f.clip(0, 255).astype(np.uint8), 'RGBA').save(os.path.join(TEX_ARMOR, a['id'] + suf + '.png'))
    print('imported icons used:', n_over)
    return icons


# ================================================================ 한눈에 보는 그림
def font(size):
    return ImageFont.truetype(FONT, size)


def grade_color(gkey):
    return next(g[4] for g in D.GRADES if g[0] == gkey)


def sheet_weapons(weapons, icons, gkey):
    ws = [w for w in weapons if w['grade'] == gkey]
    cols = 10 if len(ws) > 10 else 5
    cw, ch = 152, 150
    rows = (len(ws) + cols - 1) // cols
    W, H = cols * cw + 20, rows * ch + 80
    im = Image.new('RGB', (W, H), (22, 22, 30))
    d = ImageDraw.Draw(im)
    gk = next(g for g in D.GRADES if g[0] == gkey)
    d.text((14, 14), f'무기 · {gk[1]} 등급 ({len(ws)}개)', font=font(34), fill=grade_color(gkey))
    f = font(15)
    for i, w in enumerate(ws):
        x, y = 10 + (i % cols) * cw, 70 + (i // cols) * ch
        d.rounded_rectangle([x + 4, y + 2, x + cw - 4, y + ch - 6], radius=8, fill=(34, 34, 46), outline=tuple(int(c * 0.6) for c in grade_color(gkey)), width=2)
        ic = Image.fromarray(icons[w['id']]).resize((96, 96), Image.NEAREST)
        im.paste(ic, (x + (cw - 96) // 2, y + 6), ic)
        name = w['ko']
        lines = [name] if d.textlength(name, font=f) <= cw - 12 else wrap2(d, name, f, cw - 12)
        for k, ln in enumerate(lines[:2]):
            tw = d.textlength(ln, font=f)
            d.text((x + (cw - tw) / 2, y + 104 + k * 18), ln, font=f, fill=(235, 235, 240))
    return im


def wrap2(d, s, f, wmax):
    words = s.split(' ')
    a = ''
    for i, wd in enumerate(words):
        t = (a + ' ' + wd).strip()
        if d.textlength(t, font=f) > wmax and a:
            return [a, ' '.join(words[i:])]
        a = t
    return [a]


def sheet_armors(armors, icons, gkey):
    sets = [a for a in armors if a['grade'] == gkey]
    cols = 5
    cw, ch = 300, 118
    rows = (len(sets) + cols - 1) // cols
    W, H = cols * cw + 20, rows * ch + 80
    im = Image.new('RGB', (W, H), (22, 22, 30))
    d = ImageDraw.Draw(im)
    gk = next(g for g in D.GRADES if g[0] == gkey)
    d.text((14, 14), f'갑옷 세트 · {gk[1]} 등급 ({len(sets)}세트, 부위 {len(sets) * 4}개)', font=font(34), fill=grade_color(gkey))
    f = font(15)
    for i, a in enumerate(sets):
        x, y = 10 + (i % cols) * cw, 70 + (i // cols) * ch
        d.rounded_rectangle([x + 4, y + 2, x + cw - 4, y + ch - 6], radius=8, fill=(34, 34, 46), outline=tuple(int(c * 0.6) for c in grade_color(gkey)), width=2)
        for k, pkey in enumerate(['helmet', 'chestplate', 'leggings', 'boots']):
            ic = Image.fromarray(icons[a['pieces'][pkey]['id']]).resize((64, 64), Image.NEAREST)
            im.paste(ic, (x + 16 + k * 68, y + 8), ic)
        tw = d.textlength(a['ko'], font=f)
        d.text((x + (cw - tw) / 2, y + 82), a['ko'], font=f, fill=(235, 235, 240))
    return im


# ================================================================ 언어 파일
def fmt_pct(v):
    return f'{v * 100:.0f}'


def write_lang(weapons, armors):
    ko, en = {}, {}
    for gkey, gko, gen, count, col in D.GRADES:
        ko[f'gear.abilitywar.grade.{gkey}'] = gko
        en[f'gear.abilitywar.grade.{gkey}'] = gen
    for k, v in D.WEAPON_TYPES.items():
        ko[f'gear.abilitywar.type.{k}'] = v[0]
        en[f'gear.abilitywar.type.{k}'] = v[1]
    for k, (pk, pe) in [(p[0], (p[1], p[2])) for p in D.PIECES]:
        ko[f'gear.abilitywar.piece.{k}'] = pk
        en[f'gear.abilitywar.piece.{k}'] = pe
    for k, (sk, se, pct) in D.STATS.items():
        ko[f'gear.abilitywar.stat.{k}'] = sk
        en[f'gear.abilitywar.stat.{k}'] = se
    for k, (nk, ne, dk, de) in D.EFFECTS.items():
        ko[f'gear.abilitywar.effect.{k}'] = nk
        en[f'gear.abilitywar.effect.{k}'] = ne
        def f(x):
            # 숫자 뒤의 % 는 %% 로 (자바 형식 문자열)
            return x.replace('%', '%%').replace('{c}', '%1$s').replace('{s}', '%2$s').replace('{p}', '%3$s')
        ko[f'gear.abilitywar.effect.{k}.desc'] = f(dk)
        en[f'gear.abilitywar.effect.{k}.desc'] = f(de)
    ko['gear.abilitywar.effect.chaos'] = '혼돈'
    en['gear.abilitywar.effect.chaos'] = 'Chaos'
    ko['gear.abilitywar.effect.chaos.desc'] = '공격할 때마다 무작위 효과가 터진다 (%1$s%% 확률)'
    en['gear.abilitywar.effect.chaos.desc'] = 'Every hit triggers a random effect (%1$s%% chance)'
    for k, (nk, ne, dk, de) in D.SET_EFFECTS.items():
        ko[f'gear.abilitywar.seteffect.{k}'] = nk
        en[f'gear.abilitywar.seteffect.{k}'] = ne
        ko[f'gear.abilitywar.seteffect.{k}.desc'] = dk
        en[f'gear.abilitywar.seteffect.{k}.desc'] = de
    ko['gear.abilitywar.seteffect.chaos'] = '혼돈'
    en['gear.abilitywar.seteffect.chaos'] = 'Chaos'
    ko['gear.abilitywar.seteffect.chaos.desc'] = '20초마다 무작위 축복 (또는 저주)'
    en['gear.abilitywar.seteffect.chaos.desc'] = 'A random blessing (or curse) every 20s'
    ui = {
        'tab': ('RPG 장비 · %s', 'RPG Gear · %s'),
        'weapon': ('무기', 'Weapon'), 'armor': ('방어구', 'Armor'),
        'set': ('세트: %s (%s/4)', 'Set: %s (%s/4)'), 'set_bonus': ('세트 효과 (4부위)', 'Set bonus (4 pieces)'),
        'on_hit': ('특수 효과', 'Special effect'), 'unknown_stat': ('???', '???'), 'unknown_hint': ('능력치를 알 수 없다', 'Stats unknown'),
        'held': ('손에 들었을 때:', 'When held:'), 'worn': ('착용했을 때:', 'When worn:'),
        'ability_hint': ('능력 평타·스킬에도 적용', 'Also applies to ability attacks and skills'),
        'give': ('%s 등급 장비 %s개를 받았습니다', 'Received %2$s %1$s gear'),
        'grade_mark': ('◆ ', '◆ '),
        'req_level': ('착용 레벨 %s', 'Requires level %s'),
        'level_locked': ('레벨 %s 이 되어야 쓸 수 있는 장비입니다', 'You need level %s to use this gear'),
    }
    for k, (a, b) in ui.items():
        ko[f'gear.abilitywar.ui.{k}'] = a
        en[f'gear.abilitywar.ui.{k}'] = b
    attrs = {'ability_damage': ('능력 피해', 'Ability Damage'), 'crit_chance': ('치명타 확률', 'Critical Chance'), 'crit_damage': ('치명타 피해', 'Critical Damage'),
             'cooldown_reduction': ('재사용 대기시간 감소', 'Cooldown Reduction'), 'lifesteal': ('흡혈', 'Lifesteal'), 'ult_charge': ('궁극기 충전', 'Ultimate Charge'),
             'damage_reduction': ('받는 피해 감소', 'Damage Reduction'),
             'physical_damage': ('물리 피해', 'Physical Damage'), 'magic_damage': ('마법 피해', 'Magic Damage'), 'heal_power': ('회복량', 'Healing Power')}
    for k, (a, b) in attrs.items():
        ko[f'attribute.name.abilitywar.{k}'] = a
        en[f'attribute.name.abilitywar.{k}'] = b
    for w in weapons:
        ko[f"item.abilitywar.{w['id']}"] = w['ko']
        en[f"item.abilitywar.{w['id']}"] = w['en']
        rng = random.Random('lore-' + w['id'])
        tn = D.WEAPON_TYPES[w['type']][:2]
        lk, le = (w['lore_ko'], w['lore_en']) if w.get('lore_ko') else lore(LORE_W, w['grade'], rng, tn, w.get('epithet'))
        if lk:
            ko[f"item.abilitywar.{w['id']}.lore"] = lk
            en[f"item.abilitywar.{w['id']}.lore"] = le
    for a in armors:
        ko[f"gear.abilitywar.set.{a['id']}"] = a['ko']
        en[f"gear.abilitywar.set.{a['id']}"] = a['en']
        rng = random.Random('lore-' + a['id'])
        lk, le = (a['lore_ko'], a['lore_en']) if a.get('lore_ko') else lore(LORE_A, a['grade'], rng, ('', ''), a.get('epithet'))
        for pkey, pc in a['pieces'].items():
            ko[f"item.abilitywar.{pc['id']}"] = pc['ko']
            en[f"item.abilitywar.{pc['id']}"] = pc['en']
            if lk:
                ko[f"item.abilitywar.{pc['id']}.lore"] = lk
                en[f"item.abilitywar.{pc['id']}.lore"] = le
    os.makedirs(LANG, exist_ok=True)
    for name, d in (('ko_kr.json', ko), ('en_us.json', en)):
        with open(os.path.join(LANG, name), 'w', encoding='utf-8') as f:
            json.dump(d, f, ensure_ascii=False, indent=1)
    return len(ko)


def write_data(weapons, armors):
    os.makedirs(DATA, exist_ok=True)
    data = {
        'grades': [{'id': g[0], 'color': '#%02X%02X%02X' % g[4], 'count': g[3]} for g in D.GRADES],
        'weapons': [{k: w[k] for k in ('id', 'grade', 'type', 'stats', 'effects')} for w in weapons],
        'armors': [{'id': a['id'], 'grade': a['grade'], 'style': a['style'],
                    'pieces': {p: {'id': v['id'], 'stats': v['stats']} for p, v in a['pieces'].items()},
                    'set_stats': a['set_stats'], 'set_effects': a['set_effects'],
                    'glow': TIER[a['grade']] >= 4} for a in armors],
    }
    with open(os.path.join(DATA, 'gear.json'), 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, separators=(',', ':'))


def main():
    weapons = build_weapons()
    armors = build_armors()
    # 개수 확인
    for gkey, gko, gen, count, col in D.GRADES:
        nw = sum(1 for w in weapons if w['grade'] == gkey)
        na = sum(1 for a in armors if a['grade'] == gkey)
        assert nw == count and na == count, (gkey, nw, na, count)
    names = [w['ko'] for w in weapons]
    assert len(set(names)) == len(names), [n for n in names if names.count(n) > 1][:5]
    snames = [a['ko'] for a in armors]
    assert len(set(snames)) == len(snames), [n for n in snames if snames.count(n) > 1][:5]
    write_data(weapons, armors)
    n = write_lang(weapons, armors)
    print('weapons', len(weapons), 'armor sets', len(armors), 'lang keys', n)
    if '--no-tex' in sys.argv:
        return
    icons = write_textures(weapons, armors)
    os.makedirs(SHEETS, exist_ok=True)
    if not ONLY:
        for f in os.listdir(SHEETS):
            os.remove(os.path.join(SHEETS, f))
    for i, g in enumerate(D.GRADES):
        if ONLY and g[0] != ONLY:
            continue
        sheet_weapons(weapons, icons, g[0]).save(os.path.join(SHEETS, f'weapons_{i + 1}_{g[0]}.png'))
        sheet_armors(armors, icons, g[0]).save(os.path.join(SHEETS, f'armor_{i + 1}_{g[0]}.png'))
    print('textures', len(os.listdir(TEX_ITEM)), len(os.listdir(TEX_ARMOR)))


if __name__ == '__main__':
    main()
