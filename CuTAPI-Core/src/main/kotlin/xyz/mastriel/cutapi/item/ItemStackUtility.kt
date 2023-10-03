package xyz.mastriel.cutapi.item

import org.bukkit.NamespacedKey
import org.bukkit.entity.Item
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.registry.idOrNull
import xyz.mastriel.cutapi.registry.unknownID
import kotlin.reflect.KClass


object ItemStackUtility {
    val CUT_ID_TAG = NamespacedKey(Plugin, "CuTID")
    val CUT_ITEMSTACK_TYPE_TAG = NamespacedKey(Plugin, "CuTItemStackType")
    val DEFAULT_ITEMSTACK_TYPE_ID = id("cutapi:builtin")

    /**
     * The custom id of this item.
     * May return [unknownID] if this doesn't have a custom id.
     * Does not check if a custom id is valid or not, just returns whatever is in the item stack data.
     */
    val ItemStack.customId: Identifier
        get() {
            if (this.type.isAir) return unknownID()
            val pdc = itemMeta.persistentDataContainer
            if (!pdc.has(CUT_ID_TAG)) return unknownID()
            return idOrNull(pdc.get(CUT_ID_TAG, PersistentDataType.STRING)!!) ?: unknownID()
        }

    /**
     * The [CustomItem] of this [ItemStack].
     *
     * @throws IllegalStateException if this doesn't have a valid custom item id.
     */
    val ItemStack.customItem: CustomItem<*>
        get() {
            return CustomItem.get(customId)
        }

    /**
     * The custom id of this item. May be null.
     */
    val ItemStack.customIdOrNull: Identifier?
        get() {
            if (this.type.isAir) return null
            val pdc = itemMeta.persistentDataContainer
            if (!pdc.has(CUT_ID_TAG)) return null
            return idOrNull(pdc.get(CUT_ID_TAG, PersistentDataType.STRING)!!)
        }

    /**
     * The [Identifier] type of this item stack. Will return the CuTItemStack ID ([DEFAULT_ITEMSTACK_TYPE_ID])
     * even if this isn't a custom item!
     */
    val ItemStack.cutItemStackType: Identifier
        get() {
            if (this.type.isAir) return DEFAULT_ITEMSTACK_TYPE_ID
            val pdc = itemMeta.persistentDataContainer
            if (!pdc.has(CUT_ITEMSTACK_TYPE_TAG)) return DEFAULT_ITEMSTACK_TYPE_ID
            return idOrNull(pdc.get(CUT_ITEMSTACK_TYPE_TAG, PersistentDataType.STRING)!!) ?: DEFAULT_ITEMSTACK_TYPE_ID
        }

    /**
     * The [KClass] type of this item stack. Will return CuTItemStack even if this isn't a
     * custom item!
     */
    val ItemStack.typeClass: KClass<out CuTItemStack>
        get() {
            val stackType = cutItemStackType
            return CuTItemStack.getType(stackType) ?: CuTItemStack::class
        }

    /**
     * Check if this ItemStack is a custom item.
     * Does NOT check if this is a __valid__ custom item, just that it has an ID.
     */
    val ItemStack.isCustom: Boolean
        get() {
            if (this.type.isAir) return false
            return customIdOrNull != null
        }

    /**
     * Turns an ItemStack into a CuTItemStack.
     *
     * Returns null if:
     * - This material is air.
     * - This material doesn't have any CustomItem id.
     * - [CuTItemStack.wrap] fails to wrap it. (use this to get a specific error thrown)
     */
    fun ItemStack.wrap(): CuTItemStack? {
        if (!isCustom || type.isAir) return null
        return try {
            CuTItemStack.wrap(this)
        } catch (_: Exception) {
            null
        }
    }

    /** Returns true if an ItemStack is returned from [CuTItemStack.getRenderedItemStack]. */
    fun ItemStack.isDisplay(): Boolean {
        if (!isCustom) return false

        return itemMeta.persistentDataContainer.get(
            NamespacedKey(Plugin, "IsDisplay"),
            PersistentDataType.BYTE
        ) == 1.toByte()
    }

    /**
     * Turns an ItemStack into a CuTItemStack.
     *
     * Returns null if:
     * - This material is air.
     * - This material doesn't have any CustomItem id.
     * - [CuTItemStack.wrap] fails to wrap it. (use this to get a specific error thrown)
     * - The result from [CuTItemStack.wrap] isn't [T]
     */
    @JvmName("wrapWithType")
    inline fun <reified T : CuTItemStack> ItemStack.wrap(): T? {
        if (!isCustom || type.isAir) return null
        val stack = try {
            CuTItemStack.wrap<T>(this)
        } catch (_: Exception) {
            null
        }
        if (stack !is T) return null
        return stack
    }

    /**
     * Check if this CuTItemStack type is [T]
     */
    inline fun <reified T : CuTItemStack> ItemStack.isType(): Boolean {
        val id = customIdOrNull ?: return false
        val type = CuTItemStack.getType(id) ?: return false
        return type == T::class
    }

    /**
     * Check if this CuTItemStack type is [T]
     */
    inline fun <reified T : CuTItemStack> Item.isType(): Boolean {
        return itemStack.isType<T>()
    }

    /**
     * Sets the CustomItem and CuTItemStack ID of an [ItemStack] to whatever the [customItem] specifies.
     *
     * Use with caution.
     */
    fun ItemStack.asCustomItem(customItem: CustomItem<*>): ItemStack {
        val meta = itemMeta
        meta.persistentDataContainer.set(CUT_ID_TAG, PersistentDataType.STRING, customItem.id.toString())
        meta.persistentDataContainer.set(
            CUT_ITEMSTACK_TYPE_TAG, PersistentDataType.STRING,
            (CuTItemStack.getType(customItem.stackTypeClass) ?: DEFAULT_ITEMSTACK_TYPE_ID).toString()
        )

        itemMeta = meta
        return this
    }
}

