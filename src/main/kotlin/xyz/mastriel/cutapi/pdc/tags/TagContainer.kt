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


    public fun <P : Any, C : Any> set(id: Identifier, complexValue: C?, converter: TagConverter<P, C>)

    public fun <P : Any, C : Any> get(id: Identifier, converter: TagConverter<P, C>): C?

    public fun has(id: Identifier): Boolean

    public fun storeNull(id: Identifier) {
        set(id, Tag.NULL, PrimitiveTagConverter.String)
    }

    public fun isNull(id: Identifier): Boolean
}

public fun TagContainer.setPlayer(key: Identifier, value: OfflinePlayer? = null): Unit =
    set(key, value, PlayerTagConverter)

public fun TagContainer.getPlayer(key: Identifier): OfflinePlayer? =
    get(key, PlayerTagConverter)

public fun TagContainer.setString(key: Identifier, value: String? = null): Unit =
    set(key, value, PrimitiveTagConverter.String)

public fun TagContainer.getString(key: Identifier): String? =
    get(key, PrimitiveTagConverter.String)

public fun TagContainer.setInt(key: Identifier, value: Int? = null): Unit =
    set(key, value, PrimitiveTagConverter.Int)

public fun TagContainer.getInt(key: Identifier): Int? =
    get(key, PrimitiveTagConverter.Int)

public fun TagContainer.setLong(key: Identifier, value: Long? = null): Unit =
    set(key, value, PrimitiveTagConverter.Long)

public fun TagContainer.getLong(key: Identifier): Long? =
    get(key, PrimitiveTagConverter.Long)

public fun TagContainer.setFloat(key: Identifier, value: Float? = null): Unit =
    set(key, value, PrimitiveTagConverter.Float)

public fun TagContainer.getFloat(key: Identifier): Float? =
    get(key, PrimitiveTagConverter.Float)

public fun TagContainer.setDouble(key: Identifier, value: Double? = null): Unit =
    set(key, value, PrimitiveTagConverter.Double)

public fun TagContainer.getDouble(key: Identifier): Double? =
    get(key, PrimitiveTagConverter.Double)

public fun TagContainer.setBoolean(key: Identifier, value: Boolean? = null): Unit =
    set(key, value, BooleanTagConverter)

public fun TagContainer.getBoolean(key: Identifier): Boolean? =
    get(key, BooleanTagConverter)

public fun TagContainer.setUUID(key: Identifier, value: UUID? = null): Unit =
    set(key, value, UUIDTagConverter)

public fun TagContainer.getUUID(key: Identifier): UUID? =
    get(key, UUIDTagConverter)

public fun TagContainer.setIntArray(key: Identifier, value: IntArray? = null): Unit =
    set(key, value, PrimitiveTagConverter.IntArray)

public fun TagContainer.getIntArray(key: Identifier): IntArray? =
    get(key, PrimitiveTagConverter.IntArray)

public fun TagContainer.setByteArray(key: Identifier, value: ByteArray? = null): Unit =
    set(key, value, PrimitiveTagConverter.ByteArray)

public fun TagContainer.getByteArray(key: Identifier): ByteArray? =
    get(key, PrimitiveTagConverter.ByteArray)

public fun TagContainer.setByte(key: Identifier, value: Byte? = null): Unit =
    set(key, value, PrimitiveTagConverter.Byte)

public fun TagContainer.getByte(key: Identifier): Byte? =
    get(key, PrimitiveTagConverter.Byte)

public fun TagContainer.setShort(key: Identifier, value: Short? = null): Unit =
    set(key, value, PrimitiveTagConverter.Short)

public fun TagContainer.getShort(key: Identifier): Short? =
    get(key, PrimitiveTagConverter.Short)

public fun TagContainer.setIdentifier(key: Identifier, value: Identifier? = null): Unit =
    set(key, value, IdentifierTagConverter)

public fun TagContainer.getIdentifier(key: Identifier): Identifier? =
    get(key, IdentifierTagConverter)

public fun TagContainer.setLocation(key: Identifier, value: Location? = null): Unit =
    set(key, value, ObjectTagConverter(Location::class, LocationSerializer))

public fun TagContainer.getLocation(key: Identifier): Location? =
    get(key, ObjectTagConverter(Location::class, LocationSerializer))

public inline fun <reified T : Resource> TagContainer.setResourceRef(
    key: Identifier,
    value: ResourceRef<T>? = null
): Unit =
    set(key, value, ResourceRefTagConverter<T>())

public inline fun <reified T : Resource> TagContainer.getResourceRef(key: Identifier): ResourceRef<T>? =
    get(key, ResourceRefTagConverter<T>())

public fun TagContainer.playerTag(key: Identifier, default: OfflinePlayer): NotNullTag<String, OfflinePlayer> =
    NotNullTag(key, this, default, PlayerTagConverter)

public fun TagContainer.nullablePlayerTag(
    key: Identifier,
    default: OfflinePlayer? = null
): NullableTag<String, OfflinePlayer> =
    NullableTag(key, this, default, PlayerTagConverter)

public fun TagContainer.identifierTag(key: Identifier, default: Identifier): NotNullTag<String, Identifier> =
    NotNullTag(key, this, default, IdentifierTagConverter)

public fun TagContainer.nullableIdentifierTag(
    key: Identifier,
    default: Identifier? = null
): NullableTag<String, Identifier> =
    NullableTag(key, this, default, IdentifierTagConverter)

public fun TagContainer.customItemTag(key: Identifier, default: CustomItem<*>): NotNullTag<String, CustomItem<*>> =
    NotNullTag(key, this, default, IdentifiableTagConverter.CustomItem)

public fun TagContainer.nullableCustomItemTag(
    key: Identifier,
    default: CustomItem<*>? = null
): NullableTag<String, CustomItem<*>> =
    NullableTag(key, this, default, IdentifiableTagConverter.CustomItem)

public fun TagContainer.customBlockTag(key: Identifier, default: CustomBlock<*>): NotNullTag<String, CustomBlock<*>> =
    NotNullTag(key, this, default, IdentifiableTagConverter.CustomBlock)

public fun TagContainer.nullableCustomBlockTag(
    key: Identifier,
    default: CustomBlock<*>? = null
): NullableTag<String, CustomBlock<*>> =
    NullableTag(key, this, default, IdentifiableTagConverter.CustomBlock)

public fun TagContainer.customBlockTag(key: Identifier, default: CustomTile<*>): NotNullTag<String, CustomTile<*>> =
    NotNullTag(key, this, default, IdentifiableTagConverter.CustomTile)

public fun TagContainer.nullableCustomBlockTag(
    key: Identifier,
    default: CustomTile<*>? = null
): NullableTag<String, CustomTile<*>> =
    NullableTag(key, this, default, IdentifiableTagConverter.CustomTile)

public fun TagContainer.customTileEntityTag(
    key: Identifier,
    default: CustomTileEntity<*>
): NotNullTag<String, CustomTileEntity<*>> =
    NotNullTag(key, this, default, IdentifiableTagConverter.CustomTileEntity)

public fun TagContainer.nullableTileEntityTag(
    key: Identifier,
    default: CustomTileEntity<*>? = null
): NullableTag<String, CustomTileEntity<*>> =
    NullableTag(key, this, default, IdentifiableTagConverter.CustomTileEntity)

public fun TagContainer.stringTag(key: Identifier, default: String): NotNullTag<String, String> =
    NotNullTag(key, this, default, PrimitiveTagConverter.String)

public fun TagContainer.nullableStringTag(key: Identifier, default: String? = null): NullableTag<String, String> =
    NullableTag(key, this, default, PrimitiveTagConverter.String)

public fun TagContainer.doubleTag(key: Identifier, default: Double): NotNullTag<Double, Double> =
    NotNullTag(key, this, default, PrimitiveTagConverter.Double)

public fun TagContainer.nullableDoubleTag(key: Identifier, default: Double? = null): NullableTag<Double, Double> =
    NullableTag(key, this, default, PrimitiveTagConverter.Double)

public fun TagContainer.longTag(key: Identifier, default: Long): NotNullTag<Long, Long> =
    NotNullTag(key, this, default, PrimitiveTagConverter.Long)

public fun TagContainer.nullableLongTag(key: Identifier, default: Long? = null): NullableTag<Long, Long> =
    NullableTag(key, this, default, PrimitiveTagConverter.Long)

public fun TagContainer.intTag(key: Identifier, default: Int): NotNullTag<Int, Int> =
    NotNullTag(key, this, default, PrimitiveTagConverter.Int)

public fun TagContainer.nullableIntTag(key: Identifier, default: Int? = null): NullableTag<Int, Int> =
    NullableTag(key, this, default, PrimitiveTagConverter.Int)

public fun TagContainer.booleanTag(key: Identifier, default: Boolean): NotNullTag<Byte, Boolean> =
    NotNullTag(key, this, default, BooleanTagConverter)

public fun TagContainer.nullableBooleanTag(key: Identifier, default: Boolean? = null): NullableTag<Byte, Boolean> =
    NullableTag(key, this, default, BooleanTagConverter)

public fun TagContainer.uuidTag(key: Identifier, default: UUID): NotNullTag<String, UUID> =
    NotNullTag(key, this, default, UUIDTagConverter)

public fun TagContainer.nullableUuidTag(key: Identifier, default: UUID? = null): NullableTag<String, UUID> =
    NullableTag(key, this, default, UUIDTagConverter)

public fun TagContainer.locationTag(key: Identifier, default: Location): NotNullTag<ByteArray, Location> =
    objectTag(key, default, LocationSerializer)

public fun TagContainer.nullableLocationTag(
    key: Identifier,
    default: Location? = null
): NullableTag<ByteArray, Location> =
    nullableObjectTag(key, default, LocationSerializer)

public inline fun <reified T : Enum<T>> TagContainer.enumTag(key: Identifier, default: T): NotNullTag<String, T> =
    NotNullTag(key, this, default, EnumTagConverter(T::class))

public inline fun <reified T : Enum<T>> TagContainer.nullableEnumTag(
    key: Identifier,
    default: T? = null
): NullableTag<String, T> =
    NullableTag(key, this, default, EnumTagConverter(T::class))


public inline fun <reified T : Resource> TagContainer.refTag(
    key: Identifier,
    default: ResourceRef<T>
): NotNullTag<String, ResourceRef<T>> =
    NotNullTag(key, this, default, ResourceRefTagConverter())

public inline fun <reified T : Resource> TagContainer.nullableRefTag(
    key: Identifier,
    default: ResourceRef<T>? = null
): NullableTag<String, ResourceRef<T>> =
    NullableTag(key, this, default, ResourceRefTagConverter())

public inline fun <reified T : Any> TagContainer.objectTag(
    key: Identifier,
    default: T,
    serializer: KSerializer<T>
): NotNullTag<ByteArray, T> =
    NotNullTag(key, this, default, ObjectTagConverter(T::class, serializer))

public inline fun <reified T : Any> TagContainer.nullableObjectTag(
    key: Identifier,
    default: T?,
    serializer: KSerializer<T>
): NullableTag<ByteArray, T> =
    NullableTag(key, this, default, ObjectTagConverter(T::class, serializer))

public inline fun <reified T : Enum<T>> TagContainer.setEnum(key: Identifier, value: T? = null): Unit =
    set(key, value, EnumTagConverter(T::class))

public inline fun <reified T : Enum<T>> TagContainer.getEnum(key: Identifier): T? =
    get(key, EnumTagConverter(T::class))

public inline fun <reified T : Any> TagContainer.setObject(
    key: Identifier,
    value: T? = null,
    serializer: KSerializer<T>
): Unit =
    set(key, value, ObjectTagConverter(T::class, serializer))

public inline fun <reified T : Any> TagContainer.getObject(key: Identifier, serializer: KSerializer<T>): T? =
    get(key, ObjectTagConverter(T::class, serializer))
