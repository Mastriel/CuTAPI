package xyz.mastriel.exampleplugin.components

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.mapSerialDescriptor
import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.components.ItemComponent
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.identifiable
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.serializers.UUIDSerializer
import xyz.mastriel.exampleplugin.Plugin
import java.util.UUID

class Soulbound private constructor() : ItemComponent(id) {
    companion object : Identifiable by identifiable(Plugin, "soulbound")

    constructor(player: OfflinePlayer? = null) : this() {
        owner = player
    }

    var owner by nullablePlayerTag("Owner")

    val uuidToStringSerializer = MapSerializer(UUIDSerializer, String.serializer())
    var complex by objectTag("ComplexTest", mutableMapOf(), uuidToStringSerializer)

    override fun getLore(cuTItemStack: CuTItemStack, viewer: Player): Component {
        if (owner == null) return "Soulbound (???)".colored
        return "Soulbound (${owner?.name})".colored
    }

    override fun onInteract(item: CuTItemStack, event: PlayerInteractEvent) {
        event.player.sendMessage("This item has bound itself to you!".colored)
        owner = event.player
        val copy = complex.toMutableMap()
        copy.put(UUID.randomUUID(), "String")
        complex = copy
    }


}