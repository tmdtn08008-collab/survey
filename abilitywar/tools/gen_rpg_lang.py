#!/usr/bin/env python3
"""RPG (레벨·스탯·전직 퀘스트·직업 트리) + 1차 직업 + 수호기사 언어 파일 항목을 ko_kr/en_us 에 써 넣음.
사용: python3 tools/gen_rpg_lang.py"""
import json, os

ROOT = os.path.join(os.path.dirname(__file__), '..')
LANG = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'abilitywar', 'lang')

E = {}  # key -> (ko, en)


def t(key, ko, en):
    E[key] = (ko, en)


# ---------------------------------------------------------------- 키
t('key.abilitywar.stats', '능력치 창', 'Stats')
t('key.abilitywar.quests', '퀘스트 창', 'Quests')
t('key.abilitywar.select', '직업 트리', 'Class Tree')

# ---------------------------------------------------------------- 엔티티 / 아이템
t('entity.abilitywar.job_master', '전직 교관', 'Class Master')
t('item.abilitywar.job_master_spawn_egg', '전직 교관 생성 알', 'Class Master Spawn Egg')

# ---------------------------------------------------------------- 직업 (능력)
SK = ['skill1', 'skill2', 'skill3', 'skill4', 'ultimate', 'passive']
LOCK_KO = ('잠김 (2차 전직)', '2차 전직을 하면 열립니다. 레벨을 올려 전직 교관을 찾아가세요.')
LOCK_EN = ('Locked (2nd class)', 'Unlocks after your 2nd class advancement. Level up and visit the Class Master.')


def ability(key, ko, en, desc_ko, desc_en, skills):
    t(f'ability.abilitywar.{key}', ko, en)
    t(f'ability.abilitywar.{key}.desc', desc_ko, desc_en)
    for slot, v in zip(SK, skills):
        if v is None:
            v = (LOCK_KO[0], LOCK_EN[0], LOCK_KO[1], LOCK_EN[1])
        nk, ne, dk, de = v
        t(f'abilitywar.skill.{key}.{slot}', nk, ne)
        t(f'abilitywar.skill.{key}.{slot}.desc', dk, de)


ability('warrior', '전사', 'Warrior',
        '1차 직업. 가까이 붙어 베고 버티는 근접 전투. 2차: 마검사 · 혈귀 · 용기사.',
        '1st class. Close-range fighter who cuts and endures. 2nd: Spellblade, Blood Fiend, Dragon Knight.',
        [('돌진 베기', 'Dash Slash', '앞으로 6칸 돌진하며 지나가는 적을 벤다 (피해 4). 재사용 8초.', 'Dash 6 blocks forward, cutting enemies in the way (4 dmg). 8s cooldown.'),
         ('회오리 베기', 'Whirlwind', '한 바퀴 돌며 주위 3.5칸을 벤다 (피해 5, 밀쳐냄). 재사용 10초.', 'Spin and cut everything within 3.5 blocks (5 dmg, knockback). 10s cooldown.'),
         ('전투의 함성', 'War Cry', '8칸 안 파티에 용맹 (8초, 주는 피해 +15%), 나는 저항. 재사용 20초.', 'Valor for party within 8 blocks (8s, +15% damage); Resistance for yourself. 20s cooldown.'),
         None, None,
         ('재정비', 'Second Wind', '숨을 고르며 최대 체력의 30% 회복 + 재생. 재사용 30초.', 'Catch your breath: heal 30% max health + Regeneration. 30s cooldown.')])
ability('mage', '마법사', 'Mage',
        '1차 직업. 멀리서 마법을 쏘는 원거리 딜러. 2차: 공허 · 흑마법사.',
        '1st class. Ranged caster. 2nd: Void, Warlock.',
        [('화염구', 'Fireball', '불덩이를 던져 맞은 곳 2.5칸에 폭발 (피해 6 + 불). 재사용 6초.', 'Hurl a fireball that explodes in 2.5 blocks (6 dmg + fire). 6s cooldown.'),
         ('서리 고리', 'Frost Ring', '땅을 내리쳐 주위 5칸을 얼린다 (피해 4 + 구속 II 3초). 재사용 12초.', 'Slam the ground and freeze everything within 5 blocks (4 dmg + Slowness II 3s). 12s cooldown.'),
         ('순간이동', 'Blink', '바라보는 쪽으로 8칸 순간이동. 재사용 8초.', 'Teleport 8 blocks where you look. 8s cooldown.'),
         None, None,
         ('마나 방패', 'Mana Shield', '10초 동안 마나 보호막 (흡수 II). 재사용 25초.', 'Mana barrier for 10s (Absorption II). 25s cooldown.')])
ability('tank', '탱커', 'Tank',
        '1차 직업. 적을 끌어모으고 막아내는 방패 전사. 2차: 수호기사.',
        '1st class. Shield fighter who draws and absorbs attacks. 2nd: Guardian.',
        [('도발', 'Taunt', '10칸 안의 몹이 나를 노린다. 다른 팀 플레이어는 불협화음 (주는 피해 -20%). 나는 저항 5초. 재사용 12초.', 'Mobs within 10 blocks target you; enemy players get Dissonance (-20% damage). Resistance for 5s. 12s cooldown.'),
         ('방패 돌격', 'Shield Charge', '방패를 앞세워 돌진, 부딪힌 적을 1초 기절. 재사용 9초.', 'Charge behind your shield; enemies hit are stunned for 1s. 9s cooldown.'),
         ('철벽', 'Iron Wall', '5초 동안 저항 II + 보호막 + 밀려나지 않음. 재사용 18초.', 'Resistance II + absorption + unstoppable for 5s. 18s cooldown.'),
         None, None,
         ('버티기', 'Endure', '최대 체력의 25% 회복 + 재생. 재사용 30초.', 'Heal 25% max health + Regeneration. 30s cooldown.')])
ability('healer', '힐러', 'Healer',
        '1차 직업. 같은 팀을 치유하고 돕는 지원가. 평타로 바라보는 아군을 치유한다. 2차: 천사 · 성가사.',
        '1st class. Support who heals teammates (basic attack heals the ally you look at). 2nd: Angel, Cantor.',
        [('치유', 'Heal', '바라보는 아군 (없으면 나) 체력 6 회복. 재사용 6초.', 'Heal the ally you look at (or yourself) for 6. 6s cooldown.'),
         ('정화', 'Purify', '5칸 안 파티의 나쁜 효과를 지우고 체력 3 회복. 재사용 12초.', 'Cleanse party within 5 blocks and heal 3. 12s cooldown.'),
         ('축복', 'Blessing', '6칸 안 파티에 수호 (8초, 받는 피해 감소). 재사용 16초.', 'Aegis for party within 6 blocks (8s, less damage taken). 16s cooldown.'),
         None, None,
         ('기도', 'Prayer', '6칸 안 파티에 재생 6초. 재사용 25초.', 'Regeneration for party within 6 blocks for 6s. 25s cooldown.')])
ability('guardian', '수호기사', 'Guardian',
        '탱커 2차 직업. 거대한 방패로 파티를 지킨다. 궁극기 게이지는 맞거나 막을수록 빨리 찬다.',
        'Tank 2nd class. Protects the party with a great shield. Ultimate charges fastest from damage taken or blocked.',
        [('도발의 함성', 'Rallying Roar', '12칸 안 적을 도발하고, 나는 저항 + 흡수 II. 재사용 12초.', 'Taunt enemies within 12 blocks; gain Resistance + Absorption II. 12s cooldown.'),
         ('방패 돌진', 'Shield Rush', '7칸 돌진하며 부딪힌 적을 방패 앞에 붙여 밀고 가다 1.5초 기절 (피해 5). 재사용 10초.', 'Rush 7 blocks pushing enemies on your shield, then stun them 1.5s (5 dmg). 10s cooldown.'),
         ('수호의 결계', 'Bulwark', '방패를 꽂아 6초 동안 반지름 5 결계: 안의 아군 받는 피해 -30%, 적은 느려짐. 재사용 22초.', 'Plant your shield: a radius-5 dome for 6s. Allies inside take 30% less damage, enemies are slowed. 22s cooldown.'),
         ('대지 강타', 'Earthshaker', '뛰어올라 방패로 내리찍어 반지름 4 피해 7 + 띄움 + 구속. 재사용 14초.', 'Leap and slam: 7 dmg in radius 4, knock up and slow. 14s cooldown.'),
         ('금성철벽', 'Fortress', '하늘에서 거대한 방패가 내려와 10초 동안 10칸 안 아군 받는 피해 -60%. 12칸 안의 적을 끌어당겨 도발.', 'A giant shield falls from the sky: allies within 10 blocks take 60% less damage for 10s. Enemies within 12 are pulled in and taunted.'),
         ('수호 태세', 'Guardian Stance', '켜고 끄기. 켜면 8칸 안 같은 팀이 받는 피해의 40% 를 대신 받는다. 나는 받는 피해 -25%, 이동 속도 -15%.', 'Toggle. While on, you take 40% of damage dealt to teammates within 8 blocks. You take 25% less damage but move 15% slower.')])
t('abilitywar.ult.guardian.title', '金城鐵壁', 'FORTRESS')
t('abilitywar.ult.guardian.subtitle', '금성철벽', 'Unbreakable Wall')

# ---------------------------------------------------------------- 단계 / 스탯
t('rpg.abilitywar.tier.1', '1차', '1st')
t('rpg.abilitywar.tier.2', '2차', '2nd')
t('rpg.abilitywar.tier.3', '3차', '3rd')
for k, ko, en, dko, den, eko, een in [
    ('str', '힘', 'Strength', '물리 공격 (전사·탱커 계열 평타·스킬, 맨손/무기 공격)이 세진다.', 'Stronger physical attacks (warrior/tank line, melee).', '물리 피해 +%s%% · 공격력 +%s', 'Physical +%s%% · Attack +%s'),
    ('dex', '민첩', 'Dexterity', '치명타가 자주 터지고 조금 빨라진다.', 'More critical hits and a bit more speed.', '치명타 +%s%% · 이동 속도 +%s%%', 'Crit +%s%% · Speed +%s%%'),
    ('int', '지능', 'Intelligence', '마법 공격 (마법사·힐러 계열 평타·스킬)이 세진다.', 'Stronger magic (mage/healer line).', '마법 피해 +%s%%', 'Magic +%s%%'),
    ('vit', '체력', 'Vitality', '최대 체력이 늘어난다.', 'More max health.', '최대 체력 +%s', 'Max health +%s'),
    ('wis', '지혜', 'Wisdom', '스킬을 더 자주 쓰고 회복량이 늘어난다.', 'Shorter cooldowns and stronger healing.', '재사용 대기 -%s%% · 회복량 +%s%%', 'Cooldown -%s%% · Healing +%s%%'),
]:
    t(f'rpg.abilitywar.stat.{k}', ko, en)
    t(f'rpg.abilitywar.stat.{k}.desc', dko, den)
    t(f'rpg.abilitywar.stat.{k}.eff', eko, een)

# ---------------------------------------------------------------- 메시지
t('rpg.abilitywar.xp_gain', '+%s 경험치', '+%s XP')
t('rpg.abilitywar.level_up', '레벨 업! 레벨 %s (스탯 포인트 +%s, [K] 로 찍기)', 'Level up! Level %s (+%s stat points, press [K])')
t('rpg.abilitywar.job_ready', '2차 전직을 할 수 있습니다! 전직 교관을 찾아가세요.', 'You can now take your 2nd class! Visit the Class Master.')
t('rpg.abilitywar.objective_done', '퀘스트 목표 완료!', 'Quest objective complete!')
t('rpg.abilitywar.quest_accepted', '퀘스트 수락: %s ([J] 로 확인)', 'Quest accepted: %s (press [J])')
t('rpg.abilitywar.quest_abandoned', '퀘스트를 포기했습니다.', 'Quest abandoned.')
t('rpg.abilitywar.advanced_title', '전직 성공!', 'Class Advanced!')
t('rpg.abilitywar.advanced_broadcast', '%s 님이 %s (으)로 전직했습니다!', '%s advanced to %s!')
t('rpg.abilitywar.locked_skill', '2차 전직을 하면 쓸 수 있습니다', 'Unlocks with your 2nd class')
t('rpg.abilitywar.npc_spawned', '전직 교관을 불렀습니다', 'Class Master summoned')
t('rpg.abilitywar.err.no_quest', '받은 퀘스트가 없습니다.', 'You have no quest.')
t('rpg.abilitywar.err.wrong_class', '이 퀘스트는 다른 1차 직업의 길입니다.', 'This quest belongs to another 1st class.')
t('rpg.abilitywar.err.already_advanced', '이미 2차 전직을 했습니다.', 'You have already advanced.')
t('rpg.abilitywar.err.level', '레벨 %s 부터 받을 수 있습니다.', 'Requires level %s.')
t('rpg.abilitywar.err.has_quest', '이미 진행 중인 전직 퀘스트가 있습니다.', 'You already have an advancement quest.')
t('rpg.abilitywar.err.not_done', '아직 목표를 다 채우지 못했습니다.', 'Objectives are not complete yet.')
t('rpg.abilitywar.err.far', '전직 교관에게 더 가까이 가세요.', 'Get closer to the Class Master.')

# ---------------------------------------------------------------- 퀘스트
Q = {
    'void': ('공허를 엿본 자', 'The One Who Gazed Into the Void', '엔더맨의 눈은 공허를 비춘다고 한다. 엔더맨을 쓰러뜨리고 엔더 진주를 모아 공허의 힘을 다룰 자격을 증명하라.',
             'They say the eyes of Endermen reflect the void. Defeat Endermen and gather ender pearls to prove you can wield it.'),
    'warlock': ('죽음과의 계약', 'Pact With Death', '죽은 자를 부리려면 먼저 죽음을 알아야 한다. 언데드를 쓰러뜨리고 뼈를 바쳐라.',
                'To command the dead, first know death. Slay the undead and offer their bones.'),
    'blade': ('검에 깃든 마력', 'Mana in the Blade', '수많은 싸움을 이겨낸 자만이 검에 마력을 담는다. 적대적인 몹을 쓰러뜨리고 검을 벼릴 철을 모아라.',
              'Only those who survive countless battles can bind mana to steel. Defeat hostile mobs and gather iron to forge your blade.'),
    'blood': ('피의 밤', 'Night of Blood', '혈귀의 힘은 밤에 깨어난다. 밤에 적대적인 몹을 사냥하고 거미 눈을 모아라.',
              'The fiend awakens at night. Hunt hostile mobs after dark and gather spider eyes.'),
    'dragon': ('용의 불꽃', 'Dragonfire', '용의 피를 깨우려면 불을 견뎌야 한다. 블레이즈를 쓰러뜨리고 블레이즈 막대를 모아라.',
               'To awaken dragon blood, endure the flame. Defeat Blazes and gather blaze rods.'),
    'guardian': ('무너지지 않는 벽', 'The Unbreaking Wall', '수호기사는 동료 대신 맞는다. 피해를 받거나 막아서 견뎌내고, 철 블록으로 방패를 만들 재료를 가져와라.',
                 'A Guardian takes the blows meant for others. Endure damage (taken or blocked) and bring iron blocks to forge your shield.'),
    'angel': ('날개를 얻는 길', 'Path to Wings', '천사는 수많은 생명을 구한 자에게 날개를 준다. 체력을 회복시키고 깃털을 모아라.',
              'Wings are granted to those who save many lives. Restore health and gather feathers.'),
    'cantor': ('첫 번째 성가', 'The First Hymn', '성가사의 노래는 동료를 강하게 한다. 같은 팀에게 버프를 걸고 소리 블록을 가져와라.',
               'A Cantor\'s song empowers allies. Buff teammates and bring note blocks.'),
}
for reward, (nk, ne, dk, de) in Q.items():
    t(f'rpg.abilitywar.quest.job_{reward}', nk, ne)
    t(f'rpg.abilitywar.quest.job_{reward}.desc', dk, de)
t('rpg.abilitywar.obj.kill', '%s 처치', 'Defeat %s')
t('rpg.abilitywar.obj.kill_night', '밤에 %s 처치', 'Defeat %s at night')
t('rpg.abilitywar.obj.item', '%s 모으기', 'Gather %s')
t('rpg.abilitywar.obj.heal', '아군 체력 회복', 'Restore health')
t('rpg.abilitywar.obj.buff', '같은 팀에게 버프', 'Buff teammates')
t('rpg.abilitywar.obj.tank', '피해 받기 / 막기', 'Take or block damage')
t('rpg.abilitywar.target.hostile', '적대적인 몹', 'hostile mobs')
t('rpg.abilitywar.target.undead', '언데드', 'undead')
for k, ko, en in [('available', '받을 수 있음', 'Available'), ('locked_level', '레벨 %s 필요', 'Requires Lv. %s'), ('active', '진행 중', 'In progress'),
                  ('ready', '완료 가능!', 'Ready to turn in!'), ('completed', '완료', 'Completed'), ('other_active', '다른 퀘스트 진행 중', 'Another quest active'),
                  ('advanced', '이미 전직함', 'Already advanced'), ('wrong_class', '다른 직업', 'Other class')]:
    t(f'rpg.abilitywar.status.{k}', ko, en)

# ---------------------------------------------------------------- HUD / 화면
t('rpg.abilitywar.hud.no_class', '직업 없음', 'No class')
t('rpg.abilitywar.hud.points', '+%s 스탯 포인트 [%s]', '+%s stat points [%s]')
t('rpg.abilitywar.hud.return', '▶ 전직 교관에게 돌아가세요', '▶ Return to the Class Master')
t('rpg.abilitywar.hud.level_up', 'LEVEL UP', 'LEVEL UP')
t('rpg.abilitywar.hud.level_now', '레벨 %s', 'Level %s')
t('rpg.abilitywar.screen.stats', '능력치', 'Stats')
t('rpg.abilitywar.screen.points', '남은 포인트: %s', 'Points left: %s')
t('rpg.abilitywar.screen.spent', '찍은 포인트 %s', '%s points spent')
t('rpg.abilitywar.screen.per_point', '1 포인트당: ', 'Per point: ')
t('rpg.abilitywar.screen.recommended', '★ 지금 직업에 추천', '★ Recommended for your class')
t('rpg.abilitywar.screen.stats_hint', '레벨 업마다 3 포인트 (10레벨마다 +2) · [%s] 닫기', '3 points per level (+2 every 10 levels) · [%s] to close')
t('rpg.abilitywar.screen.quests', '퀘스트', 'Quests')
t('rpg.abilitywar.screen.no_quests', '받을 수 있는 퀘스트가 없습니다.', 'No quests available.')
t('rpg.abilitywar.screen.requires', '조건', 'Requirements')
t('rpg.abilitywar.screen.req_level', '레벨 %s 이상', 'Level %s+')
t('rpg.abilitywar.screen.req_class', '1차 직업: %s', '1st class: %s')
t('rpg.abilitywar.screen.objectives', '목표', 'Objectives')
t('rpg.abilitywar.screen.rewards', '보상', 'Rewards')
t('rpg.abilitywar.screen.reward_class', '2차 직업: %s', '2nd class: %s')
t('rpg.abilitywar.screen.reward_xp', '경험치 %s', '%s XP')
t('rpg.abilitywar.screen.reward_gear', '희귀 등급 무기 1개', '1 Rare weapon')
t('rpg.abilitywar.screen.abandon', '퀘스트 포기', 'Abandon')
t('rpg.abilitywar.screen.abandon_confirm', '정말 포기?', 'Really?')
t('rpg.abilitywar.screen.tree', '직업 트리', 'Class Tree')
t('rpg.abilitywar.guide.no_class', '아직 직업이 없습니다.', 'You have no class yet.')
t('rpg.abilitywar.guide.advanced', '2차 전직 완료! 3차 전직은 준비 중입니다.', '2nd class done! 3rd class is coming later.')
t('rpg.abilitywar.guide.ready', '목표 완료! 전직 교관에게 돌아가 전직하세요.', 'Done! Return to the Class Master to advance.')
t('rpg.abilitywar.guide.progress', '전직 퀘스트 진행 중', 'Advancement quest in progress')
t('rpg.abilitywar.guide.level', '레벨 %s 이 되면 전직 교관에게서 전직 퀘스트를 받을 수 있습니다 (지금 %s)', 'Reach level %s to get an advancement quest from the Class Master (now %s)')
t('rpg.abilitywar.guide.visit', '전직 교관을 찾아가 퀘스트를 받으세요.', 'Visit the Class Master to get your quest.')
t('rpg.abilitywar.npc.accept', '퀘스트 수락', 'Accept')
t('rpg.abilitywar.npc.turn_in', '전직하기!', 'Advance!')
t('rpg.abilitywar.npc.close', '닫기', 'Close')
t('rpg.abilitywar.npc.say.no_class', '자네는 아직 길을 정하지 않았군. 먼저 직업을 고르고 오게.', 'You have not chosen a path yet. Pick a class first.')
t('rpg.abilitywar.npc.say.advanced', '%s의 길을 걷고 있군. 더 높은 경지는... 아직 때가 아니네.', 'You walk the path of the %s. The next step... is not yet ready.')
t('rpg.abilitywar.npc.say.ready', '오, 「%s」을(를) 해냈구나! 증표를 보여주게. 새로운 힘을 주지.', 'You finished "%s"! Show me the proof and I will grant you new power.')
t('rpg.abilitywar.npc.say.progress', '「%s」은(는) 아직 끝나지 않았네. 목표를 채우고 다시 오게.', '"%s" is not finished yet. Come back when you are done.')
t('rpg.abilitywar.npc.say.level', '아직 부족하네. 레벨 %s 이 되면 다시 찾아오게.', 'You are not ready. Come back at level %s.')
t('rpg.abilitywar.npc.say.choose.warrior', '전사여, 검의 끝에서 무엇을 찾나? 마력, 피, 아니면 용의 불꽃? 하나만 고를 수 있네.', 'Warrior, what do you seek at the edge of the blade? Mana, blood, or dragonfire? Choose one.')
t('rpg.abilitywar.npc.say.choose.mage', '마법사여, 깊은 마법은 두 갈래라네. 공허를 다룰 텐가, 죽음을 부릴 텐가?', 'Mage, deep magic splits in two. Will you wield the void, or command death?')
t('rpg.abilitywar.npc.say.choose.tank', '탱커여, 동료의 방패가 될 각오가 되었나? 수호기사의 길은 험하다네.', 'Tank, are you ready to be the shield of your allies? The Guardian\'s path is hard.')
t('rpg.abilitywar.npc.say.choose.healer', '힐러여, 날개를 얻어 하늘에서 구할 텐가, 노래로 동료를 강하게 할 텐가?', 'Healer, will you earn wings to save from the sky, or sing to empower allies?')
t('rpg.abilitywar.tree.sub', '1차 직업 → 전직 교관의 퀘스트 → 2차 직업 → (3차 준비 중)', '1st class → Class Master quest → 2nd class → (3rd coming soon)')
t('rpg.abilitywar.tree.all', '모든 능력 보기', 'All abilities')
t('rpg.abilitywar.tree.soon', '준비 중', 'Soon')
t('rpg.abilitywar.tree.third', '3차 직업', '3rd class')
t('rpg.abilitywar.tree.third_desc', '아직 준비 중입니다.', 'Not available yet.')
t('rpg.abilitywar.tree.base_info', '처음 접속할 때 고르는 1차 직업', 'Chosen when you first join')
t('rpg.abilitywar.tree.how', '레벨 %s + %s 전직 퀘스트:', 'Level %s + %s quest:')
t('rpg.abilitywar.tree.click', '눌러서 바로 바꾸기 (관리자)', 'Click to switch (admin)')
t('rpg.abilitywar.tree.hint', '전직은 전직 교관에게서 · [%s] 닫기', 'Advance at the Class Master · [%s] to close')
t('rpg.abilitywar.tree.hint_admin', '직업을 눌러 바로 바꿀 수 있습니다 · [%s] 닫기', 'Click a class to switch · [%s] to close')
t('abilitywar.screen.first_hint', '1차 직업을 고르세요. 레벨 30 에 전직 교관에게서 2차 직업으로 전직할 수 있습니다.', 'Choose your 1st class. At level 30 the Class Master can advance you to a 2nd class.')

# ================================================================ 3차 전직 ("대") · 왕 · 보스 (2.0)
GRAND = {'blade': ('대마검사', 'Grand Demonic Swordsman'), 'blood': ('대혈귀', 'Elder Bloodfiend'), 'dragon': ('대용기사', 'Grand Dragon Knight'),
         'void': ('대공허', 'Abyssal Void'), 'warlock': ('대흑마법사', 'Archwarlock'), 'guardian': ('대수호기사', 'Grand Guardian'),
         'angel': ('대천사', 'Archangel'), 'cantor': ('대성가사', 'High Cantor'), 'paladin': ('대성기사', 'Grand Paladin')}
for k, (ko, en) in GRAND.items():
    t(f'ability.abilitywar.{k}.grand', ko, en)
t('rpg.abilitywar.tier.3', '3차', '3rd')
t('rpg.abilitywar.grand_title', '3차 전직 성공!', '3rd Class Awakened!')
t('rpg.abilitywar.king_title', '왕의 기사가 되었다!', 'Knighted by the King!')
t('rpg.abilitywar.err.already_grand', '이미 3차 전직을 했습니다.', 'You have already awakened.')
t('rpg.abilitywar.err.other_giver', '이 퀘스트는 다른 사람에게 완료해야 합니다.', 'Turn this quest in to someone else.')
t('rpg.abilitywar.obj.boss', '보스 처치', 'Defeat a boss')
t('rpg.abilitywar.boss_down', '보스 %s 을(를) 쓰러뜨렸다!', 'Boss %s defeated!')
# Ultris: Boss Expansion (호환)
t('compat.abilitywar.ultris.ender', '타락한 엔더맨', 'Corrupted Enderman')
t('compat.abilitywar.ultris.blazeking', '블레이즈 왕', 'Blaze King')
t('compat.abilitywar.ultris.giant', '거인', 'Giant')
t('compat.abilitywar.ultris.illusioner', '환술사', 'Illusioner')
t('compat.abilitywar.ultris.keeper', '성소의 수호자', 'Sanctum Keeper')
t('compat.abilitywar.ultris.phantom', '팬텀 떼', 'Phantom Swarm')
t('compat.abilitywar.ultris.shulker', '셜커 돌', 'Shulker Stone')
t('compat.abilitywar.ultris.ultrawither', '울트라 위더', 'Ultra Wither')
t('rpg.abilitywar.screen.req_class2', '2차 직업: %s', '2nd class: %s')
t('rpg.abilitywar.screen.reward_grand', '3차 전직: %s', '3rd class: %s')
t('rpg.abilitywar.screen.reward_special', '특별 직업: %s', 'Special class: %s')
t('rpg.abilitywar.screen.reward_gear_job', '희귀 등급 무기 1개', '1 Rare weapon')
t('rpg.abilitywar.screen.reward_gear_grand', '전설 등급 무기 1개', '1 Legendary weapon')
t('rpg.abilitywar.screen.reward_gear_king', '영웅 등급 무기 1개', '1 Hero weapon')
t('rpg.abilitywar.guide.grand_done', '3차 전직 완료! 지금 가장 강한 모습입니다.', '3rd class awakened! You are at your peak.')
t('rpg.abilitywar.guide.grand_level', '레벨 %s 이 되면 전직 교관에게서 3차 전직 퀘스트를 받을 수 있습니다 (지금 %s)', 'Reach level %s for the 3rd class quest (now %s)')
t('rpg.abilitywar.guide.grand_visit', '전직 교관을 찾아가 3차 전직 퀘스트를 받으세요.', 'Visit the Class Master for your 3rd class quest.')
t('rpg.abilitywar.tree.sub', '1차 직업 → 전직 교관 퀘스트 → 2차 직업 → 3차 "대" 전직 (레벨 60 + 보스) · 왕의 퀘스트 → 성기사', '1st → Class Master quest → 2nd → 3rd "Grand" (Lv 60 + boss) · King\'s quest → Paladin')
t('rpg.abilitywar.tree.king', '왕의 직업', 'King\'s class')
t('rpg.abilitywar.tree.grand_desc', '3차 전직: 주는 피해 +30%, 받는 피해 -10%, 재사용 대기 -15%, 궁극기 충전 +20%, 회복·버프 +30%, 최대 체력 +6, 금빛 오라와 스킬 폭발', '3rd class: +30% damage, -10% damage taken, -15% cooldowns, +20% ult charge, +30% healing & buffs, +6 max health, golden aura')
t('rpg.abilitywar.tree.how_grand', '레벨 %s + 전직 교관의 3차 퀘스트:', 'Level %s + Class Master quest:')
t('rpg.abilitywar.tree.how_king', '레벨 %s + 왕의 3차 퀘스트:', 'Level %s + King\'s quest:')
t('rpg.abilitywar.tree.how_special', '레벨 %s + 왕의 퀘스트 (전사·탱커·힐러):', 'Level %s + King\'s quest (warrior, tank, healer):')
t('rpg.abilitywar.npc.say.advanced', '%s의 길을 걷고 있군. 레벨 %s 이 되면 더 높은 경지, "대" 의 이름을 줄 수 있네.', 'You walk the path of the %s. At level %s I can grant you a greater name.')
t('rpg.abilitywar.npc.say.grand', '%s여, 이제 마지막 시련이다. 보스를 쓰러뜨리고 증표를 가져오면 "대" 의 이름을 주지.', '%s, the final trial awaits. Slay a boss and bring proof, and you shall be Grand.')
t('rpg.abilitywar.npc.say.grand_done', '%s... 더 가르칠 것이 없네. 자네의 이름이 전설이 되길.', '%s... I have nothing more to teach. May your name become legend.')
t('rpg.abilitywar.npc.say.special', '자네는 왕의 기사로군. 자네의 시련은 왕께서 내리실 걸세.', 'You are a knight of the King. Your trials come from His Majesty.')
t('rpg.abilitywar.npc.say.busy', '지금은 「%s」에 집중하게.', 'Focus on "%s" for now.')
for k, (q, qe, d, de) in {
    'blade': ('마검의 극의', 'Mastery of the Demon Blade', '검과 마력이 하나가 되는 경지. 수많은 적과 보스를 베고 다이아몬드로 검을 다시 벼려라.', 'Where blade and mana become one. Cut down hordes and a boss, then reforge your sword with diamonds.'),
    'blood': ('끝나지 않는 밤', 'The Endless Night', '피의 굶주림을 다스려라. 밤마다 사냥하고 보스의 피를 마셔라. 가스트의 눈물이 증표다.', 'Master the hunger. Hunt every night and drink a boss\'s blood. Ghast tears are your proof.'),
    'dragon': ('고룡의 심장', 'Heart of the Elder Dragon', '불꽃 속에서 태어난 자만이 고룡이 된다. 블레이즈를 사냥하고 보스를 쓰러뜨려라.', 'Only those born of flame become Elder. Hunt blazes and fell a boss.'),
    'void': ('심연을 걷는 자', 'Walker of the Abyss', '공허의 끝을 보라. 엔더맨을 사냥하고 보스를 쓰러뜨려 엔더의 눈을 모아라.', 'See the end of the void. Hunt endermen, fell a boss, and gather eyes of ender.'),
    'warlock': ('죽음의 군주', 'Lord of Death', '죽은 자의 군대를 다스려라. 언데드를 쓰러뜨리고 보스의 영혼을 거둬라.', 'Rule an army of the dead. Slay the undead and reap a boss\'s soul.'),
    'guardian': ('무너지지 않는 성채', 'The Unbreakable Citadel', '동료를 위해 수천 번 맞고도 서 있어라. 보스의 공격까지 막아내라.', 'Stand after taking thousands of blows for your allies. Even a boss\'s.'),
    'angel': ('대천사의 날개', 'Wings of the Archangel', '수많은 생명을 구하라. 보스와의 싸움에서도 동료를 지켜라.', 'Save countless lives. Keep allies alive even against a boss.'),
    'cantor': ('천상의 성가', 'The Heavenly Hymn', '노래로 동료를 이끌어라. 보스를 쓰러뜨리는 노래를 불러라.', 'Lead your allies with song. Sing the hymn that fells a boss.'),
    'paladin': ('성검의 계승자', 'Heir of the Holy Sword', '왕국을 위협하는 어둠을 몰아내라. 언데드를 쓸어버리고 보스 둘을 쓰러뜨려라.', 'Drive back the darkness that threatens the realm. Purge the undead and fell two bosses.'),
}.items():
    t(f'rpg.abilitywar.quest.grand_{k}', q, qe)
    t(f'rpg.abilitywar.quest.grand_{k}.desc', d, de)
t('rpg.abilitywar.quest.king_paladin', '왕의 서약', 'The King\'s Oath')
t('rpg.abilitywar.quest.king_paladin.desc', '왕국의 기사가 되려면 어둠과 맞서야 한다. 언데드를 쓰러뜨리고 보스를 무찌른 뒤, 금 블록을 왕께 바쳐라.', 'To be knighted you must face the darkness. Slay the undead, defeat a boss, and offer gold blocks to the King.')
# 왕
t('entity.abilitywar.king', '아르덴 왕', 'King Arden')
t('item.abilitywar.king_spawn_egg', '왕 생성 알', 'King Spawn Egg')
t('rpg.abilitywar.king.say.offer', '용감한 자여, 왕국은 성스러운 기사가 필요하다. 나의 서약을 받아 성기사가 되겠는가?', 'Brave one, the realm needs holy knights. Will you take my oath and become a Paladin?')
t('rpg.abilitywar.king.say.level', '자네의 용기는 알겠다. 허나 레벨 %s 은 되어야 나의 기사가 될 수 있다.', 'I see your courage. But you must be level %s to be my knight.')
t('rpg.abilitywar.king.say.wrong_class', '성기사는 전사·탱커·힐러의 길을 걸은 자만이 될 수 있다.', 'Only warriors, tanks and healers may become Paladins.')
t('rpg.abilitywar.king.say.paladin', '나의 성기사여, 왕국을 지켜주게. 레벨 %s 이 되면 성검의 비밀을 알려주지.', 'My Paladin, guard the realm. At level %s I will tell you the secret of the holy sword.')
t('rpg.abilitywar.king.say.grand', '때가 되었다, 성기사여. 성검을 이어받을 시련을 내리노라.', 'The time has come, Paladin. Take up the trial of the holy sword.')
t('rpg.abilitywar.king.say.grand_done', '대성기사여, 그대가 있어 왕국은 무너지지 않는다.', 'Grand Paladin, with you the realm shall never fall.')
t('rpg.abilitywar.king.say.ready', '「%s」을(를) 해냈구나! 무릎을 꿇어라, 기사 서임을 하겠다.', 'You completed "%s"! Kneel, and be knighted.')
t('rpg.abilitywar.king.say.progress', '「%s」은(는) 아직이다. 왕국은 그대를 믿는다.', '"%s" is not yet done. The realm trusts you.')
t('rpg.abilitywar.king.say.busy', '지금은 「%s」에 힘쓰게. 끝나면 다시 오라.', 'See "%s" through first, then return.')

ability('paladin', '성기사', 'Paladin',
        '왕의 퀘스트로 얻는 특별 직업 (전사·탱커·힐러). 성검으로 싸우며 동료를 지키고 치유한다. 언데드에게 50% 더 강하다.',
        'Special class from the King\'s quest (warrior, tank, healer). Fights with a holy sword, shields and heals allies. +50% vs undead.',
        [('심판의 일격', 'Judgment', '앞으로 뛰어올라 성검을 내리꽂는다. 반지름 3 피해 6 + 구속 + 빛기둥. 재사용 8초.', 'Leap and drive your holy sword down: 6 dmg in radius 3, slow, pillar of light. 8s cooldown.'),
         ('신성한 방패', 'Sacred Shield', '6칸 안 파티의 나쁜 효과를 지우고 보호막 (흡수 II) + 수호 8초. 재사용 16초.', 'Cleanse party within 6 blocks and grant Absorption II + Aegis for 8s. 16s cooldown.'),
         ('정화의 땅', 'Consecration', '성검을 땅에 꽂아 6초 동안 반지름 5 성역: 적은 0.5초마다 피해 1.5 (언데드 2배), 아군은 회복. 재사용 18초.', 'Plant your sword: a radius-5 sanctum for 6s. Enemies take 1.5 every 0.5s (undead x2), allies heal. 18s cooldown.'),
         ('빛의 사슬', 'Chains of Light', '14칸 안 적 하나를 빛의 사슬로 묶어 2.5초 속박 + 피해 4 + 끌어당김. 재사용 12초.', 'Bind an enemy within 14 blocks: 2.5s root, 4 dmg, pulled toward you. 12s cooldown.'),
         ('성검강림 (聖劍降臨)', 'Descent of the Holy Sword', '연출: 하늘에서 거대한 성검이 내려와 박힌다. 반지름 7 피해 18 + 2초 기절, 10칸 안 아군 체력 40% 회복 + 저항 II, 박힌 성검은 8초 동안 성역.', 'Cinematic: a colossal holy sword falls from the sky. 18 dmg in radius 7 + 2s stun, allies within 10 heal 40% + Resistance II, the sword stays as an 8s sanctum.'),
         ('성스러운 오라', 'Devotion Aura', '켜기/끄기. 8칸 안 아군 받는 피해 -12%, 내 공격이 6칸 안 아군을 조금씩 회복.', 'Toggle. Allies within 8 blocks take 12% less damage; your hits heal allies within 6 a little.')])
t('abilitywar.ult.paladin.title', '聖劍降臨', 'HOLY SWORD')
t('abilitywar.ult.paladin.subtitle', '성검강림', 'Descent of the Holy Sword')

# ================================================================ 몹 · 보스
for k, ko, en in [('goblin', '고블린', 'Goblin'), ('orc', '오크 전사', 'Orc Warrior'), ('skeleton_knight', '해골 기사', 'Skeleton Knight'),
                  ('wisp', '도깨비불', 'Will-o\'-Wisp'), ('fallen_king', '타락한 기사왕', 'The Fallen King'), ('ember_colossus', '화염 거인', 'Ember Colossus'),
                  ('frost_witch', '서리 마녀', 'Frost Witch'), ('ancient_wyvern', '고대 비룡', 'Ancient Wyvern')]:
    t(f'entity.abilitywar.{k}', ko, en)
    t(f'item.abilitywar.{k}_spawn_egg', f'{ko} 생성 알', f'{en} Spawn Egg')
t('boss.abilitywar.fallen_king.sub', '— 왕좌를 잃은 자 —', '— He Who Lost the Throne —')
t('boss.abilitywar.ember_colossus.sub', '— 대지를 태우는 거인 —', '— Giant Who Burns the Earth —')
t('boss.abilitywar.frost_witch.sub', '— 영원한 겨울의 주인 —', '— Mistress of Endless Winter —')
t('boss.abilitywar.ancient_wyvern.sub', '— 하늘을 찢는 날개 —', '— Wings That Tear the Sky —')
t('boss.abilitywar.appeared', '⚠ %s 이(가) 나타났다!', '⚠ %s has appeared!')
t('boss.abilitywar.wild', '⚠ 야생 보스 %s 이(가) 근처에 나타났다! (%s, %s)', '⚠ A wild boss, %s, appeared nearby! (%s, %s)')
t('block.abilitywar.boss_altar', '보스 제단', 'Boss Altar')


# ================================================================ 맵 (지역 이름)
t('rpg.abilitywar.no_capital', '이 월드에는 수도가 없습니다 (모드를 넣기 전에 만든 월드).', 'This world has no capital (it was created before the mod).')
t('rpg.abilitywar.capital_tp', '왕도 아르덴으로 이동 (%s, %s)', 'Teleported to the Royal Capital Arden (%s, %s)')
t('rpg.abilitywar.place_not_found', '가까운 곳에서 찾지 못했습니다.', 'None found nearby.')
t('rpg.abilitywar.goto', '%s (으)로 이동: %s %s %s', 'Teleported to %s: %s %s %s')
t('region.abilitywar.capital', '왕도 아르덴', 'Royal Capital Arden')
t('region.abilitywar.capital.sub', '안전 지대 · 왕궁 · 모험가 길드 · 시장 · 전직 교관', "Safe zone · Palace · Adventurers' Guild · Market · Class Master")
t('region.abilitywar.capital_lands', '아르덴 왕령', 'Crownlands of Arden')
t('region.abilitywar.capital_lands.sub', '왕국의 들판 · 밭과 풍차, 목장', 'Fields of the kingdom · farms, windmills and pastures')
t('region.abilitywar.town', '%s 마을', '%s Village')
t('region.abilitywar.town.generic', '마을', 'Village')
t('region.abilitywar.town.sub', '안전 지대 · 전직 교관이 있습니다', 'Safe zone · A Class Master lives here')
for i, (ko, en) in enumerate([('에델', 'Edel'), ('로웬', 'Rowen'), ('하벤', 'Haven'), ('미르', 'Mir'), ('카렌', 'Karen'), ('실바', 'Silva'), ('테론', 'Theron'),
                              ('엘름', 'Elm'), ('리벤', 'Riven'), ('오크힐', 'Oakhill'), ('스톤브룩', 'Stonebrook'), ('브라이튼', 'Brighton'), ('윈터홀', 'Winterhall'),
                              ('솔레아', 'Solea'), ('마렌', 'Maren'), ('펠리아', 'Pelia')]):
    t(f'region.abilitywar.town_name.{i}', ko, en)
t('region.abilitywar.fallen_fortress', '몰락한 요새', 'Fallen Fortress')
t('region.abilitywar.fallen_fortress.sub', '위험 · 보스: 타락한 기사왕', 'Danger · Boss: The Fallen King')
t('region.abilitywar.ember_altar', '잿불 제단', 'Ember Altar')
t('region.abilitywar.ember_altar.sub', '위험 · 보스: 화염 거인', 'Danger · Boss: Ember Colossus')
t('region.abilitywar.frost_temple', '서리 신전', 'Frost Temple')
t('region.abilitywar.frost_temple.sub', '위험 · 보스: 서리 마녀', 'Danger · Boss: Frost Witch')
t('region.abilitywar.wyvern_roost', '비룡의 둥지', 'Wyvern Roost')
t('region.abilitywar.wyvern_roost.sub', '위험 · 보스: 고대 비룡', 'Danger · Boss: Ancient Wyvern')
t('region.abilitywar.goblin_camp', '고블린 야영지', 'Goblin Camp')
t('region.abilitywar.goblin_camp.sub', '위험 · 고블린 무리', 'Danger · Goblin warband')

# ================================================================ 모험가 길드
t('entity.abilitywar.guild_clerk', '모험가 길드 접수원', "Adventurers' Guild Clerk")
t('item.abilitywar.guild_clerk_spawn_egg', '모험가 길드 접수원 생성 알', 'Guild Clerk Spawn Egg')
t('guild.abilitywar.err.not_member', '먼저 모험가 길드에 가입하세요.', "Join the Adventurers' Guild first.")
t('guild.abilitywar.err.already_member', '이미 길드원입니다.', 'You are already a member.')
t('guild.abilitywar.err.rank', '자기 등급의 의뢰만 받을 수 있습니다.', 'You can only take quests of your own rank.')
t('guild.abilitywar.err.has_quest', '이미 진행 중인 길드 의뢰가 있습니다.', 'You already have a guild quest.')
t('guild.abilitywar.err.top', '이미 최고 등급 (SSS) 입니다.', 'You are already the top rank (SSS).')
t('guild.abilitywar.err.points', '승급하려면 이 등급에서 길드 포인트 %2$s 이 필요합니다.', 'You need %2$s guild points in this rank to be promoted.')
t('guild.abilitywar.err.level', '승급하려면 레벨 %1$s 이 필요합니다.', 'You need level %1$s to be promoted.')
t('guild.abilitywar.err.far', '접수원에게 더 가까이 가세요.', 'Get closer to the clerk.')
t('guild.abilitywar.objective_done', '길드 의뢰 목표 완료!', 'Guild quest objective complete!')
t('guild.abilitywar.accepted', '길드 의뢰 수락: %s', 'Guild quest accepted: %s')
t('guild.abilitywar.done', '길드 의뢰 완료: %s (포인트 +%s, 경험치 +%s, +%s G)', 'Guild quest complete: %s (+%s points, +%s XP, +%s G)')
t('guild.abilitywar.can_promote', '★ %s 등급 승급 심사를 받을 수 있습니다! 접수원에게 가세요.', '★ You can take the promotion exam for rank %s! Visit the clerk.')
t('guild.abilitywar.joined_title', '모험가 길드 가입! %s 등급', "Joined the Adventurers' Guild! Rank %s")
t('guild.abilitywar.promoted_title', '%s 등급으로 승급!', 'Promoted to rank %s!')
t('guild.abilitywar.promoted_broadcast', '%s 님이 모험가 길드 %s 등급이 되었습니다!', "%s is now a rank %s adventurer!")
t('guild.abilitywar.btn.join', '길드 가입', 'Join the Guild')
t('guild.abilitywar.btn.abandon', '포기', 'Abandon')
t('guild.abilitywar.btn.promote', '승급 심사', 'Promotion Exam')
t('guild.abilitywar.say.welcome', '모험가 길드에 어서 오세요! 가입하면 F 등급부터 시작해요. 자기 등급의 의뢰만 받을 수 있고, 의뢰를 끝내 포인트를 모으면 승급 심사를 받을 수 있어요. 최고는 SSS 등급!',
  "Welcome to the Adventurers' Guild! New members start at rank F. You can only take quests of your rank; finish them for points and take the promotion exam. The top rank is SSS!")
t('guild.abilitywar.say.ready', '「%s」 의뢰를 끝내셨군요! 보고해 주세요.', 'You finished "%s"! Please report.')
t('guild.abilitywar.say.progress', '「%s」 의뢰는 아직 진행 중이에요. 힘내세요!', '"%s" is still in progress. Good luck!')
t('guild.abilitywar.say.promote', '축하해요! %s 등급 승급 심사를 받을 수 있어요.', 'Congratulations! You can take the exam for rank %s.')
t('guild.abilitywar.say.top', 'SSS 등급 모험가님, 길드의 자랑입니다! 어떤 의뢰든 맡겨 주세요.', 'A rank SSS adventurer, the pride of the guild! Any quest is yours.')
t('guild.abilitywar.say.board', '%s 등급 의뢰 게시판이에요. 하나 골라 보세요.', 'Here is the rank %s quest board. Pick one.')
t('guild.abilitywar.screen.rank', '길드 등급', 'Guild Rank')
t('guild.abilitywar.screen.not_member', '아직 길드원이 아니에요. 아래 [길드 가입] 을 누르세요.', 'Not a member yet. Press [Join the Guild].')
t('guild.abilitywar.screen.points', '포인트 %s / %s', 'Points %s / %s')
t('guild.abilitywar.screen.next', '다음 등급: %s', 'Next rank: %s')
t('guild.abilitywar.screen.req_points', '포인트 %s', '%s points')
t('guild.abilitywar.screen.top', '최고 등급!', 'Top rank!')
t('guild.abilitywar.screen.done', '끝낸 의뢰 %s개', '%s quests done')
t('guild.abilitywar.screen.join_first', '가입하면 의뢰 목록이 보여요.', 'Join to see the quest board.')
t('guild.abilitywar.screen.rank_only', '%s 등급 의뢰만 받을 수 있어요.', 'Only rank %s quests.')
t('guild.abilitywar.screen.reward_points', '길드 포인트 +%s', '+%s guild points')
t('guild.abilitywar.screen.reward_gear', '%s 등급 장비 (%s%% 확률)', '%s gear (%s%% chance)')
t('guild.abilitywar.hud.return', '접수원에게 보고하세요!', 'Report to the clerk!')
GQ = [
  ('g_f1', '마을 주변의 좀비', 'Zombies Near Town', '밤마다 마을 근처를 어슬렁거리는 좀비를 처치해 주세요.', 'Defeat the zombies that wander near town at night.'),
  ('g_f2', '거미 소탕', 'Spider Sweep', '들판에 거미가 늘었어요. 처치해 주세요.', 'Spiders are multiplying in the fields. Clear them out.'),
  ('g_f3', '밀 납품', 'Wheat Delivery', '빵집에서 밀이 필요해요.', 'The bakery needs wheat.'),
  ('g_f4', '고블린 퇴치', 'Goblin Trouble', '고블린이 창고를 털어 갔어요!', 'Goblins raided the storehouse!'),
  ('g_e1', '해골 사냥', 'Skeleton Hunt', '길을 막는 해골 궁수를 처치하세요.', 'Defeat the skeleton archers on the roads.'),
  ('g_e2', '크리퍼 처리반', 'Creeper Squad', '밭을 날려 버리는 크리퍼를 처리해 주세요.', 'Deal with the creepers blowing up the fields.'),
  ('g_e3', '가죽 납품', 'Leather Delivery', '방어구 장인이 가죽을 찾고 있어요.', 'The armorer needs leather.'),
  ('g_e4', '고블린 소굴', 'Goblin Den', '고블린 무리를 크게 소탕하세요.', 'Wipe out a goblin warband.'),
  ('g_d1', '밤의 언데드', 'Undead of the Night', '밤에 나오는 언데드를 처치하세요.', 'Defeat undead that rise at night.'),
  ('g_d2', '오크 전사 토벌', 'Orc Warriors', '오크 전사가 상단을 습격했어요.', 'Orc warriors attacked a caravan.'),
  ('g_d3', '철 납품', 'Iron Delivery', '성벽 수리에 철이 필요해요.', 'The wall repairs need iron.'),
  ('g_d4', '마녀 사냥', 'Witch Hunt', '늪의 마녀가 저주를 퍼뜨려요.', 'Swamp witches are spreading curses.'),
  ('g_c1', '해골 기사단', 'Skeleton Knights', '갑옷 입은 해골 기사를 처치하세요.', 'Defeat the armored skeleton knights.'),
  ('g_c2', '엔더맨 조사', 'Enderman Survey', '엔더맨을 처치해 표본을 모아 주세요.', 'Defeat endermen for the scholars.'),
  ('g_c3', '도깨비불 퇴치', 'Wisp Banishing', '여행자를 홀리는 도깨비불을 없애 주세요.', 'Banish the wisps that lure travelers.'),
  ('g_c4', '금 납품', 'Gold Delivery', '왕실 금고에 금을 바쳐 주세요.', 'Deliver gold to the royal treasury.'),
  ('g_b1', '블레이즈 토벌', 'Blaze Hunt', '네더의 블레이즈를 처치하세요.', 'Defeat blazes in the Nether.'),
  ('g_b2', '오크 대토벌', 'Orc Purge', '오크 부족을 크게 토벌하세요.', 'Purge an orc tribe.'),
  ('g_b3', '몬스터 대청소', 'Monster Sweep', '왕국 곳곳의 몬스터를 처치하세요.', 'Defeat monsters across the kingdom.'),
  ('g_b4', '다이아몬드 납품', 'Diamond Delivery', '왕관 수리에 다이아몬드가 필요해요.', 'The crown needs diamonds.'),
  ('g_a1', '위더 스켈레톤 토벌', 'Wither Skeletons', '네더 요새의 위더 스켈레톤을 처치하세요.', 'Defeat wither skeletons in a Nether fortress.'),
  ('g_a2', '보스 토벌', 'Boss Hunt', '보스를 하나 쓰러뜨리세요.', 'Defeat a boss.'),
  ('g_a3', '가스트 격추', 'Ghast Downing', '네더 하늘의 가스트를 떨어뜨리세요.', 'Shoot down ghasts in the Nether.'),
  ('g_a4', '엔더 진주 납품', 'Ender Pearl Delivery', '마법사 탑에 엔더 진주가 필요해요.', "The mages' tower needs ender pearls."),
  ('g_s1', '보스 사냥꾼', 'Boss Hunter', '보스를 둘 쓰러뜨리세요.', 'Defeat two bosses.'),
  ('g_s2', '타락한 기사왕 토벌', 'The Fallen King', '몰락한 요새의 타락한 기사왕을 쓰러뜨리세요.', 'Defeat the Fallen King of the ruined fortress.'),
  ('g_s3', '대토벌', 'Great Purge', '적대적인 몹을 대량으로 처치하세요.', 'Defeat a great number of hostile mobs.'),
  ('g_s4', '서리 마녀 토벌', 'The Frost Witch', '서리 신전의 마녀를 쓰러뜨리세요.', 'Defeat the witch of the Frost Temple.'),
  ('g_ss1', '보스 셋', 'Three Bosses', '보스를 셋 쓰러뜨리세요.', 'Defeat three bosses.'),
  ('g_ss2', '고대 비룡 토벌', 'The Ancient Wyvern', '비룡의 둥지의 고대 비룡을 쓰러뜨리세요.', 'Defeat the Ancient Wyvern of the roost.'),
  ('g_ss3', '화염 거인 토벌', 'The Ember Colossus', '잿불 제단의 화염 거인을 쓰러뜨리세요.', 'Defeat the Ember Colossus of the altar.'),
  ('g_ss4', '고대 잔해 납품', 'Ancient Debris', '전설의 대장장이가 네더라이트 파편을 찾아요.', 'The legendary smith needs netherite scrap.'),
  ('g_sss1', '위더 토벌', 'The Wither', '위더를 쓰러뜨리세요.', 'Defeat the Wither.'),
  ('g_sss2', '엔더 드래곤 토벌', 'The Ender Dragon', '엔드의 드래곤을 쓰러뜨리세요.', 'Defeat the Ender Dragon.'),
  ('g_sss3', '전설의 사냥꾼', 'Legendary Hunter', '보스를 다섯 쓰러뜨리세요.', 'Defeat five bosses.'),
  ('g_sss4', '워든 토벌', 'The Warden', '고대 도시의 워든을 쓰러뜨리세요.', 'Defeat the Warden of the ancient city.'),
]
for qid, ko, en, dko, den in GQ:
    t(f'guild.abilitywar.quest.{qid}', ko, en)
    t(f'guild.abilitywar.quest.{qid}.desc', dko, den)
for k, ko, en in [('market', '시장', 'Market'), ('guild', '모험가 길드', "Adventurers' Guild"), ('guild_board', '의뢰 게시판', 'Quest Board'),
                  ('class_master', '전직 교관', 'Class Master'), ('temple', '신전', 'Temple'), ('inn', '여관', 'Inn'), ('smithy', '대장간', 'Smithy')]:
    t(f'sign.abilitywar.{k}', ko, en)

# ---------------------------------------------------------------- 상점 · 대장장이 (강화 · 승급)
t('entity.abilitywar.apothecary', '물약 상인', 'Apothecary')
t('entity.abilitywar.blacksmith', '대장장이', 'Blacksmith')
t('item.abilitywar.apothecary_spawn_egg', '물약 상인 생성 알', 'Apothecary Spawn Egg')
t('item.abilitywar.blacksmith_spawn_egg', '대장장이 생성 알', 'Blacksmith Spawn Egg')
t('itemGroup.abilitywar.rpg_items', '능력대전쟁 RPG 아이템', 'Ability War RPG Items')
t('item.abilitywar.health_potion_small', '작은 회복 물약', 'Small Health Potion')
t('item.abilitywar.health_potion', '회복 물약', 'Health Potion')
t('item.abilitywar.health_potion_large', '큰 회복 물약', 'Large Health Potion')
t('item.abilitywar.health_potion.desc', '마시면 최대 체력의 %s%% 를 바로 회복', 'Instantly restores %s%% of max health')
t('item.abilitywar.health_potion.cooldown', '회복 물약끼리 재사용 대기 %s초', 'Shared health potion cooldown: %ss')
t('item.abilitywar.enhance_stone', '강화석', 'Enhancement Stone')
t('item.abilitywar.enhance_stone.desc', '대장장이에게 장비 강화를 맡길 때 씀. 몬스터 · 보스 · 길드 의뢰 · 대장장이 상점에서 얻음',
  'Used by blacksmiths to enhance gear. From monsters, bosses, guild quests and the blacksmith shop')
t('item.abilitywar.protection_scroll', '보호 주문서', 'Protection Scroll')
t('item.abilitywar.protection_scroll.desc', '+10 이상 강화가 실패해도 강화 수치가 내려가지 않게 지켜 줌 (1장 씀)',
  'Keeps the enhancement level from dropping when a +10 or higher enhancement fails (uses 1)')
t('shop.abilitywar.say.apothecary', '어서 오시게, 모험가! 상처엔 회복 물약이 최고지. 필요한 만큼 골라 가게.',
  'Welcome, adventurer! Nothing beats a health potion for those wounds. Take what you need.')
t('shop.abilitywar.say.blacksmith', '강화 재료는 여기 있네. 돌만 넉넉하면 장비를 얼마든지 두들겨 주지!',
  'Enhancement materials, right here. Bring enough stones and I will hammer your gear all day!')
t('shop.abilitywar.have', '가진 돈: %s G', 'Money: %s G')
t('shop.abilitywar.hint', '누르기: 1개 · Shift+누르기: 5개 (팔기는 전부)', 'Click: 1 · Shift+Click: 5 (sell: all)')
t('shop.abilitywar.bought', '%s ×%s 샀다 (-%s G)', 'Bought %s ×%s (-%s G)')
t('shop.abilitywar.sold', '%s ×%s 팔았다 (+%s G)', 'Sold %s ×%s (+%s G)')
t('shop.abilitywar.sell', '%s 팔기', 'Sell %s')
t('shop.abilitywar.err.gold', '돈 (G) 이 모자랍니다.', 'Not enough G.')
t('shop.abilitywar.err.nothing_to_sell', '팔 물건이 없습니다.', 'Nothing to sell.')
t('gold.abilitywar.have', '가진 돈: %s G', 'Money: %s G')
# 승급 재료: 몬스터 · 보스 전리품
for k, ko, en, dko, den in [
    ('goblin_ear', '고블린의 귀', 'Goblin Ear', '고블린 (50%) · 희귀 승급 재료', 'Goblins (50%) · Rare promotion material'),
    ('orc_tusk', '오크의 송곳니', 'Orc Tusk', '오크 (40%) · 영웅 승급 재료', 'Orcs (40%) · Hero promotion material'),
    ('wisp_essence', '도깨비불 정수', 'Wisp Essence', '도깨비불 (40%) · 영웅 승급 재료', 'Wisps (40%) · Hero promotion material'),
    ('knight_crest', '해골 기사의 문장', 'Skeleton Knight Crest', '해골 기사 (35%) · 전설 승급 재료', 'Skeleton Knights (35%) · Legendary promotion material'),
    ('crown_shard', '기사왕의 왕관 조각', "Fallen King's Crown Shard", '보스 타락한 기사왕 · 전설 승급 재료', 'Boss: the Fallen King · Legendary promotion material'),
    ('frost_heart', '서리 마녀의 얼음 심장', "Frost Witch's Ice Heart", '보스 서리 마녀 · 신화 승급 재료', 'Boss: the Frost Witch · Mythic promotion material'),
    ('ember_core', '화염 거인의 핵', 'Ember Colossus Core', '보스 화염 거인 · 신화 승급 재료', 'Boss: the Ember Colossus · Mythic promotion material'),
    ('wyvern_scale', '고대 비룡의 비늘', 'Ancient Wyvern Scale', '보스 고대 비룡 · 시크릿 승급 재료', 'Boss: the Ancient Wyvern · Secret promotion material'),
    ('boss_soul', '보스의 영혼', 'Boss Soul', '모든 보스 (다른 모드 보스도, 위더 · 엔더 드래곤 · 워든은 3) · A 등급 이상 길드 의뢰 · 시크릿 · ? 승급 재료',
     'Every boss (other mods too; Wither, Ender Dragon, Warden give 3) · rank A+ guild quests · Secret and ? promotion material')]:
    t(f'item.abilitywar.{k}', ko, en)
    t(f'item.abilitywar.{k}.desc', dko, den)
t('guild.abilitywar.screen.reward_soul', '보스의 영혼 %s개', 'Boss Soul ×%s')
t('smith.abilitywar.err.gold', '돈 (G) 이 모자랍니다.', 'Not enough G.')
t('smith.abilitywar.err.materials', '승급 재료 (광물 · 몬스터 · 보스 전리품) 가 모자랍니다.', 'Not enough promotion materials (ores, monster and boss loot).')
t('guild.abilitywar.screen.reward_gold', '%s G', '%s G')
t('shop.abilitywar.err.unknown', '그런 물건은 없습니다.', 'No such item.')
t('shop.abilitywar.err.emeralds', '에메랄드가 모자랍니다.', 'Not enough emeralds.')
t('shop.abilitywar.err.far', '상인에게서 너무 멀리 있습니다.', 'You are too far from the merchant.')
t('smith.abilitywar.title', '장비 강화 · 승급', 'Enhance · Promote')
t('smith.abilitywar.say', '강화할 장비를 고르게. 높이 올릴수록 성공하기 어렵지!', 'Pick the gear to enhance. The higher it goes, the harder it gets!')
t('smith.abilitywar.my_gear', '내 장비', 'My Gear')
t('smith.abilitywar.scroll_hint', '휠로 넘기기', 'Scroll to see more')
t('smith.abilitywar.worn', '착용', 'Worn')
t('smith.abilitywar.no_gear', '강화할 RPG 장비가 없습니다.', 'You have no RPG gear to enhance.')
t('smith.abilitywar.maxed', '최대 강화 (+15)!', 'Fully enhanced (+15)!')
t('smith.abilitywar.next', '강화', 'Enhance')
t('smith.abilitywar.chance', '성공 확률 %s%%', 'Success %s%%')
t('smith.abilitywar.fail_drop', '실패하면 +%s 로 내려감', 'On failure: drops to +%s')
t('smith.abilitywar.fail_keep', '실패해도 그대로', 'On failure: stays')
t('smith.abilitywar.fail_none', '항상 성공', 'Always succeeds')
t('smith.abilitywar.use_scroll', '보호 주문서 쓰기 (가짐: %s)', 'Use a Protection Scroll (have %s)')
t('smith.abilitywar.promote_to', '승급 →', 'Promote →')
t('smith.abilitywar.promote_req', '+%s 이상 강화 (지금 +%s)', 'Enhanced to +%s or more (now +%s)')
t('smith.abilitywar.promote_note', '다음 등급의 같은 종류로 바뀜 · 강화 +%s 유지', 'Becomes next grade, same kind · keeps +%s')
t('smith.abilitywar.promote_top', '가장 높은 등급이라 더 승급할 수 없습니다.', 'Already the highest grade.')
t('smith.abilitywar.btn.enhance', '강화하기', 'Enhance')
t('smith.abilitywar.btn.promote', '승급하기', 'Promote')
t('smith.abilitywar.btn.shop', '재료 상점', 'Materials Shop')
t('smith.abilitywar.btn.enhance_tab', '강화 · 승급', 'Enhance · Promote')
t('smith.abilitywar.result.success', '강화 성공! +%s', 'Success! +%s')
t('smith.abilitywar.result.fail', '강화 실패…', 'Failed…')
t('smith.abilitywar.result.down', '실패! +%s 로 내려감', 'Failed! Dropped to +%s')
t('smith.abilitywar.result.promoted', '승급 성공!', 'Promoted!')
t('smith.abilitywar.promoted', '승급! %s (%s 등급)', 'Promoted! %s (%s)')
t('smith.abilitywar.broadcast', '%s 님이 %s 강화에 성공했습니다!', '%s enhanced %s!')
t('smith.abilitywar.broadcast_promote', '%s 님이 %s (%s 등급) 승급에 성공했습니다!', '%s promoted to %s (%s)!')
t('smith.abilitywar.err.no_item', 'RPG 장비를 골라 주세요.', 'Choose a piece of RPG gear.')
t('smith.abilitywar.err.max', '이미 최대 강화 (+15) 입니다.', 'Already at +15.')
t('smith.abilitywar.err.stones', '강화석이 모자랍니다.', 'Not enough Enhancement Stones.')
t('smith.abilitywar.err.emeralds', '에메랄드가 모자랍니다.', 'Not enough emeralds.')
t('smith.abilitywar.err.scroll', '보호 주문서가 없습니다.', 'You have no Protection Scroll.')
t('smith.abilitywar.err.top_grade', '더 승급할 수 없는 장비입니다.', 'This gear cannot be promoted further.')
t('smith.abilitywar.err.promote_level', '승급하려면 강화 수치가 더 높아야 합니다.', 'Enhance it further before promoting.')
t('gear.abilitywar.ui.enhance', '강화 +%s / %s', 'Enhanced +%s / %s')
t('gear.abilitywar.ui.enhance_armor', '강화 보너스: 방어력 +%s, 최대 체력 +%s', 'Enhancement: Armor +%s, Max Health +%s')
t('guild.abilitywar.screen.reward_stones', '강화석 %s개', 'Enhancement Stone ×%s')
t('sign.abilitywar.apothecary', '물약', 'Potions')

# ---------------------------------------------------------------- 플레이어끼리: 파티 · 거래 · 요청
for k, ko, en in [
    ('key.abilitywar.accept', '요청 수락', 'Accept request'), ('key.abilitywar.decline', '요청 거절', 'Decline request'),
    ('social.abilitywar.party', '파티', 'Party'),
    ('social.abilitywar.party_joined', '%s 님이 파티에 들어왔습니다.', '%s joined the party.'),
    ('social.abilitywar.party_left_you', '파티에서 나왔습니다.', 'You left the party.'),
    ('social.abilitywar.party_left', '%s 님이 파티에서 나갔습니다.', '%s left the party.'),
    ('social.abilitywar.party_disbanded', '파티가 해산되었습니다.', 'The party was disbanded.'),
    ('social.abilitywar.party_list', '파티 (%s/%s): %s', 'Party (%s/%s): %s'),
    ('social.abilitywar.err.already_party', '이미 파티에 들어가 있습니다.', 'Already in a party.'),
    ('social.abilitywar.err.party_full', '파티가 가득 찼습니다 (최대 5명).', 'The party is full (max 5).'),
    ('social.abilitywar.err.no_party', '파티에 들어가 있지 않습니다.', 'You are not in a party.'),
    ('social.abilitywar.err.self', '자기 자신에게는 보낼 수 없습니다.', 'You cannot send that to yourself.'),
    ('social.abilitywar.err.far', '너무 멀리 있습니다.', 'Too far away.'),
    ('social.abilitywar.err.target_in_party', '그 플레이어는 이미 파티에 있습니다.', 'That player is already in a party.'),
    ('social.abilitywar.err.busy', '이미 거래 중입니다.', 'Already trading.'),
    ('social.abilitywar.err.already_sent', '이미 요청을 보냈습니다.', 'Request already sent.'),
    ('social.abilitywar.err.no_request', '받은 요청이 없습니다.', 'No pending request.'),
    ('social.abilitywar.err.offline', '요청을 보낸 플레이어가 접속해 있지 않습니다.', 'That player is offline.'),
    ('social.abilitywar.err.trade_gold', '돈 (G) 이 모자라서 거래가 취소되었습니다.', 'Trade cancelled: not enough G.'),
    ('social.abilitywar.req.party', '%s 님이 파티에 초대했습니다.', '%s invited you to a party.'),
    ('social.abilitywar.req.trade', '%s 님이 거래를 요청했습니다.', '%s wants to trade.'),
    ('social.abilitywar.btn.accept', '수락', 'Accept'), ('social.abilitywar.btn.decline', '거절', 'Decline'),
    ('social.abilitywar.req_sent.party', '%s 님에게 파티 초대를 보냈습니다.', 'Party invite sent to %s.'),
    ('social.abilitywar.req_sent.trade', '%s 님에게 거래 요청을 보냈습니다.', 'Trade request sent to %s.'),
    ('social.abilitywar.declined.party', '%s 님이 파티 초대를 거절했습니다.', '%s declined your party invite.'),
    ('social.abilitywar.declined.trade', '%s 님이 거래를 거절했습니다.', '%s declined your trade request.'),
    ('social.abilitywar.you_declined', '거절했습니다.', 'Declined.'),
    ('social.abilitywar.trade_done', '%s 님과 거래했습니다!', 'Trade with %s complete!'),
    ('social.abilitywar.trade_cancelled', '거래가 취소되었습니다 (%s).', 'Trade cancelled (%s).'),
    ('social.abilitywar.hint', 'Shift + 우클릭: 거래 · 파티', 'Shift + Right-click: Trade · Party'),
    ('social.abilitywar.card.party', '⚑ %s 님의 파티 초대', '⚑ Party invite from %s'),
    ('social.abilitywar.card.trade', '⇄ %s 님의 거래 요청', '⇄ Trade request from %s'),
    ('social.abilitywar.card.keys', '[%s] 수락    [%s] 거절', '[%s] Accept    [%s] Decline'),
    ('social.abilitywar.party_hud', '파티 (%s명)', 'Party (%s)'),
    ('social.abilitywar.offline', '접속 안 함', 'Offline'),
    ('social.abilitywar.menu', '플레이어', 'Player'),
    ('social.abilitywar.btn.trade', '거래 요청', 'Request Trade'),
    ('social.abilitywar.btn.party', '파티 초대', 'Invite to Party'),
    ('social.abilitywar.btn.leave', '파티 나가기', 'Leave Party'),
    ('trade.abilitywar.title', '%s 님과 거래', 'Trade with %s'),
    ('trade.abilitywar.header', '거래', 'Trade'),
    ('trade.abilitywar.me', '나', 'Me'),
    ('trade.abilitywar.set_gold', 'G 올리기', 'Set G'),
    ('trade.abilitywar.ready', '준비', 'Ready'), ('trade.abilitywar.unready', '준비 취소', 'Unready'),
    ('trade.abilitywar.confirm', '거래 확정', 'Confirm'), ('trade.abilitywar.cancel', '취소', 'Cancel'),
    ('trade.abilitywar.have', '가진 돈: %s G', 'Money: %s G'),
    ('trade.abilitywar.state.place', '줄 물건을 왼쪽 칸에 넣고, G 를 적어 [G 올리기] 한 뒤 [준비] 를 누르세요.', 'Put items on the left, set G, then press [Ready].'),
    ('trade.abilitywar.state.wait_ready', '상대가 준비하기를 기다리는 중…', 'Waiting for the other player to be ready…'),
    ('trade.abilitywar.state.both_ready', '둘 다 준비 완료! 확인하고 [거래 확정] 을 누르세요.', 'Both ready! Check and press [Confirm].'),
    ('trade.abilitywar.state.wait_confirm', '상대의 확정을 기다리는 중…', 'Waiting for the other player to confirm…'),
    ('trade.abilitywar.mark.not_ready', '준비 안 됨', 'Not ready'),
    ('trade.abilitywar.mark.ready', '✔ 준비', '✔ Ready'),
    ('trade.abilitywar.mark.confirmed', '✔✔ 확정', '✔✔ Confirmed')]:
    t(k, ko, en)

# ---------------------------------------------------------------- 2.8: 심연의 탑 · 각성 · 마지막 드래곤 · 인벤토리 카드
for k, ko, en in [
    ('entity.abilitywar.tower_keeper', '탑지기', 'Tower Keeper'),
    ('item.abilitywar.tower_keeper_spawn_egg', '탑지기 생성 알', 'Tower Keeper Spawn Egg'),
    ('item.abilitywar.abyss_crystal', '심연의 결정', 'Abyss Crystal'),
    ('item.abilitywar.abyss_crystal.desc', '심연의 탑에서 얻는 결정. 대장장이에게서 +15 장비를 각성시킬 때 씀.', 'Found in the Abyss Tower. Used to awaken +15 gear at a blacksmith.'),
    ('item.abilitywar.dragon_heart', '용의 심장', 'Dragon Heart'),
    ('item.abilitywar.dragon_heart.desc', '엔더 드래곤의 아직 뛰는 심장. ★3 부터 각성에 씀.', "The still-beating heart of the Ender Dragon. Needed for awakening from ★3."),
    # 각성
    ('gear.abilitywar.ui.awaken', '각성 ★%s / %s', 'Awakened ★%s / %s'),
    ('smith.abilitywar.btn.awaken', '각성하기', 'Awaken'),
    ('smith.abilitywar.awaken_next', '각성', 'Awaken'),
    ('smith.abilitywar.awaken_maxed', '각성 완료! (★5)', 'Fully awakened! (★5)'),
    ('smith.abilitywar.awaken_fail', '실패해도 그대로 (재료만 사라짐)', 'Failure keeps the stars (materials are used)'),
    ('smith.abilitywar.result.awakened', '★%s 각성!', 'Awakened ★%s!'),
    ('smith.abilitywar.err.awaken_level', '+15 장비만 각성할 수 있습니다.', 'Only +15 gear can be awakened.'),
    ('smith.abilitywar.err.awaken_max', '이미 ★5 까지 각성했습니다.', 'Already awakened to ★5.'),
    ('smith.abilitywar.broadcast_awaken', '%s 님이 %s 각성에 성공했습니다!', '%s awakened %s!'),
    # 심연의 탑
    ('tower.abilitywar.title', '심연의 탑', 'Abyss Tower'),
    ('tower.abilitywar.say', '끝없이 이어지는 심연의 탑이다. 오를수록 몬스터가 강해지고, 5층마다 보스가 기다린다. 얼마나 높이 갈 수 있겠나?',
     'An endless tower into the abyss. Monsters grow stronger with every floor, and a boss waits every 5 floors. How high can you go?'),
    ('tower.abilitywar.best', '내 최고 기록: %s층', 'My best: floor %s'),
    ('tower.abilitywar.req', '레벨 %s 이상 (지금 %s)', 'Level %s+ (now %s)'),
    ('tower.abilitywar.party', '파티원 %s명이 같이 들어갑니다 (32칸 안, 레벨 40 이상)', '%s party members enter together (within 32 blocks, level 40+)'),
    ('tower.abilitywar.solo', '혼자 들어갑니다 (파티면 32칸 안 파티원이 같이 들어감)', 'Entering alone (party members within 32 blocks join you)'),
    ('tower.abilitywar.rule1', '층을 깰 때마다 G · 경험치 · 강화석, 보스 층은 심연의 결정 (각성 재료). 10층마다 첫 돌파 보너스.',
     'Each floor gives G, XP and enhance stones; boss floors give Abyss Crystals (awakening). Bonus for every first 10-floor clear.'),
    ('tower.abilitywar.rule2', '깬 10층 단위 다음 층 (11 · 21 · 31 …) 부터 다시 시작할 수 있습니다.', 'You can restart from the floor after any 10 you cleared (11, 21, 31 …).'),
    ('tower.abilitywar.rule3', '탑에서 쓰러져도 아이템은 떨어지지 않습니다. 나가기: /aw tower leave', 'Dying in the tower keeps your items. Leave with /aw tower leave'),
    ('tower.abilitywar.btn.start', '%s층부터', 'From floor %s'),
    ('tower.abilitywar.enter', '심연의 탑 %s층으로 들어갑니다… (나가기: /aw tower leave)', 'Entering the Abyss Tower at floor %s… (leave: /aw tower leave)'),
    ('tower.abilitywar.floor', '%s층', 'Floor %s'),
    ('tower.abilitywar.floor_unit', '층', ''),
    ('tower.abilitywar.floor.sub', '몬스터 %s 마리', '%s monsters'),
    ('tower.abilitywar.floor.boss', '⚠ 보스 층', '⚠ Boss floor'),
    ('tower.abilitywar.bar.prep', '심연의 탑 %s층 — %s초 뒤 시작', 'Abyss Tower floor %s — starts in %ss'),
    ('tower.abilitywar.bar.fight', '심연의 탑 %s층 — 남은 적 %s / %s', 'Abyss Tower floor %s — %s / %s enemies left'),
    ('tower.abilitywar.bar.boss', '심연의 탑 %s층 (보스) — 남은 적 %s / %s', 'Abyss Tower floor %s (boss) — %s / %s left'),
    ('tower.abilitywar.bar.break', '다음 층 (%s층) 까지 %s초 · 나가기: /aw tower leave', 'Next floor (%s) in %ss · leave: /aw tower leave'),
    ('tower.abilitywar.cleared', '%s층 돌파!', 'Floor %s cleared!'),
    ('tower.abilitywar.cleared.sub', '+%s G · 심연의 결정 %s', '+%s G · Abyss Crystal ×%s'),
    ('tower.abilitywar.cleared.sub_g', '+%s G', '+%s G'),
    ('smith.abilitywar.result.awaken_fail', '각성 실패…', 'Awakening failed…'),
    ('tower.abilitywar.milestone', '★ %s층 첫 돌파 보너스! (G · 심연의 결정 · 보호 주문서 · 보스의 영혼)', '★ First clear of floor %s! (G, Abyss Crystals, Protection Scroll, Boss Soul)'),
    ('tower.abilitywar.broadcast', '%s 님이 심연의 탑 %s층을 처음 돌파했습니다!', '%s cleared Abyss Tower floor %s for the first time!'),
    ('tower.abilitywar.left', '%s 님이 탑에서 나갔습니다.', '%s left the tower.'),
    ('tower.abilitywar.fell', '%s 님이 쓰러졌습니다.', '%s has fallen.'),
    ('tower.abilitywar.wiped', '모두 쓰러졌습니다… (%s층)', 'Everyone fell… (floor %s)'),
    ('tower.abilitywar.timeout', '시간이 다 되었습니다… (%s층)', 'Out of time… (floor %s)'),
    ('tower.abilitywar.summary', '이번 도전: %s층까지 · 최고 기록 %s층', 'This run: up to floor %s · best: floor %s'),
    ('tower.abilitywar.top', '🗼 심연의 탑 순위 (접속 중)', '🗼 Abyss Tower ranking (online)'),
    ('tower.abilitywar.err.level', '심연의 탑은 레벨 %s 부터 들어갈 수 있습니다.', 'The Abyss Tower needs level %s.'),
    ('tower.abilitywar.err.level_member', '레벨이 모자라 이번에는 같이 못 들어갑니다 (레벨 %s).', 'Your level is too low to join this run (level %s).'),
    ('tower.abilitywar.err.already', '이미 탑 안에 있습니다.', 'You are already in the tower.'),
    ('tower.abilitywar.err.full', '지금은 탑이 가득 찼습니다. 잠시 뒤에 다시 오세요.', 'The tower is full right now. Try again later.'),
    ('tower.abilitywar.err.closed', '심연의 탑이 열리지 않았습니다.', 'The Abyss Tower is not available.'),
    ('sign.abilitywar.tower', '심연의 탑', 'Abyss Tower'),
    # 마지막 드래곤
    ('boss.abilitywar.ender_dragon.sub', '— 종언의 용 —', '— The Dragon of the End —'),
    ('boss.abilitywar.ender_dragon.appeared', '⚠ 종언의 용, 엔더 드래곤이 깨어났다!', '⚠ The Ender Dragon, Dragon of the End, awakens!'),
    ('boss.abilitywar.ender_dragon.rage1', '엔더 드래곤이 분노해 포효한다!', 'The Ender Dragon roars in fury!'),
    ('boss.abilitywar.ender_dragon.rage2', '엔더 드래곤이 마지막 힘을 끌어올린다!', 'The Ender Dragon summons its last strength!'),
    # 인벤토리 카드
    ('inv.abilitywar.no_class', '직업 없음', 'No class'),
    ('inv.abilitywar.level', 'Lv %s', 'Lv %s'),
    ('inv.abilitywar.hp', '체력', 'Health'),
    ('inv.abilitywar.armor', '방어력', 'Armor'),
    ('inv.abilitywar.attack', '공격력', 'Attack'),
    ('inv.abilitywar.guild', '길드 %s', 'Guild %s'),
    ('inv.abilitywar.tower', '탑 %s층', 'Tower %s'),
    ('inv.abilitywar.tower_none', '탑 기록 없음', 'Tower —'),
    ('inv.abilitywar.s.ability', '능력 피해', 'Ability Dmg'),
    ('inv.abilitywar.s.crit', '치명타', 'Crit'),
    ('inv.abilitywar.s.cooldown', '쿨타임 감소', 'Cooldown'),
    ('inv.abilitywar.s.lifesteal', '흡혈', 'Lifesteal'),
    ('inv.abilitywar.s.reduction', '피해 감소', 'Dmg Taken'),
]:
    t(k, ko, en)


# ================================================================ 2.9 야생 생명체
for k, ko, en in [('goblin_lord', '고블린 로드', 'Goblin Lord'), ('orc_lord', '오크 로드', 'Orc Warlord'), ('bandit', '산적', 'Bandit'),
                  ('thief', '도적', 'Thief'), ('pirate', '해적', 'Pirate'), ('cultist', '광신도', 'Cultist'), ('wraith', '유령', 'Wraith'),
                  ('fairy', '요정', 'Fairy'), ('mermaid', '인어', 'Mermaid'), ('dire_wolf', '흉포한 늑대', 'Dire Wolf'),
                  ('ancient_golem', '고대 골렘', 'Ancient Golem'), ('enhanced_golem', '강화 골렘', 'Enhanced Golem'),
                  ('drake', '비룡', 'Drake'), ('dragon_king', '용왕', 'The Dragon King')]:
    t(f'entity.abilitywar.{k}', ko, en)
    t(f'item.abilitywar.{k}_spawn_egg', f'{ko} 생성 알', f'{en} Spawn Egg')
t('elite.abilitywar.goblin_lord.sub', '— 고블린 무리의 우두머리 —', '— Chieftain of the Goblin Horde —')
t('elite.abilitywar.orc_lord.sub', '— 피로 물든 부족장 —', '— The Bloodied Warchief —')
t('elite.abilitywar.enhanced_golem.sub', '— 룬이 새겨진 파수꾼 —', '— The Rune-Carved Sentinel —')
t('boss.abilitywar.dragon_king.sub', '— 모든 용의 왕 —', '— King of All Dragons —')
t('mob.abilitywar.thief.stole', '도적이 %s G 를 훔쳐 달아납니다! 쫓아가서 쓰러뜨리면 돌려받습니다.', 'A thief stole %s G and ran! Catch and defeat it to get it back.')
t('region.abilitywar.dragon_nest', '용의 둥지', "Dragon's Nest")
t('region.abilitywar.dragon_nest.sub', '매우 위험 · 보스: 용왕 · 비룡 무리', 'Extreme danger · Boss: The Dragon King · Drakes')
for k, ko, en, dko, den in [
        ('dire_fang', '흉포한 송곳니', 'Dire Fang', '흉포한 늑대의 송곳니. 인피니티 승급 재료.', 'Fang of a dire wolf. Infinity ascension material.'),
        ('bandit_seal', '도적단의 인장', "Brigand's Seal", '산적 · 도적이 지닌 인장. 인피니티 승급 재료.', 'A seal carried by bandits and thieves. Infinity ascension material.'),
        ('pirate_doubloon', '해적 금화', 'Pirate Doubloon', '해적의 낡은 금화. 인피니티 승급 재료.', "A pirate's old gold coin. Infinity ascension material."),
        ('cult_sigil', '광신도의 인장', 'Cult Sigil', '광신도가 품은 검은 인장. 정제 재료.', 'A black sigil carried by cultists. refining material.'),
        ('wraith_essence', '원혼의 정수', 'Wraith Essence', '유령이 흩어지며 남긴 차가운 빛. 정제 재료.', 'Cold light left by a vanishing wraith. refining material.'),
        ('fairy_dust', '요정 가루', 'Fairy Dust', '반짝이는 요정의 가루. 정제 재료.', 'Glittering fairy dust. refining material.'),
        ('mermaid_tear', '인어의 눈물', "Mermaid's Tear", '진주처럼 굳은 인어의 눈물. 정제 재료.', "A mermaid's tear hardened like a pearl. refining material."),
        ('drake_scale', '비룡 비늘', 'Drake Scale', '산을 맴도는 비룡의 비늘. 인피니티 승급 재료.', 'Scale of a mountain drake. Infinity ascension material.'),
        ('golem_core', '골렘 핵', 'Golem Core', '골렘을 움직이던 룬의 핵. 초월+ 재료.', 'The rune core that drove a golem. Transcendence material.'),
        ('lord_emblem', '군주의 증표', "Warlord's Emblem", '고블린 로드 · 오크 로드의 증표. 초월+ 재료.', 'Emblem of a goblin lord or orc warlord. Transcendence material.'),
        ('dragon_king_scale', '용왕의 역린', "Dragon King's Scale", '용왕의 목 아래 거꾸로 난 비늘. 인피니티 승급 재료.', "The reversed scale beneath the Dragon King's throat. Infinity ascension material.")]:
    t(f'item.abilitywar.{k}', ko, en)
    t(f'item.abilitywar.{k}.desc', dko, den)


# ================================================================ 2.9 ? +99 → 초월+ → 정제 → 인피니티 (아바리티아 모드가 있으면 그 인피니티 장비)
for k, ko, en in [
        ('smith.abilitywar.btn.repeat', '연속 강화 ×20', 'Enhance ×20'),
        ('smith.abilitywar.btn.transcend', '초월+', 'Transcend+'),
        ('smith.abilitywar.btn.refine', '정제', 'Refine'),
        ('smith.abilitywar.btn.infinity', '인피니티 승급', 'Ascend: Infinity'),
        ('smith.abilitywar.btn.infinity_avaritia', '아바리티아 승급', 'Ascend: Avaritia'),
        ('smith.abilitywar.ascend.transcend', '초월+ → +1+ 부터 다시', 'Transcend+ → restart at +1+'),
        ('smith.abilitywar.ascend.refine', '정제 → 정제 +1 부터 다시', 'Refine → restart at Refined +1'),
        ('smith.abilitywar.ascend.infinity', '승급 → 인피니티 장비', 'Ascend → Infinity gear'),
        ('smith.abilitywar.ascend.infinity_avaritia', '승급 → 아바리티아 인피니티', 'Ascend → Avaritia Infinity'),
        ('smith.abilitywar.ascend_req', '+%s 필요 (지금 +%s)', 'Needs +%s (now +%s)'),
        ('smith.abilitywar.ascend_note.transcend', '능력치는 그대로 이어지고 +1+ 부터 다시 +99+ 까지 강화합니다. 각성 ★ 은 남습니다.', 'Stats carry over; enhance again from +1+ to +99+. Awakening ★ stays.'),
        ('smith.abilitywar.ascend_note.refine', '능력치는 그대로 이어지고 정제 +1 부터 다시 +99 까지 강화합니다.', 'Stats carry over; enhance again from Refined +1 to +99.'),
        ('smith.abilitywar.ascend_note.infinity', '같은 종류의 인피니티 등급 장비 (+0, 정제 +99 보다 강함) 로 바뀝니다. 각성 ★ 은 남습니다.', 'Becomes Infinity grade gear of the same kind (+0, stronger than Refined +99). Awakening ★ stays.'),
        ('smith.abilitywar.ascend_note.infinity_avaritia', '아바리티아의 진짜 인피니티 장비로 바뀝니다: 날붙이 → 우주의 검 · 도끼 → 자연의 파멸 · 망치 → 세계 파괴자 · 창 → 삼지창 · 지팡이 → 천상의 장궁 · 갑옷 → 인피니티 갑옷.', "Becomes real Avaritia Infinity gear: blades → Sword of the Cosmos, axe → Nature's Ruin, hammer → World Breaker, spear → trident, staff → Longbow of the Heavens, armor → Infinity armor."),
        ('smith.abilitywar.result.repeat', '연속 강화 끝 · +%s', 'Done · +%s'),
        ('smith.abilitywar.result.transcend', '초월+!', 'TRANSCENDED+!'),
        ('smith.abilitywar.result.refine', '정제 완료!', 'REFINED!'),
        ('smith.abilitywar.result.infinity', '인피니티!', 'INFINITY!'),
        ('smith.abilitywar.result.infinity_avaritia', '아바리티아 인피니티!', 'AVARITIA INFINITY!'),
        ('smith.abilitywar.repeat_done', '연속 강화: %s 번 시도, %s 번 성공 (+%s → +%s)', 'Repeat enhance: %s tries, %s successes (+%s → +%s)'),
        ('smith.abilitywar.broadcast_transcend', '%s 님이 %s 을(를) 초월+ 했습니다!', '%s transcended %s!'),
        ('smith.abilitywar.broadcast_refine', '%s 님이 %s 을(를) 정제했습니다!', '%s refined %s!'),
        ('smith.abilitywar.broadcast_infinity', '✦ %s 님이 인피니티 장비 %s 을(를) 손에 넣었습니다! ✦', '✦ %s obtained the Infinity gear %s! ✦'),
        ('smith.abilitywar.broadcast_infinity_avaritia', '✦ %s 님이 아바리티아 인피니티 장비 %s 을(를) 손에 넣었습니다! ✦', '✦ %s obtained the Avaritia Infinity %s! ✦'),
        ('smith.abilitywar.err.ascend_level', '+99 까지 강화해야 합니다.', 'Enhance to +99 first.'),
        ('smith.abilitywar.err.stage_unknown', '? 등급 장비를 손에 들어야 합니다.', 'Hold a ? grade item.'),
        ('gear.abilitywar.ui.refined_mark', '정제', 'Refined'),
        ('gear.abilitywar.ui.enhance_beyond', '강화', 'Enhance'),
        ('gear.abilitywar.ui.stage.1', '[초월+]', '[Transcended+]'),
        ('gear.abilitywar.ui.stage.2', '[정제]', '[Refined]'),
        ('gear.abilitywar.ui.next_transcend', '+99 에서 대장장이에게 초월+ 를 받을 수 있다.', 'At +99 a blacksmith can Transcend+ it.'),
        ('gear.abilitywar.ui.next_refine', '+99+ 에서 대장장이에게 정제를 받을 수 있다.', 'At +99+ a blacksmith can Refine it.'),
        ('gear.abilitywar.ui.next_infinity', '정제 +99 에서 인피니티 장비로 승급할 수 있다.', 'At Refined +99 it can ascend to Infinity gear.'),
        ('gear.abilitywar.ui.next_infinity_avaritia', '정제 +99 에서 아바리티아 인피니티 장비로 승급할 수 있다.', 'At Refined +99 it can ascend to Avaritia Infinity gear.'),
        ('gear.abilitywar.ui.beyond_hint', '? 장비는 +99 까지 강화할 수 있다 (+15 부터는 실패해도 그대로).', '? gear can be enhanced to +99 (from +15, failures keep the level).')]:
    t(k, ko, en)


def apply(fname, idx):
    p = os.path.join(LANG, fname)
    d = json.load(open(p, encoding='utf-8'))
    for k, v in E.items():
        d[k] = v[idx]
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(d, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print(fname, len(d))


apply('ko_kr.json', 0)
apply('en_us.json', 1)
