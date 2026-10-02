@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block.breaklogic

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.state.BlockState
import org.bukkit.GameMode
import org.bukkit.Particle
import org.bukkit.SoundCategory
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import xyz.mastriel.cutapi.block.CustomTile
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockClientBridge
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockDisplayManager
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockTypes
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.attachments.Tool
import xyz.mastriel.cutapi.nms.nms
import xyz.mastriel.cutapi.nms.sendTo
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor

internal data class MiningTickInputs(
    val hardness: Float,
    val baseToolSpeed: Float,
    val correctTool: Boolean,
    val miningEfficiency: Double,
    val hasteLevel: Int,
    val fatigueLevel: Int,
    val blockBreakSpeed: Double,
    val submergedMiningSpeed: Double,
    val underwater: Boolean,
    val onGround: Boolean,
)

internal object CustomMiningMath {
    fun progressPerTick(input: MiningTickInputs): Float {
        if (input.hardness < 0.0f) return 0.0f
        if (input.hardness == 0.0f) return 1.0f

        var speed = input.baseToolSpeed.toDouble()
        if (speed > 1.0) speed += input.miningEfficiency
        speed *= 1.0 + 0.2 * input.hasteLevel
        speed *= fatigueMultiplier(input.fatigueLevel)
        speed *= input.blockBreakSpeed
        if (input.underwater) speed *= input.submergedMiningSpeed
        if (!input.onGround) speed /= 5.0
        speed /= input.hardness
        speed /= if (input.correctTool) 30.0 else 100.0
        return speed.coerceAtLeast(0.0).toFloat()
    }

    private fun fatigueMultiplier(level: Int): Double = when (level) {
        0 -> 1.0
        1 -> 0.3
        2 -> 0.09
        3 -> 0.0027
        else -> 0.00081
    }
}

internal object CustomMiningBreakerIds {
    private val next = AtomicInteger(Int.MIN_VALUE)

    fun allocate(): Int = next.getAndUpdate { current ->
        if (current == -1) Int.MIN_VALUE else current + 1
    }
}

internal class CustomMiningSession(
    val player: Player,
    val pos: BlockPos,
    private val hitFace: Direction,
    val initialState: BlockState,
    val definition: CustomTile<*>,
    private val onComplete: (CustomMiningSession) -> Unit,
) {
    private val initialWorld = player.world
    val initialWorldId: java.util.UUID = initialWorld.uid
    var progress: Float = 0.0f
        private set
    var correctToolUsed: Boolean = false
        private set
    var age: Int = 0
        private set
    private var lastStage: Int = -1
    private var ended: Boolean = false
    private val displayVisual = NativeBlockClientBridge.resolve(initialState)?.displayEntity
    // The client writes its own predicted crack stage under the player's entity ID every tick.
    // Keeping the authoritative overlay under a separate ID prevents those two stages from
    // alternately replacing one another. Negative IDs cannot collide with normal entity IDs.
    private val breakerId: Int = CustomMiningBreakerIds.allocate()

    fun tick(): Boolean {
        if (ended || !isValid()) return false
        age++
        val input = currentInputs()
        correctToolUsed = input.correctTool
        val increment = CustomMiningMath.progressPerTick(input)
        progress = (progress + increment).coerceAtMost(1.0f)
        if (increment > 0.0f && progress < 1.0f && age % 4 == 0) playHitEffects()
        sendStage()
        if (progress >= 1.0f) onComplete(this)
        return true
    }

    fun isValid(): Boolean {
        if (!player.isOnline || player.isDead || age > SafetyTimeoutTicks) return false
        if (player.gameMode !in setOf(GameMode.SURVIVAL, GameMode.ADVENTURE)) return false
        if (player.world.uid != initialWorldId) return false
        val nms = player.nms()
        if (nms.level().dimension() != initialDimension) return false
        if (!nms.isWithinBlockInteractionRange(pos, 1.0)) return false
        return nms.level().getBlockState(pos) === initialState
    }

    fun finishClientState(restoreAttribute: Boolean) {
        if (ended) return
        ended = true
        sendCrack(-1)
        if (restoreAttribute) BlockBreakManager.restoreBreakSpeed(player)
    }

    private val initialDimension = player.nms().level().dimension()

    @Suppress("DEPRECATION")
    private fun currentInputs(): MiningTickInputs {
        val descriptor = definition.descriptor
        val held = CuTItemStack.wrap(player.inventory.itemInMainHand)
        val tools = Tool.from(held)
        val effective = tools.filter { it.category in descriptor.settings.effectiveTools }
        val fastest = effective.maxByOrNull { it.toolSpeed.speed }
        val tier = descriptor.settings.minimumToolTier
        val correct = fastest != null && (tier == null || fastest.tier.breakingLevel >= tier.breakingLevel)

        return MiningTickInputs(
            hardness = descriptor.settings.hardness,
            baseToolSpeed = fastest?.toolSpeed?.speed ?: 1.0f,
            correctTool = correct,
            miningEfficiency = player.getAttribute(Attribute.MINING_EFFICIENCY)?.value ?: 0.0,
            hasteLevel = player.getPotionEffect(PotionEffectType.HASTE)?.amplifier?.plus(1) ?: 0,
            fatigueLevel = player.getPotionEffect(PotionEffectType.MINING_FATIGUE)?.amplifier?.plus(1) ?: 0,
            blockBreakSpeed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED)?.value ?: 1.0,
            submergedMiningSpeed = player.getAttribute(Attribute.SUBMERGED_MINING_SPEED)?.value ?: 0.2,
            underwater = player.isUnderWater,
            onGround = player.isOnGround,
        )
    }

    private fun sendStage() {
        val stage = floor(progress * 10.0f).toInt().coerceIn(0, 9)
        if (stage == lastStage) return
        lastStage = stage
        sendCrack(stage)
    }

    private fun sendCrack(stage: Int) {
        if (displayVisual != null) {
            NativeBlockDisplayManager.setBreakingStage(
                initialWorld.getBlockAt(pos.x, pos.y, pos.z),
                breakerId,
                stage,
            )
            return
        }
        val packet = ClientboundBlockDestructionPacket(breakerId, pos, stage)
        packet.sendTo(player)
        val level = player.nms().level()
        MinecraftServer.getServer().playerList.broadcast(
            player.nms(),
            pos.x.toDouble(),
            pos.y.toDouble(),
            pos.z.toDouble(),
            32.0,
            level.dimension(),
            packet,
        )
    }

    private fun playHitEffects() {
        val block = player.world.getBlockAt(pos.x, pos.y, pos.z)
        val sound = block.blockSoundGroup
        player.world.playSound(
            block.location,
            sound.hitSound,
            SoundCategory.BLOCKS,
            sound.volume,
            sound.pitch,
        )
        displayVisual?.let { display ->
            repeat(2) {
                player.world.spawnParticle(
                    Particle.ITEM,
                    displayBreakParticleLocation(block, hitFace),
                    1,
                    0.03,
                    0.03,
                    0.03,
                    0.02,
                    display.item,
                )
            }
        }
    }

    companion object {
        private const val SafetyTimeoutTicks: Int = 20 * 30
    }
}
