@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.block.breaklogic

import net.minecraft.core.Direction
import org.bukkit.Location
import org.bukkit.block.Block
import kotlin.math.sqrt
import kotlin.random.Random

/** Places item particles just outside the carrier so they sample the adjacent cell's light. */
internal fun displayBreakParticleLocation(block: Block, face: Direction): Location {
    val center = block.location.toCenterLocation()
    return center.add(
        if (face.stepX == 0) Random.nextDouble(-0.45, 0.45) else face.stepX * 0.6,
        if (face.stepY == 0) Random.nextDouble(-0.45, 0.45) else face.stepY * 0.6,
        if (face.stepZ == 0) Random.nextDouble(-0.45, 0.45) else face.stepZ * 0.6,
    )
}

internal data class DisplayBreakParticleEmission(
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val velocityX: Double,
    val velocityY: Double,
    val velocityZ: Double,
)

/**
 * Reproduces the full-cube lattice and motion used by Minecraft's block-destroy particle routine.
 *
 * Vanilla divides each shape axis into cells no larger than 0.25 blocks and emits from each cell's
 * center. Display-backed blocks currently occupy a full carrier cube, so that produces 4 x 4 x 4
 * emissions. Their initial direction points away from the block center before Minecraft's particle
 * constructor adds jitter, normalizes the result, scales it, and gives it a small upward impulse.
 */
internal fun displayBreakCompletionParticles(
    random: Random = Random.Default,
): List<DisplayBreakParticleEmission> = buildList(VanillaBreakGridSize * VanillaBreakGridSize * VanillaBreakGridSize) {
    repeat(VanillaBreakGridSize) { xIndex ->
        repeat(VanillaBreakGridSize) { yIndex ->
            repeat(VanillaBreakGridSize) { zIndex ->
                val offsetX = (xIndex + 0.5) / VanillaBreakGridSize
                val offsetY = (yIndex + 0.5) / VanillaBreakGridSize
                val offsetZ = (zIndex + 0.5) / VanillaBreakGridSize
                val velocity = vanillaBreakParticleVelocity(
                    offsetX - 0.5,
                    offsetY - 0.5,
                    offsetZ - 0.5,
                    random,
                )
                add(
                    DisplayBreakParticleEmission(
                        offsetX,
                        offsetY,
                        offsetZ,
                        velocity.x,
                        velocity.y,
                        velocity.z,
                    ),
                )
            }
        }
    }
}

private fun vanillaBreakParticleVelocity(
    initialX: Double,
    initialY: Double,
    initialZ: Double,
    random: Random,
): ParticleVelocity {
    val jitteredX = initialX + random.nextVanillaParticleJitter()
    val jitteredY = initialY + random.nextVanillaParticleJitter()
    val jitteredZ = initialZ + random.nextVanillaParticleJitter()
    val length = sqrt(jitteredX * jitteredX + jitteredY * jitteredY + jitteredZ * jitteredZ)
    if (length == 0.0) return ParticleVelocity(0.0, VanillaUpwardImpulse, 0.0)

    val randomMagnitude = ((random.nextFloat() + random.nextFloat() + 1.0f) * VanillaRandomMagnitude).toDouble()
    val scale = randomMagnitude * VanillaVelocityScale / length
    return ParticleVelocity(
        jitteredX * scale,
        jitteredY * scale + VanillaUpwardImpulse,
        jitteredZ * scale,
    )
}

private fun Random.nextVanillaParticleJitter(): Double =
    ((nextFloat() * 2.0f - 1.0f) * VanillaJitter).toDouble()

private data class ParticleVelocity(val x: Double, val y: Double, val z: Double)

private const val VanillaBreakGridSize: Int = 4
private const val VanillaJitter: Float = 0.4f
private const val VanillaRandomMagnitude: Float = 0.15f
private const val VanillaVelocityScale: Double = 0.4000000059604645
private const val VanillaUpwardImpulse: Double = 0.10000000149011612
