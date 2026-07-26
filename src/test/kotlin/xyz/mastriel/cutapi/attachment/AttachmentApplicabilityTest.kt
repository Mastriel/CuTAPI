package xyz.mastriel.cutapi.attachment

import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.player.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.testing.*
import kotlin.test.*

public class AttachmentApplicabilityTest : MockBukkitTest() {
    @BeforeTest
    public fun registerSharedAttachmentSchema() {
        Schema.registerSchema(SharedAttachment)
    }

    @Test
    public fun `an attachment implementing both markers applies to items and players`() {
        val value = SharedAttachment("shared")
        val descriptor = itemDescriptor {
            attach(value)
        }
        val player = server.addPlayer()

        player.setAttachment(value)

        assertEquals(value, descriptor.getAttachment(SharedAttachment))
        assertEquals(value, player.getAttachment(SharedAttachment))
        assertIs<ItemAttachment>(value)
        assertIs<PlayerAttachment>(value)
    }

    @Test
    public fun `persisting an attachment requires explicit schema registration`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            server.addPlayer().setAttachment(UnregisteredAttachment("value"))
        }

        assertContains(exception.message.orEmpty(), "Schema ${UnregisteredAttachment.id} is not registered")
        assertContains(exception.message.orEmpty(), "Schema.modifyRegistry")
    }
}

private data class SharedAttachment(
    val value: String
) : ItemAttachment, PlayerAttachment {
    companion object : Schema<SharedAttachment> by schema(id("test:shared_attachment"), {
        property(SharedAttachment::value, VariantSerializer.String)
    })
}

private data class UnregisteredAttachment(
    val value: String
) : PlayerAttachment {
    companion object : Schema<UnregisteredAttachment> by schema(id("test:unregistered_attachment"), {
        property(UnregisteredAttachment::value, VariantSerializer.String)
    })
}
