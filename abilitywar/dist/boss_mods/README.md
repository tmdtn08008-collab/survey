# 보스 모드: Bosses of Mass Destruction (BOMD) + Marium's Soulslike Weaponry + The Graveyard + Archon + AdventureZ (+ VoidZ) + Dungeon Now Loading + Withering Heights

Fabric 1.20.1 용 보스 모드 7개 (+ 애드온) 입니다.

> 2.8.0 부터: 엔더 드래곤 모드는 **True Ending** 으로 바뀌었고 (Ender Dragon Fight Remastered 는 뺌 — 같이 쓰면 안 됨), 드래곤 · 생명체 보스 모드 (Saint's Dragons · Mobs of Mythology · Myths of The Sea · Aquamirae · Mutant Monsters), 셰이더 (Sodium + Iris + Complementary Reimagined · MakeUp Ultra Fast), 인벤토리 꾸미기 모드가 더 들어갔습니다.
> 새로 들어간 모드는 이 폴더에 jar 로 넣지 않고 **Modrinth 공식 주소에서 받게** 했습니다: `../abilitywar-rpg-2.9.1.mrpack` (Modrinth 앱) 또는 `../download_mods.ps1` (Windows) 을 쓰세요.
> 예전에 넣은 `edf-remastered-4.3.jar` 는 mods 폴더에서 **지우세요**.
- **Bosses of Mass Destruction**: Fabric 보스 모드 중 가장 많이 쓰이는 모드 (Modrinth 다운로드 800만 이상). L_Ender's Cataclysm 처럼 보스마다 전용 경기장 구조물이 있습니다. (Cataclysm 은 Forge 전용이라 대신 골랐습니다.)
- **Marium's Soulslike Weaponry**: 소울라이크 보스 8 + 보스가 떨어뜨리는 전설 무기 40개 이상 (Modrinth 다운로드 400만 이상).
- **The Graveyard**: 묘지 · 지하 묘실 · 유령의 집 · 언데드에게 먹힌 마을 · 리치의 감옥 같은 구조물 + 언데드 몹 + 보스 리치 (Modrinth 다운로드 500만 이상).
- 🆕 **AdventureZ** (+ **VoidZ**): 새 몬스터 30여 종 + 보스 — 블랙스톤 골렘 (네더의 최종 보스급), 디 아이 (엔드), 공허의 그림자 (VoidZ 의 공허 차원), 그리고 피글린 비스트 · 영혼 수확자 · 시든 강령술사 같은 중간 보스 (다운로드 740만 이상).
- 🆕 **Dungeon Now Loading**: 크고 어려운 던전 + 끝의 보스 — 혼돈의 스포너, 쌍둥이 보스 페어키퍼 보로스 · 우로스 (다운로드 190만 이상).
- 🆕 **Withering Heights**: 위더를 진짜 보스로 — 폭발 · 혼돈 기술 여러 개, 체력 늘림, 보스 음악.
- **Archon** (Archon Renewed): 원소 마법 · 마나 · 강령술 · 원소 무기 + 소환 제단으로 부르는 원소 보스 5 (원작 Archon 다운로드 130만 이상, Renewed 는 원작의 버그를 고친 후속판).

## 넣는 법

이 폴더의 **jar 16개를 전부** `mods` 폴더에 넣으세요 (능력대전쟁 jar · Fabric API 와 같이). **서버와 모든 플레이어 둘 다** 넣어야 합니다.
**Fabric Loader 0.19.5 이상**, **Fabric API 0.92.2 이상** 이 필요합니다 (Soulslike Weaponry 때문). 런처에서 Fabric Loader 를 새로 설치하면 됩니다.

| 파일 | 무엇 | 라이선스 | 출처 (소스) |
|---|---|---|---|
| `BOMD-1.7.5-1.20.1.jar` | Bosses of Mass Destruction 1.7.5 (보스 모드 본체) | LGPL-3.0 | [Modrinth](https://modrinth.com/mod/bosses-of-mass-destruction) · [GitHub](https://github.com/miyo6032/bosses-of-mass-destruction) |
| `soulslike-weaponry-1.4.10-1.20.1-fabric.jar` | Marium's Soulslike Weaponry 1.4.10 (보스 모드 본체) | CC0-1.0 | [Modrinth](https://modrinth.com/mod/mariums-soulslike-weaponry) · [GitHub](https://github.com/mariumbacchus/Soulslike-Weaponry) |
| `graveyard-FABRIC-4.0.1+mc1.20.1.jar` | The Graveyard 4.0.1 (모드 본체) | GPL-3.0 | [Modrinth](https://modrinth.com/mod/the-graveyard-fabric) · [GitHub (소스)](https://github.com/finallion/The_Graveyard) |
| `Archon-1.1.5.jar` | Archon Renewed 1.1.5 (모드 본체, 원작: Safro) | GPL-3.0 | [Modrinth](https://modrinth.com/mod/archon-renewed) · [GitHub (소스)](https://github.com/Stalemated/Archon-Renewed) · [원작](https://modrinth.com/mod/archon) |
| `spell_power-0.12.0+1.20.1.jar` | Spell Power Attributes 0.12.0 (Archon 이 필요로 함) | LGPL-3.0 | [Modrinth](https://modrinth.com/mod/spell-power) · [GitHub](https://github.com/ZsoltMolnarrr/SpellPower) |
| `geckolib-fabric-1.20.1-4.8.4.jar` | GeckoLib (보스 모델·애니메이션, BOMD · Soulslike · Graveyard 가 필요로 함) | MIT | [Modrinth](https://modrinth.com/mod/geckolib) · [GitHub](https://github.com/bernie-g/geckolib) |
| `cloth-config-11.1.136-fabric.jar` | Cloth Config (설정 화면, BOMD 가 필요로 함) | LGPL-3.0 | [Modrinth](https://modrinth.com/mod/cloth-config) · [GitHub](https://github.com/shedaniel/ClothConfig) |
| `cardinal-components-api-5.2.3.jar` | Cardinal Components API (BOMD 가 필요로 함) | MIT | [Modrinth](https://modrinth.com/mod/cardinal-components-api) · [GitHub](https://github.com/Ladysnake/Cardinal-Components-API) |
| `fabric-language-kotlin-1.13.14+kotlin.2.4.20.jar` | Fabric Language Kotlin (BOMD 가 필요로 함) | Apache-2.0 | [Modrinth](https://modrinth.com/mod/fabric-language-kotlin) · [GitHub](https://github.com/FabricMC/fabric-language-kotlin) |
| `AttributeFix-Fabric-1.20.1-21.0.5.jar` | AttributeFix (Soulslike Weaponry 가 필요로 함) | LGPL-2.1 | [Modrinth](https://modrinth.com/mod/attributefix) · [GitHub](https://github.com/Darkhax-Minecraft/AttributeFix) |
| `ranged_weapon_api-fabric-2.3.4+1.20.1.jar` | Ranged Weapon API (Soulslike Weaponry 가 필요로 함) | MIT | [Modrinth](https://modrinth.com/mod/ranged-weapon-api) · [GitHub](https://github.com/FabricExtras/RangedWeaponAPI) |
| `adventurez-1.4.20.jar` | AdventureZ 1.4.20 (모드 본체, Cloth Config 필요) | GPL-3.0 | [Modrinth](https://modrinth.com/mod/adventurez) · [GitHub (소스)](https://github.com/Globox1997/AdventureZ) |
| `voidz-1.0.11.jar` | VoidZ 1.0.11 (AdventureZ 애드온: 공허 차원 + 공허의 그림자) | GPL-3.0 | [Modrinth](https://modrinth.com/mod/voidz) · [GitHub (소스)](https://github.com/Globox1997/VoidZ) |
| `dungeon-now-loading-fabric-1.20.1-2.2.jar` | Dungeon Now Loading 2.2 (모드 본체, 파일 이름만 띄어쓰기 없이 바꿈) | CC0-1.0 (Modrinth 표기 MIT) | [Modrinth](https://modrinth.com/mod/dungeon-now-loading) · [GitHub](https://github.com/hexnowloading/DungeonNowLoading) |
| `withering-heights-alpha-1.jar` | Withering Heights alpha-1 (위더 강화, 데이터팩 모드) | MIT (licenses/ 폴더) | [Modrinth](https://modrinth.com/datapack/withering-heights) |
| `villagertradingplus-fabric-mc1.20.1-1.1.1.jar` | VillagersTradingPlus (주민 거래 라이브러리, The Graveyard 가 필요로 함) | GPL-3.0 | [Modrinth](https://modrinth.com/mod/villagerstradingplus) · [GitHub (소스)](https://github.com/finallion/VillagersTradingPlus) |

모두 Modrinth 에 올라온 원래 파일 그대로입니다 (고치지 않음, sha512 를 Modrinth 와 맞춰 봄). 라이선스 파일은 각 jar 안에 들어 있고, jar 안에 없는 AttributeFix · Ranged Weapon API 는 `licenses/` 폴더에 넣어 두었습니다. 새 버전은 위 링크에서 받을 수 있습니다.
GPL 모드 (The Graveyard · VillagersTradingPlus · Archon · AdventureZ · VoidZ) 의 소스는 위 GitHub 링크에 있습니다.
⚠️ Spell Power 는 **0.12.0** 을 쓰세요: 최신 1.6.x 는 Archon 이 쓰는 부분이 바뀌어서 Archon 이 켜지다 멈출 수 있습니다 (jar 를 열어 맞춰 봄).
일부만 쓰려면: BOMD = BOMD · GeckoLib · Cloth Config · Cardinal Components · Fabric Language Kotlin / Soulslike = Soulslike · GeckoLib · AttributeFix · Ranged Weapon API / Graveyard = Graveyard · GeckoLib · VillagersTradingPlus / Archon = Archon · Spell Power / AdventureZ = AdventureZ · Cloth Config (+ VoidZ) / Dungeon Now Loading · Withering Heights = 그 jar 하나.

## BOMD 보스 4

| 보스 | id | 원래 나오는 곳 (BOMD) | 능력대전쟁에서 더 나오는 곳 |
|---|---|---|---|
| 밤의 리치 (Night Lich) | `bosses_of_mass_destruction:lich` | 추운 바이옴의 리치 탑 | 야생 보스 (추운 곳, 가끔 어디든), 몰락한 요새 · 서리 신전 제단 |
| 옵시딜리스 (Obsidilith) | `bosses_of_mass_destruction:obsidilith` | 엔드의 경기장 | 야생 보스 (산), 비룡의 둥지 제단 |
| 네더 건틀릿 (Nether Gauntlet) | `bosses_of_mass_destruction:gauntlet` | 네더의 경기장 | 야생 보스 (더운 곳), 잿불 제단 |
| 공허의 꽃 (Void Blossom) | `bosses_of_mass_destruction:void_blossom` | 지하 무성한 동굴 | 야생 보스 (숲 · 늪) |

- 이 보스들을 잡아도 3차 전직 · 왕 퀘스트의 **"보스 처치"** 로 인정되고, 보스 경험치도 들어옵니다.
- 야생에 얼마나 나올지는 `config/abilitywar_wild_bosses.json` 의 `weight` 로 바꿉니다 (0 = 안 나옴). 예전 설정 파일이 있으면 새 항목이 자동으로 덧붙습니다.
- 관리자: `/aw boss bosses_of_mass_destruction:lich` 처럼 바로 불러낼 수 있습니다.

## Soulslike Weaponry 보스 8

| 보스 | id | 능력대전쟁에서 나오는 곳 |
|---|---|---|
| 썩어가는 왕 (The Decaying King) | `soulsweapons:accursed_lord_boss` | 야생 보스 (숲), 몰락한 요새 제단 |
| 귀환한 기사 (Returning Knight) | `soulsweapons:returning_knight` | 야생 보스 (어디든), 몰락한 요새 제단 |
| 옛 챔피언의 유해 (Old Champion's Remains) | `soulsweapons:draugr_boss` | 야생 보스 (추운 곳), 서리 신전 제단 |
| 혼돈의 군주 (Monarch of Chaos) | `soulsweapons:chaos_monarch` | 야생 보스 (더운 곳), 잿불 제단 |
| 낮의 추적자 (Day Stalker) | `soulsweapons:day_stalker` | 야생 보스 (더운 곳), 잿불 제단 |
| 타락한 성상 (Fallen Icon) | `soulsweapons:moonknight` | 야생 보스 (산), 비룡의 둥지 제단 |
| 광란의 그림자 (Frenzied Shade) | `soulsweapons:night_shade` | 야생 보스 (어디든) |
| 밤의 배회자 (Night Prowler) | `soulsweapons:night_prowler` | 야생 보스 (어디든) |

- **던전 제단**: 원래 보스 대신 가끔 다른 모드 보스가 나옵니다 (설치된 대체 보스가 1 마리면 3번에 1번, 2 마리면 2번에 1번, 3 마리면 5번에 3번).
- 이 보스들을 잡아도 3차 전직 · 왕 퀘스트 · 길드 의뢰의 **"보스 처치"** 로 인정되고, 보스 경험치도 들어옵니다.
- 야생에 얼마나 나올지는 `config/abilitywar_wild_bosses.json` 의 `weight` 로 바꿉니다 (0 = 안 나옴).
- 관리자: `/aw boss soulsweapons:moonknight` 처럼 바로 불러낼 수 있습니다.

## Archon 보스 5

| 보스 | id | 원래 부르는 법 (소환 제단에 넣기) | 능력대전쟁에서 더 나오는 곳 |
|---|---|---|---|
| 타르 (Tar, 대지) | `archon:tar` | 심층암 · 대지 정수 2 · 에메랄드 | 야생 보스 (어디든), 몰락한 요새 제단 |
| 아일라 (Ayla, 하늘) | `archon:ayla` | 깃털 · 금 주괴 · 하늘 정수 2 | 야생 보스 (산), 비룡의 둥지 제단 |
| 레벤 (Leven, 물) | `archon:leven` | 다시마 · 복어 · 물 정수 2 | 야생 보스 (바다), 서리 신전 제단 |
| 이니고 (Inigo, 불) | `archon:inigo` | 마그마 크림 · 블레이즈 가루 · 불 정수 2 | 야생 보스 (더운 곳), 잿불 제단 |
| 널 (Null, 엔드, 가장 셈) | `archon:null` | 엔더 진주 · 엔드 수정 · 엔드 정수 2 | 소환 제단으로만 (야생 `weight` 0 = 꺼짐) |

- Archon 의 마나 표시 위치는 Archon 설정 파일에서 바꿀 수 있습니다. Archon 키는 `.` (원소 갑옷 터뜨리기) 하나라 능력대전쟁 키와 겹치지 않습니다.

## 🆕 AdventureZ · VoidZ 보스

| 보스 | id | 원래 나오는 곳 | 능력대전쟁에서 더 |
|---|---|---|---|
| 블랙스톤 골렘 | `adventurez:blackstone_golem` | 네더의 폐허 (아주 셈) | 야생 보스 (더운 곳), 잿불 제단 |
| 디 아이 | `adventurez:the_eye` | 엔드 | (야생 꺼짐) |
| 공허의 그림자 | `adventurez:void_shadow` | VoidZ 의 공허 차원 | (야생 꺼짐) |

- 피글린 비스트 · 영혼 수확자 · 시든 강령술사 · 소환사 · 블레이즈 수호자 같은 중간 보스와 매머드 · 사슴 · 이구아나 등 새 동물 · 몬스터도 들어옵니다 (보스로는 안 셈).

## 🆕 Dungeon Now Loading 보스

| 보스 | id | 나오는 곳 |
|---|---|---|
| 혼돈의 스포너 | `dungeonnowloading:chaos_spawner` | Dungeon Now Loading 의 던전 끝 |
| 페어키퍼 보로스 · 우로스 (쌍둥이) | `dungeonnowloading:fairkeeper_boros` · `fairkeeper_ouros` | 던전 끝 |

- 던전에 묶인 보스라 야생 · 제단에는 안 나옵니다 (설정의 `weight` 로 켤 수는 있음).

## 🆕 더 세진 바닐라 보스
- **Withering Heights**: 위더가 폭발 · 혼돈 기술을 쓰고 더 단단해집니다. 설정: `/function wither_rework:config`
- **True Ending** (2.8.0 부터, 모드팩 · 다운로드 스크립트로 받음): 엔더 드래곤 체력 300 + 급강하 충격파 · 연속 화염구 · 거대 레이저 · 마지막 공중전 페이즈 + 입자 연출 + 보스 음악.
- 둘 다 능력대전쟁의 HP 표시 · "보스 처치" · 보스 전리품 (보스의 영혼 3 개) 이 그대로 적용됩니다.

## 따로 받아서 넣는 모드: Ultris: Boss Expansion

**Ultris: Boss Expansion** (LimeSplatus 제작) 도 능력대전쟁과 같이 쓸 수 있게 해 두었습니다. 다만 Ultris 라이선스는 "공개된 곳에 다시 올리기 금지" 라서 **이 폴더에는 넣지 않았습니다**.
공식 페이지 **https://modrinth.com/datapack/ultris** 에서 Fabric 1.20.1 용 `ultris-v5.6.9c.jar` 를 받아 `mods` 에 넣으세요 (Fabric API 만 필요).

- 보스 8: 타락한 엔더맨 · 블레이즈 왕 · 울트라 위더 · 성소의 수호자 · 거인 · 팬텀 떼 · 환술사 · 셜커 돌
- 쓰러뜨리면 (보스가 사라질 때 근처에 있던 모두) "보스 처치" 와 보스 경험치. Ultris 의 점수판 `kills_*` 이 오를 때 셉니다.
- Ultris 가 플레이어 근처에 보스를 불러내는 이벤트는 수도 · 마을 안에서는 막습니다.

## The Graveyard

| 보스 | id | 원래 나오는 곳 | 능력대전쟁에서 더 나오는 곳 |
|---|---|---|---|
| 타락한 챔피언 (Corrupted Champion) | `graveyard:lich` | 리치의 감옥 · 제단 의식 | 야생 보스 (어디든) |

- 묘지 · 지하 묘실 · 유령의 집 · 언데드에게 먹힌 마을 같은 구조물과 언데드 몹 (구울 · 망령 · 사신 · 레버넌트 …), 밤의 언데드 무리가 같이 들어옵니다.
- 능력대전쟁의 **수도와 왕령에는 이런 다른 지상 구조물이 생기지 않고** (바닐라 마을 · 전초기지도 마찬가지), **수도 · 마을 안에는 언데드 무리 · 약탈자 순찰대가 생기지 않습니다**.
