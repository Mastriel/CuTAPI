package xyz.mastriel.cutapi.block.breaklogic

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.InteractionHand
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.craftbukkit.v1_20_R3.event.CraftEventFactory
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffectType
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.item.toAgnostic
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.nms.PacketEvent
import xyz.mastriel.cutapi.nms.PacketListener
import xyz.mastriel.cutapi.periodic.Periodic
import xyz.mastriel.cutapi.utils.onlinePlayers
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

// This class uses NMS to detect when a player starts breaking a block.
@UsesNMS
class BlockBreakManager : Listener, PacketListener {

    private val breakers = ConcurrentHashMap<Player, PlayerBlockBreaker>()


    @Periodic(1)
    fun updateEffects() {
        for (player in onlinePlayers()) {
            if (!player.hasPotionEffect(PotionEffectType.SLOW_DIGGING)) {
                player.addPotionEffect(
                    PotionEffectType.SLOW_DIGGING
                        .createEffect(1000000, 100)
                        .withIcon(false)
                        .withParticles(false))
            }
        }
    }

    @Periodic(1)
    fun updateTicks() {
        for ((player, breaker) in breakers.toMap()) {
            try {
                if (!breaker.hasStopped) {
                    breaker.tick()
                } else {
                    breakers.remove(player)
                }
            } catch (ex: Exception) {
                Plugin.logger.log(Level.SEVERE, "Error while ticking block breaker for ${breaker.player.name}", ex)
            }

        }
    }

    @PacketHandler
    internal fun onStartBreaking(ev: PacketEvent<ServerboundPlayerActionPacket>) : ServerboundPlayerActionPacket? {
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

    private fun startBreaking(player: Player, blockPosition: BlockPos, activeItem: ItemStack, packet: ServerboundPlayerActionPacket, direction: Direction, sequence: Int) {

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


        val breaker = PlayerBlockBreaker(blockPosition.bukkitBlock(player.world), player, activeItem.toAgnostic())

        // creative does not send stop or abort, plus we only need
        // to trigger the first tick since everything should break
        // instantly in creative mode.
        if (player.gameMode != GameMode.CREATIVE) {
            breakers[player] = breaker
        }

    }


}