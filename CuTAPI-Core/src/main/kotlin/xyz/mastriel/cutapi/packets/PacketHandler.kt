package xyz.mastriel.cutapi.packets

import org.bukkit.event.EventPriority


@Target(AnnotationTarget.FUNCTION)
internal annotation class PacketHandler(val priority: EventPriority)