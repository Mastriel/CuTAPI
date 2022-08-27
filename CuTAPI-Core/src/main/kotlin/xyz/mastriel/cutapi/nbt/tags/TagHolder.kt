package xyz.mastriel.cutapi.nbt.tags

import de.tr7zw.changeme.nbtapi.NBTContainer
import org.bukkit.OfflinePlayer
import java.util.*

abstract class TagHolder(val container: NBTContainer) {

    protected fun playerTag(key: String, default: OfflinePlayer) =
        NullablePlayerTag(key, this.container, default)
    protected fun nullablePlayerTag(key: String, default: OfflinePlayer?=null) =
        NullablePlayerTag(key, this.container, default)

    protected fun stringTag(key: String, default: String) =
        NotNullTag(key, this.container, String::class, default)
    protected fun nullableStringTag(key: String, default: String?) =
        NullableTag(key, this.container, String::class, default)

    protected fun uuidTag(key: String, default: UUID) =
        NotNullTag(key, this.container, UUID::class, default)
    protected fun nullableUuidTag(key: String, default: UUID?) =
        NullableTag(key, this.container, UUID::class, default)


}