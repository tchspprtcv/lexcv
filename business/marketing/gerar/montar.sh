#!/bin/bash
# Monta GIF + MP4 a partir de frames/ para o tema indicado.
set -e
TEMA="$1"; L="${2:-900}"
ffmpeg -y -framerate 10 -i frames/%04d.png \
  -vf "scale=${L}:-2:flags=lanczos,palettegen=max_colors=128:stats_mode=diff" paleta.png -loglevel error
ffmpeg -y -framerate 10 -i frames/%04d.png -i paleta.png \
  -lavfi "scale=${L}:-2:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=3:diff_mode=rectangle" \
  -loop 0 "lexcv-${TEMA}.gif" -loglevel error
ffmpeg -y -framerate 10 -i frames/%04d.png \
  -vf "scale=${L}:-2:flags=lanczos,format=yuv420p" \
  -c:v libx264 -preset slow -crf 22 -movflags +faststart "lexcv-${TEMA}.mp4" -loglevel error
