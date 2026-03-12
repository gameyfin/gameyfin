#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# AOT cache training script
#
# Instead of the quick-exit  -Dspring.context.exit=onRefresh  approach, this
# script boots the full application, waits for readiness, fires HTTP requests
# that exercise the hot paths (Vaadin/Hilla, Spring Security, Hibernate, PF4J
# plugin loading), and then gracefully shuts the JVM down.
#
# Because the JVM is started with  -XX:AOTCacheOutput=…  it records method
# profiling data for every code path that actually ran, producing a much
# richer AOT cache than the "exit on refresh" strategy.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Configurable knobs ────────────────────────────────────────────────────────
APP_PORT=8080
MGMT_PORT=8081
HEALTH_URL="http://localhost:${MGMT_PORT}/actuator/health/readiness"
MAX_WAIT=300          # seconds to wait for the app to become ready
WARMUP_PAUSE=2        # seconds to sleep after warmup before sending SIGTERM

# Dummy AES key — only used inside the ephemeral training container
export APP_KEY="bknLhYui9N21X2z3sIR+HR9LHI9STMMOZRz5K8nFzJY="

# ── Start the application in the background ───────────────────────────────────
# On x86-64, restrict to baseline SSE2 (UseAVX=0) so the AOT cache is portable across all x86 CPUs
# The flag is x86-only and must not be passed on aarch64.
ARCH_OPTS=""
case "$(uname -m)" in
    x86_64|amd64) ARCH_OPTS="-XX:UseAVX=0" ;;
esac

echo "▶ Starting application for AOT training …"
java -XX:AOTCacheOutput=app.aot \
     -XX:+UseCompactObjectHeaders \
     $ARCH_OPTS \
     -jar application.jar --spring.profiles.active=aot-training &
APP_PID=$!

# ── Wait for readiness ────────────────────────────────────────────────────────
echo "Waiting for health endpoint (${HEALTH_URL}) …"
elapsed=0
until curl -sf "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'; do
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        echo "✗ Application exited prematurely (PID ${APP_PID})."
        exit 1
    fi
    if [ "$elapsed" -ge "$MAX_WAIT" ]; then
        echo "✗ Timed out after ${MAX_WAIT}s waiting for the app to start."
        kill "$APP_PID" 2>/dev/null || true
        exit 1
    fi
    sleep 1s
    elapsed=$((elapsed + 1))
done
echo "✓ Application is ready (took ~${elapsed}s)."

# ── Warm-up requests ─────────────────────────────────────────────────────────
# Each request exercises a different slice of the runtime:
#   • Vaadin Hilla page rendering + React SSR bootstrap
#   • Spring Security filter chain
#   • Hilla JSON-RPC endpoint serialisation (SetupEndpoint, UserEndpoint)
#   • Actuator / Micrometer metrics
echo "Sending warm-up requests …"

# Helper: ignore HTTP errors — we only care that the JVM executes the code path.
warmup() {
    echo "   → $1"
    curl -sf -o /dev/null -w "     HTTP %{http_code} (%{time_total}s)\n" "$@" || true
}

warmup_post() {
    local url="$1"
    local body="${2:-{}}"
    echo "   → POST $url"
    curl -sf -o /dev/null -w "     HTTP %{http_code} (%{time_total}s)\n" \
         -X POST \
         -H "Content-Type: application/json" \
         -d "$body" \
         "$url" || true
}

# -- Vaadin / static pages --
warmup "http://localhost:${APP_PORT}/"
warmup "http://localhost:${APP_PORT}/login"
warmup "http://localhost:${APP_PORT}/setup"

# -- Hilla endpoints (JSON-RPC style: POST /connect/<Endpoint>/<method>) --
warmup_post "http://localhost:${APP_PORT}/connect/SetupEndpoint/isSetupCompleted"
warmup_post "http://localhost:${APP_PORT}/connect/UserEndpoint/getUserInfo"
warmup_post "http://localhost:${APP_PORT}/connect/MessageEndpoint/isEnabled"
warmup_post "http://localhost:${APP_PORT}/connect/ConfigEndpoint/areGameRequestsEnabled"
warmup_post "http://localhost:${APP_PORT}/connect/PlatformEndpoint/getStats"

# -- Actuator --
warmup "http://localhost:${MGMT_PORT}/actuator/health"
warmup "http://localhost:${MGMT_PORT}/actuator/info"
warmup "http://localhost:${MGMT_PORT}/actuator/metrics"

echo "✓ Warm-up complete."

# ── Graceful shutdown ─────────────────────────────────────────────────────────
echo "⏳ Pausing ${WARMUP_PAUSE}s before shutdown …"
sleep "$WARMUP_PAUSE"

echo "⏹ Sending SIGTERM to PID ${APP_PID} …"
kill "$APP_PID" 2>/dev/null || true

# Wait for the process to exit
if wait "$APP_PID" 2>/dev/null; then
    echo "✓ Application exited cleanly."
else
    echo "✓ Application exited (code $?)."
fi

# ── Verify the AOT cache was produced ─────────────────────────────────────────
if [ -s app.aot ]; then
    echo "✓ AOT cache written ($(du -h app.aot | cut -f1))."
else
    echo "✗ AOT cache file is missing or empty!"
    exit 1
fi

