# 능력대전쟁 + 보스 모드 + 셰이더를 한 번에 내려받기 (Windows PowerShell)
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
    @('abilitywar-2.10.1.jar', 'https://raw.githubusercontent.com/tmdtn08008-collab/survey/claude/blissful-carson-s8snwp/abilitywar/dist/abilitywar-2.10.1.jar', 'both'),
    @('fabric-api-0.92.12+1.20.1.jar', 'https://cdn.modrinth.com/data/P7dR8mSH/versions/rvI2dfzR/fabric-api-0.92.12%2B1.20.1.jar', 'both'),
    @('BOMD-1.7.5-1.20.1.jar', 'https://cdn.modrinth.com/data/du3UfiLL/versions/yoCCX4Cc/BOMD-1.7.5-1.20.1.jar', 'both'),
    @('soulslike-weaponry-1.4.10-1.20.1-fabric.jar', 'https://cdn.modrinth.com/data/oX6SohLj/versions/LY1MhWN0/soulslike-weaponry-1.4.10-1.20.1-fabric.jar', 'both'),
    @('graveyard-FABRIC-4.0.1+mc1.20.1.jar', 'https://cdn.modrinth.com/data/QivVPB8W/versions/EOb8Is0J/graveyard-FABRIC-4.0.1%2Bmc1.20.1.jar', 'both'),
    @('Archon-1.1.5.jar', 'https://cdn.modrinth.com/data/qAHEe69a/versions/2M6Fhhxf/Archon-1.1.5.jar', 'both'),
    @('adventurez-1.4.20.jar', 'https://cdn.modrinth.com/data/defK2XM3/versions/mBYmRou0/adventurez-1.4.20.jar', 'both'),
    @('voidz-1.0.11.jar', 'https://cdn.modrinth.com/data/OMiCHIwt/versions/liAgrvFi/voidz-1.0.11.jar', 'both'),
    @('Dungeon-Now-Loading-fabric-1.20.1-2.2.jar', 'https://cdn.modrinth.com/data/vZoqTqwv/versions/luhyJg7U/Dungeon%20Now%20Loading-fabric-1.20.1-2.2.jar', 'both'),
    @('withering-heights-alpha-1.jar', 'https://cdn.modrinth.com/data/NCPLOWlF/versions/prpDWC8S/withering-heights-alpha-1.jar', 'both'),
    @('ultris-v5.6.9c.jar', 'https://cdn.modrinth.com/data/tA7mQv7R/versions/2V8JegEj/ultris-v5.6.9c.jar', 'both'),
    @('tru.e-ending-v1.1.0c.jar', 'https://cdn.modrinth.com/data/MCnBYP0b/versions/TO1g4SDa/tru.e-ending-v1.1.0c.jar', 'both'),
    @('saintsdragons-0.9.88+fabric-1.20.1.jar', 'https://cdn.modrinth.com/data/rjcsjwEU/versions/4LGtJIwV/saintsdragons-0.9.88%2Bfabric-1.20.1.jar', 'both'),
    @('mobs_of_mythology-fabric-3.0.0.jar', 'https://cdn.modrinth.com/data/avrKhvsK/versions/iM3DRnM3/mobs_of_mythology-fabric-3.0.0.jar', 'both'),
    @('MOTS-Fabric-1.20.1-1.3.1.jar', 'https://cdn.modrinth.com/data/WaCchT6K/versions/PErFvJ7K/MOTS-Fabric-1.20.1-1.3.1.jar', 'both'),
    @('aquamirae-fabric-1.20.1-7.1.14.jar', 'https://cdn.modrinth.com/data/k23mNPhZ/versions/NOuOAVhC/aquamirae-fabric-1.20.1-7.1.14.jar', 'both'),
    @('MutantMonsters-v8.0.8-1.20.1-Fabric.jar', 'https://cdn.modrinth.com/data/derP0ten/versions/W1xZlM32/MutantMonsters-v8.0.8-1.20.1-Fabric.jar', 'both'),
    @('IllagerInvasion-v8.0.7-1.20.1-Fabric.jar', 'https://cdn.modrinth.com/data/jSV9w0J5/versions/tdl5EQxi/IllagerInvasion-v8.0.7-1.20.1-Fabric.jar', 'both'),
    @('ghosts-fabric-1.20.1-1.3.4.jar', 'https://cdn.modrinth.com/data/gJKLGvbS/versions/G3k8pPhx/ghosts-fabric-1.20.1-1.3.4.jar', 'both'),
    @('skeleton_ghost_ship-1.0.0-fabric-1.20.1.jar', 'https://cdn.modrinth.com/data/UILFr9Hb/versions/W7kOIEHK/skeleton_ghost_ship-1.0.0%20fabric%201.20.1.jar', 'both'),
    @('Re-Avaritia-fabric-1.20.1-1.4.2-fabric.jar', 'https://cdn.modrinth.com/data/QeB3NRC5/versions/9YZiPPHJ/Re-Avaritia-fabric-1.20.1-1.4.2-fabric.jar', 'both'),
    @('Terralith_1.20.x_v2.5.4.jar', 'https://cdn.modrinth.com/data/8oi3bsk5/versions/WeYhEb5d/Terralith_1.20.x_v2.5.4.jar', 'both'),
    @('tectonic-3.0.17-fabric-1.20.1.jar', 'https://cdn.modrinth.com/data/lWDHr9jE/versions/mz3wlQKd/tectonic-3.0.17-fabric-1.20.1.jar', 'both'),
    @('lithostitched-fabric-1.20.1-1.4.11.jar', 'https://cdn.modrinth.com/data/XaDC71GB/versions/9bbVphAR/lithostitched-fabric-1.20.1-1.4.11.jar', 'both'),
    @('cloth-config-11.1.136-fabric.jar', 'https://cdn.modrinth.com/data/9s6osm5g/versions/2xQdCMyG/cloth-config-11.1.136-fabric.jar', 'both'),
    @('cardinal-components-api-5.2.3.jar', 'https://cdn.modrinth.com/data/K01OU20C/versions/Ielhod3p/cardinal-components-api-5.2.3.jar', 'both'),
    @('fabric-language-kotlin-1.13.14+kotlin.2.4.20.jar', 'https://cdn.modrinth.com/data/Ha28R6CL/versions/KcCe0rZz/fabric-language-kotlin-1.13.14%2Bkotlin.2.4.20.jar', 'both'),
    @('geckolib-fabric-1.20.1-4.8.4.jar', 'https://cdn.modrinth.com/data/8BmcQJ2H/versions/SD2lOScS/geckolib-fabric-1.20.1-4.8.4.jar', 'both'),
    @('AttributeFix-Fabric-1.20.1-21.0.5.jar', 'https://cdn.modrinth.com/data/lOOpEntO/versions/9PvB3nqH/AttributeFix-Fabric-1.20.1-21.0.5.jar', 'both'),
    @('ranged_weapon_api-fabric-2.3.4+1.20.1.jar', 'https://cdn.modrinth.com/data/AqaIIO6D/versions/7VQ4OHzQ/ranged_weapon_api-fabric-2.3.4%2B1.20.1.jar', 'both'),
    @('spell_power-0.12.0+1.20.1.jar', 'https://cdn.modrinth.com/data/8ooWzSQP/versions/G74msHHs/spell_power-0.12.0%2B1.20.1.jar', 'both'),
    @('villagertradingplus-fabric-mc1.20.1-1.1.1.jar', 'https://cdn.modrinth.com/data/DhOXUfnP/versions/hSF37DpJ/villagertradingplus-fabric-mc1.20.1-1.1.1.jar', 'both'),
    @('fragmentum-5.4.0+1.20.1-fabric.jar', 'https://cdn.modrinth.com/data/49C5QgTK/versions/GYt8wsOl/fragmentum-5.4.0%2B1.20.1-fabric.jar', 'both'),
    @('CerbonsAPI-Fabric-1.20.1-1.1.0.jar', 'https://cdn.modrinth.com/data/GkIc6rRo/versions/15NpMum1/CerbonsAPI-Fabric-1.20.1-1.1.0.jar', 'both'),
    @('ForgeConfigAPIPort-v8.0.3-1.20.1-Fabric.jar', 'https://cdn.modrinth.com/data/ohNO6lps/versions/HvR3IdRE/ForgeConfigAPIPort-v8.0.3-1.20.1-Fabric.jar', 'both'),
    @('PuzzlesLib-v8.1.36-1.20.1-Fabric.jar', 'https://cdn.modrinth.com/data/QAGBst4M/versions/QSW0oJPl/PuzzlesLib-v8.1.36-1.20.1-Fabric.jar', 'both'),
    @('azurelib-fabric-1.20.1-3.2.3.jar', 'https://cdn.modrinth.com/data/7zlUOZvb/versions/xb3Ml1d5/azurelib-fabric-1.20.1-3.2.3.jar', 'both'),
    @('SmartBrainLib-fabric-1.20.1-1.15.jar', 'https://cdn.modrinth.com/data/PuyPazRT/versions/ft0ElRBl/SmartBrainLib-fabric-1.20.1-1.15.jar', 'both'),
    @('architectury-9.2.14-fabric.jar', 'https://cdn.modrinth.com/data/lhGA9TYQ/versions/WbL7MStR/architectury-9.2.14-fabric.jar', 'both'),
    @('sodium-fabric-0.5.13+mc1.20.1.jar', 'https://cdn.modrinth.com/data/AANobbMI/versions/OihdIimA/sodium-fabric-0.5.13%2Bmc1.20.1.jar', 'client'),
    @('iris-1.7.6+mc1.20.1.jar', 'https://cdn.modrinth.com/data/YL57xq9U/versions/s5eFLITc/iris-1.7.6%2Bmc1.20.1.jar', 'client'),
    @('indium-1.0.36+mc1.20.1.jar', 'https://cdn.modrinth.com/data/Orvt0mRa/versions/nQHYSjxO/indium-1.0.36%2Bmc1.20.1.jar', 'client'),
    @('ComplementaryReimagined_r5.9.3.zip', 'https://cdn.modrinth.com/data/HVnmMxH1/versions/Bqen1mJX/ComplementaryReimagined_r5.9.3.zip', 'shader'),
    @('MakeUp-UltraFast-9.6.zip', 'https://cdn.modrinth.com/data/izsIPI7a/versions/t4YRxQug/MakeUp-UltraFast-9.6.zip', 'shader'),
    @('InventoryParticles-3.2.0+1.20.1+fabric.jar', 'https://cdn.modrinth.com/data/XYnKrsxH/versions/JDwRa5Wi/InventoryParticles-3.2.0%2B1.20.1%2Bfabric.jar', 'client'),
    @('MossyLib-1.6.0+1.20.1+fabric.jar', 'https://cdn.modrinth.com/data/ffLDUGbm/versions/wl1N1Ts7/MossyLib-1.6.0%2B1.20.1%2Bfabric.jar', 'client'),
    @('smoothgui-fabric-2.1.0+mc1.20.1.jar', 'https://cdn.modrinth.com/data/j6yrZogB/versions/XM7uHMcu/smoothgui-fabric-2.1.0%2Bmc1.20.1.jar', 'client'),
    @('MouseTweaks-fabric-mc1.20-2.26.jar', 'https://cdn.modrinth.com/data/aC3cM3Vq/versions/mjuG4AYd/MouseTweaks-fabric-mc1.20-2.26.jar', 'client')
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
