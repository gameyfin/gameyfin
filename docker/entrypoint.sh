#!/bin/sh
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

# Activate Docker profile for Docker-specific Spring Boot configuration
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-docker}"
export SPRING_PROFILES_ACTIVE

# JVM flags for proper signal handling in Docker with native libraries (jlibtorrent)
JVM_OPTS="-Djava.net.preferIPv4Stack=true"
JVM_OPTS="$JVM_OPTS -XX:+UseContainerSupport"
JVM_OPTS="$JVM_OPTS -XX:+ExitOnOutOfMemoryError"
JVM_OPTS="$JVM_OPTS -Djdk.lang.Process.launchMechanism=vfork"

exec su-exec gameyfin:gameyfin java "$JVM_OPTS" org.springframework.boot.loader.launch.JarLauncher
