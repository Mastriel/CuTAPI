package xyz.mastriel.cutapi.nbt.tags

import de.tr7zw.changeme.nbtapi.NBTCompound
import org.bukkit.OfflinePlayer
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.nbt.tags.notnull.*
import xyz.mastriel.cutapi.nbt.tags.nullable.*
import xyz.mastriel.cutapi.registry.Identifier
import java.util.*

open class TagContainer(container: NBTCompound) {

    var compound: NBTCompound = container
        internal set(value) {
            field = value
            for (tag in tags) {
                tag.compound = compound
            }
        }

    internal val tags = mutableSetOf<NBTTag<*>>()

    open fun playerTag(key: String, default: OfflinePlayer): NotNullTag<OfflinePlayer> =
        NotNullPlayerTag(key, this.compound, default).addedToTags()

    open fun nullablePlayerTag(key: String, default: OfflinePlayer? = null): NullableTag<OfflinePlayer> =
        NullablePlayerTag(key, this.compound, default).addedToTags()

    open fun identifierTag(key: String, default: Identifier): NotNullTag<Identifier> =
        NotNullIdentifierTag(key, this.compound, default).addedToTags()

    open fun nullableIdentifierTag(key: String, default: Identifier? = null): NullableTag<Identifier> =
        NullableIdentifierTag(key, this.compound, default).addedToTags()

    open fun customMaterialTag(key: String, default: CustomMaterial): NotNullTag<CustomMaterial> =
        NotNullCustomMaterialTag(key, this.compound, default).addedToTags()

    open fun nullableCustomMaterialTag(key: String, default: CustomMaterial? = null): NullableTag<CustomMaterial> =
        NullableCustomMaterialTag(key, this.compound, default).addedToTags()

    open fun stringTag(key: String, default: String) =
        NotNullPrimitiveTag(
            key,
            this.compound,
            String::class,
            default,
            NBTCompound::getString,
            NBTCompound::setString
        ).addedToTags()


    open fun nullableStringTag(key: String, default: String? = null) =
        NullablePrimitiveTag(
            key,
            this.compound,
            String::class,
            default,
            NBTCompound::getString,
            NBTCompound::setString
        ).addedToTags()

    open fun doubleTag(key: String, default: Double) =
        NotNullPrimitiveTag(
            key,
            this.compound,
            Double::class,
            default,
            NBTCompound::getDouble,
            NBTCompound::setDouble
        ).addedToTags()

    open fun nullableDoubleTag(key: String, default: Double? = null) =
        NullablePrimitiveTag(
            key,
            this.compound,
            Double::class,
            default,
            NBTCompound::getDouble,
            NBTCompound::setDouble
        ).addedToTags()

    open fun longTag(key: String, default: Long) =
        NotNullPrimitiveTag(
            key,
            this.compound,
            Long::class,
            default,
            NBTCompound::getLong,
            NBTCompound::setLong
        ).addedToTags()

    open fun nullableLongTag(key: String, default: Long? = null) =
        NullablePrimitiveTag(
            key,
            this.compound,
            Long::class,
            default,
            NBTCompound::getLong,
            NBTCompound::setLong
        ).addedToTags()

    open fun intTag(key: String, default: Int) =
        NotNullPrimitiveTag(
            key,
            this.compound,
            Int::class,
            default,
            NBTCompound::getInteger,
            NBTCompound::setInteger
        ).addedToTags()

    open fun nullableIntTag(key: String, default: Int? = null) =
        NullablePrimitiveTag(
            key,
            this.compound,
            Int::class,
            default,
            NBTCompound::getInteger,
            NBTCompound::setInteger
        ).addedToTags()

    open fun uuidTag(key: String, default: UUID) =
        NotNullTag(key, this.compound, UUID::class, default).addedToTags()

    open fun nullableUuidTag(key: String, default: UUID? = null) =
        NullableTag(key, this.compound, UUID::class, default).addedToTags()

    inline fun <reified T : Enum<T>> nullableEnumTag(key: String, default: T? = null) =
        NullableEnumTag(key, this.compound, default, T::class).addedToTags()

    inline fun <reified T : Enum<T>> enumTag(key: String, default: T) =
        NotNullEnumTag(key, this.compound, default, T::class).addedToTags()


    fun <T : NBTTag<V>, V> T.addedToTags(): T {
        tags.add(this)
        return this
    }


}