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
    version = "0.3.0"
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}

paper {
    name = "XaliVote"
    main = "mc.xalitoria.vote.paper.XaliVote"
    apiVersion = "1.21"
    author = "audizian"
    description = "Saves NuVotifier votes, rewards them, runs vote parties and lists the vote links, with XaliMenu, XaliRedeem and PlaceholderAPI hooks."
    load = Load.POSTWORLD
    foliaSupported = true
    relocateSources = true
    serverDependencies {
        register("Votifier") {
            load = RelativeLoad.BEFORE
            required = true
        }
        register("PlaceholderAPI") { load = RelativeLoad.BEFORE; required = false }
        // The menu's catalogs and action are registered while this plugin enables, so XaliMenu has to have enabled.
        register("XaliMenu") { load = RelativeLoad.BEFORE; required = false }
        // Only so that its commands exist by the time rewards run; nothing of it is loaded.
        register("XaliRedeem") { load = RelativeLoad.BEFORE; required = false }
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
