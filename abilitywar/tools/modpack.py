"""능력대전쟁 RPG 모드팩 만들기: Modrinth .mrpack + Windows 다운로드 스크립트 (download_mods.ps1).

쓰는 법 (abilitywar 폴더에서):
    python3 tools/modpack.py

- 모드 목록은 아래 MODS (Modrinth 버전 id 로 고정). 파일 주소 · 해시 · 크기는 Modrinth API 에서 받아 온다.
- 능력대전쟁 jar 는 build/libs 에서 팩 안 (overrides/mods) 에 넣는다.
- 결과: dist/abilitywar-rpg-<버전>.mrpack, dist/download_mods.ps1
"""
import hashlib
import io
import json
import os
import re
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UA = {"User-Agent": "abilitywar-modpack-builder"}

# (이름, 만든 사람, Modrinth 프로젝트 id, 버전 id, 어디에 필요한지, 묶음)
#   env: both = 서버·클라이언트 둘 다, client = 클라이언트만
#   group: 설명서 / CREDITS 에서 묶어 보여 줄 분류
MODS = [
    ("Fabric API", "FabricMC", "P7dR8mSH", "rvI2dfzR", "both", "lib"),
    # --- 보스 모드
    ("Bosses of Mass Destruction", "miyo6032", "du3UfiLL", "yoCCX4Cc", "both", "boss"),
    ("Marium's Soulslike Weaponry", "mariumbacchus", "oX6SohLj", "LY1MhWN0", "both", "boss"),
    ("The Graveyard", "finallion", "QivVPB8W", "EOb8Is0J", "both", "boss"),
    ("Archon Renewed", "Stalemated (원작 Archon — Safro)", "qAHEe69a", "2M6Fhhxf", "both", "boss"),
    ("AdventureZ", "Globox1997", "defK2XM3", "mBYmRou0", "both", "boss"),
    ("VoidZ", "Globox1997", "OMiCHIwt", "liAgrvFi", "both", "boss"),
    ("Dungeon Now Loading", "hexnowloading", "vZoqTqwv", "luhyJg7U", "both", "boss"),
    ("Withering Heights", "sleeptalker", "NCPLOWlF", "prpDWC8S", "both", "boss"),
    ("Ultris: Boss Expansion", "LimeSplatus", "tA7mQv7R", "2V8JegEj", "both", "boss"),
    # 엔더 드래곤 (Ender Dragon Fight Remastered 대신)
    ("True Ending - Ender Dragon Overhaul", "LimeSplatus", "MCnBYP0b", "TO1g4SDa", "both", "dragon"),
    # 드래곤 · 생명체 보스
    ("Saint's Dragons", "Leon / raevyx", "rjcsjwEU", "4LGtJIwV", "both", "creature"),
    ("Mobs of Mythology", "Pixel Dream Studios", "avrKhvsK", "iM3DRnM3", "both", "creature"),
    ("Myths of The Sea", "CERBON", "WaCchT6K", "PErFvJ7K", "both", "creature"),
    ("Aquamirae", "Obscuria", "k23mNPhZ", "NOuOAVhC", "both", "creature"),
    ("Mutant Monsters", "Fuzs (원작 Chaosyr)", "derP0ten", "W1xZlM32", "both", "creature"),
    # --- 야생 생명체 (광신도 일리저 · 숲의 유령 · 해골 유령선)
    ("Illager Invasion", "Fuzs · Safro (원작 Illager Expansion)", "jSV9w0J5", "tdl5EQxi", "both", "wild"),
    ("Ghost", "Joo5h · Xylonity", "gJKLGvbS", "G3k8pPhx", "both", "wild"),
    ("Skeleton Ghost Ship", "SuperWarioModTeam", "UILFr9Hb", "W7kOIEHK", "both", "wild"),
    # --- 아바리티아 (? 장비 정제 +99 → 승급하면 인피니티 장비)
    ("Re:Avaritia", "cnlimiter (원작 Avaritia — SpitefulFox)", "QeB3NRC5", "9YZiPPHJ", "both", "avaritia"),
    # --- 지형 (Tectonic 이 Terralith 를 알아보고 같이 씀)
    ("Terralith", "Stardust Labs (Apollo · catter1 · Starmute)", "8oi3bsk5", "WeYhEb5d", "both", "terrain"),
    ("Tectonic", "Apollo · DawnKiro", "lWDHr9jE", "mz3wlQKd", "both", "terrain"),
    ("Lithostitched", "Apollo", "XaDC71GB", "9bbVphAR", "both", "lib"),
    # --- 라이브러리
    ("Cloth Config API", "shedaniel", "9s6osm5g", "2xQdCMyG", "both", "lib"),
    ("Cardinal Components API", "Ladysnake", "K01OU20C", "Ielhod3p", "both", "lib"),
    ("Fabric Language Kotlin", "FabricMC", "Ha28R6CL", "KcCe0rZz", "both", "lib"),
    ("GeckoLib", "bernie-g", "8BmcQJ2H", "SD2lOScS", "both", "lib"),
    ("AttributeFix", "Darkhax", "lOOpEntO", "9PvB3nqH", "both", "lib"),
    ("Ranged Weapon API", "FabricExtras", "AqaIIO6D", "7VQ4OHzQ", "both", "lib"),
    ("Spell Power Attributes", "ZsoltMolnarrr", "8ooWzSQP", "G74msHHs", "both", "lib"),
    ("VillagersTradingPlus", "finallion", "DhOXUfnP", "hSF37DpJ", "both", "lib"),
    ("Fragmentum", "Obscuria", "49C5QgTK", "GYt8wsOl", "both", "lib"),
    ("CERBON's API", "CERBON", "GkIc6rRo", "15NpMum1", "both", "lib"),
    ("Forge Config API Port", "Fuzs", "ohNO6lps", "HvR3IdRE", "both", "lib"),
    ("Puzzles Lib", "Fuzs", "QAGBst4M", "QSW0oJPl", "both", "lib"),
    ("AzureLib", "AzureDoom", "7zlUOZvb", "xb3Ml1d5", "both", "lib"),
    ("SmartBrainLib", "Tslat", "PuyPazRT", "ft0ElRBl", "both", "lib"),
    ("Architectury API", "architectury", "lhGA9TYQ", "WbL7MStR", "both", "lib"),
    # --- 그래픽 (클라이언트만)
    ("Sodium", "CaffeineMC", "AANobbMI", "OihdIimA", "client", "graphics"),
    ("Iris Shaders", "IrisShaders", "YL57xq9U", "s5eFLITc", "client", "graphics"),
    ("Indium", "comp500", "Orvt0mRa", "nQHYSjxO", "client", "graphics"),
    ("Complementary Shaders - Reimagined", "EminGT", "HVnmMxH1", "Bqen1mJX", "client", "shader"),
    # 저사양 셰이더 (O 키 → 셰이더 팩 화면에서 고름)
    ("MakeUp - Ultra Fast", "XavierFST", "izsIPI7a", "t4YRxQug", "client", "shader"),
    # --- 인벤토리 꾸미기 (클라이언트만)
    ("Inventory Particles", "MossyLeaf", "XYnKrsxH", "JDwRa5Wi", "client", "inventory"),
    ("MossyLib", "MossyLeaf", "ffLDUGbm", "wl1N1Ts7", "client", "inventory"),
    ("Smooth Gui", "Ezzenix", "j6yrZogB", "XM7uHMcu", "client", "inventory"),
    ("Mouse Tweaks", "YaLTeR", "aC3cM3Vq", "mjuG4AYd", "client", "inventory"),
]

MC = "1.20.1"
LOADER = "0.19.5"


def get(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))


def mod_version():
    for line in open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8"):
        if line.startswith("mod_version="):
            return line.split("=", 1)[1].strip()
    raise SystemExit("mod_version 없음")


def resolve():
    ids = [m[3] for m in MODS]
    vs = {v["id"]: v for v in get("https://api.modrinth.com/v2/versions?ids=" + urllib.request.quote(json.dumps(ids)))}
    out = []
    for name, author, pid, vid, env, group in MODS:
        v = vs[vid]
        assert v["project_id"] == pid, (name, v["project_id"])
        f = ([x for x in v["files"] if x["primary"]] or v["files"])[0]
        folder = "shaderpacks" if group == "shader" else "mods"
        out.append(dict(name=name, author=author, env=env, group=group, version=v["version_number"],
                        path=folder + "/" + f["filename"].replace(" ", "-"), url=f["url"], sha1=f["hashes"]["sha1"],
                        sha512=f["hashes"]["sha512"], size=f["size"]))
    return out


IRIS_PROPERTIES = """# 능력대전쟁 RPG 모드팩: 처음 켤 때 셰이더 (Complementary Reimagined) 를 켠 상태로 시작
# 끄려면: 게임 안에서 ESC → 설정 → 비디오 설정 → 셰이더 팩 (또는 O 키) 에서 OFF
enableShaders=true
shaderPack={pack}
"""


def credits(mods, ver):
    groups = [("boss", "보스 모드"), ("dragon", "엔더 드래곤"), ("creature", "드래곤 · 생명체"), ("wild", "야생 생명체"), ("avaritia", "아바리티아"), ("terrain", "지형"),
              ("graphics", "그래픽 (셰이더)"), ("shader", "셰이더 팩"), ("inventory", "인벤토리 꾸미기"), ("lib", "라이브러리")]
    lines = ["능력대전쟁 RPG 모드팩 — 들어 있는 모드와 만든 사람", f"- 능력대전쟁 (Ability War) {ver}"]
    for g, title in groups:
        ms = [m for m in mods if m["group"] == g]
        if ms:
            lines.append(f"[{title}]")
            lines += [f"- {m['name']} {m['version']} — {m['author']}" for m in ms]
    lines.append("Ultris · True Ending: https://modrinth.com/user/LimeSplatus")
    lines.append("모든 모드 · 셰이더 파일은 Modrinth 공식 주소에서 받아집니다 (이 팩에는 주소만 들어 있음).")
    return "\n".join(lines) + "\n"


def build_mrpack(mods, ver, jar):
    shader = next(m for m in mods if m["group"] == "shader")
    index = {
        "formatVersion": 1, "game": "minecraft", "versionId": ver,
        "name": "능력대전쟁 RPG (보스 모드 팩)",
        "summary": "능력대전쟁 + 보스 · 드래곤 · 생명체 · 야생 생명체 모드 + 아바리티아 + 지형 + 셰이더 + 인벤토리 꾸미기",
        "files": [{
            "path": m["path"], "hashes": {"sha1": m["sha1"], "sha512": m["sha512"]},
            "env": {"client": "required", "server": "required" if m["env"] == "both" else "unsupported"},
            "downloads": [m["url"]], "fileSize": m["size"]} for m in mods],
        "dependencies": {"minecraft": MC, "fabric-loader": LOADER},
    }
    out = os.path.join(ROOT, "dist", f"abilitywar-rpg-{ver}.mrpack")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, ensure_ascii=False, indent=1))
        z.write(jar, "overrides/mods/" + os.path.basename(jar))
        z.writestr("overrides/CREDITS.txt", credits(mods, ver))
        # 클라이언트에서만: 셰이더 켜 둔 Iris 설정
        z.writestr("client-overrides/config/iris.properties", IRIS_PROPERTIES.format(pack=os.path.basename(shader["path"])))
    return out


PS1_HEAD = r"""# 능력대전쟁 + 보스 모드 + 셰이더를 한 번에 내려받기 (Windows PowerShell)
# 쓰는 법: PowerShell 을 열고 아래 한 줄을 붙여넣기
#   irm https://raw.githubusercontent.com/tmdtn08008-collab/survey/claude/blissful-carson-s8snwp/abilitywar/dist/download_mods.ps1 | iex
# 받을 폴더: 바탕화면\mod 모음 (OneDrive 바탕화면이 있으면 그쪽). 다른 폴더: 먼저 $env:AW_DEST = 'D:\원하는 폴더' 를 실행
# (이 파일은 tools/modpack.py 가 만듭니다)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$name = 'mod ' + [char]0xBAA8 + [char]0xC74C
if ($env:AW_DEST) { $dest = $env:AW_DEST }
elseif (Test-Path (Join-Path $env:USERPROFILE 'OneDrive\Desktop')) { $dest = Join-Path $env:USERPROFILE ('OneDrive\Desktop\' + $name) }
else { $dest = Join-Path ([Environment]::GetFolderPath('Desktop')) $name }
$client = Join-Path $dest 'client_only'
$shaders = Join-Path $dest 'shaderpacks'
New-Item -ItemType Directory -Force -Path $dest, $client, $shaders | Out-Null
Write-Host "받을 폴더: $dest" -ForegroundColor Cyan

# 예전에 받은 엔더 드래곤 모드 (True Ending 과 같이 쓰면 안 됨)
Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $dest 'edf-remastered-4.3.jar')
Get-ChildItem -Path $dest -Filter 'abilitywar-*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force

$files = @(
"""

PS1_TAIL = r"""
)
$i = 0
foreach ($f in $files) {
    $i++
    $dir = $dest
    if ($f[2] -eq 'client') { $dir = $client }
    if ($f[2] -eq 'shader') { $dir = $shaders }
    Write-Host ("[{0}/{1}] {2}" -f $i, $files.Count, $f[0])
    Invoke-WebRequest -Uri $f[1] -OutFile (Join-Path $dir $f[0]) -UseBasicParsing
}

Write-Host ''
Write-Host '다 받았습니다!' -ForegroundColor Green
Write-Host '- 이 폴더의 .jar 는 서버와 모든 플레이어의 mods 폴더에 넣으세요.'
Write-Host '- client_only 폴더의 .jar (셰이더 · 인벤토리 꾸미기) 는 플레이어 컴퓨터의 mods 폴더에만 넣으세요 (서버에는 넣지 마세요).'
Write-Host '- shaderpacks 폴더의 .zip 은 .minecraft\shaderpacks 에 넣고, 게임 안에서 O 키 → 셰이더 팩을 고르세요.'
Write-Host '- 예전에 넣은 edf-remastered-4.3.jar 는 mods 폴더에서 지우세요 (True Ending 이 대신 들어감).'
Write-Host '- Fabric Loader 0.19.5 이상 (1.20.1) 이 필요합니다: https://fabricmc.net/use/'
Write-Host '- 더 쉬운 방법: Modrinth 앱에서 abilitywar-rpg-*.mrpack 을 "파일에서 가져오기"'
Start-Process explorer.exe $dest
"""


def ps1_quote(s):
    return "'" + s.replace("'", "''") + "'"


def build_ps1(mods, ver, jar):
    base = "https://raw.githubusercontent.com/tmdtn08008-collab/survey/claude/blissful-carson-s8snwp/abilitywar/dist/"
    rows = [(os.path.basename(jar), base + os.path.basename(jar), "both")]
    for m in mods:
        rows.append((os.path.basename(m["path"]), m["url"], "shader" if m["group"] == "shader" else m["env"]))
    body = ",\n".join(f"    @({ps1_quote(a)}, {ps1_quote(b)}, {ps1_quote(c)})" for a, b, c in rows)
    text = PS1_HEAD + body + PS1_TAIL
    out = os.path.join(ROOT, "dist", "download_mods.ps1")
    # UTF-8 (BOM 없음, irm | iex 로 받으면 그대로 읽힘) + CRLF
    with open(out, "wb") as f:
        f.write(text.replace("\r\n", "\n").replace("\n", "\r\n").encode("utf-8"))
    return out


def main():
    ver = mod_version()
    jar = os.path.join(ROOT, "dist", f"abilitywar-{ver}.jar")
    if not os.path.exists(jar):
        raise SystemExit(f"{jar} 가 없음 (./gradlew build 후 dist 로 복사)")
    mods = resolve()
    if "--check" in sys.argv:
        import time
        bad = 0
        for m in mods:
            size = -1
            for attempt in range(4):
                try:
                    r = urllib.request.urlopen(urllib.request.Request(m["url"], headers=UA, method="HEAD"), timeout=30)
                    size = int(r.headers.get("Content-Length", -1))
                    break
                except Exception as e:
                    print("  retry", attempt + 1, m["url"], e)
                    time.sleep(2 ** (attempt + 1))
            ok = size == m["size"]
            bad += not ok
            print("ok" if ok else "BAD", m["path"], size)
        if bad:
            raise SystemExit(f"{bad} 개 주소가 이상함")
    print(build_mrpack(mods, ver, jar))
    print(build_ps1(mods, ver, jar))
    print(len(mods), "files,", round(sum(m["size"] for m in mods) / 1e6, 1), "MB")


if __name__ == "__main__":
    main()
