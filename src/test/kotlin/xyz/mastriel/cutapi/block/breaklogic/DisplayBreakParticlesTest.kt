package xyz.mastriel.cutapi.block.breaklogic

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class DisplayBreakParticlesTest {
    @Test
    public fun `full cube completion particles use the vanilla four by four by four lattice`() {
        val particles = displayBreakCompletionParticles(Random(1234))
        val expectedOffsets = setOf(0.125, 0.375, 0.625, 0.875)

        assertEquals(64, particles.size)
        assertEquals(expectedOffsets, particles.map { it.offsetX }.toSet())
        assertEquals(expectedOffsets, particles.map { it.offsetY }.toSet())
        assertEquals(expectedOffsets, particles.map { it.offsetZ }.toSet())
        assertEquals(64, particles.map { Triple(it.offsetX, it.offsetY, it.offsetZ) }.toSet().size)
    }

    @Test
    public fun `completion particle velocities stay within the vanilla transformed bounds`() {
        val particles = displayBreakCompletionParticles(Random(5678))

        particles.forEach { particle ->
            assertTrue(particle.velocityX.isFinite())
            assertTrue(particle.velocityY.isFinite())
            assertTrue(particle.velocityZ.isFinite())
            assertTrue(abs(particle.velocityX) <= 0.180001)
            assertTrue(particle.velocityY in -0.080001..0.280001)
            assertTrue(abs(particle.velocityZ) <= 0.180001)
        }
        assertTrue(particles.any { abs(it.velocityX) + abs(it.velocityY) + abs(it.velocityZ) > 0.1 })
    }
}
