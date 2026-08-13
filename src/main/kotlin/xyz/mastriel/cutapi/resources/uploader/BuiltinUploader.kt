package xyz.mastriel.cutapi.resources.uploader

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bukkit.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import java.io.*
import kotlin.concurrent.*

public class BuiltinUploader : Uploader {
    override val id: Identifier = id(Plugin, "internal")

    private var thread: Thread? = null

    @Volatile
    private var engine: ApplicationEngine? = null

    @Suppress("HttpUrlsUsage")
    override suspend fun upload(file: File): String {
        val url = urlForConnection(null)
        if (ServerIp == AutomaticAddress) {
            Plugin.info("Pack server is using per-player connection addresses (fallback URL: $url)")
        } else {
            Plugin.info("Pack URL is: $url")
        }
        return url
    }

    internal fun urlForConnection(handshakeHostname: String?): String {
        val host = ServerIp
            .takeUnless { it == AutomaticAddress }
            ?: resourcePackHostFromHandshake(handshakeHostname)
            ?: Bukkit.getIp().takeIf(String::isNotBlank)
            ?: LoopbackAddress
        return resourcePackHttpUrl(host, PackPort)
    }

    override fun setup() {
        thread = thread(name = "Resource Pack Server", isDaemon = true) {
            engine = embeddedServer(Netty, port = PackPort) {
                routing {
                    get("/") {
                        Plugin.info("Getting request for resource pack")
                        val packFile = CuTAPI.resourcePackManager.zipFile
                        if (packFile.exists()) call.respondFile(packFile)
                        else call.respond(HttpStatusCode.NotFound)
                    }
                }
            }.start(wait = true)

        }
    }

    override fun teardown() {
        engine?.stop()
        thread = null
        engine = null
    }

    public companion object {
        private const val AutomaticAddress: String = "0.0.0.0"
        private const val LoopbackAddress: String = "127.0.0.1"

        public val ServerIp: String by cutConfigValue("uploader.ip-address") { "0.0.0.0" }
        public val PackPort: Int by cutConfigValue("uploader.port") { 32120 }
    }
}
