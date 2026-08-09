package xyz.mastriel.cutapi.item

import org.bukkit.entity.Item
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KClass

public object ItemStackUtility {
    public val ItemStack.customItem: CustomItem<*>
        get() = customItemOrNull ?: error("${itemIdentity.itemType.key} is not a registered custom item.")

    public val ItemStack.customItemOrNull: CustomItem<*>?
        get() = (itemIdentity as? ItemIdentity.Custom)?.item

    public val ItemStack.isCustom: Boolean get() = itemIdentity.isCustom

    public val ItemStack.typeClass: KClass<out CuTItemStack>
        get() = customItemOrNull?.stackTypeClass ?: CuTItemStack::class

    public fun ItemStack.wrap(): CuTItemStack = CuTItemStack.wrap(this)

    @JvmName("wrapWithType")
    public inline fun <reified T : CuTItemStack> ItemStack.wrap(): T? =
        runCatching { CuTItemStack.wrap<T>(this) }.getOrNull()

    public inline fun <reified T : CuTItemStack> ItemStack.isType(): Boolean = wrap() is T

    public inline fun <reified T : CuTItemStack> Item.isType(): Boolean = itemStack.isType<T>()
}
