package xyz.mastriel.cutapi.item

import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.registry.*

internal class ItemDescriptorAttachmentHolder(private val descriptor: ItemDescriptor) : AttachmentHolder {
    override fun hasAttachment(schema: Schema<out Attachment>): Boolean =
        descriptor.attachments.any { it.schema().id == schema.id }

    override fun <T : Attachment> getAttachment(schema: Schema<T>): T =
        getAttachmentOrNull(schema) ?: error("Attachment ${schema.id} does not exist on this descriptor.")

    override fun <T : Attachment> getAttachmentOrNull(schema: Schema<T>): T? =
        getAttachments(schema).firstOrNull()

    override fun <T : Attachment> getAttachments(schema: Schema<T>): List<T> =
        descriptor.attachments.matching(schema)

    override fun getAllAttachments(): List<Attachment> =
        descriptor.attachments.toList()
}

internal class CustomItemAttachmentHolder(private val item: CustomItem<*>) : AttachmentHolder {
    private val holder = ItemDescriptorAttachmentHolder(item.descriptor)

    override fun hasAttachment(schema: Schema<out Attachment>): Boolean = holder.hasAttachment(schema)

    override fun <T : Attachment> getAttachment(schema: Schema<T>): T = holder.getAttachment(schema)

    override fun <T : Attachment> getAttachmentOrNull(schema: Schema<T>): T? = holder.getAttachmentOrNull(schema)

    override fun <T : Attachment> getAttachments(schema: Schema<T>): List<T> = holder.getAttachments(schema)

    override fun getAllAttachments(): List<Attachment> = holder.getAllAttachments()
}

internal class CuTItemStackAttachmentHolder(private val item: CuTItemStack) : AttachmentHolder {
    override fun hasAttachment(schema: Schema<out Attachment>): Boolean =
        getAllAttachments().any { it.schema().id == schema.id }

    override fun <T : Attachment> getAttachment(schema: Schema<T>): T =
        getAttachmentOrNull(schema) ?: error("Attachment ${schema.id} does not exist on this item.")

    override fun <T : Attachment> getAttachmentOrNull(schema: Schema<T>): T? =
        getAttachments(schema).firstOrNull()

    override fun <T : Attachment> getAttachments(schema: Schema<T>): List<T> =
        getAllAttachments().matching(schema)

    override fun getAllAttachments(): List<Attachment> {
        val overlay = readOverlay(item)
        val overlaySchemaIds = overlay.attachments.map { it.schema().id }.toSet()
        val defaults = item.type.descriptor.attachments.filter { attachment ->
            val schema = attachment.schema()
            schema.id !in overlay.suppressed &&
                (attachment.isRepeatableAttachment() || schema.id !in overlaySchemaIds)
        }
        return defaults + overlay.attachments
    }
}

/**
 * Persists [attachment] as this stack's replacement for its schema.
 *
 * Use this operation instead of mutating an attachment returned by a lookup. Attachments are not
 * reactive, and mutations to a returned instance are not persisted.
 */
public fun CuTItemStack.setAttachment(attachment: Attachment) {
    val schema = attachment.schema()
    val overlay = readOverlay(this)
    val next = overlay.attachments
        .filterNot { it.schema().id == schema.id }
        .toMutableList()
    next += attachment
    val suppressed =
        if (attachment.isRepeatableAttachment()) overlay.suppressed + schema.id else overlay.suppressed - schema.id
    writeOverlay(this, next, suppressed)
}

public fun CuTItemStack.addAttachment(attachment: Attachment) {
    if (!attachment.isRepeatableAttachment()) return setAttachment(attachment)

    val schema = attachment.schema()
    val overlay = readOverlay(this)
    writeOverlay(this, overlay.attachments + attachment, overlay.suppressed - schema.id)
}

public fun CuTItemStack.removeAttachment(schema: Schema<out Attachment>) {
    val overlay = readOverlay(this)
    writeOverlay(
        this,
        overlay.attachments.filterNot { it.schema().id == schema.id },
        overlay.suppressed + schema.id
    )
}

public inline fun <reified T : Attachment> CuTItemStack.removeAttachment() {
    removeAttachment(schemaForAttachment<T>())
}

public inline fun <reified T : Attachment> CuTItemStack.hasAttachment(): Boolean =
    hasAttachment(schemaForAttachment<T>())

public inline fun <reified T : Attachment> CuTItemStack.getAttachment(): T =
    getAttachment(schemaForAttachment<T>())

public inline fun <reified T : Attachment> CuTItemStack.getAttachmentOrNull(): T? =
    getAttachmentOrNull(schemaForAttachment<T>())

public inline fun <reified T : Attachment> CuTItemStack.getAttachments(): List<T> =
    getAttachments(schemaForAttachment<T>())

public inline fun <reified T : Attachment> CustomItem<*>.hasAttachment(): Boolean =
    hasAttachment(schemaForAttachment<T>())

public inline fun <reified T : Attachment> CustomItem<*>.getAttachment(): T =
    getAttachment(schemaForAttachment<T>())

public inline fun <reified T : Attachment> CustomItem<*>.getAttachmentOrNull(): T? =
    getAttachmentOrNull(schemaForAttachment<T>())

public inline fun <reified T : Attachment> CustomItem<*>.getAttachments(): List<T> =
    getAttachments(schemaForAttachment<T>())

private fun readOverlay(item: CuTItemStack): PersistentAttachmentState =
    AttachmentPdcStorage.read(item.handle.itemMeta.persistentDataContainer)

private fun writeOverlay(
    item: CuTItemStack,
    attachments: List<Attachment>,
    suppressed: Set<Identifier>
) {
    val meta = item.handle.itemMeta
    AttachmentPdcStorage.write(meta.persistentDataContainer, attachments, suppressed)
    item.handle.itemMeta = meta
}

public fun org.bukkit.inventory.ItemStack.getAllAttachments(): List<Attachment> =
    wrap()?.getAllAttachments().orEmpty()
