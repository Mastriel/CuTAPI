package xyz.mastriel.cutapi.attachment

import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.utils.*
import kotlin.reflect.*
import kotlin.reflect.full.*

/**
 * Common base for passive data that can be attached to a domain object and read by systems.
 *
 * Attachments are not reactive. Mutating an attachment does not notify systems, refresh its
 * holder, or reliably persist the change. Some holders also return newly deserialized attachment
 * instances, so a mutation may affect only that particular object.
 *
 * User-defined attachments implement [ItemAttachment], [PlayerAttachment], or both rather than
 * implementing this base directly. Attachment implementations are strongly recommended to be
 * immutable. Prefer constructor properties declared with `val`, and replace an attachment through
 * the holder's `setAttachment`, `addAttachment`, or `removeAttachment` APIs when it changes.
 *
 * Attachment schemas are not registered when they are created. Plugins must register their
 * attachment schemas during startup with `Schema.modifyRegistry { register(MyAttachment) }`
 * before registering holders or persisting attachment values.
 */
public sealed interface Attachment

/** Passive data that can be attached to custom item types and item stacks. */
public interface ItemAttachment : Attachment

/** Passive data that can be attached to players. */
public interface PlayerAttachment : Attachment

@Target(AnnotationTarget.CLASS)
@MustBeDocumented
public annotation class RepeatableAttachment

public fun Attachment.isRepeatableAttachment(): Boolean =
    this::class.hasAnnotation<RepeatableAttachment>()

@Suppress("UNCHECKED_CAST")
public fun Attachment.schema(): Schema<Attachment> {
    if (this is Schema<*>) return this as Schema<Attachment>
    val instance = this::class.accessibleCompanionObjectInstance()
    require(instance is Schema<*>) {
        "Attachment ${this::class.qualifiedName} must have a companion object implementing ${Schema::class.qualifiedName}"
    }
    return instance as Schema<Attachment>
}

@Suppress("UNCHECKED_CAST")
public fun <T : Attachment> schemaForAttachment(type: KClass<T>): Schema<T> {
    val objectInstance = type.objectInstance
    if (objectInstance is Schema<*>) return objectInstance as Schema<T>
    val instance = type.accessibleCompanionObjectInstance()
    require(instance is Schema<*>) {
        "Attachment ${type.qualifiedName} must either implement ${Schema::class.qualifiedName} as a Kotlin object or have a companion object implementing it"
    }
    return instance as Schema<T>
}

public inline fun <reified T : Attachment> schemaForAttachment(): Schema<T> =
    schemaForAttachment(T::class)

/**
 * Provides attachment snapshots associated with a domain object.
 *
 * Returned attachments should be treated as read-only values. Mutating one is not an update
 * operation and is not guaranteed to affect subsequent lookups.
 */
public interface AttachmentHolder<A : Attachment> {
    public fun hasAttachment(schema: Schema<out A>): Boolean

    public fun <T : A> getAttachment(schema: Schema<T>): T

    public fun <T : A> getAttachmentOrNull(schema: Schema<T>): T?

    public fun <T : A> getAttachments(schema: Schema<T>): List<T>

    public fun getAllAttachments(): List<A>
}
