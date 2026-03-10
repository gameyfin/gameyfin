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
# Explicit heap cap instead of MaxRAMPercentage to leave room for
# metaspace, native threads, NIO direct buffers and H2 file-mapped pages.
# -Xmx512m / -Xms128m  – hard heap ceiling; floor at 128 m so idle apps shrink.
# UseG1GC              – good general-purpose GC.
# G1HeapRegionSize=1m  – smaller regions improve reclamation at small heaps.
# MinHeapFreeRatio / MaxHeapFreeRatio – return unused heap to the OS faster.
# G1PeriodicGCInterval – triggers a concurrent GC every 15 s while idle so
#                        the free-ratio settings are actually evaluated and
#                        uncommitted pages are returned to the OS.
# UseStringDeduplication saves heap on duplicate String values.
# UseCompactObjectHeaders saves ~8 bytes per object reference on 64-bit JVMs.
# Reduced thread-stack size (-Xss512k) saves ~0.5 MB per thread.
# MaxMetaspaceSize     – bounds class-metadata growth (Vaadin/Spring load many).
# MaxDirectMemorySize  – caps NIO direct buffers.
# AOT Cache reduces startup time and memory footprint.
DEFAULT_JVM_OPTS="\
  -Xms128m \
  -Xmx512m \
  -XX:+UseG1GC \
  -XX:G1HeapRegionSize=1m \
  -XX:MinHeapFreeRatio=15 \
  -XX:MaxHeapFreeRatio=30 \
  -XX:G1PeriodicGCInterval=15000 \
  -XX:+UseStringDeduplication \
  -XX:+UseCompactObjectHeaders \
  -XX:MaxMetaspaceSize=192m \
  -XX:MaxDirectMemorySize=64m \
  -Xss512k"

# Append AOT Cache flag if a non-empty training cache file exists
if [ -s /opt/gameyfin/app.aot ]; then
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

exec gosu gameyfin:gameyfin java -Djava.net.preferIPv4Stack=true -jar application.jar
