@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item

import kotlinx.serialization.Serializable
import org.bukkit.*
import org.bukkit.event.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.CustomItem.Companion.ProgressArrow
import xyz.mastriel.cutapi.item.attachments.*
import xyz.mastriel.cutapi.item.nativeitem.*
import xyz.mastriel.cutapi.item.systems.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.process.*
import xyz.mastriel.cutapi.utils.Color
import kotlin.reflect.*

public object CustomItemSerializer : IdentifiableSerializer<CustomItem<*>>("customItem", CustomItem)

public typealias AnyCustomItem = CustomItem<*>

@Serializable(with = CustomItemSerializer::class)
public open class CustomItem<TStack : CuTItemStack> @PublishedApi internal constructor(
    override val id: Identifier,
    public val backingItem: ItemType,
    public val stackTypeClass: KClass<out TStack>,
    descriptor: ItemDescriptor,
) : Identifiable, Listener, AttachmentHolder<ItemAttachment> {

    private var preparedDescriptor: ItemDescriptor = descriptor

    public open val descriptor: ItemDescriptor get() = preparedDescriptor

    public val itemType: ItemType
        get() {
            NativeItemLifecycle.requireActive()
            return NativeItemTypes.get(id)
        }

    public val identity: ItemIdentity.Custom get() = ItemIdentity.of(this)

    public fun createItemStack(quantity: Int = 1): TStack = CuTItemStack.create<TStack>(this, quantity)

    internal fun nativeSpecification(): NativeItemSpecification = NativeItemSpecification(this)

    internal fun prepareDescriptor(transform: (ItemDescriptor) -> ItemDescriptor) {
        check(NativeItemLifecycle.state == NativeItemState.Collecting) {
            "Custom item $id cannot be prepared while native items are ${NativeItemLifecycle.state}."
        }
        check(isOpen) { "Custom item $id cannot be prepared after its registry has closed." }
        preparedDescriptor = transform(preparedDescriptor)
    }

    internal fun activate() {
        descriptor.attachments.forEach { it.schema().requireRegistered() }
        id.plugin?.plugin?.let { Bukkit.getPluginManager().registerEvents(this, it) }
        descriptor.onRegister.trigger(ItemRegisterEvent(this))
    }

    private val attachmentHolder by lazy { CustomItemAttachmentHolder(this) }

    override fun hasAttachment(schema: Schema<out ItemAttachment>): Boolean =
        attachmentHolder.hasAttachment(schema)

    override fun <T : ItemAttachment> getAttachment(schema: Schema<T>): T =
        attachmentHolder.getAttachment(schema)

    override fun <T : ItemAttachment> getAttachmentOrNull(schema: Schema<T>): T? =
        attachmentHolder.getAttachmentOrNull(schema)

    override fun <T : ItemAttachment> getAttachments(schema: Schema<T>): List<T> =
        attachmentHolder.getAttachments(schema)

    override fun getAllAttachments(): List<ItemAttachment> = attachmentHolder.getAllAttachments()

    /** Controls the rendered appearance of [ProgressArrow]. */
    public data class ProgressVisual(
        public val progress: Float,
        public val unfilledColor: Color = Color.of(0x8B8B8B),
        public val filledColor: Color = Color.of(0xFFFFFF),
        public val inventoryBackground: Boolean = true,
    ) : ItemAttachment {
        public companion object : Schema<ProgressVisual> by schema(id("cutapi:progress_visual"), {
            property(ProgressVisual::progress, VariantSerializer.Float)
            property(ProgressVisual::unfilledColor, BuiltinSerializers.Color) {
                optional(omitDefaults = true) { Color.of(0x8B8B8B) }
            }
            property(ProgressVisual::filledColor, BuiltinSerializers.Color) {
                optional(omitDefaults = true) { Color.of(0xFFFFFF) }
            }
            property(ProgressVisual::inventoryBackground, VariantSerializer.Boolean) {
                optional(omitDefaults = true) { true }
            }
        })
    }

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
            id = id("cutapi:ui/inventory_background"),
            backingItem = ItemType.GLISTERING_MELON_SLICE,
        ) {
            attach(HideTooltip, ItemOriginImmune)
            display {
                itemModel = ref(Plugin, "ui/inventory_bg.item_model.json")
            }
        }

        public val ProgressArrow: CustomItem<CuTItemStack> by DeferredRegistry.registerCustomItem(
            id = id("cutapi:ui/progress_arrow"),
            backingItem = ItemType.GLISTERING_MELON_SLICE,
        ) {
            attach(ProgressVisual(0f), HideTooltip, ItemOriginImmune)
            display {
                val visual = getAttachmentOrNull(ProgressVisual) ?: return@display
                itemModel = horizontalProgressItemModelRef(
                    source = ref<Texture2D>(Plugin, "ui/progress_arrow_empty.png"),
                    generationSubId = ProgressArrowGenerationSubId,
                )
                customModelData {
                    float(horizontalProgressFrameIndex(visual.progress, ProgressArrowFrameCount).toFloat())
                    flag(visual.inventoryBackground)
                    color(visual.unfilledColor)
                    color(visual.filledColor)
                }
            }
        }

        public val Transparent: CustomItem<CuTItemStack> by DeferredRegistry.registerCustomItem(
            id = id("cutapi:ui/transparent"),
            backingItem = ItemType.GLISTERING_MELON_SLICE,
        ) {
            attach(HideTooltip, ItemOriginImmune)

            display {
                itemModel = ref(Plugin, "ui/transparent.png")
            }
        }

        private const val ProgressArrowFrameCount: Int = 24
        private const val ProgressArrowGenerationSubId: String = "frames"
    }
}
