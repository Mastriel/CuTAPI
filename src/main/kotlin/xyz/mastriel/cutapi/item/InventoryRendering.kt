package xyz.mastriel.cutapi.item

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.PlayerInventory

/**
 * Forces every current player viewer to receive a fresh rendering of this inventory.
 *
 * The authoritative inventory and its stacks are not modified. Each inventory sync passes through
 * CuTAPI's clientbound item projection, so render systems run independently for every viewer.
 * Inventories without an active player viewer require no work; they render normally when opened.
 *
 * This operation must run on the primary server thread.
 */
public fun Inventory.forceRerender() {
    check(Bukkit.isPrimaryThread()) {
        "Inventory rendering must run on the primary server thread."
    }

    val players = buildSet {
        viewers.filterIsInstanceTo(this, Player::class.java)
        if (this@forceRerender is PlayerInventory) {
            (this@forceRerender.holder as? Player)?.let(::add)
        }
    }
    players.filter(Player::isOnline).forEach(Player::updateInventory)
}
