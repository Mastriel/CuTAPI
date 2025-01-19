package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.*
import org.bukkit.*
import org.bukkit.enchantments.*
import org.bukkit.entity.*
import org.bukkit.inventory.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.item.ItemStackUtility.CUT_ID_TAG
import xyz.mastriel.cutapi.item.ItemStackUtility.asCustomItem
import xyz.mastriel.cutapi.item.ItemStackUtility.customIdOrNull
import xyz.mastriel.cutapi.item.ItemStackUtility.customItem
import xyz.mastriel.cutapi.item.ItemStackUtility.cutItemStackType
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.item.PacketItemHandler.hasPrerenderStack
import xyz.mastriel.cutapi.item.PacketItemHandler.setPrerenderItemStack
import xyz.mastriel.cutapi.item.behaviors.*
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import xyz.mastriel.cutapi.utils.personalized.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*


/**
 * A wrapped ItemStack that is guarenteed to be a custom item.
 *
 * Use [ItemStack.wrap](ItemStackUtility.wrap) to automatically wrap any ItemStack into an appropriate subclass of this.
 *
 * Wrapping an ItemStack:
 * ```kt
 * // If the subtype is known
 * val wrapped : MyCuTItemStack? = itemStack.wrap<MyCuTItemStack>()
 *
 * // If the subtype is not known, or is CuTItemStack
 * val wrapped : CuTItemStack? = itemStack.wrap()
 * ```
 *
 * Using a subtype:
 * ```kotlin
 * class MyCuTItemStack internal constructor(handle: ItemStack) : CuTItemStack(handle) {
 *
 *     // Leverage Tags to store persistant data easily in the stack.
 *
 *     // Will never be null, and instead the default of "hello".
 *     var persistentString by stringTag("TagName", "hello")
 *     private var uses by intTag("Uses", 0)
 *
 *     // May be null.
 *     var nullablePersistentString by nullableStringTag("AnotherTagName")
 *
 *     // This will not persist in the item, and will be
 *     // cleared whenever this item is wrapped again.
 *     private var tempBoolean = false
 *
 *     fun increaseUses() {
 *         // Automatically saved to the handle ItemStack.
 *         uses++
 *     }
 *
 * }
 * ```
 *
 * Registering the subtype:
 * ```kotlin
 * // In your plugin class
 * override fun onEnable() {
 *     // ...
 *     CuTItemStack.registerType(
 *         id("example:my_cut_itemstack"),
 *         MyCuTItemStack::class,
 *
 *         // The constructor to MyCuTItemStack, or any function that takes
 *         // in an ItemStack and returns an instance of your subclass.
 *         ::MyCuTItemStack
 *     )
 * }
 * ```
 *
 * @param handle The ItemStack that is being wrapped.
 *
 */
public open class CuTItemStack protected constructor(
    /**
     * The item stack being wrapped. If you want to have a vanilla item stack to mess with,
     * use this. Any changes made to this class will be automatically reflected to the handle.
     */
    public val handle: ItemStack
) : TagContainer by ItemTagContainer(handle),
    BehaviorHolder<ItemBehavior> by handle.customItem,
    PersonalizedWithDefault<ItemStack> {

    /**
     * Equivalent to [handle].
     */
    public fun vanilla(): ItemStack = handle

    init {
        require(handle.customIdOrNull != null) { "ItemStack not wrappable into a CuTItemStack." }
        require(CustomItem.has(handle.customIdOrNull)) { "${handle.customIdOrNull} is not a registered Custom Item." }
    }

    public var name: Component
        get() = handle.itemMeta.itemName()
        set(value) {
            nameHasChanged = true
            val meta = handle.itemMeta
            meta.itemName(value)
            handle.itemMeta = meta
        }

    /**
     * Evaluated whenever the item stack is created for the first time, NOT when it is wrapped.
     * If you want to have something run whenever an item stack is wrapped, then just add to
     * your constructor.
     */
    protected open fun onCreate() {}

    public var type: CustomItem<*> by customItemTag("CuTID", CustomItem.Unknown)
    public var nameHasChanged: Boolean by booleanTag("NameHasChanged", false)

    internal var lore by loreTag("lore")


    /**
     * The descriptor of this custom item stack. This is equivalent to [CustomItem.descriptor].
     *
     * @see ItemDescriptor
     */
    public val descriptor: ItemDescriptor get() = type.descriptor

    /**
     * The material of the item. This is equivalent to [ItemStack.type], or [handle].type.
     *
     * @see org.bukkit.Material
     */
    public var material: Material by handle::type

    /**
     * The enchantments of the [handle].
     *
     * @see ItemStack.getEnchantments
     */
    @TemporaryAPI
    public val enchantments: MutableMap<Enchantment, Int>
        get() = handle.enchantments

    /**
     * Returns the lore of this item stack. Lore on a custom item stack is
     * different from adding text in a display builder. Lore appears after
     * [ItemDisplayBuilder.text].
     *
     * @param viewer The viewer of the item stack. May be null if there's no
     * viewer, or it can't be determined.
     * @return A list of components, representing the lore.
     */
    public fun getLore(viewer: Player?): List<Component> {
        val lore = mutableListOf<Component>()
        val display = descriptor.display
        if (display != null && displayLoreVisible) {
            val displayBuilder = ItemDisplayBuilder(this, viewer)
            lore += displayBuilder.apply(display).toTextComponents()
        }
        lore += this.lore.get()

        return lore
    }


    final override fun getAllBehaviors(): Set<ItemBehavior> = handle.customItem.getAllBehaviors()

    /**
     * Calls [getRenderedItemStack].
     */
    override fun withViewer(viewer: Player): ItemStack {
        return getRenderedItemStack(viewer)
    }

    /**
     * Calls [getRenderedItemStack] with null for a player.
     */
    override fun getDefault(): ItemStack {
        return getRenderedItemStack(null)
    }

    /**
     * This will return an [ItemStack] clone from this custom item stack's handle, applying
     * all the data that it needs to. This is used in sending the player packets containing
     * this custom item. It is not recommened to have any rendered item stacks actually given
     * to a player or made available, as this could confuse CuTAPI.
     *
     * The rendered item stack returned will have the key "cutapi:IsDisplay" byte in the persistent
     * data container set to 1, to indicate that it's a display item.
     */
    @OptIn(UsesNMS::class)
    public open fun getRenderedItemStack(viewer: Player?): ItemStack {
        val itemStack = handle.clone()

        val rewrap = wrap(itemStack)
        if (!rewrap.hasPrerenderStack()) {
            rewrap.setPrerenderItemStack(itemStack)
        }

        itemStack.editMeta { meta ->

            if (descriptor.display != null) {
                val display = try {
                    ItemDisplayBuilder(this, viewer).apply(descriptor.display!!)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    return@editMeta
                }
                meta.lore(getLore(viewer))

                if (nameHasChanged) {
                    meta.itemName(name)
                } else {
                    meta.itemName(display.name ?: "&c${type.id}".colored)
                }

                meta.itemModel = display.texture?.getItemModelId()?.toNamespacedKey()
            }

            meta.persistentDataContainer.set(NamespacedKey(Plugin, "IsDisplay"), PersistentDataType.BYTE, 1)

        }

        getAllBehaviors().forEach { it.onRender(viewer, itemStack.wrap()!!) }

        return itemStack
    }

    /**
     * Returns a rendered itemstack without a CuTAPI ID, making it not act as a custom item.
     *
     * This uses [getRenderedItemStack], but also removes information about what [CuTItemStack] type
     * this is, as well as information about what the [CustomItem] ID is.
     *
     * Recommended for use in GUIs or similar to prevent custom actions and things from happening
     * in a place that you might not expect. Also allows you to just interact with this like a normal
     * [ItemStack], which can be useful.
     */
    public fun getStaticItemStack(viewer: Player?): ItemStack {
        return getRenderedItemStack(viewer).apply {
            val meta = itemMeta
            meta.persistentDataContainer.remove(CUT_ID_TAG)
            meta.persistentDataContainer.remove(ItemStackUtility.CUT_ITEMSTACK_TYPE_TAG)

            if (this@CuTItemStack.descriptor.display != null) {
                val display = ItemDisplayBuilder(this@CuTItemStack, viewer).apply(descriptor.display!!)

                val itemTexture = display.texture
                if (itemTexture != null && itemTexture.isAvailable()) {
                    meta.itemModel = itemTexture.getItemModelId().toNamespacedKey()
                }
            }

            itemMeta = meta
        }
    }

    public companion object {
        internal val CONSTRUCTOR = ::CuTItemStack // allow for internal visibility as well
        private val types = mutableMapOf<Identifier, ItemStackType>()


        public fun getType(id: Identifier): KClass<out CuTItemStack>? {
            return types[id]?.kClass
        }

        public fun getType(kClass: KClass<out CuTItemStack>): Identifier? {
            return types.toList().firstOrNull { it.second.kClass == kClass }?.first
        }

        /**
         * Register a custom item stack type. This is required to be able to properly wrap
         * an item stack.
         *
         * @param id The identifier of this CuTItemStack.
         * @param kClass The class of this CuTItemStack.
         * @param constructor The constructor of this CuTItemStack, OR any function that takes in an ItemStack and
         * returns [T].
         */
        @OptIn(ExperimentalReflectionOnLambdas::class)
        public fun <T : CuTItemStack> registerType(id: Identifier, kClass: KClass<out T>, constructor: PrimaryCISCtor) {
            types[id] = ItemStackType(kClass, constructor)
            constructor.reflect()?.isAccessible = true
        }


        public fun wrap(handle: ItemStack): CuTItemStack {
            val id = handle.cutItemStackType
            val type = types[id] ?: error("$id is not a registered CuTItemStack type.")

            return type.primaryConstructor(handle)
        }

        @JvmName("wrapWithType")
        @Suppress("UNCHECKED_CAST")
        public fun <T : CuTItemStack> wrap(handle: ItemStack): T {
            return wrap(handle) as? T ?: error("ItemStack could not be cast into this class.")
        }

        public fun create(customItem: CustomItem<*>, quantity: Int = 1): CuTItemStack {
            return wrap(ItemStack(customItem.type, quantity).asCustomItem(customItem))
                .also { it.getAllBehaviors().forEach { b -> b.onCreate(it) }; it.onCreate() }

        }

        @JvmName("createWithType")
        public fun <T : CuTItemStack> create(customItem: CustomItem<T>, quantity: Int = 1): T {
            return wrap<T>(ItemStack(customItem.type, quantity).asCustomItem(customItem))
                .also { it.getAllBehaviors().forEach { b -> b.onCreate(it) }; it.onCreate() }
        }

    }
}


/**
 * Represents an item that is either a normal item stack or a CuT item stack.
 */
public sealed class AgnosticItemStack(public val handle: ItemStack) {
    public fun vanilla(): ItemStack = handle


    public data class Custom(val stack: CuTItemStack) : AgnosticItemStack(stack.handle) {
        public fun custom(): CuTItemStack = stack
    }

    public data class Vanilla(private val stack: ItemStack) : AgnosticItemStack(stack)
}

public fun ItemStack.toAgnostic(): AgnosticItemStack {
    val wrapped = wrap()
    if (wrapped != null) {
        return AgnosticItemStack.Custom(wrapped)
    }
    return AgnosticItemStack.Vanilla(this)
}

public fun CuTItemStack.toAgnostic(): AgnosticItemStack.Custom {
    return AgnosticItemStack.Custom(this)
}


public typealias PrimaryCISCtor = (ItemStack) -> CuTItemStack

private data class ItemStackType(
    val kClass: KClass<out CuTItemStack>,
    val primaryConstructor: PrimaryCISCtor
)
