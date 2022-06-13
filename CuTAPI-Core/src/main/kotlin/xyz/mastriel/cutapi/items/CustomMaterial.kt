package xyz.mastriel.cutapi.items

import kotlinx.serialization.Serializable
import org.bukkit.Material
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.IdentifiableSerializer
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierMap
import xyz.mastriel.cutapi.registry.descriptors.defaultMaterialDescriptor
import xyz.mastriel.cutapi.registry.descriptors.materialDescriptor


private object CustomMaterialSerializer : IdentifiableSerializer<CustomMaterial>("customMaterial", CustomMaterial)

@Serializable(with = CustomMaterialSerializer::class)
open class CustomMaterial(override val id: Identifier, val type: Material) : Identifiable {

    /**
     * The descriptor that describes the custom material's default values, such
     * as a default name, default lore, default NBT values, etc. These can be modified
     * dynamically in [onCreate].
     */
    open val materialDescriptor = defaultMaterialDescriptor()

    /**
     * A function that is called when a [CustomItemStack]'s constructor is called.
     *
     * @param itemStack The CustomItemStack being created.
     * */
    open fun onCreate(itemStack: CustomItemStack) {

    }

    companion object : IdentifierMap<CustomMaterial>() {
        override fun register(item: CustomMaterial) {
            super.register(item)

        }
    }
}