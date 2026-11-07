plugins {
    kotlin("plugin.serialization")
}

// What the Paper and Velocity plugins share: the reward rules, the configuration classes and the database. It uses no
// platform API, so both jars bundle it. The libraries are compileOnly: each platform module declares them with api, so
// the plugin downloads them when it loads. It uses util, which needs Java 21, so unlike refer's it does not target 17.
dependencies {
    compileOnly(libs.kotlinx.coroutines)
    compileOnly(libs.kotlinx.serialization)
    compileOnly(libs.kaml)
    compileOnly(libs.bundles.exposed)
    compileOnly(libs.hikaricp)
    compileOnly(libs.xalicraft.util)
}
