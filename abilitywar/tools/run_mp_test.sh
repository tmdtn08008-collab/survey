#!/bin/bash
# 개발용 멀티플레이 테스트: 데디서버 + 클라이언트 2개 (Caster, Victim)
# 사용: bash tools/run_mp_test.sh <casterScript> <victimScript> <prefix>
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
A_SCRIPT="$1"; B_SCRIPT="$2"; PREFIX="$3"
SP="${SP:-/tmp/claude-0/-home-user-survey/aae6e28b-7ee7-5236-bb1b-8f4f1525a598/scratchpad}"
cd "$ROOT"
./gradlew classes clientClasses --no-daemon -q || exit 1
rm -rf run/world && cp -r run/world_template run/world && rm -f run/world/session.lock
python3 - <<'PY'
import hashlib, uuid, json
def off(n):
    h = bytearray(hashlib.md5(('OfflinePlayer:' + n).encode()).digest())
    h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80
    return str(uuid.UUID(bytes=bytes(h)))
json.dump([{'uuid': off(n), 'name': n, 'level': 4, 'bypassesPlayerLimit': False} for n in ('Caster', 'Victim')], open('run/ops.json', 'w'))
PY
rm -f "$SP/mpcmd"; mkfifo "$SP/mpcmd"
(tail -f "$SP/mpcmd" | timeout 1500 ./gradlew runServer --no-daemon > "$SP/mp_server.log" 2>&1 &)
for i in $(seq 1 240); do grep -q "Done (" "$SP/mp_server.log" 2>/dev/null && break; sleep 1; done
CP_ARGS="@$ROOT/build/loom-cache/argFiles/runTestClient"
launch() { # name script
  local D="$ROOT/run_$1"; mkdir -p "$D"; cp "$ROOT/run/options.txt" "$D/options.txt"; rm -f "$D"/screenshots/*.png
  (cd "$D" && LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe timeout 1400 xvfb-run -a -s "-screen 0 960x540x24" \
    /usr/lib/jvm/java-21-openjdk-amd64/bin/java -Xmx2G -Dabilitywar.test="$2" \
    -Dfabric.dli.config="$ROOT/.gradle/loom-cache/launch.cfg" -Dfabric.dli.env=client \
    -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient "$CP_ARGS" -Dfile.encoding=UTF-8 \
    net.fabricmc.devlaunchinjector.Main --quickPlayMultiplayer localhost:25565 --username "$1" --gameDir "$D" --width 960 --height 540 \
    > "$SP/mp_$1.log" 2>&1 &)
}
launch Victim "$B_SCRIPT"
sleep 8
launch Caster "$A_SCRIPT"
for i in $(seq 1 1300); do
  a=$(grep -c "TestDriver\] quit" "$SP/mp_Caster.log" 2>/dev/null); b=$(grep -c "TestDriver\] quit" "$SP/mp_Victim.log" 2>/dev/null)
  [ "${a:-0}" -ge 1 ] && [ "${b:-0}" -ge 1 ] && break
  grep -q "Crash Report" "$SP/mp_Caster.log" "$SP/mp_Victim.log" 2>/dev/null && break
  sleep 1
done
sleep 4
pkill -f devlaunchinjector; echo stop > "$SP/mpcmd"; sleep 6; pkill -f runServer; pkill Xvfb
for n in Caster Victim; do grep -E "Crash Report|Exception|ERROR" "$SP/mp_$n.log" | grep -vE "Yggdrasil|authlib|minecraftservices|sessionserver|OpenAL|SoundSystem|ALSA|UnknownHost|realms|Realms|profile|Profile|telemetry|textures" | head -8; done
grep -E "Exception|ERROR" "$SP/mp_server.log" | grep -vE "Yggdrasil|authlib|minecraftservices|UnknownHost" | head -8
python3 - "$ROOT" "$PREFIX" "$SP/${PREFIX}.png" <<'PY'
import sys, glob, os
from PIL import Image, ImageDraw
root, prefix, out = sys.argv[1:4]
fs = []
for n in ('Caster', 'Victim'):
    fs += [(n, f) for f in glob.glob(os.path.join(root, 'run_' + n, 'screenshots', '*.png'))]
fs.sort(key=lambda x: os.path.basename(x[1]))
if not fs: print('no shots'); sys.exit()
cols = 4; tw, th = 480, 270
rows = (len(fs) + cols - 1) // cols
sheet = Image.new('RGB', (tw * cols, th * rows))
d = ImageDraw.Draw(sheet)
for i, (n, f) in enumerate(fs):
    im = Image.open(f).convert('RGB').resize((tw, th))
    x, y = (i % cols) * tw, (i // cols) * th
    sheet.paste(im, (x, y)); d.text((x + 4, y + 4), n + ':' + os.path.basename(f)[:-4], fill=(255, 255, 0))
sheet.save(out); print('sheet', out, len(fs))
PY
