# XaliVote

Saves NuVotifier votes and dispatches reward commands when players join. A Kotlin Multiplatform module with a JVM target
for each platform: `jvm("paper")` and `jvm("velocity")`, built from `common/` (shared), `paper/` and `velocity/`. The
`paper { }` and `velocity { }` blocks in `build.gradle.kts` come from the [Gradle plugin](../plugin/README.md), which
writes the descriptors and a loader for each platform that downloads the `api(...)` libraries when the plugin loads.

The Velocity plugin is `mcv.xalitoria.vote.XaliVoteVelocity`, created by the generated loader once its libraries are in.
Jars are built into `build/libs/vote/`.
