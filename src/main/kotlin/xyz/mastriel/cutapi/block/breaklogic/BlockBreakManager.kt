package xyz.mastriel.cutapi.block.breaklogic

import net.minecraft.core.*
import net.minecraft.network.protocol.game.*
import net.minecraft.world.*
import org.bukkit.*
import org.bukkit.craftbukkit.event.*
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.inventory.*
import org.bukkit.potion.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.periodic.*
import xyz.mastriel.cutapi.utils.*
import java.util.concurrent.*
import java.util.logging.*

// This class uses NMS to detect when a player starts breaking a block.
@OptIn(UsesNMS::class)
public class BlockBreakManager : Listener, PacketListener {

    private val breakers = ConcurrentHashMap<PlayerUUID, PlayerBlockBreaker>()


    @Periodic(1)
    public fun updateEffects() {
        for (player in onlinePlayers()) {
            if (!player.hasPotionEffect(PotionEffectType.MINING_FATIGUE)) {
                player.addPotionEffect(
                    PotionEffectType.MINING_FATIGUE
                        .createEffect(1000000, 100)
                        .withIcon(false)
                        .withParticles(false)
                )
            }
        }
    }

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

    @PacketHandler
    internal fun onStartBreaking(ev: PacketEvent<ServerboundPlayerActionPacket>): ServerboundPlayerActionPacket? {
        val (player, packet) = ev
        val action = packet.action

        return when (action) {
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK -> {
                startBreaking(player, packet.pos, player.activeItem, packet, packet.direction, packet.sequence)
                null
            }

            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK -> {
                // stopBreaking(player, packet)
                null
            }

            ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK -> {
                // abortBreaking(player, packet)
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
            PlayerBlockBreaker(blockPosition.bukkitBlock(player.world), player.playerUUID, activeItem.toAgnostic())

        // creative does not send stop or abort, plus we only need
        // to trigger the first tick since everything should break
        // instantly in creative mode.
        if (player.gameMode != GameMode.CREATIVE) {
            breakers[player.playerUUID] = breaker
        }

    }


}