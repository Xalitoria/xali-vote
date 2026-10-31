import mc.xalitoria.gradle.Load
import mc.xalitoria.gradle.RelativeLoad

plugins {
    kotlin("multiplatform")
    id("mc.xalitoria.conventions")
    kotlin("plugin.serialization")
    id("mc.xalitoria.minecraft")
    alias(libs.plugins.shadow)
}

group = "mc.xalitoria"
version = "0.0.1"

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
        val commonMain = getByName("commonMain")

        // What both platforms share: `common/`, and the libraries they download when the plugin loads.
        val sharedJvmMain by creating {
            kotlin.setSrcDirs(listOf("common/kotlin"))
            resources.setSrcDirs(listOf("common/resources"))
            dependsOn(commonMain)
            dependencies {
                api(kotlin("stdlib"))
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

        val paperMain by getting {
            dependsOn(sharedJvmMain)
            dependencies {
                compileOnly(libs.paper.api)
                compileOnly(libs.nuvotifier.universal)
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
                compileOnly(libs.nuvotifier.velocity)
            }
        }
    }
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
    main = "mcv.xalitoria.vote.XaliVote"
    author = "audizian"
    description = "Saves NuVotifier votes and dispatches reward commands on player login."
    relocateSources = true
    dependencies {
        register("nuvotifier") { required = true }
    }
}

tasks {
    named("shadowJar") { enabled = false }
    metadataProcessResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    build {
        dependsOn(named("paperJar"), named("velocityJar"))
    }
}
