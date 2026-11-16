#!/usr/bin/env bash
#
# Compares the reactive and the virtual thread endpoints under load, against a local stub with a fixed
# latency. No request reaches Finnhub. Package first: mvn package -DskipTests
#
#   ./compare.sh                        defaults below
#   DELAY_MS=50 DURATION=30s ./compare.sh
#
# A cold JVM distorts the first high load run, whichever model it measures. The default CONNECTIONS ramp up
# for that reason. With a single high value, list the first model twice and discard its first result.
#
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

DELAY_MS="${DELAY_MS:-200}"
DURATION="${DURATION:-20s}"
CONNECTIONS="${CONNECTIONS:-100 500 1000 2000}"
MODELS="${MODELS:-reactive virtual offload-worker offload-virtual await}"
POOL_SIZE="${POOL_SIZE:-4000}"
APP_PORT="${APP_PORT:-18090}"
STUB_PORT="${STUB_PORT:-18999}"
JAR=target/quarkus-app/quarkus-run.jar
APP_LOG="$(mktemp)"

command -v wrk >/dev/null || { echo "wrk is required" >&2; exit 1; }
[[ -f "$JAR" ]] || { echo "Package first: mvn package -DskipTests" >&2; exit 1; }
ulimit -n 65536 2>/dev/null || true

pids=()
cleanup() { kill "${pids[@]}" 2>/dev/null || true; }
trap cleanup EXIT

java src/test/java/fr/simplex_software/workshop/mutiny_virtual_threads/LatencyStub.java "$STUB_PORT" "$DELAY_MS" &
pids+=($!)

# The connection pool caps both models at pool size / latency, so it is raised well above the load.
java -Dquarkus.http.port="$APP_PORT" \
     -Dquarkus.rest-client.finnhub.url="http://127.0.0.1:$STUB_PORT" \
     -Dquarkus.rest-client.finnhub.connection-pool-size="$POOL_SIZE" \
     -Dfinnhub.token=load-test \
     -jar "$JAR" >"$APP_LOG" 2>&1 &
pids+=($!)

for _ in $(seq 1 60); do
  curl -sf "http://127.0.0.1:$APP_PORT/q/health/ready" >/dev/null && break
  sleep 1
done
curl -sf "http://127.0.0.1:$STUB_PORT/quote" >/dev/null

width=5
for model in $MODELS; do (( ${#model} > width )) && width=${#model}; done

RESULTS=target/compare-results.txt
echo "# model connections requests/s p50 p99 errors (latency ${DELAY_MS} ms, ${DURATION} per run)" >"$RESULTS"

progress() { [[ -t 2 ]] && printf '\r\033[K%s' "$*" >&2 || true; }
total=$(( $(wc -w <<<"$MODELS") * $(wc -w <<<"$CONNECTIONS") ))
run=0

for model in $MODELS; do
  url="http://127.0.0.1:$APP_PORT/api/v1/quote/$model/AAPL"
  progress "[$run/$total] $model, warming up..."
  wrk -t2 -c50 -d10s "$url" >/dev/null
  for connections in $CONNECTIONS; do
    run=$((run + 1))
    progress "[$run/$total] $model, $connections connections..."
    out=$(wrk -t4 -c"$connections" -d"$DURATION" --timeout 30s --latency "$url")
    rps=$(awk '/Requests\/sec/{print $2}' <<<"$out")
    p50=$(awk '$1=="50%"{print $2}' <<<"$out")
    p99=$(awk '$1=="99%"{print $2}' <<<"$out")
    errors=$(awk '/Non-2xx/{non=$NF} /Socket errors/{gsub(",","");sock=$4+$6+$8+$10} END{print non+sock+0}' <<<"$out")
    echo "$model $connections $rps $p50 $p99 $errors" >>"$RESULTS"
  done
done
progress ""

color=0
[[ -t 1 && -z "${NO_COLOR:-}" ]] && color=1
echo "Upstream latency ${DELAY_MS} ms, ${DURATION} per run, ceiling = connections / latency"
[[ $color == 1 ]] && echo "Green = best, red = worst, among the models at the same connection count"
echo
# The stable sort keeps the MODELS order inside each group.
grep -v '^#' "$RESULTS" | sort -s -n -k2,2 | awk -v width="$width" -v color="$color" '
  function ms(v,   n) {
    n = v + 0
    if (v ~ /us$/) return n / 1000
    if (v ~ /ms$/) return n
    if (v ~ /m$/)  return n * 60000
    if (v ~ /s$/)  return n * 1000
    return n
  }
  function paint(text, value, best, worst) {
    if (!color || best == worst) return text
    if (value == best)  return "\033[1;32m" text "\033[0m"
    if (value == worst) return "\033[31m" text "\033[0m"
    return text
  }
  function flush(   i, line, bold, reset) {
    for (i = 1; i <= n; i++) {
      line = sprintf("%-" width "s %12s ", model[i], conn[i])
      line = line paint(sprintf("%12s", rps[i]), rps[i] + 0, max_rps, min_rps) " "
      line = line paint(sprintf("%10s", p50[i]), ms(p50[i]), min_p50, max_p50) " "
      line = line paint(sprintf("%10s", p99[i]), ms(p99[i]), min_p99, max_p99) " "
      e = sprintf("%8s", err[i])
      line = line ((color && err[i] > 0) ? "\033[31m" e "\033[0m" : e)
      print line
    }
    n = 0
  }
  BEGIN {
    bold = color ? "\033[1m" : ""; reset = color ? "\033[0m" : ""
    printf "%s%-" width "s %12s %12s %10s %10s %8s%s\n", bold, "model", "connections", "requests/s", "p50", "p99", "errors", reset
  }
  $2 != current {
    if (n) { flush(); print "" }
    current = $2
  }
  {
    n++
    model[n] = $1; conn[n] = $2; rps[n] = $3; p50[n] = $4; p99[n] = $5; err[n] = $6
    if (n == 1 || $3 + 0 > max_rps) max_rps = $3 + 0
    if (n == 1 || $3 + 0 < min_rps) min_rps = $3 + 0
    if (n == 1 || ms($4) < min_p50) min_p50 = ms($4)
    if (n == 1 || ms($4) > max_p50) max_p50 = ms($4)
    if (n == 1 || ms($5) < min_p99) min_p99 = ms($5)
    if (n == 1 || ms($5) > max_p99) max_p99 = ms($5)
  }
  END { if (n) flush() }
'

echo
echo "Raw results: $RESULTS"
echo "Application log, with any upstream failure: $APP_LOG"
