package xyz.mastriel.cutapi.entity

import net.kyori.adventure.text.Component
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.behavior.isRepeatable
import xyz.mastriel.cutapi.entity.behaviors.EntityBehavior
import xyz.mastriel.cutapi.resourcepack.resourcetypes.TextureRef
import xyz.mastriel.cutapi.utils.personalized.Personalized
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault

class EntityDescriptor(
    val name: PersonalizedWithDefault<Component>? = null,
    val texture: Personalized<TextureRef>? = null,
    val maxHealth: Int = 20,
    val entityBehaviors: List<EntityBehavior> = listOf(),
    val equipment: EntityEquipment
) {

}


open class EntityDescriptorBuilder {
    var name: PersonalizedWithDefault<Component>? = null
    var maxHealth: Int = 20
    var texture : Personalized<TextureRef>? = null

    var equipment : EntityEquipment = EntityEquipment()
        private set

    val entityBehaviors = mutableListOf<EntityBehavior>()

    fun behavior(vararg behaviors: EntityBehavior) {
        for (behavior in behaviors) {
            if (this.entityBehaviors.any { it.id == behavior.id } && !behavior.isRepeatable())
                error("${behavior.id} lacks a RepeatableBehavior annotation to be repeatable.")
            this.entityBehaviors += behavior
        }
    }

    fun behavior(behaviors: Collection<EntityBehavior>) {
        for (behavior in behaviors) {
            if (this.entityBehaviors.any { it.id == behavior.id } && !behavior.isRepeatable())
                error("${behavior.id} lacks a RepeatableBehavior annotation to be repeatable.")
            this.entityBehaviors += behavior
        }
    }

    fun equipment(block: EquipmentBuilder.() -> Unit) {
        equipment = EquipmentBuilder().apply(block).build()
    }

    fun build() : EntityDescriptor {
        return EntityDescriptor(
            name,
            texture,
            maxHealth,
            entityBehaviors,
            equipment
        )
    }
}

data class EntityEquipment(
    val helmet: ItemStack? = null,
    val chestplate: ItemStack? = null,
    val leggings: ItemStack? = null,
    val boots: ItemStack? = null,
    val mainhand: ItemStack? = null,
    val offhand: ItemStack? = null,
) {
    fun applyToEntity(entity: Entity) {
        if (entity !is LivingEntity) return
        with(entity.equipment) {
            this?.helmet = helmet
            this?.chestplate = chestplate
            this?.leggings = leggings
            this?.boots = boots
            this?.setItemInMainHand(mainhand)
            this?.setItemInOffHand(offhand)
        }
    }
}

class EquipmentBuilder {

    val helmet: ItemStack? = null
    val chestplate: ItemStack? = null
    val leggings: ItemStack? = null
    val boots: ItemStack? = null
    val mainhand: ItemStack? = null
    val offhand: ItemStack? = null

    fun build() : EntityEquipment {
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


fun entityDescriptor(block: EntityDescriptorBuilder.() -> Unit) =
    EntityDescriptorBuilder().apply(block).build()

fun defaultEntityDescriptor() =
    EntityDescriptorBuilder().build()