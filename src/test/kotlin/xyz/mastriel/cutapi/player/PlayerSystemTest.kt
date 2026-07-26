package xyz.mastriel.cutapi.player

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
    public fun `player systems filter by attachment and sort by priority`() {
        val player = server.addPlayer()
        val general = generalPlayerSystem(
            id("test:player_system/general"),
            RegistryPriority.Low
        )
        val attached = attachmentPlayerSystem(
            TestPlayerAttachment,
            id("test:player_system/attached"),
            RegistryPriority.High
        )
        PlayerSystem.modifyRegistry {
            register(general)
            register(attached)
        }
        PlayerSystem.initialize()

        assertEquals(listOf(general), PlayerSystem.applicableTo(player))

        player.setAttachment(TestPlayerAttachment(1))
        assertEquals(listOf(attached, general), PlayerSystem.applicableTo(player))
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
