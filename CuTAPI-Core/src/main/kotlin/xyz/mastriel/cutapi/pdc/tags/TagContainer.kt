package xyz.mastriel.cutapi.pdc.tags

import kotlinx.serialization.KSerializer
import org.bukkit.OfflinePlayer
import xyz.mastriel.cutapi.items.CustomItem
import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.registry.Identifier
import java.util.*

abstract class TagContainer {

    fun setPlayer(key: String, value: OfflinePlayer? = null) =
        set(key, value, PlayerTagConverter)
    fun getPlayer(key: String) =
        get(key, PlayerTagConverter)

    fun setString(key: String, value: String? = null) =
        set(key, value, PrimitiveTagConverter.String)
    fun getString(key: String) =
        get(key, PrimitiveTagConverter.String)

    fun setInt(key: String, value: Int? = null) =
        set(key, value, PrimitiveTagConverter.Int)
    fun getInt(key: String) =
        get(key, PrimitiveTagConverter.Int)

    fun setLong(key: String, value: Long? = null) =
        set(key, value, PrimitiveTagConverter.Long)
    fun getLong(key: String) =
        get(key, PrimitiveTagConverter.Long)

    fun setFloat(key: String, value: Float? = null) =
        set(key, value, PrimitiveTagConverter.Float)
    fun getFloat(key: String) =
        get(key, PrimitiveTagConverter.Float)

    fun setDouble(key: String, value: Double? = null) =
        set(key, value, PrimitiveTagConverter.Double)
    fun getDouble(key: String) =
        get(key, PrimitiveTagConverter.Double)

    fun setBoolean(key: String, value: Boolean? = null) =
        set(key, value, BooleanTagConverter)
    fun getBoolean(key: String) =
        get(key, BooleanTagConverter)

    fun setUUID(key: String, value: UUID? = null) =
        set(key, value, UUIDTagConverter)
    fun getUUID(key: String) =
        get(key, UUIDTagConverter)

    fun setIntArray(key: String, value: IntArray? = null) =
        set(key, value, PrimitiveTagConverter.IntArray)
    fun getIntArray(key: String) =
        get(key, PrimitiveTagConverter.IntArray)

    fun setByteArray(key: String, value: ByteArray? = null) =
        set(key, value, PrimitiveTagConverter.ByteArray)
    fun getByteArray(key: String) =
        get(key, PrimitiveTagConverter.ByteArray)

    fun setByte(key: String, value: Byte? = null) =
        set(key, value, PrimitiveTagConverter.Byte)
    fun getByte(key: String) =
        get(key, PrimitiveTagConverter.Byte)

    fun setShort(key: String, value: Short? = null) =
        set(key, value, PrimitiveTagConverter.Short)
    fun getShort(key: String) =
        get(key, PrimitiveTagConverter.Short)

    fun setIdentifier(key: String, value: Identifier? = null) =
        set(key, value, IdentifierTagConverter)
    fun getIdentifier(key: String) =
        get(key, IdentifierTagConverter)

    inline fun <reified T: Enum<T>> setEnum(key: String, value: T? = null) =
        set(key, value, EnumTagConverter(T::class))
    inline fun <reified T: Enum<T>> getEnum(key: String) =
        get(key, EnumTagConverter(T::class))

    inline fun <reified T: Any> setObject(key: String, value: T? = null, serializer: KSerializer<T>) =
        set(key, value, ObjectTagConverter(T::class, serializer))
    inline fun <reified T: Any> getObject(key: String, serializer: KSerializer<T>) =
        get(key, ObjectTagConverter(T::class, serializer))

    fun playerTag(key: String, default: OfflinePlayer) =
        NotNullTag(key, this, default, PlayerTagConverter).addedToTags()

    fun nullablePlayerTag(key: String, default: OfflinePlayer? = null) =
        NullableTag(key, this, default, PlayerTagConverter).addedToTags()

    fun identifierTag(key: String, default: Identifier) =
        NotNullTag(key, this, default, IdentifierTagConverter).addedToTags()

    fun nullableIdentifierTag(key: String, default: Identifier? = null) =
        NullableTag(key, this, default, IdentifierTagConverter).addedToTags()

    fun customItemTag(key: String, default: CustomItem) =
        NotNullTag(key, this, default, CustomItemTagConverter).addedToTags()

    fun nullableCustomItemTag(key: String, default: CustomItem? = null) =
        NullableTag(key, this, default, CustomItemTagConverter).addedToTags()

    fun stringTag(key: String, default: String) =
        NotNullTag(key, this, default, PrimitiveTagConverter.String).addedToTags()

    fun nullableStringTag(key: String, default: String? = null) =
        NullableTag(key, this, default, PrimitiveTagConverter.String).addedToTags()

    fun doubleTag(key: String, default: Double) =
        NotNullTag(key, this, default, PrimitiveTagConverter.Double).addedToTags()

    fun nullableDoubleTag(key: String, default: Double? = null) =
        NullableTag(key, this, default, PrimitiveTagConverter.Double).addedToTags()

    fun longTag(key: String, default: Long) =
        NotNullTag(key, this, default, PrimitiveTagConverter.Long).addedToTags()

    fun nullableLongTag(key: String, default: Long? = null) =
        NullableTag(key, this, default, PrimitiveTagConverter.Long).addedToTags()

    fun intTag(key: String, default: Int) =
        NotNullTag(key, this, default, PrimitiveTagConverter.Int).addedToTags()

    fun nullableIntTag(key: String, default: Int? = null) =
        NullableTag(key, this, default, PrimitiveTagConverter.Int).addedToTags()

    fun booleanTag(key: String, default: Boolean) =
        NotNullTag(key, this, default, BooleanTagConverter).addedToTags()

    fun nullableBooleanTag(key: String, default: Boolean? = null) =
        NullableTag(key, this, default, BooleanTagConverter).addedToTags()

    fun uuidTag(key: String, default: UUID) =
        NotNullTag(key, this, default, UUIDTagConverter).addedToTags()

    fun nullableUuidTag(key: String, default: UUID? = null) =
        NullableTag(key, this, default, UUIDTagConverter).addedToTags()

    inline fun <reified T : Enum<T>> enumTag(key: String, default: T) =
        NotNullTag(key, this, default, EnumTagConverter(T::class)).addedToTags()

    inline fun <reified T : Enum<T>> nullableEnumTag(key: String, default: T? = null) =
        NullableTag(key, this, default, EnumTagConverter(T::class)).addedToTags()

    inline fun <reified T : Any> objectTag(key: String, default: T, serializer: KSerializer<T>) =
        NotNullTag(key, this, default, ObjectTagConverter(T::class, serializer)).addedToTags()

    inline fun <reified T : Any> nullableObjectTag(key: String, default: T?, serializer: KSerializer<T>) =
        NullableTag(key, this, default, ObjectTagConverter(T::class, serializer)).addedToTags()

    internal val tags = mutableSetOf<Tag<*>>()

    fun <T : Tag<V>, V> T.addedToTags(): T {
        tags.add(this)
        return this
    }


    abstract fun <P: Any, C: Any> set(key: String, complexValue: C?, converter: TagConverter<P, C>)

    abstract fun <P: Any, C: Any> get(key: String, converter: TagConverter<P, C>) : C?

    abstract fun has(key: String) : Boolean


    internal open fun storeNull(key: String) {
        set(key, Tag.NULL, PrimitiveTagConverter.String)
    }

    internal abstract fun isNull(key: String): Boolean


}