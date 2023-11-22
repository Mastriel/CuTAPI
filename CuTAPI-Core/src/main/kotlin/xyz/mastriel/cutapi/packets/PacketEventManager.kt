package xyz.mastriel.cutapi.packets

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.injector.packet.PacketRegistry
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.commands.PacketDebugCommand
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.onlinePlayers
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation

internal typealias PLibPacketEvent = com.comphenix.protocol.events.PacketEvent

private fun getAllPacketTypes() : List<PacketType> {
    return buildList {
        addAll(PacketRegistry.getServerPacketTypes())
        addAll(PacketRegistry.getClientPacketTypes())
        // Don't need these
        removeAll(listOf(
            PacketType.Login.Client.CUSTOM_PAYLOAD,
            PacketType.Login.Client.ENCRYPTION_BEGIN,
            PacketType.Login.Client.START,
            PacketType.Login.Server.CUSTOM_PAYLOAD,
            PacketType.Login.Server.ENCRYPTION_BEGIN,
            PacketType.Login.Server.DISCONNECT,
            PacketType.Login.Server.SUCCESS,
            PacketType.Login.Server.SET_COMPRESSION,
            PacketType.Status.Client.START,
            PacketType.Status.Client.PING,
            PacketType.Status.Server.SERVER_INFO,
            PacketType.Status.Server.PONG,
            PacketType.Handshake.Client.SET_PROTOCOL,

            // causes infinite recursion with packetdebug, plus everything needed in system chat
            // is available through the bukkit event
            PacketType.Play.Server.SYSTEM_CHAT
        ))
    }
}

internal class PacketEventManager(val packetManager: PacketManager) :
    PacketAdapter(Plugin, getAllPacketTypes()) {

    private val packetHandlers = mutableMapOf<KClass<WrappedPacket>, MutableList<PacketHandlerFunction<*>>>()
    private val packetListeners = mutableMapOf<PacketHandlerFunction<*>, PacketListener>()

    @Suppress("UNCHECKED_CAST")
    fun registerListener(vararg listeners: PacketListener) {
        listeners.forEach { listener ->
            val kClass = listener::class
            val handlers = kClass.functions
                .filter { it.hasAnnotation<PacketHandler>() }

            for (handler in handlers) {
                val priority = handler.findAnnotation<PacketHandler>()!!.priority

                val eventParameter = handler.parameters.getOrNull(1) ?:
                error("Invalid arguments in PacketHandler. Must have exactly 1 PacketEvent<WrappedPacket> argument.")

                require(eventParameter.type.classifier == PacketEvent::class)
                { "Argument is not a PacketEvent. (${PacketEvent::class} != ${eventParameter.type.classifier}" }

                val packetType = eventParameter.type.arguments.firstOrNull()?.type?.classifier as? KClass<WrappedPacket>
                    ?: error("Error in finding type argument for PacketEvent")

                val packetHandlerFunction = PacketHandlerFunction(priority, handler as PacketListener.(PacketEvent<WrappedPacket>) -> Unit)

                Plugin.info("Function ${getFunctionName(handler)} registered as a packet event handler.")
                packetHandlers.putIfAbsent(packetType, mutableListOf())
                packetHandlers[packetType]!! += packetHandlerFunction

                packetListeners[packetHandlerFunction] = listener

            }
        }
    }

    private fun getFunctionName(function: KFunction<*>): String {
        val param = function.parameters.getOrNull(1)
        val genericName = (param?.type?.arguments?.first()?.type?.classifier as KClass<*>).simpleName
        return "${function.name}(${param.name}: PacketEvent<${genericName}>)"
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: WrappedPacket> callPacketEvent(packet: T, player: Player) : PacketEvent<T> {
        val kClass = packet::class as KClass<WrappedPacket>
        val packetHandlerFunctions = packetHandlers.getOrPut(kClass) { mutableListOf() }
        val functions = packetHandlerFunctions.sortedBy { it.priority }
        val packetEvent = PacketEvent(packet, player)
        functions.forEach {
            it as PacketHandlerFunction<T>
            val listener = packetListeners[it]!!
            it.function.invoke(listener, packetEvent)
        }
        return packetEvent
    }


    override fun onPacketReceiving(event: PLibPacketEvent) {
        val wrappedPacket = packetManager.wrap(event.packet)

        val packetEvent = callPacketEvent(wrappedPacket, event.player)
        event.isCancelled = packetEvent.isCancelled
        packetDebug(event.packet, event.player)
    }

    fun packetDebug(packet: PacketContainer, target: Player) {
        val packetType = packet.type
        onlinePlayers().forEach { player ->
            if (PacketDebugCommand.getPacketDebug(player)) {

                if (packetType.name() in PacketDebugCommand.getSilencedTypes(player).map(PacketType::name)
                    || "Dynamic" in packetType.name() ) { // some proxies broadcast a Dynamic packet?
                    return@forEach
                }

                val hoverEvent = HoverEvent.showText(
                    buildString {
                        append("&#e1a8ff&l${packetType.name()} of ${target.name}")
                        val typeToUses = mutableMapOf<KClass<out Any>, Int>()
                        for (value in packet.modifier.values.filterNotNull()) {
                            typeToUses.putIfAbsent(value::class, -1)
                            typeToUses[value::class] = typeToUses[value::class]!! + 1
                            append("\n&#aecfc0${value::class.simpleName} ${typeToUses[value::class]}: &#b3eeff${value}")
                        }
                    }.colored
                )

                player.sendMessage(buildString {
                    if (packetType.isClient) {
                        append(" &#ed786f&lSERVERBOUND ")
                    } else if (packetType.isServer) {
                        append(" &#b3eeff&lCLIENTBOUND ")
                    }
                    append("&#e1a8ff${packetType.name()}")
                }.colored.hoverEvent(hoverEvent))
            }
        }
    }

    // sending is the exact same as recieving considering there are no packets that are bidirectional
    // that we care about
    override fun onPacketSending(event: PLibPacketEvent) = onPacketReceiving(event)


    internal data class PacketHandlerFunction<T: WrappedPacket> internal constructor(
        val priority: EventPriority,
        val function: PacketListener.(PacketEvent<T>) -> Unit
    )
}