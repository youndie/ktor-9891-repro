# Building `ktor-io` with the patch

The A/B in [README.md](README.md) needs `io.ktor:ktor-io:3.5.2-atomicfu` in your local Maven
repository. It is Ktor 3.5.2 with one file replaced — the file that
[ktorio/ktor#5874](https://github.com/ktorio/ktor/pull/5874) changes.

```bash
git clone --depth 1 --branch 3.5.2 https://github.com/ktorio/ktor.git
cd ktor
# replace ktor-io/posix/src/io/ktor/utils/io/locks/Synchronized.kt with the PR's version
echo -n 3.5.2-atomicfu > VERSION
./gradlew -Pkotlin.native.enableKlibsCrossCompilation=true \
    :ktor-io:publishLinuxX64PublicationToMavenLocal \
    :ktor-io:publishKotlinMultiplatformPublicationToMavenLocal
```

The cross-compilation flag is only needed when publishing a `linuxX64` artifact from a Mac; Ktor's
`gradle.properties` turns klib cross-compilation off.

## One trap worth an afternoon

The obvious spelling of the change does not work:

```kotlin
public actual typealias SynchronizedObject = kotlinx.atomicfu.locks.SynchronizedObject
```

It compiles, links, starts, and dies on the first request:

```text
IrLinkageError: Property accessor 'lock.<get-lock>' can not be called:
uses unlinked class symbol 'io.ktor.utils.io.locks/SynchronizedObject'
```

`ktor-utils` and `ktor-network` are pre-built klibs that reference that **class** by symbol. A
typealias deletes the class, and Kotlin/Native's partial linkage reports the break at runtime rather
than at link time — so the build is clean and the binary is dead. Keeping the class and delegating
its three methods avoids that and preserves the ABI of everything else. That is what the pull request
does.

Seventy-two runs of one sweep were spent on a binary built the first way before anyone noticed,
because the smoke test in front of it checked for the server's `READY` line — which is printed before
`start()`, so it could not have failed however broken the server was.
