package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.pdc.getPrimitiveValue
import xyz.mastriel.cutapi.pdc.setPrimitiveValue
import xyz.mastriel.cutapi.pdc.tags.converters.TagConverter

open class PDCTagContainer(var container: PersistentDataContainer) : TagContainer() {


    override fun <P: Any, C: Any> set(key: String, complexValue: C?, converter: TagConverter<P, C>) {
        val namespacedKey = NamespacedKey(Plugin, key)
        if (complexValue == null) return container.remove(namespacedKey)

        val primitiveValue = converter.toPrimitive(complexValue)

        container.setPrimitiveValue(converter.primitiveClass, key, primitiveValue)
    }

    override fun <P: Any, C: Any> get(key: String, converter: TagConverter<P, C>) : C? {
        val namespacedKey = NamespacedKey(Plugin, key)
        if (!container.has(namespacedKey)) return null

        val value = container.getPrimitiveValue(converter.primitiveClass, key)
        return converter.fromPrimitive(value!!)
    }

    override fun has(key: String): Boolean {
        return container.has(NamespacedKey(Plugin, key))
    }


    override fun isNull(key: String): Boolean {
        val namespacedKey = NamespacedKey(Plugin, key)
        return checkNull(container, namespacedKey)
    }


    companion object {
        fun checkNull(container: PersistentDataContainer, namespacedKey: NamespacedKey) : Boolean {
            if (container.has(namespacedKey)) {
                try {
                    return container.get(namespacedKey, PersistentDataType.STRING) == Tag.NULL

                // an IllegalArgumentException is thrown if the type isn't String.
                } catch (ex: IllegalArgumentException) {
                    return false
                }
            }
            return false
        }
    }
}