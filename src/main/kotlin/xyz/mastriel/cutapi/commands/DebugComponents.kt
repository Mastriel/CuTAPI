package xyz.mastriel.cutapi.commands

import kotlinx.serialization.json.*
import net.kyori.adventure.text.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.utils.*

internal fun Identifiable.debugEntryComponent(): Component =
    debugEntryComponent(id, debugView, this)

internal fun debugEntryComponent(
    id: Identifier,
    debugView: DebugRepresentation<*>?,
    value: Any?,
    intrinsic: Boolean = false,
    suppressed: Boolean = false
): Component {
    val entry = when {
        suppressed -> "&7- &c❌ $id".colored
        intrinsic -> "&7- &6★ &e$id".colored
        else -> "&7- &a$id".colored
    }

    if (debugView == null || value == null) return entry
    return entry.hoverEvent(debugRepresentationHover(id, debugView, value, intrinsic, suppressed))
}

@Suppress("UNCHECKED_CAST")
private fun debugRepresentationHover(
    id: Identifier,
    debugView: DebugRepresentation<*>,
    value: Any,
    intrinsic: Boolean = false,
    suppressed: Boolean = false
): Component {
    val marker = when {
        suppressed -> "&c❌ "
        intrinsic -> "★ "
        else -> ""
    }
    val hint = when {
        suppressed -> "&7 (intrinsic, suppressed)"
        intrinsic -> "&7 (intrinsic)"
        else -> ""
    }
    val title =
        "&${ResourceInspector.InspectorTitle}${marker}${id}${hint}".colored
    val body = try {
        val variant = (debugView as DebugRepresentation<Any>).serialize(value).getOrThrow()
        variant.toJsonElement().debugJsonComponent()
    } catch (exception: Exception) {
        "&c${exception.message ?: "Unable to serialize debug properties"}".colored
    }
    return title.appendNewline().append(body)
}

internal fun JsonElement.debugJsonComponent(indent: Int = 0): Component = when (this) {
    is JsonObject -> objectDebugComponent(indent)
    is JsonArray -> arrayDebugComponent(indent)
    else -> "&${ResourceInspector.PropertyValue}${toString()}".colored
}

private fun JsonObject.objectDebugComponent(indent: Int): Component {
    val typeId = (this[SCHEMA_TYPE_DISCRIMINATOR] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
    val displayedEntries = entries.filter { it.key != SCHEMA_TYPE_DISCRIMINATOR || typeId == null }
    val opening = if (typeId == null) {
        "&7{".colored
    } else {
        "&b<$typeId> &7{".colored
    }

    if (displayedEntries.isEmpty()) return opening.append("&7}".colored)

    var result: Component = opening
    displayedEntries.forEachIndexed { index, (key, value) ->
        result = result.appendNewline()
            .append(" ".repeat(indent + JSON_INDENT).colored)
            .append("&${ResourceInspector.PropertyKey}${JsonPrimitive(key)}".colored)
            .append("&7: ".colored)
            .append(value.debugJsonComponent(indent + JSON_INDENT))
        if (index < displayedEntries.size - 1) {
            result = result.append("&7,".colored)
        }
    }
    return result.appendNewline()
        .append(" ".repeat(indent).colored)
        .append("&7}".colored)
}

private fun JsonArray.arrayDebugComponent(indent: Int): Component {
    if (isEmpty()) return "&7[]".colored

    var result: Component = "&7[".colored
    forEachIndexed { index, value ->
        result = result.appendNewline()
            .append(" ".repeat(indent + JSON_INDENT).colored)
            .append(value.debugJsonComponent(indent + JSON_INDENT))
        if (index < size - 1) {
            result = result.append("&7,".colored)
        }
    }
    return result.appendNewline()
        .append(" ".repeat(indent).colored)
        .append("&7]".colored)
}

internal fun joinedLines(lines: Collection<Component>): Component {
    var result: Component = Component.empty()
    lines.forEachIndexed { index, line ->
        if (index > 0) result = result.appendNewline()
        result = result.append(line)
    }
    return result
}

private const val JSON_INDENT: Int = 2
