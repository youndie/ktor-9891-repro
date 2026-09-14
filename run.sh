#!/usr/bin/env bash
# Run the server N times under load and print how many runs landed in the slow state.
#
#   ./run.sh                      # 10 runs at 200 connections
#   VUS=12 ./run.sh               # 10 runs at 12 connections, for the contrast
#   BIN=build/bin/linuxX64/releaseExecutable/repro-atomicfu.kexe ./run.sh
#
# Why a loop and not one run: this service has two speeds. The same binary under the same load
# either answers in single-digit milliseconds or in hundreds, decided in the first seconds, with
# nothing in between. A single p99 is a coin toss; the number that means something is the share of
# runs that land in the slow state.
set -uo pipefail

BIN="${BIN:-build/bin/linuxX64/releaseExecutable/repro.kexe}"
RUNS="${RUNS:-10}"
VUS="${VUS:-200}"
RPS="${RPS:-2000}"
DURATION="${DURATION:-30s}"
SLOW_MS="${SLOW_MS:-100}"
PORT="${PORT:-8080}"
# Set GEN_HOST to run k6 from another machine over ssh; a generator on the same host competes with
# the subject for the same cores and flatters the result.
GEN_HOST="${GEN_HOST:-}"
TARGET="${TARGET:-127.0.0.1:$PORT}"

[ -f "$BIN" ] || { echo "no $BIN — run ./gradlew linkReleaseExecutableLinuxX64 first" >&2; exit 1; }
command -v k6 > /dev/null || [ -n "$GEN_HOST" ] || { echo "k6 not found" >&2; exit 1; }

echo "binary   $BIN"
echo "cores    $(grep -c ^processor /proc/cpuinfo) online   <-- see README: two or three do not reproduce"
echo "load     $RPS rps, $VUS connections, $DURATION, $RUNS runs"
echo

slow=0
for i in $(seq 1 "$RUNS"); do
    "$BIN" "$PORT" > /dev/null 2>&1 &
    server=$!
    for _ in $(seq 1 50); do
        (exec 3<>/dev/tcp/127.0.0.1/"$PORT") 2>/dev/null && break
        sleep 0.1
    done

    if [ -n "$GEN_HOST" ]; then
        out=$(ssh -o BatchMode=yes "$GEN_HOST" \
            "env TARGET='$TARGET' VUS='$VUS' RPS='$RPS' DURATION='$DURATION' k6 run --quiet -" < load.js 2>&1)
    else
        out=$(TARGET="$TARGET" VUS="$VUS" RPS="$RPS" DURATION="$DURATION" k6 run --quiet load.js 2>&1)
    fi

    kill "$server" 2>/dev/null
    wait "$server" 2>/dev/null

    line=$(grep -m1 http_req_duration <<< "$out")
    p99=$(sed -n 's/.*p(99)=\([0-9.]*\)\([a-z]*\).*/\1 \2/p' <<< "$line")
    reqs=$(grep -m1 http_reqs <<< "$out" | tr -s ' ' | cut -d' ' -f3)
    ms=$(awk -v v="${p99%% *}" -v u="${p99##* }" 'BEGIN{ if (u=="s") print v*1000; else if (u=="ms") print v; else print v/1000 }')
    verdict=fast
    awk -v m="$ms" -v t="$SLOW_MS" 'BEGIN{ exit !(m>t) }' && { verdict=SLOW; slow=$((slow + 1)); }
    printf 'run %2d  p99 %8s  requests %-8s %s\n' "$i" "$p99" "$reqs" "$verdict"
    sleep 3
done

echo
echo "slow runs: $slow of $RUNS"
