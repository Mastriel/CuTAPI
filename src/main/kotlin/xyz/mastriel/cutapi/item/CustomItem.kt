@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item

import kotlinx.serialization.Serializable
import org.bukkit.*
import org.bukkit.event.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.nativeitem.*
import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*

public object CustomItemSerializer : IdentifiableSerializer<CustomItem<*>>("customItem", CustomItem)

public typealias AnyCustomItem = CustomItem<*>

@Serializable(with = CustomItemSerializer::class)
public open class CustomItem<TStack : CuTItemStack> @PublishedApi internal constructor(
    override val id: Identifier,
    public val backingItem: ItemType,
    public val stackTypeClass: KClass<out TStack>,
    public open val descriptor: ItemDescriptor,
) : Identifiable, Listener, AttachmentHolder<ItemAttachment> {

    public val itemType: ItemType
        get() {
            xyz.mastriel.cutapi.item.nativeitem.NativeItemLifecycle.requireActive()
            return NativeItemTypes.get(id)
        }

    public val identity: ItemIdentity.Custom get() = ItemIdentity.of(this)

    public fun createItemStack(quantity: Int = 1): TStack = CuTItemStack.create<TStack>(this, quantity)

    internal fun nativeSpecification(): NativeItemSpecification = NativeItemSpecification(this)

    internal fun activate() {
        descriptor.attachments.forEach { it.schema().requireRegistered() }
        id.plugin?.plugin?.let { Bukkit.getPluginManager().registerEvents(this, it) }
        descriptor.onRegister.trigger(ItemRegisterEvent(this))
    }

    private val attachmentHolder by lazy { CustomItemAttachmentHolder(this) }

    override fun hasAttachment(schema: xyz.mastriel.cutapi.data.Schema<out ItemAttachment>): Boolean =
        attachmentHolder.hasAttachment(schema)

    override fun <T : ItemAttachment> getAttachment(schema: xyz.mastriel.cutapi.data.Schema<T>): T =
        attachmentHolder.getAttachment(schema)

    override fun <T : ItemAttachment> getAttachmentOrNull(schema: xyz.mastriel.cutapi.data.Schema<T>): T? =
        attachmentHolder.getAttachmentOrNull(schema)

    override fun <T : ItemAttachment> getAttachments(schema: xyz.mastriel.cutapi.data.Schema<T>): List<T> =
        attachmentHolder.getAttachments(schema)

    override fun getAllAttachments(): List<ItemAttachment> = attachmentHolder.getAllAttachments()

    public companion object :
        IdentifierRegistry<CustomItem<*>>(id("cutapi:registry/custom_item")),
        DebugViewProvider<CustomItem<*>> by debugView(
            id("cutapi:custom_item"),
            {
                extends { Identifiable }
                property("backingItem", VariantSerializer.String) { it.backingItem.key.toString() }
                property("stackTypeClass", VariantSerializer.String) {
                    it.stackTypeClass.qualifiedName ?: "<anonymous class>"
                }
                property("intrinsicAttachments", VariantSerializer.List) { item ->
                    item.getAllAttachments().map { attachment ->
                        attachment.schema().serialize(attachment).getOrThrow()
                    }
                }
            },
        ) {
        internal val DeferredRegistry = defer(RegistryPriority(Int.MAX_VALUE))

        public val InventoryBackground: CustomItem<CuTItemStack> by DeferredRegistry.registerCustomItem(
            id = id("cutapi:inventory_background"),
            backingItem = ItemType.GLISTERING_MELON_SLICE,
        ) {
            attach(xyz.mastriel.cutapi.item.attachments.HideTooltip)
            display {
                texture = itemModel(
                    xyz.mastriel.cutapi.Plugin,
                    "ui/inventory_bg.model3d.json",
                )
            }
        }
    }
}
