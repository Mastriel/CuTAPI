package xyz.mastriel.cutapi.resources.uploader

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.utils.cutConfigValue
import java.io.File
import java.net.InetAddress
import kotlin.concurrent.Volatile
import kotlin.concurrent.thread
import kotlin.random.Random

class BuiltinUploader : Uploader {
    override val id: Identifier = id(Plugin, "internal")

    private var thread: Thread? = null

    @Volatile
    private var engine: ApplicationEngine? = null

    @Suppress("HttpUrlsUsage")
    override suspend fun upload(file: File): String {
        val ip = inferIp()

        // bug on some minecraft versions requires adding a random value
        // to the end, otherwise you can never get new versions
        val url = "http://$ip:$PackPort/${Random.nextInt(0, Int.MAX_VALUE)}"
        Plugin.info("Pack URL is: $url")
        return url
    }

    private suspend fun inferIp() : String {
        var ip = ServerIp
        if (ip == "0.0.0.0") {
            ip = Bukkit.getIp()
            if (ip.isEmpty()) {
                ip = withContext(Dispatchers.IO) {
                    InetAddress.getLocalHost().hostAddress
                }
            }
        }
        return ip
    }

    override fun setup() {
        thread = thread(name = "Resource Pack Server", isDaemon = true) {
            engine = embeddedServer(Netty, port = PackPort) {
                routing {
                    get("*") {
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

    companion object {
        val ServerIp by cutConfigValue("uploader.ip-address", "0.0.0.0")
        val PackPort by cutConfigValue("uploader.port", 32120)
    }
}