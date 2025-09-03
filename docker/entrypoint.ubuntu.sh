#!/bin/bash
set -e

if [ -n "$PUID" ] && [ -n "$PGID" ]; then
  groupmod -o -g "$PGID" gameyfin
  usermod -o -u "$PUID" gameyfin
  chown -R gameyfin:gameyfin /opt/gameyfin
  exec gosu gameyfin:gameyfin java -jar gameyfin.jar
else
  exec gosu gameyfin:gameyfin java -jar gameyfin.jar
fi

