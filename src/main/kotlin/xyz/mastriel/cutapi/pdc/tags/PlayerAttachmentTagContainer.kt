package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.entity.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.pdc.*
import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.registry.*

public class PlayerAttachmentTagContainer(
    private val player: Player,
    private val attachmentId: Identifier
) : TagContainer {
    private val attachmentDataKey = id("cutapi:attachment_data").toNamespacedKey()

    override fun <P : Any, C : Any> set(id: Identifier, complexValue: C?, converter: TagConverter<P, C>) {
        val container = getDataContainer()
            ?: player.persistentDataContainer.adapterContext.newPersistentDataContainer()
        val namespacedKey = id.toNamespacedKey()
        if (complexValue == null) {
            container.remove(namespacedKey)
        } else {
            container.setPrimitiveValue(converter.primitiveClass, id, converter.toPrimitive(complexValue))
        }
        setDataContainer(container)
    }

    override fun <P : Any, C : Any> get(id: Identifier, converter: TagConverter<P, C>): C? {
        val container = getDataContainer() ?: return null
        if (!container.has(id.toNamespacedKey())) return null
        return converter.fromPrimitive(container.getPrimitiveValue(converter.primitiveClass, id)!!)
    }

    override fun has(id: Identifier): Boolean =
        getDataContainer()?.has(id.toNamespacedKey()) == true

    override fun isNull(id: Identifier): Boolean {
        val container = getDataContainer() ?: return false
        return PDCTagContainer.checkNull(container, id.toNamespacedKey())
    }

    private fun getDataContainer(): PersistentDataContainer? {
        val root = player.persistentDataContainer.get(attachmentDataKey, PersistentDataType.TAG_CONTAINER)
            ?: return null
        return root.get(attachmentId.toNamespacedKey(), PersistentDataType.TAG_CONTAINER)
    }

    private fun setDataContainer(container: PersistentDataContainer) {
        val holder = player.persistentDataContainer
        val root = holder.get(attachmentDataKey, PersistentDataType.TAG_CONTAINER)
            ?: holder.adapterContext.newPersistentDataContainer()
        root.set(attachmentId.toNamespacedKey(), PersistentDataType.TAG_CONTAINER, container)
        holder.set(attachmentDataKey, PersistentDataType.TAG_CONTAINER, root)
    }
}
