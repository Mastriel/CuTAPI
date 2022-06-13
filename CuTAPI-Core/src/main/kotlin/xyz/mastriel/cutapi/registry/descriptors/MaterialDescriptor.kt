package xyz.mastriel.cutapi.registry.descriptors

import net.kyori.adventure.text.Component


data class MaterialDescriptor internal constructor(
    val name: Component?,
    val lore: List<Component>?,
)

/**
 * A descriptor for a [xyz.mastriel.cutapi.items.CustomMaterial], which contains useful info
 * such as a name, lore, NBT, etc.
 *
 * @param block The builder, used to create the final [MaterialDescriptor]
 */
fun materialDescriptor(block: MaterialDescriptorBuilder.() -> Unit) =
    MaterialDescriptorBuilder().apply(block)

/**
 * A default descriptor for a [xyz.mastriel.cutapi.items.CustomMaterial]. No values are set in this, and
 * and without a custom [xyz.mastriel.cutapi.items.CustomMaterial.onCreate] block, this will create a
 * vanilla Minecraft item with only the [xyz.mastriel.cutapi.registry.Identifier] NBT given to all custom items
 * to identify them.
 *
 * @param block The builder, used to create the final [MaterialDescriptor]
 */
fun defaultMaterialDescriptor() =
    MaterialDescriptorBuilder().build()

class MaterialDescriptorBuilder {
    var name : Component? = null
    var lore : List<Component>? = null

    fun build() : MaterialDescriptor {
        return MaterialDescriptor(
            name,
            lore
        )
    }
}