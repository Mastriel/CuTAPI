package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.inventory.*
import org.bukkit.inventory.meta.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.pdc.*
import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.registry.*

public class ItemBehaviorTagContainer(private val itemStack: ItemStack, componentId: Identifier) : TagContainer {

    private val key = componentId

    private val behaviorsContainer = id(Plugin, "behaviors")

    override fun <P : Any, C : Any> set(id: Identifier, complexValue: C?, converter: TagConverter<P, C>) {
        val meta = itemStack.itemMeta
        val container = getDataContainer(meta)

        val namespacedKey = id.toNamespacedKey();
        if (complexValue == null) return container.remove(namespacedKey)

        val primitiveValue = converter.toPrimitive(complexValue)

        container.setPrimitiveValue(converter.primitiveClass, id, primitiveValue)

        setDataContainer(meta, container)
        itemStack.itemMeta = meta
    }

    private fun getDataContainer(meta: ItemMeta): PersistentDataContainer {
        val componentsContainer = getOrCreateContainer(meta.persistentDataContainer, behaviorsContainer)
        return getOrCreateContainer(componentsContainer, key)
    }

    public fun setDataContainer(meta: ItemMeta, container: PersistentDataContainer) {
        val componentsContainer = getOrCreateContainer(meta.persistentDataContainer, behaviorsContainer)
        componentsContainer.set(key.toNamespacedKey(), PersistentDataType.TAG_CONTAINER, container)
        meta.persistentDataContainer.set(
            behaviorsContainer.toNamespacedKey(),
            PersistentDataType.TAG_CONTAINER,
            componentsContainer
        )
    }


    private fun getOrCreateContainer(container: PersistentDataContainer, id: Identifier): PersistentDataContainer {
        val namespacedKey = id.toNamespacedKey()
        if (container.has(namespacedKey)) return container.get(namespacedKey, PersistentDataType.TAG_CONTAINER)!!

        val newContainer = container.adapterContext.newPersistentDataContainer()
        container.set(namespacedKey, PersistentDataType.TAG_CONTAINER, newContainer)
        return container.get(namespacedKey, PersistentDataType.TAG_CONTAINER)!!
    }

    @Suppress("DuplicatedCode")
    override fun <P : Any, C : Any> get(id: Identifier, converter: TagConverter<P, C>): C? {
        val meta = itemStack.itemMeta
        val container = getDataContainer(meta)

        if (isNull(id)) storeNull(id)
        val namespacedKey = id.toNamespacedKey()
        if (!container.has(namespacedKey)) return null

        val value = container.getPrimitiveValue(converter.primitiveClass, id)
        return converter.fromPrimitive(value!!)
    }

    override fun has(id: Identifier): Boolean {
        return getDataContainer(itemStack.itemMeta).has(id.toNamespacedKey())
    }

    override fun isNull(id: Identifier): Boolean {
        val container = getDataContainer(itemStack.itemMeta)
        val namespacedKey = id.toNamespacedKey()
        return PDCTagContainer.checkNull(container, namespacedKey)
    }
}