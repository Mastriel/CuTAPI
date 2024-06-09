package xyz.mastriel.cutapi.pdc.tags.converters

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*


private typealias CustomItemRegistry = xyz.mastriel.cutapi.item.CustomItem<*>
private typealias CustomBlockRegistry = xyz.mastriel.cutapi.block.CustomBlock<*>
private typealias CustomTileRegistry = xyz.mastriel.cutapi.block.CustomTile<*>
private typealias CustomTileEntityRegistry = xyz.mastriel.cutapi.block.CustomTileEntity<*>


public class IdentifiableTagConverter<T : Identifiable>(
    public val registry: IdentifierRegistry<T>,
    identifiableKClass: KClass<T>
) : TagConverter<String, T>(String::class, identifiableKClass) {

    override fun fromPrimitive(primitive: String): T {
        return registry.get(id(primitive))
    }

    override fun toPrimitive(complex: T): String {
        return complex.id.toString()
    }

    public companion object {
        public val CustomItem: IdentifiableTagConverter<CustomItemRegistry> =
            IdentifiableTagConverter(CustomItemRegistry)
        public val CustomBlock: IdentifiableTagConverter<CustomBlockRegistry> =
            IdentifiableTagConverter(CustomBlockRegistry)
        public val CustomTile: IdentifiableTagConverter<CustomTileRegistry> =
            IdentifiableTagConverter(CustomTileRegistry)
        public val CustomTileEntity: IdentifiableTagConverter<CustomTileEntityRegistry> =
            IdentifiableTagConverter(CustomTileEntityRegistry)
    }
}


public inline fun <reified T : Identifiable> IdentifiableTagConverter(registry: IdentifierRegistry<T>): IdentifiableTagConverter<T> =
    IdentifiableTagConverter(registry, T::class)