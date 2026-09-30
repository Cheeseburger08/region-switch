#!/system/bin/sh
set -eu
B=/data/adb/uk-hook-switch
live() {
  p=$(cat "$B/controller.pid" 2>/dev/null || true)
  case "$p" in ''|*[!0-9]*) return 1;; esac
  [ "$(readlink /proc/"$p"/exe 2>/dev/null || true)" = "$B/controller" ]
}
case "${1:-status}" in
  status)
    if live; then cat "$B/status.json"; else printf '{"phase":"stopped","targets":[]}\n'; fi ;;
  start)
    [ "$(getenforce)" = Enforcing ] || { echo 'SELinux must be enforcing'; exit 1; }
    if ! live; then "$B/controller" --daemon; fi
    i=0
    until live && grep -q '"phase":"listening"' "$B/status.json"; do
      i=$((i+1)); [ "$i" -lt 40 ] || { echo 'Controller did not start'; exit 1; }; sleep .25
    done ;;
  stop)
    if live; then
      # A cached Android app can be frozen, preventing the agent's unload reply.
      # Stop selected apps first, as documented by the control app.
      tab=$(printf '\t')
      while IFS="$tab" read -r package profile; do
        case "$package" in ''|*[!a-zA-Z0-9_.]*) continue;; esac
        su 2000 -c "am force-stop $package" < /dev/null
      done < "$B/targets.conf"
      kill -TERM "$p"
      i=0
      while live; do i=$((i+1)); [ "$i" -lt 60 ] || { echo 'Controller is still stopping'; exit 1; }; sleep .25; done
    fi ;;
  *) echo 'Unknown action'; exit 2 ;;
esac
