package xyz.mastriel.cutapi.items.behaviors

import net.kyori.adventure.text.Component
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.behavior.Behavior
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent
import xyz.mastriel.cutapi.pdc.tags.ItemBehaviorTagContainer
import xyz.mastriel.cutapi.pdc.tags.TagContainer
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier

/**
 * Alters the behavior of a material.
 */
abstract class ItemBehavior(
    override val id: Identifier
) : Identifiable, Behavior {

    /**
     * The lore which this component shows when applied to a [CuTItemStack].
     */
    open fun getLore(item: CuTItemStack, viewer: Player?) : Component? = null

    protected fun getData(item: CuTItemStack) : TagContainer {
        return ItemBehaviorTagContainer(item.handle, this.id)
    }

    open fun onLeftClick(player: Player, item: CuTItemStack, event: PlayerInteractEvent) {}
    open fun onMiddleClick(player: Player, item: CuTItemStack, event: PlayerInteractEvent) {}
    open fun onRightClick(player: Player, item: CuTItemStack, event: PlayerInteractEvent) {}

    open fun onDrop(player: Player, item: CuTItemStack, event: PlayerDropItemEvent) {}
    open fun onObtain(player: Player, item: CuTItemStack, event: CustomItemObtainEvent) {}
    open fun onCreate(item: CuTItemStack) {}
    open fun onRender(viewer: Player?, item: ItemStack) {}

    open fun onTickInEitherHand(player: Player, item: CuTItemStack) {}
    open fun onTickInInventory(player: Player, item: CuTItemStack) {}
    open fun onTickEquipped(player: Player, item: CuTItemStack) {}

    open fun onOffhandEquip(player: Player, item: CuTItemStack, event: Cancellable) {}
    open fun onDamageEntity(attacker: LivingEntity, victim: LivingEntity, mainHandItem: CuTItemStack, event: EntityDamageByEntityEvent) {}


}