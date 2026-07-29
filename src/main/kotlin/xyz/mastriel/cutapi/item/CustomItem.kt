package xyz.mastriel.cutapi.item

import org.bukkit.*
import org.bukkit.event.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.ItemStackUtility.customItem
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.item.attachments.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import kotlin.reflect.*

private typealias KSerializable = kotlinx.serialization.Serializable

public object CustomItemSerializer : IdentifiableSerializer<CustomItem<*>>("customMaterial", CustomItem)

/**
 * An alias for a [CustomItem<*>](CustomItem). This has a CuTItemStack as its stack type.
 *
 * @see CustomItem
 */
public typealias AnyCustomItem = CustomItem<*>

@KSerializable(with = CustomItemSerializer::class)
public open class CustomItem<TStack : CuTItemStack>(
    override val id: Identifier,
    public val type: Material,
    public val stackTypeClass: KClass<out TStack>,
    descriptor: ItemDescriptor? = null
) : Identifiable, Listener, AttachmentHolder<ItemAttachment> {


    /**
     * The descriptor that describes the custom material's default values, such
     * as a default name, default lore, attachments, etc.
     */
    public open val descriptor: ItemDescriptor = descriptor ?: defaultItemDescriptor()

    init {
        if (descriptor != null) {
            addAutoDisplayAs(descriptor)
        }
    }

    private fun addAutoDisplayAs(descriptor: ItemDescriptor) {
        val attachments = descriptor.attachments as? MutableList ?: return
        val plugin = id.plugin ?: return

        val autoDisplayAs = CuTAPI.getDescriptor(plugin)
            .options.autoDisplayAsForTexturedItems ?: return

        attachments.add(DisplayAs(autoDisplayAs))
    }

    public fun createItemStack(quantity: Int = 1): TStack =
        CuTItemStack.create<TStack>(this, quantity)

    public open fun onCreate(item: CuTItemStack) {}

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

    protected fun getData(item: CuTItemStack): TagContainer {
        return ItemAttachmentTagContainer(item.handle, id.copy(namespace = id.namespace, key = id.key + "/data"))
    }


    public companion object :
        IdentifierRegistry<CustomItem<*>>(id("cutapi:registry/custom_item")),
        DebugViewProvider<CustomItem<*>> by debugView<CustomItem<*>>(
            id("cutapi:custom_item"),
            {
                extends { Identifiable }
                property("type", VariantSerializer.String) { it.type.key.toString() }
                property("stackTypeClass", VariantSerializer.String) {
                    it.stackTypeClass.qualifiedName ?: "<anonymous class>"
                }
            }
        ) {
        internal val DeferredRegistry = defer(RegistryPriority(Int.MAX_VALUE))

        public val Unknown: CustomItem<CuTItemStack> by lazy {
            customItem(
                unknownID(),
                Material.ANVIL
            ) {
                attach(StaticLore("&cYou probably shouldn't have this...".colored))
                attach(DisplayAs(Material.GLISTERING_MELON_SLICE))
                display {
                    texture = itemTexture(Plugin, "items/unknown_item.png")
                }
            }
        }

        public val InventoryBackground: CustomItem<CuTItemStack> by DeferredRegistry.registerCustomItem(
            id = id("cutapi:inventory_background"),
            Material.GLISTERING_MELON_SLICE
        ) {
            attach(HideTooltip)

            display {
                texture = itemModel(Plugin, "ui/inventory_bg.model3d.json")
            }
        }

        override fun get(id: Identifier): CustomItem<*> {
            return super.getOrNull(id) ?: return Unknown
        }

        override fun register(item: CustomItem<*>): CustomItem<*> {
            item.descriptor.attachments.forEach { attachment ->
                attachment.schema().requireRegistered()
            }
            val plugin = item.id.plugin

            if (plugin != null) {
                val bukkitPlugin = plugin.plugin
                Bukkit.getServer().pluginManager.registerEvents(item, bukkitPlugin)
            }
            return super.register(item).also { item.descriptor.onRegister.trigger(ItemRegisterEvent(item)) }
        }
    }
}


/**
 * Represents an item that is either a normal item stack or a CuT item stack.
 */
public sealed class AgnosticMaterial {

    public fun matches(item: ItemStack): Boolean = item.agnosticMaterial == this

    /** The expected vanilla material of this agnostic material.
     *
     * For vanilla items, this is always just the material of the item.
     * For custom items, this is the material that the custom item is assigned by default.
     */
    public abstract val expectedVanillaMaterial: Material

    public infix fun materialIs(value: Material): Boolean {
        if (this is Vanilla) {
            return this.vanilla() == value
        }
        return false
    }

    public infix fun materialIs(value: CustomItem<*>): Boolean {
        if (this is Custom) {
            return this.custom() == value
        }
        return false
    }

    public data class Custom(val itemType: AnyCustomItem) : AgnosticMaterial() {
        public fun custom(): CustomItem<*> = itemType

        override val expectedVanillaMaterial: Material
            get() = itemType.type
    }

    public data class Vanilla(private val material: Material) : AgnosticMaterial() {
        public fun vanilla(): Material = material

        override val expectedVanillaMaterial: Material
            get() = material
    }
}

public val ItemStack.agnosticMaterial: AgnosticMaterial
    get() {
        if (this.isCustom) {
            return AgnosticMaterial.Custom(this.customItem)
        }
        return AgnosticMaterial.Vanilla(this.type)
    }

public val CuTItemStack.agnosticMaterial: AgnosticMaterial
    get() {
        return AgnosticMaterial.Custom(this.type)
    }


public fun Material.toAgnostic(): AgnosticMaterial.Vanilla {
    return AgnosticMaterial.Vanilla(this)
}

public fun AnyCustomItem.toAgnostic(): AgnosticMaterial.Custom {
    return AgnosticMaterial.Custom(this)
}
