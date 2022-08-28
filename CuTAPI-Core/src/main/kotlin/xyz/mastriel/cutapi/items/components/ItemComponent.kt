package xyz.mastriel.cutapi.items.components

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import de.tr7zw.changeme.nbtapi.NBTCompound
import de.tr7zw.changeme.nbtapi.NBTContainer
import net.kyori.adventure.text.Component
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.items.CustomItemStack
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent
import xyz.mastriel.cutapi.nbt.tags.TagHolder
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.ReferenceRegistry
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * A class for all Item Components.
 *
 * An Item Component is essentially a small bit of data that can be attached to any [CustomItemStack] in
 * their CustomMaterial's onCreate functions that can alter behavior without having a ton of boilerplate
 * code to do so. An example is shown below:
 *
 */
abstract class ItemComponent(
    override val id: Identifier
) : Identifiable, TagHolder(container = NBTContainer()) {

    fun mergeWithCompound(container: NBTCompound) {
        this.container.mergeCompound(container)
    }

    /**
     * The lore which this component shows when applied to a [CustomItemStack].
     */
    open val lore : Component? = null

    /**
     * Called whenever this [ItemComponent] is applied to a [CustomItemStack].
     * @param item The [CustomItemStack] involved.
     */
    open fun onApply(item: CustomItemStack) {}

    /**
     * Called whenever this [ItemComponent] is obtained, through any means (chest, picked up, given, etc.)
     * @param event The [CustomItemObtainEvent].
     */
    open fun onObtain(event: CustomItemObtainEvent) {}

    /**
     * Called a [PlayerInteractEvent] is called on a [CustomItemStack] with this [ItemComponent]
     * @param item The [CustomItemStack] involved.
     */
    open fun onInteract(item: CustomItemStack, event: PlayerInteractEvent) {}

    companion object : ReferenceRegistry<ItemComponent>() {

        internal fun getConstructor(kClass: KClass<ItemComponent>) : KFunction<ItemComponent> {
            val constructor = kClass.constructors
                .find { it.parameters.isEmpty() } ?: error("ItemComponent does not have a no-args constructor.")
            return constructor
        }

        internal fun create(kClass: KClass<ItemComponent>) : ItemComponent {
            val constructor = getConstructor(kClass)
            constructor.isAccessible = true
            val component = constructor.call()
            constructor.isAccessible = false
            return component
        }

        override fun register(item: KClass<ItemComponent>) {
            val hasProperties = item.declaredMemberProperties.isNotEmpty()
            if (hasProperties) {
                val plugin = (item.companionObjectInstance as? Identifiable)?.id?.plugin?.name
                Plugin.warn("${item.simpleName} has member properties with backing fields. " +
                        "Components will not automatically serialize to an ItemStack, so this will probably lead to " +
                        "unexpected behavior. (fault of ${plugin ?: "not CuTAPI"})")
            }
            getConstructor(item)
            super.register(item)
        }
    }



}