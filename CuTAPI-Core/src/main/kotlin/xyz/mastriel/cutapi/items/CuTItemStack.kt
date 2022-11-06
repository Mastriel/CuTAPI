package xyz.mastriel.cutapi.items

import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.items.behaviors.ItemBehavior
import xyz.mastriel.cutapi.pdc.tags.ItemTagContainer
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.idOrNull
import xyz.mastriel.cutapi.registry.unknownID
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault
import xyz.mastriel.cutapi.utils.personalized.withViewer


/**
 * This **does not** contain behaviors! This only will show the behaviors that the custom material has.
 */
open class CuTItemStack(val handle: ItemStack) : ItemTagContainer(handle),
    BehaviorHolder<ItemBehavior> by handle.customItem, PersonalizedWithDefault<ItemStack> {
    
    var name: Component
        get() = handle.displayName()
        set(value) {
            nameHasChanged = true
            val meta = handle.itemMeta
            meta.displayName(value)
            handle.itemMeta = meta
        }

    var type by customItemTag("CuTID", CustomItem.Unknown)
    var nameHasChanged : Boolean by booleanTag("NameHasChanged", false)
    val descriptor get() = type.descriptor
    var texture = descriptor.texture

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

        itemStack.editMeta {
            it.lore(getLore(viewer))

            if (nameHasChanged) {
                it.displayName(name)
                return@editMeta
            }

            val typeName = descriptor.name
            if (typeName == null) {
                it.displayName("&c${type.id}".colored)
                return@editMeta
            }
            it.displayName(typeName withViewer viewer)
        }
        return itemStack
    }

    companion object {
        val CUT_ID_TAG = NamespacedKey(Plugin, "CuTID")

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

        val ItemStack.isCustom: Boolean
            get() {
                if (this.type.isAir) return false
                return customIdOrNull != null
            }

        fun ItemStack.wrap() : CuTItemStack? {
            if (!isCustom || type.isAir) return null
            return CuTItemStack(this)
        }

        private fun ItemStack.withMaterialId(customItem: CustomItem): ItemStack {
            val meta = itemMeta
            meta.persistentDataContainer.set(CUT_ID_TAG, PersistentDataType.STRING, customItem.id.toString())
            itemMeta = meta
            return this
        }
    }
}