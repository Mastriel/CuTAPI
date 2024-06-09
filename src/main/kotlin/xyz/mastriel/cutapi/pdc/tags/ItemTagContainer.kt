package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.pdc.*
import xyz.mastriel.cutapi.pdc.tags.converters.*

public open class ItemTagContainer(private val itemStack: ItemStack) : TagContainer {


    override fun <P: Any, C: Any> set(key: String, complexValue: C?, converter: TagConverter<P, C>) {
        val meta = itemStack.itemMeta
        val container = meta.persistentDataContainer

        val namespacedKey = NamespacedKey(Plugin, key)
        if (complexValue == null) return container.remove(namespacedKey)

        val primitiveValue = converter.toPrimitive(complexValue)

        container.setPrimitiveValue(converter.primitiveClass, key, primitiveValue)

        itemStack.itemMeta = meta
    }

    override fun <P: Any, C: Any> get(key: String, converter: TagConverter<P, C>) : C? {
        val meta = itemStack.itemMeta
        val container = meta.persistentDataContainer

        if (isNull(key)) storeNull(key)
        val namespacedKey = NamespacedKey(Plugin, key)
        if (!container.has(namespacedKey)) return null

        val value = container.getPrimitiveValue(converter.primitiveClass, key)
        return converter.fromPrimitive(value!!)
    }

    override fun has(key: String): Boolean {
        return itemStack.itemMeta.persistentDataContainer.has(NamespacedKey(Plugin, key))
    }

    override fun isNull(key: String): Boolean {
        val container = itemStack.itemMeta.persistentDataContainer
        val namespacedKey = NamespacedKey(Plugin, key)
        return PDCTagContainer.checkNull(container, namespacedKey)
    }
}