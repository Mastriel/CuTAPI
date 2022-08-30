package xyz.mastriel.cutapi.items

import kotlinx.serialization.Serializable
import org.bukkit.Material
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.registry.descriptors.defaultMaterialDescriptor
import xyz.mastriel.cutapi.registry.descriptors.materialDescriptor
import xyz.mastriel.cutapi.utils.colored


private object CustomMaterialSerializer : IdentifiableSerializer<CustomMaterial>("customMaterial", CustomMaterial)

@Serializable(with = CustomMaterialSerializer::class)
open class CustomMaterial(override val id: Identifier, val type: Material) : Identifiable {


    /**
     * The descriptor that describes the custom material's default values, such
     * as a default name, default lore, default NBT values, etc. These can be modified
     * dynamically in [onCreate].
     */
    open val materialDescriptor = defaultMaterialDescriptor()


    fun createItemStack(quantity: Int) =
        CuTItemStack(this, quantity)

    open fun onCreate(item: CuTItemStack) {}


    companion object : IdentifierMap<CustomMaterial>() {
        val Unknown = object : CustomMaterial(unknownID(), Material.ANVIL) {
            override val materialDescriptor = materialDescriptor {
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


    }
}