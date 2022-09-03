package xyz.mastriel.cutapi.nbt.tags

import de.tr7zw.changeme.nbtapi.NBTCompound
import de.tr7zw.changeme.nbtapi.NBTContainer
import kotlinx.serialization.KSerializer
import org.bukkit.OfflinePlayer
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.nbt.tags.converters.*
import xyz.mastriel.cutapi.registry.Identifier
import java.util.*

open class SimpleTagContainer(compound: NBTCompound = NBTContainer()) {

    open var compound: NBTCompound = compound
        internal set

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
        set(key, value, PrimitiveTagConverter.Boolean)
    fun getBoolean(key: String) =
        get(key, PrimitiveTagConverter.Boolean)

    fun setUUID(key: String, value: UUID? = null) =
        set(key, value, PrimitiveTagConverter.UUID)
    fun getUUID(key: String) =
        get(key, PrimitiveTagConverter.UUID)

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

    fun setItemStack(key: String, value: ItemStack? = null) =
        set(key, value, PrimitiveTagConverter.ItemStack)
    fun getItemStack(key: String) =
        get(key, PrimitiveTagConverter.ItemStack)

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



    fun <P: Any, C: Any> set(key: String, complexValue: C?, converter: TagConverter<P, C>) {
        if (complexValue == null) return compound.removeKey(key)

        Tag.setPrimitiveValue(converter.primitiveClass, compound, key, converter.toPrimitive(complexValue))
    }

    fun <P: Any, C: Any> get(key: String, converter: TagConverter<P, C>) : C? {
        if (!compound.hasKey(key)) return null

        val value = Tag.getPrimitiveValue(converter.primitiveClass, compound, key)
        return converter.fromPrimitive(value!!)
    }


}