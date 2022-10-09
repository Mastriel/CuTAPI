package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.pdc.tags.converters.TagConverter

open class BlockTagContainer(val block : Block) : TagContainer() {

    val location : Location get() = block.location
    val chunk : Chunk get() = block.chunk
    val rootContainer : PersistentDataContainer get() = block.chunk.persistentDataContainer

    override fun <P: Any, C: Any> set(key: String, complexValue: C?, converter: TagConverter<P, C>) {

    }

    override fun <P: Any, C: Any> get(key: String, converter: TagConverter<P, C>) : C? {
        val namespacedKey = NamespacedKey(Plugin, key)

        val container = getOrCreateBlockData()
        if (isNull(key)) storeNull(key)
        if (!container.has(namespacedKey)) return null

        val value = Tag.getPrimitiveValue(converter.primitiveClass, container, key)
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
        return NamespacedKey(Plugin, "CuTBlock/${block.x}/${block.y}/${block.z}")
    }
}