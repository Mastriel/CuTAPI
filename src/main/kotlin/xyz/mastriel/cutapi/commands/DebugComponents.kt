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
    debugView: EncodeOnlySerializer<*>?,
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
    return entry.hoverEvent(debugViewHover(id, debugView, value, intrinsic, suppressed))
}

@Suppress("UNCHECKED_CAST")
private fun debugViewHover(
    id: Identifier,
    debugView: EncodeOnlySerializer<*>,
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
        val variant = (debugView as EncodeOnlySerializer<Any>).serialize(value).getOrThrow()
        variant.debugYamlComponent(debugView, DebugSource(value))
    } catch (exception: Exception) {
        "&c${exception.message ?: "Unable to serialize debug properties"}".colored
    }
    return title.appendNewline().append(body)
}

internal fun JsonElement.debugYamlComponent(): Component =
    toVariant().debugYamlComponent()

internal fun Variant.debugYamlComponent(
    serializer: EncodeOnlySerializer<*>? = null,
    source: DebugSource? = null
): Component = joinedLines(yamlLines(serializer = serializer, source = source))

private fun Variant.yamlLines(
    indent: Int = 0,
    header: Component? = null,
    serializer: EncodeOnlySerializer<*>? = null,
    property: StructuredProperty<*, *>? = null,
    source: DebugSource? = null,
    useFormatter: Boolean = true
): List<Component> {
    val defaultFormatter = { defaultDebugComponent(serializer, source) }
    val formatted = if (useFormatter) {
        if (property != null && source != null) {
            property.formatDebugValue(source.value, this, defaultFormatter)
        } else {
            null
        } ?: DebugFormatter.format(
            serializer = serializer,
            variant = this,
            sourceValue = source?.value,
            hasSourceValue = source != null,
            defaultFormatter = defaultFormatter
        )
    } else null
    if (formatted != null) {
        return listOf(
            (header ?: yamlIndent(indent))
                .append(if (header != null) " ".colored else Component.empty())
                .append(formatted)
        )
    }

    return when (this) {
        is Variant.Map -> yamlLines(indent, header, serializer, source)
        is Variant.List -> yamlLines(indent, header, source)
        else -> listOf(
            (header ?: yamlIndent(indent))
                .append(if (header != null) " ".colored else Component.empty())
                .append(yamlScalarComponent())
        )
    }
}

private fun Variant.Map.yamlLines(
    indent: Int,
    header: Component?,
    serializer: EncodeOnlySerializer<*>?,
    source: DebugSource?
): List<Component> {
    val entries = stringEntries()
    val typeId = (get(SCHEMA_TYPE_DISCRIMINATOR) as? Variant.String)?.value
    val displayedEntries = entries.filter { it.first != SCHEMA_TYPE_DISCRIMINATOR || typeId == null }
    val typeTag = typeId?.let { "&7!<&${ResourceInspector.ObjectType}$it&7>".colored }

    if (displayedEntries.isEmpty()) {
        return listOf(
            buildHeader(header, typeTag)
                .append(if (header != null || typeTag != null) " ".colored else Component.empty())
                .append("&7{}".colored)
        )
    }

    return buildList {
        if (header != null || typeTag != null) {
            add(buildHeader(header, typeTag))
        }
        for ((key, value) in displayedEntries) {
            val property = serializer?.structuredProperty(key)
            val propertySource = if (property != null && source?.value != null) {
                DebugSource(property.valueFrom(source.value))
            } else {
                null
            }
            val keyHeader = yamlIndent(indent)
                .append(yamlKeyComponent(key))
                .append("&7:".colored)
            addAll(
                value.yamlLines(
                    indent = indent + YAML_INDENT,
                    header = keyHeader,
                    serializer = property?.serializer,
                    property = property,
                    source = propertySource
                )
            )
        }
    }
}

private fun Variant.List.yamlLines(
    indent: Int,
    header: Component?,
    source: DebugSource?
): List<Component> {
    if (isEmpty()) {
        return listOf(
            (header ?: yamlIndent(indent))
                .append(if (header != null) " ".colored else Component.empty())
                .append("&7[]".colored)
        )
    }

    return buildList {
        if (header != null) add(header)
        val sourceValues = (source?.value as? List<*>)
        for ((index, value) in this@yamlLines.withIndex()) {
            val itemHeader = yamlIndent(indent).append("&7-".colored)
            val itemSource = sourceValues
                ?.takeIf { index in it.indices }
                ?.let { DebugSource(it[index]) }
            addAll(value.yamlLines(indent + YAML_INDENT, itemHeader, source = itemSource))
        }
    }
}

private fun Variant.Map.stringEntries(): List<Pair<String, Variant>> = map { (key, value) ->
    val name = when (key) {
        is Variant.String -> key.value
        is Variant.Identifier -> key.value.toString()
        else -> key.value.toString()
    }
    name to value
}

private fun buildHeader(
    header: Component?,
    typeTag: Component?
): Component {
    var result = header ?: Component.empty()
    if (typeTag != null) {
        if (header != null) result = result.append(" ".colored)
        result = result.append(typeTag)
    }
    return result
}

private fun yamlIndent(indent: Int): Component = " ".repeat(indent).colored

private fun yamlKeyComponent(key: String): Component {
    val rendered = if (YAML_PLAIN_KEY.matches(key)) key else JsonPrimitive(key).toString()
    return "&${ResourceInspector.PropertyKey}$rendered".colored
}

private fun Variant.defaultDebugComponent(
    serializer: EncodeOnlySerializer<*>?,
    source: DebugSource?
): Component = when (this) {
    is Variant.Map, is Variant.List ->
        joinedLines(yamlLines(serializer = serializer, source = source, useFormatter = false))

    else -> yamlScalarComponent()
}

private fun Variant.yamlScalarComponent(): Component =
    "&${ResourceInspector.PropertyValue}${toJsonElement()}".colored

internal class DebugSource(
    val value: Any?
)

internal fun joinedLines(lines: Collection<Component>): Component {
    var result: Component = Component.empty()
    lines.forEachIndexed { index, line ->
        if (index > 0) result = result.appendNewline()
        result = result.append(line)
    }
    return result
}

private val YAML_PLAIN_KEY: Regex = Regex("[A-Za-z_][A-Za-z0-9_.-]*")
private const val YAML_INDENT: Int = 2
