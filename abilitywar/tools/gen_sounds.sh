#!/bin/bash
# Ability War 효과음 합성 (ffmpeg). 실행: bash tools/gen_sounds.sh
set -e
OUT="$(dirname "$0")/../src/main/resources/assets/abilitywar/sounds"
mkdir -p "$OUT"
gen() { ffmpeg -v error -y -f lavfi -i "aevalsrc=exprs='$2':s=44100:d=$3" -ac 1 -af "$4" -c:a libvorbis -q:a 5 "$OUT/$1.ogg"; }
# 휙 (필터된 노이즈 + 하강)
gen void_whoosh "(random(0)*2-1)*sin(PI*t/0.32)^2*0.9" 0.32 "bandpass=f=900:width_type=o:w=2.5,lowpass=f=3500,volume=2.2,afade=t=out:st=0.22:d=0.1"
# 짧은 휘두르기
gen void_swing "(random(0)*2-1)*sin(PI*t/0.18)^3" 0.18 "bandpass=f=1400:width_type=o:w=2,volume=2.5"
# 깊은 충격음
gen void_impact "0.9*sin(2*PI*t*(55+90*exp(-t*9)))*exp(-t*3.2)+0.6*(random(0)*2-1)*exp(-t*28)" 1.2 "lowpass=f=2500,volume=1.6,alimiter=limit=0.95"
# 이명 (뇌진탕)
gen concussion_ring "(0.5*sin(2*PI*3800*t)+0.35*sin(2*PI*3870*t)+0.15*sin(2*PI*7600*t))*min(1,t*12)*exp(-t*0.9)" 3.0 "volume=0.35"
# 블랙홀 웅웅거림
gen void_hum "(0.5*sin(2*PI*41*t)+0.35*sin(2*PI*55.5*t+sin(2*PI*0.7*t))+0.25*sin(2*PI*82*t))*(0.75+0.25*sin(2*PI*2.2*t))+0.25*(random(0)*2-1)" 3.0 "lowpass=f=400,volume=1.8,afade=t=in:d=0.3,afade=t=out:st=2.5:d=0.5,alimiter=limit=0.95"
# 궁극기 상승음
gen void_ult_rise "(0.6*sin(2*PI*(70*t+60*t*t*t))+0.25*sin(2*PI*(140*t+120*t*t*t))+0.2*(random(0)*2-1)*t/2.5)*min(1,t*2)" 2.5 "lowpass=f=5000,volume=1.4,afade=t=out:st=2.3:d=0.2,alimiter=limit=0.95"
# UI: 준비됨
gen ui_ready "(0.6*sin(2*PI*1318*t)+0.4*sin(2*PI*1976*t))*exp(-t*9)" 0.45 "volume=0.8"
# UI: 불가
gen ui_deny "0.7*sin(2*PI*196*t)*if(gt(sin(2*PI*196*t),0),1,0.6)*exp(-t*18)" 0.16 "lowpass=f=1500,volume=0.9"
# UI: 궁극기 준비
gen ui_ult_ready "(0.45*sin(2*PI*659*t)+0.35*sin(2*PI*988*t+0.5*sin(2*PI*6*t))+0.3*sin(2*PI*1318*t)+0.12*sin(2*PI*2637*t))*min(1,t*30)*exp(-t*2.4)" 1.4 "volume=0.9"
echo ok
