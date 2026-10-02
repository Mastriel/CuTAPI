package xyz.mastriel.cutapi.item.attachments

import io.papermc.paper.datacomponent.DataComponentTypes
import xyz.mastriel.cutapi.data.getOrThrow
import xyz.mastriel.cutapi.item.ItemTraitClaim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

public class MeleeWeaponTest {
    @Test
    public fun `player-facing values convert to canonical modifier amounts`() {
        assertEquals(6.5, meleeAttackDamageModifierAmount(7.5))
        assertEquals(-2.3, meleeAttackSpeedModifierAmount(1.7))
    }

    @Test
    public fun `schema round trip preserves weapon behavior and optional defaults`() {
        val weapon = MeleeWeapon(
            attackDamage = 9.0,
            attacksPerSecond = 1.25,
            durabilityCost = 2,
            disableBlockingFor = 750.milliseconds,
        )

        assertEquals(weapon, MeleeWeapon.deserialize(MeleeWeapon.serialize(weapon).getOrThrow()).getOrThrow())
        assertEquals(
            MeleeWeapon(5.0, 2.0),
            MeleeWeapon.deserialize(MeleeWeapon.serialize(MeleeWeapon(5.0, 2.0)).getOrThrow()).getOrThrow(),
        )
    }

    @Test
    public fun `materializer owns canonical attributes and vanilla weapon behavior`() {
        assertTrue(ItemTraitClaim.KeyedAttributeModifiers in MeleeWeaponMaterializer.claims)
        assertTrue(
            ItemTraitClaim.Component(DataComponentTypes.WEAPON) in MeleeWeaponMaterializer.claims,
        )
    }

    @Test
    public fun `invalid melee characteristics are rejected`() {
        assertFailsWith<IllegalArgumentException> { MeleeWeapon(Double.NaN, 1.0) }
        assertFailsWith<IllegalArgumentException> { MeleeWeapon(1.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { MeleeWeapon(1.0, 1.0, durabilityCost = -1) }
        assertFailsWith<IllegalArgumentException> {
            MeleeWeapon(1.0, 1.0, disableBlockingFor = (-1).milliseconds)
        }
    }
}
