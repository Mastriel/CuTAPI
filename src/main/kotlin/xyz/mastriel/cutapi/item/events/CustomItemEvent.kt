package xyz.mastriel.cutapi.item.events

import org.bukkit.event.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*


public abstract class CustomItemEvent(public open val item: CuTItemStack) : Event(),
    AttachmentHolder by item {

    public val attachments: List<Attachment> get() = item.getAllAttachments()

    override fun hasAttachment(schema: Schema<out Attachment>): Boolean = item.hasAttachment(schema)
    override fun <T : Attachment> getAttachment(schema: Schema<T>): T = item.getAttachment(schema)
    override fun <T : Attachment> getAttachmentOrNull(schema: Schema<T>): T? = item.getAttachmentOrNull(schema)
    override fun <T : Attachment> getAttachments(schema: Schema<T>): List<T> = item.getAttachments(schema)

    public inline fun <reified T : Attachment> getAttachment(): T = item.getAttachment<T>()
    public inline fun <reified T : Attachment> getAttachmentOrNull(): T? = item.getAttachmentOrNull<T>()
    public inline fun <reified T : Attachment> hasAttachment(): Boolean = item.hasAttachment<T>()


    override fun getHandlers(): HandlerList {
        return HANDLERS
    }

    public companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        public fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }
}
