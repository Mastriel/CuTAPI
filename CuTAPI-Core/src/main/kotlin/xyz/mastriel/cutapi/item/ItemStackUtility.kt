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

    val ItemStack.customId: Identifier
        get() {
            if (this.type.isAir) return unknownID()
            val pdc = itemMeta.persistentDataContainer
            if (!pdc.has(CUT_ID_TAG)) return unknownID()
            return idOrNull(pdc.get(CUT_ID_TAG, PersistentDataType.STRING)!!) ?: unknownID()
        }

    val ItemStack.customItem: CustomItem<*>
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
            val stackType = cutItemStackType
            return CuTItemStack.getType(stackType) ?: CuTItemStack::class
        }

    val ItemStack.isCustom: Boolean
        get() {
            if (this.type.isAir) return false
            return customIdOrNull != null
        }

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

    inline fun <reified T : CuTItemStack> ItemStack.isType(): Boolean {
        val id = customIdOrNull ?: return false
        val type = CuTItemStack.getType(id) ?: return false
        return type == T::class
    }

    inline fun <reified T : CuTItemStack> Item.isType(): Boolean {
        return itemStack.isType<T>()
    }

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

