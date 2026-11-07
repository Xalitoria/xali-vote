import mc.xalitoria.gradle.sibling

plugins {
    kotlin("plugin.serialization")
    alias(libs.plugins.shadow)
}

// Declared with api, so the proxy downloads the libraries when the plugin loads (Velocity has none of them) and the
// plugin gets a loader class; nothing declared with api is shaded.
dependencies {
    implementation(sibling("common"))

    api(kotlin("stdlib"))
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.serialization)
    api(libs.kaml)
    api(libs.bundles.exposed)
    api(libs.bundles.database)
    api(libs.xalicraft.util)

    compileOnly(libs.velocity.api)
    compileOnly(libs.nuvotifier.velocity)
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
