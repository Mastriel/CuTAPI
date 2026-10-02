package xyz.mastriel.cutapi.player

import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.player.*
import xyz.mastriel.cutapi.item.events.*
import xyz.mastriel.cutapi.periodic.*
import xyz.mastriel.cutapi.system.*
import xyz.mastriel.cutapi.utils.*

public class PlayerSystemEvents : Listener {
    @EventHandler
    public fun onJoin(event: PlayerJoinEvent) {
        dispatch(event.player) { it.onJoin(PlayerJoinContext(event.player, event)) }
    }

    @EventHandler
    public fun onQuit(event: PlayerQuitEvent) {
        dispatch(event.player) { it.onQuit(PlayerQuitContext(event.player, event)) }
    }

    @EventHandler
    public fun onRespawn(event: PlayerRespawnEvent) {
        dispatch(event.player) { it.onRespawn(PlayerRespawnContext(event.player, event)) }
    }

    @EventHandler
    public fun onDeath(event: PlayerDeathEvent) {
        dispatch(event.player) { it.onDeath(PlayerDeathContext(event.player, event)) }
    }

    @EventHandler
    public fun onObtainItem(event: CustomItemObtainEvent) {
        dispatch(event.player) { it.onObtainItem(PlayerObtainItemContext(event.player, event)) }
    }

    @EventHandler
    public fun onInteract(event: PlayerInteractEvent) {
        dispatch(event.player) {
            when {
                event.action.isLeftClick -> it.onLeftClick(PlayerInteractContext(event.player, event))
                event.action.isRightClick -> it.onRightClick(PlayerInteractContext(event.player, event))
            }
        }
    }

    @EventHandler
    public fun onInteractEntity(event: PlayerInteractEntityEvent) {
        dispatch(event.player) {
            it.onRightClickEntity(PlayerRightClickEntityContext(event.player, event.rightClicked, event))
        }
    }

    @EventHandler
    public fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        dispatch(player) { it.onDamage(PlayerDamageContext(player, event)) }
    }

    @EventHandler
    public fun onDamageEntity(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val victim = event.entity as? LivingEntity ?: return
        dispatch(player) { it.onDamageEntity(PlayerDamageEntityContext(player, victim, event)) }
    }

    @EventHandler
    public fun onBreak(event: BlockBreakEvent) {
        dispatch(event.player) { it.onBreak(PlayerBlockBreakContext(event.player, event)) }
    }

    @EventHandler
    public fun onPlace(event: BlockPlaceEvent) {
        dispatch(event.player) { it.onPlace(PlayerBlockPlaceContext(event.player, event)) }
    }

    @EventHandler
    public fun onDropItem(event: PlayerDropItemEvent) {
        dispatch(event.player) { it.onDropItem(PlayerDropItemContext(event.player, event)) }
    }

    @EventHandler
    public fun onHeldSlotChange(event: PlayerItemHeldEvent) {
        dispatch(event.player) { it.onHeldSlotChange(PlayerHeldSlotChangeContext(event.player, event)) }
    }

    @EventHandler
    public fun onProjectileHit(event: ProjectileHitEvent) {
        val player = event.entity.shooter as? Player ?: return
        dispatch(player) { it.onProjectileHit(PlayerProjectileHitContext(player, event.entity, event)) }
    }

    @EventHandler
    public fun onToggleGlide(event: EntityToggleGlideEvent) {
        val player = event.entity as? Player ?: return
        dispatch(player) { it.onToggleGlide(PlayerToggleGlideContext(player, event)) }
    }

    @Periodic(1)
    public fun tickEvents() {
        onlinePlayers().forEach { player ->
            dispatch(player) { it.onTick(PlayerTickContext(player)) }
        }
    }

    private fun dispatch(player: Player, block: (PlayerSystem) -> Unit) {
        PlayerSystem.applicableTo(player).forEach(block)
    }
}
