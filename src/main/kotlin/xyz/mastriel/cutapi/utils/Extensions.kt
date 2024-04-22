package xyz.mastriel.cutapi.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.inventory.ItemStack
import java.time.Instant
import kotlin.time.Duration

val String.colored: Component
    get() =
        Component.empty()
            .decoration(TextDecoration.ITALIC, false)
            .append(LegacyComponentSerializer.legacyAmpersand().deserialize(this))

val ItemStack.chatTooltip: Component
    get() =
        Component.text()
            .hoverEvent(this)
            .append(this.itemMeta?.displayName() ?: Component.translatable(this))
            .build()

val Instant.hasPassed: Boolean get() = this.isBefore(Instant.now())

fun ItemStack.emptyName(): ItemStack {
    this.editMeta { it.displayName("&7".colored) }
    return this
}

fun ItemStack.withName(name: Component): ItemStack {
    this.editMeta { it.displayName(name) }
    return this
}

fun ItemStack.withLore(lore: List<Component>): ItemStack {
    this.editMeta { it.lore(lore) }
    return this
}

fun ItemStack.withLore(vararg lore: Component): ItemStack {
    this.editMeta { it.lore(lore.toList()) }
    return this
}

fun ItemStack.appendLore(vararg lore: Component): ItemStack {
    this.editMeta {
        val l = it.lore()?.toMutableList() ?: mutableListOf()
        l.addAll(lore)
        it.lore(l)
    }
    return this
}


val Duration.inWholeTicks: Long get() = this.inWholeMilliseconds / 50

fun <T> List<List<T>>.trim(include: (T) -> Boolean): List<List<T>> {
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