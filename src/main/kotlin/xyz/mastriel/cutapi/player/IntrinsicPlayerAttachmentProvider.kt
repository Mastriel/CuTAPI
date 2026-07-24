package xyz.mastriel.cutapi.player

import org.bukkit.entity.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*

/**
 * Supplies the intrinsic value of an attachment that every player always has.
 *
 * Intrinsic values are not persisted unless explicitly overridden with [Player.setAttachment].
 * Removing an intrinsic attachment clears that override; the next lookup creates a fresh value
 * with [create], so an intrinsic attachment can never be absent from a player.
 */
public interface IntrinsicPlayerAttachmentProvider<T : Attachment> {
    public val schema: Schema<T>

    public fun create(player: Player): T

    public companion object {
        private val providers = mutableListOf<IntrinsicPlayerAttachmentProvider<out Attachment>>()

        /**
         * Returns all registered providers.
         *
         * A provider is registered when its [provideIntrinsicPlayerAttachment] delegate is initialized.
         */
        public fun getAll(): List<IntrinsicPlayerAttachmentProvider<out Attachment>> {
            val snapshot = synchronized(providers) { providers.toList() }
            val duplicateIds = snapshot.groupBy { it.schema.id }
                .filterValues { it.size > 1 }
                .keys
            require(duplicateIds.isEmpty()) {
                "Multiple intrinsic player attachment providers are registered for: ${duplicateIds.joinToString()}"
            }
            return snapshot
        }

        @Suppress("UNCHECKED_CAST")
        public fun <T : Attachment> getOrNull(schema: Schema<T>): IntrinsicPlayerAttachmentProvider<T>? =
            getAll().firstOrNull { it.schema.id == schema.id } as? IntrinsicPlayerAttachmentProvider<T>

        @PublishedApi
        internal fun register(provider: IntrinsicPlayerAttachmentProvider<out Attachment>) {
            synchronized(providers) {
                providers += provider
            }
        }
    }
}

/**
 * Creates and registers an intrinsic player attachment provider for delegation.
 *
 * This is intended to be delegated from the attachment's companion alongside its schema:
 * ```kt
 * companion object :
 *     Schema<PlayerStats> by schema(id("example:player_stats"), { ... }),
 *     IntrinsicPlayerAttachmentProvider<PlayerStats> by
 *         provideIntrinsicPlayerAttachment({ PlayerStats() })
 * ```
 *
 * The attachment schema is resolved lazily after companion initialization.
 */
public inline fun <reified T : Attachment> provideIntrinsicPlayerAttachment(
    noinline create: (Player) -> T
): IntrinsicPlayerAttachmentProvider<T> {
    val attachmentType = T::class
    val factory = create
    val provider = object : IntrinsicPlayerAttachmentProvider<T> {
        override val schema: Schema<T>
            get() = schemaForAttachment(attachmentType)

        override fun create(player: Player): T {
            val attachment = factory(player)
            require(attachment.schema().id == schema.id) {
                "Intrinsic provider for ${schema.id} created attachment ${attachment.schema().id}"
            }
            return attachment
        }
    }
    IntrinsicPlayerAttachmentProvider.register(provider)
    return provider
}
