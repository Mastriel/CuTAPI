@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item.nativeitem

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier as MinecraftIdentifier
import net.minecraft.world.item.Item
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.craftbukkit.inventory.CraftItemType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import xyz.mastriel.cutapi.nms.UsesNMS
import xyz.mastriel.cutapi.registry.Identifier

@UsesNMS
public object NativeItemTypes {
    private val customItems: MutableMap<Identifier, Item> = mutableMapOf()
    internal var stackResolverForTests: ((ItemStack) -> ItemType)? = null

    internal fun bind(id: Identifier, item: Item) {
        check(customItems.putIfAbsent(id, item) == null) { "Native item $id is already bound." }
    }

    internal fun clear() {
        customItems.clear()
    }

    public fun get(id: Identifier): ItemType {
        val item = customItems[id] ?: error("Native item $id has not been installed.")
        return CraftItemType.minecraftToBukkitNew(item)
            ?: error("Native item $id is missing from Bukkit's item registry.")
    }

    internal fun getMinecraft(id: Identifier): Item =
        customItems[id] ?: error("Native item $id has not been installed.")

    internal fun getMinecraft(type: ItemType): Item = CraftItemType.bukkitToMinecraftNew(type)

    public fun resolve(stack: ItemStack): ItemType {
        stackResolverForTests?.let { return it(stack) }
        if (stack.type.isAir || stack.amount <= 0) return ItemType.AIR
        val item = CraftItemStack.asNMSCopy(stack).item
        return CraftItemType.minecraftToBukkitNew(item)
            ?: error("Item ${BuiltInRegistries.ITEM.getKey(item)} is missing from Bukkit's item registry.")
    }

    internal fun idOf(item: Item): Identifier? {
        val location: MinecraftIdentifier = BuiltInRegistries.ITEM.getKey(item) ?: return null
        return Identifier(location.namespace, location.path)
    }
}
