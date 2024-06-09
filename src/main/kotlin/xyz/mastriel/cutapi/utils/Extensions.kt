package xyz.mastriel.cutapi.utils

import net.kyori.adventure.text.*
import net.kyori.adventure.text.format.*
import net.kyori.adventure.text.serializer.legacy.*
import org.bukkit.inventory.*
import org.bukkit.inventory.meta.*
import xyz.mastriel.cutapi.item.behaviors.*
import java.time.*
import kotlin.math.*
import kotlin.time.Duration

public val String.colored: Component
    get() =
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(LegacyComponentSerializer.legacyAmpersand().deserialize(this))

public val ItemStack.chatTooltip: Component
    get() {
        // fixes a problem with Adventure not processing tool components properly
        val item = stripToolData(clone())

        return Component.text()
            .hoverEvent(item)
            .append(item.itemMeta?.displayName() ?: item.itemMeta.itemNameOrNull() ?: Component.translatable(item))
            .build()
    }

private fun ItemMeta.itemNameOrNull(): Component? {
    return if (hasItemName()) itemName() else null
}

public val Instant.hasPassed: Boolean get() = this.isBefore(Instant.now())

public fun ItemStack.emptyName(): ItemStack {
    this.editMeta { it.displayName("&7".colored) }
    return this
}

public fun ItemStack.withName(name: Component): ItemStack {
    this.editMeta { it.displayName(name) }
    return this
}

public fun ItemStack.withLore(lore: List<Component>): ItemStack {
    this.editMeta { it.lore(lore) }
    return this
}

public fun ItemStack.withLore(vararg lore: Component): ItemStack {
    this.editMeta { it.lore(lore.toList()) }
    return this
}

public fun ItemStack.appendLore(vararg lore: Component): ItemStack {
    this.editMeta {
        val l = it.lore()?.toMutableList() ?: mutableListOf()
        l.addAll(lore)
        it.lore(l)
    }
    return this
}


public val Duration.inWholeTicks: Long get() = ceil(this.inWholeMilliseconds / 50.0).toLong()

public fun <T> List<List<T>>.trim(include: (T) -> Boolean): List<List<T>> {
    val rows = size
    val cols = this[0].size

    var rmin = 0
    var rmax = rows
    var cmin = 0
    var cmax = cols

    for (i in 0 until rows) {
        if (this[i].any(include)) {
            rmin = i
            break
        }
    }

    for (i in rows - 1 downTo 0) {
        if (this[i].any(include)) {
            rmax = i + 1
            break
        }
    }

    for (i in 0 until cols) {
        if (this.any { include(it[i]) }) {
            cmin = i
            break
        }
    }

    for (i in cols - 1 downTo 0) {
        if (this.any { include(it[i]) }) {
            cmax = i + 1
            break
        }
    }


    return subList(rmin, rmax)
        .map { it.subList(cmin, cmax) }
}