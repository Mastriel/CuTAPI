package xyz.mastriel.cutapi.nms

import net.minecraft.network.protocol.Packet
import org.bukkit.event.EventPriority
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
annotation class PacketHandler(val priority: EventPriority = EventPriority.NORMAL)
