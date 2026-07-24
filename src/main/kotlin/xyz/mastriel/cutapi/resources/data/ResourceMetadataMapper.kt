package xyz.mastriel.cutapi.resources.data

import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import kotlin.math.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

public data class ResourceMappingContext(
    public val strictUnknownKeys: Boolean = false,
    public val warning: (String) -> Unit = {}
)

/** Extension point for field types that are not handled by the standard reflection mapper. */
public interface ResourceValueCodec : Identifiable {
    public fun supports(type: KType): Boolean

    public fun decode(
        value: ResourceConfigValue,
        type: KType,
        context: ResourceMappingContext,
        path: String
    ): Any?

    public fun encode(value: Any, type: KType): ResourceConfigValue

    public companion object : IdentifierRegistry<ResourceValueCodec>(id("cutapi:registry/resource_value_codec")) {
        internal fun find(type: KType): ResourceValueCodec? =
            getAllValues().firstOrNull { it.supports(type) }
    }
}

/** Maps configuration trees to constructor-backed Kotlin objects without kotlinx.serialization. */
public object ResourceMetadataMapper {
    private val collectionTypes: Set<KClass<*>> = setOf(List::class, MutableList::class, Collection::class)
    private val setTypes: Set<KClass<*>> = setOf(Set::class, MutableSet::class)
    private val mapTypes: Set<KClass<*>> = setOf(Map::class, MutableMap::class)

    public fun metadataId(type: KClass<out CuTMeta>): Identifier {
        val annotation = type.findAnnotation<ResourceMetadata>()
            ?: throw IllegalArgumentException(
                "Metadata class ${type.qualifiedName} must be annotated with @ResourceMetadata."
            )
        val constructor = type.primaryConstructor
            ?: throw IllegalArgumentException("Metadata class ${type.qualifiedName} must have a primary constructor.")
        require(constructor.visibility == KVisibility.PUBLIC) {
            "Metadata class ${type.qualifiedName} must have a public primary constructor."
        }
        val properties = type.memberProperties.associateBy { it.name }
        for (parameter in constructor.parameters.filter { it.kind == KParameter.Kind.VALUE }) {
            val property = properties[parameter.name]
            require(property != null && property.visibility == KVisibility.PUBLIC) {
                "Metadata constructor parameter '${parameter.name}' on ${type.qualifiedName} must be a public property."
            }
        }
        return id(annotation.id)
    }

    public fun <T : Any> decode(
        type: KClass<T>,
        value: ResourceConfigValue,
        context: ResourceMappingContext = ResourceMappingContext()
    ): T {
        @Suppress("UNCHECKED_CAST")
        return decodeValue(type.createType(), value, context, "$") as T
    }

    public inline fun <reified T : Any> decode(
        value: ResourceConfigValue,
        context: ResourceMappingContext = ResourceMappingContext()
    ): T = decode(T::class, value, context)

    public fun <M : CuTMeta> decodeMetadata(
        type: KClass<M>,
        value: ResourceConfigMap,
        context: ResourceMappingContext = ResourceMappingContext()
    ): M {
        val metadata = decode(type, value.without("extends", "clone", "generate"), context)
        metadata.setGenerateBlocks(parseGenerateBlocks(value["generate"]))
        return metadata
    }

    public fun encodeMetadata(metadata: CuTMeta): ResourceConfigDocument {
        val type = metadata::class
        val root = encodeObject(metadata, type.createType()).asConfigMap()
        val withGenerate = if (metadata.generateBlocks.isEmpty()) {
            root
        } else {
            root.copy(values = root.values + ("generate" to encodeGenerateBlocks(metadata.generateBlocks)))
        }
        return ResourceConfigDocument(metadataId(type), withGenerate, "<generated>")
    }

    public fun encode(value: Any, type: KType = value::class.createType()): ResourceConfigValue =
        encodeValue(value, type)

    public fun <T : Any> merge(
        type: KClass<T>,
        base: T,
        overlay: T,
        context: ResourceMappingContext = ResourceMappingContext()
    ): T {
        val targetType = type.createType()
        val combined = encodeValue(base, targetType).combine(
            encodeValue(overlay, targetType),
            combineLists = true
        )
        return decode(type, combined, context)
    }

    private fun decodeValue(
        type: KType,
        value: ResourceConfigValue,
        context: ResourceMappingContext,
        path: String
    ): Any? {
        if (value is ResourceConfigScalar && value.value == null) {
            if (type.isMarkedNullable) return null
            throw mappingError("$path cannot be null; expected $type.", value)
        }

        ResourceValueCodec.find(type)?.let { return it.decode(value, type, context, path) }

        val classifier = type.classifier as? KClass<*>
            ?: throw mappingError("$path has unsupported target type $type.", value)

        if (classifier == ResourceConfigValue::class) return value
        if (classifier == ResourceConfigMap::class) return value.asConfigMap()
        if (classifier == ResourceConfigList::class) return value.asConfigList()
        if (classifier == TaggedResourceConfig::class) return value as? TaggedResourceConfig
            ?: throw mappingError("$path must be a tagged value.", value)

        if (classifier == Any::class) return decodeUntyped(value, context, path)
        if (classifier == String::class) return scalar<String>(value, path)
        if (classifier == Char::class) {
            val text = scalar<String>(value, path)
            if (text.length != 1) throw mappingError("$path must contain exactly one character.", value)
            return text.single()
        }
        if (classifier == Boolean::class) return scalar<Boolean>(value, path)
        if (classifier == Byte::class) return integer(value, path).checked(path, value, Byte.MIN_VALUE..Byte.MAX_VALUE)
            .toByte()
        if (classifier == Short::class) return integer(value, path).checked(
            path,
            value,
            Short.MIN_VALUE..Short.MAX_VALUE
        ).toShort()
        if (classifier == Int::class) return integer(value, path).checked(path, value, Int.MIN_VALUE..Int.MAX_VALUE)
            .toInt()
        if (classifier == Long::class) return integer(value, path)
        if (classifier == UByte::class) return integer(value, path).checked(path, value, 0..UByte.MAX_VALUE.toInt())
            .toUByte()
        if (classifier == UShort::class) return integer(value, path).checked(path, value, 0..UShort.MAX_VALUE.toInt())
            .toUShort()
        if (classifier == UInt::class) {
            val number = integer(value, path)
            if (number !in 0..UInt.MAX_VALUE.toLong()) throw mappingError("$path is outside UInt range.", value)
            return number.toUInt()
        }
        if (classifier == ULong::class) {
            val number = integer(value, path)
            if (number < 0) throw mappingError("$path is outside ULong range.", value)
            return number.toULong()
        }
        if (classifier == Float::class) return decimal(value, path).also {
            if (abs(it) > Float.MAX_VALUE) throw mappingError("$path is outside Float range.", value)
        }.toFloat()
        if (classifier == Double::class) return decimal(value, path)
        if (classifier == Identifier::class) return id(scalar<String>(value, path))
        if (classifier == ResourceRef::class) return ref<Resource>(scalar<String>(value, path))

        if (classifier.java.isEnum) return decodeEnum(classifier, value, path)

        if (classifier in collectionTypes || classifier in setTypes) {
            val elementType = type.arguments.singleOrNull()?.type
                ?: throw mappingError("$path must declare a collection element type.", value)
            val decoded = value.asConfigList().mapIndexed { index, child ->
                decodeValue(elementType, child, context, "$path[$index]")
            }
            return if (classifier in setTypes) decoded.toSet() else decoded
        }

        if (classifier in mapTypes) {
            val keyType = type.arguments.getOrNull(0)?.type
                ?: throw mappingError("$path must declare a map key type.", value)
            val valueType = type.arguments.getOrNull(1)?.type
                ?: throw mappingError("$path must declare a map value type.", value)
            return value.asConfigMap().mapValues { (key, child) ->
                decodeValue(valueType, child, context, "$path.$key")
            }.mapKeys { (key) -> decodeMapKey(key, keyType, value, path) }
        }

        return decodeObject(classifier, type, value, context, path)
    }

    private fun decodeObject(
        classifier: KClass<*>,
        type: KType,
        value: ResourceConfigValue,
        context: ResourceMappingContext,
        path: String
    ): Any {
        val map = value.asConfigMap()
        val constructor = classifier.primaryConstructor
            ?: throw mappingError("$path target $type must have a primary constructor.", value)
        constructor.isAccessible = true

        val properties = classifier.memberProperties.associateBy { it.name }
        val persistedParameters = constructor.parameters.filter { parameter ->
            parameter.kind == KParameter.Kind.VALUE &&
                parameter.findAnnotation<ResourceIgnore>() == null &&
                properties[parameter.name]?.findAnnotation<ResourceIgnore>() == null
        }
        val parametersByKey = persistedParameters.associateBy { parameter ->
            parameter.findAnnotation<ResourceKey>()?.value
                ?: properties[parameter.name]?.findAnnotation<ResourceKey>()?.value
                ?: parameter.name
                ?: throw mappingError("$path target $type has an unnamed constructor parameter.", value)
        }

        val unknown = map.keys - parametersByKey.keys
        for (key in unknown) {
            val child = map.getValue(key)
            val message = "Unknown metadata key '$path.$key'."
            if (context.strictUnknownKeys) throw mappingError(message, child)
            context.warning(message + (child.span?.let { " ($it)" } ?: ""))
        }

        val arguments = mutableMapOf<KParameter, Any?>()
        for ((key, parameter) in parametersByKey) {
            val child = map[key]
            if (child == null) {
                if (!parameter.isOptional) {
                    throw mappingError("Missing required metadata key '$path.$key'.", value)
                }
                continue
            }
            arguments[parameter] = decodeValue(parameter.type, child, context, "$path.$key")
        }

        return try {
            constructor.callBy(arguments)
        } catch (exception: ResourceConfigException) {
            throw exception
        } catch (exception: Exception) {
            throw mappingError(
                "Could not construct $type: ${exception.cause?.message ?: exception.message}",
                value,
                exception
            )
        }
    }

    private fun encodeValue(value: Any?, type: KType): ResourceConfigValue {
        if (value == null) return ResourceConfigScalar(null)
        ResourceValueCodec.find(type)?.let { return it.encode(value, type) }

        return when (value) {
            is ResourceConfigValue -> value
            is String, is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double -> ResourceConfigScalar(value)
            is UByte -> ResourceConfigScalar(value.toLong())
            is UShort -> ResourceConfigScalar(value.toLong())
            is UInt -> ResourceConfigScalar(value.toLong())
            is ULong -> {
                require(value <= Long.MAX_VALUE.toULong()) { "ULong values above Long.MAX_VALUE cannot be written to resource YAML." }
                ResourceConfigScalar(value.toLong())
            }

            is Char -> ResourceConfigScalar(value.toString())
            is Identifier -> ResourceConfigScalar(value.toString())
            is ResourceRef<*> -> ResourceConfigScalar(value.toString())
            is Enum<*> -> ResourceConfigScalar(enumValue(value))
            is Collection<*> -> {
                val elementType = type.arguments.singleOrNull()?.type
                ResourceConfigList(value.map { child ->
                    val childType = elementType
                        ?: child?.let { it::class.createType() }
                        ?: Any::class.createType(nullable = true)
                    encodeValue(child, childType)
                })
            }

            is Map<*, *> -> {
                val valueType = type.arguments.getOrNull(1)?.type
                ResourceConfigMap(value.entries.associate { (key, child) ->
                    val encodedKey = encodeMapKey(requireNotNull(key))
                    encodedKey to encodeValue(
                        child,
                        valueType ?: child?.let { it::class.createType() } ?: Any::class.createType(nullable = true))
                })
            }

            else -> encodeObject(value, type)
        }
    }

    private fun encodeObject(value: Any, type: KType): ResourceConfigValue {
        val classifier = type.classifier as? KClass<*> ?: value::class
        val constructor = classifier.primaryConstructor
            ?: throw IllegalArgumentException("${classifier.qualifiedName} must have a primary constructor to be written as resource metadata.")
        val properties = classifier.memberProperties.associateBy { it.name }
        val values = linkedMapOf<String, ResourceConfigValue>()
        for (parameter in constructor.parameters.filter { it.kind == KParameter.Kind.VALUE }) {
            val property = properties[parameter.name] ?: continue
            if (parameter.findAnnotation<ResourceIgnore>() != null || property.findAnnotation<ResourceIgnore>() != null) continue
            property.isAccessible = true
            val key = parameter.findAnnotation<ResourceKey>()?.value
                ?: property.findAnnotation<ResourceKey>()?.value
                ?: parameter.name
                ?: continue

            @Suppress("UNCHECKED_CAST")
            val propertyValue = (property as KProperty1<Any, *>).get(value)
            values[key] = encodeValue(propertyValue, parameter.type)
        }
        return ResourceConfigMap(values)
    }

    private fun decodeUntyped(
        value: ResourceConfigValue,
        context: ResourceMappingContext,
        path: String
    ): Any? = when (value) {
        is ResourceConfigScalar -> value.value
        is ResourceConfigList -> value.mapIndexed { index, child -> decodeUntyped(child, context, "$path[$index]") }
        is ResourceConfigMap -> value.mapValues { (key, child) -> decodeUntyped(child, context, "$path.$key") }
        is TaggedResourceConfig -> value
    }

    private fun parseGenerateBlocks(value: ResourceConfigValue?): List<GenerateBlock> {
        if (value == null) return emptyList()
        return value.asConfigList().mapIndexed { index, child ->
            val tagged = child as? TaggedResourceConfig
                ?: throw mappingError("$.generate[$index] must have a generator ID tag.", child)
            val options = tagged.value.asConfigMap()
            val subIdValue = options["subId"]
            val subId = subIdValue?.let { scalar<String>(it, "$.generate[$index].subId") }
            GenerateBlock(tagged.id, subId, options.without("subId"))
        }
    }

    private fun encodeGenerateBlocks(blocks: List<GenerateBlock>): ResourceConfigList = ResourceConfigList(
        blocks.map { block ->
            val values = if (block.subId == null) block.options.values else
                linkedMapOf("subId" to ResourceConfigScalar(block.subId)) + block.options.values
            TaggedResourceConfig(block.generatorId, ResourceConfigMap(values))
        }
    )

    private fun decodeMapKey(key: String, type: KType, value: ResourceConfigValue, path: String): Any {
        val classifier = type.classifier as? KClass<*>
            ?: throw mappingError("$path has unsupported map key type $type.", value)
        return when {
            classifier == String::class -> key
            classifier == Int::class -> key.toIntOrNull() ?: throw mappingError("Map key '$key' is not an Int.", value)
            classifier == Long::class -> key.toLongOrNull() ?: throw mappingError(
                "Map key '$key' is not a Long.",
                value
            )

            classifier == Identifier::class -> id(key)
            classifier.java.isEnum -> decodeEnum(classifier, ResourceConfigScalar(key, value.span), path)
            else -> throw mappingError("$path has unsupported map key type $type.", value)
        }
    }

    private fun encodeMapKey(value: Any): String = when (value) {
        is String -> value
        is Int, is Long -> value.toString()
        is Identifier -> value.toString()
        is Enum<*> -> enumValue(value)
        else -> throw IllegalArgumentException("Unsupported resource metadata map key type ${value::class.qualifiedName}.")
    }

    private fun decodeEnum(type: KClass<*>, value: ResourceConfigValue, path: String): Any {
        val text = scalar<String>(value, path)
        return type.java.enumConstants.firstOrNull { enumValue(it as Enum<*>) == text }
            ?: throw mappingError("$path has unknown ${type.simpleName} value '$text'.", value)
    }

    private fun enumValue(value: Enum<*>): String {
        val field = value.javaClass.getField(value.name)
        return field.getAnnotation(ResourceValue::class.java)?.value ?: value.name
    }

    private inline fun <reified T> scalar(value: ResourceConfigValue, path: String): T {
        val scalar = value as? ResourceConfigScalar
            ?: throw mappingError("$path must be a ${T::class.simpleName} scalar.", value)
        return scalar.value as? T
            ?: throw mappingError("$path must be a ${T::class.simpleName} scalar.", value)
    }

    private fun integer(value: ResourceConfigValue, path: String): Long {
        val scalar = value as? ResourceConfigScalar
            ?: throw mappingError("$path must be an integer.", value)
        return scalar.value as? Long
            ?: throw mappingError("$path must be an integer.", value)
    }

    private fun decimal(value: ResourceConfigValue, path: String): Double {
        val scalar = value as? ResourceConfigScalar
            ?: throw mappingError("$path must be a number.", value)
        return when (val number = scalar.value) {
            is Long -> number.toDouble()
            is Double -> number
            else -> throw mappingError("$path must be a number.", value)
        }
    }

    private fun Long.checked(path: String, value: ResourceConfigValue, range: IntRange): Long {
        if (this !in range.first.toLong()..range.last.toLong()) {
            throw mappingError("$path is outside the supported range.", value)
        }
        return this
    }

    private fun mappingError(
        message: String,
        value: ResourceConfigValue,
        cause: Throwable? = null
    ): ResourceConfigException = ResourceConfigException(message, value.span, cause)
}
