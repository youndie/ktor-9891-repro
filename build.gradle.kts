plugins {
    kotlin("multiplatform") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
}

repositories {
    // `-PktorPatch` resolves a locally published `io.ktor:ktor-io:3.5.2-atomicfu`, which is
    // Ktor 3.5.2 with the pull request's one file changed. Without the flag nothing comes from
    // here: that version does not exist on Central, so a plain build cannot pick it up by accident.
    mavenLocal {
        content { includeVersionByRegex("io\\.ktor", "ktor-io.*", "3\\.5\\.2-atomicfu") }
    }
    mavenCentral()
}

// `-PktorPatch=atomicfu` builds the same program against the patched ktor-io, so the two binaries
// differ by one file and nothing else. See PATCHING.md.
val ktorPatch = findProperty("ktorPatch") as String?

// `-PktorVersion=3.6.0` builds it against the release that carries the fix, which is the arm that
// matters now: the patch above only ever existed to answer the question this version answers.
val ktorVersion = (findProperty("ktorVersion") as String?) ?: "3.5.2"

require(ktorPatch == null || ktorVersion == "3.5.2") {
    "-PktorPatch patches ktor-io 3.5.2; it has nothing to substitute in $ktorVersion"
}

kotlin {
    linuxX64 {
        binaries.executable {
            entryPoint = "main"
            baseName = when {
                ktorPatch != null -> "repro-$ktorPatch"
                ktorVersion != "3.5.2" -> "repro-$ktorVersion"
                else -> "repro"
            }
        }
    }

    if (ktorPatch != null) {
        configurations.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "io.ktor" && requested.name.startsWith("ktor-io")) {
                    useVersion("3.5.2-$ktorPatch")
                    because("A/B against the patched lock")
                }
            }
        }
    }

    sourceSets.getByName("linuxX64Main").dependencies {
        implementation("io.ktor:ktor-server-cio:$ktorVersion")
        implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    }
}
