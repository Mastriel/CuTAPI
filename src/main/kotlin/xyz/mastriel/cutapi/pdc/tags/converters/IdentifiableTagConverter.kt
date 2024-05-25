package xyz.mastriel.cutapi.pdc.tags.converters

import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.registry.id
import kotlin.reflect.KClass


class IdentifiableTagConverter<T : Identifiable>(
    val registry: IdentifierRegistry<T>,
    identifiableKClass: KClass<T>
) : TagConverter<String, T>(String::class, identifiableKClass) {

    override fun fromPrimitive(primitive: String): T {
        return registry.get(id(primitive))
    }

    override fun toPrimitive(complex: T): String {
        return complex.id.toString()
    }

    companion object {
        val CustomItem = IdentifiableTagConverter(CustomItemRegistry)
        val CustomBlock = IdentifiableTagConverter(CustomBlockRegistry)
        val CustomTile = IdentifiableTagConverter(CustomTileRegistry)
        val CustomTileEntity = IdentifiableTagConverter(CustomTileEntityRegistry)
    }
}

private typealias CustomItemRegistry = xyz.mastriel.cutapi.item.CustomItem<*>
private typealias CustomBlockRegistry = xyz.mastriel.cutapi.block.CustomBlock<*>
private typealias CustomTileRegistry = xyz.mastriel.cutapi.block.CustomTile<*>
private typealias CustomTileEntityRegistry = xyz.mastriel.cutapi.block.CustomTileEntity<*>

inline fun <reified T : Identifiable> IdentifiableTagConverter(registry: IdentifierRegistry<T>) =
    IdentifiableTagConverter(registry, T::class)