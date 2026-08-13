@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.*
import org.bukkit.*
import org.bukkit.craftbukkit.inventory.*
import org.bukkit.enchantments.*
import org.bukkit.entity.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.item.attachments.*
import xyz.mastriel.cutapi.item.nativeitem.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import xyz.mastriel.cutapi.utils.Color
import xyz.mastriel.cutapi.utils.personalized.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

/** A CuTAPI wrapper around either a vanilla or native custom item stack. */
public open class CuTItemStack protected constructor(public val handle: ItemStack) :
    TagContainer by ItemTagContainer(handle),
    AttachmentHolder<ItemAttachment>,
    PersonalizedWithDefault<ItemStack> {

    public fun vanilla(): ItemStack = handle

    public val identity: ItemIdentity get() = handle.itemIdentity

    public val itemType: ItemType get() = identity.itemType

    public val customItem: CustomItem<*>? get() = (identity as? ItemIdentity.Custom)?.item

    public val isCustom: Boolean get() = identity.isCustom

    public val material: Material get() = handle.type

    public var name: Component
        get() = handle.itemMeta.itemName()
        set(value) {
            nameHasChanged = true
            val meta = handle.itemMeta
            meta.itemName(value)
            handle.itemMeta = meta
        }

    public var nameHasChanged: Boolean by booleanTag(id("cutapi:name_has_changed")) { false }

    internal var lore by loreTag(id("cutapi:lore"))

    private val vanillaDescriptor: ItemDescriptor by lazy(::defaultItemDescriptor)

    public val descriptor: ItemDescriptor get() = customItem?.descriptor ?: vanillaDescriptor

    private val attachmentHolder by lazy { CuTItemStackAttachmentHolder(this) }

    override fun hasAttachment(schema: xyz.mastriel.cutapi.data.Schema<out ItemAttachment>): Boolean =
        attachmentHolder.hasAttachment(schema)

    override fun <T : ItemAttachment> getAttachment(schema: xyz.mastriel.cutapi.data.Schema<T>): T =
        attachmentHolder.getAttachment(schema)

    override fun <T : ItemAttachment> getAttachmentOrNull(schema: xyz.mastriel.cutapi.data.Schema<T>): T? =
        attachmentHolder.getAttachmentOrNull(schema)

    override fun <T : ItemAttachment> getAttachments(schema: xyz.mastriel.cutapi.data.Schema<T>): List<T> =
        attachmentHolder.getAttachments(schema)

    override fun getAllAttachments(): List<ItemAttachment> = attachmentHolder.getAllAttachments()

    @TemporaryAPI
    public val enchantments: MutableMap<Enchantment, Int> get() = handle.enchantments

    public fun getLore(viewer: Player?): List<Component> {
        val result = mutableListOf<Component>()
        val display = customItem?.descriptor?.display
        if (display != null && displayLoreVisible) {
            result += ItemDisplayBuilder(this, viewer).apply(display).toTextComponents()
        } else if (!isCustom && displayLoreVisible) {
            result += ItemDisplayBuilder(this, viewer).apply {
                attachmentLore(Color.Blue)
            }.toTextComponents()
        }
        result += lore.get()
        return result
    }

    override fun withViewer(viewer: Player): ItemStack = getRenderedItemStack(viewer)

    override fun getDefault(): ItemStack = getRenderedItemStack(null)

    /** Returns a detached presentation copy. The authoritative [handle] is never mutated. */
    public open fun getRenderedItemStack(viewer: Player?): ItemStack {
        val rendered = handle.clone()
        val display = customItem?.descriptor?.display

        rendered.editMeta { meta ->
            meta.itemModel = customItem?.descriptor?.forcedItemModelId?.toNamespacedKey()
        }
        if (display != null) {
            rendered.editMeta { meta ->
                val builder = ItemDisplayBuilder(this, viewer).apply(display)
                meta.lore(getLore(viewer))
                meta.itemName(if (nameHasChanged) name else builder.name ?: "&c${customItem?.id}".colored)
                val itemModelId = builder.texture?.getItemModelId()
                if (itemModelId != null) meta.itemModel = itemModelId.toNamespacedKey()
            }
        } else if (!isCustom) {
            val addedLore = getLore(viewer)
            if (addedLore.isNotEmpty()) {
                rendered.editMeta { meta -> meta.lore(meta.lore().orEmpty() + addedLore) }
            }
        }

        ItemSystem.dispatchRender(ItemRenderContext(wrap(rendered), handle.clone().wrap(), viewer))
        return rendered
    }

    /** Returns a permanent vanilla representation with no native custom identity. */
    public fun getStaticItemStack(viewer: Player?): ItemStack {
        val rendered = getRenderedItemStack(viewer)
        val definition = customItem ?: return rendered
        val displayedType = getAttachmentOrNull<DisplayAs>()?.itemType ?: definition.backingItem
        val displayedItem = NativeItemTypes.getMinecraft(displayedType)
        return CraftItemStack.asBukkitCopy(CraftItemStack.asNMSCopy(rendered).transmuteCopy(displayedItem))
    }

    public companion object {
        internal val CONSTRUCTOR: PrimaryCISCtor = ::CuTItemStack
        private val types: MutableMap<Identifier, ItemStackType> = mutableMapOf()

        public fun getType(id: Identifier): KClass<out CuTItemStack>? = types[id]?.kClass

        public fun getType(kClass: KClass<out CuTItemStack>): Identifier? =
            types.entries.firstOrNull { it.value.kClass == kClass }?.key

        @OptIn(ExperimentalReflectionOnLambdas::class)
        public fun <T : CuTItemStack> registerType(
            id: Identifier,
            kClass: KClass<out T>,
            constructor: PrimaryCISCtor,
        ) {
            types[id] = ItemStackType(kClass, constructor)
            constructor.reflect()?.isAccessible = true
        }

        public fun wrap(handle: ItemStack): CuTItemStack {
            val definition = (handle.itemIdentity as? ItemIdentity.Custom)?.item
            if (definition == null || definition.stackTypeClass == CuTItemStack::class) return CONSTRUCTOR(handle)

            val type = types.values.firstOrNull { it.kClass == definition.stackTypeClass }
                ?: error("${definition.stackTypeClass.qualifiedName} is not a registered CuTItemStack type.")
            return type.primaryConstructor(handle)
        }

        @JvmName("wrapWithType")
        @Suppress("UNCHECKED_CAST")
        public fun <T : CuTItemStack> wrap(handle: ItemStack): T =
            wrap(handle) as? T ?: error("ItemStack could not be cast to the requested CuTItemStack type.")

        public fun create(identity: ItemIdentity, quantity: Int = 1): CuTItemStack {
            if (identity.isCustom) NativeItemLifecycle.requireActive()
            return finishCreate(wrap(identity.itemType.createItemStack(quantity)))
        }

        public fun create(itemType: ItemType, quantity: Int = 1): CuTItemStack =
            create(ItemIdentity.of(itemType), quantity)

        public fun create(customItem: CustomItem<*>, quantity: Int = 1): CuTItemStack =
            create(customItem.identity, quantity)

        @JvmName("createWithType")
        public fun <T : CuTItemStack> create(customItem: CustomItem<T>, quantity: Int = 1): T {
            @Suppress("UNCHECKED_CAST")
            return create(customItem.identity, quantity) as T
        }

        private fun <T : CuTItemStack> finishCreate(stack: T): T = stack.also {
            ItemMaterializationManager.ensureReconciled(it.handle)
        }
    }
}

public typealias PrimaryCISCtor = (ItemStack) -> CuTItemStack

private data class ItemStackType(
    val kClass: KClass<out CuTItemStack>,
    val primaryConstructor: PrimaryCISCtor,
)
