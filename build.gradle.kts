import mc.xalitoria.gradle.Load
import mc.xalitoria.gradle.RelativeLoad

// Kotlin Multiplatform with a JVM target per platform: `common` is shared by both, `paper` and `velocity` are the
// plugins. The Gradle plugin reads the api() dependencies of each target's source sets for its loader.
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("mc.xalitoria.minecraft")
    // Not shading anything yet: the Gradle plugin only writes the loaders when the shadow plugin is applied.
    alias(libs.plugins.shadow)
}

group = "mc.xalitoria"
version = "0.0.1"

val kotlinVersion: String = libs.versions.kotlin.get()

kotlin {
    jvmToolchain(25)
    jvm("paper")
    jvm("velocity")

    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlin.uuid.ExperimentalUuidApi",
            "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=kotlinx.serialization.InternalSerializationApi",
            "-Xcontext-sensitive-resolution",
            "-Xcollection-literals",
            "-Xname-based-destructuring=complete",
        )
    }

    sourceSets {
        val commonMain by getting

        // What both platforms share: `common/`, and the libraries they download when the plugin loads.
        val sharedJvmMain by creating {
            kotlin.setSrcDirs(listOf("common/kotlin"))
            resources.setSrcDirs(listOf("common/resources"))
            dependsOn(commonMain)
            dependencies {
                api("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
                api(libs.kotlinx.coroutines)
                api(libs.kotlinx.serialization)
                api(libs.kaml)
                api(libs.bundles.exposed)
                api(libs.hikaricp)
                api(libs.sqlite)
                api(libs.mariadb)
                api(libs.mysql)
                api(libs.xalicraft.util)
            }
        }

        val nuvotifier = files("libs/nuvotifier-2.7.3.jar")

        val paperMain by getting {
            dependsOn(sharedJvmMain)
            dependencies {
                compileOnly(libs.paper.api)
                compileOnly(nuvotifier)
                api(libs.ktor.server.core)
                api(libs.ktor.server.cio)
                api(libs.ktor.server.content.negotiation)
                api(libs.ktor.serialization.json)
            }
        }

        val velocityMain by getting {
            dependsOn(sharedJvmMain)
            dependencies {
                compileOnly(libs.velocity.api)
                compileOnly(nuvotifier)
            }
        }
    }

    coreLibrariesVersion = kotlinVersion
}

paper {
    name = "XaliVote"
    main = "mc.xalitoria.vote.XaliVote"
    apiVersion = "1.21"
    author = "audizian"
    description = "Saves NuVotifier votes and dispatches reward commands on join."
    load = Load.POSTWORLD
    relocateSources = true
    serverDependencies {
        register("NuVotifier") {
            load = RelativeLoad.BEFORE
            required = true
        }
    }
}

velocity {
    name = "XaliVote"
    id = "xalivote"
    main = "mcv.xalitoria.vote.XaliVoteVelocity"
    author = "audizian"
    description = "Saves NuVotifier votes and dispatches reward commands on player login."
    relocateSources = true
    dependencies {
        register("nuvotifier") { required = true }
    }
}

tasks {
    // The shadow plugin is only applied so the loaders get written; each target's own jar is the plugin.
    named("shadowJar") { enabled = false }
    metadataProcessResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    build {
        dependsOn(named("paperJar"), named("velocityJar"))
    }
}
