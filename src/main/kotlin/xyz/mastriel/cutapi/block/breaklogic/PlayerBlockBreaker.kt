@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block.breaklogic

import net.minecraft.core.*
import net.minecraft.network.protocol.game.*
import net.minecraft.server.*
import net.minecraft.server.level.*
import net.minecraft.world.entity.item.*
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.*
import net.minecraft.world.level.block.entity.*
import net.minecraft.world.phys.*
import net.minecraft.world.phys.shapes.*
import org.bukkit.*
import org.bukkit.block.*
import org.bukkit.craftbukkit.*
import org.bukkit.craftbukkit.block.*
import org.bukkit.craftbukkit.util.*
import org.bukkit.enchantments.*
import org.bukkit.entity.*
import org.bukkit.event.block.*
import org.bukkit.potion.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.behaviors.*
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.utils.*
import kotlin.math.*
import kotlin.random.*

private typealias MojangStack = net.minecraft.world.item.ItemStack

/**
 * Exists only when a player is breaking a block. Controlled by the [BlockBreakManager].
 *
 * @param block The block being broken.
 * @param player The player breaking the block.
 * @param item The item being used to break the block.
 */
@UsesNMS
public open class PlayerBlockBreaker(
    public val block: Block,
    public val playerUUID: PlayerUUID,
    public val item: AgnosticItemStack,
    public val startSequence: Int,
) {
    public val player: Player? by playerUUID

    protected val soundGroup: SoundGroup = block.blockSoundGroup
    protected val material: Material = block.type
    protected val hardness: Float = material.hardness
    protected val tools: List<Tool> = Tool.from(item)
    protected val toolCategories: List<ToolCategory> = tools.map { it.category }
    protected val correctToolCategories: List<ToolCategory> = ToolCategory.vanillaProperCategoriesOf(block)

    protected val correctToolIsUsed: Boolean = correctToolCategories.any { it in toolCategories }

    protected val utilizedTool: Tool = tools
        .filter { it.category in correctToolCategories }
        .maxByOrNull { it.toolSpeed.speed } ?: Tool.Fists


    public var hasStopped: Boolean = false

    public val isDone: Boolean
        get() = progress >= 1.0f

    private var destroyTicks: Int = 0

    public var progress: Float = 0.0f
        private set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    private fun determineBlockFace(location: Location, maxDistance: Double = 6.0): BlockFace? {
        val start = Vec3(location.x, location.y, location.z)
        val direction = location.direction
        val end = start.add(direction.x * maxDistance, direction.y * maxDistance, direction.z * maxDistance)

        val ctx = ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty())
        val serverLevel = (location.world as CraftWorld).handle
        val result = serverLevel.clip(ctx)
        if (result.type == HitResult.Type.BLOCK) {
            return result.direction.blockFace
        }

        return null
    }

    @Suppress("UnstableApiUsage")
    public open fun tick() {
        if (isDone) return
        val player = player ?: return

        var damage = calculateDamageDealt()
        val clientsideDamage = calculateClientsideDamage()

        if (clientsideDamage >= 1 && damage < 1) {
            stop(false, startSequence)
            return
        }


        val damageEvent = BlockDamageEvent(
            player,
            block,
            determineBlockFace(player.eyeLocation) ?: BlockFace.NORTH,
            item.vanilla(),
            damage > 1
        )
        Plugin.server.pluginManager.callEvent(damageEvent)
        if (damageEvent.isCancelled)
            return
        if (damageEvent.instaBreak && damage < 1)
            damage = 1.0f

        if (damage <= 1.0) {
            progress += damage

            // play break sound every 4 ticks
            if (progress < 1.0 && destroyTicks % 4 == 0) {
                val location = block.location
                val world = location.world
                world.playSound(
                    location,
                    soundGroup.hitSound,
                    SoundCategory.BLOCKS,
                    soundGroup.volume,
                    soundGroup.pitch
                )
            }

            destroyTicks++
        }
        if (isDone) {
            breakBlock()
            stop(true)
        } else {
            val breakStage = (progress.coerceAtMost(1.0f) * 10).toInt()
            broadcastDestructionStage(block, player, breakStage)
        }
    }


    private fun getVanillaBlockExp(level: ServerLevel, pos: BlockPos, tool: MojangStack): Int {
        val blockState = level.getBlockState(pos)
        val block = blockState.block
        return block.getExpDrop(blockState, level, pos, tool, true)
    }

    private fun isVanillaProtected(player: OfflinePlayer, location: Location): Boolean {
        val spawnRadius = Bukkit.getServer().spawnRadius.toDouble()
        val world = location.world!!
        val spawnMin = world.spawnLocation.subtract(spawnRadius, 0.0, spawnRadius)
        val spawnMax = world.spawnLocation.add(spawnRadius, 0.0, spawnRadius)

        fun Location.isBetweenXZ(min: Location, max: Location): Boolean =
            x in min.x.rangeTo(max.x)
                && z in min.z.rangeTo(max.z)

        return world.name == "world"
            && spawnRadius > 0
            && !player.isOp
            && location.isBetweenXZ(spawnMin, spawnMax)
    }

    private fun getVanillaFurnaceExp(furnace: AbstractFurnaceBlockEntity): Int {
        return furnace.recipesUsed.reference2IntEntrySet().sumOf { entry ->
            val recipeHolder = MinecraftServer.getServer().recipeManager.byKey(entry.key).orElse(null)
            val recipe = recipeHolder?.value as? AbstractCookingRecipe

            val amount = entry.intValue
            val expPerRecipe = recipe?.experience()?.toDouble() ?: 0.0

            // Minecraft's logic to calculate the furnace exp
            var exp = floor(amount * expPerRecipe).toInt()
            val f = (amount * expPerRecipe) % 1
            if (f != 0.0 && Math.random() < f) {
                exp++
            }

            return@sumOf exp
        }
    }

    private fun createBlockDropItemEntities(
        level: ServerLevel,
        pos: BlockPos,
        items: Iterable<MojangStack>
    ): List<ItemEntity> =
        items.map {
            ItemEntity(
                level,
                pos.x + 0.5 + Random.nextDouble(-0.25, 0.25),
                pos.y + 0.5 + Random.nextDouble(-0.25, 0.25),
                pos.z + 0.5 + Random.nextDouble(-0.25, 0.25),
                it.copy()
            ).apply(ItemEntity::setDefaultPickUpDelay)
        }

    private inline fun Level.captureDrops(run: () -> Unit): List<ItemEntity> {
        val captureDrops = ArrayList<ItemEntity>()
        this.captureDrops = captureDrops
        try {
            run.invoke()
            return captureDrops
        } finally {
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            this.captureDrops = null;
        }
    }

    private fun broadcastDestructionStage(block: Block, player: Player, stage: Int) {
        val packet = ClientboundBlockDestructionPacket(player.entityId, block.location.toBlock().nms(), stage)
        MinecraftServer.getServer().playerList.broadcast(
            null,
            block.x.toDouble(),
            block.y.toDouble(),
            block.z.toDouble(),
            32.0,
            block.world.nms().dimension(),
            packet
        )
    }


    @Suppress("UnstableApiUsage")
    public open fun breakBlock() {
        val player = player ?: return
        val level = (block.location.world as CraftWorld).handle
        val blockPos = block.location.toBlock().nms()


        val succeeded = player.breakBlock(block)

        if (!succeeded) {
            // reset
            progress = 0.0f
        }

        // send ack packet
        ClientboundBlockChangedAckPacket(startSequence).sendTo(player)
    }

    public open fun stop(isFinished: Boolean, sequence: Int? = null) {
        hasStopped = true

        if (sequence != null) {
            if (player != null) ClientboundBlockChangedAckPacket(sequence).sendTo(player!!)
        }
    }

    private fun isCorrectToolForDropsCustomTool(block: Block): Boolean {
        val nmsBlock = (block as CraftBlock).nms

        if (!nmsBlock.requiresCorrectToolForDrops()) return true


        val blockValidCategories = ToolCategory.vanillaProperCategoriesOf(block)
        val blockToolTier = ToolTier.fromVanillaBlock(block).breakingLevel
        val itemToolCategory = utilizedTool.category
        val itemToolTier = utilizedTool.tier.breakingLevel

        return itemToolCategory in blockValidCategories && itemToolTier >= blockToolTier
    }

    private fun isCorrectToolForDropsVanillaTool(block: Block): Boolean {
        val nmsBlock = (block as CraftBlock).nms

        if (!nmsBlock.requiresCorrectToolForDrops()) return true

        val nmsItem = item.vanilla().nms()

        return CraftMagicNumbers.getItem(material).isCorrectToolForDrops(nmsItem, nmsBlock)
    }

    private fun calculateClientsideDamage(): Double {
        return if (player?.gameMode == GameMode.CREATIVE) 1.0 else 0.0
    }

    /**
     * Calculates the damage dealt to the block.
     * @return The damage dealt to the block, as a percentage of the block's health from 0.0f to 1.0f.
     */
    @Suppress("DEPRECATION")
    public open fun calculateDamageDealt(): Float {

        val player = this.player ?: return 0f

        // blocks under 0 are unbreakable
        if (hardness < 0f) return 0f

        // blocks at 0 are instant break
        if (hardness == 0f) return 1f

        var totalSpeedMultiplier = 1f

        // calculates out the tool's base speed, and efficiency enchant.
        if (correctToolIsUsed) {

            totalSpeedMultiplier = utilizedTool
                .category
                .attributes
                .getBreakingSpeedMultiplier(material) ?: utilizedTool.toolSpeed.speed

            val efficiencyLevel = item.vanilla().getEnchantmentLevel(Enchantment.EFFICIENCY)
            if (efficiencyLevel > 0) {
                totalSpeedMultiplier += efficiencyLevel * efficiencyLevel + 1
            }
        }
        val hasteLevel = player.getPotionEffect(PotionEffectType.HASTE)?.amplifier?.plus(1) ?: 0

        totalSpeedMultiplier *= hasteLevel * 0.2f + 1

        if (player.isUnderWater) totalSpeedMultiplier /= 5f

        // deprecation is suppressed here because isOnGround can be
        // easily spoofed. it doesn't really matter that much since
        // it's used for a minor benefit when mining while flying.
        // (also, fly hacks should be pretty obvious to spot)
        if (!player.isOnGround) totalSpeedMultiplier /= 5f

        val correctToolIsUsed = when (item) {
            is AgnosticItemStack.Custom -> isCorrectToolForDropsCustomTool(block)
            is AgnosticItemStack.Vanilla -> isCorrectToolForDropsVanillaTool(block)
        }

        val correctToolBonus = if (correctToolIsUsed) 30f else 100f

        return (totalSpeedMultiplier / hardness / correctToolBonus).roundToDecimalPlaces(3)
    }


}