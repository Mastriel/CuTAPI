package xyz.mastriel.cutapi.packets

import com.comphenix.protocol.reflect.StructureModifier
import kotlin.reflect.KProperty


internal fun WrappedPacket.intField(fieldIndex: Int) =
    PacketField(handle.integers, fieldIndex)

internal fun WrappedPacket.byteField(fieldIndex: Int) =
    PacketField(handle.bytes, fieldIndex)

internal fun WrappedPacket.itemField(fieldIndex: Int) =
    PacketField(handle.itemModifier, fieldIndex)


class PacketField<T>(val structureModifier: StructureModifier<T>, val index: Int) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return structureModifier.read(index)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        structureModifier.write(index, value)
    }
}