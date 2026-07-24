package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.inventory.*
import org.bukkit.inventory.meta.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.pdc.*
import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.registry.*

public class ItemAttachmentTagContainer(private val itemStack: ItemStack, attachmentId: Identifier) : TagContainer {
    private val key = attachmentId
    private val attachmentsContainer = id("cutapi:attachment_data")

    override fun <P : Any, C : Any> set(id: Identifier, complexValue: C?, converter: TagConverter<P, C>) {
        val meta = itemStack.itemMeta
        val container = getDataContainer(meta)
        val namespacedKey = id.toNamespacedKey()
        if (complexValue == null) return container.remove(namespacedKey)

        val primitiveValue = converter.toPrimitive(complexValue)
        container.setPrimitiveValue(converter.primitiveClass, id, primitiveValue)
        setDataContainer(meta, container)
        itemStack.itemMeta = meta
    }

    override fun <P : Any, C : Any> get(id: Identifier, converter: TagConverter<P, C>): C? {
        val container = getDataContainer(itemStack.itemMeta)
        if (isNull(id)) storeNull(id)
        if (!container.has(id.toNamespacedKey())) return null

        val value = container.getPrimitiveValue(converter.primitiveClass, id)
        return converter.fromPrimitive(value!!)
    }

    override fun has(id: Identifier): Boolean =
        getDataContainer(itemStack.itemMeta).has(id.toNamespacedKey())

    override fun isNull(id: Identifier): Boolean =
        PDCTagContainer.checkNull(getDataContainer(itemStack.itemMeta), id.toNamespacedKey())

    private fun getDataContainer(meta: ItemMeta): PersistentDataContainer {
        val root = getOrCreateContainer(meta.persistentDataContainer, attachmentsContainer)
        return getOrCreateContainer(root, key)
    }

    private fun setDataContainer(meta: ItemMeta, container: PersistentDataContainer) {
        val root = getOrCreateContainer(meta.persistentDataContainer, attachmentsContainer)
        root.set(key.toNamespacedKey(), PersistentDataType.TAG_CONTAINER, container)
        meta.persistentDataContainer.set(
            attachmentsContainer.toNamespacedKey(),
            PersistentDataType.TAG_CONTAINER,
            root
        )
    }

    private fun getOrCreateContainer(container: PersistentDataContainer, id: Identifier): PersistentDataContainer {
        val namespacedKey = id.toNamespacedKey()
        if (container.has(namespacedKey)) return container.get(namespacedKey, PersistentDataType.TAG_CONTAINER)!!

        val newContainer = container.adapterContext.newPersistentDataContainer()
        container.set(namespacedKey, PersistentDataType.TAG_CONTAINER, newContainer)
        return container.get(namespacedKey, PersistentDataType.TAG_CONTAINER)!!
    }
}
