# 보스 모드: Bosses of Mass Destruction (BOMD) + Marium's Soulslike Weaponry + The Graveyard

Fabric 1.20.1 용 보스 모드 3개입니다.
- **Bosses of Mass Destruction**: Fabric 보스 모드 중 가장 많이 쓰이는 모드 (Modrinth 다운로드 800만 이상). L_Ender's Cataclysm 처럼 보스마다 전용 경기장 구조물이 있습니다. (Cataclysm 은 Forge 전용이라 대신 골랐습니다.)
- **Marium's Soulslike Weaponry**: 소울라이크 보스 8 + 보스가 떨어뜨리는 전설 무기 40개 이상 (Modrinth 다운로드 400만 이상).
- **The Graveyard**: 묘지 · 지하 묘실 · 유령의 집 · 언데드에게 먹힌 마을 · 리치의 감옥 같은 구조물 + 언데드 몹 + 보스 리치 (Modrinth 다운로드 500만 이상).

## 넣는 법

이 폴더의 **jar 10개를 전부** `mods` 폴더에 넣으세요 (능력대전쟁 jar · Fabric API 와 같이). **서버와 모든 플레이어 둘 다** 넣어야 합니다.
**Fabric Loader 0.19.5 이상**, **Fabric API 0.92.2 이상** 이 필요합니다 (Soulslike Weaponry 때문). 런처에서 Fabric Loader 를 새로 설치하면 됩니다.

| 파일 | 무엇 | 라이선스 | 출처 (소스) |
|---|---|---|---|
| `BOMD-1.7.5-1.20.1.jar` | Bosses of Mass Destruction 1.7.5 (보스 모드 본체) | LGPL-3.0 | [Modrinth](https://modrinth.com/mod/bosses-of-mass-destruction) · [GitHub](https://github.com/miyo6032/bosses-of-mass-destruction) |
| `soulslike-weaponry-1.4.10-1.20.1-fabric.jar` | Marium's Soulslike Weaponry 1.4.10 (보스 모드 본체) | CC0-1.0 | [Modrinth](https://modrinth.com/mod/mariums-soulslike-weaponry) · [GitHub](https://github.com/mariumbacchus/Soulslike-Weaponry) |
| `graveyard-FABRIC-4.0.1+mc1.20.1.jar` | The Graveyard 4.0.1 (모드 본체) | GPL-3.0 | [Modrinth](https://modrinth.com/mod/the-graveyard-fabric) · [GitHub (소스)](https://github.com/finallion/The_Graveyard) |
| `geckolib-fabric-1.20.1-4.8.4.jar` | GeckoLib (보스 모델·애니메이션, **세 모드 다** 필요로 함) | MIT | [Modrinth](https://modrinth.com/mod/geckolib) · [GitHub](https://github.com/bernie-g/geckolib) |
| `cloth-config-11.1.136-fabric.jar` | Cloth Config (설정 화면, BOMD 가 필요로 함) | LGPL-3.0 | [Modrinth](https://modrinth.com/mod/cloth-config) · [GitHub](https://github.com/shedaniel/ClothConfig) |
| `cardinal-components-api-5.2.3.jar` | Cardinal Components API (BOMD 가 필요로 함) | MIT | [Modrinth](https://modrinth.com/mod/cardinal-components-api) · [GitHub](https://github.com/Ladysnake/Cardinal-Components-API) |
| `fabric-language-kotlin-1.13.14+kotlin.2.4.20.jar` | Fabric Language Kotlin (BOMD 가 필요로 함) | Apache-2.0 | [Modrinth](https://modrinth.com/mod/fabric-language-kotlin) · [GitHub](https://github.com/FabricMC/fabric-language-kotlin) |
| `AttributeFix-Fabric-1.20.1-21.0.5.jar` | AttributeFix (Soulslike Weaponry 가 필요로 함) | LGPL-2.1 | [Modrinth](https://modrinth.com/mod/attributefix) · [GitHub](https://github.com/Darkhax-Minecraft/AttributeFix) |
| `ranged_weapon_api-fabric-2.3.4+1.20.1.jar` | Ranged Weapon API (Soulslike Weaponry 가 필요로 함) | MIT | [Modrinth](https://modrinth.com/mod/ranged-weapon-api) · [GitHub](https://github.com/FabricExtras/RangedWeaponAPI) |
| `villagertradingplus-fabric-mc1.20.1-1.1.1.jar` | VillagersTradingPlus (주민 거래 라이브러리, The Graveyard 가 필요로 함) | GPL-3.0 | [Modrinth](https://modrinth.com/mod/villagerstradingplus) · [GitHub (소스)](https://github.com/finallion/VillagersTradingPlus) |

모두 Modrinth 에 올라온 원래 파일 그대로입니다 (고치지 않음, sha512 를 Modrinth 와 맞춰 봄). 라이선스 파일은 각 jar 안에 들어 있고, jar 안에 없는 AttributeFix · Ranged Weapon API 는 `licenses/` 폴더에 넣어 두었습니다. 새 버전은 위 링크에서 받을 수 있습니다.
GPL 모드 (The Graveyard · VillagersTradingPlus) 의 소스는 위 GitHub 링크에 있습니다.
일부만 쓰려면: BOMD = BOMD · GeckoLib · Cloth Config · Cardinal Components · Fabric Language Kotlin / Soulslike = Soulslike · GeckoLib · AttributeFix · Ranged Weapon API / Graveyard = Graveyard · GeckoLib · VillagersTradingPlus.

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
