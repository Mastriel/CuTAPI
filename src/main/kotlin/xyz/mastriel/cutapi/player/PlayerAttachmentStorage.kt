package xyz.mastriel.cutapi.player

import org.bukkit.entity.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*

@get:JvmName("getPlayerAttachments")
public val Player.attachments: List<Attachment>
    get() = getAllAttachments()

public fun Player.hasAttachment(schema: Schema<out Attachment>): Boolean =
    persistedAttachments().any { it.schema().id == schema.id } ||
        IntrinsicPlayerAttachmentProvider.getAll().any { it.schema.id == schema.id }

public fun <T : Attachment> Player.getAttachment(schema: Schema<T>): T =
    getAttachmentOrNull(schema)
        ?: error("Attachment ${schema.id} does not exist on player $uniqueId.")

public fun <T : Attachment> Player.getAttachmentOrNull(schema: Schema<T>): T? =
    getAttachments(schema).firstOrNull()

public fun <T : Attachment> Player.getAttachments(schema: Schema<T>): List<T> {
    val persisted = persistedAttachments().matching(schema)
    if (persisted.isNotEmpty()) return persisted

    val provider = IntrinsicPlayerAttachmentProvider.getOrNull(schema) ?: return emptyList()
    return listOf(provider.create(this))
}

public fun Player.getAllAttachments(): List<Attachment> {
    val persisted = persistedAttachments()
    val persistedSchemaIds = persisted.map { it.schema().id }.toSet()
    val intrinsic = IntrinsicPlayerAttachmentProvider.getAll()
        .filter { it.schema.id !in persistedSchemaIds }
        .map { it.create(this) }
    return intrinsic + persisted
}

/**
 * Stores an attachment as the player's value for its schema.
 *
 * For an intrinsic schema, this value overrides the provider-created value until [removeAttachment]
 * clears the override. Use this operation instead of mutating an attachment returned by a lookup;
 * attachments are not reactive, and mutations to a returned instance are not persisted.
 */
public fun Player.setAttachment(attachment: Attachment) {
    val schema = attachment.schema()
    val next = persistedAttachments()
        .filterNot { it.schema().id == schema.id }
        .toMutableList()
    next += attachment
    AttachmentPdcStorage.write(persistentDataContainer, next)
}

public fun Player.addAttachment(attachment: Attachment) {
    if (!attachment.isRepeatableAttachment()) {
        setAttachment(attachment)
        return
    }

    val schema = attachment.schema()
    val persisted = persistedAttachments()
    val existing = persisted.filter { it.schema().id == schema.id }
    val intrinsic = if (existing.isEmpty()) {
        IntrinsicPlayerAttachmentProvider.getAll()
            .firstOrNull { it.schema.id == schema.id }
            ?.create(this)
            ?.let(::listOf)
            .orEmpty()
    } else {
        emptyList()
    }
    AttachmentPdcStorage.write(persistentDataContainer, persisted + intrinsic + attachment)
}

/**
 * Removes the player's persisted attachment values for [schema].
 *
 * For a normal attachment, the attachment is absent afterward. For an intrinsic attachment,
 * removal resets it: the persisted override is cleared and the next lookup returns a newly
 * created value from its [IntrinsicPlayerAttachmentProvider].
 */
public fun Player.removeAttachment(schema: Schema<out Attachment>) {
    AttachmentPdcStorage.remove(persistentDataContainer, schema.id)
}

public inline fun <reified T : Attachment> Player.hasAttachment(): Boolean =
    hasAttachment(schemaForAttachment<T>())

public inline fun <reified T : Attachment> Player.getAttachment(): T =
    getAttachment(schemaForAttachment<T>())

public inline fun <reified T : Attachment> Player.getAttachmentOrNull(): T? =
    getAttachmentOrNull(schemaForAttachment<T>())

public inline fun <reified T : Attachment> Player.getAttachments(): List<T> =
    getAttachments(schemaForAttachment<T>())

public inline fun <reified T : Attachment> Player.removeAttachment() {
    removeAttachment(schemaForAttachment<T>())
}

private fun Player.persistedAttachments(): List<Attachment> =
    AttachmentPdcStorage.read(persistentDataContainer).attachments
