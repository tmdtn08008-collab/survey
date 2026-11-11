# 보스 모드: Bosses of Mass Destruction (BOMD)

Fabric 1.20.1 용 보스 모드 중 가장 많이 쓰이는 모드입니다 (Modrinth 다운로드 800만 이상). L_Ender's Cataclysm 처럼 보스마다 전용 경기장 구조물이 있습니다.
Cataclysm 은 Forge 전용이라 이 모드팩 (Fabric) 에서 못 쓰기 때문에 대신 골랐습니다.

## 넣는 법

이 폴더의 **jar 5개를 전부** `mods` 폴더에 넣으세요 (능력대전쟁 jar · Fabric API 와 같이). **서버와 모든 플레이어 둘 다** 넣어야 합니다.

| 파일 | 무엇 | 라이선스 | 출처 (소스) |
|---|---|---|---|
| `BOMD-1.7.5-1.20.1.jar` | Bosses of Mass Destruction 1.7.5 (보스 모드 본체) | LGPL-3.0 | [Modrinth](https://modrinth.com/mod/bosses-of-mass-destruction) · [GitHub](https://github.com/miyo6032/bosses-of-mass-destruction) |
| `cloth-config-11.1.136-fabric.jar` | Cloth Config (설정 화면, BOMD 가 필요로 함) | LGPL-3.0 | [Modrinth](https://modrinth.com/mod/cloth-config) · [GitHub](https://github.com/shedaniel/ClothConfig) |
| `cardinal-components-api-5.2.3.jar` | Cardinal Components API (BOMD 가 필요로 함) | MIT | [Modrinth](https://modrinth.com/mod/cardinal-components-api) · [GitHub](https://github.com/Ladysnake/Cardinal-Components-API) |
| `fabric-language-kotlin-1.13.14+kotlin.2.4.20.jar` | Fabric Language Kotlin (BOMD 가 필요로 함, Fabric Loader 0.16.9 이상) | Apache-2.0 | [Modrinth](https://modrinth.com/mod/fabric-language-kotlin) · [GitHub](https://github.com/FabricMC/fabric-language-kotlin) |
| `geckolib-fabric-1.20.1-4.8.4.jar` | GeckoLib (보스 모델·애니메이션, BOMD 가 필요로 함) | MIT | [Modrinth](https://modrinth.com/mod/geckolib) · [GitHub](https://github.com/bernie-g/geckolib) |

모두 Modrinth 에 올라온 원래 파일 그대로입니다 (고치지 않음, 라이선스 파일은 각 jar 안에 들어 있음). 새 버전은 위 링크에서 받을 수 있습니다.

## 보스 4

| 보스 | id | 원래 나오는 곳 (BOMD) | 능력대전쟁에서 더 나오는 곳 |
|---|---|---|---|
| 밤의 리치 (Night Lich) | `bosses_of_mass_destruction:lich` | 추운 바이옴의 리치 탑 | 야생 보스 (추운 곳, 가끔 어디든), 몰락한 요새 · 서리 신전 제단 (3번에 1번) |
| 옵시딜리스 (Obsidilith) | `bosses_of_mass_destruction:obsidilith` | 엔드의 경기장 | 야생 보스 (산), 비룡의 둥지 제단 (3번에 1번) |
| 네더 건틀릿 (Nether Gauntlet) | `bosses_of_mass_destruction:gauntlet` | 네더의 경기장 | 야생 보스 (더운 곳), 잿불 제단 (3번에 1번) |
| 공허의 꽃 (Void Blossom) | `bosses_of_mass_destruction:void_blossom` | 지하 무성한 동굴 | 야생 보스 (숲 · 늪) |

- 이 보스들을 잡아도 3차 전직 · 왕 퀘스트의 **"보스 처치"** 로 인정되고, 보스 경험치도 들어옵니다.
- 야생에 얼마나 나올지는 `config/abilitywar_wild_bosses.json` 의 `weight` 로 바꿉니다 (0 = 안 나옴). 예전 설정 파일이 있으면 새 항목이 자동으로 덧붙습니다.
- 관리자: `/aw boss bosses_of_mass_destruction:lich` 처럼 바로 불러낼 수 있습니다.
