package xyz.mastriel.cutapi.item

import io.papermc.paper.datacomponent.*
import io.papermc.paper.datacomponent.item.*
import net.kyori.adventure.text.*
import org.bukkit.entity.*
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
    attachments: List<ItemAttachment> = emptyList(),
    public val onRegister: EventHandlerList<ItemRegisterEvent> = EventHandlerList(),
    internal val forcedItemModelId: Identifier? = null,
) : AttachmentHolder<ItemAttachment> {

    public val attachments: List<ItemAttachment> = attachments.toList()

    override fun hasAttachment(schema: xyz.mastriel.cutapi.data.Schema<out ItemAttachment>): Boolean =
        attachments.any { it.schema().id == schema.id }

    override fun <T : ItemAttachment> getAttachment(schema: xyz.mastriel.cutapi.data.Schema<T>): T =
        getAttachmentOrNull(schema) ?: error("Attachment ${schema.id} does not exist on this descriptor.")

    override fun <T : ItemAttachment> getAttachmentOrNull(schema: xyz.mastriel.cutapi.data.Schema<T>): T? =
        getAttachments(schema).firstOrNull()

    override fun <T : ItemAttachment> getAttachments(schema: xyz.mastriel.cutapi.data.Schema<T>): List<T> =
        attachments.filter { it.schema().id == schema.id }.map {
            @Suppress("UNCHECKED_CAST")
            it as T
        }

    override fun getAllAttachments(): List<ItemAttachment> = attachments.toList()

    public infix fun with(block: ItemDescriptorBuilder.() -> Unit): ItemDescriptor {
        val other = ItemDescriptorBuilder().apply(block).build()
        return this + other
    }

    public operator fun plus(other: ItemDescriptor): ItemDescriptor {
        return itemDescriptor {
            display = other.display ?: this@ItemDescriptor.display
            forcedItemModelId = other.forcedItemModelId ?: this@ItemDescriptor.forcedItemModelId

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

    private val _attachments = mutableListOf<ItemAttachment>()
    public val attachments: List<ItemAttachment> get() = _attachments

    public val onRegister: EventHandlerList<ItemRegisterEvent> = EventHandlerList()
    internal var forcedItemModelId: Identifier? = null

    public fun attach(vararg attachments: ItemAttachment) {
        for (attachment in attachments) {
            val schema = attachment.schema()
            if (this._attachments.any { it.schema().id == schema.id } && !attachment.isRepeatableAttachment())
                error("${schema.id} lacks a RepeatableAttachment annotation to be repeatable.")
            _attachments.add(attachment)
        }
    }

    public fun attach(attachments: Collection<ItemAttachment>) {
        attach(*attachments.toTypedArray())
    }

    public fun build(): ItemDescriptor {

        return ItemDescriptor(
            display = display,
            attachments = _attachments.toList(),
            onRegister = onRegister,
            forcedItemModelId = forcedItemModelId,
        )
    }

    public fun display(block: ItemDisplayBuilder.() -> Unit) {
        display = block
    }


    public fun noDisplay() {
        display = {}
    }
}

/**
 * A class for creating dynamic displays (lore, name, etc.) for [CuTItemStack]s.
 * Whenever the server sends an ItemStack to the client for any reason, this will be
 * applied to it.
 */
@ItemDescriptorDsl
public open class ItemDisplayBuilder(public val itemStack: CuTItemStack, public val viewer: Player?) {

    public val type: CustomItem<*>
        get() = itemStack.customItem
            ?: error("ItemDisplayBuilder.type is only available while rendering a custom item.")

    private val lines = mutableListOf<Component>()
    public var name: Component? = null

    /** The exact client item definition assigned to the rendered stack. */
    public var itemModel: ResourceRef<ItemModel>? = null

    private var customModelDataBuilder: ItemDisplayCustomModelDataBuilder? = null

    /**
     * Supplies the ordered values read by `minecraft:custom_model_data` item-model properties.
     * Each value kind has its own zero-based index space.
     */
    public fun customModelData(block: ItemDisplayCustomModelDataBuilder.() -> Unit) {
        customModelDataBuilder = ItemDisplayCustomModelDataBuilder().apply(block)
    }

    internal fun buildCustomModelDataOrNull(): CustomModelData? = customModelDataBuilder?.build()

    internal fun applyCustomModelDataTo(rendered: org.bukkit.inventory.ItemStack) {
        buildCustomModelDataOrNull()?.let { component ->
            rendered.setData(DataComponentTypes.CUSTOM_MODEL_DATA, component)
        }
    }

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

    public fun hasAttachment(schema: xyz.mastriel.cutapi.data.Schema<out ItemAttachment>): Boolean =
        itemStack.hasAttachment(schema)

    public fun <T : ItemAttachment> getAttachment(schema: xyz.mastriel.cutapi.data.Schema<T>): T =
        itemStack.getAttachment(schema)

    public fun <T : ItemAttachment> getAttachmentOrNull(schema: xyz.mastriel.cutapi.data.Schema<T>): T? =
        itemStack.getAttachmentOrNull(schema)

}

/** Builds the component-side inputs consumed by a client [ItemModel]. */
@Suppress("UnstableApiUsage")
@ItemDescriptorDsl
public class ItemDisplayCustomModelDataBuilder internal constructor() {
    private val floats: MutableList<Float> = mutableListOf()
    private val flags: MutableList<Boolean> = mutableListOf()
    private val strings: MutableList<String> = mutableListOf()
    private val colors: MutableList<Color> = mutableListOf()

    public fun float(value: Float) {
        floats += value
    }

    public fun flag(value: Boolean) {
        flags += value
    }

    public fun string(value: String) {
        strings += value
    }

    public fun color(value: Color) {
        colors += value
    }

    internal fun build(): CustomModelData = CustomModelData.customModelData()
        .addFloats(floats)
        .addFlags(flags)
        .addStrings(strings)
        .addColors(colors.map(Color::bukkit))
        .build()
}

public interface ItemLoreAttachment : ItemAttachment {
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

