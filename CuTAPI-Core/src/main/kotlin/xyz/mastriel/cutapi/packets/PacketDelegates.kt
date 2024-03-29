package xyz.mastriel.cutapi.packets

import com.comphenix.protocol.reflect.StructureModifier
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KProperty


internal fun WrappedPacket.intField(fieldIndex: Int) =
    PacketField<Int>(handle.integers, fieldIndex)

internal fun WrappedPacket.byteField(fieldIndex: Int) =
    PacketField<Byte>(handle.bytes, fieldIndex)

internal fun WrappedPacket.byteArrayField(fieldIndex: Int) =
    PacketField<ByteArray>(handle.byteArrays, fieldIndex)

internal fun WrappedPacket.itemField(fieldIndex: Int) =
    PacketField<ItemStack>(handle.itemModifier, fieldIndex)

internal fun WrappedPacket.itemArrayField(fieldIndex: Int) =
    PacketField<Array<ItemStack?>>(handle.itemArrayModifier, fieldIndex)

internal fun WrappedPacket.itemListField(fieldIndex: Int) =
    PacketField<List<ItemStack?>>(handle.itemListModifier, fieldIndex)




class PacketField<T>(val structureModifier: StructureModifier<T>, val index: Int) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return structureModifier.read(index)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        structureModifier.write(index, value)
    }
}