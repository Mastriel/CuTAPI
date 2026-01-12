package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.inventory.*
import xyz.mastriel.cutapi.pdc.*
import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.registry.*

public open class ItemTagContainer(private val itemStack: ItemStack) : TagContainer {


    override fun <P : Any, C : Any> set(id: Identifier, complexValue: C?, converter: TagConverter<P, C>) {
        val meta = itemStack.itemMeta
        val container = meta.persistentDataContainer

        val namespacedKey = id.toNamespacedKey()
        if (complexValue == null) return container.remove(namespacedKey)

        val primitiveValue = converter.toPrimitive(complexValue)

        container.setPrimitiveValue(converter.primitiveClass, id, primitiveValue)

        itemStack.itemMeta = meta
    }

    override fun <P : Any, C : Any> get(id: Identifier, converter: TagConverter<P, C>): C? {
        val meta = itemStack.itemMeta
        val container = meta.persistentDataContainer

        if (isNull(id)) storeNull(id)
        val namespacedKey = id.toNamespacedKey()
        if (!container.has(namespacedKey)) return null

        val value = container.getPrimitiveValue(converter.primitiveClass, id)
        return converter.fromPrimitive(value!!)
    }

    override fun has(id: Identifier): Boolean {
        return itemStack.itemMeta.persistentDataContainer.has(id.toNamespacedKey())
    }

    override fun isNull(id: Identifier): Boolean {
        val container = itemStack.itemMeta.persistentDataContainer
        val namespacedKey = id.toNamespacedKey()
        return PDCTagContainer.checkNull(container, namespacedKey)
    }
}