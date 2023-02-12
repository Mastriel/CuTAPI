package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.item.ItemStackUtility.CUT_ID_TAG
import xyz.mastriel.cutapi.item.ItemStackUtility.customIdOrNull
import xyz.mastriel.cutapi.item.ItemStackUtility.customItem
import xyz.mastriel.cutapi.item.ItemStackUtility.cutItemStackType
import xyz.mastriel.cutapi.item.ItemStackUtility.asCustomItem
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault
import kotlin.reflect.KClass
import kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.reflect


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
open class CuTItemStack protected constructor(val handle: ItemStack) :
    TagContainer by ItemTagContainer(handle),
    BehaviorHolder<ItemBehavior> by handle.customItem,
    PersonalizedWithDefault<ItemStack> {

    init {
        require(CustomItem.has(handle.customIdOrNull)) { "${handle.customIdOrNull} is not a registered Custom Item." }
        getAllBehaviors().forEach { b -> b.onCreate(this) }
    }

    var name: Component
        get() = handle.displayName()
        set(value) {
            nameHasChanged = true
            val meta = handle.itemMeta
            meta.displayName(value)
            handle.itemMeta = meta
        }

    protected open fun onCreate() {}

    var type by customItemTag("CuTID", CustomItem.Unknown)
    var nameHasChanged: Boolean by booleanTag("NameHasChanged", false)

    internal var lore by loreTag("lore")


    val descriptor get() = type.descriptor

    var material by handle::type

    val enchantments: MutableMap<Enchantment, Int>
        get() = handle.enchantments

    fun getLore(viewer: Player?): List<Component> {
        val lore = mutableListOf<Component>()
        val display = descriptor.display
        if (display != null && displayLoreVisible) {
            val displayBuilder = DisplayBuilder(this, viewer)
            lore += displayBuilder.apply(display).toTextComponents()
        }
        lore += this.lore.get()

        return lore
    }


    final override fun getAllBehaviors(): Set<ItemBehavior> = handle.customItem.getAllBehaviors()

    init {
        require(handle.customIdOrNull != null) { "ItemStack not wrappable into a CuTItemStack." }
    }

    override fun withViewer(viewer: Player): ItemStack {
        return getRenderedItemStack(viewer)
    }

    override fun getDefault(): ItemStack {
        return getRenderedItemStack(null)
    }

    open fun getRenderedItemStack(viewer: Player?): ItemStack {
        val itemStack = handle.clone()

        itemStack.editMeta { meta ->

            if (descriptor.display != null) {
                val display = DisplayBuilder(this, viewer).apply(descriptor.display!!)

                meta.lore(getLore(viewer))

                if (nameHasChanged) {
                    meta.displayName(name)
                } else {
                    meta.displayName(display.name ?: "&c${type.id}".colored)
                }
            }

            val textureRef = viewer?.let { p -> descriptor.texture?.withViewer(p) }

            if (textureRef != null && textureRef.isAvailable) {
                val customModelData = textureRef.getResource().getCustomModelData()
                meta.setCustomModelData(customModelData)
            }

            meta.persistentDataContainer.set(NamespacedKey(Plugin, "IsDisplay"), PersistentDataType.BYTE, 1)
        }

        getAllBehaviors().forEach { it.onRender(viewer, itemStack.wrap()!!) }

        return itemStack
    }

    /**
     * Returns a rendered itemstack without a CuTAPI ID, making it not act as a custom item.
     */
    fun getStaticItemStack(viewer: Player?) : ItemStack {
        return getRenderedItemStack(viewer).apply {
            val meta = itemMeta
            meta.persistentDataContainer.remove(CUT_ID_TAG)
            itemMeta = meta
        }
    }

    companion object {
        internal val CONSTRUCTOR = ::CuTItemStack // allow for internal visibility as well
        private val types = mutableMapOf<Identifier, ItemStackType>()

        fun getType(id: Identifier) : KClass<out CuTItemStack>? {
            return types[id]?.kClass
        }

        fun getType(kClass: KClass<out CuTItemStack>) : Identifier? {
            return types.toList().firstOrNull { it.second.kClass == kClass }?.first
        }

        @OptIn(ExperimentalReflectionOnLambdas::class)
        fun <T: CuTItemStack> registerType(id: Identifier, kClass: KClass<out T>, constructor: PrimaryCISCtor) {
            types[id] = ItemStackType(kClass, constructor)
            constructor.reflect()?.isAccessible = true
        }


        fun wrap(handle: ItemStack) : CuTItemStack {
            val id = handle.cutItemStackType
            val type = types[id] ?: error("$id is not a registered CuTItemStack type.")

            return type.primaryConstructor(handle).also { it.onCreate() }
        }

        @JvmName("wrapWithType")
        @Suppress("UNCHECKED_CAST")
        fun <T: CuTItemStack> wrap(handle: ItemStack) : T {
            return wrap(handle) as? T ?: error("ItemStack could not be cast into this class.")
        }

        fun create(customItem: CustomItem<*>, quantity: Int = 1) : CuTItemStack {
            return wrap(ItemStack(customItem.type, quantity).asCustomItem(customItem))

        }

        @JvmName("createWithType")
        fun <T: CuTItemStack> create(customItem: CustomItem<T>, quantity: Int = 1) : T {
            return wrap<T>(ItemStack(customItem.type, quantity).asCustomItem(customItem))
        }

    }
}



typealias PrimaryCISCtor = (ItemStack) -> CuTItemStack

private data class ItemStackType(
    val kClass: KClass<out CuTItemStack>,
    val primaryConstructor: PrimaryCISCtor
)
