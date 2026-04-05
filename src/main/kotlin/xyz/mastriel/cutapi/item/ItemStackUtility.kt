package xyz.mastriel.cutapi.item

import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.inventory.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.ItemStackUtility.DefaultItemStackTypeId
import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*


public object ItemStackUtility {
    public val TypeId: Identifier = id(Plugin, "type")
    public val TypeKey: NamespacedKey = TypeId.toNamespacedKey()
    public val ItemStackTypeTag: NamespacedKey = NamespacedKey(Plugin, "itemstack_type")
    public val DefaultItemStackTypeId: Identifier = id("cutapi:builtin")

    /**
     * The custom id of this item.
     * May return [unknownID] if this doesn't have a custom id.
     * Does not check if a custom id is valid or not, just returns whatever is in the item stack data.
     */
    public val ItemStack.customId: Identifier
        get() {
            if (this.type.isAir) return unknownID()
            val pdc = itemMeta.persistentDataContainer
            if (!pdc.has(TypeKey)) return unknownID()
            return idOrNull(pdc.get(TypeKey, PersistentDataType.STRING)!!) ?: unknownID()
        }

    /**
     * The [CustomItem] of this [ItemStack].
     *
     * @throws IllegalStateException if this doesn't have a valid custom item id.
     */
    public val ItemStack.customItem: CustomItem<*>
        get() {
            return CustomItem.get(customId)
        }

    /**
     * The [CustomItem] of this [ItemStack].
     *
     * @returns null if this doesn't have a valid custom item id, otherwise the [CustomItem].
     */
    public val ItemStack.customItemOrNull: CustomItem<*>?
        get() {
            return CustomItem.getOrNull(customId)
        }


    /**
     * The custom id of this item. May be null.
     */
    public val ItemStack.customIdOrNull: Identifier?
        get() {
            if (this.type.isAir) return null
            val pdc = itemMeta.persistentDataContainer
            if (!pdc.has(TypeKey)) return null
            return idOrNull(pdc.get(TypeKey, PersistentDataType.STRING)!!)
        }

    /**
     * The [Identifier] type of this item stack. Will return the CuTItemStack ID ([DefaultItemStackTypeId])
     * even if this isn't a custom item!
     */
    public val ItemStack.cutItemStackType: Identifier
        get() {
            if (this.type.isAir) return DefaultItemStackTypeId
            val pdc = itemMeta.persistentDataContainer
            if (!pdc.has(ItemStackTypeTag)) return DefaultItemStackTypeId
            return idOrNull(pdc.get(ItemStackTypeTag, PersistentDataType.STRING)!!) ?: DefaultItemStackTypeId
        }

    /**
     * The [KClass] type of this item stack. Will return CuTItemStack even if this isn't a
     * custom item!
     */
    public val ItemStack.typeClass: KClass<out CuTItemStack>
        get() {
            val stackType = cutItemStackType
            return CuTItemStack.getType(stackType) ?: CuTItemStack::class
        }

    /**
     * Check if this ItemStack is a custom item.
     * Does NOT check if this is a __valid__ custom item, just that it has an ID.
     */
    public val ItemStack.isCustom: Boolean
        get() {
            if (this.type.isAir) return false
            return customIdOrNull?.let { CustomItem.getOrNull(it) } != null
        }

    /**
     * Turns an ItemStack into a CuTItemStack.
     *
     * Returns null if:
     * - This material is air.
     * - This material doesn't have any CustomItem id.
     * - [CuTItemStack.wrap] fails to wrap it. (use this to get a specific error thrown)
     */
    public fun ItemStack.wrap(): CuTItemStack? {
        if (!isCustom || type.isAir) return null
        return try {
            CuTItemStack.wrap(this)
        } catch (_: Exception) {
            null
        }
    }

    /** Returns true if an ItemStack is returned from [CuTItemStack.getRenderedItemStack]. */
    public fun ItemStack.isDisplay(): Boolean {
        if (!isCustom) return false

        return itemMeta.persistentDataContainer.get(
            NamespacedKey(Plugin, "is_display"),
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
    public inline fun <reified T : CuTItemStack> ItemStack.wrap(): T? {
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
    public inline fun <reified T : CuTItemStack> ItemStack.isType(): Boolean {
        val id = customIdOrNull ?: return false
        val type = CuTItemStack.getType(id) ?: return false
        return type == T::class
    }

    /**
     * Check if this CuTItemStack type is [T]
     */
    public inline fun <reified T : CuTItemStack> Item.isType(): Boolean {
        return itemStack.isType<T>()
    }

    /**
     * Sets the CustomItem and CuTItemStack ID of an [ItemStack] to whatever the [customItem] specifies.
     *
     * Use with caution.
     */
    public fun ItemStack.asCustomItem(customItem: CustomItem<*>): ItemStack {
        val meta = itemMeta
        meta.persistentDataContainer.set(TypeKey, PersistentDataType.STRING, customItem.id.toString())
        meta.persistentDataContainer.set(
            ItemStackTypeTag, PersistentDataType.STRING,
            (CuTItemStack.getType(customItem.stackTypeClass) ?: DefaultItemStackTypeId).toString()
        )

        itemMeta = meta
        return this
    }
}

