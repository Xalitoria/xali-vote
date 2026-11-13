package com.xalitoria.vote.protocol

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/** Where each side finds Velocity's forwarding secret, which signs the messages between the proxy plugin and the servers. */
object ForwardingSecret {
    /**
     * A server's own: `proxies.velocity.secret` of `config/paper-global.yml` under [serverFolder], the folder that holds `plugins`.
     * Empty unless Velocity forwarding is enabled there, which is also how a server knows it is behind Velocity. Paper reads the
     * file once at startup, so this is asked once as well.
     */
    fun ofServer(serverFolder: Path): String = try {
        val file = serverFolder.resolve("config/paper-global.yml")
        if (!file.exists()) ""
        else Yaml(configuration = YamlConfiguration(strictMode = false))
            .decodeFromString(PaperGlobal.serializer(), file.readText())
            .proxies.velocity.takeIf { it.enabled }?.secret.orEmpty()
    } catch (e: Exception) {
        ""
    }

    /**
     * The proxy's, found the way Velocity finds it: `VELOCITY_FORWARDING_SECRET`, else the file named by `forwarding-secret-file`
     * in `velocity.toml` (`forwarding.secret` when unset), under [proxyFolder]. The API does not expose it. Null when it cannot be read.
     */
    fun ofProxy(proxyFolder: Path): String? {
        System.getenv("VELOCITY_FORWARDING_SECRET")?.takeIf { it.isNotBlank() }?.let { return it }
        return try {
            val name = runCatching { FILE.find(proxyFolder.resolve("velocity.toml").readText())?.groupValues?.getOrNull(1) }.getOrNull() ?: "forwarding.secret"
            proxyFolder.resolve(name).readText().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private val FILE = Regex("""(?m)^\s*forwarding-secret-file\s*=\s*"([^"]*)"""")

    @Serializable
    private class PaperGlobal(val proxies: Proxies = Proxies()) {
        @Serializable
        class Proxies(val velocity: Velocity = Velocity())

        @Serializable
        class Velocity(val enabled: Boolean = false, val secret: String = "")
    }
}
