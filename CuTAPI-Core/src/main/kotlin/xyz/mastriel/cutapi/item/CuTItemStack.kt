package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.item.ItemStackUtility.customIdOrNull
import xyz.mastriel.cutapi.item.ItemStackUtility.customItem
import xyz.mastriel.cutapi.item.ItemStackUtility.withMaterialId
import xyz.mastriel.cutapi.item.behaviors.ItemBehavior
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.registry.idOrNull
import xyz.mastriel.cutapi.registry.unknownID
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault
import xyz.mastriel.cutapi.utils.personalized.withViewer
import kotlin.reflect.KClass
import kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.reflect


/**
 * A CuTAPI item stack with a CuTID tag under cutapi's
 * [PDC](org.bukkit.persistence.PersistentDataContainer) namespace.
 *
 * It is not recommended to have a public constructor for this class, and it is instead recommended to
 */
open class CuTItemStack protected constructor(val handle: ItemStack) : ItemTagContainer(handle),
    BehaviorHolder<ItemBehavior> by handle.customItem, PersonalizedWithDefault<ItemStack> {

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

    val descriptor get() = type.descriptor

    var material
        get() = handle.type
        set(value) {
            handle.type = value
        }

    val enchantments: MutableMap<Enchantment, Int>
        get() = handle.enchantments

    fun getLore(viewer: Player?): List<Component> {
        val loreFormatter = descriptor.description
        if (loreFormatter != null) {
            val descriptionBuilder = DescriptionBuilder(this, viewer)
            return descriptionBuilder.apply(loreFormatter).toTextComponents()
        }
        return emptyList()
    }


    final override fun getAllBehaviors(): Set<ItemBehavior> = handle.customItem.getAllBehaviors()

    constructor(customItem: CustomItem, quantity: Int) : this(
        ItemStack(customItem.type, quantity).withMaterialId(customItem)
    ) {
        getAllBehaviors().forEach { it.onCreate(this) }
    }

    init {
        require(handle.customIdOrNull != null) { "ItemStack not wrappable into a CuTItemStack." }

        if (!nameHasChanged) {
            val meta = handle.itemMeta
            meta.displayName(descriptor.name?.withViewer(null))
            handle.itemMeta = meta
        }
    }

    override fun withViewer(viewer: Player): ItemStack {
        return getRenderedItemStack(viewer)
    }

    override fun getDefault(): ItemStack {
        return getRenderedItemStack(null)
    }

    fun getRenderedItemStack(viewer: Player?): ItemStack {
        val itemStack = handle.clone()

        getAllBehaviors().forEach { it.onRender(viewer, itemStack) }

        itemStack.editMeta { item ->
            item.lore(getLore(viewer))

            if (nameHasChanged) {
                item.displayName(name)
                return@editMeta
            }

            val typeName = descriptor.name
            if (typeName == null) {
                item.displayName("&c${type.id}".colored)
                return@editMeta
            }
            item.displayName(typeName withViewer viewer)

            val textureRef = viewer?.let { p -> descriptor.texture?.withViewer(p) }

            if (textureRef != null && textureRef.isAvailable) {
                val customModelData = textureRef.getResource().getCustomModelData()
                item.setCustomModelData(customModelData)
            }
        }
        return itemStack
    }

    companion object {
        private val types = mutableMapOf<Identifier, ItemStackType>()

        fun getType(id: Identifier) : KClass<out CuTItemStack>? {
            return types[id]?.kClass
        }

        @OptIn(ExperimentalReflectionOnLambdas::class)
        fun <T: CuTItemStack> registerType(id: Identifier, kClass: KClass<out T>, constructor: PrimaryCISCtor) {
            types[id] = ItemStackType(kClass, constructor)
            constructor.reflect()?.isAccessible = true
        }


        fun wrap(handle: ItemStack) : CuTItemStack {
            val id = handle.customIdOrNull ?: error("ItemStack not wrappable into a CuTItemStack.")
            val type = types[id] ?: error("$id is not a registered CuTItemStack type.")

            return type.primaryConstructor(handle)
        }

        @Suppress("UNCHECKED_CAST")
        fun <T: CuTItemStack> wrap(handle: ItemStack) : T {
            return wrap(handle) as T
        }

        fun create(customItem: CustomItem, quantity: Int) : CuTItemStack {
            return wrap(ItemStack(customItem.type, quantity).withMaterialId(customItem)).also {
                it.getAllBehaviors().forEach { b -> b.onCreate(it) }
            }

        }

        fun <T: CuTItemStack> create(customItem: CustomItem, quantity: Int) : T {
            return wrap<T>(ItemStack(customItem.type, quantity).withMaterialId(customItem)).also {
                it.getAllBehaviors().forEach { b -> b.onCreate(it) }
            }
        }

    }
}
typealias PrimaryCISCtor = (ItemStack) -> CuTItemStack

private data class ItemStackType(
    val kClass: KClass<out CuTItemStack>,
    val primaryConstructor: PrimaryCISCtor
)

object ItemStackUtility {
    val CUT_ID_TAG = NamespacedKey(Plugin, "CuTID")
    val CUT_ITEMSTACK_TYPE_TAG = NamespacedKey(Plugin, "CuTItemStackType")
    val DEFAULT_ITEMSTACK_TYPE_ID = id("cutapi:builtin")

    val ItemStack.customId: Identifier
        get() {
            if (this.type.isAir) return unknownID()
            val pdc = itemMeta.persistentDataContainer
            if (!pdc.has(CUT_ID_TAG)) return unknownID()
            return idOrNull(pdc.get(CUT_ID_TAG, PersistentDataType.STRING)!!) ?: unknownID()
        }

    val ItemStack.customItem: CustomItem
        get() {
            return CustomItem.get(customId)
        }

    val ItemStack.customIdOrNull: Identifier?
        get() {
            if (this.type.isAir) return null
            val pdc = itemMeta.persistentDataContainer
            if (!pdc.has(CUT_ID_TAG)) return null
            return idOrNull(pdc.get(CUT_ID_TAG, PersistentDataType.STRING)!!)
        }

    val ItemStack.cutItemStackType: Identifier
        get() {
            if (this.type.isAir) return DEFAULT_ITEMSTACK_TYPE_ID
            val pdc = itemMeta.persistentDataContainer
            if (!pdc.has(CUT_ITEMSTACK_TYPE_TAG)) return DEFAULT_ITEMSTACK_TYPE_ID
            return idOrNull(pdc.get(CUT_ITEMSTACK_TYPE_TAG, PersistentDataType.STRING)!!) ?: DEFAULT_ITEMSTACK_TYPE_ID
        }

    val ItemStack.typeClass: KClass<out CuTItemStack>
        get() {
            return CuTItemStack.getType(cutItemStackType) ?: CuTItemStack::class
        }

    val ItemStack.isCustom: Boolean
        get() {
            if (this.type.isAir) return false
            return customIdOrNull != null
        }

    fun ItemStack.wrap(): CuTItemStack? {
        if (!isCustom || type.isAir) return null
        return CuTItemStack.wrap(this)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: CuTItemStack> ItemStack.wrap(): CuTItemStack? {
        if (!isCustom || type.isAir) return null
        return CuTItemStack.wrap<T>(this)
    }

    internal fun ItemStack.withMaterialId(customItem: CustomItem): ItemStack {
        val meta = itemMeta
        meta.persistentDataContainer.set(CUT_ID_TAG, PersistentDataType.STRING, customItem.id.toString())
        itemMeta = meta
        return this
    }
}

