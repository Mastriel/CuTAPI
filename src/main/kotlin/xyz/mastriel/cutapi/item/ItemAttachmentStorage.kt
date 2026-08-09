package xyz.mastriel.cutapi.item

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.registry.*

internal class ItemDescriptorAttachmentHolder(private val descriptor: ItemDescriptor) :
    AttachmentHolder<ItemAttachment> {
    override fun hasAttachment(schema: Schema<out ItemAttachment>): Boolean =
        descriptor.attachments.any { it.schema().id == schema.id }

    override fun <T : ItemAttachment> getAttachment(schema: Schema<T>): T =
        getAttachmentOrNull(schema) ?: error("Attachment ${schema.id} does not exist on this descriptor.")

    override fun <T : ItemAttachment> getAttachmentOrNull(schema: Schema<T>): T? =
        getAttachments(schema).firstOrNull()

    override fun <T : ItemAttachment> getAttachments(schema: Schema<T>): List<T> =
        descriptor.attachments.matching(schema)

    override fun getAllAttachments(): List<ItemAttachment> =
        descriptor.attachments.toList()
}

internal class CustomItemAttachmentHolder(private val item: CustomItem<*>) :
    AttachmentHolder<ItemAttachment> {
    private val holder: AttachmentHolder<ItemAttachment> get() = item.identity

    override fun hasAttachment(schema: Schema<out ItemAttachment>): Boolean = holder.hasAttachment(schema)

    override fun <T : ItemAttachment> getAttachment(schema: Schema<T>): T = holder.getAttachment(schema)

    override fun <T : ItemAttachment> getAttachmentOrNull(schema: Schema<T>): T? = holder.getAttachmentOrNull(schema)

    override fun <T : ItemAttachment> getAttachments(schema: Schema<T>): List<T> = holder.getAttachments(schema)

    override fun getAllAttachments(): List<ItemAttachment> = holder.getAllAttachments()
}

internal class CuTItemStackAttachmentHolder(private val item: CuTItemStack) :
    AttachmentHolder<ItemAttachment> {
    override fun hasAttachment(schema: Schema<out ItemAttachment>): Boolean =
        getAllAttachments().any { it.schema().id == schema.id }

    override fun <T : ItemAttachment> getAttachment(schema: Schema<T>): T =
        getAttachmentOrNull(schema) ?: error("Attachment ${schema.id} does not exist on this item.")

    override fun <T : ItemAttachment> getAttachmentOrNull(schema: Schema<T>): T? =
        getAttachments(schema).firstOrNull()

    override fun <T : ItemAttachment> getAttachments(schema: Schema<T>): List<T> =
        getAllAttachments().matching(schema)

    override fun getAllAttachments(): List<ItemAttachment> =
        resolveItemAttachments(item).map(ResolvedItemAttachment::attachment)
}

internal fun resolveItemAttachments(item: CuTItemStack): List<ResolvedItemAttachment> {
    if (item.handle.type.isAir || item.handle.amount <= 0) return emptyList()
    val overlay = readOverlay(item)
    val overlaySchemaIds = overlay.attachments.map { it.schema().id }.toSet()
    val defaults = ItemIdentityExtension.resolveWithSources(item.identity).filter { intrinsic ->
        val attachment = intrinsic.attachment
        val schema = attachment.schema()
        schema.id !in overlay.suppressed &&
            (attachment.isRepeatableAttachment() || schema.id !in overlaySchemaIds)
    }.map { intrinsic ->
        ResolvedItemAttachment(intrinsic.attachment, intrinsic.source)
    }
    val stack = overlay.attachments.map { attachment ->
        ResolvedItemAttachment(attachment, ItemAttachmentSource.Stack)
    }
    return defaults + stack
}

/**
 * Persists [attachment] as this stack's replacement for its schema.
 *
 * Use this operation instead of mutating an attachment returned by a lookup. Attachments are not
 * reactive, and mutations to a returned instance are not persisted.
 */
public fun CuTItemStack.setAttachment(attachment: ItemAttachment) {
    mutateAttachments(this) { working -> working.setAttachmentRaw(attachment) }
}

private fun CuTItemStack.setAttachmentRaw(attachment: ItemAttachment) {
    val schema = attachment.schema()
    val overlay = readOverlay(this)
    val next = overlay.attachments
        .filterNot { it.schema().id == schema.id }
        .toMutableList()
    next += attachment
    val isIntrinsic = ItemIdentityExtension.hasIntrinsicAttachment(identity, schema.id)
    val suppressed = if (attachment.isRepeatableAttachment() && isIntrinsic) {
        overlay.suppressed + schema.id
    } else {
        overlay.suppressed - schema.id
    }
    writeOverlay(this, next, suppressed)
}

public fun CuTItemStack.addAttachment(attachment: ItemAttachment) {
    mutateAttachments(this) { working -> working.addAttachmentRaw(attachment) }
}

private fun CuTItemStack.addAttachmentRaw(attachment: ItemAttachment) {
    if (!attachment.isRepeatableAttachment()) return setAttachmentRaw(attachment)

    val schema = attachment.schema()
    val overlay = readOverlay(this)
    writeOverlay(this, overlay.attachments + attachment, overlay.suppressed - schema.id)
}

/**
 * Removes this stack's values for [schema]. If the item identity provides the schema
 * intrinsically, the schema is persisted as suppressed. Otherwise, both its values and any stale
 * suppression marker are removed completely.
 */
public fun CuTItemStack.removeAttachment(schema: Schema<out ItemAttachment>) {
    mutateAttachments(this) { working -> working.removeAttachmentRaw(schema) }
}

private fun CuTItemStack.removeAttachmentRaw(schema: Schema<out ItemAttachment>) {
    schema.requireRegistered()
    val overlay = readOverlay(this)
    val suppressed = if (identity.hasAttachment(schema)) {
        overlay.suppressed + schema.id
    } else {
        overlay.suppressed - schema.id
    }
    writeOverlay(
        this,
        overlay.attachments.filterNot { it.schema().id == schema.id },
        suppressed,
    )
}

private inline fun mutateAttachments(
    item: CuTItemStack,
    mutation: (CuTItemStack) -> Unit,
) {
    require(!item.handle.type.isAir && item.handle.amount > 0) {
        "Attachments cannot be written to an empty item stack."
    }
    requirePrimaryServerThread()
    ItemAttachmentMaterializer.requireActive()

    val working = CuTItemStack.wrap(item.handle.clone())
    mutation(working)
    ItemAttachmentReconciler.reconcile(working.handle)
    if (Bukkit.getServer().javaClass.name.startsWith("org.mockbukkit.")) {
        // MockBukkit does not implement Paper's component-copy API. The mock path has no native
        // materializers, so copying its metadata is the equivalent atomic commit for unit tests.
        item.handle.itemMeta = working.handle.itemMeta
    } else {
        copyItemDataExactly(item.handle, working.handle)
    }
}

public inline fun <reified T : ItemAttachment> CuTItemStack.removeAttachment() {
    removeAttachment(schemaForAttachment<T>())
}

public inline fun <reified T : ItemAttachment> CuTItemStack.hasAttachment(): Boolean =
    hasAttachment(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> CuTItemStack.getAttachment(): T =
    getAttachment(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> CuTItemStack.getAttachmentOrNull(): T? =
    getAttachmentOrNull(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> CuTItemStack.getAttachments(): List<T> =
    getAttachments(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> CustomItem<*>.hasAttachment(): Boolean =
    hasAttachment(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> CustomItem<*>.getAttachment(): T =
    getAttachment(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> CustomItem<*>.getAttachmentOrNull(): T? =
    getAttachmentOrNull(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> CustomItem<*>.getAttachments(): List<T> =
    getAttachments(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> ItemIdentity.hasAttachment(): Boolean =
    hasAttachment(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> ItemIdentity.getAttachment(): T =
    getAttachment(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> ItemIdentity.getAttachmentOrNull(): T? =
    getAttachmentOrNull(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> ItemIdentity.getAttachments(): List<T> =
    getAttachments(schemaForAttachment<T>())

public fun ItemStack.setAttachment(attachment: ItemAttachment) {
    wrap().setAttachment(attachment)
}

public fun ItemStack.addAttachment(attachment: ItemAttachment) {
    wrap().addAttachment(attachment)
}

public fun ItemStack.removeAttachment(schema: Schema<out ItemAttachment>) {
    wrap().removeAttachment(schema)
}

public fun ItemStack.hasAttachment(schema: Schema<out ItemAttachment>): Boolean =
    wrap().hasAttachment(schema)

public fun <T : ItemAttachment> ItemStack.getAttachment(schema: Schema<T>): T =
    wrap().getAttachment(schema)

public fun <T : ItemAttachment> ItemStack.getAttachmentOrNull(schema: Schema<T>): T? =
    wrap().getAttachmentOrNull(schema)

public fun <T : ItemAttachment> ItemStack.getAttachments(schema: Schema<T>): List<T> =
    wrap().getAttachments(schema)

public inline fun <reified T : ItemAttachment> ItemStack.removeAttachment() {
    removeAttachment(schemaForAttachment<T>())
}

public inline fun <reified T : ItemAttachment> ItemStack.hasAttachment(): Boolean =
    hasAttachment(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> ItemStack.getAttachment(): T =
    getAttachment(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> ItemStack.getAttachmentOrNull(): T? =
    getAttachmentOrNull(schemaForAttachment<T>())

public inline fun <reified T : ItemAttachment> ItemStack.getAttachments(): List<T> =
    getAttachments(schemaForAttachment<T>())

internal fun readOverlay(item: CuTItemStack): ItemAttachmentOverlay {
    if (item.handle.type.isAir || item.handle.amount <= 0) return ItemAttachmentOverlay.Empty
    val state = AttachmentPdcStorage.read(item.handle.itemMeta.persistentDataContainer)
    return ItemAttachmentOverlay(
        attachments = state.attachments.filterIsInstance<ItemAttachment>(),
        suppressed = state.suppressed
    )
}

internal fun writeOverlay(
    item: CuTItemStack,
    attachments: List<ItemAttachment>,
    suppressed: Set<Identifier>
) {
    require(!item.handle.type.isAir && item.handle.amount > 0) {
        "Attachments cannot be written to an empty item stack."
    }
    val meta = item.handle.itemMeta
    AttachmentPdcStorage.write(meta.persistentDataContainer, attachments, suppressed)
    item.handle.itemMeta = meta
}

public fun ItemStack.getAllAttachments(): List<ItemAttachment> =
    wrap().getAllAttachments()

internal fun ItemStack.hasStoredItemAttachments(): Boolean =
    !type.isAir && amount > 0 && AttachmentPdcStorage.has(itemMeta.persistentDataContainer)

internal fun ItemStack.clearStoredItemAttachments() {
    if (type.isAir || amount <= 0) return
    val meta = itemMeta
    AttachmentPdcStorage.clear(meta.persistentDataContainer)
    itemMeta = meta
}

internal data class ItemAttachmentOverlay(
    val attachments: List<ItemAttachment>,
    val suppressed: Set<Identifier>
) {
    companion object {
        val Empty: ItemAttachmentOverlay = ItemAttachmentOverlay(emptyList(), emptySet())
    }
}
