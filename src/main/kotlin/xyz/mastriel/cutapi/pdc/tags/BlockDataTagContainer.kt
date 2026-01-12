package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.*
import org.bukkit.block.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.pdc.*
import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.registry.*

public open class BlockDataTagContainer(public val block: Block) : TagContainer {

    public val location: Location get() = block.location
    public val chunk: Chunk get() = block.chunk
    public val rootContainer: PersistentDataContainer get() = block.chunk.persistentDataContainer

    override fun <P : Any, C : Any> set(id: Identifier, complexValue: C?, converter: TagConverter<P, C>) {

        if (complexValue == null) {
            val namespacedKey = id.toNamespacedKey()
            getOrCreateBlockData().remove(namespacedKey)
            return
        }

        val primitive = converter.toPrimitive(complexValue)

        getOrCreateBlockData().setPrimitiveValue(converter.primitiveClass, id, primitive)
    }

    override fun <P : Any, C : Any> get(id: Identifier, converter: TagConverter<P, C>): C? {
        val namespacedKey = id.toNamespacedKey()

        val container = getOrCreateBlockData()
        if (isNull(id)) storeNull(id)
        if (!container.has(namespacedKey)) return null

        val value = container.getPrimitiveValue(converter.primitiveClass, id)
        return converter.fromPrimitive(value!!)
    }

    private fun getOrCreateBlockData(): PersistentDataContainer {
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

    override fun has(id: Identifier): Boolean {
        return getOrCreateBlockData().has(id.toNamespacedKey())
    }

    override fun isNull(id: Identifier): Boolean {
        val container = chunk.persistentDataContainer
        val namespacedKey = id.toNamespacedKey()
        return PDCTagContainer.checkNull(container, namespacedKey)
    }

    private fun getBlockKey(): NamespacedKey {
        return NamespacedKey(Plugin, "CuTBlockData/${block.x}/${block.y}/${block.z}")
    }
}