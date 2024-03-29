package xyz.mastriel.cutapi.packets

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.packets.wrappers.*
import xyz.mastriel.cutapi.packets.wrappers.ClientboundEntityEffectPacket
import xyz.mastriel.cutapi.packets.wrappers.ClientboundOpenScreenPacket
import xyz.mastriel.cutapi.packets.wrappers.ClientboundSetContainerContentPacket
import xyz.mastriel.cutapi.packets.wrappers.ClientboundSetSlotPacket
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.primaryConstructor

internal class PacketManager(val plugin: Plugin) {

    val eventManager = PacketEventManager(this)

    private val packetTypes = mutableListOf<KClass<out WrappedPacket>>()

    init {
        packetTypes.add(ClientboundSetSlotPacket::class)
        packetTypes.add(ClientboundEntityEffectPacket::class)
        packetTypes.add(ClientboundSetContainerContentPacket::class)
        packetTypes.add(ClientboundOpenScreenPacket::class)
        packetTypes.add(ClientboundCloseContainerPacket::class)
        packetTypes.add(ServerboundCloseContainerPacket::class)

        ProtocolLibrary
            .getProtocolManager()
            .addPacketListener(eventManager)
    }

    fun registerPacket(vararg packets: KClass<WrappedPacket>) {
        packets.forEach { packetClass ->
            requireValidConstructor(packetClass)
            packetTypes += packetClass
        }
    }



    fun getPacketInfo(kclass: KClass<out WrappedPacket>) : PacketInfo {
        return kclass.companionObjectInstance as PacketInfo
    }

    fun findPacketWrapperByType(type: PacketType) : KClass<out WrappedPacket> {
        return packetTypes
            .find { getPacketInfo(it).packetType == type } ?: return WrappedPacket::class
    }


    internal fun createPacketFromClass(wrappedPacket: KClass<out WrappedPacket>) : WrappedPacket {
        val packetInfo = getPacketInfo(wrappedPacket)
        val constructor = wrappedPacket.primaryConstructor
            ?: throw IllegalStateException("WrappedPacket primary constructor must take in a PacketContainer and nothing else.")

        val handle = PacketContainer(packetInfo.packetType)
        handle.modifier.writeDefaults()
        return constructor.call(handle)
    }

    internal fun createPacketFromClass(wrappedPacket: KClass<out WrappedPacket>, handle: PacketContainer) : WrappedPacket {
        val constructor = wrappedPacket.primaryConstructor
            ?: throw IllegalStateException("WrappedPacket primary constructor must take in a PacketContainer and nothing else.")

        handle.modifier.writeDefaults()
        return constructor.call(handle)
    }

    @JvmName("wrapTo")
    @Suppress("UNCHECKED_CAST")
    internal fun <T: WrappedPacket> wrap(handle: PacketContainer) : T {
        val wrappedPacketClass = findPacketWrapperByType(handle.type)
        return createPacketFromClass(wrappedPacketClass, handle) as T
    }

    @JvmName("wrap")
    internal fun wrap(handle: PacketContainer) : WrappedPacket {
        val wrappedPacketClass = findPacketWrapperByType(handle.type)
        return createPacketFromClass(wrappedPacketClass, handle)
    }

    internal fun requireValidConstructor(wrappedPacket: KClass<WrappedPacket>) {
        val parameters = wrappedPacket.primaryConstructor?.parameters ?: error("WrappedPacket primary constructor takes no arguments.")
        val parameterClassifier = parameters.firstOrNull()?.type?.classifier
        require(parameterClassifier == PacketContainer::class) { "WrappedPacket primary constructor doesn't take exactly 1 PacketContainer argument." }
    }
}