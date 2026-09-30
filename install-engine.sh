#!/system/bin/sh
# Run explicitly through an already-authorized su. This does not obtain root.
set -eu
[ "$(id -u)" = 0 ] || { echo 'Run this installer with su.'; exit 1; }
[ "$(getprop ro.product.cpu.abi)" = arm64-v8a ] || { echo 'This bundle requires arm64 Android.'; exit 1; }
[ "$(getenforce)" = Enforcing ] || { echo 'SELinux must be enforcing.'; exit 1; }
SOURCE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BASE=/data/adb/uk-hook-switch
for name in controller region.js ctl.sh; do
  [ -s "$SOURCE/$name" ] || { echo "Missing bundle file: $name"; exit 1; }
done
umask 077
# The old control script validates its controller PID before stopping it.
if [ -x "$BASE/ctl.sh" ]; then "$BASE/ctl.sh" stop; fi
mkdir -p "$BASE"
chmod 700 "$BASE"
for name in controller region.js ctl.sh; do
  cp "$SOURCE/$name" "$BASE/$name.new"
  case "$name" in region.js) chmod 600 "$BASE/$name.new" ;; *) chmod 700 "$BASE/$name.new" ;; esac
  mv "$BASE/$name.new" "$BASE/$name"
done
if [ ! -e "$BASE/targets.conf" ]; then : > "$BASE/targets.conf"; fi
chmod 600 "$BASE/targets.conf"
echo 'Engine installed. Open Region Switch, grant root, and enable your selected apps.'
