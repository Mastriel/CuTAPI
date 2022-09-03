package xyz.mastriel.cutapi.nbt.tags

import de.tr7zw.changeme.nbtapi.NBTCompound
import de.tr7zw.changeme.nbtapi.NBTContainer
import kotlinx.serialization.KSerializer
import org.bukkit.OfflinePlayer
import xyz.mastriel.cutapi.items.CustomMaterial
import xyz.mastriel.cutapi.nbt.tags.converters.*
import xyz.mastriel.cutapi.registry.Identifier
import java.util.*
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

open class TagContainer(container: NBTCompound = NBTContainer()) : SimpleTagContainer(container) {

    override var compound: NBTCompound = container
        set(value) {
            field = value
            for (tag in tags) {
                tag.compound = compound
            }
        }

    internal val tags = mutableSetOf<Tag<*>>()

    fun playerTag(key: String, default: OfflinePlayer) =
        NotNullNBTTag(key, compound, default, PlayerTagConverter).addedToTags()

    fun nullablePlayerTag(key: String, default: OfflinePlayer? = null) =
        NullableNBTTag(key, compound, default, PlayerTagConverter).addedToTags()

    fun identifierTag(key: String, default: Identifier) =
        NotNullNBTTag(key, compound, default, IdentifierTagConverter).addedToTags()

    fun nullableIdentifierTag(key: String, default: Identifier? = null) =
        NullableNBTTag(key, compound, default, IdentifierTagConverter).addedToTags()

    fun customMaterialTag(key: String, default: CustomMaterial) =
        NotNullNBTTag(key, this.compound, default, CustomMaterialTagConverter).addedToTags()

    fun nullableCustomMaterialTag(key: String, default: CustomMaterial? = null) =
        NullableNBTTag(key, this.compound, default, CustomMaterialTagConverter).addedToTags()

    fun stringTag(key: String, default: String) =
        NotNullNBTTag(key, this.compound, default, PrimitiveTagConverter.String).addedToTags()

    fun nullableStringTag(key: String, default: String? = null) =
        NullableNBTTag(key, this.compound, default, PrimitiveTagConverter.String).addedToTags()

    fun doubleTag(key: String, default: Double) =
        NotNullNBTTag(key, this.compound, default, PrimitiveTagConverter.Double).addedToTags()

    fun nullableDoubleTag(key: String, default: Double? = null) =
        NullableNBTTag(key, this.compound, default, PrimitiveTagConverter.Double).addedToTags()

    fun longTag(key: String, default: Long) =
        NotNullNBTTag(key, this.compound, default, PrimitiveTagConverter.Long).addedToTags()

    fun nullableLongTag(key: String, default: Long? = null) =
        NullableNBTTag(key, this.compound, default, PrimitiveTagConverter.Long).addedToTags()

    fun intTag(key: String, default: Int) =
        NotNullNBTTag(key, this.compound, default, PrimitiveTagConverter.Int).addedToTags()

    fun nullableIntTag(key: String, default: Int? = null) =
        NullableNBTTag(key, this.compound, default, PrimitiveTagConverter.Int).addedToTags()

    fun booleanTag(key: String, default: Boolean) =
        NotNullNBTTag(key, this.compound, default, PrimitiveTagConverter.Boolean).addedToTags()

    fun nullableBooleanTag(key: String, default: Boolean? = null) =
        NullableNBTTag(key, this.compound, default, PrimitiveTagConverter.Boolean).addedToTags()

    fun uuidTag(key: String, default: UUID) =
        NotNullNBTTag(key, this.compound, default, PrimitiveTagConverter.UUID).addedToTags()

    fun nullableUuidTag(key: String, default: UUID? = null) =
        NullableNBTTag(key, this.compound, default, PrimitiveTagConverter.UUID).addedToTags()

    inline fun <reified T : Enum<T>> enumTag(key: String, default: T) =
        NotNullNBTTag(key, this.compound, default, EnumTagConverter(T::class)).addedToTags()

    inline fun <reified T : Enum<T>> nullableEnumTag(key: String, default: T? = null) =
        NullableNBTTag(key, this.compound, default, EnumTagConverter(T::class)).addedToTags()

    inline fun <reified T : Any> objectTag(key: String, default: T, serializer: KSerializer<T>) =
        NotNullNBTTag(key, this.compound, default, ObjectTagConverter(T::class, serializer)).addedToTags()

    inline fun <reified T : Any> nullableObjectTag(key: String, default: T?, serializer: KSerializer<T>) =
        NullableNBTTag(key, this.compound, default, ObjectTagConverter(T::class, serializer)).addedToTags()

    fun <T : Tag<V>, V> T.addedToTags(): T {
        tags.add(this)
        return this
    }

    @Suppress("UNCHECKED_CAST")
    fun <V> saveTag(tag: KProperty0<V>) {
        tag.isAccessible = true
        val delegate = (tag.getDelegate() as? Tag<V>) ?: error("Tried to save value that is not a tag.")
        delegate.store(tag.get())
        tag.isAccessible = false
    }


}