#!/bin/sh
set -e

if [ -n "$PUID" ] && [ -n "$PGID" ]; then
  groupmod -o -g "$PGID" gameyfin
  usermod -o -u "$PUID" gameyfin
  chown -R gameyfin:gameyfin /opt/gameyfin
  exec su-exec gameyfin:gameyfin java -jar gameyfin.jar
else
  exec su-exec gameyfin:gameyfin java -jar gameyfin.jar
fi