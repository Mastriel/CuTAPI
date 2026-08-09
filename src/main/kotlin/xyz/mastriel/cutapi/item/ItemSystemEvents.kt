@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.item

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent
import org.bukkit.*
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.*
import org.bukkit.event.player.*
import org.bukkit.event.world.*
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.item.events.*
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.periodic.*
import xyz.mastriel.cutapi.system.*
import xyz.mastriel.cutapi.utils.*
import java.util.*

public class ItemSystemEvents : Listener {
    private val pendingCreativeSlotSyncs: MutableSet<Pair<UUID, Int>> = mutableSetOf()

    /**
     * Creative inventory stacks originate on the client. Paper accepts the serverbound stack and
     * marks its slot as synchronized, so it does not normally pass through the clientbound item
     * codec where render systems run. Resynchronize only the changed slot on the next tick, after
     * the accepted stack has been installed in the authoritative inventory. A full inventory sync
     * would incorrectly clear the creative client's locally managed cursor.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onCreativeInventory(event: InventoryCreativeEvent) {
        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot
        val pendingSync = player.uniqueId to slot
        if (slot !in 1..45 || !pendingCreativeSlotSyncs.add(pendingSync)) return

        Bukkit.getScheduler().runTask(xyz.mastriel.cutapi.Plugin, Runnable {
            pendingCreativeSlotSyncs.remove(pendingSync)
            if (!player.isOnline) return@Runnable

            val serverPlayer = player.nms()
            val inventoryMenu = serverPlayer.inventoryMenu
            if (slot !in inventoryMenu.slots.indices) return@Runnable

            ItemMaterializationManager.ensureReconciled(
                CraftItemStack.asCraftMirror(inventoryMenu.getSlot(slot).item),
            )

            serverPlayer.connection.send(
                ClientboundContainerSetSlotPacket(
                    inventoryMenu.containerId,
                    inventoryMenu.incrementStateId(),
                    slot,
                    inventoryMenu.getSlot(slot).item,
                ),
            )
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onInventoryClickMaterialization(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory
        scheduleInventoryBoundary(player, top)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onInventoryDragMaterialization(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory
        scheduleInventoryBoundary(player, top)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public fun onJoin(event: PlayerJoinEvent) {
        ItemMaterializationManager.reconcileInventory(event.player.inventory)
        ItemMaterializationManager.reconcileInventory(event.player.enderChest)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onInventoryOpen(event: InventoryOpenEvent) {
        ItemMaterializationManager.reconcileInventory(event.inventory)
        val player = event.player as? Player ?: return
        ItemMaterializationManager.reconcileInventory(player.inventory)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onItemSpawn(event: ItemSpawnEvent) {
        val stack = event.entity.itemStack
        if (ItemMaterializationManager.ensureReconciled(stack)) event.entity.itemStack = stack
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public fun onPickupBoundary(event: PlayerAttemptPickupItemEvent) {
        val stack = event.item.itemStack
        if (ItemMaterializationManager.ensureReconciled(stack)) event.item.itemStack = stack
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public fun onInventoryPickup(event: InventoryPickupItemEvent) {
        val stack = event.item.itemStack
        if (ItemMaterializationManager.ensureReconciled(stack)) event.item.itemStack = stack
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public fun onInventoryMove(event: InventoryMoveItemEvent) {
        val stack = event.item
        if (ItemMaterializationManager.ensureReconciled(stack)) event.item = stack
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onLootGenerate(event: LootGenerateEvent) {
        val loot = event.loot.map(ItemStack::clone)
        var changed = false
        loot.forEach { changed = ItemMaterializationManager.ensureReconciled(it) || changed }
        if (changed) event.setLoot(loot)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public fun onPrepareResult(event: PrepareInventoryResultEvent) {
        val result = event.result ?: return
        if (ItemMaterializationManager.ensureReconciled(result)) event.result = result
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onCook(event: BlockCookEvent) {
        val result = event.result
        if (ItemMaterializationManager.ensureReconciled(result)) event.result = result
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onBrew(event: BrewEvent) {
        event.results.forEach(ItemMaterializationManager::ensureReconciled)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public fun onEquipmentChanged(event: EntityEquipmentChangedEvent) {
        Bukkit.getScheduler().runTask(xyz.mastriel.cutapi.Plugin, Runnable {
            ItemMaterializationManager.reconcileEquipment(event.entity)
        })
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public fun onChunkLoad(event: ChunkLoadEvent) {
        ItemMaterializationManager.reconcileChunk(event.chunk)
    }

    @EventHandler
    public fun onObtain(event: CustomItemObtainEvent) {
        dispatch(event.item) {
            it.onObtain(ItemObtainContext(event.item, event.player, event))
        }
    }

    @EventHandler
    public fun onInteract(event: PlayerInteractEvent) {
        val item = event.player.inventory.itemInMainHand.wrap()
        dispatch(item) {
            when {
                event.action.isLeftClick -> it.onLeftClick(ItemInteractContext(item, event.player, event))
                event.action.isRightClick -> it.onRightClick(ItemInteractContext(item, event.player, event))
            }
        }
    }

    @EventHandler
    public fun onInteract(event: PlayerInteractEntityEvent) {
        val item = event.player.inventory.itemInMainHand.wrap()
        dispatch(item) {
            it.onRightClickEntity(ItemRightClickEntityContext(item, event.player, event.rightClicked, event))
        }
    }

    @EventHandler
    public fun onBreak(event: BlockBreakEvent) {
        val item = event.player.inventory.itemInMainHand.wrap()
        dispatch(item) {
            it.onBreak(ItemBlockBreakContext(item, event.player, event))
        }
    }

    @EventHandler
    public fun onPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand.wrap()
        dispatch(item) {
            it.onPlace(ItemBlockPlaceContext(item, event.player, event.blockPlaced.location, event))
        }
    }

    @EventHandler
    public fun onDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack.wrap()
        dispatch(item) {
            it.onDrop(ItemDropContext(item, event.player, event))
        }
    }

    @EventHandler
    public fun onOffhandSwap(event: PlayerSwapHandItemsEvent) {
        val item = event.offHandItem.wrap()
        dispatch(item) {
            it.onOffhandEquip(ItemOffhandEquipContext(item, event.player, event))
        }
    }

    @EventHandler
    public fun onOffhandInventoryPlace(event: InventoryClickEvent) {
        if (event.slot != 40) return
        val player = event.whoClicked as? Player ?: return
        val itemStack = player.itemOnCursor
        val item = itemStack.wrap()
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
        val item = heldItem.wrap()

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
            dispatch(mainHand) {
                it.onTickInEitherHand(ItemHandTickContext(mainHand, player, HandSlot.MainHand))
            }

            val offHand = player.inventory.itemInOffHand.wrap()
            dispatch(offHand) {
                it.onTickInEitherHand(ItemHandTickContext(offHand, player, HandSlot.OffHand))
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
        if (item.handle.type.isAir || item.handle.amount <= 0) return
        ItemMaterializationManager.ensureReconciled(item.handle)
        ItemSystem.applicableTo(item).forEach(block)
    }

    private fun scheduleInventoryBoundary(player: Player, top: Inventory) {
        Bukkit.getScheduler().runTask(xyz.mastriel.cutapi.Plugin, Runnable {
            if (!player.isOnline) return@Runnable
            ItemMaterializationManager.reconcileInventory(player.inventory)
            ItemMaterializationManager.reconcileInventory(top)
            val cursor = player.itemOnCursor
            if (ItemMaterializationManager.ensureReconciled(cursor)) player.setItemOnCursor(cursor)
        })
    }
}
