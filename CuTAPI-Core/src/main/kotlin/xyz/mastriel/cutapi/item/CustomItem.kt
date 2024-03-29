package xyz.mastriel.cutapi.item

import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.Listener
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.item.behaviors.DisplayAs
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior
import xyz.mastriel.cutapi.item.behaviors.StaticLore
import xyz.mastriel.cutapi.item.behaviors.itemBehaviorHolder
import xyz.mastriel.cutapi.pdc.tags.ItemBehaviorTagContainer
import xyz.mastriel.cutapi.pdc.tags.TagContainer
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.ref
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.personalized.personalized
import kotlin.reflect.KClass


object CustomItemSerializer : IdentifiableSerializer<CustomItem<*>>("customMaterial", CustomItem)

/**
 * An alias for a [CustomItem<*>](CustomItem). This has a CuTItemStack as its stack type.
 *
 * @see CustomItem
 */
typealias BasicCustomItem = CustomItem<*>

@Serializable(with = CustomItemSerializer::class)
open class CustomItem<TStack: CuTItemStack>(
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
                texture = ref(Plugin, "items/unknown_item.png")
            }
        }

        override fun get(id: Identifier): CustomItem<*> {
            return super.getOrNull(id) ?: return Unknown
        }

        override fun register(item: CustomItem<*>) : CustomItem<*> {
            val plugin = item.id.plugin

            if (plugin != null) Bukkit.getServer().pluginManager.registerEvents(item, plugin)
            return super.register(item).also { item.descriptor.onRegister.trigger(ItemRegisterEvent(item)) }
        }
    }
}
