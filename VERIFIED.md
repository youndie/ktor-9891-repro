# This reproducer was run before it was handed over

Not the original probe — **this** tree, built by its own `build.gradle.kts` and driven by its own
`run.sh`, on the stand the issue's numbers come from: two dedicated hosts, four cores each, Ubuntu
26.04, k6 on the second host over a private link, 2 000 rps offered, 30 s per run.

```text
binary   repro.kexe
cores    4 online
load     2000 rps, 200 connections, 30s, 4 runs

run  1  p99   1.34 s  requests 26626    SLOW
run  2  p99   1.16 s  requests 30254    SLOW
run  3  p99   1.41 s  requests  7283    SLOW
run  4  p99   1.45 s  requests  6840    SLOW

slow runs: 4 of 4
```

The same binary, the same machine, the same offered rate, twelve connections instead of two hundred:

```text
load     2000 rps, 12 connections, 30s, 4 runs

run  1  p99   7.30 ms  requests 59005    fast
run  2  p99   5.64 ms  requests 59446    fast
run  3  p99   5.05 ms  requests 59483    fast
run  4  p99   5.89 ms  requests 59220    fast

slow runs: 0 of 4
```

Six thousand eight hundred requests against fifty-nine thousand, and a p99 two hundred times apart,
with nothing changed but the number of connections. That is the whole claim, and it is why `run.sh`
takes `VUS` as the first thing to vary.

Four runs per arm here, because this is a smoke check that the packaged reproducer behaves like the
original probe. The numbers quoted in the issue come from twelve runs per arm and are in
[README.md](README.md).

## Re-run against the release

17 September 2026, the same two hosts, this tree built twice from one source with `-PktorVersion`,
arms interleaved run for run:

```text
load     2000 rps, 200 connections, 30s, 12 rounds per arm

ktor 3.5.2   slow 12 of 12   p99 median   1.50 s   requests median   6 850
ktor 3.6.0   slow  0 of 12   p99 median  23.7 ms   requests median  59 700
```

A second sweep the same day put the patched `ktor-io` of [PATCHING.md](PATCHING.md) beside the
release: 0 of 10 slow in both arms, medians 23.7 ms and 24.1 ms. The release behaves like the patch
it shipped, and the 8.4 ms in [README.md](README.md) belongs to the day it was measured on.
