package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*

/** Schema facade over the descriptor-driven native PDC representation. */
internal object SchemaPdcCodec {
    fun <T : Any> encode(
        context: PersistentDataAdapterContext,
        schema: Schema<T>,
        value: T
    ): PersistentDataContainer {
        val variant = schema.serialize(value).getOrThrow()
        return DescriptorPdcCodec.encode(
            context = context,
            descriptor = schema.descriptor,
            variant = variant,
            defaultNamespace = schema.id.namespace
        )
    }

    fun <T : Any> decode(
        schema: Schema<T>,
        container: PersistentDataContainer
    ): T {
        val variant = DescriptorPdcCodec.decode(
            container = container,
            descriptor = schema.descriptor,
            defaultNamespace = schema.id.namespace
        )
        return schema.deserialize(variant).getOrThrow()
    }
}

/** Maps statically described Variant values to native PDC types. */
internal object DescriptorPdcCodec {
    private val SchemaTypeKey = id("cutapi:codec/schema_type").toNamespacedKey()
    private val ListValueKey = id("cutapi:codec/value").toNamespacedKey()
    private val NativeKeyPattern = Regex("[a-z0-9._/-]+")

    fun encode(
        context: PersistentDataAdapterContext,
        descriptor: SerializerDescriptor<*>,
        variant: Variant,
        defaultNamespace: String
    ): PersistentDataContainer = context.newPersistentDataContainer().also { container ->
        encodeRoot(container, descriptor, variant, defaultNamespace, descriptor.id.toString())
    }

    fun decode(
        container: PersistentDataContainer,
        descriptor: SerializerDescriptor<*>,
        defaultNamespace: String
    ): Variant = decodeRoot(container, descriptor, defaultNamespace, descriptor.id.toString())

    private fun encodeRoot(
        container: PersistentDataContainer,
        descriptor: SerializerDescriptor<*>,
        variant: Variant,
        defaultNamespace: String,
        path: String
    ) {
        when (val shape = descriptor.shape) {
            is SerializerShape.Mapped -> encodeRoot(container, shape.encoded, variant, defaultNamespace, path)
            is SerializerShape.Object -> encodeObject(container, descriptor, shape, variant, path)
            is SerializerShape.Polymorphic -> encodePolymorphic(container, shape, variant, path)
            else -> throw DataSerializationException(
                "Schema PDC root '$path' must be an object or polymorphic object, found ${shape::class.simpleName}"
            )
        }
    }

    private fun decodeRoot(
        container: PersistentDataContainer,
        descriptor: SerializerDescriptor<*>,
        defaultNamespace: String,
        path: String
    ): Variant = when (val shape = descriptor.shape) {
        is SerializerShape.Mapped -> decodeRoot(container, shape.encoded, defaultNamespace, path)
        is SerializerShape.Object -> decodeObject(container, descriptor, shape, path)
        is SerializerShape.Polymorphic -> decodePolymorphic(container, shape, path)
        else -> throw DataSerializationException(
            "Schema PDC root '$path' must be an object or polymorphic object, found ${shape::class.simpleName}"
        )
    }

    private fun write(
        container: PersistentDataContainer,
        key: NamespacedKey,
        descriptor: SerializerDescriptor<*>,
        variant: Variant,
        defaultNamespace: String,
        path: String
    ) {
        when (val shape = descriptor.shape) {
            is SerializerShape.Nullable -> {
                if (variant == Variant.Null) {
                    container.set(key, PersistentDataType.BYTE_ARRAY, byteArrayOf())
                } else {
                    write(container, key, shape.value, variant, defaultNamespace, path)
                }
            }

            is SerializerShape.Mapped -> write(container, key, shape.encoded, variant, defaultNamespace, path)
            is SerializerShape.Primitive -> writePrimitive(container, key, shape.kind, variant, path)
            is SerializerShape.List -> writeList(
                container,
                key,
                shape.element,
                expectVariant<Variant.List>(variant, descriptor, path).value,
                defaultNamespace,
                path
            )

            is SerializerShape.Map -> {
                val child = container.adapterContext.newPersistentDataContainer()
                encodeMap(child, shape, expectVariant<Variant.Map>(variant, descriptor, path), defaultNamespace, path)
                container.set(key, PersistentDataType.TAG_CONTAINER, child)
            }

            is SerializerShape.Object -> {
                val child = container.adapterContext.newPersistentDataContainer()
                encodeObject(child, descriptor, shape, variant, path)
                container.set(key, PersistentDataType.TAG_CONTAINER, child)
            }

            is SerializerShape.Polymorphic -> {
                val child = container.adapterContext.newPersistentDataContainer()
                encodePolymorphic(child, shape, variant, path)
                container.set(key, PersistentDataType.TAG_CONTAINER, child)
            }

            SerializerShape.Opaque -> writeTagged(container, key, variant)
        }
    }

    private fun read(
        container: PersistentDataContainer,
        key: NamespacedKey,
        descriptor: SerializerDescriptor<*>,
        defaultNamespace: String,
        path: String
    ): Variant = when (val shape = descriptor.shape) {
        is SerializerShape.Nullable -> {
            if (container.has(key, PersistentDataType.BYTE_ARRAY)) {
                val marker = required(container, key, PersistentDataType.BYTE_ARRAY, descriptor, path)
                requireData(marker.isEmpty(), path, "explicit null marker must be an empty byte array")
                Variant.Null
            } else {
                read(container, key, shape.value, defaultNamespace, path)
            }
        }

        is SerializerShape.Mapped -> read(container, key, shape.encoded, defaultNamespace, path)
        is SerializerShape.Primitive -> readPrimitive(container, key, shape.kind, descriptor, path)
        is SerializerShape.List -> Variant.List(readList(container, key, shape.element, defaultNamespace, path))
        is SerializerShape.Map -> decodeMap(
            required(container, key, PersistentDataType.TAG_CONTAINER, descriptor, path),
            shape,
            defaultNamespace,
            path
        )

        is SerializerShape.Object -> decodeObject(
            required(container, key, PersistentDataType.TAG_CONTAINER, descriptor, path),
            descriptor,
            shape,
            path
        )

        is SerializerShape.Polymorphic -> decodePolymorphic(
            required(container, key, PersistentDataType.TAG_CONTAINER, descriptor, path),
            shape,
            path
        )

        SerializerShape.Opaque -> readTagged(container, key, descriptor, path)
    }

    private fun writePrimitive(
        container: PersistentDataContainer,
        key: NamespacedKey,
        kind: VariantKind,
        variant: Variant,
        path: String
    ) {
        when (kind) {
            VariantKind.Any -> writeTagged(container, key, variant)
            VariantKind.Null -> {
                expectVariant<Variant.Null>(variant, kind.id, path)
                container.set(key, PersistentDataType.BYTE_ARRAY, byteArrayOf())
            }

            VariantKind.String -> container.set(
                key,
                PersistentDataType.STRING,
                expectVariant<Variant.String>(variant, kind.id, path).value
            )

            VariantKind.Boolean -> container.set(
                key,
                PersistentDataType.BOOLEAN,
                expectVariant<Variant.Boolean>(variant, kind.id, path).value
            )

            VariantKind.Byte -> container.set(
                key,
                PersistentDataType.BYTE,
                expectVariant<Variant.Byte>(variant, kind.id, path).value
            )

            VariantKind.Short -> container.set(
                key,
                PersistentDataType.SHORT,
                expectVariant<Variant.Short>(variant, kind.id, path).value
            )

            VariantKind.Int -> container.set(
                key,
                PersistentDataType.INTEGER,
                expectVariant<Variant.Int>(variant, kind.id, path).value
            )

            VariantKind.Long -> container.set(
                key,
                PersistentDataType.LONG,
                expectVariant<Variant.Long>(variant, kind.id, path).value
            )

            VariantKind.Float -> container.set(
                key,
                PersistentDataType.FLOAT,
                expectVariant<Variant.Float>(variant, kind.id, path).value
            )

            VariantKind.Double -> container.set(
                key,
                PersistentDataType.DOUBLE,
                expectVariant<Variant.Double>(variant, kind.id, path).value
            )

            VariantKind.Char -> container.set(
                key,
                PersistentDataType.STRING,
                expectVariant<Variant.Char>(variant, kind.id, path).value.toString()
            )

            VariantKind.Identifier -> container.set(
                key,
                PersistentDataType.STRING,
                expectVariant<Variant.Identifier>(variant, kind.id, path).value.toString()
            )

            VariantKind.ResourceRef -> container.set(
                key,
                PersistentDataType.STRING,
                expectVariant<Variant.ResourceRef>(variant, kind.id, path).value.toString()
            )

            VariantKind.List,
            VariantKind.Map -> writeTagged(container, key, variant)
        }
    }

    private fun readPrimitive(
        container: PersistentDataContainer,
        key: NamespacedKey,
        kind: VariantKind,
        descriptor: SerializerDescriptor<*>,
        path: String
    ): Variant = when (kind) {
        VariantKind.Any,
        VariantKind.List,
        VariantKind.Map -> readTagged(container, key, descriptor, path)

        VariantKind.Null -> {
            val marker = required(container, key, PersistentDataType.BYTE_ARRAY, descriptor, path)
            requireData(marker.isEmpty(), path, "null marker must be an empty byte array")
            Variant.Null
        }

        VariantKind.String -> Variant.String(required(container, key, PersistentDataType.STRING, descriptor, path))
        VariantKind.Boolean -> Variant.Boolean(required(container, key, PersistentDataType.BOOLEAN, descriptor, path))
        VariantKind.Byte -> Variant.Byte(required(container, key, PersistentDataType.BYTE, descriptor, path))
        VariantKind.Short -> Variant.Short(required(container, key, PersistentDataType.SHORT, descriptor, path))
        VariantKind.Int -> Variant.Int(required(container, key, PersistentDataType.INTEGER, descriptor, path))
        VariantKind.Long -> Variant.Long(required(container, key, PersistentDataType.LONG, descriptor, path))
        VariantKind.Float -> Variant.Float(required(container, key, PersistentDataType.FLOAT, descriptor, path))
        VariantKind.Double -> Variant.Double(required(container, key, PersistentDataType.DOUBLE, descriptor, path))
        VariantKind.Char -> {
            val value = required(container, key, PersistentDataType.STRING, descriptor, path)
            requireData(value.length == 1, path, "character value must contain exactly one character")
            Variant.Char(value.single())
        }

        VariantKind.Identifier -> Variant.Identifier(id(required(container, key, PersistentDataType.STRING, descriptor, path)))
        VariantKind.ResourceRef -> Variant.ResourceRef(
            ref<Resource>(required(container, key, PersistentDataType.STRING, descriptor, path))
        )
    }

    private fun encodeObject(
        container: PersistentDataContainer,
        descriptor: SerializerDescriptor<*>,
        shape: SerializerShape.Object,
        variant: Variant,
        path: String
    ) {
        val values = expectVariant<Variant.Map>(variant, descriptor, path).value
        val serializedType = values[SCHEMA_TYPE_DISCRIMINATOR]
        if (shape.tagged) {
            requireData(
                (serializedType as? Variant.String)?.value == descriptor.id.toString(),
                path,
                "expected '$SCHEMA_TYPE_DISCRIMINATOR' to equal '${descriptor.id}'"
            )
        } else {
            requireData(serializedType == null, path, "untagged object cannot contain '$SCHEMA_TYPE_DISCRIMINATOR'")
        }

        val properties = shape.properties.associateBy { it.name }
        val unknown = values.keys - properties.keys - SCHEMA_TYPE_DISCRIMINATOR
        requireData(unknown.isEmpty(), path, "unknown properties: ${unknown.joinToString()}")

        for ((name, property) in properties) {
            val value = values[name] ?: continue
            val propertyKey = nativePropertyKey(descriptor.id.namespace, name, "$path.$name")
            write(
                container,
                propertyKey,
                property.serializerDescriptor,
                value,
                descriptor.id.namespace,
                "$path.$name"
            )
        }
    }

    private fun decodeObject(
        container: PersistentDataContainer,
        descriptor: SerializerDescriptor<*>,
        shape: SerializerShape.Object,
        path: String,
        allowedMetadata: Set<NamespacedKey> = emptySet()
    ): Variant.Map {
        val properties = shape.properties.associateBy { property ->
            nativePropertyKey(descriptor.id.namespace, property.name, "$path.${property.name}")
        }
        val unknown = container.keys - properties.keys - allowedMetadata
        requireData(unknown.isEmpty(), path, "unknown PDC keys: ${unknown.joinToString()}")

        val values = linkedMapOf<String, Variant>()
        if (shape.tagged) values[SCHEMA_TYPE_DISCRIMINATOR] = Variant.String(descriptor.id.toString())
        for ((key, property) in properties) {
            if (!container.has(key)) continue
            values[property.name] = read(
                container,
                key,
                property.serializerDescriptor,
                descriptor.id.namespace,
                "$path.${property.name}"
            )
        }
        return Variant.Map(values)
    }

    private fun encodePolymorphic(
        container: PersistentDataContainer,
        shape: SerializerShape.Polymorphic,
        variant: Variant,
        path: String
    ) {
        val values = expectVariant<Variant.Map>(variant, shape.base.id, path)
        val serializedType = (values[SCHEMA_TYPE_DISCRIMINATOR] as? Variant.String)?.value
        val selected = selectPolymorphicDescriptor(shape, serializedType, path)
        container.set(SchemaTypeKey, PersistentDataType.STRING, selected.id.toString())
        encodeObject(container, selected, selected.shape, variant, path)
    }

    private fun decodePolymorphic(
        container: PersistentDataContainer,
        shape: SerializerShape.Polymorphic,
        path: String
    ): Variant.Map {
        val serializedType = required(
            container,
            SchemaTypeKey,
            PersistentDataType.STRING,
            shape.base,
            "$path.$SCHEMA_TYPE_DISCRIMINATOR"
        )
        val selected = selectPolymorphicDescriptor(shape, serializedType, path)
        return decodeObject(container, selected, selected.shape, path, setOf(SchemaTypeKey))
    }

    private fun selectPolymorphicDescriptor(
        shape: SerializerShape.Polymorphic,
        serializedType: String?,
        path: String
    ): SerializerDescriptor<SerializerShape.Object> {
        val descriptors = listOf(shape.base) + shape.included
        if (serializedType == null && !shape.base.shape.tagged) return shape.base
        return descriptors.singleOrNull { it.id.toString() == serializedType }
            ?: throw DataSerializationException(
                "Invalid schema PDC at '$path': unknown polymorphic type '$serializedType'; expected one of " +
                    descriptors.joinToString { it.id.toString() }
            )
    }

    private fun encodeMap(
        container: PersistentDataContainer,
        shape: SerializerShape.Map,
        variant: Variant.Map,
        defaultNamespace: String,
        path: String
    ) {
        val normalized = linkedMapOf<NamespacedKey, String>()
        for ((name, value) in variant.value) {
            val key = dynamicMapKey(name, defaultNamespace, "$path[$name]")
            val previous = normalized.put(key, name)
            requireData(
                previous == null,
                path,
                "map keys '$previous' and '$name' normalize to the same PDC key '$key'"
            )
            write(container, key, shape.value, value, defaultNamespace, "$path[$name]")
        }
    }

    private fun decodeMap(
        container: PersistentDataContainer,
        shape: SerializerShape.Map,
        defaultNamespace: String,
        path: String
    ): Variant.Map = Variant.Map(
        container.keys
            .sortedBy(NamespacedKey::toString)
            .associateTo(linkedMapOf()) { key ->
                requireNotReserved(key, "$path[$key]")
                val name = if (key.namespace == defaultNamespace) key.key else key.toString()
                name to read(container, key, shape.value, defaultNamespace, "$path[$name]")
            }
    )

    private fun writeList(
        container: PersistentDataContainer,
        key: NamespacedKey,
        element: SerializerDescriptor<*>,
        values: List<Variant>,
        defaultNamespace: String,
        path: String
    ) {
        when (val shape = element.shape) {
            is SerializerShape.Mapped -> writeList(container, key, shape.encoded, values, defaultNamespace, path)
            is SerializerShape.Nullable -> writeWrappedContainerList(
                container,
                key,
                element,
                values,
                defaultNamespace,
                path
            )

            is SerializerShape.Primitive -> writePrimitiveList(container, key, shape.kind, values, path)
            is SerializerShape.Object,
            is SerializerShape.Map,
            is SerializerShape.Polymorphic -> container.set(
                key,
                PersistentDataType.LIST.dataContainers(),
                values.mapIndexed { index, value ->
                    encodeContainerElement(container.adapterContext, element, value, defaultNamespace, "$path[$index]")
                }
            )

            is SerializerShape.List -> writeWrappedContainerList(
                container,
                key,
                element,
                values,
                defaultNamespace,
                path
            )

            SerializerShape.Opaque -> container.set(
                key,
                PersistentDataType.LIST.dataContainers(),
                values.map { VariantPdcCodec.encode(container.adapterContext, it) }
            )
        }
    }

    private fun readList(
        container: PersistentDataContainer,
        key: NamespacedKey,
        element: SerializerDescriptor<*>,
        defaultNamespace: String,
        path: String
    ): List<Variant> = when (val shape = element.shape) {
        is SerializerShape.Mapped -> readList(container, key, shape.encoded, defaultNamespace, path)
        is SerializerShape.Nullable -> readWrappedContainerList(container, key, element, defaultNamespace, path)
        is SerializerShape.Primitive -> readPrimitiveList(container, key, shape.kind, element, path)
        is SerializerShape.Object,
        is SerializerShape.Map,
        is SerializerShape.Polymorphic -> required(
            container,
            key,
            PersistentDataType.LIST.dataContainers(),
            element,
            path
        ).mapIndexed { index, value ->
            decodeContainerElement(value, element, defaultNamespace, "$path[$index]")
        }

        is SerializerShape.List -> readWrappedContainerList(container, key, element, defaultNamespace, path)
        SerializerShape.Opaque -> required(
            container,
            key,
            PersistentDataType.LIST.dataContainers(),
            element,
            path
        ).mapIndexed { index, value -> decodeTagged(value, element, "$path[$index]") }
    }

    private fun writePrimitiveList(
        container: PersistentDataContainer,
        key: NamespacedKey,
        kind: VariantKind,
        values: List<Variant>,
        path: String
    ) {
        when (kind) {
            VariantKind.Any,
            VariantKind.List,
            VariantKind.Map -> container.set(
                key,
                PersistentDataType.LIST.dataContainers(),
                values.map { VariantPdcCodec.encode(container.adapterContext, it) }
            )

            VariantKind.Null -> container.set(
                key,
                PersistentDataType.LIST.byteArrays(),
                values.mapIndexed { index, value ->
                    expectVariant<Variant.Null>(value, kind.id, "$path[$index]")
                    byteArrayOf()
                }
            )

            VariantKind.String -> container.set(
                key,
                PersistentDataType.LIST.strings(),
                values.valuesOf<Variant.String, String>(kind, path) { it.value }
            )

            VariantKind.Boolean -> container.set(
                key,
                PersistentDataType.LIST.booleans(),
                values.valuesOf<Variant.Boolean, Boolean>(kind, path) { it.value }
            )

            VariantKind.Byte -> container.set(
                key,
                PersistentDataType.LIST.bytes(),
                values.valuesOf<Variant.Byte, Byte>(kind, path) { it.value }
            )

            VariantKind.Short -> container.set(
                key,
                PersistentDataType.LIST.shorts(),
                values.valuesOf<Variant.Short, Short>(kind, path) { it.value }
            )

            VariantKind.Int -> container.set(
                key,
                PersistentDataType.LIST.integers(),
                values.valuesOf<Variant.Int, Int>(kind, path) { it.value }
            )

            VariantKind.Long -> container.set(
                key,
                PersistentDataType.LIST.longs(),
                values.valuesOf<Variant.Long, Long>(kind, path) { it.value }
            )

            VariantKind.Float -> container.set(
                key,
                PersistentDataType.LIST.floats(),
                values.valuesOf<Variant.Float, Float>(kind, path) { it.value }
            )

            VariantKind.Double -> container.set(
                key,
                PersistentDataType.LIST.doubles(),
                values.valuesOf<Variant.Double, Double>(kind, path) { it.value }
            )

            VariantKind.Char -> container.set(
                key,
                PersistentDataType.LIST.strings(),
                values.valuesOf<Variant.Char, String>(kind, path) { it.value.toString() }
            )

            VariantKind.Identifier -> container.set(
                key,
                PersistentDataType.LIST.strings(),
                values.valuesOf<Variant.Identifier, String>(kind, path) { it.value.toString() }
            )

            VariantKind.ResourceRef -> container.set(
                key,
                PersistentDataType.LIST.strings(),
                values.valuesOf<Variant.ResourceRef, String>(kind, path) { it.value.toString() }
            )
        }
    }

    private fun readPrimitiveList(
        container: PersistentDataContainer,
        key: NamespacedKey,
        kind: VariantKind,
        descriptor: SerializerDescriptor<*>,
        path: String
    ): List<Variant> = when (kind) {
        VariantKind.Any,
        VariantKind.List,
        VariantKind.Map -> required(
            container,
            key,
            PersistentDataType.LIST.dataContainers(),
            descriptor,
            path
        ).mapIndexed { index, value -> decodeTagged(value, descriptor, "$path[$index]") }

        VariantKind.Null -> required(
            container,
            key,
            PersistentDataType.LIST.byteArrays(),
            descriptor,
            path
        ).mapIndexed { index, marker ->
            requireData(marker.isEmpty(), "$path[$index]", "null marker must be an empty byte array")
            Variant.Null
        }

        VariantKind.String -> required(container, key, PersistentDataType.LIST.strings(), descriptor, path)
            .map(Variant::String)
        VariantKind.Boolean -> required(container, key, PersistentDataType.LIST.booleans(), descriptor, path)
            .map(Variant::Boolean)
        VariantKind.Byte -> required(container, key, PersistentDataType.LIST.bytes(), descriptor, path)
            .map(Variant::Byte)
        VariantKind.Short -> required(container, key, PersistentDataType.LIST.shorts(), descriptor, path)
            .map(Variant::Short)
        VariantKind.Int -> required(container, key, PersistentDataType.LIST.integers(), descriptor, path)
            .map(Variant::Int)
        VariantKind.Long -> required(container, key, PersistentDataType.LIST.longs(), descriptor, path)
            .map(Variant::Long)
        VariantKind.Float -> required(container, key, PersistentDataType.LIST.floats(), descriptor, path)
            .map(Variant::Float)
        VariantKind.Double -> required(container, key, PersistentDataType.LIST.doubles(), descriptor, path)
            .map(Variant::Double)
        VariantKind.Char -> required(container, key, PersistentDataType.LIST.strings(), descriptor, path)
            .mapIndexed { index, value ->
                requireData(value.length == 1, "$path[$index]", "character value must contain exactly one character")
                Variant.Char(value.single())
            }

        VariantKind.Identifier -> required(container, key, PersistentDataType.LIST.strings(), descriptor, path)
            .map { Variant.Identifier(id(it)) }
        VariantKind.ResourceRef -> required(container, key, PersistentDataType.LIST.strings(), descriptor, path)
            .map { Variant.ResourceRef(ref<Resource>(it)) }
    }

    private fun writeWrappedContainerList(
        container: PersistentDataContainer,
        key: NamespacedKey,
        element: SerializerDescriptor<*>,
        values: List<Variant>,
        defaultNamespace: String,
        path: String
    ) {
        container.set(
            key,
            PersistentDataType.LIST.dataContainers(),
            values.mapIndexed { index, value ->
                container.adapterContext.newPersistentDataContainer().also { child ->
                    write(child, ListValueKey, element, value, defaultNamespace, "$path[$index]")
                }
            }
        )
    }

    private fun readWrappedContainerList(
        container: PersistentDataContainer,
        key: NamespacedKey,
        element: SerializerDescriptor<*>,
        defaultNamespace: String,
        path: String
    ): List<Variant> = required(
        container,
        key,
        PersistentDataType.LIST.dataContainers(),
        element,
        path
    ).mapIndexed { index, child ->
        read(child, ListValueKey, element, defaultNamespace, "$path[$index]")
    }

    private fun encodeContainerElement(
        context: PersistentDataAdapterContext,
        descriptor: SerializerDescriptor<*>,
        variant: Variant,
        defaultNamespace: String,
        path: String
    ): PersistentDataContainer = context.newPersistentDataContainer().also { child ->
        when (val shape = descriptor.shape) {
            is SerializerShape.Mapped -> return encodeContainerElement(
                context,
                shape.encoded,
                variant,
                defaultNamespace,
                path
            )

            is SerializerShape.Object -> encodeObject(child, descriptor, shape, variant, path)
            is SerializerShape.Map -> encodeMap(
                child,
                shape,
                expectVariant<Variant.Map>(variant, descriptor, path),
                defaultNamespace,
                path
            )

            is SerializerShape.Polymorphic -> encodePolymorphic(child, shape, variant, path)
            else -> write(child, ListValueKey, descriptor, variant, defaultNamespace, path)
        }
    }

    private fun decodeContainerElement(
        container: PersistentDataContainer,
        descriptor: SerializerDescriptor<*>,
        defaultNamespace: String,
        path: String
    ): Variant = when (val shape = descriptor.shape) {
        is SerializerShape.Mapped -> decodeContainerElement(container, shape.encoded, defaultNamespace, path)
        is SerializerShape.Object -> decodeObject(container, descriptor, shape, path)
        is SerializerShape.Map -> decodeMap(container, shape, defaultNamespace, path)
        is SerializerShape.Polymorphic -> decodePolymorphic(container, shape, path)
        else -> read(container, ListValueKey, descriptor, defaultNamespace, path)
    }

    private fun writeTagged(container: PersistentDataContainer, key: NamespacedKey, variant: Variant) {
        container.set(
            key,
            PersistentDataType.TAG_CONTAINER,
            VariantPdcCodec.encode(container.adapterContext, variant)
        )
    }

    private fun readTagged(
        container: PersistentDataContainer,
        key: NamespacedKey,
        descriptor: SerializerDescriptor<*>,
        path: String
    ): Variant = decodeTagged(
        required(container, key, PersistentDataType.TAG_CONTAINER, descriptor, path),
        descriptor,
        path
    )

    private fun decodeTagged(
        container: PersistentDataContainer,
        descriptor: SerializerDescriptor<*>,
        path: String
    ): Variant = try {
        VariantPdcCodec.decode(container)
    } catch (exception: Exception) {
        throw DataSerializationException(
            "Invalid tagged schema PDC at '$path' for ${descriptor.id}: ${exception.message}",
            exception
        )
    }

    private fun nativePropertyKey(namespace: String, name: String, path: String): NamespacedKey {
        requireData(NativeKeyPattern.matches(name), path, "property name '$name' is not PDC-safe")
        return checkedKey(namespace, name, path)
    }

    private fun dynamicMapKey(name: String, defaultNamespace: String, path: String): NamespacedKey {
        val separator = name.indexOf(':')
        val key = when {
            separator < 0 -> checkedKey(defaultNamespace, name, path)
            separator == 0 || separator != name.lastIndexOf(':') || separator == name.lastIndex ->
                throw DataSerializationException("Invalid schema PDC at '$path': map key '$name' is not namespaced")
            else -> checkedKey(name.substring(0, separator), name.substring(separator + 1), path)
        }
        requireNotReserved(key, path)
        return key
    }

    private fun checkedKey(namespace: String, key: String, path: String): NamespacedKey = try {
        NamespacedKey(namespace, key)
    } catch (exception: IllegalArgumentException) {
        throw DataSerializationException("Invalid schema PDC key '$namespace:$key' at '$path'", exception)
    }.also { requireNotReserved(it, path) }

    private fun requireNotReserved(key: NamespacedKey, path: String) {
        requireData(
            key.namespace != "cutapi" || !key.key.startsWith("codec/"),
            path,
            "PDC key '$key' uses the reserved cutapi:codec namespace"
        )
    }

    private fun <P : Any, C : Any> required(
        container: PersistentDataContainer,
        key: NamespacedKey,
        type: PersistentDataType<P, C>,
        descriptor: SerializerDescriptor<*>,
        path: String
    ): C = container.get(key, type)
        ?: throw DataSerializationException(
            "Invalid schema PDC at '$path': expected PDC type ${pdcTypeName(type)} " +
                "for ${descriptor.id} under '$key'"
        )

    private fun pdcTypeName(type: PersistentDataType<*, *>): String = when (type) {
        PersistentDataType.BYTE -> "BYTE"
        PersistentDataType.SHORT -> "SHORT"
        PersistentDataType.INTEGER -> "INTEGER"
        PersistentDataType.LONG -> "LONG"
        PersistentDataType.FLOAT -> "FLOAT"
        PersistentDataType.DOUBLE -> "DOUBLE"
        PersistentDataType.BOOLEAN -> "BOOLEAN"
        PersistentDataType.STRING -> "STRING"
        PersistentDataType.BYTE_ARRAY -> "BYTE_ARRAY"
        PersistentDataType.INTEGER_ARRAY -> "INTEGER_ARRAY"
        PersistentDataType.LONG_ARRAY -> "LONG_ARRAY"
        PersistentDataType.TAG_CONTAINER -> "TAG_CONTAINER"
        else -> if (List::class.java.isAssignableFrom(type.complexType)) "LIST" else type.complexType.simpleName
    }

    private inline fun <reified T : Variant> expectVariant(
        variant: Variant,
        descriptor: SerializerDescriptor<*>,
        path: String
    ): T = expectVariant(variant, descriptor.id, path)

    private inline fun <reified T : Variant> expectVariant(
        variant: Variant,
        descriptorId: Identifier,
        path: String
    ): T = variant as? T ?: throw DataSerializationException(
        "Invalid schema value at '$path': ${descriptorId} expected ${T::class.simpleName}, " +
            "found ${variant::class.simpleName}"
    )

    private inline fun <reified T : Variant, R> List<Variant>.valuesOf(
        kind: VariantKind,
        path: String,
        transform: (T) -> R
    ): List<R> = mapIndexed { index, value ->
        transform(expectVariant(value, kind.id, "$path[$index]"))
    }

    private fun requireData(condition: Boolean, path: String, message: String) {
        if (!condition) throw DataSerializationException("Invalid schema PDC at '$path': $message")
    }
}
