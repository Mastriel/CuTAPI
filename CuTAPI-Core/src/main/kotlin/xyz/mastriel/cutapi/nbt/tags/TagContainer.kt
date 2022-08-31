package xyz.mastriel.cutapi.nbt.tags

import de.tr7zw.changeme.nbtapi.NBTCompound
import de.tr7zw.changeme.nbtapi.NBTContainer
import kotlinx.serialization.KSerializer
import org.bukkit.OfflinePlayer
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.nbt.tags.notnull.*
import xyz.mastriel.cutapi.nbt.tags.nullable.*
import xyz.mastriel.cutapi.registry.Identifier
import java.util.*
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

open class TagContainer(container: NBTCompound = NBTContainer()) {

    var compound: NBTCompound = container
        internal set(value) {
            field = value
            for (tag in tags) {
                tag.compound = compound
            }
        }

    internal val tags = mutableSetOf<NBTTag<*>>()

    fun playerTag(key: String, default: OfflinePlayer): NotNullTag<OfflinePlayer> =
        NotNullPlayerTag(key, this.compound, default).addedToTags()

    fun nullablePlayerTag(key: String, default: OfflinePlayer? = null): NullableTag<OfflinePlayer> =
        NullablePlayerTag(key, this.compound, default).addedToTags()

    fun identifierTag(key: String, default: Identifier): NotNullTag<Identifier> =
        NotNullIdentifierTag(key, this.compound, default).addedToTags()

    fun nullableIdentifierTag(key: String, default: Identifier? = null): NullableTag<Identifier> =
        NullableIdentifierTag(key, this.compound, default).addedToTags()

    fun customMaterialTag(key: String, default: CustomMaterial): NotNullTag<CustomMaterial> =
        NotNullCustomMaterialTag(key, this.compound, default).addedToTags()

    fun nullableCustomMaterialTag(key: String, default: CustomMaterial? = null): NullableTag<CustomMaterial> =
        NullableCustomMaterialTag(key, this.compound, default).addedToTags()

    fun stringTag(key: String, default: String) =
        NotNullPrimitiveTag(
            key,
            this.compound,
            String::class,
            default,
            NBTCompound::getString,
            NBTCompound::setString
        ).addedToTags()


    fun nullableStringTag(key: String, default: String? = null) =
        NullablePrimitiveTag(
            key,
            this.compound,
            String::class,
            default,
            NBTCompound::getString,
            NBTCompound::setString
        ).addedToTags()

    fun doubleTag(key: String, default: Double) =
        NotNullPrimitiveTag(
            key,
            this.compound,
            Double::class,
            default,
            NBTCompound::getDouble,
            NBTCompound::setDouble
        ).addedToTags()

    fun nullableDoubleTag(key: String, default: Double? = null) =
        NullablePrimitiveTag(
            key,
            this.compound,
            Double::class,
            default,
            NBTCompound::getDouble,
            NBTCompound::setDouble
        ).addedToTags()

    fun longTag(key: String, default: Long) =
        NotNullPrimitiveTag(
            key,
            this.compound,
            Long::class,
            default,
            NBTCompound::getLong,
            NBTCompound::setLong
        ).addedToTags()

    fun nullableLongTag(key: String, default: Long? = null) =
        NullablePrimitiveTag(
            key,
            this.compound,
            Long::class,
            default,
            NBTCompound::getLong,
            NBTCompound::setLong
        ).addedToTags()

    fun intTag(key: String, default: Int) =
        NotNullPrimitiveTag(
            key,
            this.compound,
            Int::class,
            default,
            NBTCompound::getInteger,
            NBTCompound::setInteger
        ).addedToTags()

    fun nullableIntTag(key: String, default: Int? = null) =
        NullablePrimitiveTag(
            key,
            this.compound,
            Int::class,
            default,
            NBTCompound::getInteger,
            NBTCompound::setInteger
        ).addedToTags()

    fun uuidTag(key: String, default: UUID) =
        NotNullTag(key, this.compound, UUID::class, default).addedToTags()

    fun nullableUuidTag(key: String, default: UUID? = null) =
        NullableTag(key, this.compound, UUID::class, default).addedToTags()

    inline fun <reified T : Enum<T>> enumTag(key: String, default: T) : NotNullTag<T> =
        NotNullEnumTag(key, this.compound, T::class, default).addedToTags()

    inline fun <reified T : Enum<T>> nullableEnumTag(key: String, default: T? = null) : NullableTag<T> =
        NullableEnumTag(key, this.compound, T::class, default).addedToTags()

    inline fun <reified T : Any> objectTag(key: String, default: T, serializer: KSerializer<T>) =
        NotNullObjectTag(key, this.compound, T::class, default, serializer).addedToTags()

    inline fun <reified T : Any> objectTag(key: String, default: T?, serializer: KSerializer<T>) =
        NullableObjectTag(key, this.compound, T::class, default, serializer).addedToTags()

    fun <T : NBTTag<V>, V> T.addedToTags(): T {
        tags.add(this)
        return this
    }

    @Suppress("UNCHECKED_CAST")
    fun <V> saveTag(tag: KProperty0<V>) {
        tag.isAccessible = true
        val delegate = (tag.getDelegate() as? NBTTag<V>) ?: error("Tried to save value that is not a tag.")
        delegate.store(tag.get())
        tag.isAccessible = false
    }


}