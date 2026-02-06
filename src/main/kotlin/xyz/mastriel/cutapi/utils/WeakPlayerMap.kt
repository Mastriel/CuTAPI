package xyz.mastriel.cutapi.utils

import org.bukkit.*
import org.bukkit.entity.*

// A map that only allows for online players to be stored.
public class WeakPlayerMap<V> internal constructor(private val base: MutableMap<Player, V>) : MutableMap<Player, V> {
    override fun put(key: Player, value: V): V? {
        purgeOfflinePlayers()
        return base.put(key, value)
    }


    private fun purgeOfflinePlayers() {
        base.keys.removeIf { !it.isOnline }
    }

    override val entries: MutableSet<MutableMap.MutableEntry<Player, V>>
        get() = purgeOfflinePlayers().let { base.entries }
    override val keys: MutableSet<Player>
        get() = purgeOfflinePlayers().let { base.keys }
    override val size: Int
        get() = purgeOfflinePlayers().let { base.size }
    override val values: MutableCollection<V>
        get() = purgeOfflinePlayers().let { base.values }

    override fun clear() {
        return purgeOfflinePlayers().let { base.clear() }
    }

    override fun isEmpty(): Boolean {
        return purgeOfflinePlayers().let { base.isEmpty() }
    }

    override fun remove(key: Player): V? {
        return purgeOfflinePlayers().let { base.remove(key) }
    }

    override fun putAll(from: Map<out Player, V>) {
        return purgeOfflinePlayers().let { base.putAll(from) }
    }

    override fun get(key: Player): V? {
        return purgeOfflinePlayers().let { base[key] }
    }

    override fun containsValue(value: V): Boolean {
        return purgeOfflinePlayers().let { base.containsValue(value) }
    }

    override fun containsKey(key: Player): Boolean {
        return purgeOfflinePlayers().let { base.containsKey(key) }
    }
}

public fun <V> weakPlayerMapOf(): WeakPlayerMap<V> = WeakPlayerMap(mutableMapOf())

public fun <V> weakPlayerMapOf(vararg pairs: Pair<Player, V>): WeakPlayerMap<V> = WeakPlayerMap(mutableMapOf(*pairs))

public fun <V> weakPlayerMapOf(map: Map<Player, V>): WeakPlayerMap<V> = WeakPlayerMap(map.toMutableMap())

public fun <V> MutableMap<Player, V>.toWeakPlayerMap(): WeakPlayerMap<V> = WeakPlayerMap(this)

@JvmName("toWeakPlayerMapFromOfflinePlayer")
public fun <V> MutableMap<OfflinePlayer, V>.toWeakPlayerMap(): WeakPlayerMap<V> =
    WeakPlayerMap(this.filterKeys { it.isOnline }.mapKeys { it.key.player!! }.toMutableMap())
