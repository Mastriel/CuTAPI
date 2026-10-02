@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block.breaklogic

import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.Blocks
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.SoundCategory
import org.bukkit.block.BlockState as BukkitBlockState
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.entity.CraftItem
import org.bukkit.craftbukkit.block.CraftBlockState
import org.bukkit.craftbukkit.event.CraftEventFactory
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.ChunkUnloadEvent
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.block.BlockBreakCause
import xyz.mastriel.cutapi.block.BlockDropContext
import xyz.mastriel.cutapi.block.BlockPostBreakContext
import xyz.mastriel.cutapi.block.BlockPreBreakContext
import xyz.mastriel.cutapi.block.BlockSystem
import xyz.mastriel.cutapi.block.CustomBlockManager.Companion.wrap
import xyz.mastriel.cutapi.block.CuTPlacedTile
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockClientBridge
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockDisplayManager
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockTypes
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.attachments.Tool
import xyz.mastriel.cutapi.item.nativeitem.NativeItemTypes
import xyz.mastriel.cutapi.nms.PacketEvent
import xyz.mastriel.cutapi.nms.PacketHandler
import xyz.mastriel.cutapi.nms.PacketListener
import xyz.mastriel.cutapi.nms.nms
import xyz.mastriel.cutapi.nms.sendTo
import xyz.mastriel.cutapi.periodic.Periodic
import xyz.mastriel.cutapi.utils.PlayerUUID
import xyz.mastriel.cutapi.utils.playerUUID
import java.lang.reflect.AccessFlag
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

public class BlockBreakManager : Listener, PacketListener {
    private val sessions = ConcurrentHashMap<PlayerUUID, CustomMiningSession>()

    @Periodic(1)
    public fun updateTicks() {
        sessions.toMap().forEach { (id, session) ->
            if (!session.tick()) cancel(id, repairBlock = true)
        }
    }

    @PacketHandler
    internal fun onPlayerAction(event: PacketEvent<ServerboundPlayerActionPacket>): ServerboundPlayerActionPacket? {
        val player = event.player
        val packet = event.packet
        val existing = sessions[player.playerUUID]
        val state = player.nms().level().getBlockState(packet.pos)
        val custom = NativeBlockTypes.definition(state)

        return when (packet.action) {
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK -> {
                if (custom == null) return packet
                runMain { start(player, packet, state) }
                null
            }
            ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK -> {
                if (existing == null) return packet
                runMain {
                    CraftEventFactory.callBlockDamageAbortEvent(
                        player.nms(),
                        existing.pos,
                        player.nms().mainHandItem,
                    )
                    cancel(player.playerUUID, repairBlock = true)
                    acknowledge(player, packet.sequence)
                }
                null
            }
            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK -> {
                if (existing == null) return packet
                runMain {
                    if (existing.progress >= 1.0f) complete(existing)
                    acknowledge(player, packet.sequence)
                }
                null
            }
            else -> packet
        }
    }

    @PacketHandler
    internal fun onPickBlock(event: PacketEvent<ServerboundPickItemFromBlockPacket>): ServerboundPickItemFromBlockPacket? {
        val state = event.player.nms().level().getBlockState(event.packet.pos)
        val definition = NativeBlockTypes.definition(state) ?: return event.packet
        runMain {
            val item = definition.placementItemOrNull() ?: return@runMain
            val inventory = event.player.inventory
            val existing = inventory.contents.indexOfFirst { stack ->
                stack != null && !stack.type.isAir && runCatching {
                    NativeItemTypes.resolve(stack).key == item.id.toNamespacedKey()
                }.getOrDefault(false)
            }
            if (existing in 0..8) {
                inventory.heldItemSlot = existing
            } else if (existing >= 9) {
                val held = inventory.heldItemSlot
                val selected = inventory.getItem(existing)
                inventory.setItem(existing, inventory.getItem(held))
                inventory.setItem(held, selected)
            } else if (event.player.gameMode == GameMode.CREATIVE) {
                inventory.setItem(inventory.heldItemSlot, item.createItemStack().vanilla())
            }
            event.player.updateInventory()
        }
        return null
    }

    private fun start(player: Player, packet: ServerboundPlayerActionPacket, state: net.minecraft.world.level.block.state.BlockState) {
        val nms = player.nms()
        val level = nms.level()
        val definition = NativeBlockTypes.definition(state) ?: return acknowledge(player, packet.sequence)
        val previous = sessions[player.playerUUID]
        if (previous != null && previous.pos == packet.pos && previous.age <= 1) {
            acknowledge(player, packet.sequence)
            return
        }
        if (previous != null) {
            sessions.remove(player.playerUUID, previous)
            previous.finishClientState(restoreAttribute = true)
        }

        if (!validateStart(player, packet)) {
            acknowledge(player, packet.sequence)
            repair(player, packet.pos)
            return
        }
        if (level.getBlockState(packet.pos) !== state) {
            acknowledge(player, packet.sequence)
            repair(player, packet.pos)
            return
        }

        val interact = CraftEventFactory.callPlayerInteractEvent(
            nms,
            Action.LEFT_CLICK_BLOCK,
            packet.pos,
            packet.direction,
            nms.mainHandItem,
            InteractionHand.MAIN_HAND,
        )
        if (interact.useInteractedBlock() == Event.Result.DENY || interact.isCancelled) {
            acknowledge(player, packet.sequence)
            repair(player, packet.pos)
            return
        }
        if (player.gameMode != GameMode.CREATIVE) state.attack(level, packet.pos, nms)

        val damage = CraftEventFactory.callBlockDamageEvent(
            nms,
            packet.pos,
            packet.direction,
            nms.mainHandItem,
            definition.descriptor.settings.hardness == 0.0f,
        )
        if (damage.isCancelled) {
            acknowledge(player, packet.sequence)
            repair(player, packet.pos)
            return
        }

        val session = CustomMiningSession(
            player,
            packet.pos,
            packet.direction,
            state,
            definition,
            ::complete,
        )
        acknowledge(player, packet.sequence)
        suppressBreakSpeed(player)
        if (player.gameMode == GameMode.CREATIVE || damage.instaBreak) {
            complete(session, BlockBreakCause.Creative.takeIf { player.gameMode == GameMode.CREATIVE } ?: BlockBreakCause.Player)
        } else {
            sessions[player.playerUUID] = session
            session.tick()
        }
    }

    private fun validateStart(player: Player, packet: ServerboundPlayerActionPacket): Boolean {
        val nms = player.nms()
        val level = nms.level()
        if (player.gameMode == GameMode.SPECTATOR) return false
        if (level.isOutsideBuildHeight(packet.pos)) return false
        if (!level.worldBorder.isWithinBounds(packet.pos)) return false
        if (!nms.isWithinBlockInteractionRange(packet.pos, 1.0)) return false
        if (!nms.mayInteract(level, packet.pos)) return false
        return nms.mayUseItemAt(packet.pos, packet.direction, nms.mainHandItem)
    }

    private fun complete(session: CustomMiningSession, cause: BlockBreakCause = BlockBreakCause.Player) {
        sessions.remove(session.player.playerUUID, session)
        try {
            val player = session.player
            val level = player.nms().level()
            if (level.getBlockState(session.pos) !== session.initialState) return
            val block = player.world.getBlockAt(session.pos.x, session.pos.y, session.pos.z)
            val tile = block.wrap<CuTPlacedTile>() ?: return
            val oldBukkitState = GenericBlockState(block)
            val sound = block.blockSoundGroup
            val visual = requireNotNull(NativeBlockClientBridge.resolve(session.initialState)) {
                "Custom block ${session.definition.id} has no resolved client visual."
            }
            val breakEvent = BlockBreakEvent(block, player)
            Plugin.server.pluginManager.callEvent(breakEvent)
            val pre = BlockPreBreakContext(tile, player, cause, breakEvent)
            BlockSystem.dispatch(tile) { it.onPreBreak(pre) }
            if (breakEvent.isCancelled || pre.isCancelled) {
                repair(player, session.pos)
                return
            }

            tile.snapshotForRemoval()
            invalidateTarget(session.pos, player.world.uid, excluding = player.playerUUID)
            val held = CuTItemStack.wrap(player.inventory.itemInMainHand)
            val dropContext = BlockDropContext(
                tile = tile,
                player = player,
                tool = held,
                correctToolUsed = session.correctToolUsed,
                cause = cause,
            )
            val definition = session.definition

            check(level.setBlock(session.pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL)) {
                "Minecraft rejected removal of custom block ${definition.id} at ${block.location}."
            }
            NativeBlockDisplayManager.refresh(block)
            if (cause != BlockBreakCause.Creative) {
                damageHeldItem(player, Tool.from(held))
            }

            val mayDrop = cause != BlockBreakCause.Creative &&
                (!definition.descriptor.settings.requiresCorrectToolForDrops || session.correctToolUsed)
            if (mayDrop) {
                dropContext.drops = definition.descriptor.dropProducer(dropContext)
                    .map(org.bukkit.inventory.ItemStack::clone)
                    .toMutableList()
                dropContext.experience = definition.descriptor.experienceProducer(dropContext).coerceAtLeast(0)
            }
            BlockSystem.dispatch(tile) { it.onDrops(dropContext) }
            if (breakEvent.isDropItems && dropContext.drops.isNotEmpty()) {
                spawnDrops(block, oldBukkitState, player, dropContext)
            }
            if (dropContext.experience > 0) {
                player.world.spawn(block.location.toCenterLocation(), org.bukkit.entity.ExperienceOrb::class.java) {
                    it.experience = dropContext.experience
                }
            }
            BlockSystem.dispatch(tile) {
                it.onPostBreak(
                    BlockPostBreakContext(tile, player, cause, dropContext.drops.toList(), dropContext.experience),
                )
            }
            playBreakEffects(block, sound, visual)
        } finally {
            session.finishClientState(restoreAttribute = true)
        }
    }

    private fun spawnDrops(
        block: org.bukkit.block.Block,
        oldState: BukkitBlockState,
        player: Player,
        context: BlockDropContext,
    ) {
        val level = player.nms().level()
        val entities = context.drops.map { stack ->
            ItemEntity(
                level,
                block.x + 0.5 + Random.nextDouble(-0.25, 0.25),
                block.y + 0.5 + Random.nextDouble(-0.25, 0.25),
                block.z + 0.5 + Random.nextDouble(-0.25, 0.25),
                CraftItemStack.asNMSCopy(stack),
            ).apply { setDefaultPickUpDelay() }
        }
        val bukkitItems = entities.map { CraftItem(Bukkit.getServer() as CraftServer, it) }
        val event = BlockDropItemEvent(block, oldState, player, bukkitItems)
        Plugin.server.pluginManager.callEvent(event)
        if (event.isCancelled) return
        val approved = event.items.toSet()
        entities.zip(bukkitItems).filter { it.second in approved }.forEach { level.addFreshEntity(it.first) }
    }

    private fun playBreakEffects(
        block: org.bukkit.block.Block,
        sound: org.bukkit.SoundGroup,
        visual: xyz.mastriel.cutapi.block.ResolvedBlockVisual,
    ) {
        block.world.playSound(block.location, sound.breakSound, SoundCategory.BLOCKS, sound.volume, sound.pitch)
        val displayItem = visual.displayEntity?.item
        if (displayItem != null) {
            val origin = block.location
            displayBreakCompletionParticles().forEach { particle ->
                block.world.spawnParticle(
                    org.bukkit.Particle.ITEM,
                    origin.x + particle.offsetX,
                    origin.y + particle.offsetY,
                    origin.z + particle.offsetZ,
                    0,
                    particle.velocityX,
                    particle.velocityY,
                    particle.velocityZ,
                    1.0,
                    displayItem,
                )
            }
        } else {
            val particleData = visual.clientBlockState.createCraftBlockData()
            block.world.spawnParticle(
                org.bukkit.Particle.BLOCK,
                block.location.toCenterLocation(),
                20,
                0.25,
                0.25,
                0.25,
                particleData,
            )
        }
    }

    private fun cancel(id: PlayerUUID, repairBlock: Boolean) {
        val session = sessions.remove(id) ?: return
        session.finishClientState(restoreAttribute = true)
        if (repairBlock) repair(session.player, session.pos)
    }

    /**
     * Native custom blocks are removed entirely by CuTAPI, so their successful-break durability
     * must be applied here instead of delegated to Minecraft's block-breaking implementation.
     */
    private fun damageHeldItem(player: Player, tools: List<Tool>) {
        val damage = tools.maxOfOrNull { it.category.attributes.breakBlockItemDamage } ?: return
        if (damage <= 0) return
        player.nms().mainHandItem.hurtAndBreak(damage, player.nms(), EquipmentSlot.MAINHAND)
    }

    /**
     * A custom item whose backing item is not a native tool has no TOOL component for Minecraft to
     * consume after an ordinary block break. Supply the same server-owned durability fallback for
     * its explicit CuTAPI Tool attachment. Native-tool-backed items remain handled by Minecraft.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onVanillaBlockBreak(event: BlockBreakEvent) {
        if (event.player.gameMode == GameMode.CREATIVE) return
        if (NativeBlockTypes.definition(event.block) != null) return

        val held = CuTItemStack.wrap(event.player.inventory.itemInMainHand)
        if (!held.isCustom) return
        val tools = held.getAttachments(Tool)
        if (tools.isEmpty()) return
        if (event.player.nms().mainHandItem.has(DataComponents.TOOL)) return

        damageHeldItem(event.player, tools)
    }

    private fun invalidateTarget(pos: BlockPos, worldId: java.util.UUID, excluding: PlayerUUID? = null) {
        sessions.entries.filter {
            it.key != excluding && it.value.pos == pos && it.value.initialWorldId == worldId
        }.forEach {
            cancel(it.key, repairBlock = true)
        }
    }

    private fun repair(player: Player, pos: BlockPos) {
        ClientboundBlockUpdatePacket(pos, player.nms().level().getBlockState(pos)).sendTo(player)
    }

    private fun acknowledge(player: Player, sequence: Int) {
        ClientboundBlockChangedAckPacket(sequence).sendTo(player)
    }

    @PacketHandler
    internal fun projectAttributes(event: PacketEvent<ClientboundUpdateAttributesPacket>): ClientboundUpdateAttributesPacket {
        if (sessions[event.player.playerUUID] == null || event.packet.entityId != event.player.entityId) return event.packet
        val values = event.packet.values.map { snapshot ->
            if (snapshot.attribute.value() === Attributes.BLOCK_BREAK_SPEED.value()) {
                AttributeSnapshot(snapshot.attribute, 0.0, emptyList())
            } else snapshot
        }
        return attributePacket(event.packet.entityId, values)
    }

    private fun suppressBreakSpeed(player: Player) {
        val instance = AttributeInstance(Attributes.BLOCK_BREAK_SPEED) {}
        instance.baseValue = 0.0
        ClientboundUpdateAttributesPacket(player.entityId, listOf(instance)).sendTo(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public fun onQuit(event: PlayerQuitEvent): Unit = cancel(event.player.playerUUID, repairBlock = false)

    @EventHandler(priority = EventPriority.MONITOR)
    public fun onDeath(event: PlayerDeathEvent): Unit = cancel(event.player.playerUUID, repairBlock = false)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onTeleport(event: PlayerTeleportEvent): Unit = cancel(event.player.playerUUID, repairBlock = false)

    @EventHandler(priority = EventPriority.MONITOR)
    public fun onWorldChange(event: PlayerChangedWorldEvent): Unit = cancel(event.player.playerUUID, repairBlock = false)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onGameMode(event: PlayerGameModeChangeEvent) {
        if (event.newGameMode !in setOf(GameMode.SURVIVAL, GameMode.ADVENTURE)) cancel(event.player.playerUUID, false)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onChunkUnload(event: ChunkUnloadEvent) {
        sessions.entries.filter {
            (it.value.pos.x shr 4) == event.chunk.x && (it.value.pos.z shr 4) == event.chunk.z &&
                it.value.initialWorldId == event.world.uid
        }.forEach { cancel(it.key, false) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onPistonExtend(event: BlockPistonExtendEvent): Unit =
        event.blocks.forEach { invalidateTarget(BlockPos(it.x, it.y, it.z), it.world.uid) }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onPistonRetract(event: BlockPistonRetractEvent): Unit =
        event.blocks.forEach { invalidateTarget(BlockPos(it.x, it.y, it.z), it.world.uid) }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onBlockExplosion(event: BlockExplodeEvent): Unit =
        event.blockList().forEach { invalidateTarget(BlockPos(it.x, it.y, it.z), it.world.uid) }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onEntityExplosion(event: EntityExplodeEvent): Unit =
        event.blockList().forEach { invalidateTarget(BlockPos(it.x, it.y, it.z), it.world.uid) }

    public companion object {
        internal fun restoreBreakSpeed(player: Player) {
            val instance = player.nms().attributes.getInstance(Attributes.BLOCK_BREAK_SPEED) ?: return
            ClientboundUpdateAttributesPacket(player.entityId, listOf(instance)).sendTo(player)
        }

        @Suppress("UNCHECKED_CAST")
        private fun attributePacket(entityId: Int, values: List<AttributeSnapshot>): ClientboundUpdateAttributesPacket {
            val constructor = ClientboundUpdateAttributesPacket::class.java.declaredConstructors
                .first { it.parameterCount == 2 && it.accessFlags().contains(AccessFlag.PRIVATE) }
                .apply { isAccessible = true }
            return constructor.newInstance(entityId, values) as ClientboundUpdateAttributesPacket
        }

        private fun runMain(action: () -> Unit) {
            Bukkit.getScheduler().runTask(Plugin, Runnable(action))
        }
    }
}

/** CraftBukkit has no BlockState factory for CuTAPI's native block-entity types. */
private class GenericBlockState(block: org.bukkit.block.Block) : CraftBlockState(block)
