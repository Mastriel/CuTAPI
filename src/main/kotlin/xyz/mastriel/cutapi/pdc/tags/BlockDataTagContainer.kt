package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.*
import org.bukkit.block.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.pdc.*
import xyz.mastriel.cutapi.pdc.tags.converters.*

public open class BlockDataTagContainer(public val block : Block) : TagContainer {

    public val location : Location get() = block.location
    public val chunk : Chunk get() = block.chunk
    public val rootContainer : PersistentDataContainer get() = block.chunk.persistentDataContainer

    override fun <P: Any, C: Any> set(key: String, complexValue: C?, converter: TagConverter<P, C>) {

        if (complexValue == null) {
            val namespacedKey = NamespacedKey(Plugin, key)
            getOrCreateBlockData().remove(namespacedKey)
            return
        }

        val primitive = converter.toPrimitive(complexValue)

        getOrCreateBlockData().setPrimitiveValue(converter.primitiveClass, key, primitive)
    }

    override fun <P: Any, C: Any> get(key: String, converter: TagConverter<P, C>) : C? {
        val namespacedKey = NamespacedKey(Plugin, key)

        val container = getOrCreateBlockData()
        if (isNull(key)) storeNull(key)
        if (!container.has(namespacedKey)) return null

        val value = container.getPrimitiveValue(converter.primitiveClass, key)
        return converter.fromPrimitive(value!!)
    }

    private fun getOrCreateBlockData() : PersistentDataContainer {
        val blockKey = getBlockKey()
        if (!rootContainer.has(blockKey)) {
            val blockDataContainer = rootContainer.adapterContext.newPersistentDataContainer()
            rootContainer.set(blockKey, PersistentDataType.TAG_CONTAINER, blockDataContainer)
            return rootContainer.get(blockKey, PersistentDataType.TAG_CONTAINER)
                ?: error("Block data doesn't exist when it was just created?")
        }
        return rootContainer.get(getBlockKey(), PersistentDataType.TAG_CONTAINER)
            ?: error("Block data doesn't exist when it should?")
    }

    override fun has(key: String): Boolean {
        return getOrCreateBlockData().has(NamespacedKey(Plugin, key))
    }

    override fun isNull(key: String): Boolean {
        val container = chunk.persistentDataContainer
        val namespacedKey = NamespacedKey(Plugin, key)
        return PDCTagContainer.checkNull(container, namespacedKey)
    }

    private fun getBlockKey() : NamespacedKey {
        return NamespacedKey(Plugin, "CuTBlockData/${block.x}/${block.y}/${block.z}")
    }
}