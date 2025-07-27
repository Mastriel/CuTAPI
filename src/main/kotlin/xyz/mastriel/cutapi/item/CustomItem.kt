package xyz.mastriel.cutapi.item

import kotlinx.serialization.*
import org.bukkit.*
import org.bukkit.event.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.item.ItemStackUtility.customItem
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.item.behaviors.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import kotlin.reflect.*


public object CustomItemSerializer : IdentifiableSerializer<CustomItem<*>>("customMaterial", CustomItem)

/**
 * An alias for a [CustomItem<*>](CustomItem). This has a CuTItemStack as its stack type.
 *
 * @see CustomItem
 */
public typealias AnyCustomItem = CustomItem<*>

@Serializable(with = CustomItemSerializer::class)
public open class CustomItem<TStack : CuTItemStack>(
    override val id: Identifier,
    public val type: Material,
    public val stackTypeClass: KClass<out TStack>,
    descriptor: ItemDescriptor? = null
) : Identifiable, Listener, BehaviorHolder<ItemBehavior> {


    /**
     * The descriptor that describes the custom material's default values, such
     * as a default name, default lore, behaviors, etc.
     */
    public open val descriptor: ItemDescriptor = descriptor ?: defaultItemDescriptor()

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

    public fun createItemStack(quantity: Int = 1): TStack =
        CuTItemStack.create<TStack>(this, quantity)

    public open fun onCreate(item: CuTItemStack) {}

    private val behaviorHolder by lazy { itemBehaviorHolder(this) }

    override fun hasBehavior(behavior: KClass<out ItemBehavior>): Boolean = behaviorHolder.hasBehavior(behavior)
    override fun <T : ItemBehavior> getBehavior(behavior: KClass<T>): T = behaviorHolder.getBehavior(behavior)
    override fun <T : ItemBehavior> getBehaviorOrNull(behavior: KClass<T>): T? =
        behaviorHolder.getBehaviorOrNull(behavior)

    override fun hasBehavior(behaviorId: Identifier): Boolean = behaviorHolder.hasBehavior(behaviorId)
    override fun <T : ItemBehavior> getBehavior(behaviorId: Identifier): T = behaviorHolder.getBehavior<T>(behaviorId)
    override fun <T : ItemBehavior> getBehaviorOrNull(behaviorId: Identifier): T? =
        behaviorHolder.getBehaviorOrNull<T>(behaviorId)

    override fun getAllBehaviors(): Set<ItemBehavior> = behaviorHolder.getAllBehaviors()

    public inline fun <reified B : ItemBehavior> hasBehavior(): Boolean = hasBehavior(B::class)
    public inline fun <reified B : ItemBehavior> getBehavior(): B = getBehavior(B::class)
    public inline fun <reified B : ItemBehavior> getBehaviorOrNull(): B? = getBehaviorOrNull(B::class)

    protected fun getData(item: CuTItemStack): TagContainer {
        return ItemBehaviorTagContainer(item.handle, id.copy(namespace = id.namespace, key = id.key + "/data"))
    }


    public companion object : IdentifierRegistry<CustomItem<*>>("Custom Items") {
        internal val DeferredRegistry = defer(RegistryPriority(Int.MAX_VALUE))

        public val Unknown: CustomItem<CuTItemStack> = customItem(
            unknownID(),
            Material.ANVIL
        ) {

            behavior(StaticLore("&cYou probably shouldn't have this...".colored))
            behavior(DisplayAs(Material.GLISTERING_MELON_SLICE))
            display {
                texture = itemTexture(Plugin, "items/unknown_item.png")
            }
        }

        public val InventoryBackground: CustomItem<CuTItemStack> by DeferredRegistry.registerCustomItem(
            id = id(Plugin, "inventory_background"),
            Material.GLISTERING_MELON_SLICE
        ) {
            behavior(HideTooltip)

            display {
                texture = itemModel(Plugin, "ui/inventory_bg.model3d.json")
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
public sealed class AgnosticMaterial {

    public fun matches(item: ItemStack): Boolean = item.agnosticMaterial == this

    /** The expected vanilla material of this agnostic material.
     *
     * For vanilla items, this is always just the material of the item.
     * For custom items, this is the material that the custom item is assigned by default.
     */
    public abstract val expectedVanillaMaterial: Material

    public infix fun materialIs(value: Material): Boolean {
        if (this is Vanilla) {
            return this.vanilla() == value
        }
        return false
    }

    public infix fun materialIs(value: CustomItem<*>): Boolean {
        if (this is Custom) {
            return this.custom() == value
        }
        return false
    }

    public data class Custom(val itemType: AnyCustomItem) : AgnosticMaterial() {
        public fun custom(): CustomItem<*> = itemType

        override val expectedVanillaMaterial: Material
            get() = itemType.type
    }

    public data class Vanilla(private val material: Material) : AgnosticMaterial() {
        public fun vanilla(): Material = material

        override val expectedVanillaMaterial: Material
            get() = material
    }
}

public val ItemStack.agnosticMaterial: AgnosticMaterial
    get() {
        if (this.isCustom) {
            return AgnosticMaterial.Custom(this.customItem)
        }
        return AgnosticMaterial.Vanilla(this.type)
    }

public val CuTItemStack.agnosticMaterial: AgnosticMaterial
    get() {
        return AgnosticMaterial.Custom(this.type)
    }


public fun Material.toAgnostic(): AgnosticMaterial.Vanilla {
    return AgnosticMaterial.Vanilla(this)
}

public fun AnyCustomItem.toAgnostic(): AgnosticMaterial.Custom {
    return AgnosticMaterial.Custom(this)
}