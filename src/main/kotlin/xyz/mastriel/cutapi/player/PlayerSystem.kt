package xyz.mastriel.cutapi.player

import org.bukkit.entity.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.player.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.events.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.system.*

public open class PlayerSystemContext(public val player: Player) {
    public fun <T : PlayerAttachment> attachment(schema: Schema<T>): T =
        player.getAttachment(schema)

    public fun data(schema: Schema<out PlayerAttachment>): TagContainer =
        PlayerAttachmentTagContainer(player, schema.id)
}

public class PlayerJoinContext(player: Player, public val event: PlayerJoinEvent) :
    PlayerSystemContext(player)

public class PlayerQuitContext(player: Player, public val event: PlayerQuitEvent) :
    PlayerSystemContext(player)

public class PlayerRespawnContext(player: Player, public val event: PlayerRespawnEvent) :
    PlayerSystemContext(player)

public class PlayerDeathContext(player: Player, public val event: PlayerDeathEvent) :
    PlayerSystemContext(player)

public class PlayerObtainItemContext(
    player: Player,
    public val event: CustomItemObtainEvent
) : PlayerSystemContext(player)

public class PlayerInteractContext(player: Player, public val event: PlayerInteractEvent) :
    PlayerSystemContext(player)

public class PlayerRightClickEntityContext(
    player: Player,
    public val entity: Entity,
    public val event: PlayerInteractEntityEvent
) : PlayerSystemContext(player)

public class PlayerDamageContext(
    player: Player,
    public val event: EntityDamageEvent
) : PlayerSystemContext(player)

public class PlayerDamageEntityContext(
    player: Player,
    public val victim: LivingEntity,
    public val event: EntityDamageByEntityEvent
) : PlayerSystemContext(player)

public class PlayerBlockBreakContext(player: Player, public val event: BlockBreakEvent) :
    PlayerSystemContext(player)

public class PlayerBlockPlaceContext(player: Player, public val event: BlockPlaceEvent) :
    PlayerSystemContext(player)

public class PlayerDropItemContext(player: Player, public val event: PlayerDropItemEvent) :
    PlayerSystemContext(player)

public class PlayerTickContext(player: Player) : PlayerSystemContext(player)

public interface PlayerSystem : CuTSystem<Player> {
    public fun onJoin(context: PlayerJoinContext) {}
    public fun onQuit(context: PlayerQuitContext) {}
    public fun onRespawn(context: PlayerRespawnContext) {}
    public fun onDeath(context: PlayerDeathContext) {}
    public fun onObtainItem(context: PlayerObtainItemContext) {}
    public fun onLeftClick(context: PlayerInteractContext) {}
    public fun onRightClick(context: PlayerInteractContext) {}
    public fun onRightClickEntity(context: PlayerRightClickEntityContext) {}
    public fun onDamage(context: PlayerDamageContext) {}
    public fun onDamageEntity(context: PlayerDamageEntityContext) {}
    public fun onBreak(context: PlayerBlockBreakContext) {}
    public fun onPlace(context: PlayerBlockPlaceContext) {}
    public fun onDropItem(context: PlayerDropItemContext) {}
    public fun onTick(context: PlayerTickContext) {}

    public companion object : IdentifierRegistry<PlayerSystem>(id("cutapi:registry/player_system"))
}

public fun generalPlayerSystem(
    id: Identifier,
    priority: RegistryPriority = RegistryPriority.Medium,
    block: PlayerSystem.() -> Unit = {}
): PlayerSystem = object : PlayerSystem {
    override val id: Identifier = id
    override val priority: RegistryPriority = priority

    override fun prerequisite(target: Player): Boolean = true

    init {
        block()
    }
}

public fun attachmentPlayerSystem(
    attachment: Schema<out PlayerAttachment>,
    id: Identifier = attachment.id / "system",
    priority: RegistryPriority = RegistryPriority.Medium,
    block: PlayerSystem.() -> Unit = {}
): PlayerSystem = object : PlayerSystem {
    override val id: Identifier = id
    override val priority: RegistryPriority = priority

    override fun prerequisite(target: Player): Boolean =
        target.hasAttachment(attachment)

    init {
        block()
    }
}
