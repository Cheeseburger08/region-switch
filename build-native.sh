#!/bin/bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to Android NDK r29}"
: "${FRIDA_CORE_DEVKIT:?Set FRIDA_CORE_DEVKIT to the Frida 17.9.11 Android arm64 core devkit}"
cc="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
"$cc/aarch64-linux-android26-clang" -DANDROID -O2 -Wall -ffunction-sections -fdata-sections controller.c \
  -I"$FRIDA_CORE_DEVKIT" -L"$FRIDA_CORE_DEVKIT" -lfrida-core -llog -ldl -lm -pthread -Wl,--export-dynamic -o controller
"$cc/llvm-strip" --strip-debug controller
