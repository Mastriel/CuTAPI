package xyz.mastriel.cutapi.nms

import org.bukkit.event.*

@Retention(AnnotationRetention.RUNTIME)
@UsesNMS
public annotation class PacketHandler(val priority: EventPriority = EventPriority.NORMAL)
