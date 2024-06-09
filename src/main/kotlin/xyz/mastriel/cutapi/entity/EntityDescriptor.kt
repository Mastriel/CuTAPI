package xyz.mastriel.cutapi.entity

import net.kyori.adventure.text.*
import org.bukkit.entity.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.entity.behaviors.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.personalized.*

public class EntityDescriptor(
    public val name: PersonalizedWithDefault<Component>? = null,
    public val texture: Personalized<ResourceRef<Texture2D>>? = null,
    public val maxHealth: Int = 20,
    public val entityBehaviors: List<EntityBehavior> = listOf(),
    public val equipment: EntityEquipment
) {

}


public open class EntityDescriptorBuilder {
    public var name: PersonalizedWithDefault<Component>? = null
    public var maxHealth: Int = 20
    public var texture: Personalized<ResourceRef<Texture2D>>? = null

    public var equipment: EntityEquipment = EntityEquipment()
        private set

    public val entityBehaviors: MutableList<EntityBehavior> = mutableListOf<EntityBehavior>()

    public fun behavior(vararg behaviors: EntityBehavior) {
        for (behavior in behaviors) {
            if (this.entityBehaviors.any { it.id == behavior.id } && !behavior.isRepeatable())
                error("${behavior.id} lacks a RepeatableBehavior annotation to be repeatable.")
            this.entityBehaviors += behavior
        }
    }

    public fun behavior(behaviors: Collection<EntityBehavior>) {
        for (behavior in behaviors) {
            if (this.entityBehaviors.any { it.id == behavior.id } && !behavior.isRepeatable())
                error("${behavior.id} lacks a RepeatableBehavior annotation to be repeatable.")
            this.entityBehaviors += behavior
        }
    }

    public fun equipment(block: EquipmentBuilder.() -> Unit) {
        equipment = EquipmentBuilder().apply(block).build()
    }

    public fun build(): EntityDescriptor {
        return EntityDescriptor(
            name,
            texture,
            maxHealth,
            entityBehaviors,
            equipment
        )
    }
}

public data class EntityEquipment(
    val helmet: ItemStack? = null,
    val chestplate: ItemStack? = null,
    val leggings: ItemStack? = null,
    val boots: ItemStack? = null,
    val mainhand: ItemStack? = null,
    val offhand: ItemStack? = null,
) {
    public fun applyToEntity(entity: Entity) {
        if (entity !is LivingEntity) return
        with(entity.equipment) {
            if (this == null) return@with
            helmet = helmet
            chestplate = chestplate
            leggings = leggings
            boots = boots
            setItemInMainHand(mainhand)
            setItemInOffHand(offhand)
        }
    }
}

public class EquipmentBuilder {

    public val helmet: ItemStack? = null
    public val chestplate: ItemStack? = null
    public val leggings: ItemStack? = null
    public val boots: ItemStack? = null
    public val mainhand: ItemStack? = null
    public val offhand: ItemStack? = null

    public fun build(): EntityEquipment {
        return EntityEquipment(
            helmet,
            chestplate,
            leggings,
            boots,
            mainhand,
            offhand
        )
    }
}


public fun entityDescriptor(block: EntityDescriptorBuilder.() -> Unit): EntityDescriptor =
    EntityDescriptorBuilder().apply(block).build()

public fun defaultEntityDescriptor(): EntityDescriptor =
    EntityDescriptorBuilder().build()