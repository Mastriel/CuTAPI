package xyz.mastriel.cutapi.block.breaklogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

public class CustomMiningMathTest {
    @Test
    public fun `authoritative crack overlays use distinct synthetic breaker ids`() {
        val first = CustomMiningBreakerIds.allocate()
        val second = CustomMiningBreakerIds.allocate()

        assertTrue(first < 0)
        assertTrue(second < 0)
        assertNotEquals(first, second)
    }

    @Test
    public fun `hardness edge cases are authoritative`() {
        assertEquals(0.0f, CustomMiningMath.progressPerTick(inputs(hardness = -1.0f)))
        assertEquals(1.0f, CustomMiningMath.progressPerTick(inputs(hardness = 0.0f)))
    }

    @Test
    public fun `correct tool uses vanilla thirty divisor and incorrect uses one hundred`() {
        assertEquals(1.0f / 30.0f, CustomMiningMath.progressPerTick(inputs(correctTool = true)))
        assertEquals(1.0f / 100.0f, CustomMiningMath.progressPerTick(inputs(correctTool = false)))
    }

    @Test
    public fun `dynamic multipliers are applied in vanilla order`() {
        val progress = CustomMiningMath.progressPerTick(
            inputs(
                baseToolSpeed = 4.0f,
                miningEfficiency = 2.0,
                hasteLevel = 1,
                blockBreakSpeed = 1.5,
                submergedMiningSpeed = 0.2,
                underwater = true,
                onGround = false,
            ),
        )
        assertEquals(6.0 * 1.2 * 1.5 * 0.2 / 5.0 / 30.0, progress.toDouble(), 0.000001)
    }

    private fun inputs(
        hardness: Float = 1.0f,
        baseToolSpeed: Float = 1.0f,
        correctTool: Boolean = true,
        miningEfficiency: Double = 0.0,
        hasteLevel: Int = 0,
        fatigueLevel: Int = 0,
        blockBreakSpeed: Double = 1.0,
        submergedMiningSpeed: Double = 0.2,
        underwater: Boolean = false,
        onGround: Boolean = true,
    ): MiningTickInputs = MiningTickInputs(
        hardness,
        baseToolSpeed,
        correctTool,
        miningEfficiency,
        hasteLevel,
        fatigueLevel,
        blockBreakSpeed,
        submergedMiningSpeed,
        underwater,
        onGround,
    )
}
