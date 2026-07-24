package xyz.mastriel.cutapi.player

import org.bukkit.entity.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.system.*
import java.lang.reflect.*
import java.util.*
import kotlin.test.*

public class PlayerSystemTest {
    @Test
    public fun `player attachments persist and retain their schema type`() {
        val player = testPlayer()

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
        val player = testPlayer()

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
        val player = testPlayer()

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
        val player = testPlayer()
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
        val context = PlayerSystemContext(testPlayer())
        val key = id("test:counter")

        context.data(TestPlayerAttachment).setInt(key, 4)

        assertEquals(4, context.data(TestPlayerAttachment).getInt(key))
        assertNull(context.data(TestRepeatablePlayerAttachment).getInt(key))
    }
}

internal data class TestPlayerAttachment(var amount: Int = 0) : Attachment {
    companion object : Schema<TestPlayerAttachment> by schema(id("test:player_attachment"), {
        property(TestPlayerAttachment::amount, VariantSerializer.Int)
    })
}

@RepeatableAttachment
internal data class TestRepeatablePlayerAttachment(var value: String = "") : Attachment {
    companion object : Schema<TestRepeatablePlayerAttachment> by schema(id("test:repeatable_player_attachment"), {
        property(TestRepeatablePlayerAttachment::value, VariantSerializer.String)
    })
}

internal var intrinsicPlayerAttachmentCreations: Int = 0

internal data class TestIntrinsicPlayerAttachment(var amount: Int = 0) : Attachment {
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

private fun testPlayer(): Player {
    val container = memoryPersistentDataContainer()
    val uniqueId = UUID.randomUUID()
    return Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java)
    ) { proxy, method, arguments ->
        when (method.name) {
            "getPersistentDataContainer" -> container
            "getUniqueId" -> uniqueId
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === arguments?.firstOrNull()
            "toString" -> "TestPlayer($uniqueId)"
            else -> defaultValue(method.returnType)
        }
    } as Player
}

private fun memoryPersistentDataContainer(): PersistentDataContainer {
    val values = mutableMapOf<Any, Any>()
    val adapterContext = Proxy.newProxyInstance(
        PersistentDataAdapterContext::class.java.classLoader,
        arrayOf(PersistentDataAdapterContext::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "newPersistentDataContainer" -> memoryPersistentDataContainer()
            else -> defaultValue(method.returnType)
        }
    } as PersistentDataAdapterContext

    return Proxy.newProxyInstance(
        PersistentDataContainer::class.java.classLoader,
        arrayOf(PersistentDataContainer::class.java)
    ) { proxy, method, arguments ->
        when (method.name) {
            "set" -> {
                values[arguments!![0]] = arguments[2]
                null
            }

            "get" -> values[arguments!![0]]
            "has" -> values.containsKey(arguments!![0])
            "remove" -> {
                values.remove(arguments!![0])
                null
            }

            "getKeys" -> values.keys.toSet()
            "isEmpty" -> values.isEmpty()
            "getAdapterContext" -> adapterContext
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === arguments?.firstOrNull()
            "toString" -> "MemoryPersistentDataContainer$values"
            else -> defaultValue(method.returnType)
        }
    } as PersistentDataContainer
}

private fun defaultValue(type: Class<*>): Any? = when (type) {
    java.lang.Boolean.TYPE -> false
    java.lang.Byte.TYPE -> 0.toByte()
    java.lang.Short.TYPE -> 0.toShort()
    java.lang.Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0f
    java.lang.Double.TYPE -> 0.0
    java.lang.Character.TYPE -> '\u0000'
    else -> null
}
