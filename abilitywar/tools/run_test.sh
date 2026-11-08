#!/bin/bash
# 개발용: 테스트 스크립트로 클라이언트 실행 후 스크린샷 시트 생성
# 사용: bash tools/run_test.sh <script.txt> <prefix> [cols]
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="$1"; PREFIX="$2"; COLS="${3:-3}"
SP="${SP:-/tmp/claude-0/-home-user-survey/aae6e28b-7ee7-5236-bb1b-8f4f1525a598/scratchpad}"
cd "$ROOT"
rm -rf run/saves/test && cp -r run/world_template run/saves/test
cp "$SCRIPT" run/test_script.txt
rm -f run/screenshots/${PREFIX}_*.png
LOG="$SP/client_${PREFIX}.log"
LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe timeout 900 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runTestClient --no-daemon > "$LOG" 2>&1 &
PID=$!
for i in $(seq 1 880); do
  if grep -qE "TestDriver\] quit|---- Minecraft Crash Report|BUILD FAILED|Stopping!" "$LOG" 2>/dev/null; then break; fi
  sleep 1
done
sleep 4
pkill -f KnotClient 2>/dev/null; pkill -f "runTestClient" 2>/dev/null; pkill Xvfb 2>/dev/null
wait $PID 2>/dev/null
grep -E "Crash Report|Exception|ERROR" "$LOG" | grep -vE "Yggdrasil|authlib|minecraftservices|sessionserver|OpenAL|SoundSystem|ALSA|UnknownHost|realms|Realms" | head -20
python3 - "$ROOT/run/screenshots" "$PREFIX" "$COLS" "$SP/${PREFIX}.png" <<'PY'
import sys, glob, os
from PIL import Image, ImageDraw
d, prefix, cols, out = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
fs = sorted(glob.glob(os.path.join(d, prefix + '_*.png')), key=os.path.getmtime)
if not fs:
    print('no screenshots'); sys.exit(0)
ims = [Image.open(f).convert('RGB') for f in fs]
w, h = ims[0].size
tw, th = w * 2 // 5, h * 2 // 5
rows = (len(ims) + cols - 1) // cols
sheet = Image.new('RGB', (tw * cols, th * rows), (0, 0, 0))
dr = ImageDraw.Draw(sheet)
for i, (f, im) in enumerate(zip(fs, ims)):
    x, y = (i % cols) * tw, (i // cols) * th
    sheet.paste(im.resize((tw, th)), (x, y))
    dr.text((x + 4, y + 4), os.path.basename(f)[len(prefix) + 1:-4], fill=(255, 255, 0))
sheet.save(out)
print('sheet', out, len(fs))
PY
