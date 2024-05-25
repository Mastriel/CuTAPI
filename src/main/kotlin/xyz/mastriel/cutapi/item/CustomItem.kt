package xyz.mastriel.cutapi.item

import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.item.ItemStackUtility.customItem
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.item.behaviors.DisplayAs
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior
import xyz.mastriel.cutapi.item.behaviors.StaticLore
import xyz.mastriel.cutapi.item.behaviors.itemBehaviorHolder
import xyz.mastriel.cutapi.pdc.tags.ItemBehaviorTagContainer
import xyz.mastriel.cutapi.pdc.tags.TagContainer
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.colored
import kotlin.reflect.KClass


object CustomItemSerializer : IdentifiableSerializer<CustomItem<*>>("customMaterial", CustomItem)

/**
 * An alias for a [CustomItem<*>](CustomItem). This has a CuTItemStack as its stack type.
 *
 * @see CustomItem
 */
typealias AnyCustomItem = CustomItem<*>

@Serializable(with = CustomItemSerializer::class)
open class CustomItem<TStack : CuTItemStack>(
    override val id: Identifier,
    val type: Material,
    val stackTypeClass: KClass<out TStack>,
    descriptor: ItemDescriptor? = null
) : Identifiable, Listener, BehaviorHolder<ItemBehavior> {


    /**
     * The descriptor that describes the custom material's default values, such
     * as a default name, default lore, behaviors, etc.
     */
    open val descriptor = descriptor ?: defaultItemDescriptor()

    init {
        if (descriptor != null) {
            addAutoDisplayAs(descriptor)
        }
    }

    private fun addAutoDisplayAs(descriptor: ItemDescriptor) {
        val behaviors = descriptor.itemBehaviors as? MutableList ?: return
        val plugin = id.plugin ?: return

        val autoDisplayAs = CuTAPI.getDescriptor(plugin)
            .options.autoDisplayAsForTexturedItems ?: return

        behaviors.add(DisplayAs(autoDisplayAs))
    }

    fun createItemStack(quantity: Int = 1) =
        CuTItemStack.create<TStack>(this, quantity)

    open fun onCreate(item: CuTItemStack) {}

    private val behaviorHolder by lazy { itemBehaviorHolder(this) }

    override fun hasBehavior(behavior: KClass<out ItemBehavior>) = behaviorHolder.hasBehavior(behavior)
    override fun <T : ItemBehavior> getBehavior(behavior: KClass<T>) = behaviorHolder.getBehavior(behavior)
    override fun <T : ItemBehavior> getBehaviorOrNull(behavior: KClass<T>) =
        behaviorHolder.getBehaviorOrNull(behavior)

    override fun hasBehavior(behaviorId: Identifier) = behaviorHolder.hasBehavior(behaviorId)
    override fun <T : ItemBehavior> getBehavior(behaviorId: Identifier) = behaviorHolder.getBehavior<T>(behaviorId)
    override fun <T : ItemBehavior> getBehaviorOrNull(behaviorId: Identifier) =
        behaviorHolder.getBehaviorOrNull<T>(behaviorId)

    override fun getAllBehaviors(): Set<ItemBehavior> = behaviorHolder.getAllBehaviors()

    inline fun <reified B : ItemBehavior> hasBehavior() = hasBehavior(B::class)
    inline fun <reified B : ItemBehavior> getBehavior() = getBehavior(B::class)
    inline fun <reified B : ItemBehavior> getBehaviorOrNull() = getBehaviorOrNull(B::class)

    protected fun getData(item: CuTItemStack): TagContainer {
        return ItemBehaviorTagContainer(item.handle, id.copy(namespace = id.namespace, key = id.key + "/data"))
    }


    companion object : IdentifierRegistry<CustomItem<*>>("Custom Items") {
        val Unknown = customItem(
            unknownID(),
            Material.ANVIL
        ) {

            behavior(StaticLore("&cYou probably shouldn't have this...".colored))
            behavior(DisplayAs(Material.GLISTERING_MELON_SLICE))
            display {
                texture = itemTexture(Plugin, "items/unknown_item.png")
            }
        }

        override fun get(id: Identifier): CustomItem<*> {
            return super.getOrNull(id) ?: return Unknown
        }

        override fun register(item: CustomItem<*>): CustomItem<*> {
            val plugin = item.id.plugin

            if (plugin != null) {
                val bukkitPlugin = plugin.plugin
                Bukkit.getServer().pluginManager.registerEvents(item, bukkitPlugin)
            }
            return super.register(item).also { item.descriptor.onRegister.trigger(ItemRegisterEvent(item)) }
        }
    }
}


/**
 * Represents an item that is either a normal item stack or a CuT item stack.
 */
sealed class AgnosticMaterial {

    fun matches(item: ItemStack) = item.agnosticMaterial == this

    /** The expected vanilla material of this agnostic material.
     *
     * For vanilla items, this is always just the material of the item.
     * For custom items, this is the material that the custom item is assigned by default.
     */
    abstract val expectedVanillaMaterial: Material

    data class Custom internal constructor(val itemType: AnyCustomItem) : AgnosticMaterial() {
        fun custom() = itemType

        override val expectedVanillaMaterial: Material
            get() = itemType.type
    }

    data class Vanilla internal constructor(private val material: Material) : AgnosticMaterial() {
        fun vanilla() = material

        override val expectedVanillaMaterial: Material
            get() = material
    }
}

val ItemStack.agnosticMaterial: AgnosticMaterial
    get() {
        if (this.isCustom) {
            return AgnosticMaterial.Custom(this.customItem)
        }
        return AgnosticMaterial.Vanilla(this.type)
    }

val CuTItemStack.agnosticMaterial: AgnosticMaterial
    get() {
        return AgnosticMaterial.Custom(this.type)
    }


fun Material.toAgnostic(): AgnosticMaterial.Vanilla {
    return AgnosticMaterial.Vanilla(this)
}

fun AnyCustomItem.toAgnostic(): AgnosticMaterial.Custom {
    return AgnosticMaterial.Custom(this)
}