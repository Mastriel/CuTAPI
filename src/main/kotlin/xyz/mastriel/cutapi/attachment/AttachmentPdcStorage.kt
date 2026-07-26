package xyz.mastriel.cutapi.attachment

import org.bukkit.persistence.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*

private val AttachmentsKey = id("cutapi:attachments").toNamespacedKey()
private val SuppressedKey = id("cutapi:suppressed").toNamespacedKey()
private val CountKey = id("cutapi:count").toNamespacedKey()

internal data class PersistentAttachmentState(
    val attachments: List<Attachment>,
    val suppressed: Set<Identifier> = emptySet()
)

internal object AttachmentPdcStorage {
    fun read(container: PersistentDataContainer): PersistentAttachmentState {
        val root = container.get(AttachmentsKey, PersistentDataType.TAG_CONTAINER)
            ?: return PersistentAttachmentState(emptyList())

        val attachments = mutableListOf<Attachment>()
        val suppressed = mutableSetOf<Identifier>()
        for (key in root.keys) {
            val schemaId = key.toIdentifier()
            @Suppress("UNCHECKED_CAST")
            val schema = Schema.getOrNull(schemaId) as? Schema<out Attachment> ?: continue
            val schemaContainer = root.get(key, PersistentDataType.TAG_CONTAINER) ?: continue
            if (schemaContainer.get(SuppressedKey, PersistentDataType.BYTE) == 1.toByte()) {
                suppressed += schemaId
            }
            val count = schemaContainer.get(CountKey, PersistentDataType.INTEGER) ?: 0
            for (index in 0 until count) {
                val valueContainer =
                    schemaContainer.get(entryKey(index), PersistentDataType.TAG_CONTAINER) ?: continue
                deserialize(schema, valueContainer)?.let(attachments::add)
            }
        }
        return PersistentAttachmentState(attachments, suppressed)
    }

    fun write(
        container: PersistentDataContainer,
        attachments: List<Attachment>,
        suppressed: Set<Identifier> = emptySet()
    ) {
        attachments.forEach { it.schema().requireRegistered() }
        suppressed.forEach { schemaId ->
            require(Schema.has(schemaId)) {
                "Cannot suppress unregistered attachment schema $schemaId"
            }
        }

        if (attachments.isEmpty() && suppressed.isEmpty()) {
            container.remove(AttachmentsKey)
            return
        }

        val root = container.get(AttachmentsKey, PersistentDataType.TAG_CONTAINER)
            ?: container.adapterContext.newPersistentDataContainer()
        val bySchema = attachments.groupBy { it.schema().id }
        val schemaIds = bySchema.keys + suppressed
        for (schemaId in schemaIds) {
            val schemaAttachments = bySchema[schemaId].orEmpty()
            if (schemaAttachments.isEmpty() && schemaId !in suppressed) {
                root.remove(schemaId.toNamespacedKey())
                continue
            }

            val schemaContainer = container.adapterContext.newPersistentDataContainer()
            if (schemaId in suppressed) {
                schemaContainer.set(SuppressedKey, PersistentDataType.BYTE, 1)
            }
            schemaContainer.set(CountKey, PersistentDataType.INTEGER, schemaAttachments.size)
            schemaAttachments.forEachIndexed { index, attachment ->
                schemaContainer.set(
                    entryKey(index),
                    PersistentDataType.TAG_CONTAINER,
                    VariantPdcCodec.encode(container.adapterContext, serialize(attachment))
                )
            }
            root.set(schemaId.toNamespacedKey(), PersistentDataType.TAG_CONTAINER, schemaContainer)
        }
        container.set(AttachmentsKey, PersistentDataType.TAG_CONTAINER, root)
    }

    fun remove(container: PersistentDataContainer, schemaId: Identifier) {
        val root = container.get(AttachmentsKey, PersistentDataType.TAG_CONTAINER) ?: return
        root.remove(schemaId.toNamespacedKey())
        if (root.keys.isEmpty()) {
            container.remove(AttachmentsKey)
        } else {
            container.set(AttachmentsKey, PersistentDataType.TAG_CONTAINER, root)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun serialize(attachment: Attachment): Variant {
        val schema = attachment.schema().requireRegistered() as Schema<Attachment>
        return schema.serialize(attachment).getOrThrow()
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserialize(
        schema: Schema<out Attachment>,
        container: PersistentDataContainer
    ): Attachment? = try {
        val concreteSchema = schema as Schema<Attachment>
        concreteSchema.deserialize(VariantPdcCodec.decode(container)).getOrThrow()
    } catch (exception: Exception) {
        null
    }

    private fun entryKey(index: Int) = id("cutapi:entry/$index").toNamespacedKey()
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Attachment> List<Attachment>.matching(schema: Schema<T>): List<T> =
    filter { it.schema().id == schema.id }.map { it as T }
