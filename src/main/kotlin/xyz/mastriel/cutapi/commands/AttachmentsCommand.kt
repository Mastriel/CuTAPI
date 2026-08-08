@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.commands

import com.mojang.brigadier.arguments.*
import io.papermc.paper.command.brigadier.argument.*
import net.kyori.adventure.text.*
import net.kyori.adventure.text.event.*
import net.kyori.adventure.text.format.*
import org.bukkit.entity.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.commands.brigadier.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.player.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import kotlin.reflect.*
import kotlin.reflect.full.*

internal val AttachmentsCommand = command("attachments") {
    requires { sender.hasPermission("cutapi.admin.debug") }

    subcommand("player") {
        argument("player", ArgumentTypes.player()) { playerSelector ->
            attachmentActions(PlayerAttachment::class) {
                PlayerAttachmentCommandTarget(playerSelector().resolve(source).single())
            }
        }
    }

    subcommand("helditem") {
        requires { sender is Player }
        attachmentActions(ItemAttachment::class) {
            val player = sender as Player
            val item = player.inventory.itemInMainHand.wrap()
                ?: error("The held item is not a custom item")
            ItemAttachmentCommandTarget(item)
        }
    }
}

private interface AttachmentCommandTarget {
    val description: String

    fun entries(): List<AttachmentCommandEntry>

    fun getAllAttachments(): List<Attachment> = entries().map { it.attachment }

    fun setAttachment(attachment: Attachment)

    fun removeAttachment(schema: Schema<out Attachment>)
}

internal data class AttachmentCommandEntry(
    val attachment: Attachment,
    val intrinsic: Boolean,
    val suppressed: Boolean = false
)

private fun BrigadierCommandNodeBuilder.attachmentActions(
    attachmentType: KClass<out Attachment>,
    resolveTarget: BrigadierCommandExecutorContext.() -> AttachmentCommandTarget
) {
    subcommand("view") {
        executes {
            withAttachmentErrors {
                viewAttachments(resolveTarget())
            }
        }
    }

    subcommand("set") {
        argument("id", attachmentSchemaArgument(attachmentType)) { schema ->
            argument("json", StringArgumentType.greedyString()) { json ->
                suggest {
                    builder.suggest(schema().jsonTemplate())
                }
                executes {
                    withAttachmentErrors {
                        val attachment = schema().asAttachmentSchema(attachmentType)
                            .deserializeJson(json())
                            .getOrThrow()
                        resolveTarget().setAttachment(attachment)
                        sender.sendMessage("&aSet attachment &e${schema().id}".colored)
                        BrigadierCommandReturn.Success
                    }
                }
            }
        }
    }

    subcommand("remove") {
        argument("id", attachmentSchemaArgument(attachmentType)) { schema ->
            suggestExistingAttachments(resolveTarget)
            executes {
                withAttachmentErrors {
                    val target = resolveTarget()
                    require(target.getAllAttachments().any { it.schema().id == schema().id }) {
                        "${target.description} does not have attachment ${schema().id}"
                    }
                    target.removeAttachment(schema().asAttachmentSchema(attachmentType))
                    sender.sendMessage("&aRemoved attachment &e${schema().id}".colored)
                    BrigadierCommandReturn.Success
                }
            }
        }
    }

    subcommand("update") {
        argument("id", attachmentSchemaArgument(attachmentType)) { schema ->
            suggestExistingAttachments(resolveTarget)
            argument("key", StringArgumentType.word()) { key ->
                suggest {
                    schema().propertyPaths()
                        .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                        .forEach(builder::suggest)
                }
                argument("value", StringArgumentType.greedyString()) { value ->
                    suggest {
                        schema().jsonPlaceholderAt(key())?.let(builder::suggest)
                    }
                    executes {
                        withAttachmentErrors {
                            val target = resolveTarget()
                            val matching = target.getAllAttachments()
                                .filter { it.schema().id == schema().id }
                            require(matching.isNotEmpty()) {
                                "${target.description} does not have attachment ${schema().id}"
                            }
                            require(matching.size == 1) {
                                "${schema().id} is repeatable and has ${matching.size} values; " +
                                    "replace it with 'set' instead"
                            }

                            val attachmentSchema = schema().asAttachmentSchema(attachmentType)
                            val updated = attachmentSchema
                                .updateJsonProperty(matching.single(), key(), value())
                                .getOrThrow()
                            target.setAttachment(updated)
                            sender.sendMessage("&aUpdated &e${schema().id}.${key()}".colored)
                            BrigadierCommandReturn.Success
                        }
                    }
                }
            }
        }
    }
}

private fun BrigadierCommandArgumentBuilder.suggestExistingAttachments(
    resolveTarget: BrigadierCommandExecutorContext.() -> AttachmentCommandTarget
) {
    suggest {
        val target = runCatching { resolveTarget() }.getOrNull() ?: return@suggest
        target.getAllAttachments()
            .map { it.schema().id.toString() }
            .distinct()
            .filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .sorted()
            .forEach(builder::suggest)
    }
}

private fun BrigadierCommandExecutorContext.viewAttachments(
    target: AttachmentCommandTarget
): BrigadierCommandReturn {
    val entries = target.entries()
    val lines = buildList {
        add("&eAttachments on ${target.description} (${entries.size})".colored)
        if (entries.isEmpty()) {
            add("&7No attachments.".colored)
        } else {
            addAll(
                entries
                    .sortedWith(
                        compareBy<AttachmentCommandEntry> { it.attachment.schema().id.toString() }
                            .thenByDescending { it.intrinsic }
                            .thenBy { it.attachment.toString() }
                    )
                    .map { entry ->
                        val schema = entry.attachment.schema()
                        debugEntryComponent(
                            id = schema.id,
                            debugView = schema,
                            value = entry.attachment,
                            intrinsic = entry.intrinsic,
                            suppressed = entry.suppressed
                        )
                    }
            )
        }
    }
    sender.sendMessage(joinedLines(lines))
    return BrigadierCommandReturn.Success
}

private inline fun BrigadierCommandExecutorContext.withAttachmentErrors(
    block: () -> BrigadierCommandReturn
): BrigadierCommandReturn = try {
    block()
} catch (exception: Exception) {
    val causes = generateSequence(exception as Throwable?) { it.cause }
    val schemaJsonError = causes.filterIsInstance<SchemaJsonException>().firstOrNull()
    if (schemaJsonError != null) {
        sender.sendMessage(schemaJsonError.attachmentErrorComponent())
    } else {
        val message = causes.firstNotNullOfOrNull { it.message }
            ?: "Attachment command failed"
        sender.sendMessage("&c$message".colored)
    }
    BrigadierCommandReturn.Failure
}

internal fun SchemaJsonException.attachmentErrorComponent(): Component {
    val lines = mutableListOf<Component>()
    lines += errorMessage.literal(NamedTextColor.RED)
    lines += "&cExpected: ".colored.append(expected.literal(NamedTextColor.YELLOW))
    lines += "&cFound: ".colored.append(found.literal(NamedTextColor.YELLOW))
    availableEntries?.let { entries ->
        lines += "&cAvailable Entries: ".colored.append(entries.availableEntriesComponent())
    }
    return joinedLines(lines)
}

private fun SerializerValueDomain.availableEntriesComponent(): Component = when (this) {
    is SerializerValueDomain.Literal ->
        values.joinToString(prefix = "{ ", postfix = " }").literal(NamedTextColor.YELLOW)

    is SerializerValueDomain.Enum ->
        if (values.size <= MAX_INLINE_AVAILABLE_ENTRIES) {
            values.joinToString(prefix = "{ ", postfix = " }").literal(NamedTextColor.YELLOW)
        } else {
            clickableEntries("/inspectenum $key")
        }

    is SerializerValueDomain.Registry ->
        clickableEntries("/inspectregistry $registryId")
}

private fun clickableEntries(command: String): Component =
    "&7{ ... }".colored
        .clickEvent(ClickEvent.runCommand(command))
        .hoverEvent("&7Click to view all entries.".colored)

private fun String.literal(color: TextColor): Component =
    Component.text(this, color).decoration(TextDecoration.ITALIC, false)

private class PlayerAttachmentCommandTarget(
    private val player: Player
) : AttachmentCommandTarget {
    override val description: String = player.name

    override fun entries(): List<AttachmentCommandEntry> {
        val persisted = AttachmentPdcStorage.read(player.persistentDataContainer)
            .attachments
            .filterIsInstance<PlayerAttachment>()
        val persistedSchemaIds = persisted.mapTo(mutableSetOf()) { it.schema().id }
        val intrinsic = IntrinsicPlayerAttachmentProvider.getAll()
            .filter { it.schema.id !in persistedSchemaIds }
            .map { AttachmentCommandEntry(it.create(player), intrinsic = true) }
        return intrinsic + persisted.map { AttachmentCommandEntry(it, intrinsic = false) }
    }

    override fun setAttachment(attachment: Attachment) {
        player.setAttachment(attachment as PlayerAttachment)
    }

    override fun removeAttachment(schema: Schema<out Attachment>) {
        @Suppress("UNCHECKED_CAST")
        player.removeAttachment(schema as Schema<out PlayerAttachment>)
    }
}

private class ItemAttachmentCommandTarget(
    private val item: CuTItemStack
) : AttachmentCommandTarget {
    override val description: String = "held ${item.type.id}"

    override fun entries(): List<AttachmentCommandEntry> {
        val state = AttachmentPdcStorage.read(item.handle.itemMeta.persistentDataContainer)
        val overlay = state.attachments.filterIsInstance<ItemAttachment>()
        return itemAttachmentCommandEntries(
            intrinsic = item.type.descriptor.attachments,
            overlay = overlay,
            suppressed = state.suppressed
        )
    }

    override fun setAttachment(attachment: Attachment) {
        item.setAttachment(attachment as ItemAttachment)
    }

    override fun removeAttachment(schema: Schema<out Attachment>) {
        @Suppress("UNCHECKED_CAST")
        item.removeAttachment(schema as Schema<out ItemAttachment>)
    }
}

internal fun itemAttachmentCommandEntries(
    intrinsic: List<ItemAttachment>,
    overlay: List<ItemAttachment>,
    suppressed: Set<Identifier>
): List<AttachmentCommandEntry> {
    val overlaySchemaIds = overlay.mapTo(mutableSetOf()) { it.schema().id }
    val intrinsicEntries = intrinsic
        .filter { attachment ->
            val schemaId = attachment.schema().id
            schemaId in suppressed ||
                attachment.isRepeatableAttachment() ||
                schemaId !in overlaySchemaIds
        }
        .map { attachment ->
            AttachmentCommandEntry(
                attachment = attachment,
                intrinsic = true,
                suppressed = attachment.schema().id in suppressed
            )
        }
    return intrinsicEntries + overlay.map { AttachmentCommandEntry(it, intrinsic = false) }
}

private fun attachmentSchemaArgument(
    attachmentType: KClass<out Attachment>
): IdentifiableArgumentType<Schema<*>> =
    IdentifiableArgumentType(Schema) { schema ->
        schema.type.isSubclassOf(attachmentType)
    }

@Suppress("UNCHECKED_CAST")
private fun Schema<*>.asAttachmentSchema(
    attachmentType: KClass<out Attachment>
): Schema<Attachment> {
    require(type.isSubclassOf(attachmentType)) {
        "Schema $id does not describe a ${attachmentType.simpleName}"
    }
    return this as Schema<Attachment>
}

private fun Schema<*>.jsonTemplate(): String = descriptor.jsonPlaceholder()

private fun Schema<*>.propertyPaths(prefix: String = ""): List<String> =
    descriptor.objectProperties().flatMap { property ->
        val path = if (prefix.isEmpty()) property.name else "$prefix.${property.name}"
        listOf(path) + property.serializerDescriptor.propertyPaths(path)
    }

private fun Schema<*>.jsonPlaceholderAt(path: String): String? {
    val segments = path.split('.').filter(String::isNotBlank)
    if (segments.isEmpty()) return null
    var current: SerializerDescriptor<*> = descriptor
    for ((index, segment) in segments.withIndex()) {
        val property = current.objectProperties().firstOrNull { it.name == segment }
            ?: return null
        if (index == segments.lastIndex) {
            return property.serializerDescriptor.jsonPlaceholder()
        }
        current = property.serializerDescriptor
    }
    return null
}

private fun SerializerDescriptor<*>.jsonPlaceholder(): String = when (val shape = shape) {
    is SerializerShape.Nullable -> "null"
    is SerializerShape.Mapped -> shape.encoded.jsonPlaceholder()
    is SerializerShape.List -> "[]"
    is SerializerShape.Map -> "{}"
    is SerializerShape.Object -> shape.objectJsonPlaceholder()
    is SerializerShape.Polymorphic -> shape.base.shape.objectJsonPlaceholder()
    SerializerShape.Opaque -> "\"\""
    is SerializerShape.Primitive -> when (shape.kind) {
        VariantKind.Null -> "null"
        VariantKind.Boolean -> "false"
        VariantKind.Byte,
        VariantKind.Short,
        VariantKind.Int,
        VariantKind.Long,
        VariantKind.Float,
        VariantKind.Double -> "0"

        VariantKind.List -> "[]"
        VariantKind.Map -> "{}"

        VariantKind.Any,
        VariantKind.String,
        VariantKind.Char,
        VariantKind.Identifier,
        VariantKind.ResourceRef -> valueDomain.firstValueOrNull()?.let(::jsonString) ?: "\"\""
    }
}

private fun SerializerShape.Object.objectJsonPlaceholder(): String =
    properties.joinToString(prefix = "{", postfix = "}", separator = ",") { property ->
        "${jsonString(property.name)}:${property.serializerDescriptor.jsonPlaceholder()}"
    }

private fun SerializerDescriptor<*>.objectProperties(): List<SerializedPropertyDescriptor> = when (val shape = shape) {
    is SerializerShape.Nullable -> shape.value.objectProperties()
    is SerializerShape.Mapped -> shape.encoded.objectProperties()
    is SerializerShape.Object -> shape.properties
    is SerializerShape.Polymorphic -> shape.base.shape.properties
    else -> emptyList()
}

private fun SerializerDescriptor<*>.propertyPaths(prefix: String): List<String> =
    objectProperties().flatMap { property ->
        val path = "$prefix.${property.name}"
        listOf(path) + property.serializerDescriptor.propertyPaths(path)
    }

private fun SerializerValueDomain?.firstValueOrNull(): String? = when (this) {
    is SerializerValueDomain.Literal -> values.firstOrNull()
    is SerializerValueDomain.Enum -> values.firstOrNull()
    is SerializerValueDomain.Registry, null -> null
}

private fun jsonString(value: String): String =
    kotlinx.serialization.json.JsonPrimitive(value).toString()
