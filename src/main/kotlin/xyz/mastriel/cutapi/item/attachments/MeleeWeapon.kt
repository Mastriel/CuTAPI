@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item.attachments

import xyz.mastriel.cutapi.data.BuiltinSerializers
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.Weapon
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlotGroup
import xyz.mastriel.cutapi.attachment.ItemAttachment
import xyz.mastriel.cutapi.data.Schema
import xyz.mastriel.cutapi.data.VariantSerializer
import xyz.mastriel.cutapi.data.schema
import xyz.mastriel.cutapi.item.ItemAttachmentMaterializer
import xyz.mastriel.cutapi.item.ItemTraitClaim
import xyz.mastriel.cutapi.item.itemAttachmentMaterializer
import xyz.mastriel.cutapi.registry.id
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Declares the complete, player-facing melee characteristics of an item.
 *
 * Damage and attack speed replace the backing item's canonical base modifiers. Additional
 * [ModifyAttribute] attachments with distinct keys remain additive.
 */
public data class MeleeWeapon(
    public val attackDamage: Double,
    public val attacksPerSecond: Double,
    public val durabilityCost: Int = 1,
    public val disableBlockingFor: Duration = Duration.ZERO,
) : ItemAttachment {
    init {
        require(attackDamage.isFinite() && attackDamage >= 0.0) {
            "MeleeWeapon.attackDamage must be a finite, non-negative number."
        }
        require(attacksPerSecond.isFinite() && attacksPerSecond > 0.0) {
            "MeleeWeapon.attacksPerSecond must be a finite, positive number."
        }
        require(durabilityCost >= 0) {
            "MeleeWeapon.durabilityCost must be non-negative."
        }
        require(disableBlockingFor.isFinite() && disableBlockingFor >= Duration.ZERO) {
            "MeleeWeapon.disableBlockingFor must be a finite, non-negative duration."
        }
        require(disableBlockingFor.toDouble(DurationUnit.SECONDS).toFloat().isFinite()) {
            "MeleeWeapon.disableBlockingFor is too large for Minecraft's weapon component."
        }
    }

    public companion object : Schema<MeleeWeapon> by schema(id("cutapi:attachment/melee_weapon"), {
        property(MeleeWeapon::attackDamage, VariantSerializer.Double, name = "attack_damage")
        property(MeleeWeapon::attacksPerSecond, VariantSerializer.Double, name = "attacks_per_second")
        property(MeleeWeapon::durabilityCost, VariantSerializer.Int, name = "durability_cost") {
            optional(omitDefaults = true) { 1 }
        }
        property(MeleeWeapon::disableBlockingFor, BuiltinSerializers.DurationSeconds, name = "disable_blocking_for_seconds") {
            optional(omitDefaults = true) { Duration.ZERO }
        }
    })
}

internal val MeleeWeaponMaterializer: ItemAttachmentMaterializer<MeleeWeapon> by lazy {
    itemAttachmentMaterializer(
        id = MeleeWeapon.id / "materializer",
        schema = MeleeWeapon,
        revision = 1,
        claims = setOf(
            ItemTraitClaim.KeyedAttributeModifiers,
            ItemTraitClaim.Component(DataComponentTypes.WEAPON),
        ),
    ) { context, output ->
        val weapon = context.attachments.single()
        output.setAttributeModifier(
            Attribute.ATTACK_DAMAGE,
            AttributeModifier(
                BaseAttackDamageKey,
                meleeAttackDamageModifierAmount(weapon.attackDamage),
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND,
            ),
        )
        output.setAttributeModifier(
            Attribute.ATTACK_SPEED,
            AttributeModifier(
                BaseAttackSpeedKey,
                meleeAttackSpeedModifierAmount(weapon.attacksPerSecond),
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND,
            ),
        )
        output.set(
            DataComponentTypes.WEAPON,
            Weapon.weapon()
                .itemDamagePerAttack(weapon.durabilityCost)
                .disableBlockingForSeconds(weapon.disableBlockingFor.toDouble(DurationUnit.SECONDS).toFloat())
                .build(),
        )
    }
}

internal val BaseAttackDamageKey = id("minecraft:base_attack_damage").toNamespacedKey()
internal val BaseAttackSpeedKey = id("minecraft:base_attack_speed").toNamespacedKey()

private const val PlayerBaseAttackDamage: Double = 1.0
private const val PlayerBaseAttacksPerSecond: Double = 4.0

internal fun meleeAttackDamageModifierAmount(attackDamage: Double): Double =
    attackDamage - PlayerBaseAttackDamage

internal fun meleeAttackSpeedModifierAmount(attacksPerSecond: Double): Double =
    attacksPerSecond - PlayerBaseAttacksPerSecond

