package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.*
import org.bukkit.entity.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*


@DslMarker
public annotation class ItemDescriptorDsl

/**
 * A class describing a CustomMaterial. Can be extended to provide additional info
 * to specific Bukkit Material types (such as armors having durability fields)
 *
 * @see ItemDescriptorBuilder
 */
public class ItemDescriptor internal constructor(
    public val display: (ItemDisplayBuilder.() -> Unit)? = null,
    public val attachments: List<Attachment> = mutableListOf(),
    public val onRegister: EventHandlerList<ItemRegisterEvent> = EventHandlerList()
) : AttachmentHolder {

    override fun hasAttachment(schema: xyz.mastriel.cutapi.data.Schema<out Attachment>): Boolean =
        attachments.any { it.schema().id == schema.id }

    override fun <T : Attachment> getAttachment(schema: xyz.mastriel.cutapi.data.Schema<T>): T =
        getAttachmentOrNull(schema) ?: error("Attachment ${schema.id} does not exist on this descriptor.")

    override fun <T : Attachment> getAttachmentOrNull(schema: xyz.mastriel.cutapi.data.Schema<T>): T? =
        getAttachments(schema).firstOrNull()

    override fun <T : Attachment> getAttachments(schema: xyz.mastriel.cutapi.data.Schema<T>): List<T> =
        attachments.filter { it.schema().id == schema.id }.map {
            @Suppress("UNCHECKED_CAST")
            it as T
        }

    override fun getAllAttachments(): List<Attachment> = attachments.toList()

    public infix fun with(block: ItemDescriptorBuilder.() -> Unit): ItemDescriptor {
        val other = ItemDescriptorBuilder().apply(block).build()
        return this + other
    }

    public operator fun plus(other: ItemDescriptor): ItemDescriptor {
        return itemDescriptor {
            display = other.display ?: this@ItemDescriptor.display

            val attachments = this@ItemDescriptor.attachments.toMutableList()

            for (otherAttachment in other.attachments) {
                val schema = otherAttachment.schema()
                val attachmentCollision = attachments.any { it.schema().id == schema.id }
                if (attachmentCollision && !otherAttachment.isRepeatableAttachment()) {
                    attachments.removeIf { it.schema().id == schema.id }
                }
                attach(otherAttachment)
            }
            attach(attachments)
        }
    }

}

public data class ItemRegisterEvent(val item: CustomItem<*>)

/**
 * A builder for the [ItemDescriptor], containing some useful functions to make creating
 * resources much easier.
 *
 * @see ItemDescriptor
 */
@ItemDescriptorDsl
public class ItemDescriptorBuilder {
    public var display: (ItemDisplayBuilder.() -> Unit)? = {
        emptyLine()
        attachmentLore(Color.Blue)
    }

    private val _attachments = mutableListOf<Attachment>()
    public val attachments: List<Attachment> get() = _attachments

    public val onRegister: EventHandlerList<ItemRegisterEvent> = EventHandlerList()

    public fun attach(vararg attachments: Attachment) {
        for (attachment in attachments) {
            val schema = attachment.schema()
            if (this._attachments.any { it.schema().id == schema.id } && !attachment.isRepeatableAttachment())
                error("${schema.id} lacks a RepeatableAttachment annotation to be repeatable.")
            _attachments.add(attachment)
        }
    }

    public fun attach(attachments: Collection<Attachment>) {
        attach(*attachments.toTypedArray())
    }

    public fun build(): ItemDescriptor {

        return ItemDescriptor(
            display = display,
            attachments = _attachments,
            onRegister = onRegister
        )
    }

    public fun display(block: ItemDisplayBuilder.() -> Unit) {
        display = block
    }


    public fun noDisplay() {
        display = {}
    }
}

public sealed class ItemTexture {

    public abstract fun isAvailable(): Boolean
    public abstract fun getRef(): ResourceRef<*>
    public abstract fun getItemModelId(): Identifier
    public abstract val showSwapAnimation: Boolean

    public data class Texture(val texture: ResourceRef<Texture2D>, override val showSwapAnimation: Boolean) :
        ItemTexture() {

        override fun getItemModelId(): Identifier {
            val id = texture.getResource()?.getItemModel()?.toIdentifier() ?: unknownID()
            val swapMode = if (showSwapAnimation) "__swap" else "__noswap"
            return id("${id}${swapMode}")
        }

        override fun isAvailable(): Boolean = texture.isAvailable()

        override fun getRef(): ResourceRef<*> {
            return texture
        }
    }

    public data class Model(val model: ResourceRef<Model3D>, override val showSwapAnimation: Boolean) : ItemTexture() {

        override fun getItemModelId(): Identifier {
            val id = model.getResource()?.getItemModel()?.toIdentifier() ?: unknownID()
            val swapMode = if (showSwapAnimation) "__swap" else "__noswap"
            return id("${id}${swapMode}")
        }

        override fun isAvailable(): Boolean = model.isAvailable()

        override fun getRef(): ResourceRef<*> {
            return model
        }
    }
}

public fun itemTexture(texture: ResourceRef<Texture2D>, showSwapAnimation: Boolean = true): ItemTexture.Texture =
    ItemTexture.Texture(texture, showSwapAnimation)

public fun itemModel(model: ResourceRef<Model3D>, showSwapAnimation: Boolean = true): ItemTexture.Model =
    ItemTexture.Model(model, showSwapAnimation)

public fun itemTexture(plugin: CuTPlugin, path: String, showSwapAnimation: Boolean = true): ItemTexture.Texture =
    ItemTexture.Texture(ref(plugin, path), showSwapAnimation)

public fun itemModel(plugin: CuTPlugin, path: String, showSwapAnimation: Boolean = true): ItemTexture.Model =
    ItemTexture.Model(ref(plugin, path), showSwapAnimation)

public fun itemTexture(stringPath: String, showSwapAnimation: Boolean = true): ItemTexture.Texture =
    ItemTexture.Texture(ref(stringPath), showSwapAnimation)

public fun itemModel(stringPath: String, showSwapAnimation: Boolean = true): ItemTexture.Model =
    ItemTexture.Model(ref(stringPath), showSwapAnimation)

/**
 * A class for creating dynamic displays (lore, name, etc.) for [CuTItemStack]s.
 * Whenever the server sends an ItemStack to the client for any reason, this will be
 * applied to it.
 */
@ItemDescriptorDsl
public open class ItemDisplayBuilder(public val itemStack: CuTItemStack, public val viewer: Player?) {

    public val type: CustomItem<*> get() = itemStack.type

    private val lines = mutableListOf<Component>()
    public var name: Component? = null

    public var texture: ItemTexture? = null

    /**
     * Adds a Component to this item description.
     *
     * @param components The component(s) being added to this item's description.
     */
    public fun text(vararg components: Component) {
        lines += components
    }

    public fun text(components: Collection<Component>) {
        lines += components
    }

    /**
     * Adds an empty line to this item description.
     */
    public fun emptyLine() {
        lines += "&7".colored
    }

    /**
     * Adds any lore that any [ItemLoreAttachment] would want to implement.
     */
    public fun attachmentLore(mainColor: Color) {
        for (attachment in itemStack.getAllAttachments().filterIsInstance<ItemLoreAttachment>()) {
            val lore = attachment.getLore(itemStack, viewer)
            if (lore != null) {
                lines.add(lore.color(mainColor.textColor))
            }
        }
    }

    public fun toTextComponents(): List<Component> {
        return lines.toList()
    }

    public fun hasAttachment(schema: xyz.mastriel.cutapi.data.Schema<out Attachment>): Boolean =
        itemStack.hasAttachment(schema)

    public fun <T : Attachment> getAttachment(schema: xyz.mastriel.cutapi.data.Schema<T>): T =
        itemStack.getAttachment(schema)

    public fun <T : Attachment> getAttachmentOrNull(schema: xyz.mastriel.cutapi.data.Schema<T>): T? =
        itemStack.getAttachmentOrNull(schema)

}

public interface ItemLoreAttachment : Attachment {
    public fun getLore(item: CuTItemStack, viewer: Player?): Component?
}

/**
 * A descriptor for a [xyz.mastriel.cutapi.item.CustomItem], which contains useful info
 * such as a name, lore, NBT, etc.
 *
 * @param block The builder, used to create the final [ItemDescriptor]
 */
public fun itemDescriptor(block: ItemDescriptorBuilder.() -> Unit): ItemDescriptor =
    ItemDescriptorBuilder().apply(block).build()

/**
 * A default descriptor for a [xyz.mastriel.cutapi.item.CustomItem].
 */
public fun defaultItemDescriptor(): ItemDescriptor =
    ItemDescriptorBuilder().build()

