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

# ── JVM memory tuning for containers ──────────────────────────────
# MaxRAMPercentage caps the heap to 75 % of that limit.
# UseG1GC is a good general-purpose GC for server apps with large heaps.
# UseStringDeduplication saves heap on duplicate String values.
# UseCompactObjectHeaders saves ~8 bytes per object reference on 64-bit JVMs.
# Reduced thread-stack size (-Xss512k) saves ~0.5 MB per thread.
# AOT Cache reduces startup time and memory footprint.
DEFAULT_JVM_OPTS="\
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:+UseStringDeduplication \
  -XX:+UseCompactObjectHeaders \
  -Xss512k"

# Append AOT Cache flag if the training cache file exists
if [ -f /opt/gameyfin/app.aot ]; then
  DEFAULT_JVM_OPTS="${DEFAULT_JVM_OPTS} -XX:AOTCache=/opt/gameyfin/app.aot"
fi

# Two env-var options for users (set in docker-compose "environment:"):
#   JAVA_OPTS          – extra flags *appended* to the built-in defaults
#                        e.g. JAVA_OPTS=-Dsome.prop=value
#   JAVA_OPTS_OVERRIDE – if set, *replaces* all built-in defaults entirely
#                        e.g. JAVA_OPTS_OVERRIDE=-Xmx512m -XX:+UseG1GC
if [ -n "${JAVA_OPTS_OVERRIDE:-}" ]; then
  export JDK_JAVA_OPTIONS="${JAVA_OPTS_OVERRIDE}"
else
  export JDK_JAVA_OPTIONS="${DEFAULT_JVM_OPTS} ${JAVA_OPTS:-}"
fi

exec gosu gameyfin:gameyfin java -Djava.net.preferIPv4Stack=true org.springframework.boot.loader.launch.JarLauncher