package xyz.mastriel.cutapi.player

import org.bukkit.entity.Projectile
import org.bukkit.entity.EntityType
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.system.*
import xyz.mastriel.cutapi.testing.*
import kotlin.test.*

public class PlayerSystemTest : MockBukkitTest() {
    @BeforeTest
    public fun registerPlayerAttachmentSchemas() {
        Schema.registerSchema(TestPlayerAttachment)
        Schema.registerSchema(TestRepeatablePlayerAttachment)
        Schema.registerSchema(TestIntrinsicPlayerAttachment)
        PlayerSystemTestFixtures.initialize()
        playerSystemCalls.clear()
    }

    @Test
    public fun `player attachments persist and retain their schema type`() {
        val player = server.addPlayer()

        player.setAttachment(TestPlayerAttachment(2))

        assertTrue(player.hasAttachment(TestPlayerAttachment))
        assertEquals(TestPlayerAttachment(2), player.getAttachment(TestPlayerAttachment))
        assertEquals(TestPlayerAttachment(2), PlayerSystemContext(player).attachment(TestPlayerAttachment))

        player.setAttachment(TestPlayerAttachment(7))
        assertEquals(TestPlayerAttachment(7), player.getAttachment(TestPlayerAttachment))

        player.removeAttachment(TestPlayerAttachment)
        assertFalse(player.hasAttachment(TestPlayerAttachment))
        assertNull(player.getAttachmentOrNull(TestPlayerAttachment))
    }

    @Test
    public fun `player attachments use the versioned native PDC layout`() {
        val player = server.addPlayer()

        player.setAttachment(TestPlayerAttachment(9))

        val root = assertNotNull(
            player.persistentDataContainer.get(
                id("cutapi:attachments").toNamespacedKey(),
                PersistentDataType.TAG_CONTAINER
            )
        )
        val schema = assertNotNull(
            root.get(TestPlayerAttachment.id.toNamespacedKey(), PersistentDataType.TAG_CONTAINER)
        )
        val values = assertNotNull(schema.get(id("cutapi:values").toNamespacedKey(), PersistentDataType.LIST.dataContainers()))

        assertEquals(1, root.get(id("cutapi:format_version").toNamespacedKey(), PersistentDataType.INTEGER))
        assertEquals(1, values.size)
        assertEquals(9, values.single().get(id("test:amount").toNamespacedKey(), PersistentDataType.INTEGER))
        assertFalse(schema.has(id("cutapi:count").toNamespacedKey()))
        assertTrue(values.single().keys.none { it.namespace == "cutapi" && it.key.startsWith("variant_") })
    }

    @Test
    public fun `repeatable player attachments can be added independently`() {
        val player = server.addPlayer()

        player.addAttachment(TestRepeatablePlayerAttachment("first"))
        player.addAttachment(TestRepeatablePlayerAttachment("second"))

        assertEquals(
            listOf(
                TestRepeatablePlayerAttachment("first"),
                TestRepeatablePlayerAttachment("second")
            ),
            player.getAttachments(TestRepeatablePlayerAttachment)
        )
    }

    @Test
    public fun `removing an intrinsic attachment restores a fresh provider value`() {
        intrinsicPlayerAttachmentCreations = 0
        val player = server.addPlayer()

        assertTrue(player.hasAttachment(TestIntrinsicPlayerAttachment))
        assertEquals(TestIntrinsicPlayerAttachment(12), player.getAttachment(TestIntrinsicPlayerAttachment))
        assertEquals(1, intrinsicPlayerAttachmentCreations)

        player.setAttachment(TestIntrinsicPlayerAttachment(30))
        assertEquals(TestIntrinsicPlayerAttachment(30), player.getAttachment(TestIntrinsicPlayerAttachment))
        assertEquals(1, intrinsicPlayerAttachmentCreations)

        player.removeAttachment(TestIntrinsicPlayerAttachment)

        assertTrue(player.hasAttachment(TestIntrinsicPlayerAttachment))
        assertEquals(TestIntrinsicPlayerAttachment(12), player.getAttachment(TestIntrinsicPlayerAttachment))
        assertEquals(2, intrinsicPlayerAttachmentCreations)
    }

    @Test
    public fun `player systems filter by attachment and sort by ascending priority`() {
        val player = server.addPlayer()
        assertEquals(listOf(GeneralTestPlayerSystem), PlayerSystem.applicableTo(player))

        player.setAttachment(TestPlayerAttachment(1))
        assertEquals(
            listOf(GeneralTestPlayerSystem, AttachedTestPlayerSystem),
            PlayerSystem.applicableTo(player),
        )
    }

    @Test
    public fun `held slot dispatch selects the event player propagates cancellation and requires attachment`() {
        val attached = server.addPlayer("attached")
        val plain = server.addPlayer("plain")
        attached.setAttachment(TestPlayerAttachment(1))
        val attachedEvent = PlayerItemHeldEvent(attached, 0, 1)
        val plainEvent = PlayerItemHeldEvent(plain, 0, 1)

        PlayerSystemEvents().onHeldSlotChange(attachedEvent)
        PlayerSystemEvents().onHeldSlotChange(plainEvent)

        assertEquals(
            listOf("general:held:attached", "attached:held:attached", "general:held:plain"),
            playerSystemCalls,
        )
        assertTrue(attachedEvent.isCancelled)
        assertFalse(plainEvent.isCancelled)
    }

    @Test
    public fun `projectile hit dispatch selects the player shooter propagates cancellation and requires attachment`() {
        val player = server.addPlayer("shooter")
        player.setAttachment(TestPlayerAttachment(1))
        val projectile = player.world.spawnEntity(player.location, EntityType.SNOWBALL) as Projectile
        projectile.shooter = player
        val event = ProjectileHitEvent(projectile)

        PlayerSystemEvents().onProjectileHit(event)

        assertEquals(
            listOf("general:projectile:shooter", "attached:projectile:shooter"),
            playerSystemCalls,
        )
        assertTrue(event.isCancelled)
    }

    @Test
    public fun `toggle glide dispatch selects the event player propagates cancellation and requires attachment`() {
        val player = server.addPlayer("glider")
        player.setAttachment(TestPlayerAttachment(1))
        val event = EntityToggleGlideEvent(player, true)

        PlayerSystemEvents().onToggleGlide(event)

        assertEquals(
            listOf("general:glide:glider", "attached:glide:glider"),
            playerSystemCalls,
        )
        assertTrue(event.isCancelled)
    }

    @Test
    public fun `player system data is scoped to its attachment schema`() {
        val context = PlayerSystemContext(server.addPlayer())
        val key = id("test:counter")

        context.data(TestPlayerAttachment).setInt(key, 4)

        assertEquals(4, context.data(TestPlayerAttachment).getInt(key))
        assertNull(context.data(TestRepeatablePlayerAttachment).getInt(key))
    }
}

internal data class TestPlayerAttachment(var amount: Int = 0) : PlayerAttachment {
    companion object : Schema<TestPlayerAttachment> by schema(id("test:player_attachment"), {
        property(TestPlayerAttachment::amount, VariantSerializer.Int)
    })
}

@RepeatableAttachment
internal data class TestRepeatablePlayerAttachment(var value: String = "") : PlayerAttachment {
    companion object : Schema<TestRepeatablePlayerAttachment> by schema(id("test:repeatable_player_attachment"), {
        property(TestRepeatablePlayerAttachment::value, VariantSerializer.String)
    })
}

internal var intrinsicPlayerAttachmentCreations: Int = 0

internal data class TestIntrinsicPlayerAttachment(var amount: Int = 0) : PlayerAttachment {
    companion object :
        Schema<TestIntrinsicPlayerAttachment> by schema(id("test:intrinsic_player_attachment"), {
            property(TestIntrinsicPlayerAttachment::amount, VariantSerializer.Int)
        }),
        IntrinsicPlayerAttachmentProvider<TestIntrinsicPlayerAttachment> by
        provideIntrinsicPlayerAttachment({
            intrinsicPlayerAttachmentCreations++
            TestIntrinsicPlayerAttachment(12)
        })
}

private val playerSystemCalls: MutableList<String> = mutableListOf()

private object GeneralTestPlayerSystem : PlayerSystem {
    override val id: Identifier = id("test:player_system/general")
    override val priority: RegistryPriority = RegistryPriority.Low
    override fun prerequisite(target: org.bukkit.entity.Player): Boolean = true

    override fun onHeldSlotChange(context: PlayerHeldSlotChangeContext) {
        playerSystemCalls += "general:held:${context.player.name}"
    }

    override fun onProjectileHit(context: PlayerProjectileHitContext) {
        playerSystemCalls += "general:projectile:${context.player.name}"
    }

    override fun onToggleGlide(context: PlayerToggleGlideContext) {
        playerSystemCalls += "general:glide:${context.player.name}"
    }
}

private object AttachedTestPlayerSystem : PlayerSystem {
    override val id: Identifier = id("test:player_system/attached")
    override val priority: RegistryPriority = RegistryPriority.High
    override fun prerequisite(target: org.bukkit.entity.Player): Boolean =
        target.hasAttachment(TestPlayerAttachment)

    override fun onHeldSlotChange(context: PlayerHeldSlotChangeContext) {
        playerSystemCalls += "attached:held:${context.player.name}"
        context.event.isCancelled = true
    }

    override fun onProjectileHit(context: PlayerProjectileHitContext) {
        playerSystemCalls += "attached:projectile:${context.player.name}"
        context.event.isCancelled = true
    }

    override fun onToggleGlide(context: PlayerToggleGlideContext) {
        playerSystemCalls += "attached:glide:${context.player.name}"
        context.event.isCancelled = true
    }
}

private object PlayerSystemTestFixtures {
    private var initialized: Boolean = false

    fun initialize() {
        if (initialized) return
        PlayerSystem.modifyRegistry {
            register(GeneralTestPlayerSystem)
            register(AttachedTestPlayerSystem)
        }
        PlayerSystem.initialize()
        initialized = true
    }
}
