 package xyz.mastriel.cutapi.packets.wrappers

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketContainer
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import xyz.mastriel.cutapi.packets.PacketInfo
import xyz.mastriel.cutapi.packets.WrappedPacket
import xyz.mastriel.cutapi.packets.intField
import xyz.mastriel.cutapi.packets.packetInfo

internal class ClientboundEntityEffectPacket(handle: PacketContainer) : WrappedPacket(handle) {
    internal companion object : PacketInfo by packetInfo(PacketType.Play.Server.ENTITY_EFFECT)

    var entityId by intField(0)

    @Suppress("DEPRECATION")
    var potionEffect : PotionEffect
        get() {
            val effectId = handle.bytes.read(0).toInt()
            val amplifier = handle.bytes.read(1).toInt()
            val duration = handle.integers.read(1)
            val hideParticles = handle.bytes.read(2) == 0.toByte()

            val effectType = PotionEffectType.getById(effectId)!!
            return PotionEffect(effectType, duration, amplifier, false, hideParticles)
        }
        set(value) {
            // type id
            handle.bytes.write(0, value.type.id.toByte())

            // amplifier
            handle.bytes.write(1, value.amplifier.toByte())

            // hide particles
            handle.bytes.write(2, if (value.hasParticles()) 0 else 1)

            // duration
            handle.integers.write(0, value.duration)
        }

    constructor(entityId: Int, effect: PotionEffect) : this(PacketContainer(PacketType.Play.Server.ENTITY_EFFECT)) {
        this.entityId = entityId
        this.potionEffect = effect
    }
}