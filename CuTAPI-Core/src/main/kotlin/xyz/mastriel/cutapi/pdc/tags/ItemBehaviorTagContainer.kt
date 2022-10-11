package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.pdc.getPrimitiveValue
import xyz.mastriel.cutapi.pdc.setPrimitiveValue
import xyz.mastriel.cutapi.pdc.tags.converters.TagConverter
import xyz.mastriel.cutapi.registry.Identifier

class ItemBehaviorTagContainer(private val itemStack: ItemStack, componentId: Identifier) : TagContainer() {

    private val key = componentId.toString().replace(':', '.')

    override fun <P : Any, C : Any> set(key: String, complexValue: C?, converter: TagConverter<P, C>) {
        val meta = itemStack.itemMeta
        val container = getDataContainer(meta)

        val namespacedKey = NamespacedKey(Plugin, key)
        if (complexValue == null) return container.remove(namespacedKey)

        val primitiveValue = converter.toPrimitive(complexValue)

        container.setPrimitiveValue(converter.primitiveClass, key, primitiveValue)

        setDataContainer(meta, container)
        itemStack.itemMeta = meta
    }

    private fun getDataContainer(meta: ItemMeta): PersistentDataContainer {
        val componentsContainer = getOrCreateContainer(meta.persistentDataContainer, "CuTComponents")
        return getOrCreateContainer(componentsContainer, key)
    }

    fun setDataContainer(meta: ItemMeta, container: PersistentDataContainer) {
        val componentsContainer = getOrCreateContainer(meta.persistentDataContainer, "CuTComponents")
        componentsContainer.set(NamespacedKey(Plugin, key), PersistentDataType.TAG_CONTAINER, container)
        meta.persistentDataContainer.set(
            NamespacedKey(Plugin, "CuTComponents"),
            PersistentDataType.TAG_CONTAINER,
            componentsContainer
        )
    }


    private fun getOrCreateContainer(container: PersistentDataContainer, key: String): PersistentDataContainer {
        val namespacedKey = NamespacedKey(Plugin, key)
        if (container.has(namespacedKey)) return container.get(namespacedKey, PersistentDataType.TAG_CONTAINER)!!

        val newContainer = container.adapterContext.newPersistentDataContainer()
        container.set(namespacedKey, PersistentDataType.TAG_CONTAINER, newContainer)
        return container.get(namespacedKey, PersistentDataType.TAG_CONTAINER)!!
    }

    @Suppress("DuplicatedCode")
    override fun <P : Any, C : Any> get(key: String, converter: TagConverter<P, C>): C? {
        val meta = itemStack.itemMeta
        val container = getDataContainer(meta)

        if (isNull(key)) storeNull(key)
        val namespacedKey = NamespacedKey(Plugin, key)
        if (!container.has(namespacedKey)) return null

        val value = container.getPrimitiveValue(converter.primitiveClass, key)
        return converter.fromPrimitive(value!!)
    }

    override fun has(key: String): Boolean {
        return getDataContainer(itemStack.itemMeta).has(NamespacedKey(Plugin, key))
    }

    override fun isNull(key: String): Boolean {
        val container = getDataContainer(itemStack.itemMeta)
        val namespacedKey = NamespacedKey(Plugin, key)
        return PDCTagContainer.checkNull(container, namespacedKey)
    }
}