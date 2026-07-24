package xyz.mastriel.cutapi.attachment

import xyz.mastriel.cutapi.data.*
import kotlin.reflect.*
import kotlin.reflect.full.*

/** Passive data that can be attached to a domain object and read by systems. */
public interface Attachment

@Target(AnnotationTarget.CLASS)
@MustBeDocumented
public annotation class RepeatableAttachment

public fun Attachment.isRepeatableAttachment(): Boolean =
    this::class.hasAnnotation<RepeatableAttachment>()

@Suppress("UNCHECKED_CAST")
public fun Attachment.schema(): Schema<out Attachment> {
    if (this is Schema<*>) return this as Schema<out Attachment>
    val instance = this::class.companionObjectInstance
    require(instance is Schema<*>) {
        "Attachment ${this::class.qualifiedName} must have a companion object implementing ${Schema::class.qualifiedName}"
    }
    return instance as Schema<out Attachment>
}

@Suppress("UNCHECKED_CAST")
public fun <T : Attachment> schemaForAttachment(type: KClass<T>): Schema<T> {
    val objectInstance = type.objectInstance
    if (objectInstance is Schema<*>) return objectInstance as Schema<T>
    val instance = type.companionObjectInstance
    require(instance is Schema<*>) {
        "Attachment ${type.qualifiedName} must either implement ${Schema::class.qualifiedName} as a Kotlin object or have a companion object implementing it"
    }
    return instance as Schema<T>
}

public inline fun <reified T : Attachment> schemaForAttachment(): Schema<T> =
    schemaForAttachment(T::class)

public interface AttachmentHolder {
    public fun hasAttachment(schema: Schema<out Attachment>): Boolean

    public fun <T : Attachment> getAttachment(schema: Schema<T>): T

    public fun <T : Attachment> getAttachmentOrNull(schema: Schema<T>): T?

    public fun <T : Attachment> getAttachments(schema: Schema<T>): List<T>

    public fun getAllAttachments(): List<Attachment>
}

public inline fun <reified T : Attachment> AttachmentHolder.hasAttachment(): Boolean =
    hasAttachment(schemaForAttachment<T>())

public inline fun <reified T : Attachment> AttachmentHolder.getAttachment(): T =
    getAttachment(schemaForAttachment<T>())

public inline fun <reified T : Attachment> AttachmentHolder.getAttachmentOrNull(): T? =
    getAttachmentOrNull(schemaForAttachment<T>())

public inline fun <reified T : Attachment> AttachmentHolder.getAttachments(): List<T> =
    getAttachments(schemaForAttachment<T>())
