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
  for d in plugins plugindata db data logs backups; do
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
  -Xss512k \
  -XX:ErrorFile=/opt/gameyfin/logs/hs_err_pid%p.log"
# ErrorFile            – the working directory (/opt/gameyfin) is not writable by the
#                        runtime user, so JVM crash reports were silently lost.

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

# Signal chaining: native libraries (libtorrent's try_signal in the torrent plugin) install
# process-wide SIGSEGV/SIGBUS handlers that replace the JVM's own. The JVM uses SIGSEGV
# internally (implicit null checks, safepoints), so without chaining it dies with exit 139
# and no crash report. libjsig makes such handlers chain behind the JVM's handler.
# jemalloc replaces glibc malloc: glibc's per-thread arenas fragment badly with many threads doing
# native allocations (ImageIO/libjpeg, H2, Tomcat NIO) and rarely return memory to the OS, so RSS
# creeps up even though the heap is capped. jemalloc must be preloaded *after* libjsig.
# Set DISABLE_JEMALLOC=true to fall back to glibc malloc.
JSIG="${JAVA_HOME:-/opt/java/openjdk}/lib/libjsig.so"
JEMALLOC=""
if [ "${DISABLE_JEMALLOC:-false}" != "true" ]; then
  JEMALLOC="$(find /usr/lib -maxdepth 2 -name libjemalloc.so.2 -print -quit 2>/dev/null || true)"
fi

if [ -z "${LD_PRELOAD:-}" ]; then
  PRELOAD=""
  [ -f "${JSIG}" ] && PRELOAD="${JSIG}"
  [ -n "${JEMALLOC}" ] && PRELOAD="${PRELOAD:+${PRELOAD}:}${JEMALLOC}"
  [ -n "${PRELOAD}" ] && export LD_PRELOAD="${PRELOAD}"
fi

if [ -n "${JEMALLOC}" ]; then
  # background_thread: purge unused pages in a separate thread so idle memory is returned to the OS
  export MALLOC_CONF="${MALLOC_CONF:-background_thread:true,dirty_decay_ms:5000,muzzy_decay_ms:5000,narenas:2}"
fi

exec gosu gameyfin:gameyfin java -Djava.net.preferIPv4Stack=true -jar application.jar
