package xyz.mastriel.cutapi.nbt

import de.tr7zw.changeme.nbtapi.NBTCompound
import de.tr7zw.changeme.nbtapi.NBTContainer
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.registry.descriptors.MaterialDescriptorDsl
import java.util.*

/**
 * A builder for [NBTContainer].
 */
@MaterialDescriptorDsl
class NBTBuilder {
    private val container = NBTContainer()

    fun string(key: String, value: String) { container.setString(key, value) }
    fun int(key: String, value: Int) { container.setInteger(key, value) }
    fun long(key: String, value: Long) { container.setLong(key, value) }
    fun boolean(key: String, value: Boolean) { container.setBoolean(key, value) }
    fun float(key: String, value: Float) { container.setFloat(key, value) }
    fun double(key: String, value: Double) { container.setDouble(key, value) }
    fun intArray(key: String, vararg value: Int) { container.setIntArray(key, value) }
    fun itemStack(key: String, value: ItemStack) { container.setItemStack(key, value) }
    fun byte(key: String, value: Byte) { container.setByte(key, value) }
    fun short(key: String, value: Short) { container.setShort(key, value) }
    fun uuid(key: String, value: UUID) { container.setUUID(key, value) }
    fun any(key: String, value: Any) { container.setObject(key, value) }

    fun compound(name: String, block: NBTBuilder.() -> Unit) {
        val compound = container.addCompound(name)
        compound.mergeCompound(NBTBuilder().apply(block).build())
    }
    fun compound(name: String, compound: NBTCompound) {
        val newCompound = container.addCompound(name)
        newCompound.mergeCompound(compound)
    }


    fun build() = container
}

fun nbtContainer(block: NBTBuilder.() -> Unit) : NBTContainer {
    return NBTBuilder().apply(block).build()
}

fun NBTCompound.edit(block: NBTBuilder.() -> Unit) {
    return this.mergeCompound(nbtContainer(block))
}
