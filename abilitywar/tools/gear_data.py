"""RPG 장비 생성 데이터: 등급, 무기 종류, 재료(색), 이름 단어, 효과, 갑옷 스타일."""

# (키, 한국어, 영어, 개수, 색)
GRADES = [
    ('common', '일반', 'Common', 100, (200, 200, 200)),
    ('good', '좋음', 'Good', 100, (107, 227, 107)),
    ('rare', '희귀', 'Rare', 100, (79, 163, 255)),
    ('hero', '영웅', 'Heroic', 50, (180, 92, 255)),
    ('legendary', '전설', 'Legendary', 50, (255, 176, 32)),
    ('mythic', '신화', 'Mythic', 50, (255, 72, 72)),
    ('secret', '시크릿', 'Secret', 10, (46, 242, 208)),
    ('unknown', '?', '?', 10, (255, 255, 255)),
    # ? 를 +99 → 초월+ +99 → 정제 +99 까지 키운 뒤 승급해서만 얻음 (2.9)
    ('arvantia', '아르반티아 인피니티', 'Arvantia Infinity', 12, (255, 128, 232)),
]
GRADE_KEYS = [g[0] for g in GRADES]

# ---------------------------------------------------------------- 무기 종류
# 키: (한국어, 영어, 공격력 배율, 공격 속도(바닐라 수정치), 능력 피해 배율, 기본 치명타 확률, 치명타 피해 보너스)
WEAPON_TYPES = {
    'sword': ('검', 'Sword', 1.0, -2.4, 1.0, 0.04, 0.0),
    'greatsword': ('대검', 'Greatsword', 1.35, -3.0, 1.05, 0.03, 0.1),
    'dagger': ('단검', 'Dagger', 0.7, -1.6, 0.9, 0.12, 0.1),
    'axe': ('도끼', 'Axe', 1.25, -3.0, 1.0, 0.04, 0.15),
    'hammer': ('전투 망치', 'Warhammer', 1.45, -3.2, 0.85, 0.02, 0.2),
    'spear': ('창', 'Spear', 1.1, -2.6, 1.0, 0.05, 0.05),
    'scythe': ('낫', 'Scythe', 1.2, -2.8, 1.05, 0.06, 0.3),
    'katana': ('태도', 'Katana', 1.05, -2.2, 1.0, 0.09, 0.2),
    'rapier': ('레이피어', 'Rapier', 0.85, -2.0, 0.95, 0.14, 0.05),
    'mace': ('철퇴', 'Mace', 1.15, -2.8, 0.95, 0.03, 0.1),
    'staff': ('지팡이', 'Staff', 0.6, -2.6, 1.6, 0.03, 0.0),
    'wand': ('완드', 'Wand', 0.45, -2.0, 1.4, 0.05, 0.0),
}
WEAPON_ORDER = list(WEAPON_TYPES)
WOODEN_TYPES = {'staff', 'wand'}

# ---------------------------------------------------------------- 재료 색 (밝음, 중간, 어두움)
MAT = {
    'wood': ((176, 132, 84), (128, 92, 54), (78, 54, 30)),
    'stone': ((170, 170, 170), (126, 126, 126), (82, 82, 86)),
    'copper': ((236, 160, 110), (196, 108, 66), (120, 60, 34)),
    'bone': ((240, 234, 214), (206, 196, 168), (140, 128, 104)),
    'bronze': ((222, 176, 104), (176, 128, 62), (110, 74, 34)),
    'flint': ((110, 110, 118), (72, 72, 80), (40, 40, 46)),
    'rusty': ((186, 132, 100), (140, 90, 62), (86, 52, 36)),
    'thorn': ((134, 150, 92), (94, 108, 60), (56, 64, 34)),
    'iron': ((236, 236, 236), (190, 190, 196), (120, 120, 130)),
    'steel': ((220, 232, 244), (160, 176, 196), (90, 102, 122)),
    'silver': ((250, 250, 255), (200, 206, 220), (130, 136, 156)),
    'ebony': ((100, 84, 76), (62, 50, 46), (32, 24, 22)),
    'tempered': ((250, 226, 200), (196, 168, 150), (110, 92, 86)),
    'whiteiron': ((246, 246, 240), (214, 214, 206), (150, 150, 144)),
    'blackiron': ((120, 120, 130), (80, 80, 92), (44, 44, 54)),
    'gold': ((255, 236, 140), (240, 190, 60), (170, 110, 20)),
    'mithril': ((214, 244, 255), (150, 206, 236), (80, 128, 170)),
    'darksteel': ((110, 100, 140), (70, 62, 98), (36, 30, 56)),
    'crystal': ((220, 250, 255), (140, 220, 250), (60, 140, 200)),
    'jade': ((170, 240, 190), (90, 190, 130), (36, 112, 72)),
    'amethyst': ((230, 190, 255), (170, 110, 230), (96, 52, 150)),
    'rune': ((200, 210, 230), (140, 150, 180), (70, 76, 110)),
    'stariron': ((150, 170, 230), (80, 96, 170), (34, 40, 96)),
    'lapis': ((120, 160, 255), (60, 96, 210), (24, 40, 130)),
    'adamant': ((200, 255, 230), (110, 210, 170), (40, 120, 96)),
    'dragon': ((255, 150, 110), (210, 70, 50), (110, 24, 20)),
    'holy': ((255, 252, 236), (250, 226, 160), (200, 160, 80)),
    'frost': ((236, 252, 255), (160, 222, 255), (80, 150, 220)),
    'flame': ((255, 220, 120), (255, 130, 40), (180, 50, 20)),
    'storm': ((210, 230, 255), (120, 150, 230), (60, 70, 160)),
    'shadow': ((90, 80, 110), (50, 44, 68), (20, 16, 30)),
    'mana': ((180, 200, 255), (110, 120, 255), (60, 50, 190)),
    'blood': ((255, 120, 130), (200, 30, 50), (100, 10, 20)),
    'void': ((150, 110, 220), (80, 40, 150), (26, 10, 60)),
    'sun': ((255, 250, 200), (255, 210, 80), (220, 130, 30)),
    'moon': ((230, 236, 255), (170, 180, 230), (90, 100, 160)),
    'abyss': ((90, 130, 150), (30, 70, 90), (8, 26, 40)),
    'verdant': ((190, 255, 150), (100, 200, 80), (40, 110, 40)),
    'obsidian': ((90, 60, 120), (40, 24, 60), (14, 8, 24)),
    'rose': ((255, 200, 220), (240, 120, 160), (160, 50, 90)),
    'chaos': ((255, 100, 255), (120, 255, 240), (30, 10, 40)),
    'arvantia': ((255, 252, 240), (246, 206, 255), (150, 86, 214)),
    'infinity': ((214, 255, 255), (120, 206, 255), (58, 64, 190)),
}
HANDLE = {
    'wood': ((150, 104, 62), (106, 72, 40), (66, 42, 22)),
    'leather': ((140, 86, 52), (98, 58, 34), (60, 34, 20)),
    'cloth': ((200, 60, 60), (150, 34, 40), (90, 20, 26)),
    'dark': ((70, 60, 80), (44, 38, 54), (24, 20, 30)),
    'white': ((240, 236, 226), (200, 194, 180), (140, 132, 118)),
    'gold': MAT['gold'],
    'blue': ((90, 120, 220), (50, 70, 170), (26, 36, 100)),
    'violet': ((150, 90, 210), (100, 54, 160), (54, 26, 96)),
}
GEMS = {
    'ruby': (255, 60, 80), 'sapphire': (70, 130, 255), 'emerald': (60, 230, 120), 'topaz': (255, 200, 50),
    'amethyst': (200, 100, 255), 'diamond': (210, 250, 255), 'onyx': (60, 50, 70), 'pearl': (255, 245, 240),
    'void': (160, 60, 255), 'sun': (255, 230, 120), 'blood': (230, 20, 50), 'frost': (150, 230, 255),
    'infinity': (255, 140, 240), 'prism': (140, 255, 250),
}

# ---------------------------------------------------------------- 무기 이름 단어 (한국어, 영어, 재료 색, 손잡이, 보석)
W_MATS = {
    'common': [('나무', 'Wooden', 'wood', 'leather', None), ('돌', 'Stone', 'stone', 'wood', None), ('구리', 'Copper', 'copper', 'leather', None),
               ('뼈', 'Bone', 'bone', 'leather', None), ('청동', 'Bronze', 'bronze', 'wood', None), ('부싯돌', 'Flint', 'flint', 'wood', None),
               ('녹슨 철', 'Rusty Iron', 'rusty', 'leather', None), ('가시나무', 'Thornwood', 'thorn', 'wood', None)],
    'good': [('철', 'Iron', 'iron', 'leather', None), ('강철', 'Steel', 'steel', 'leather', 'topaz'), ('은', 'Silver', 'silver', 'blue', 'sapphire'),
             ('흑단', 'Ebony', 'ebony', 'dark', None), ('담금질한 강철', 'Tempered Steel', 'tempered', 'cloth', None), ('백철', 'White Iron', 'whiteiron', 'white', 'pearl'),
             ('흑철', 'Black Iron', 'blackiron', 'dark', 'ruby'), ('황동', 'Brass', 'gold', 'leather', None)],
    'rare': [('미스릴', 'Mithril', 'mithril', 'blue', 'sapphire'), ('흑강', 'Darksteel', 'darksteel', 'violet', 'amethyst'), ('수정', 'Crystal', 'crystal', 'white', 'diamond'),
             ('비취', 'Jade', 'jade', 'gold', 'emerald'), ('자수정', 'Amethyst', 'amethyst', 'violet', 'amethyst'), ('룬석', 'Runestone', 'rune', 'dark', 'sapphire'),
             ('별철', 'Stariron', 'stariron', 'blue', 'diamond'), ('청금석', 'Lapis', 'lapis', 'gold', 'sapphire')],
    'hero': [('아다만트', 'Adamant', 'adamant', 'dark', 'emerald'), ('용비늘', 'Dragonscale', 'dragon', 'cloth', 'ruby'), ('성은', 'Hallowed Silver', 'holy', 'white', 'pearl'),
             ('서리수정', 'Frostcrystal', 'frost', 'blue', 'frost'), ('화염석', 'Flamestone', 'flame', 'cloth', 'topaz'), ('폭풍강철', 'Stormsteel', 'storm', 'blue', 'sapphire'),
             ('그림자철', 'Shadowiron', 'shadow', 'dark', 'void'), ('마나석', 'Manastone', 'mana', 'violet', 'amethyst'), ('핏빛 강철', 'Bloodsteel', 'blood', 'dark', 'blood'),
             ('달은', 'Moonsilver', 'moon', 'white', 'diamond')],
}
W_PREFIX = {
    'common': [('낡은', 'Worn'), ('투박한', 'Crude'), ('수련용', 'Training'), ('평범한', 'Plain'), ('견습생의', "Apprentice's"),
               ('마을 대장장이의', "Village Smith's"), ('금 간', 'Cracked'), ('가벼운', 'Light'), ('무딘', 'Dull'), ('사냥용', 'Hunting'),
               ('농부의', "Farmer's"), ('여행자의', "Traveler's")],
    'good': [('단단한', 'Sturdy'), ('날카로운', 'Sharp'), ('경비병의', "Guard's"), ('용병의', "Mercenary's"), ('사냥꾼의', "Hunter's"),
             ('정교한', 'Fine'), ('균형 잡힌', 'Balanced'), ('기사 견습의', "Squire's"), ('모험가의', "Adventurer's"), ('숙련된', 'Skilled'),
             ('결투가의', "Duelist's"), ('순찰대의', "Ranger's")],
    'rare': [('마력 깃든', 'Enchanted'), ('기사단의', "Order's"), ('고대의', 'Ancient'), ('빛나는', 'Gleaming'), ('정령의', 'Spirit'),
             ('은빛 달의', 'Silvermoon'), ('바람의', 'Windborne'), ('장인의', 'Masterwork'), ('왕실 근위대의', 'Royal Guard'), ('현자의', "Sage's"),
             ('룬이 새겨진', 'Runed'), ('별빛의', 'Starlit')],
    'hero': [('영웅의', "Hero's"), ('용사의', "Champion's"), ('전쟁군주의', "Warlord's"), ('대마법사의', "Archmage's"), ('수호자의', "Guardian's"),
             ('백전노장의', "Veteran's"), ('검성의', "Sword Saint's"), ('정복자의', "Conqueror's"), ('사냥의 달인', "Master Hunter's"), ('성기사의', "Paladin's")],
}

LEGEND_EPITHETS = [
    ('태양왕', 'the Sun King', 'sun', 'gold', 'sun'), ('서리군주', 'the Frost Lord', 'frost', 'blue', 'frost'),
    ('폭풍의 기수', 'the Storm Rider', 'storm', 'blue', 'sapphire'), ('불사조', 'the Phoenix', 'flame', 'cloth', 'topaz'),
    ('늑대왕', 'the Wolf King', 'silver', 'leather', 'diamond'), ('심연의 감시자', 'the Abyss Warden', 'abyss', 'dark', 'sapphire'),
    ('별을 쫓는 자', 'the Starchaser', 'stariron', 'blue', 'diamond'), ('피의 백작', 'the Blood Count', 'blood', 'dark', 'blood'),
    ('황혼의 기사', 'the Twilight Knight', 'darksteel', 'violet', 'amethyst'), ('거인 학살자', 'the Giantslayer', 'steel', 'leather', 'ruby'),
    ('바다의 왕', 'the Sea King', 'abyss', 'blue', 'pearl'), ('사막의 군주', 'the Desert Lord', 'gold', 'cloth', 'topaz'),
    ('천둥의 사자', 'the Thunder Herald', 'storm', 'gold', 'topaz'), ('망각의 현자', 'the Sage of Oblivion', 'void', 'violet', 'void'),
    ('검은 용', 'the Black Dragon', 'obsidian', 'dark', 'ruby'), ('백은의 성녀', 'the Silver Saint', 'holy', 'white', 'pearl'),
    ('서풍의 사냥꾼', 'the Westwind Hunter', 'verdant', 'leather', 'emerald'), ('잿빛 방랑자', 'the Ashen Wanderer', 'flint', 'dark', 'onyx'),
    ('첫 번째 기사', 'the First Knight', 'silver', 'blue', 'sapphire'), ('꿈을 먹는 자', 'the Dreameater', 'amethyst', 'violet', 'amethyst'),
    ('철의 여왕', 'the Iron Queen', 'iron', 'cloth', 'ruby'), ('마지막 수호자', 'the Last Guardian', 'adamant', 'gold', 'emerald'),
    ('영원한 불꽃', 'the Eternal Flame', 'flame', 'gold', 'sun'), ('달그림자', 'the Moonshadow', 'moon', 'dark', 'diamond'),
    ('혹한의 마녀', 'the Winter Witch', 'frost', 'violet', 'frost'),
]

# 신화: (한국어, 영어, 종류, 재료, 손잡이, 보석)
MYTHIC_WEAPONS = [
    ('엑스칼리버', 'Excalibur', 'sword', 'holy', 'blue', 'sapphire'), ('뒤랑달', 'Durandal', 'sword', 'silver', 'gold', 'ruby'),
    ('쿠사나기', 'Kusanagi', 'katana', 'storm', 'dark', 'emerald'), ('레바테인', 'Laevateinn', 'sword', 'flame', 'dark', 'sun'),
    ('그람', 'Gram', 'greatsword', 'steel', 'gold', 'topaz'), ('클라우 솔라스', 'Claiomh Solais', 'sword', 'sun', 'white', 'sun'),
    ('아론다이트', 'Arondight', 'sword', 'moon', 'blue', 'diamond'), ('프라가라흐', 'Fragarach', 'sword', 'abyss', 'blue', 'pearl'),
    ('티르빙', 'Tyrfing', 'sword', 'blood', 'gold', 'blood'), ('다인슬레이프', 'Dainsleif', 'greatsword', 'obsidian', 'dark', 'blood'),
    ('무라마사', 'Muramasa', 'katana', 'blood', 'dark', 'blood'), ('마사무네', 'Masamune', 'katana', 'silver', 'white', 'pearl'),
    ('칠지도', 'Seven-Branched Sword', 'sword', 'gold', 'cloth', 'topaz'), ('간장', 'Ganjiang', 'sword', 'darksteel', 'dark', 'onyx'),
    ('막야', 'Moye', 'sword', 'rose', 'white', 'pearl'), ('조이외즈', 'Joyeuse', 'sword', 'sun', 'gold', 'diamond'),
    ('흐룬팅', 'Hrunting', 'greatsword', 'abyss', 'leather', 'sapphire'), ('궁니르', 'Gungnir', 'spear', 'gold', 'gold', 'sun'),
    ('게 볼그', 'Gae Bolg', 'spear', 'blood', 'cloth', 'blood'), ('브리오낙', 'Brionac', 'spear', 'sun', 'white', 'topaz'),
    ('롱기누스', 'Longinus', 'spear', 'holy', 'white', 'pearl'), ('방천화극', 'Sky Piercer', 'spear', 'flame', 'cloth', 'ruby'),
    ('트라이아이나', 'Triaina', 'spear', 'abyss', 'blue', 'pearl'), ('묠니르', 'Mjolnir', 'hammer', 'storm', 'leather', 'sapphire'),
    ('라브리스', 'Labrys', 'axe', 'bronze', 'cloth', 'ruby'), ('파라슈', 'Parashu', 'axe', 'sun', 'gold', 'sun'),
    ('여의봉', 'Ruyi Jingu Bang', 'staff', 'gold', 'cloth', 'ruby'), ('케뤼케이온', 'Caduceus', 'staff', 'gold', 'white', 'emerald'),
    ('아스클레피오스의 지팡이', 'Rod of Asclepius', 'staff', 'verdant', 'wood', 'emerald'), ('바즈라', 'Vajra', 'mace', 'gold', 'gold', 'diamond'),
    ('헤라클레스의 곤봉', 'Club of Heracles', 'mace', 'wood', 'leather', 'topaz'), ('크로노스의 낫', 'Scythe of Kronos', 'scythe', 'void', 'dark', 'void'),
    ('하르페', 'Harpe', 'scythe', 'bronze', 'dark', 'emerald'), ('카른웬한', 'Carnwennan', 'dagger', 'shadow', 'dark', 'onyx'),
    ('미스틸테인', 'Mistilteinn', 'wand', 'verdant', 'wood', 'emerald'), ('세계수의 가지', 'Branch of Yggdrasil', 'staff', 'verdant', 'wood', 'emerald'),
    ('태초의 불꽃', 'Primordial Flame', 'wand', 'flame', 'gold', 'sun'), ('바람의 신 아이올로스', 'Aeolus', 'rapier', 'storm', 'white', 'diamond'),
    ('아테나의 창', 'Spear of Athena', 'spear', 'silver', 'blue', 'sapphire'), ('아르테미스의 은월', 'Silver Moon of Artemis', 'katana', 'moon', 'white', 'diamond'),
    ('헤파이스토스의 망치', 'Hammer of Hephaestus', 'hammer', 'flame', 'leather', 'topaz'), ('하데스의 쌍지창', 'Bident of Hades', 'spear', 'obsidian', 'dark', 'void'),
    ('토르의 분노', 'Wrath of Thor', 'axe', 'storm', 'leather', 'sapphire'), ('오딘의 지혜', 'Wisdom of Odin', 'staff', 'mana', 'dark', 'sapphire'),
    ('로키의 속임수', "Loki's Trick", 'dagger', 'jade', 'violet', 'emerald'), ('프레이의 승리검', 'Victory Sword of Freyr', 'sword', 'verdant', 'gold', 'emerald'),
    ('수르트의 불검', 'Flame Sword of Surtr', 'greatsword', 'flame', 'dark', 'sun'), ('니드호그의 송곳니', 'Fang of Nidhogg', 'dagger', 'dragon', 'dark', 'blood'),
    ('메두사의 눈물', 'Tears of Medusa', 'wand', 'jade', 'violet', 'emerald'), ('니벨룽의 반지검', 'Ringblade of the Nibelung', 'rapier', 'gold', 'cloth', 'sun'),
]

SECRET_WEAPONS = [
    ('별을 꿰는 바늘', 'Starpiercer Needle', 'rapier', 'stariron', 'blue', 'diamond', '밤하늘의 별 하나하나를 실로 꿰어 만들었다는 바늘. 찌를 때마다 별빛이 흩어진다.', 'A needle said to be threaded through every star of the night sky.'),
    ('잊혀진 이름', 'The Forgotten Name', 'sword', 'moon', 'white', 'pearl', '이 검의 이름을 기억하는 자는 아무도 없다. 검만이 자신을 기억한다.', 'No one remembers the name of this sword. Only the sword remembers.'),
    ('마지막 페이지', 'The Last Page', 'wand', 'holy', 'gold', 'sun', '세상의 마지막 페이지를 말아 만든 완드. 휘두를 때마다 무언가가 끝난다.', 'A wand rolled from the last page of the world.'),
    ('시간을 베는 자', 'Timecleaver', 'katana', 'void', 'dark', 'void', '베인 자는 1초 전의 자신을 잃는다.', 'Those it cuts lose the second before.'),
    ('황금 까마귀', 'Golden Crow', 'spear', 'sun', 'gold', 'sun', '태양 속에 산다는 세 발 까마귀의 깃으로 만든 창.', 'A spear made from the feather of the three-legged crow that lives in the sun.'),
    ('검은 태양', 'Black Sun', 'hammer', 'obsidian', 'dark', 'sun', '빛을 삼키는 태양을 망치 머리에 가두었다.', 'A sun that swallows light, trapped in a hammer head.'),
    ('꿈의 열쇠', 'Key of Dreams', 'staff', 'rose', 'white', 'amethyst', '잠든 자의 꿈으로 들어가는 문을 연다.', 'Opens the door into the dreams of the sleeping.'),
    ('고요한 종말', 'Silent End', 'scythe', 'abyss', 'dark', 'frost', '소리 없이 다가와 소리 없이 거둔다.', 'It comes without a sound and reaps without a sound.'),
    ('피닉스의 깃', 'Phoenix Quill', 'dagger', 'flame', 'gold', 'sun', '타오르고, 재가 되고, 다시 타오른다.', 'It burns, turns to ash, and burns again.'),
    ('천 개의 별자리', 'Thousand Constellations', 'greatsword', 'stariron', 'violet', 'diamond', '칼날에 천 개의 별자리가 새겨져 있고, 밤마다 모양이 바뀐다.', 'A thousand constellations are etched on the blade, rearranging every night.'),
]

# ? 등급: 정체를 알 수 없는 무기 (이름은 게임 안에서 깨진 글자로 보임)
UNKNOWN_WEAPONS = [
    ('sword', 'chaos'), ('greatsword', 'void'), ('dagger', 'chaos'), ('axe', 'obsidian'), ('spear', 'chaos'),
    ('scythe', 'void'), ('katana', 'chaos'), ('hammer', 'obsidian'), ('staff', 'chaos'), ('wand', 'void'),
]

# ---------------------------------------------------------------- 무기 특수 효과 (영웅 이상)
# 키: (한국어 이름, 영어 이름, 설명 한국어 형식, 설명 영어 형식)
EFFECTS = {
    'ignite': ('화염', 'Ignite', '공격 시 {c}% 확률로 {s}초 동안 불태움', '{c}% chance on hit to set the target ablaze for {s}s'),
    'frost': ('서리', 'Frost', '공격 시 {c}% 확률로 {s}초 동안 느리게 함', '{c}% chance on hit to slow the target for {s}s'),
    'lightning': ('번개', 'Lightning', '공격 시 {c}% 확률로 번개가 떨어져 {p} 추가 피해', '{c}% chance on hit to call lightning for {p} extra damage'),
    'bleed': ('출혈', 'Bleed', '공격 시 {c}% 확률로 {s}초 동안 출혈', '{c}% chance on hit to cause bleeding for {s}s'),
    'poison': ('맹독', 'Venom', '공격 시 {c}% 확률로 {s}초 동안 중독', '{c}% chance on hit to poison the target for {s}s'),
    'wither': ('쇠약', 'Wither', '공격 시 {c}% 확률로 {s}초 동안 시들게 함', '{c}% chance on hit to wither the target for {s}s'),
    'vampire': ('흡혈', 'Vampirism', '준 피해의 {p}%만큼 체력 회복', 'Heal for {p}% of damage dealt'),
    'holy': ('신성', 'Holy', '공격 시 {c}% 확률로 주변 아군 체력 {p} 회복', '{c}% chance on hit to heal nearby allies for {p}'),
    'execute': ('처형', 'Execute', '체력 30% 이하인 적에게 피해 +{p}%', '+{p}% damage to enemies below 30% health'),
    'chain': ('연쇄', 'Chain', '공격 시 {c}% 확률로 주변 적 하나에게 {p}% 피해가 튐', '{c}% chance on hit to arc {p}% damage to a nearby enemy'),
    'explode': ('폭발', 'Burst', '공격 시 {c}% 확률로 폭발해 주변에 {p} 피해', '{c}% chance on hit to explode for {p} damage around the target'),
    'void': ('공허', 'Void', '공격 시 {c}% 확률로 적을 끌어당기고 {s}초 동안 어둠', '{c}% chance on hit to pull the target in and blind it for {s}s'),
    'haste': ('가속', 'Haste', '공격 시 {c}% 확률로 모든 스킬 재사용 대기시간 {s}초 감소', '{c}% chance on hit to reduce all skill cooldowns by {s}s'),
    'berserk': ('광폭', 'Berserk', '공격 시 {c}% 확률로 {s}초 동안 힘', '{c}% chance on hit to gain Strength for {s}s'),
}
EFFECT_KEYS = list(EFFECTS)
# 재료 → 어울리는 효과
MAT_EFFECT = {
    'flame': 'ignite', 'sun': 'ignite', 'dragon': 'ignite', 'frost': 'frost', 'moon': 'frost', 'storm': 'lightning', 'blood': 'bleed',
    'jade': 'poison', 'verdant': 'poison', 'shadow': 'wither', 'obsidian': 'wither', 'holy': 'holy', 'void': 'void', 'abyss': 'void',
    'mana': 'haste', 'stariron': 'haste', 'adamant': 'berserk', 'steel': 'execute', 'silver': 'execute', 'gold': 'explode',
    'darksteel': 'chain', 'amethyst': 'chain', 'rose': 'vampire', 'bronze': 'berserk', 'iron': 'execute', 'crystal': 'chain',
    'wood': 'berserk', 'mithril': 'haste', 'chaos': 'chain', 'flint': 'explode', 'lapis': 'haste',
}

# ---------------------------------------------------------------- 능력치 줄 (키: 한국어, 영어, 단위 %)
STATS = {
    'attack_damage': ('공격력', 'Attack Damage', False),
    'attack_speed': ('공격 속도', 'Attack Speed', False),
    'ability_damage': ('능력 피해', 'Ability Damage', True),
    'crit_chance': ('치명타 확률', 'Critical Chance', True),
    'crit_damage': ('치명타 피해', 'Critical Damage', True),
    'max_health': ('최대 체력', 'Max Health', False),
    'move_speed': ('이동 속도', 'Movement Speed', True),
    'cooldown': ('재사용 대기시간 감소', 'Cooldown Reduction', True),
    'lifesteal': ('흡혈', 'Lifesteal', True),
    'ult_charge': ('궁극기 충전', 'Ultimate Charge', True),
    'armor': ('방어력', 'Armor', False),
    'toughness': ('방어 강도', 'Armor Toughness', False),
    'knockback_resistance': ('밀쳐내기 저항', 'Knockback Resistance', True),
    'damage_reduction': ('받는 피해 감소', 'Damage Reduction', True),
    'luck': ('행운', 'Luck', False),
}

# ---------------------------------------------------------------- 갑옷
PIECES = [('helmet', '투구', 'Helmet'), ('chestplate', '흉갑', 'Chestplate'), ('leggings', '각반', 'Leggings'), ('boots', '장화', 'Boots')]
# 스타일마다 부위 이름이 다름 (로브는 두건/로브/바지/신발)
PIECE_NAMES = {
    'robe': [('두건', 'Hood'), ('로브', 'Robe'), ('바지', 'Trousers'), ('신발', 'Shoes')],
    'leather': [('모자', 'Cap'), ('조끼', 'Tunic'), ('바지', 'Pants'), ('장화', 'Boots')],
    'assassin': [('복면', 'Mask'), ('경갑', 'Jerkin'), ('바지', 'Leggings'), ('신발', 'Treads')],
}

# (한국어, 영어, 스타일, 재료 색, 장식 색)
A_MATS = {
    'common': [('가죽', 'Leather', 'leather', 'wood', 'bronze'), ('천', 'Cloth', 'robe', 'bone', 'copper'), ('누빈 천', 'Padded', 'robe', 'stone', 'bronze'),
               ('생가죽', 'Rawhide', 'leather', 'tempered', 'wood'), ('구리', 'Copper', 'plate', 'copper', 'bronze'), ('뼈', 'Bone', 'bone', 'bone', 'wood'),
               ('털가죽', 'Fur', 'leather', 'bone', 'wood'), ('녹슨 사슬', 'Rusty Chain', 'chain', 'rusty', 'wood'), ('나무껍질', 'Bark', 'scale', 'wood', 'thorn'),
               ('청동', 'Bronze', 'plate', 'bronze', 'copper')],
    'good': [('사슬', 'Chainmail', 'chain', 'iron', 'leather'), ('철', 'Iron', 'plate', 'iron', 'stone'), ('강철', 'Steel', 'plate', 'steel', 'iron'),
             ('은', 'Silver', 'plate', 'silver', 'gold'), ('강화 가죽', 'Hardened Leather', 'leather', 'ebony', 'iron'), ('미늘', 'Lamellar', 'scale', 'steel', 'cloth'),
             ('비단', 'Silk', 'robe', 'rose', 'gold'), ('청동 비늘', 'Bronze Scale', 'scale', 'bronze', 'gold'), ('흑철', 'Black Iron', 'plate', 'blackiron', 'blood'),
             ('순찰대', 'Patrol', 'leather', 'verdant', 'iron')],
    'rare': [('미스릴', 'Mithril', 'plate', 'mithril', 'silver'), ('흑강', 'Darksteel', 'plate', 'darksteel', 'amethyst'), ('수정', 'Crystal', 'crystal', 'crystal', 'silver'),
             ('룬 새김', 'Runed', 'plate', 'rune', 'mana'), ('비취', 'Jade', 'scale', 'jade', 'gold'), ('마력 비단', 'Arcane Silk', 'robe', 'mana', 'gold'),
             ('별철', 'Stariron', 'plate', 'stariron', 'gold'), ('자수정', 'Amethyst', 'crystal', 'amethyst', 'silver'), ('정령 가죽', 'Spirithide', 'leather', 'adamant', 'silver'),
             ('서리늑대', 'Frostwolf', 'leather', 'frost', 'silver')],
    'hero': [('아다만트', 'Adamant', 'knight', 'adamant', 'gold'), ('용비늘', 'Dragonscale', 'dragon', 'dragon', 'gold'), ('성은', 'Hallowed', 'angel', 'holy', 'gold'),
             ('서리수정', 'Frostcrystal', 'crystal', 'frost', 'silver'), ('그림자', 'Shadow', 'assassin', 'shadow', 'blood')],
}
A_PREFIX = W_PREFIX
LEGEND_ARMOR_STYLE = ['knight', 'dragon', 'angel', 'demon', 'assassin', 'robe', 'plate', 'crystal']

MYTHIC_ARMORS = [
    ('아이기스', 'Aegis', 'angel', 'holy', 'gold'), ('아킬레우스', 'Achilles', 'knight', 'bronze', 'gold'), ('네메아의 사자', 'Nemean Lion', 'leather', 'gold', 'bronze'),
    ('발키리', 'Valkyrie', 'angel', 'silver', 'gold'), ('니벨룽', 'Nibelung', 'knight', 'gold', 'blood'), ('오딘', 'Odin', 'robe', 'mana', 'gold'),
    ('토르', 'Thor', 'knight', 'storm', 'gold'), ('파프니르', 'Fafnir', 'dragon', 'gold', 'blood'), ('니드호그', 'Nidhogg', 'dragon', 'obsidian', 'blood'),
    ('요르문간드', 'Jormungandr', 'dragon', 'abyss', 'jade'), ('펜리르', 'Fenrir', 'leather', 'moon', 'shadow'), ('수르트', 'Surtr', 'demon', 'flame', 'obsidian'),
    ('헬', 'Hel', 'demon', 'moon', 'obsidian'), ('아테나', 'Athena', 'knight', 'silver', 'gold'), ('아레스', 'Ares', 'knight', 'blood', 'gold'),
    ('하데스', 'Hades', 'demon', 'obsidian', 'void'), ('포세이돈', 'Poseidon', 'scale', 'abyss', 'gold'), ('아폴론', 'Apollo', 'angel', 'sun', 'gold'),
    ('아르테미스', 'Artemis', 'leather', 'moon', 'silver'), ('헤르메스', 'Hermes', 'assassin', 'gold', 'silver'), ('헤라클레스', 'Heracles', 'leather', 'gold', 'bronze'),
    ('페르세우스', 'Perseus', 'knight', 'bronze', 'jade'), ('이카로스', 'Icarus', 'angel', 'sun', 'bronze'), ('메두사', 'Medusa', 'scale', 'jade', 'gold'),
    ('키메라', 'Chimera', 'demon', 'flame', 'jade'), ('그리폰', 'Griffin', 'angel', 'gold', 'bronze'), ('불사조', 'Phoenix', 'angel', 'flame', 'sun'),
    ('청룡', 'Azure Dragon', 'dragon', 'lapis', 'gold'), ('백호', 'White Tiger', 'leather', 'whiteiron', 'shadow'), ('주작', 'Vermilion Bird', 'angel', 'flame', 'gold'),
    ('현무', 'Black Tortoise', 'scale', 'obsidian', 'jade'), ('기린', 'Qilin', 'scale', 'gold', 'jade'), ('손오공', 'Sun Wukong', 'knight', 'gold', 'blood'),
    ('관우', 'Guan Yu', 'knight', 'jade', 'gold'), ('아서 왕', 'King Arthur', 'knight', 'silver', 'lapis'), ('랜슬롯', 'Lancelot', 'knight', 'moon', 'lapis'),
    ('멀린', 'Merlin', 'robe', 'mana', 'silver'), ('모르건', 'Morgan', 'robe', 'void', 'silver'), ('지크프리트', 'Siegfried', 'knight', 'steel', 'dragon'),
    ('베오울프', 'Beowulf', 'leather', 'bronze', 'iron'), ('쿠 훌린', 'Cu Chulainn', 'assassin', 'blood', 'gold'), ('잔 다르크', 'Jeanne', 'angel', 'silver', 'lapis'),
    ('길가메시', 'Gilgamesh', 'knight', 'gold', 'lapis'), ('엔키두', 'Enkidu', 'leather', 'verdant', 'gold'), ('이자나기', 'Izanagi', 'robe', 'holy', 'blood'),
    ('스사노오', 'Susanoo', 'assassin', 'storm', 'blood'), ('아마테라스', 'Amaterasu', 'angel', 'sun', 'blood'), ('라', 'Ra', 'angel', 'sun', 'lapis'),
    ('아누비스', 'Anubis', 'assassin', 'obsidian', 'gold'), ('세트', 'Set', 'demon', 'blood', 'gold'),
]

SECRET_ARMORS = [
    ('별을 입은 자', 'Starclad', 'crystal', 'stariron', 'diamond', '밤하늘을 재단해 지은 갑옷. 입으면 별자리가 몸을 따라 흐른다.', 'Armor tailored from the night sky. Constellations flow across the wearer.'),
    ('잊혀진 왕', 'The Forgotten King', 'knight', 'moon', 'gold', '이름도 나라도 사라졌지만 갑옷만은 왕을 기억한다.', 'His name and kingdom are gone, yet the armor still remembers its king.'),
    ('마지막 장', 'The Final Chapter', 'robe', 'holy', 'gold', '이야기의 마지막 장에서만 입을 수 있다는 로브.', 'A robe said to be worn only in the final chapter of a story.'),
    ('시간의 틈', 'Rift of Time', 'assassin', 'void', 'frost', '입은 자의 그림자는 언제나 한 박자 늦게 따라온다.', "The wearer's shadow always follows one beat behind."),
    ('황금 까마귀', 'Golden Crow', 'angel', 'sun', 'sun', '태양 속 세 발 까마귀의 깃털로 엮었다.', 'Woven from the feathers of the three-legged crow in the sun.'),
    ('검은 태양', 'Black Sun', 'demon', 'obsidian', 'sun', '빛을 삼키는 태양의 갑주. 가까이 가면 그림자가 사라진다.', 'Armor of the light-swallowing sun. Shadows vanish near it.'),
    ('꿈결', 'Dreamweave', 'robe', 'rose', 'amethyst', '잠든 자의 꿈을 실로 자아 지은 옷.', 'Clothes spun from the dreams of the sleeping.'),
    ('고요', 'Stillness', 'plate', 'abyss', 'frost', '이 갑옷을 입으면 발소리조차 사라진다.', 'In this armor even footsteps fall silent.'),
    ('재의 깃', 'Ashen Plume', 'angel', 'flame', 'sun', '타올라 재가 되었다가 다시 깃털이 된다.', 'It burns to ash and becomes feathers again.'),
    ('천 개의 별자리', 'Thousand Constellations', 'knight', 'stariron', 'diamond', '갑옷에 새긴 별자리가 밤마다 자리를 바꾼다.', 'The constellations etched on it rearrange every night.'),
]
UNKNOWN_ARMORS = ['chaos', 'void', 'obsidian', 'chaos', 'void', 'chaos', 'obsidian', 'void', 'chaos', 'chaos']
UNKNOWN_STYLES = ['knight', 'demon', 'crystal', 'assassin', 'robe', 'plate', 'dragon', 'angel', 'scale', 'chain']

# 세트 효과 (전설 이상): (한국어, 영어, 설명)
SET_EFFECTS = {
    'regen': ('재생', 'Regeneration', '착용하는 동안 계속 재생', 'Constant Regeneration while worn'),
    'fire': ('화염 저항', 'Fireproof', '불과 용암에 피해를 받지 않음', 'Immune to fire and lava'),
    'speed': ('신속', 'Swiftness', '이동 속도 증가', 'Increased movement speed'),
    'thorns': ('가시', 'Thorns', '근접 공격자에게 받은 피해의 30% 반사', 'Reflect 30% of melee damage taken'),
    'guardian': ('수호', 'Guardian', '30초마다 흡수 보호막', 'Gain an absorption shield every 30s'),
    'night': ('밤눈', 'Night Eye', '야간 투시', 'Night vision'),
    'water': ('물의 숨결', 'Waterbreath', '물속에서 숨쉬기 + 빠른 수영', 'Water breathing and faster swimming'),
    'jump': ('도약', 'Leap', '점프력 증가 + 낙하 피해 감소', 'Higher jumps, less fall damage'),
    'resist': ('강철 의지', 'Iron Will', '저항', 'Resistance'),
    'aura': ('치유의 오라', 'Healing Aura', '주변 아군을 천천히 회복', 'Slowly heals nearby allies'),
}
STYLE_SET_EFFECT = {
    'knight': 'resist', 'dragon': 'fire', 'angel': 'aura', 'demon': 'thorns', 'assassin': 'speed', 'robe': 'regen', 'plate': 'guardian',
    'crystal': 'night', 'scale': 'water', 'leather': 'jump', 'chain': 'resist', 'bone': 'thorns',
}


# ---------------------------------------------------------------- 아르반티아 인피니티 (2.9): 무기 종류마다 하나 · 갑옷 모양마다 하나
# (한국어, 영어, 종류, 재료, 손잡이, 보석, 설명 한국어, 설명 영어)
ARVANTIA_WEAPONS = [
    ('아르반티아 · 영원의 검', 'Arvantia: Blade of Eternity', 'sword', 'arvantia', 'gold', 'infinity', '끝나지 않는 이야기의 첫 줄을 벼려 만든 검.', 'A blade forged from the first line of a story that never ends.'),
    ('아르반티아 · 무한의 대검', 'Arvantia: Greatsword of the Infinite', 'greatsword', 'infinity', 'gold', 'prism', '아무리 휘둘러도 끝이 닿지 않는 대검.', 'A greatsword whose edge never reaches its end.'),
    ('아르반티아 · 찰나의 단검', 'Arvantia: Dagger of the Instant', 'dagger', 'arvantia', 'violet', 'prism', '찌른 순간이 영원히 남는다.', 'The instant it strikes remains forever.'),
    ('아르반티아 · 종말의 도끼', 'Arvantia: Axe of the End', 'axe', 'infinity', 'dark', 'infinity', '세계의 끝에서 주워 온 도끼.', 'An axe picked up at the end of the world.'),
    ('아르반티아 · 창세의 망치', 'Arvantia: Hammer of Genesis', 'hammer', 'arvantia', 'gold', 'sun', '세상을 처음 두드려 모양을 만든 망치.', 'The hammer that first beat the world into shape.'),
    ('아르반티아 · 천공의 창', 'Arvantia: Spear of the Firmament', 'spear', 'infinity', 'white', 'prism', '하늘의 덮개를 꿰뚫는 창.', 'A spear that pierces the vault of the sky.'),
    ('아르반티아 · 윤회의 낫', 'Arvantia: Scythe of Rebirth', 'scythe', 'arvantia', 'violet', 'infinity', '베인 것은 다시 태어난다. 더 약하게.', 'What it reaps is reborn. Weaker.'),
    ('아르반티아 · 여명의 태도', 'Arvantia: Katana of Dawn', 'katana', 'infinity', 'white', 'sun', '밤을 베어 아침을 여는 칼.', 'A blade that cuts the night to open the morning.'),
    ('아르반티아 · 별무리 레이피어', 'Arvantia: Rapier of the Starfield', 'rapier', 'arvantia', 'blue', 'diamond', '별 사이를 꿰매는 가는 칼끝.', 'A fine point that stitches between the stars.'),
    ('아르반티아 · 심판의 철퇴', 'Arvantia: Mace of Judgment', 'mace', 'infinity', 'gold', 'infinity', '마지막 심판의 날 울릴 철퇴.', 'The mace that will toll on the day of final judgment.'),
    ('아르반티아 · 근원의 지팡이', 'Arvantia: Staff of the Origin', 'staff', 'arvantia', 'white', 'prism', '모든 마법이 시작된 곳을 가리키는 지팡이.', 'A staff that points to where all magic began.'),
    ('아르반티아 · 꿈결의 완드', 'Arvantia: Wand of Dreams', 'wand', 'infinity', 'violet', 'infinity', '꿈을 현실로, 현실을 꿈으로.', 'Dreams into reality, reality into dreams.'),
]
# (한국어, 영어, 모양, 재료, 보석, 설명 한국어, 설명 영어)
ARVANTIA_ARMORS = [
    ('아르반티아 · 영원의 기사', 'Arvantia: Eternal Knight', 'knight', 'arvantia', 'infinity', '쓰러지지 않는 기사의 맹세가 깃든 갑주.', 'Armor sworn with the oath of a knight who never falls.'),
    ('아르반티아 · 무한의 용', 'Arvantia: Infinite Dragon', 'dragon', 'infinity', 'prism', '용왕의 역린으로 지은 비늘 갑옷.', "Scale armor built from the Dragon King's reversed scales."),
    ('아르반티아 · 천상의 날개', 'Arvantia: Celestial Wings', 'angel', 'arvantia', 'sun', '입으면 등 뒤에서 빛이 펼쳐진다.', "Light unfolds behind the wearer's back."),
    ('아르반티아 · 종말의 군주', 'Arvantia: Lord of the End', 'demon', 'infinity', 'infinity', '마지막 날의 왕이 입는 갑주.', 'Armor of the king of the last day.'),
    ('아르반티아 · 찰나의 그림자', 'Arvantia: Shadow of the Instant', 'assassin', 'arvantia', 'prism', '눈을 깜빡이는 사이에 사라진다.', 'Gone in the blink of an eye.'),
    ('아르반티아 · 근원의 법의', 'Arvantia: Vestment of the Origin', 'robe', 'infinity', 'prism', '마법의 근원에서 짠 법의.', 'Vestments woven at the source of magic.'),
    ('아르반티아 · 창세의 판금', 'Arvantia: Plate of Genesis', 'plate', 'arvantia', 'sun', '세상이 생길 때의 첫 쇠로 두드린 판금.', 'Plate beaten from the first iron of creation.'),
    ('아르반티아 · 별무리 수정', 'Arvantia: Starfield Crystal', 'crystal', 'infinity', 'diamond', '별무리를 통째로 굳혀 깎았다.', 'Carved from an entire starfield, frozen solid.'),
    ('아르반티아 · 심해의 비늘', 'Arvantia: Abyssal Scale', 'scale', 'arvantia', 'frost', '인어의 눈물로 씻어 낸 비늘.', "Scales washed in mermaids' tears."),
    ('아르반티아 · 바람의 가죽', 'Arvantia: Windborne', 'leather', 'infinity', 'prism', '입으면 발이 땅에 닿지 않는다.', "The wearer's feet never quite touch the ground."),
    ('아르반티아 · 윤회의 사슬', 'Arvantia: Chains of Rebirth', 'chain', 'arvantia', 'infinity', '끊어져도 다시 이어지는 사슬.', 'Chains that join again even when broken.'),
    ('아르반티아 · 불멸의 뼈', 'Arvantia: Undying Bone', 'bone', 'infinity', 'void', '죽지 않는 자의 뼈를 깎아 만들었다.', 'Carved from the bones of one who cannot die.'),
]
