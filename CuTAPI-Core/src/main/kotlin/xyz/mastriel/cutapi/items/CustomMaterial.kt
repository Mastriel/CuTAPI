package xyz.mastriel.cutapi.items

import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.Listener
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.items.behaviors.MaterialBehavior
import xyz.mastriel.cutapi.items.behaviors.StaticLore
import xyz.mastriel.cutapi.items.behaviors.materialBehaviorHolder
import xyz.mastriel.cutapi.pdc.tags.MaterialBehaviorTagContainer
import xyz.mastriel.cutapi.pdc.tags.TagContainer
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.colored
import kotlin.reflect.KClass


private object CustomMaterialSerializer : IdentifiableSerializer<CustomMaterial>("customMaterial", CustomMaterial)

@Serializable(with = CustomMaterialSerializer::class)
open class CustomMaterial(
    override val id: Identifier,
    val type: Material,
    descriptor: MaterialDescriptor? = null
) : Identifiable, Listener, BehaviorHolder<MaterialBehavior> {


    /**
     * The descriptor that describes the custom material's default values, such
     * as a default name, default lore, behaviors, etc.
     */
    open val descriptor = descriptor ?: defaultMaterialDescriptor()


    fun createItemStack(quantity: Int) =
        CuTItemStack(this, quantity)

    open fun onCreate(item: CuTItemStack) {}

    private val behaviorHolder by lazy { materialBehaviorHolder(this) }

    override fun hasBehavior(behavior: KClass<out MaterialBehavior>) = behaviorHolder.hasBehavior(behavior)
    override fun <T : MaterialBehavior> getBehavior(behavior: KClass<T>) = behaviorHolder.getBehavior(behavior)
    override fun <T : MaterialBehavior> getBehaviorOrNull(behavior: KClass<T>) =
        behaviorHolder.getBehaviorOrNull(behavior)

    override fun getAllBehaviors(): Set<MaterialBehavior> = behaviorHolder.getAllBehaviors()

    inline fun <reified B : MaterialBehavior> hasBehavior() = hasBehavior(B::class)
    inline fun <reified B : MaterialBehavior> getBehavior() = getBehavior(B::class)
    inline fun <reified B : MaterialBehavior> getBehaviorOrNull() = getBehaviorOrNull(B::class)

    protected fun getData(item: CuTItemStack): TagContainer {
        return MaterialBehaviorTagContainer(item.handle, id.copy(namespace = id.namespace, id = id.id + "/data"))
    }


    companion object : IdentifierRegistry<CustomMaterial>() {
        val Unknown = customMaterial(
            unknownID(),
            Material.ANVIL,
            "Unknown".colored,
            listOf(StaticLore("&cYou probably shouldn't have this...".colored))
        )

        override fun get(id: Identifier): CustomMaterial {
            return super.getOrNull(id) ?: return Unknown
        }

        override fun register(item: CustomMaterial) {
            val plugin = item.id.plugin

            if (plugin != null) Bukkit.getServer().pluginManager.registerEvents(item, plugin)
            super.register(item)
        }


    }
}