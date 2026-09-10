#!/usr/bin/env bash
# Smoke-tests every astro-engine endpoint. Boots the built server, curls each
# route, prints status + a short body preview, then shuts the server down.
set -u

BASE="http://127.0.0.1:8090"
APP="build/install/astro-engine/bin/astro-engine"

# --- boot ---
VEDIC_ENGINE_PORT=8090 "$APP" >/tmp/astro-engine.log 2>&1 &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null' EXIT

# wait for /health
for i in $(seq 1 60); do
  if curl -sf "$BASE/health" >/dev/null 2>&1; then break; fi
  sleep 0.5
done

pass=0; fail=0
check() { # name method path [json]
  local name="$1" method="$2" path="$3" body="${4:-}"
  local out code
  if [ "$method" = "GET" ]; then
    out=$(curl -s -w $'\n%{http_code}' "$BASE$path")
  else
    out=$(curl -s -w $'\n%{http_code}' -X "$method" -H 'Content-Type: application/json' -d "$body" "$BASE$path")
  fi
  code=$(printf '%s' "$out" | tail -n1)
  local json; json=$(printf '%s' "$out" | sed '$d')
  local preview; preview=$(printf '%s' "$json" | cut -c1-220)
  if [ "$code" = "200" ]; then
    pass=$((pass+1)); echo "PASS [$code] $name $method $path"
  else
    fail=$((fail+1)); echo "FAIL [$code] $name $method $path"
  fi
  echo "     $preview"
}

AT='"at":"2026-09-08T09:00:00"'
LOC='"latitude":19.0760,"longitude":72.8777,"zone":"Asia/Kolkata"'
BIRTH='"birth":{"at":"1990-05-15T08:30:00","latitude":19.0760,"longitude":72.8777}'

echo "===== astro-engine endpoint tests ====="
check "index"           GET  "/"
check "health"          GET  "/health"
check "snapshot"        POST "/v1/snapshot"             "{$LOC,$AT}"
check "day-summary"     POST "/v1/day-summary"          "{$LOC,$AT}"
check "panchanga-now"   POST "/v1/panchanga-now"        "{$LOC,$AT}"
check "planetary-pos"   POST "/v1/planetary-positions"  "{$AT}"
check "sunrise"         POST "/v1/sunrise"              "{$LOC,$AT}"
check "festivals"       POST "/v1/festivals"            "{$LOC,$AT,\"withinDays\":90,\"limit\":10}"
check "festival-on"     POST "/v1/festival-on"          "{$LOC,$AT}"
check "next-tithi"      POST "/v1/next-tithi"           "{$LOC,$AT,\"tithis\":[15,30],\"withinDays\":60}"
check "natal-chart"     POST "/v1/natal-chart"          "{$LOC,\"at\":\"1990-05-15T08:30:00\",\"dashaDepth\":2}"
check "muhurta"         POST "/v1/muhurta"              "{$LOC,$AT,\"activity\":\"VIVAH\",\"days\":30,$BIRTH}"
check "muhurta-acts"    GET  "/v1/muhurta/activities"
check "panchak"         POST "/v1/panchak"              "{$LOC,$AT,\"withinDays\":40,\"limit\":2}"
check "rashifal"        POST "/v1/rashifal"             "{\"rashi\":\"Mesha\",$LOC,$AT,\"days\":7,$BIRTH}"
echo "----- error handling -----"
check "bad-activity"    POST "/v1/muhurta"              "{$LOC,$AT,\"activity\":\"NOPE\"}"   # expect 400 -> FAIL line
check "bad-rashi"       POST "/v1/rashifal"             "{\"rashi\":\"Nope\",$LOC}"          # expect 400 -> FAIL line

echo "======================================="
echo "PASS=$pass FAIL=$fail (the two error-handling cases are expected non-200)"
