package xyz.mastriel.cutapi.items

import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.Listener
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.items.behaviors.ItemBehavior
import xyz.mastriel.cutapi.items.behaviors.StaticLore
import xyz.mastriel.cutapi.items.behaviors.itemBehaviorHolder
import xyz.mastriel.cutapi.pdc.tags.ItemBehaviorTagContainer
import xyz.mastriel.cutapi.pdc.tags.TagContainer
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.colored
import kotlin.reflect.KClass


private object CustomItemSerializer : IdentifiableSerializer<CustomItem>("customMaterial", CustomItem)

@Serializable(with = CustomItemSerializer::class)
open class CustomItem(
    override val id: Identifier,
    val type: Material,
    descriptor: ItemDescriptor? = null
) : Identifiable, Listener, BehaviorHolder<ItemBehavior> {


    /**
     * The descriptor that describes the custom material's default values, such
     * as a default name, default lore, behaviors, etc.
     */
    open val descriptor = descriptor ?: defaultItemDescriptor()


    fun createItemStack(quantity: Int) =
        CuTItemStack(this, quantity)

    open fun onCreate(item: CuTItemStack) {}

    private val behaviorHolder by lazy { itemBehaviorHolder(this) }

    override fun hasBehavior(behavior: KClass<out ItemBehavior>) = behaviorHolder.hasBehavior(behavior)
    override fun <T : ItemBehavior> getBehavior(behavior: KClass<T>) = behaviorHolder.getBehavior(behavior)
    override fun <T : ItemBehavior> getBehaviorOrNull(behavior: KClass<T>) =
        behaviorHolder.getBehaviorOrNull(behavior)

    override fun getAllBehaviors(): Set<ItemBehavior> = behaviorHolder.getAllBehaviors()

    inline fun <reified B : ItemBehavior> hasBehavior() = hasBehavior(B::class)
    inline fun <reified B : ItemBehavior> getBehavior() = getBehavior(B::class)
    inline fun <reified B : ItemBehavior> getBehaviorOrNull() = getBehaviorOrNull(B::class)

    protected fun getData(item: CuTItemStack): TagContainer {
        return ItemBehaviorTagContainer(item.handle, id.copy(namespace = id.namespace, id = id.id + "/data"))
    }


    companion object : IdentifierRegistry<CustomItem>() {
        val Unknown = customItem(
            unknownID(),
            Material.ANVIL,
            "Unknown".colored,
            listOf(StaticLore("&cYou probably shouldn't have this...".colored))
        )

        override fun get(id: Identifier): CustomItem {
            return super.getOrNull(id) ?: return Unknown
        }

        override fun register(item: CustomItem) {
            val plugin = item.id.plugin

            if (plugin != null) Bukkit.getServer().pluginManager.registerEvents(item, plugin)
            super.register(item)
        }


    }
}