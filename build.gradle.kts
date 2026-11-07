import mc.xalitoria.gradle.Load
import mc.xalitoria.gradle.RelativeLoad

plugins {
    kotlin("jvm")
    id("mc.xalitoria.conventions")
    id("mc.xalitoria.minecraft")
    alias(libs.plugins.shadow) apply false
}

subprojects {
    // Named for the package of what the module holds, see the Packages section of the root README.
    group = when (name) {
        "common" -> "com.xalitoria.vote"
        "paper" -> "mc.xalitoria.vote.paper"
        "velocity" -> "mc.xalitoria.vote.velocity"
        else -> error("No group for $path")
    }
    version = "0.1.0"
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}

paper {
    name = "XaliVote"
    main = "mc.xalitoria.vote.paper.XaliVote"
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
    main = "mc.xalitoria.vote.velocity.XaliVote"
    author = "audizian"
    description = "Saves NuVotifier votes and dispatches reward commands on player login."
    relocateSources = true
    dependencies {
        register("nuvotifier") { required = true }
    }
}
