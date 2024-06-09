package xyz.mastriel.cutapi.pdc.tags

import kotlinx.serialization.*
import org.bukkit.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.utils.serializers.*
import java.util.*

public interface TagContainer {


    public fun <P : Any, C : Any> set(key: String, complexValue: C?, converter: TagConverter<P, C>)

    public fun <P : Any, C : Any> get(key: String, converter: TagConverter<P, C>): C?

    public fun has(key: String): Boolean

    public fun storeNull(key: String) {
        set(key, Tag.NULL, PrimitiveTagConverter.String)
    }

    public fun isNull(key: String): Boolean


}

public fun TagContainer.setPlayer(key: String, value: OfflinePlayer? = null): Unit =
    set(key, value, PlayerTagConverter)

public fun TagContainer.getPlayer(key: String): OfflinePlayer? =
    get(key, PlayerTagConverter)

public fun TagContainer.setString(key: String, value: String? = null): Unit =
    set(key, value, PrimitiveTagConverter.String)

public fun TagContainer.getString(key: String): String? =
    get(key, PrimitiveTagConverter.String)

public fun TagContainer.setInt(key: String, value: Int? = null): Unit =
    set(key, value, PrimitiveTagConverter.Int)

public fun TagContainer.getInt(key: String): Int? =
    get(key, PrimitiveTagConverter.Int)

public fun TagContainer.setLong(key: String, value: Long? = null): Unit =
    set(key, value, PrimitiveTagConverter.Long)

public fun TagContainer.getLong(key: String): Long? =
    get(key, PrimitiveTagConverter.Long)

public fun TagContainer.setFloat(key: String, value: Float? = null): Unit =
    set(key, value, PrimitiveTagConverter.Float)

public fun TagContainer.getFloat(key: String): Float? =
    get(key, PrimitiveTagConverter.Float)

public fun TagContainer.setDouble(key: String, value: Double? = null): Unit =
    set(key, value, PrimitiveTagConverter.Double)

public fun TagContainer.getDouble(key: String): Double? =
    get(key, PrimitiveTagConverter.Double)

public fun TagContainer.setBoolean(key: String, value: Boolean? = null): Unit =
    set(key, value, BooleanTagConverter)

public fun TagContainer.getBoolean(key: String): Boolean? =
    get(key, BooleanTagConverter)

public fun TagContainer.setUUID(key: String, value: UUID? = null): Unit =
    set(key, value, UUIDTagConverter)

public fun TagContainer.getUUID(key: String): UUID? =
    get(key, UUIDTagConverter)

public fun TagContainer.setIntArray(key: String, value: IntArray? = null): Unit =
    set(key, value, PrimitiveTagConverter.IntArray)

public fun TagContainer.getIntArray(key: String): IntArray? =
    get(key, PrimitiveTagConverter.IntArray)

public fun TagContainer.setByteArray(key: String, value: ByteArray? = null): Unit =
    set(key, value, PrimitiveTagConverter.ByteArray)

public fun TagContainer.getByteArray(key: String): ByteArray? =
    get(key, PrimitiveTagConverter.ByteArray)

public fun TagContainer.setByte(key: String, value: Byte? = null): Unit =
    set(key, value, PrimitiveTagConverter.Byte)

public fun TagContainer.getByte(key: String): Byte? =
    get(key, PrimitiveTagConverter.Byte)

public fun TagContainer.setShort(key: String, value: Short? = null): Unit =
    set(key, value, PrimitiveTagConverter.Short)

public fun TagContainer.getShort(key: String): Short? =
    get(key, PrimitiveTagConverter.Short)

public fun TagContainer.setIdentifier(key: String, value: Identifier? = null): Unit =
    set(key, value, IdentifierTagConverter)

public fun TagContainer.getIdentifier(key: String): Identifier? =
    get(key, IdentifierTagConverter)

public fun TagContainer.setLocation(key: String, value: Location? = null): Unit =
    set(key, value, ObjectTagConverter(Location::class, LocationSerializer))

public fun TagContainer.getLocation(key: String): Location? =
    get(key, ObjectTagConverter(Location::class, LocationSerializer))

public inline fun <reified T : Resource> TagContainer.setResourceRef(key: String, value: ResourceRef<T>? = null): Unit =
    set(key, value, ResourceRefTagConverter<T>())

public inline fun <reified T : Resource> TagContainer.getResourceRef(key: String): ResourceRef<T>? =
    get(key, ResourceRefTagConverter<T>())

public fun TagContainer.playerTag(key: String, default: OfflinePlayer): NotNullTag<String, OfflinePlayer> =
    NotNullTag(key, this, default, PlayerTagConverter)

public fun TagContainer.nullablePlayerTag(key: String, default: OfflinePlayer? = null): NullableTag<String, OfflinePlayer> =
    NullableTag(key, this, default, PlayerTagConverter)

public fun TagContainer.identifierTag(key: String, default: Identifier): NotNullTag<String, Identifier> =
    NotNullTag(key, this, default, IdentifierTagConverter)

public fun TagContainer.nullableIdentifierTag(key: String, default: Identifier? = null): NullableTag<String, Identifier> =
    NullableTag(key, this, default, IdentifierTagConverter)

public fun TagContainer.customItemTag(key: String, default: CustomItem<*>): NotNullTag<String, CustomItem<*>> =
    NotNullTag(key, this, default, IdentifiableTagConverter.CustomItem)

public fun TagContainer.nullableCustomItemTag(
    key: String,
    default: CustomItem<*>? = null
): NullableTag<String, CustomItem<*>> =
    NullableTag(key, this, default, IdentifiableTagConverter.CustomItem)

public fun TagContainer.customBlockTag(key: String, default: CustomBlock<*>): NotNullTag<String, CustomBlock<*>> =
    NotNullTag(key, this, default, IdentifiableTagConverter.CustomBlock)

public fun TagContainer.nullableCustomBlockTag(
    key: String,
    default: CustomBlock<*>? = null
): NullableTag<String, CustomBlock<*>> =
    NullableTag(key, this, default, IdentifiableTagConverter.CustomBlock)

public fun TagContainer.customBlockTag(key: String, default: CustomTile<*>): NotNullTag<String, CustomTile<*>> =
    NotNullTag(key, this, default, IdentifiableTagConverter.CustomTile)

public fun TagContainer.nullableCustomBlockTag(
    key: String,
    default: CustomTile<*>? = null
): NullableTag<String, CustomTile<*>> =
    NullableTag(key, this, default, IdentifiableTagConverter.CustomTile)

public fun TagContainer.customTileEntityTag(
    key: String,
    default: CustomTileEntity<*>
): NotNullTag<String, CustomTileEntity<*>> =
    NotNullTag(key, this, default, IdentifiableTagConverter.CustomTileEntity)

public fun TagContainer.nullableTileEntityTag(
    key: String,
    default: CustomTileEntity<*>? = null
): NullableTag<String, CustomTileEntity<*>> =
    NullableTag(key, this, default, IdentifiableTagConverter.CustomTileEntity)

public fun TagContainer.stringTag(key: String, default: String): NotNullTag<String, String> =
    NotNullTag(key, this, default, PrimitiveTagConverter.String)

public fun TagContainer.nullableStringTag(key: String, default: String? = null): NullableTag<String, String> =
    NullableTag(key, this, default, PrimitiveTagConverter.String)

public fun TagContainer.doubleTag(key: String, default: Double): NotNullTag<Double, Double> =
    NotNullTag(key, this, default, PrimitiveTagConverter.Double)

public fun TagContainer.nullableDoubleTag(key: String, default: Double? = null): NullableTag<Double, Double> =
    NullableTag(key, this, default, PrimitiveTagConverter.Double)

public fun TagContainer.longTag(key: String, default: Long): NotNullTag<Long, Long> =
    NotNullTag(key, this, default, PrimitiveTagConverter.Long)

public fun TagContainer.nullableLongTag(key: String, default: Long? = null): NullableTag<Long, Long> =
    NullableTag(key, this, default, PrimitiveTagConverter.Long)

public fun TagContainer.intTag(key: String, default: Int): NotNullTag<Int, Int> =
    NotNullTag(key, this, default, PrimitiveTagConverter.Int)

public fun TagContainer.nullableIntTag(key: String, default: Int? = null): NullableTag<Int, Int> =
    NullableTag(key, this, default, PrimitiveTagConverter.Int)

public fun TagContainer.booleanTag(key: String, default: Boolean): NotNullTag<Byte, Boolean> =
    NotNullTag(key, this, default, BooleanTagConverter)

public fun TagContainer.nullableBooleanTag(key: String, default: Boolean? = null): NullableTag<Byte, Boolean> =
    NullableTag(key, this, default, BooleanTagConverter)

public fun TagContainer.uuidTag(key: String, default: UUID): NotNullTag<String, UUID> =
    NotNullTag(key, this, default, UUIDTagConverter)

public fun TagContainer.nullableUuidTag(key: String, default: UUID? = null): NullableTag<String, UUID> =
    NullableTag(key, this, default, UUIDTagConverter)

public fun TagContainer.locationTag(key: String, default: Location): NotNullTag<ByteArray, Location> =
    objectTag(key, default, LocationSerializer)

public fun TagContainer.nullableLocationTag(key: String, default: Location? = null): NullableTag<ByteArray, Location> =
    nullableObjectTag(key, default, LocationSerializer)

public inline fun <reified T : Enum<T>> TagContainer.enumTag(key: String, default: T): NotNullTag<String, T> =
    NotNullTag(key, this, default, EnumTagConverter(T::class))

public inline fun <reified T : Enum<T>> TagContainer.nullableEnumTag(key: String, default: T? = null): NullableTag<String, T> =
    NullableTag(key, this, default, EnumTagConverter(T::class))


public inline fun <reified T : Resource> TagContainer.refTag(
    key: String,
    default: ResourceRef<T>
): NotNullTag<String, ResourceRef<T>> =
    NotNullTag(key, this, default, ResourceRefTagConverter())

public inline fun <reified T : Resource> TagContainer.nullableRefTag(
    key: String,
    default: ResourceRef<T>? = null
): NullableTag<String, ResourceRef<T>> =
    NullableTag(key, this, default, ResourceRefTagConverter())

public inline fun <reified T : Any> TagContainer.objectTag(
    key: String,
    default: T,
    serializer: KSerializer<T>
): NotNullTag<ByteArray, T> =
    NotNullTag(key, this, default, ObjectTagConverter(T::class, serializer))

public inline fun <reified T : Any> TagContainer.nullableObjectTag(
    key: String,
    default: T?,
    serializer: KSerializer<T>
): NullableTag<ByteArray, T> =
    NullableTag(key, this, default, ObjectTagConverter(T::class, serializer))

public inline fun <reified T : Enum<T>> TagContainer.setEnum(key: String, value: T? = null): Unit =
    set(key, value, EnumTagConverter(T::class))

public inline fun <reified T : Enum<T>> TagContainer.getEnum(key: String): T? =
    get(key, EnumTagConverter(T::class))

public inline fun <reified T : Any> TagContainer.setObject(key: String, value: T? = null, serializer: KSerializer<T>): Unit =
    set(key, value, ObjectTagConverter(T::class, serializer))

public inline fun <reified T : Any> TagContainer.getObject(key: String, serializer: KSerializer<T>): T? =
    get(key, ObjectTagConverter(T::class, serializer))
