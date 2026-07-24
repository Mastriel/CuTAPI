package xyz.mastriel.cutapi.item

import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.*
import org.bukkit.event.player.*
import xyz.mastriel.cutapi.item.ItemStackUtility.isCustom
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.item.events.*
import xyz.mastriel.cutapi.periodic.*
import xyz.mastriel.cutapi.system.*
import xyz.mastriel.cutapi.utils.*

public class ItemSystemEvents : Listener {
    @EventHandler
    public fun onObtain(event: CustomItemObtainEvent) {
        dispatch(event.item) {
            it.onObtain(ItemObtainContext(event.item, event.player, event))
        }
    }

    @EventHandler
    public fun onInteract(event: PlayerInteractEvent) {
        val item = event.player.inventory.itemInMainHand.wrap() ?: return
        dispatch(item) {
            when {
                event.action.isLeftClick -> it.onLeftClick(ItemInteractContext(item, event.player, event))
                event.action.isRightClick -> it.onRightClick(ItemInteractContext(item, event.player, event))
            }
        }
    }

    @EventHandler
    public fun onInteract(event: PlayerInteractEntityEvent) {
        val item = event.player.inventory.itemInMainHand.wrap() ?: return
        dispatch(item) {
            it.onRightClickEntity(ItemRightClickEntityContext(item, event.player, event.rightClicked, event))
        }
    }

    @EventHandler
    public fun onBreak(event: BlockBreakEvent) {
        val item = event.player.inventory.itemInMainHand.wrap() ?: return
        dispatch(item) {
            it.onBreak(ItemBlockBreakContext(item, event.player, event))
        }
    }

    @EventHandler
    public fun onPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand.wrap() ?: return
        dispatch(item) {
            it.onPlace(ItemBlockPlaceContext(item, event.player, event.blockPlaced.location, event))
        }
    }

    @EventHandler
    public fun onDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack.wrap() ?: return
        dispatch(item) {
            it.onDrop(ItemDropContext(item, event.player, event))
        }
    }

    @EventHandler
    public fun onOffhandSwap(event: PlayerSwapHandItemsEvent) {
        val item = event.offHandItem?.wrap() ?: return
        dispatch(item) {
            it.onOffhandEquip(ItemOffhandEquipContext(item, event.player, event))
        }
    }

    @EventHandler
    public fun onOffhandInventoryPlace(event: InventoryClickEvent) {
        if (event.slot != 40) return
        val player = event.whoClicked as? Player ?: return
        val itemStack = player.itemOnCursor
        if (!itemStack.isCustom) return
        val item = itemStack.wrap() ?: return
        dispatch(item) {
            it.onOffhandEquip(ItemOffhandEquipContext(item, player, event))
            if (event.isCancelled) player.setItemOnCursor(itemStack)
        }
    }

    @EventHandler
    public fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return

        val damager = event.damager as? LivingEntity ?: return
        val victim = event.entity as? LivingEntity ?: return
        val heldItem = if (damager is Player) damager.inventory.itemInMainHand else damager.activeItem
        val item = heldItem.wrap() ?: return

        dispatch(item) {
            it.onDamageEntity(ItemDamageEntityContext(item, damager, victim, event))
        }
    }

    /** Dispatches tick hooks once per server tick. */
    @Periodic(1)
    public fun tickEvents() {
        for (player in onlinePlayers()) {
            for ((slot, itemStack) in player.inventory.withIndex()) {
                val item = itemStack?.wrap() ?: continue
                dispatch(item) {
                    it.onTickInInventory(ItemInventoryTickContext(item, player, slot))
                }
            }

            val mainHand = player.inventory.itemInMainHand.wrap()
            if (mainHand != null) {
                dispatch(mainHand) {
                    it.onTickInEitherHand(ItemHandTickContext(mainHand, player, HandSlot.MAIN_HAND))
                }
            }

            val offHand = player.inventory.itemInOffHand.wrap()
            if (offHand != null) {
                dispatch(offHand) {
                    it.onTickInEitherHand(ItemHandTickContext(offHand, player, HandSlot.OFF_HAND))
                }
            }

            val armorList = player.inventory.run { listOf(helmet, chestplate, leggings, boots) }
            val slotList = ArmorSlot.entries
            for ((index, piece) in armorList.withIndex()) {
                val item = piece?.wrap() ?: continue
                dispatch(item) {
                    it.onTickEquipped(ItemEquippedTickContext(item, player, slotList[index]))
                }
            }
        }
    }

    private fun dispatch(item: CuTItemStack, block: (ItemSystem) -> Unit) {
        ItemSystem.applicableTo(item).forEach(block)
    }
}
