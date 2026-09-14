# Reproducer for KTOR-9891

One route returning one JSON object on `embeddedServer(CIO)`, Kotlin/Native `linuxX64`, Ktor 3.5.2.
Under enough concurrency it stops serving: at 200 connections and 2 000 requests a second offered, it
answers about seven thousand of sixty thousand with a p99 around a second and a half, on three and a
half cores.

Issue: <https://youtrack.jetbrains.com/issue/KTOR-9891> · pull request:
<https://github.com/ktorio/ktor/pull/5874>

## Read this before running it

Two things decide whether you see anything at all.

**1. The machine needs four cores online.** This is the part that will waste your afternoon
otherwise. With cores taken *offline* — so that `sysconf(_SC_NPROCESSORS_ONLN)` itself returns fewer
— the collapse does not happen:

| Cores online | Slow runs | p99 | Requests in 30 s |
|---|---|---|---|
| 2 | 0 of 12 | 39.2 ms | 58 528 |
| 3 | 0 of 12 | 13.2 ms | 59 268 |
| 4 | 15 of 20 | 199 ms | 52 414 |

p = 7 × 10⁻⁸ for the pooled comparison. **A two-vCPU VM will show nothing and look like a failed
reproduction.** Check `nproc` first. A cpuset is not a substitute in either direction: restricting a
four-core machine to two CPUs leaves the online count at four and still collapses, so
`taskset`/`--cpuset-cpus` will not make it go away either.

Why the online count matters is the one thing in the original investigation that is still open. It is
not the thread count — a census of `/proc/<pid>/task` puts all three configurations between 75 and
146 distinct threads, with the arm that never goes slow building the most — and it is not anything
Kotlin can see: `KOTLIN_NATIVE_AVAILABLE_PROCESSORS`, which is what `Platform.getAvailableProcessors()`
honours and nothing below Kotlin reads, changes the rate not at all.

**2. One run proves nothing.** The same binary under the same load either answers in single-digit
milliseconds or in hundreds, decided in the first five seconds. Across 156 runs there was nothing in
between: every p99 was under 70 ms or over 110. So `run.sh` loops and reports the **share of runs that
land in the slow state**, and that share is the number to compare.

A generator on the same host competes with the subject for the same cores. Everything quoted here was
measured with k6 on a second machine over a private link; `GEN_HOST=user@host ./run.sh` does that.
Same-host runs still show the effect, with more noise.

## Running it

```bash
./gradlew linkReleaseExecutableLinuxX64
./run.sh                 # 10 runs at 200 connections
VUS=12 ./run.sh          # the contrast: same rate, twelve connections
```

Expected, on four cores, 2 000 rps offered:

| Connections | Slow runs | p99 | Requests served in 30 s |
|---|---|---|---|
| 12 | 0 of 10 | 5.7 ms | 59 362 |
| 50 | 7 of 10 | 352 ms | 48 884 |
| 200 | 10 of 10 | 1 620 ms | 6 361 |

**Concurrency, not rate, is what it cannot take.** The generator offers the same two thousand
requests a second to all three arms.

## A/B against the patch

`PATCHING.md` has the four lines that build `ktor-io` with the pull request's change and publish it
locally. Then:

```bash
./gradlew -PktorPatch=atomicfu linkReleaseExecutableLinuxX64
BIN=build/bin/linuxX64/releaseExecutable/repro-atomicfu.kexe ./run.sh
```

Twelve runs per arm on the original stand, four builds from one tree differing in that one
dependency:

| Build | 200 conn | p99 | Requests | 50 conn | p99 |
|---|---|---|---|---|---|
| 3.5.2 as released | 12/12 slow | 1 500 ms | 7 082 | 7/12 slow | 310 ms |
| pool made lock-free | 0/12 | 11.3 ms | 59 924 | 0/12 | 7.8 ms |
| lock delegated to atomicfu (the PR) | 0/12 | 8.4 ms | 59 928 | 0/12 | 6.5 ms |
| both | 0/12 | 8.8 ms | 60 053 | 0/12 | 6.4 ms |

## What it is

`HttpHeadersMap` keeps its storage in two process-wide `DefaultPool`s, and on Kotlin/Native
`DefaultPool` takes a `SynchronizedObject` on every `borrow` and every `recycle` — so every parsed
request takes two process-wide locks at least twice each, while the process's hundred-odd threads do
the same on the same two atomics. That lock is a CAS loop over an `AtomicReference<LockState>` which
allocates a `LockState` on every attempt, so a lost CAS is an allocation rather than a cheap retry and
the cost of an acquisition rises with the number of contenders.

`perf` on a slow run puts 32–73 % of all CPU inside `SynchronizedObject#lock`; gdb under load puts all
211 frames in that function under `DefaultPool#borrow` from `HttpHeadersMap`'s constructor and
`DefaultPool#recycle` from its `release`. The rung below HTTP parsing — raw sockets through
`ktor-network`, no `ktor-server-core` — goes slow in 0 of 12 runs, and a bare `epoll` loop with no
Ktor holds the same 2 000 rps in 5 MB at 1.52 ms.

## Environment these numbers come from

Two dedicated hosts, four cores and 7.7 GiB each, Ubuntu 26.04, subject and generator on separate
machines over a private link. Kotlin 2.4.10 `linuxX64` release binaries, Ktor 3.5.2, k6, 30 s per run,
runs interleaved between arms, memory bounded by a cgroup and read from it.

`linuxX64` is the only target measured. The lock lives in `ktor-io/posix`, the source set every native
target shares, so Apple and MinGW targets should behave the same — should, not do.
