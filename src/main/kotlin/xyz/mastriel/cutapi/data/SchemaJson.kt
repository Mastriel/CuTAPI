package xyz.mastriel.cutapi.data

import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*

internal class SchemaJsonException(
    val errorMessage: String,
    val expected: String,
    val found: String,
    val availableEntries: SerializerValueDomain? = null,
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
                    is SerializerValueDomain.Literal ->
                        availableEntries.values.joinToString(prefix = "{ ", postfix = " }")

                    is SerializerValueDomain.Enum ->
                        if (availableEntries.values.size > MAX_INLINE_AVAILABLE_ENTRIES) {
                            "{ ... }"
                        } else {
                            availableEntries.values.joinToString(prefix = "{ ", postfix = " }")
                        }

                    is SerializerValueDomain.Registry -> "{ ... }"
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
    is Variant.Map -> JsonObject(value.mapValues { (_, value) -> value.toJsonElement() })
}

internal fun Variant.toJson(pretty: Boolean = true): String {
    val format = if (pretty) CuTApiJson else Json
    return format.encodeToString(JsonElement.serializer(), toJsonElement())
}

internal fun JsonElement.toVariant(): Variant = when (this) {
    JsonNull -> Variant.Null
    is JsonArray -> Variant.List(map(JsonElement::toVariant))
    is JsonObject -> Variant.Map(mapValues { (_, value) -> value.toVariant() })
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
    if (schema is PolySchema<*>) {
        return toVariant(schema.descriptor, rootSchema, path) as Variant.Map
    }

    val properties = schema.properties.associateBy { it.name }
    val values = linkedMapOf<String, Variant>()
    for ((name, element) in this) {
        if (name == SCHEMA_TYPE_DISCRIMINATOR) {
            val serializedId = (element as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?: throw invalidJsonValue(
                    rootSchema,
                    path + name,
                    VariantKind.Identifier.id.toString(),
                    element
                )
            if (serializedId != schema.id.toString()) {
                throw invalidJsonValue(
                    rootSchema,
                    path + name,
                    schema.id.toString(),
                    element,
                    SerializerValueDomain.Literal(listOf(schema.id.toString()))
                )
            }
            values[name] = Variant.String(serializedId)
            continue
        }

        val property = properties[name]
        if (property == null) {
            if (!schema.strict) continue
            throw SchemaJsonException(
                errorMessage = "Unknown property for schema ${rootSchema.id} at " +
                    "'${(path + name).renderPath()}'.",
                expected = "registered property name",
                found = JsonPrimitive(name).toString(),
                availableEntries = SerializerValueDomain.Literal(properties.keys.sorted())
            )
        }
        values[name] = property.serializer.deserializeJsonValue(
            element = element,
            rootSchema = rootSchema,
            path = path + name
        )
    }

    if (!schema.untagged && SCHEMA_TYPE_DISCRIMINATOR !in this) {
        values[SCHEMA_TYPE_DISCRIMINATOR] = Variant.String(schema.id.toString())
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
    val property = properties.firstOrNull { it.name == segment || it.sourceName == segment }
        ?: throw DataSerializationException(
            "Invalid JSON property '${currentPath.renderPath()}' for schema ${rootSchema.id}: " +
                "unknown property"
        )
    val key = property.name
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
    val serializer = this as Serializer<Any?>
    val candidate = if (this is Schema<*>) {
        val jsonObject = element as? JsonObject
            ?: throw invalidJsonValue(
                rootSchema,
                path,
                descriptor.id.toString(),
                element
            )
        jsonObject.toVariant(this, rootSchema, path)
    } else {
        element.toVariant(descriptor, rootSchema, path)
    }
    when (val result = serializer.deserialize(candidate)) {
        is DeserializeResult.Success -> return serializer.serialize(result.value).getOrThrow()
        is DeserializeResult.Failure -> {
            if (this is Schema<*>) {
                throw result.error.asSchemaJsonError(
                    schema = this,
                    rootSchema = rootSchema,
                    path = path
                )
            }
            throw invalidJsonValue(
                schema = rootSchema,
                path = path,
                expected = descriptor.id.toString(),
                actual = element,
                availableEntries = descriptor.valueDomain,
                cause = result.error
            )
        }
    }
}

private fun JsonElement.toVariant(
    descriptor: SerializerDescriptor<*>,
    rootSchema: Schema<*>,
    path: List<String>,
    expectedDescriptor: SerializerDescriptor<*> = descriptor
): Variant = when (val shape = descriptor.shape) {
    is SerializerShape.Nullable -> if (this === JsonNull) {
        Variant.Null
    } else {
        toVariant(shape.value, rootSchema, path, expectedDescriptor)
    }

    is SerializerShape.Mapped ->
        toVariant(shape.encoded, rootSchema, path, expectedDescriptor)

    is SerializerShape.Primitive ->
        toPrimitiveVariant(shape, expectedDescriptor, rootSchema, path)

    is SerializerShape.List -> {
        val array = this as? JsonArray
            ?: throw invalidJsonValue(
                rootSchema,
                path,
                expectedDescriptor.id.toString(),
                this,
                expectedDescriptor.valueDomain
            )
        Variant.List(
            array.mapIndexed { index, element ->
                element.toVariant(shape.element, rootSchema, path + index.toString())
            }
        )
    }

    is SerializerShape.Map -> {
        val jsonObject = this as? JsonObject
            ?: throw invalidJsonValue(
                rootSchema,
                path,
                expectedDescriptor.id.toString(),
                this,
                expectedDescriptor.valueDomain
        )
        Variant.Map(
            jsonObject.mapValues { (key, element) ->
                element.toVariant(shape.value, rootSchema, path + key)
            }
        )
    }

    is SerializerShape.Object ->
        toObjectVariant(descriptor, shape, rootSchema, path)

    is SerializerShape.Polymorphic -> {
        val jsonObject = this as? JsonObject
            ?: throw invalidJsonValue(
                rootSchema,
                path,
                expectedDescriptor.id.toString(),
                this
            )
        val typeElement = jsonObject[SCHEMA_TYPE_DISCRIMINATOR]
        val objectDescriptor = if (typeElement == null) {
            shape.base
        } else {
            val serializedId = (typeElement as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?: throw invalidJsonValue(
                    rootSchema,
                    path + SCHEMA_TYPE_DISCRIMINATOR,
                    VariantKind.Identifier.id.toString(),
                    typeElement
                )
            (listOf(shape.base) + shape.included)
                .singleOrNull { it.id.toString() == serializedId }
                ?: throw invalidJsonValue(
                    rootSchema,
                    path + SCHEMA_TYPE_DISCRIMINATOR,
                    expectedDescriptor.id.toString(),
                    typeElement,
                    SerializerValueDomain.Literal(
                        (listOf(shape.base) + shape.included)
                            .map { it.id.toString() }
                    )
                )
        }
        jsonObject.toObjectVariant(objectDescriptor, objectDescriptor.shape, rootSchema, path)
    }

    SerializerShape.Opaque -> toVariant()
}

private fun JsonElement.toObjectVariant(
    descriptor: SerializerDescriptor<*>,
    shape: SerializerShape.Object,
    rootSchema: Schema<*>,
    path: List<String>
): Variant.Map {
    val jsonObject = this as? JsonObject
        ?: throw invalidJsonValue(
            rootSchema,
            path,
            descriptor.id.toString(),
            this
        )
    val properties = shape.properties.associateBy { it.name }
    val values = linkedMapOf<String, Variant>()
    for ((name, element) in jsonObject) {
        if (name == SCHEMA_TYPE_DISCRIMINATOR) {
            val serializedId = (element as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?: throw invalidJsonValue(
                    rootSchema,
                    path + name,
                    VariantKind.Identifier.id.toString(),
                    element
                )
            if (serializedId != descriptor.id.toString()) {
                throw invalidJsonValue(
                    rootSchema,
                    path + name,
                    descriptor.id.toString(),
                    element,
                    SerializerValueDomain.Literal(listOf(descriptor.id.toString()))
                )
            }
            values[name] = Variant.String(serializedId)
            continue
        }

        val property = properties[name]
        if (property == null) {
            if (!shape.strict) continue
            throw SchemaJsonException(
                errorMessage = "Unknown property for schema ${rootSchema.id} at " +
                    "'${(path + name).renderPath()}'.",
                expected = "registered property name",
                found = JsonPrimitive(name).toString(),
                availableEntries = SerializerValueDomain.Literal(properties.keys.sorted())
            )
        }
        values[name] = element.toVariant(
            property.serializerDescriptor,
            rootSchema,
            path + name
        )
    }
    if (shape.tagged && SCHEMA_TYPE_DISCRIMINATOR !in jsonObject) {
        values[SCHEMA_TYPE_DISCRIMINATOR] =
            Variant.String(descriptor.id.toString())
    }
    return Variant.Map(values)
}

private fun JsonElement.toPrimitiveVariant(
    shape: SerializerShape.Primitive,
    descriptor: SerializerDescriptor<*>,
    rootSchema: Schema<*>,
    path: List<String>
): Variant {
    fun invalid(cause: Throwable? = null): Nothing = throw invalidJsonValue(
        schema = rootSchema,
        path = path,
        expected = descriptor.id.toString(),
        actual = this,
        availableEntries = descriptor.valueDomain,
        cause = cause
    )

    if (shape.kind == VariantKind.Any) return toVariant()
    if (shape.kind == VariantKind.Null) {
        return if (this === JsonNull) Variant.Null else invalid()
    }
    if (shape.kind == VariantKind.List) {
        val values = this as? JsonArray ?: invalid()
        return Variant.List(values.map(JsonElement::toVariant))
    }
    if (shape.kind == VariantKind.Map) {
        val values = this as? JsonObject ?: invalid()
        return Variant.Map(values.mapValues { (_, value) -> value.toVariant() })
    }
    val primitive = this as? JsonPrimitive ?: invalid()
    return try {
        when (shape.kind) {
            VariantKind.Any -> toVariant()
            VariantKind.Null -> Variant.Null
            VariantKind.String -> {
                if (!primitive.isString) invalid()
                descriptor.valueDomain.requireContains(primitive.content, ::invalid)
                Variant.String(primitive.content)
            }
            VariantKind.Boolean ->
                if (!primitive.isString && primitive.booleanOrNull != null) {
                    Variant.Boolean(primitive.boolean)
                } else {
                    invalid()
                }
            VariantKind.Byte ->
                if (!primitive.isString) Variant.Byte(primitive.content.toByte()) else invalid()
            VariantKind.Short ->
                if (!primitive.isString) Variant.Short(primitive.content.toShort()) else invalid()
            VariantKind.Int ->
                if (!primitive.isString) Variant.Int(primitive.content.toInt()) else invalid()
            VariantKind.Long ->
                if (!primitive.isString) Variant.Long(primitive.content.toLong()) else invalid()
            VariantKind.Float ->
                if (!primitive.isString) Variant.Float(primitive.content.toFloat()) else invalid()
            VariantKind.Double ->
                if (!primitive.isString) Variant.Double(primitive.content.toDouble()) else invalid()
            VariantKind.Char ->
                if (primitive.isString && primitive.content.length == 1) {
                    Variant.Char(primitive.content.single())
                } else {
                    invalid()
                }
            VariantKind.Identifier -> {
                if (!primitive.isString) invalid()
                val identifier = idOrNull(primitive.content) ?: invalid()
                descriptor.valueDomain.requireContains(identifier.toString(), ::invalid)
                Variant.Identifier(identifier)
            }
            VariantKind.ResourceRef -> {
                if (!primitive.isString) invalid()
                Variant.ResourceRef(ref<Resource>(primitive.content))
            }
            VariantKind.List,
            VariantKind.Map -> error("Composite VariantKind was handled before primitive conversion")
        }
    } catch (exception: SchemaJsonException) {
        throw exception
    } catch (exception: Exception) {
        invalid(exception)
    }
}

private fun SerializerValueDomain?.requireContains(
    value: String,
    invalid: (Throwable?) -> Nothing
) {
    when (this) {
        is SerializerValueDomain.Literal -> if (value !in values) invalid(null)
        is SerializerValueDomain.Enum -> if (value !in values) invalid(null)
        is SerializerValueDomain.Registry -> {
            val registry = IdentifierRegistry.AllRegistries.getOrNull(registryId)
            if (registry != null) {
                val identifier = idOrNull(value) ?: invalid(null)
                if (!registry.has(identifier)) invalid(null)
            }
        }
        null -> Unit
    }
}

private fun invalidJsonValue(
    schema: Schema<*>,
    path: List<String>,
    expected: String,
    actual: JsonElement,
    availableEntries: SerializerValueDomain? = null,
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
        val expected = property?.serializerDescriptor?.id?.toString() ?: "value"
        return SchemaJsonException(
            errorMessage = "Missing required property for schema ${rootSchema.id} at " +
                "'${(path + missingName).renderPath()}'.",
            expected = expected,
            found = "<missing>",
            availableEntries = property?.serializerDescriptor?.valueDomain,
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
    Regex("""Missing (?:required )?property '([^']+)'""")

private fun Exception.parserMessage(): String =
    message
        ?.substringBefore("\nJSON input:")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: "malformed JSON"

internal const val MAX_INLINE_AVAILABLE_ENTRIES: Int = 10
