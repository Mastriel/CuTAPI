package xyz.mastriel.cutapi.nms

import org.bukkit.event.*

@Retention(AnnotationRetention.RUNTIME)
public annotation class PacketHandler(val priority: EventPriority = EventPriority.NORMAL)
