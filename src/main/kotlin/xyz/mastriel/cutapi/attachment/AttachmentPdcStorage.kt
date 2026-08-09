package xyz.mastriel.cutapi.attachment

import org.bukkit.persistence.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*

private val AttachmentsKey = id("cutapi:attachments").toNamespacedKey()
private val FormatVersionKey = id("cutapi:format_version").toNamespacedKey()
private val SuppressedKey = id("cutapi:suppressed").toNamespacedKey()
private val ValuesKey = id("cutapi:values").toNamespacedKey()
private const val FormatVersion = 1

internal data class PersistentAttachmentState(
    val attachments: List<Attachment>,
    val suppressed: Set<Identifier> = emptySet()
)

internal object AttachmentPdcStorage {
    fun has(container: PersistentDataContainer): Boolean {
        val root = container.get(AttachmentsKey, PersistentDataType.TAG_CONTAINER) ?: return false
        return root.get(FormatVersionKey, PersistentDataType.INTEGER) == FormatVersion
    }

    fun clear(container: PersistentDataContainer) {
        container.remove(AttachmentsKey)
    }

    fun read(container: PersistentDataContainer): PersistentAttachmentState {
        val root = container.get(AttachmentsKey, PersistentDataType.TAG_CONTAINER)
            ?: return PersistentAttachmentState(emptyList())
        if (root.get(FormatVersionKey, PersistentDataType.INTEGER) != FormatVersion) {
            return PersistentAttachmentState(emptyList())
        }

        val attachments = mutableListOf<Attachment>()
        val suppressed = mutableSetOf<Identifier>()
        for (key in root.keys - FormatVersionKey) {
            val schemaId = key.toIdentifier()

            @Suppress("UNCHECKED_CAST")
            val schema = Schema.getOrNull(schemaId) as? Schema<out Attachment> ?: continue
            val schemaContainer = root.get(key, PersistentDataType.TAG_CONTAINER) ?: continue
            if (schemaContainer.get(SuppressedKey, PersistentDataType.BOOLEAN) == true) {
                suppressed += schemaId
            }
            val values = schemaContainer.get(ValuesKey, PersistentDataType.LIST.dataContainers()).orEmpty()
            for (valueContainer in values) {
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

        // [attachments] and [suppressed] describe the complete authoritative state. Rebuilding the
        // root prevents schemas omitted by a remove operation from surviving as stale PDC entries.
        val root = container.adapterContext.newPersistentDataContainer()
        root.set(FormatVersionKey, PersistentDataType.INTEGER, FormatVersion)
        val bySchema = attachments.groupBy { it.schema().id }
        val schemaIds = bySchema.keys + suppressed
        for (schemaId in schemaIds) {
            require(
                schemaId.toNamespacedKey() != FormatVersionKey &&
                    (schemaId.namespace != "cutapi" || !schemaId.key.startsWith("codec/"))
            ) {
                "Attachment schema id $schemaId is reserved by the PDC format"
            }
            val schemaAttachments = bySchema[schemaId].orEmpty()
            if (schemaAttachments.isEmpty() && schemaId !in suppressed) {
                root.remove(schemaId.toNamespacedKey())
                continue
            }

            val schemaContainer = container.adapterContext.newPersistentDataContainer()
            if (schemaId in suppressed) {
                schemaContainer.set(SuppressedKey, PersistentDataType.BOOLEAN, true)
            }
            schemaContainer.set(
                ValuesKey,
                PersistentDataType.LIST.dataContainers(),
                schemaAttachments.map { attachment -> serialize(container.adapterContext, attachment) }
            )
            root.set(schemaId.toNamespacedKey(), PersistentDataType.TAG_CONTAINER, schemaContainer)
        }
        container.set(AttachmentsKey, PersistentDataType.TAG_CONTAINER, root)
    }

    fun remove(container: PersistentDataContainer, schemaId: Identifier) {
        val root = container.get(AttachmentsKey, PersistentDataType.TAG_CONTAINER) ?: return
        if (root.get(FormatVersionKey, PersistentDataType.INTEGER) != FormatVersion) {
            container.remove(AttachmentsKey)
            return
        }
        root.remove(schemaId.toNamespacedKey())
        if ((root.keys - FormatVersionKey).isEmpty()) {
            container.remove(AttachmentsKey)
        } else {
            container.set(AttachmentsKey, PersistentDataType.TAG_CONTAINER, root)
        }
    }

    private fun serialize(
        context: PersistentDataAdapterContext,
        attachment: Attachment
    ): PersistentDataContainer {
        val schema = attachment.schema().requireRegistered()
        return SchemaPdcCodec.encode(context, schema, attachment)
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserialize(
        schema: Schema<out Attachment>,
        container: PersistentDataContainer
    ): Attachment? = try {
        val concreteSchema = schema as Schema<Attachment>
        SchemaPdcCodec.decode(concreteSchema, container)
    } catch (exception: Exception) {
        null
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Attachment> List<Attachment>.matching(schema: Schema<T>): List<T> =
    filter { it.schema().id == schema.id }.map { it as T }
