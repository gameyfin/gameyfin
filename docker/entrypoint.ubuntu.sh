#!/bin/bash
set -e

# Optional UID/GID remapping for mounted volumes
PUID=${PUID:-}
PGID=${PGID:-}

if [ -n "$PGID" ]; then
  groupmod -o -g "$PGID" gameyfin
fi
if [ -n "$PUID" ]; then
  usermod -o -u "$PUID" gameyfin
fi

# Only fix ownership on writable dirs when remapping is requested
if [ -n "$PUID$PGID" ]; then
  for d in plugins plugindata db data logs; do
    [ -d "/opt/gameyfin/$d" ] || mkdir -p "/opt/gameyfin/$d"
    chown -R gameyfin:gameyfin "/opt/gameyfin/$d"
  done
fi

export JAVA_TOOL_OPTIONS="${JAVA_OPTS:-}"

exec gosu gameyfin:gameyfin java org.springframework.boot.loader.launch.JarLauncher