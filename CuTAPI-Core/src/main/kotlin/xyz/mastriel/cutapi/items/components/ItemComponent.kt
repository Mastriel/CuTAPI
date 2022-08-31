package xyz.mastriel.cutapi.items.components

import de.tr7zw.changeme.nbtapi.NBTCompound
import de.tr7zw.changeme.nbtapi.NBTContainer
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent
import xyz.mastriel.cutapi.nbt.tags.TagContainer
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
 * An Item Component is essentially a small bit of data that can be attached to any [CuTItemStack] in
 * their CustomMaterial's onCreate functions that can alter behavior without having a ton of boilerplate
 * code to do so. An example is shown below:
 *
 */
abstract class ItemComponent(
    override val id: Identifier
) : Identifiable, TagContainer(container = NBTContainer()) {

    init {
        require(isRegistered(this::class)) { "An ItemComponent must be registered before it is constructed. " +
                "Is this registered before a CustomMaterial that uses it?" }
    }

    /**
     * The lore which this component shows when applied to a [CuTItemStack].
     */
    open fun getLore(cuTItemStack: CuTItemStack, viewer: Player) : Component? = null

    /**
     * Called whenever this [ItemComponent] is applied to a [CuTItemStack].
     * @param item The [CuTItemStack] involved.
     */
    open fun onApply(item: CuTItemStack) {}

    /**
     * Called whenever this [ItemComponent] is obtained, through any means (chest, picked up, given, etc.)
     * @param event The [CustomItemObtainEvent].
     */
    open fun onObtain(event: CustomItemObtainEvent) {}

    /**
     * Called a [PlayerInteractEvent] is called on a [CuTItemStack] with this [ItemComponent]
     * @param item The [CuTItemStack] involved.
     */
    open fun onInteract(item: CuTItemStack, event: PlayerInteractEvent) {}

    companion object : ReferenceRegistry<ItemComponent>() {

        internal fun getConstructor(kClass: KClass<out ItemComponent>): KFunction<ItemComponent> {
            val constructor = kClass.constructors
                .find { it.parameters.isEmpty() } ?: error("ItemComponent does not have a no-args constructor.")
            return constructor
        }

        internal fun create(kClass: KClass<out ItemComponent>): ItemComponent {
            val constructor = getConstructor(kClass)
            constructor.isAccessible = true
            val component = constructor.call()
            constructor.isAccessible = false
            return component
        }

        override fun register(item: KClass<out ItemComponent>) {
            val hasProperties = item.declaredMemberProperties.isNotEmpty()
            if (hasProperties) {
                val plugin = (item.companionObjectInstance as? Identifiable)?.id?.plugin?.name
                Plugin.warn(
                    "${item.simpleName} has member properties with backing fields. " +
                            "Components will not automatically serialize to an ItemStack, so this will probably lead to " +
                            "unexpected behavior. (fault of ${plugin ?: "not CuTAPI"})"
                )
            }
            getConstructor(item)
            super.register(item)
        }
    }

    /**
     * Sets this component's NBT compound to the supplied compound, then merges the previous compound's data.
     * This is done to ensure that the parent tree is set up correctly for CuTItemStacks, so that it can
     * direct apply.
     */
    internal fun bind(container: NBTCompound) {
        val previousContainer = this.compound

        this.compound = container
        this.compound.mergeCompound(previousContainer)
    }
}