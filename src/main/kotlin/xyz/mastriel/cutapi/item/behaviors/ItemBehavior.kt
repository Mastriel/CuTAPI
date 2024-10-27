package xyz.mastriel.cutapi.item.behaviors

import net.kyori.adventure.text.*
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.player.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.events.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*

/**
 * Alters the behavior of a material.
 */
public abstract class ItemBehavior(
    override val id: Identifier
) : Identifiable, Behavior {

    /**
     * The lore which this component shows when applied to a [CuTItemStack].
     */
    public open fun getLore(item: CuTItemStack, viewer: Player?): Component? = null

    protected fun getData(item: CuTItemStack): TagContainer {
        return ItemBehaviorTagContainer(item.handle, this.id)
    }

    public open fun onLeftClick(player: Player, item: CuTItemStack, event: PlayerInteractEvent) {}

    public open fun onMiddleClick(player: Player, item: CuTItemStack, event: PlayerInteractEvent) {}
    public open fun onRightClick(player: Player, item: CuTItemStack, event: PlayerInteractEvent) {}
    public open fun onRightClickEntity(
        player: Player,
        item: CuTItemStack,
        entity: Entity,
        event: PlayerInteractEntityEvent
    ) {
    }

    public open fun onDrop(player: Player, item: CuTItemStack, event: PlayerDropItemEvent) {}
    public open fun onObtain(player: Player, item: CuTItemStack, event: CustomItemObtainEvent) {}

    /**
     * Called whenever CustomItem.createItemStack or [CuTItemStack.create] is called.
     */
    public open fun onCreate(item: CuTItemStack) {}

    /**
     * Triggered when the item is being rendered.
     *
     * @param viewer The viewer of this item. May be null if it doesn't have a known viewer.
     * @param item A normal ItemStack which will only be sent to the client, and will not reflect
     * the "real" properties of the respective CuTItemStack if it is changed.
     */
    public open fun onRender(viewer: Player?, item: CuTItemStack) {}


    public open fun onTickInEitherHand(player: Player, item: CuTItemStack, slot: HandSlot) {}

    /**
     * This will trigger for both items in your hands and items equipped still.
     */
    public open fun onTickInInventory(player: Player, item: CuTItemStack, slot: Int) {}
    public open fun onTickEquipped(player: Player, item: CuTItemStack, slot: ArmorSlot) {}

    public open fun onOffhandEquip(player: Player, item: CuTItemStack, event: Cancellable) {}
    public open fun onDamageEntity(
        attacker: LivingEntity,
        victim: LivingEntity,
        mainHandItem: CuTItemStack,
        event: EntityDamageByEntityEvent
    ) {
    }


    public open fun onPlace(player: Player, item: CuTItemStack, location: Location, event: BlockPlaceEvent) {}

    public open fun ItemDescriptorBuilder.modifyDescriptor() {}

    public open fun onBreak(player: Player, item: CuTItemStack, event: BlockBreakEvent) {}
}

public enum class HandSlot { MAIN_HAND, OFF_HAND }
public enum class ArmorSlot { HELMET, CHESTPLATE, LEGGINGS, BOOTS }
