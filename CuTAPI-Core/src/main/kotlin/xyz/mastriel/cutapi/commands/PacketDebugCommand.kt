package xyz.mastriel.cutapi.commands

import com.comphenix.protocol.PacketType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.pdc.tags.getBoolean
import xyz.mastriel.cutapi.pdc.tags.getObject
import xyz.mastriel.cutapi.pdc.tags.setBoolean
import xyz.mastriel.cutapi.pdc.tags.setObject
import xyz.mastriel.cutapi.player.tags
import xyz.mastriel.cutapi.utils.colored

object PacketDebugCommand : Command("packetdebug") {

    override fun getPermission() = "cutapi.admin.packetdebug"

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>?): MutableList<String> {
        val position = (args?.size ?: 0) - 1
        return when (position) {
            0 -> listOf("enable", "disable", "list-disabled", "silence", "unsilence")
            1 -> {
                return when (args!![1]) {
                    "silence" -> PacketType.values().map(PacketType::name)
                    "unsilence" -> getSilencedTypes(sender as Player).map(PacketType::name)
                    else -> emptyList()
                }.toMutableList()
            }
            else -> emptyList()
        }.toMutableList()
    }

    override fun execute(sender: CommandSender, label: String, args: Array<out String>?): Boolean {
        if (sender !is Player) return false
        val subcommand = args?.getOrNull(0)
        if (subcommand == null) {
            sender.sendMessage("&c/packetdebug <enable/disable/list-disabled/silence/unsilence> [type]".colored)
            return true
        }

        fun getPacketType() : PacketType? {
            val type = args.getOrNull(1)
            if (type == null) {
                sender.sendMessage("&cSpecify a type!".colored)
                return null
            }
            val packet = PacketType.fromName(type)?.firstOrNull()
            if (packet == null) {
                sender.sendMessage("&cInvalid packet type!".colored)
                return null
            }
            return packet
        }

        when (subcommand) {
            "enable" -> {
                setPacketDebug(sender, true)
                sender.sendMessage("&#e1a8ffEnabled packet debug.".colored)
            }
            "disable" -> {
                setPacketDebug(sender, false)
                sender.sendMessage("&#e1a8ffDisabled packet debug.".colored)
            }
            "list-disabled" -> {
                val silencedTypes = getSilencedTypes(sender)
                    .joinToString("\n") { "&#e1a8ff${it.name()}" }

                sender.sendMessage(silencedTypes.colored)
            }
            "silence" -> {
                val type = getPacketType() ?: return true
                addSilencedType(sender, type)
                sender.sendMessage("&#e1a8ffSilenced ${type.name()}".colored)
            }
            "unsilence" -> {
                val type = getPacketType() ?: return true
                removeSilencedType(sender, type)
                sender.sendMessage("&#e1a8ffUnsilenced ${type.name()}".colored)
            }
            else -> {
                sender.sendMessage("&c/packetdebug <enable/disable/list-disabled/silence/unsilence> [type]".colored)
            }
        }
        return true
    }

    fun getSilencedTypes(player: Player) : List<PacketType> {
        return player.tags.getObject(
            "packet_debug_silenced",
            ListSerializer(PacketTypeSerializer)
        ) ?: emptyList()
    }

    fun addSilencedType(player: Player, packetType: PacketType) {
        player.tags.setObject(
            "packet_debug_silenced",
            getSilencedTypes(player).toMutableList().apply { add(packetType) },
            ListSerializer(PacketTypeSerializer)
        )
    }

    fun removeSilencedType(player: Player, packetType: PacketType) {
        player.tags.setObject(
            "packet_debug_silenced",
            getSilencedTypes(player).toMutableList().filter { it != packetType },
            ListSerializer(PacketTypeSerializer)
        )
    }

    fun setPacketDebug(player: Player, state: Boolean) {
        player.tags.setBoolean("packet_debug_enabled", state)
        println(player.tags.getBoolean("packet_debug_enabled"))
    }

    fun getPacketDebug(player: Player) : Boolean {
        return player.tags.getBoolean("packet_debug_enabled") ?: false
    }

    object PacketTypeSerializer : KSerializer<PacketType> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("packet_type", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): PacketType {
            return PacketType.fromName(decoder.decodeString()).first()
        }

        override fun serialize(encoder: Encoder, value: PacketType) {
            encoder.encodeString(value.name())
        }

    }
}