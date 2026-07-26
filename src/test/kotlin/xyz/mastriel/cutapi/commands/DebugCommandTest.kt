@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands

import com.mojang.brigadier.*
import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.exceptions.*
import com.mojang.brigadier.tree.*
import io.papermc.paper.command.brigadier.*
import kotlinx.serialization.json.*
import net.kyori.adventure.text.*
import net.kyori.adventure.text.event.*
import net.kyori.adventure.text.format.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.testing.*
import kotlin.test.*

public class DebugCommandTest : MockBukkitTest() {
    @BeforeTest
    public fun registerCommandSchemas() {
        Schema.registerSchema(CommandItemAttachment)
        Schema.registerSchema(CommandPlayerAttachment)
        Schema.registerSchema(CommandSharedAttachment)
        Schema.registerSchema(CommandData)
    }

    @Test
    public fun `inspect registry accepts an optional registry id`() {
        assertNotNull(InspectRegistryCommand.command)
        assertNotNull(InspectRegistryCommand.child("registry_id").command)
    }

    @Test
    public fun `inspect enum accepts an enum catalog key`() {
        assertNotNull(InspectEnumCommand.child("enum_key").command)
    }

    @Test
    public fun `attachment command exposes the requested grammar`() {
        val targetNodes = listOf(
            AttachmentsCommand.child("player").child("player"),
            AttachmentsCommand.child("helditem")
        )
        for (targetNode in targetNodes) {
            assertNotNull(targetNode.child("view").command)
            assertNotNull(targetNode.child("set").child("id").child("json").command)
            assertNotNull(targetNode.child("remove").child("id").command)
            assertNotNull(
                targetNode.child("update")
                    .child("id")
                    .child("key")
                    .child("value")
                    .command
            )
        }
    }

    @Test
    public fun `attachment id arguments reject schemas for other data`() {
        CommandItemAttachment.id
        CommandPlayerAttachment.id
        CommandSharedAttachment.id
        CommandData.id
        val playerIdNode = AttachmentsCommand
            .child("player")
            .child("player")
            .child("set")
            .child("id")
        val itemIdNode = AttachmentsCommand
            .child("helditem")
            .child("set")
            .child("id")
        @Suppress("UNCHECKED_CAST")
        val playerType =
            assertIs<ArgumentCommandNode<CommandSourceStack, *>>(playerIdNode).type as ArgumentType<Schema<*>>
        @Suppress("UNCHECKED_CAST")
        val itemType =
            assertIs<ArgumentCommandNode<CommandSourceStack, *>>(itemIdNode).type as ArgumentType<Schema<*>>

        assertEquals(
            CommandPlayerAttachment.id,
            playerType.parse(StringReader(CommandPlayerAttachment.id.toString())).id
        )
        assertEquals(
            CommandItemAttachment.id,
            itemType.parse(StringReader(CommandItemAttachment.id.toString())).id
        )
        assertEquals(
            CommandSharedAttachment.id,
            playerType.parse(StringReader(CommandSharedAttachment.id.toString())).id
        )
        assertEquals(
            CommandSharedAttachment.id,
            itemType.parse(StringReader(CommandSharedAttachment.id.toString())).id
        )
        assertFailsWith<CommandSyntaxException> {
            playerType.parse(StringReader(CommandItemAttachment.id.toString()))
        }
        assertFailsWith<CommandSyntaxException> {
            itemType.parse(StringReader(CommandPlayerAttachment.id.toString()))
        }
        assertFailsWith<CommandSyntaxException> {
            itemType.parse(StringReader(CommandData.id.toString()))
        }
    }

    @Test
    public fun `debug JSON uses the resource inspector key and value colors`() {
        val component = buildJsonObject {
            put("name", "value")
            putJsonObject("nested") {
                put("count", 3)
            }
        }.debugJsonComponent()
        val segments = component.textSegments()

        assertTrue(
            segments.any {
                it.text == """"name"""" &&
                    it.color == ResourceInspector.PropertyKey.textColor
            }
        )
        assertTrue(
            segments.any {
                it.text == """"value"""" &&
                    it.color == ResourceInspector.PropertyValue.textColor
            }
        )
        assertTrue(
            segments.any {
                it.text == """"count"""" &&
                    it.color == ResourceInspector.PropertyKey.textColor
            }
        )
        assertTrue(
            segments.any {
                it.text == "3" &&
                    it.color == ResourceInspector.PropertyValue.textColor
            }
        )
    }

    @Test
    public fun `debug JSON displays schema types before their objects`() {
        val component = buildJsonObject {
            put(SCHEMA_TYPE_DISCRIMINATOR, "cutapi:outer")
            put("name", "value")
            putJsonObject("nested") {
                put(SCHEMA_TYPE_DISCRIMINATOR, "cutapi:inner")
                put("count", 3)
            }
        }.debugJsonComponent()
        val segments = component.textSegments()

        assertTrue(
            segments.any {
                it.text == "<cutapi:outer> " && it.color == NamedTextColor.AQUA
            }
        )
        assertTrue(
            segments.any {
                it.text == "<cutapi:inner> " && it.color == NamedTextColor.AQUA
            }
        )
        assertFalse(segments.any { it.text == """"$SCHEMA_TYPE_DISCRIMINATOR"""" })
    }

    @Test
    public fun `suppressed intrinsic item attachments remain visible`() {
        val attachment = CommandItemAttachment("default")

        val entries = itemAttachmentCommandEntries(
            intrinsic = listOf(attachment),
            overlay = emptyList(),
            suppressed = setOf(CommandItemAttachment.id)
        )

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertSame(attachment, entry.attachment)
        assertTrue(entry.intrinsic)
        assertTrue(entry.suppressed)
    }

    @Test
    public fun `suppressed intrinsic attachment entries use a red cross`() {
        val component = debugEntryComponent(
            id = CommandItemAttachment.id,
            debugView = CommandItemAttachment,
            value = CommandItemAttachment("default"),
            intrinsic = true,
            suppressed = true
        )
        val redText = component.textSegments()
            .filter { it.color == NamedTextColor.RED }
            .joinToString(separator = "") { it.text }

        assertTrue("❌" in redText)
        assertTrue(CommandItemAttachment.id.toString() in redText)
    }

    @Test
    public fun `attachment JSON errors use the requested line colors`() {
        val component = SchemaJsonException(
            errorMessage = "Invalid attachment value.",
            expected = "integer",
            found = """"four"""",
            availableEntries = SerializerAvailableEntries.LiteralValues(listOf("one", "two"))
        ).attachmentErrorComponent()
        val segments = component.textSegments()

        assertTrue(segments.any { it.text == "Invalid attachment value." && it.color == NamedTextColor.RED })
        assertTrue(segments.any { it.text == "Expected: " && it.color == NamedTextColor.RED })
        assertTrue(segments.any { it.text == "integer" && it.color == NamedTextColor.YELLOW })
        assertTrue(segments.any { it.text == "Found: " && it.color == NamedTextColor.RED })
        assertTrue(segments.any { it.text == """"four"""" && it.color == NamedTextColor.YELLOW })
        assertTrue(segments.any { it.text == "Available Entries: " && it.color == NamedTextColor.RED })
        assertTrue(segments.any { it.text == "{ one, two }" && it.color == NamedTextColor.YELLOW })
    }

    @Test
    public fun `long enum entries link to inspect enum`() {
        val entries = EnumEntryCatalog.register(
            CommandLongEnum::class,
            CommandLongEnum.entries.map { it.name }
        )
        val component = SchemaJsonException(
            errorMessage = "Invalid attachment value.",
            expected = "CommandLongEnum",
            found = """"MISSING"""",
            availableEntries = entries
        ).attachmentErrorComponent()
        val clickable = component.allComponents()
            .first { it.clickEvent()?.action() == ClickEvent.Action.RUN_COMMAND }

        assertEquals(ClickEvent.runCommand("/inspectenum ${entries.key}"), clickable.clickEvent())
        assertTrue(
            clickable.textSegments().any {
                it.text == "{ ... }" && it.color == NamedTextColor.GRAY
            }
        )
    }

    @Test
    public fun `identifier entries link to their registry`() {
        val registryId = id("test:identifier_entries")
        val component = SchemaJsonException(
            errorMessage = "Invalid attachment value.",
            expected = "Identifier",
            found = """"test:missing"""",
            availableEntries = SerializerAvailableEntries.RegistryValues(registryId)
        ).attachmentErrorComponent()
        val clickable = component.allComponents()
            .first { it.clickEvent()?.action() == ClickEvent.Action.RUN_COMMAND }

        assertEquals(ClickEvent.runCommand("/inspectregistry $registryId"), clickable.clickEvent())
        assertTrue(
            clickable.textSegments().any {
                it.text == "{ ... }" && it.color == NamedTextColor.GRAY
            }
        )
    }
}

private fun <S> CommandNode<S>.child(name: String): CommandNode<S> =
    assertNotNull(getChild(name), "Expected '$name' below '$this'")

private data class TextSegment(
    val text: String,
    val color: TextColor?
)

private fun Component.textSegments(): List<TextSegment> = buildList {
    val component = this@textSegments
    if (component is TextComponent && component.content().isNotEmpty()) {
        add(TextSegment(component.content(), component.color()))
    }
    component.children().flatMapTo(this) { it.textSegments() }
}

private fun Component.allComponents(): List<Component> =
    listOf(this) + children().flatMap { it.allComponents() }

private enum class CommandLongEnum {
    ONE,
    TWO,
    THREE,
    FOUR,
    FIVE,
    SIX,
    SEVEN,
    EIGHT,
    NINE,
    TEN,
    ELEVEN
}

private data class CommandItemAttachment(val value: String) : ItemAttachment {
    companion object : Schema<CommandItemAttachment> by schema(id("test:command_item_attachment"), {
        property(CommandItemAttachment::value, VariantSerializer.String)
    })
}

private data class CommandPlayerAttachment(val value: String) : PlayerAttachment {
    companion object : Schema<CommandPlayerAttachment> by schema(id("test:command_player_attachment"), {
        property(CommandPlayerAttachment::value, VariantSerializer.String)
    })
}

private data class CommandSharedAttachment(val value: String) : ItemAttachment, PlayerAttachment {
    companion object : Schema<CommandSharedAttachment> by schema(id("test:command_shared_attachment"), {
        property(CommandSharedAttachment::value, VariantSerializer.String)
    })
}

private data class CommandData(val value: String) {
    companion object : Schema<CommandData> by schema(id("test:command_data"), {
        property(CommandData::value, VariantSerializer.String)
    })
}
