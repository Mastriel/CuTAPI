package xyz.mastriel.cutapi.nms

import io.netty.channel.*
import net.minecraft.core.*
import net.minecraft.server.level.*
import org.bukkit.*
import org.bukkit.block.*
import org.bukkit.craftbukkit.entity.*
import org.bukkit.craftbukkit.inventory.*
import org.bukkit.entity.*
import org.bukkit.inventory.*


internal typealias MojangPacket<T> = net.minecraft.network.protocol.Packet<T>
internal typealias MojangItemStack = net.minecraft.world.item.ItemStack

/**
 * Converts a Bukkit Player to a Mojang ServerPlayer
 */
@UsesNMS
internal fun Player.nms() = (this as CraftPlayer).handle!!

/**
 * Converts a Bukkit ItemStack to a Mojang ItemStack
 */
@UsesNMS
internal fun ItemStack.nms(): MojangItemStack = CraftItemStack.asNMSCopy(this)

/**
 * Converts a Mojang ItemStack to a Bukkit ItemStack
 */
@UsesNMS
internal fun MojangItemStack.bukkit(): ItemStack = CraftItemStack.asBukkitCopy(this)

/**
 * Converts a collection to a non-null list
 */
@UsesNMS
internal fun <E> Collection<E>.toNonNullList(): NonNullList<E> {
    val list = NonNullList.create<E>()
    list.addAll(this)
    return list
}

@UsesNMS
internal fun MojangPacket<*>.sendTo(player: Player) {
    player.nms().connection.send(this)
}

@UsesNMS
internal fun ServerPlayer.packetPipeline(): ChannelPipeline {
    return connection.connection.channel.pipeline()
}

@UsesNMS
internal fun BlockPos.bukkitBlock(world: World): Block {
    return world.getBlockAt(x, y, z)
}

