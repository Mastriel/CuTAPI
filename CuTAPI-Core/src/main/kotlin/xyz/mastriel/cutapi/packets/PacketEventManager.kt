package xyz.mastriel.cutapi.packets

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import xyz.mastriel.cutapi.Plugin
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation

internal typealias PLibPacketEvent = com.comphenix.protocol.events.PacketEvent

internal class PacketEventManager(val packetManager: PacketManager) : PacketAdapter(Plugin, PacketType.values()) {

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
    }

    // sending is the exact same as recieving considering there are no packets that are bidirectional
    override fun onPacketSending(event: PLibPacketEvent) = onPacketReceiving(event)


    internal data class PacketHandlerFunction<T: WrappedPacket> internal constructor(
        val priority: EventPriority,
        val function: PacketListener.(PacketEvent<T>) -> Unit
    )
}