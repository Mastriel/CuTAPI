package xyz.mastriel.cutapi.data

import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*

internal class SchemaJsonException(
    val errorMessage: String,
    val expected: String,
    val found: String,
    val availableEntries: SerializerAvailableEntries? = null,
    cause: Throwable? = null
) : DataSerializationException(
    buildString {
        appendLine(errorMessage)
        appendLine("Expected: $expected")
        append("Found: $found")
        if (availableEntries != null) {
            appendLine()
            append("Available Entries: ")
            append(
                when (availableEntries) {
                    is SerializerAvailableEntries.LiteralValues ->
                        availableEntries.values.joinToString(prefix = "{ ", postfix = " }")

                    is SerializerAvailableEntries.EnumValues ->
                        if (availableEntries.values.size > MAX_INLINE_AVAILABLE_ENTRIES) {
                            "{ ... }"
                        } else {
                            availableEntries.values.joinToString(prefix = "{ ", postfix = " }")
                        }

                    is SerializerAvailableEntries.RegistryValues -> "{ ... }"
                }
            )
        }
    },
    cause
)

internal fun Variant.toJsonElement(): JsonElement = when (this) {
    Variant.Null -> JsonNull
    is Variant.String -> JsonPrimitive(value)
    is Variant.Boolean -> JsonPrimitive(value)
    is Variant.Byte -> JsonPrimitive(value)
    is Variant.Short -> JsonPrimitive(value)
    is Variant.Int -> JsonPrimitive(value)
    is Variant.Long -> JsonPrimitive(value)
    is Variant.Float -> JsonPrimitive(value)
    is Variant.Double -> JsonPrimitive(value)
    is Variant.Char -> JsonPrimitive(value.toString())
    is Variant.Identifier -> JsonPrimitive(value.toString())
    is Variant.ResourceRef -> JsonPrimitive(value.toString())
    is Variant.List -> JsonArray(value.map(Variant::toJsonElement))
    is Variant.Map -> JsonObject(
        value.mapKeys { (key) ->
            when (key) {
                is Variant.String -> key.value
                is Variant.Identifier -> key.value.toString()
                else -> key.value.toString()
            }
        }.mapValues { (_, value) -> value.toJsonElement() }
    )
}

internal fun Variant.toJson(pretty: Boolean = true): String {
    val format = if (pretty) CuTApiJson else Json
    return format.encodeToString(JsonElement.serializer(), toJsonElement())
}

internal fun JsonElement.toVariant(): Variant = when (this) {
    JsonNull -> Variant.Null
    is JsonArray -> Variant.List(map(JsonElement::toVariant))
    is JsonObject -> Variant.Map(map { (key, value) -> Variant.String(key) to value.toVariant() }.toMap())
    is JsonPrimitive -> when {
        isString -> Variant.String(content)
        booleanOrNull != null -> Variant.Boolean(boolean)
        longOrNull != null && long in Int.MIN_VALUE..Int.MAX_VALUE -> Variant.Int(int)
        longOrNull != null -> Variant.Long(long)
        doubleOrNull != null -> Variant.Double(double)
        else -> Variant.String(content)
    }
}

internal fun <T : Any> Schema<T>.deserializeJson(json: String): DeserializeResult<T> = try {
    val element = CuTApiJson.parseToJsonElement(json)
    deserializeJson(element)
} catch (exception: Exception) {
    DeserializeResult.Failure(
        SchemaJsonException(
            errorMessage = "Could not parse JSON for schema $id: ${exception.parserMessage()}",
            expected = "valid JSON object",
            found = json,
            cause = exception
        )
    )
}

internal fun <T : Any> Schema<T>.deserializeJson(element: JsonElement): DeserializeResult<T> = try {
    val jsonObject = element as? JsonObject
        ?: throw invalidJsonValue(
            schema = this,
            path = emptyList(),
            expected = "object",
            actual = element
        )
    when (val result = deserialize(jsonObject.toVariant(this, this))) {
        is DeserializeResult.Success -> result
        is DeserializeResult.Failure -> DeserializeResult.Failure(
            result.error.asSchemaJsonError(
                schema = this,
                rootSchema = this,
                path = emptyList()
            )
        )
    }
} catch (exception: Exception) {
    DeserializeResult.Failure(exception)
}

internal fun <T : Any> Schema<T>.updateJsonProperty(
    value: T,
    path: String,
    json: String
): DeserializeResult<T> = try {
    val segments = path.split('.').filter(String::isNotBlank)
    require(segments.isNotEmpty()) { "Attachment property path cannot be empty" }
    require(SCHEMA_TYPE_DISCRIMINATOR !in segments) {
        "'$SCHEMA_TYPE_DISCRIMINATOR' cannot be updated"
    }

    val serialized = serialize(value).getOrThrow() as? Variant.Map
        ?: throw DataSerializationException("Schema $id did not serialize to a map")
    val replacement = try {
        CuTApiJson.parseToJsonElement(json)
    } catch (exception: Exception) {
        throw SchemaJsonException(
            errorMessage = "Could not parse JSON for schema $id at '${segments.renderPath()}': " +
                exception.parserMessage(),
            expected = "valid JSON value",
            found = json,
            cause = exception
        )
    }
    val updated = updateJsonProperty(
        map = serialized,
        path = segments,
        replacement = replacement,
        rootSchema = this
    )
    when (val result = deserialize(updated)) {
        is DeserializeResult.Success -> result
        is DeserializeResult.Failure -> DeserializeResult.Failure(
            result.error.asSchemaJsonError(
                schema = this,
                rootSchema = this,
                path = emptyList()
            )
        )
    }
} catch (exception: Exception) {
    DeserializeResult.Failure(
        exception.asSchemaJsonError(
            schema = this,
            rootSchema = this,
            path = emptyList()
        )
    )
}

private fun JsonObject.toVariant(
    schema: Schema<*>,
    rootSchema: Schema<*>,
    path: List<String> = emptyList()
): Variant.Map {
    val properties = schema.properties.associateBy { it.name }
    val values = linkedMapOf<Variant, Variant>()

    for ((name, element) in this) {
        if (name == SCHEMA_TYPE_DISCRIMINATOR) {
            values[Variant.String(name)] = Variant.String(element.jsonPrimitive.content)
            continue
        }

        val property = properties[name]
            ?: throw SchemaJsonException(
                errorMessage = "Unknown property for schema ${rootSchema.id} at " +
                    "'${(path + name).renderPath()}'.",
                expected = "registered property name",
                found = JsonPrimitive(name).toString(),
                availableEntries = SerializerAvailableEntries.LiteralValues(
                    properties.keys.sorted()
                )
            )
        values[Variant.String(name)] = property.serializer.deserializeJsonValue(
            element = element,
            rootSchema = rootSchema,
            path = path + name
        )
    }

    if (!schema.untagged && SCHEMA_TYPE_DISCRIMINATOR !in this) {
        values[Variant.String(SCHEMA_TYPE_DISCRIMINATOR)] = Variant.String(schema.id.toString())
    }
    return Variant.Map(values)
}

private fun Schema<*>.updateJsonProperty(
    map: Variant.Map,
    path: List<String>,
    replacement: JsonElement,
    rootSchema: Schema<*>,
    traversedPath: List<String> = emptyList()
): Variant.Map {
    val segment = path.first()
    val currentPath = traversedPath + segment
    val property = properties.firstOrNull { it.name == segment || it.propertyName == segment }
        ?: throw DataSerializationException(
            "Invalid JSON property '${currentPath.renderPath()}' for schema ${rootSchema.id}: " +
                "unknown property"
        )
    val key = Variant.String(property.name)
    val updatedValue = if (path.size == 1) {
        property.serializer.deserializeJsonValue(
            element = replacement,
            rootSchema = rootSchema,
            path = currentPath
        )
    } else {
        val nestedSchema = property.serializer as? Schema<*>
            ?: throw DataSerializationException(
                "Invalid JSON property '${currentPath.renderPath()}' for schema ${rootSchema.id}: " +
                    "expected an object with nested properties"
            )
        val nestedMap = map[key] as? Variant.Map
            ?: throw DataSerializationException(
                "Invalid JSON property '${currentPath.renderPath()}' for schema ${rootSchema.id}: " +
                    "the current value is not an object"
            )
        nestedSchema.updateJsonProperty(
            map = nestedMap,
            path = path.drop(1),
            replacement = replacement,
            rootSchema = rootSchema,
            traversedPath = currentPath
        )
    }

    return Variant.Map(map.value.toMutableMap().also { it[key] = updatedValue })
}

@Suppress("UNCHECKED_CAST")
private fun Serializer<*>.deserializeJsonValue(
    element: JsonElement,
    rootSchema: Schema<*>,
    path: List<String>
): Variant {
    if (this is Schema<*>) {
        val map = element as? JsonObject
            ?: throw invalidJsonValue(
                schema = rootSchema,
                path = path,
                expected = "${type.simpleName ?: id} object",
                actual = element
            )
        val result = (this as Schema<Any>).deserialize(map.toVariant(this, rootSchema, path))
        val decoded = when (result) {
            is DeserializeResult.Success -> result.value
            is DeserializeResult.Failure -> throw result.error.asSchemaJsonError(
                schema = this,
                rootSchema = rootSchema,
                path = path
            )
        }
        return serialize(decoded).getOrThrow()
    }

    val serializer = this as Serializer<Any?>
    var firstError: Exception? = null
    for (candidate in element.variantCandidates()) {
        when (val result = serializer.deserialize(candidate)) {
            is DeserializeResult.Success -> return serializer.serialize(result.value).getOrThrow()
            is DeserializeResult.Failure -> if (firstError == null) firstError = result.error
        }
    }

    throw invalidJsonValue(
        schema = rootSchema,
        path = path,
        expected = expectedJsonValue(firstError),
        actual = element,
        availableEntries = availableJsonEntries(),
        cause = firstError
    )
}

private fun Serializer<*>.expectedJsonValue(error: Exception?): String {
    if (this is IdentifierRegistry<*>) return "Identifier"

    val metadata = this as? SerializerJsonMetadata
    if (metadata != null) return metadata.expectedType

    val variantType = error.causeSequence()
        .filterIsInstance<VariantTypeException>()
        .firstOrNull()
    if (variantType != null) return variantType.expected.toJsonTypeName()

    if (this is TaggedSerializer<*>) {
        return id.toString()
    }

    return "<unknown>"
}

private fun Serializer<*>.availableJsonEntries(): SerializerAvailableEntries? = when (this) {
    is IdentifierRegistry<*> -> SerializerAvailableEntries.RegistryValues(id)
    is SerializerJsonMetadata -> availableEntries
    else -> null
}

private fun String.toJsonTypeName(): String = when (lowercase()) {
    "null" -> "null"
    "string" -> "string"
    "boolean" -> "boolean"
    "byte" -> "byte-sized integer"
    "short" -> "short integer"
    "int" -> "integer"
    "long" -> "long integer"
    "float" -> "floating-point number"
    "double" -> "number"
    "char" -> "single-character string"
    "identifier" -> "identifier string"
    "resourceref" -> "resource reference string"
    "list" -> "array"
    "map" -> "object"
    else -> this
}

private fun invalidJsonValue(
    schema: Schema<*>,
    path: List<String>,
    expected: String,
    actual: JsonElement,
    availableEntries: SerializerAvailableEntries? = null,
    cause: Throwable? = null
): SchemaJsonException {
    val location = if (path.isEmpty()) "root" else "'${path.renderPath()}'"
    return SchemaJsonException(
        errorMessage = "Invalid JSON for schema ${schema.id} at $location.",
        expected = expected,
        found = actual.toString(),
        availableEntries = availableEntries,
        cause = cause
    )
}

private fun List<String>.renderPath(): String =
    joinToString(separator = ".")

private fun Throwable?.causeSequence(): Sequence<Throwable> =
    if (this == null) emptySequence() else generateSequence(this) { it.cause }

private fun Exception.asSchemaJsonError(
    schema: Schema<*>,
    rootSchema: Schema<*>,
    path: List<String>
): Exception {
    if (this is SchemaJsonException) return this

    val missingName = MISSING_PROPERTY_PATTERN.find(message.orEmpty())?.groupValues?.get(1)
    if (missingName != null) {
        val property = schema.properties.firstOrNull { it.name == missingName }
        val expected = property?.serializer?.expectedJsonValue(null) ?: "value"
        return SchemaJsonException(
            errorMessage = "Missing required property for schema ${rootSchema.id} at " +
                "'${(path + missingName).renderPath()}'.",
            expected = expected,
            found = "<missing>",
            availableEntries = property?.serializer?.availableJsonEntries(),
            cause = this
        )
    }

    val location = if (path.isEmpty()) "" else " at '${path.renderPath()}'"
    return DataSerializationException(
        "Invalid JSON for schema ${rootSchema.id}$location: ${message ?: "deserialization failed"}",
        this
    )
}

private val MISSING_PROPERTY_PATTERN: Regex =
    Regex("""Missing property '([^']+)'""")

private fun Exception.parserMessage(): String =
    message
        ?.substringBefore("\nJSON input:")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: "malformed JSON"

private fun JsonElement.variantCandidates(): List<Variant> = when (this) {
    JsonNull -> listOf(Variant.Null)
    is JsonPrimitive -> primitiveCandidates()
    is JsonArray -> cartesian(map { it.variantCandidates() })
        .map { Variant.List(it) }
        .ifEmpty { listOf(Variant.List(emptyList())) }

    is JsonObject -> {
        val entries = entries.toList()
        val combinations = cartesian(entries.map { (_, value) -> value.variantCandidates() })
        combinations.map { values ->
            Variant.Map(
                entries.indices.associate { index ->
                    Variant.String(entries[index].key) to values[index]
                }
            )
        }.ifEmpty { listOf(Variant.Map(emptyMap())) }
    }
}

private fun JsonPrimitive.primitiveCandidates(): List<Variant> {
    if (isString) {
        val values = mutableListOf<Variant>(Variant.String(content))
        if (content.length == 1) values += Variant.Char(content.single())
        idOrNull(content)?.let { values += Variant.Identifier(it) }
        try {
            values += Variant.ResourceRef(ref<Resource>(content))
        } catch (_: Exception) {
            // A plain JSON string is still a valid candidate.
        }
        return values
    }

    booleanOrNull?.let { return listOf(Variant.Boolean(it)) }

    val values = mutableListOf<Variant>()
    content.toIntOrNull()?.let { values += Variant.Int(it) }
    content.toLongOrNull()?.let { values += Variant.Long(it) }
    content.toShortOrNull()?.let { values += Variant.Short(it) }
    content.toByteOrNull()?.let { values += Variant.Byte(it) }
    content.toDoubleOrNull()?.let { values += Variant.Double(it) }
    content.toFloatOrNull()?.let { values += Variant.Float(it) }
    return values.ifEmpty { listOf(Variant.String(content)) }
}

private fun cartesian(options: List<List<Variant>>): List<List<Variant>> {
    var combinations = listOf(emptyList<Variant>())
    for (values in options) {
        combinations = combinations
            .flatMap { existing -> values.map { existing + it } }
            .take(MAX_JSON_VARIANT_CANDIDATES)
    }
    return combinations
}

private const val MAX_JSON_VARIANT_CANDIDATES: Int = 256
internal const val MAX_INLINE_AVAILABLE_ENTRIES: Int = 10
