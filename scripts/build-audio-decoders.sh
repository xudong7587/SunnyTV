#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd "$(dirname "$0")/.." && pwd)
module_path="$project_dir/decoder-ffmpeg/src/main"
ffmpeg_path="$module_path/jni/ffmpeg"
if [ ! -d "$ffmpeg_path/.git" ]; then
  git clone --depth 1 --branch n6.0.1 https://github.com/FFmpeg/FFmpeg.git "$ffmpeg_path"
fi
test "$(git -C "$ffmpeg_path" rev-parse HEAD)" = c41ff724ede7da657762d61097e26fac296c53bf
bash "$module_path/jni/build_ffmpeg.sh" "$module_path" "$ANDROID_HOME/ndk/26.1.10909125" linux-x86_64 24 ac3 eac3 dca truehd
mkdir -p "$project_dir/app/src/main/assets/licenses"
cp "$ffmpeg_path/COPYING.LGPLv2.1" "$project_dir/app/src/main/assets/licenses/FFmpeg-LGPL-2.1.txt"
cp "$project_dir/decoder-ffmpeg/LICENSE-APACHE-2.0" "$project_dir/app/src/main/assets/licenses/Media3-FFmpeg-Apache-2.0.txt"
