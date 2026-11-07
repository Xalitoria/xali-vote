import mc.xalitoria.gradle.sibling

plugins {
    kotlin("plugin.serialization")
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(sibling("common"))

    // Libraries the server downloads when the plugin loads (see loader.json); nothing declared here is shaded.
    api(kotlin("stdlib"))
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.serialization)
    api(libs.kaml)
    api(libs.bundles.exposed)
    api(libs.bundles.database)
    api(libs.xalicraft.util)
    api(libs.ktor.server.core)
    api(libs.ktor.server.cio)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.serialization.json)

    compileOnly(libs.paper.api)
    compileOnly(libs.nuvotifier.universal)
}

tasks {
    jar { enabled = false }
    shadowJar {
        minimize()
    }
    build {
        dependsOn(shadowJar)
    }
}
