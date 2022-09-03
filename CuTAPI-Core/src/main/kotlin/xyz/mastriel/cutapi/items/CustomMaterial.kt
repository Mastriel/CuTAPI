package xyz.mastriel.cutapi.items

import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.Listener
import xyz.mastriel.cutapi.items.components.ComponentHolder
import xyz.mastriel.cutapi.items.components.MaterialComponent
import xyz.mastriel.cutapi.items.components.materialComponentList
import xyz.mastriel.cutapi.nbt.tags.TagContainer
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.registry.descriptors.MaterialDescriptorBuilder
import xyz.mastriel.cutapi.registry.descriptors.defaultMaterialDescriptor
import xyz.mastriel.cutapi.registry.descriptors.materialDescriptor
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.nbt
import kotlin.reflect.KClass


private object CustomMaterialSerializer : IdentifiableSerializer<CustomMaterial>("customMaterial", CustomMaterial)

@Serializable(with = CustomMaterialSerializer::class)
open class CustomMaterial(
    override val id: Identifier,
    val type: Material,
    descriptor: MaterialDescriptorBuilder? = null
) : Identifiable, Listener, ComponentHolder {


    /**
     * The descriptor that describes the custom material's default values, such
     * as a default name, default lore, default NBT values, etc. These can be modified
     * dynamically in [onCreate].
     */
    open val descriptor = descriptor?.build() ?: defaultMaterialDescriptor()


    fun createItemStack(quantity: Int) =
        CuTItemStack(this, quantity)

    open fun onCreate(item: CuTItemStack) {}

    private val componentHolder by lazy { materialComponentList(this) }

    override fun hasComponent(component: KClass<out MaterialComponent>) = componentHolder.hasComponent(component)
    override fun <T : MaterialComponent> getComponent(component: KClass<T>) = componentHolder.getComponent(component)
    override fun <T : MaterialComponent> getComponentOrNull(component: KClass<T>) = componentHolder.getComponentOrNull(component)
    override fun getAllComponents(): Set<MaterialComponent> = componentHolder.getAllComponents()

    protected fun getData(item: CuTItemStack) : TagContainer {
        val compound = item.handle.nbt
            .getOrCreateCompound("$id/data")
        return TagContainer(compound)
    }


    companion object : IdentifierMap<CustomMaterial>() {
        val Unknown = object : CustomMaterial(unknownID(), Material.ANVIL) {
            override val descriptor = materialDescriptor {
                name = "&cUnknown".colored
                description {
                    emptyLine()
                    textComponent("&7You probably shouldn't have this.".colored)
                }
            }
        }

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