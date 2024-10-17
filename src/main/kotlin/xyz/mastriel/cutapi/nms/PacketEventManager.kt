package xyz.mastriel.cutapi.nms

import net.minecraft.network.protocol.*
import org.bukkit.event.*
import xyz.mastriel.cutapi.*
import java.lang.reflect.*


@UsesNMS
public interface PacketListener

@UsesNMS
private data class PacketListenerFunctionDefinition(
    private val parent: PacketListener,
    private val function: Method
) {
    operator fun invoke(event: PacketEvent<*>): MojangPacket<*>? {
        return function.invoke(parent, event) as? MojangPacket<*>?
    }
}

@UsesNMS
private data class PacketListenerFunction(
    val parentClass: PacketListener,
    val priority: EventPriority,
    val function: PacketListenerFunctionDefinition,
    val packetType: Class<Packet<*>>
)

@UsesNMS
public class PacketEventManager {

    private val packetListeners = mutableListOf<PacketListenerFunction>()

    /**
     * Registers a packet listener
     * @param listener the listener to register
     */
    @Suppress("UNCHECKED_CAST")
    public fun registerPacketListener(listener: PacketListener) {
        for (function in listener::class.java.declaredMethods) {
            try {
                val annotation = function.getAnnotation(PacketHandler::class.java)
                    ?: continue
                val priority = annotation.priority
                val packetFunction = PacketListenerFunctionDefinition(listener, function)

                val eventParameter = function.parameters.getOrNull(0)
                    ?: error("Invalid arguments in PacketHandler. Must have exactly 1 PacketEvent<MojangPacket> argument.")

                if (eventParameter.type != PacketEvent::class.java)
                    error("Argument is not a PacketEvent. (${PacketEvent::class.java} != ${eventParameter.type}")

                val parameterizedType = (eventParameter.parameterizedType as ParameterizedType)
                val packetType = parameterizedType.actualTypeArguments.first() as? Class<Packet<*>>
                    ?: error("PacketEvent does not have a type valid argument. ${parameterizedType.actualTypeArguments.first()}")

                if (!function.isAccessible) {
                    function.isAccessible = true
                }

                packetListeners.add(PacketListenerFunction(listener, priority, packetFunction, packetType))
                Plugin.info("Registered packet listener of (${packetType}) for ${listener::class.simpleName}#${function.name}")
            } catch (ex: Exception) {
                Plugin.warn(
                    "Failed to register packet listener for ${listener::class.simpleName}#${function.name}. " +
                        "The function must have a parameter of type PacketEvent with any MojangPacket (not an *) as the " +
                        "type parameter, and must return a MojangPacket of the same type as the PacketEvent."
                )
                ex.printStackTrace()
            }
        }
    }

    /**
     * Unregisters a packet listener
     * @param listener the listener to unregister
     * @return true if the listener was found and removed, false otherwise
     */
    public fun unregisterPacketListener(listener: PacketListener): Boolean {
        return packetListeners.removeIf { it.parentClass == listener }
    }


    /**
     * Triggers the packet event listeners with this packet event.
     * @param packetEvent the packet event to trigger
     * @return the packet after all listeners have been triggered. May be the same packet or a new one.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun <T : MojangPacket<*>> trigger(packetEvent: PacketEvent<T>): T? {

        var packet: T? = packetEvent.packet
        for (listener in packetListeners.sortedBy { it.priority.slot }) {
            if (packet == null) return null
            if (listener.packetType == packet::class.java) {
                packet = listener.function(packetEvent) as? T?
            }
        }
        return packet
    }
}