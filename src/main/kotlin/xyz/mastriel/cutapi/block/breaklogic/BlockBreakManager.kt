package xyz.mastriel.cutapi.block.breaklogic

import com.github.shynixn.mccoroutine.bukkit.*
import net.minecraft.core.*
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot
import net.minecraft.world.*
import net.minecraft.world.entity.ai.attributes.*
import org.bukkit.*
import org.bukkit.craftbukkit.event.*
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.event.player.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.periodic.*
import xyz.mastriel.cutapi.utils.*
import java.lang.reflect.*
import java.util.concurrent.*
import java.util.logging.*

// This class uses NMS to detect when a player starts breaking a block.
@OptIn(UsesNMS::class)
public class BlockBreakManager : Listener, PacketListener {

    private val breakers = ConcurrentHashMap<PlayerUUID, PlayerBlockBreaker>()

    @Periodic(1)
    public fun updateTicks() {
        for ((player, breaker) in breakers.toMap()) {
            try {
                if (!breaker.hasStopped) {
                    breaker.tick()
                } else {
                    breakers.remove(player)
                }
            } catch (ex: Exception) {
                Plugin.logger.log(Level.SEVERE, "Error while ticking block breaker for ${breaker.player?.name}", ex)
            }

        }
    }

    private fun abortBreaking(player: Player, packet: ServerboundPlayerActionPacket) {
        val breaker = breakers.remove(player.playerUUID)
        if (breaker != null) {
            breaker.stop(false, packet.sequence)
        }
    }

    private fun stopBreaking(player: Player, packet: ServerboundPlayerActionPacket) {
        val breaker = breakers.remove(player.playerUUID)
        if (breaker != null) {
            breaker.breakBlock()
            breaker.stop(true)
        } else {
            //player.packetHandler?.injectIncoming(packet)
        }
    }

    @PacketHandler
    internal fun onStartBreaking(ev: PacketEvent<ServerboundPlayerActionPacket>): ServerboundPlayerActionPacket? {
        val (player, packet) = ev
        val action = packet.action

        return when (action) {
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK -> {
                Plugin.launch {
                    startBreaking(player, packet.pos, player.activeItem, packet, packet.direction, packet.sequence)
                }
                null
            }

            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK -> {
                Plugin.launch {
                    stopBreaking(player, packet)
                }
                null
            }

            ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK -> {
                Plugin.launch {
                    abortBreaking(player, packet)
                }
                null
            }

            else -> packet
        }
    }

    private fun startBreaking(
        player: Player,
        blockPosition: BlockPos,
        activeItem: ItemStack,
        packet: ServerboundPlayerActionPacket,
        direction: Direction,
        sequence: Int
    ) {

        val level = player.nms().level()
        val block = level.getBlockState(blockPosition)

        val newInteractEvent = CraftEventFactory.callPlayerInteractEvent(
            player.nms(),
            Action.LEFT_CLICK_BLOCK,
            blockPosition,
            direction,
            activeItem.nms(),
            InteractionHand.MAIN_HAND
        )

        if (newInteractEvent.useInteractedBlock() == Event.Result.DENY) {
            ClientboundBlockChangedAckPacket(sequence).sendTo(player)
            return
        }

        // Handle stuff like punching the dragon egg, hitting note blocks, etc.
        if (player.gameMode != GameMode.CREATIVE) {
            block.attack(level, blockPosition, player.nms())
        }


        val breaker =
            PlayerBlockBreaker(
                blockPosition.bukkitBlock(player.world),
                player.playerUUID,
                activeItem.toAgnostic(),
                sequence
            )

        // creative does not send stop or abort, plus we only need
        // to trigger the first tick since everything should break
        // instantly in creative mode.
        if (player.gameMode != GameMode.CREATIVE) {
            breakers[player.playerUUID] = breaker
        }

    }

    @PacketHandler
    private fun handleAttributes(event: PacketEvent<ClientboundUpdateAttributesPacket>): ClientboundUpdateAttributesPacket {
        if (event.player.entityId != event.packet.entityId)
            return event.packet

        val newValues = event.packet.values.map {
            if (it.attribute.value() == Attributes.BLOCK_BREAK_SPEED.value()) {
                AttributeSnapshot(it.attribute, 0.0, emptyList())
            } else {
                it
            }
        }

        // Use reflection to access the constructor of ClientboundUpdateAttributesPacket
        val ctor = ClientboundUpdateAttributesPacket::class.java.declaredConstructors
            .filter { it.accessFlags().contains(AccessFlag.PRIVATE) }
            .firstOrNull { it.parameterTypes.size == 2 }
            ?: throw IllegalStateException("No suitable constructor found for ClientboundUpdateAttributesPacket")

        ctor.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return ctor.newInstance(event.packet.entityId, newValues) as ClientboundUpdateAttributesPacket
    }

    @EventHandler
    internal fun playerJoinEvent(event: PlayerJoinEvent) {
        val attributes = listOf(
            AttributeInstance(Attributes.BLOCK_BREAK_SPEED) {}
                .also {
                    it.baseValue = 0.0
                }
        )

        val packet = ClientboundUpdateAttributesPacket(
            event.player.entityId,
            attributes
        )

        packet.sendTo(event.player)

    }

    @EventHandler(priority = EventPriority.LOWEST)
    private fun handleQuit(event: PlayerQuitEvent) {
        val player = event.player

        breakers.remove(player.playerUUID)?.stop(true)
    }


}