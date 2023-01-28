package xyz.mastriel.cutapi.pdc.tags

import kotlinx.serialization.KSerializer
import org.bukkit.OfflinePlayer
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.resourcepack.management.ResourceReference
import xyz.mastriel.cutapi.resourcepack.management.ResourceWithMeta
import java.util.*

interface TagContainer {



    fun <P: Any, C: Any> set(key: String, complexValue: C?, converter: TagConverter<P, C>)

    fun <P: Any, C: Any> get(key: String, converter: TagConverter<P, C>) : C?

    fun has(key: String) : Boolean

    fun storeNull(key: String) {
        set(key, Tag.NULL, PrimitiveTagConverter.String)
    }

    fun isNull(key: String): Boolean


}

fun TagContainer.setPlayer(key: String, value: OfflinePlayer? = null) =
    set(key, value, PlayerTagConverter)
fun TagContainer.getPlayer(key: String) =
    get(key, PlayerTagConverter)

fun TagContainer.setString(key: String, value: String? = null) =
    set(key, value, PrimitiveTagConverter.String)
fun TagContainer.getString(key: String) =
    get(key, PrimitiveTagConverter.String)

fun TagContainer.setInt(key: String, value: Int? = null) =
    set(key, value, PrimitiveTagConverter.Int)
fun TagContainer.getInt(key: String) =
    get(key, PrimitiveTagConverter.Int)

fun TagContainer.setLong(key: String, value: Long? = null) =
    set(key, value, PrimitiveTagConverter.Long)
fun TagContainer.getLong(key: String) =
    get(key, PrimitiveTagConverter.Long)

fun TagContainer.setFloat(key: String, value: Float? = null) =
    set(key, value, PrimitiveTagConverter.Float)
fun TagContainer.getFloat(key: String) =
    get(key, PrimitiveTagConverter.Float)

fun TagContainer.setDouble(key: String, value: Double? = null) =
    set(key, value, PrimitiveTagConverter.Double)
fun TagContainer.getDouble(key: String) =
    get(key, PrimitiveTagConverter.Double)

fun TagContainer.setBoolean(key: String, value: Boolean? = null) =
    set(key, value, BooleanTagConverter)
fun TagContainer.getBoolean(key: String) =
    get(key, BooleanTagConverter)

fun TagContainer.setUUID(key: String, value: UUID? = null) =
    set(key, value, UUIDTagConverter)
fun TagContainer.getUUID(key: String) =
    get(key, UUIDTagConverter)

fun TagContainer.setIntArray(key: String, value: IntArray? = null) =
    set(key, value, PrimitiveTagConverter.IntArray)
fun TagContainer.getIntArray(key: String) =
    get(key, PrimitiveTagConverter.IntArray)

fun TagContainer.setByteArray(key: String, value: ByteArray? = null) =
    set(key, value, PrimitiveTagConverter.ByteArray)
fun TagContainer.getByteArray(key: String) =
    get(key, PrimitiveTagConverter.ByteArray)

fun TagContainer.setByte(key: String, value: Byte? = null) =
    set(key, value, PrimitiveTagConverter.Byte)
fun TagContainer.getByte(key: String) =
    get(key, PrimitiveTagConverter.Byte)

fun TagContainer.setShort(key: String, value: Short? = null) =
    set(key, value, PrimitiveTagConverter.Short)
fun TagContainer.getShort(key: String) =
    get(key, PrimitiveTagConverter.Short)

fun TagContainer.setIdentifier(key: String, value: Identifier? = null) =
    set(key, value, IdentifierTagConverter)
fun TagContainer.getIdentifier(key: String) =
    get(key, IdentifierTagConverter)

inline fun <reified T: ResourceWithMeta<*>> TagContainer.setResourceRef(key: String, value: ResourceReference<T>? = null) =
    set(key, value, ResourceRefTagConverter<T>())
inline fun <reified T: ResourceWithMeta<*>> TagContainer.getResourceRef(key: String) =
    get(key, ResourceRefTagConverter<T>())

fun TagContainer.playerTag(key: String, default: OfflinePlayer) =
    NotNullTag(key, this, default, PlayerTagConverter)

fun TagContainer.nullablePlayerTag(key: String, default: OfflinePlayer? = null) =
    NullableTag(key, this, default, PlayerTagConverter)

fun TagContainer.identifierTag(key: String, default: Identifier) =
    NotNullTag(key, this, default, IdentifierTagConverter)

fun TagContainer.nullableIdentifierTag(key: String, default: Identifier? = null) =
    NullableTag(key, this, default, IdentifierTagConverter)

fun TagContainer.customItemTag(key: String, default: CustomItem) =
    NotNullTag(key, this, default, CustomItemTagConverter)

fun TagContainer.nullableCustomItemTag(key: String, default: CustomItem? = null) =
    NullableTag(key, this, default, CustomItemTagConverter)

fun TagContainer.stringTag(key: String, default: String) =
    NotNullTag(key, this, default, PrimitiveTagConverter.String)

fun TagContainer.nullableStringTag(key: String, default: String? = null) =
    NullableTag(key, this, default, PrimitiveTagConverter.String)

fun TagContainer.doubleTag(key: String, default: Double) =
    NotNullTag(key, this, default, PrimitiveTagConverter.Double)

fun TagContainer.nullableDoubleTag(key: String, default: Double? = null) =
    NullableTag(key, this, default, PrimitiveTagConverter.Double)

fun TagContainer.longTag(key: String, default: Long) =
    NotNullTag(key, this, default, PrimitiveTagConverter.Long)

fun TagContainer.nullableLongTag(key: String, default: Long? = null) =
    NullableTag(key, this, default, PrimitiveTagConverter.Long)

fun TagContainer.intTag(key: String, default: Int) =
    NotNullTag(key, this, default, PrimitiveTagConverter.Int)

fun TagContainer.nullableIntTag(key: String, default: Int? = null) =
    NullableTag(key, this, default, PrimitiveTagConverter.Int)

fun TagContainer.booleanTag(key: String, default: Boolean) =
    NotNullTag(key, this, default, BooleanTagConverter)

fun TagContainer.nullableBooleanTag(key: String, default: Boolean? = null) =
    NullableTag(key, this, default, BooleanTagConverter)

fun TagContainer.uuidTag(key: String, default: UUID) =
    NotNullTag(key, this, default, UUIDTagConverter)

fun TagContainer.nullableUuidTag(key: String, default: UUID? = null) =
    NullableTag(key, this, default, UUIDTagConverter)

inline fun <reified T : Enum<T>> TagContainer.enumTag(key: String, default: T) =
    NotNullTag(key, this, default, EnumTagConverter(T::class))

inline fun <reified T : Enum<T>> TagContainer.nullableEnumTag(key: String, default: T? = null) =
    NullableTag(key, this, default, EnumTagConverter(T::class))

inline fun <reified T : Any> TagContainer.objectTag(key: String, default: T, serializer: KSerializer<T>) =
    NotNullTag(key, this, default, ObjectTagConverter(T::class, serializer))

inline fun <reified T : Any> TagContainer.nullableObjectTag(key: String, default: T?, serializer: KSerializer<T>) =
    NullableTag(key, this, default, ObjectTagConverter(T::class, serializer))

inline fun <reified T: ResourceWithMeta<*>> TagContainer.resourceRefTag(key: String, default: ResourceReference<T>) =
    NotNullTag(key, this, default, ResourceRefTagConverter<T>())

inline fun <reified T: ResourceWithMeta<*>> TagContainer.nullableResourceRefTag(
    key: String, default: ResourceReference<T>? = null) =
    NullableTag(key, this, default, ResourceRefTagConverter<T>())

inline fun <reified T: Enum<T>> TagContainer.setEnum(key: String, value: T? = null) =
    set(key, value, EnumTagConverter(T::class))
inline fun <reified T: Enum<T>> TagContainer.getEnum(key: String) =
    get(key, EnumTagConverter(T::class))

inline fun <reified T: Any> TagContainer.setObject(key: String, value: T? = null, serializer: KSerializer<T>) =
    set(key, value, ObjectTagConverter(T::class, serializer))
inline fun <reified T: Any> TagContainer.getObject(key: String, serializer: KSerializer<T>) =
    get(key, ObjectTagConverter(T::class, serializer))

