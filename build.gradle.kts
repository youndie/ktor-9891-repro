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

kotlin {
    linuxX64 {
        binaries.executable {
            entryPoint = "main"
            baseName = "repro" + (ktorPatch?.let { "-$it" } ?: "")
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
        implementation("io.ktor:ktor-server-cio:3.5.2")
        implementation("io.ktor:ktor-server-content-negotiation:3.5.2")
        implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    }
}
