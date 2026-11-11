# 능력대전쟁 + 보스 모드를 한 번에 내려받기 (Windows PowerShell)
# 쓰는 법: PowerShell 을 열고 아래 한 줄을 붙여넣기
#   irm https://raw.githubusercontent.com/tmdtn08008-collab/survey/claude/blissful-carson-s8snwp/abilitywar/dist/download_mods.ps1 | iex
# 받을 폴더: 바탕화면\mod 모음 (OneDrive 바탕화면이 있으면 그쪽). 다른 폴더: 먼저 $env:AW_DEST = 'D:\원하는 폴더' 를 실행

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$name = 'mod ' + [char]0xBAA8 + [char]0xC74C
if ($env:AW_DEST) { $dest = $env:AW_DEST }
elseif (Test-Path (Join-Path $env:USERPROFILE 'OneDrive\Desktop')) { $dest = Join-Path $env:USERPROFILE ('OneDrive\Desktop\' + $name) }
else { $dest = Join-Path ([Environment]::GetFolderPath('Desktop')) $name }
New-Item -ItemType Directory -Force -Path $dest | Out-Null
Write-Host "받을 폴더: $dest" -ForegroundColor Cyan

$base = 'https://raw.githubusercontent.com/tmdtn08008-collab/survey/claude/blissful-carson-s8snwp/abilitywar/dist'
$files = @(
    'abilitywar-2.7.0.jar',
    'boss_mods/Archon-1.1.5.jar',
    'boss_mods/AttributeFix-Fabric-1.20.1-21.0.5.jar',
    'boss_mods/BOMD-1.7.5-1.20.1.jar',
    'boss_mods/adventurez-1.4.20.jar',
    'boss_mods/cardinal-components-api-5.2.3.jar',
    'boss_mods/cloth-config-11.1.136-fabric.jar',
    'boss_mods/dungeon-now-loading-fabric-1.20.1-2.2.jar',
    'boss_mods/edf-remastered-4.3.jar',
    'boss_mods/fabric-language-kotlin-1.13.14+kotlin.2.4.20.jar',
    'boss_mods/geckolib-fabric-1.20.1-4.8.4.jar',
    'boss_mods/graveyard-FABRIC-4.0.1+mc1.20.1.jar',
    'boss_mods/ranged_weapon_api-fabric-2.3.4+1.20.1.jar',
    'boss_mods/soulslike-weaponry-1.4.10-1.20.1-fabric.jar',
    'boss_mods/spell_power-0.12.0+1.20.1.jar',
    'boss_mods/villagertradingplus-fabric-mc1.20.1-1.1.1.jar',
    'boss_mods/voidz-1.0.11.jar',
    'boss_mods/withering-heights-alpha-1.jar',
    'boss_mods/README.md'
)
$i = 0
foreach ($f in $files) {
    $i++
    $leaf = Split-Path $f -Leaf
    if ($leaf -eq 'README.md') { $leaf = 'boss_mods_README.md' }
    Write-Host ("[{0}/{1}] {2}" -f $i, ($files.Count + 1), $leaf)
    Invoke-WebRequest -Uri "$base/$f" -OutFile (Join-Path $dest $leaf) -UseBasicParsing
}
# Fabric API (공식 Modrinth)
Write-Host ("[{0}/{0}] fabric-api-0.92.12+1.20.1.jar" -f ($files.Count + 1))
Invoke-WebRequest -Uri 'https://cdn.modrinth.com/data/P7dR8mSH/versions/rvI2dfzR/fabric-api-0.92.12%2B1.20.1.jar' -OutFile (Join-Path $dest 'fabric-api-0.92.12+1.20.1.jar') -UseBasicParsing

Write-Host ''
Write-Host '다 받았습니다!' -ForegroundColor Green
Write-Host '- 이 폴더의 .jar 를 전부 .minecraft\mods 에 넣으세요 (서버와 모든 플레이어 둘 다).'
Write-Host '- Fabric Loader 0.19.5 이상 (1.20.1) 이 필요합니다: https://fabricmc.net/use/'
Write-Host '- Ultris: Boss Expansion 은 라이선스 때문에 따로 받으세요: https://modrinth.com/datapack/ultris'
Start-Process explorer.exe $dest
