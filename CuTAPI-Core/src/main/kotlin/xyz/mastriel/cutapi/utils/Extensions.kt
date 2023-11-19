package xyz.mastriel.cutapi.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.inventory.ItemStack
import java.time.Instant

val String.colored : Component get() =
    Component.empty()
        .decoration(TextDecoration.ITALIC, false)
        .append(LegacyComponentSerializer.legacyAmpersand().deserialize(this))

val ItemStack.chatTooltip : Component get() =
    Component.text()
        .hoverEvent(this)
        .append(this.itemMeta.displayName() ?: Component.translatable(this))
        .build()

val Instant.hasPassed : Boolean get() = this.isBefore(Instant.now())

fun ItemStack.emptyName() : ItemStack {
    this.editMeta { it.displayName("&7".colored) }
    return this
}
fun ItemStack.withName(name: Component) : ItemStack {
    this.editMeta { it.displayName(name) }
    return this
}
fun ItemStack.withLore(lore: List<Component>) : ItemStack {
    this.editMeta { it.lore(lore) }
    return this
}
fun ItemStack.withLore(vararg lore: Component) : ItemStack {
    this.editMeta { it.lore(lore.toList()) }
    return this
}
fun ItemStack.appendLore(vararg lore: Component) : ItemStack {
    this.editMeta {
        val l = it.lore()?.toMutableList() ?: mutableListOf()
        l.addAll(lore)
        it.lore(l)
    }
    return this
}
