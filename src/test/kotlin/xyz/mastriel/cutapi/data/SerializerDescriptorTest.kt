package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.item.attachments.*
import kotlin.test.*

class SerializerDescriptorTest {
    @Test
    fun `built-in serializers describe every primitive variant kind`() {
        val descriptors = mapOf(
            VariantSerializer.AnyVariant to VariantKind.Any,
            VariantSerializer.Null to VariantKind.Null,
            VariantSerializer.String to VariantKind.String,
            VariantSerializer.Boolean to VariantKind.Boolean,
            VariantSerializer.Byte to VariantKind.Byte,
            VariantSerializer.Short to VariantKind.Short,
            VariantSerializer.Int to VariantKind.Int,
            VariantSerializer.Long to VariantKind.Long,
            VariantSerializer.Float to VariantKind.Float,
            VariantSerializer.Double to VariantKind.Double,
            VariantSerializer.Char to VariantKind.Char,
            VariantSerializer.Id to VariantKind.Identifier,
            VariantSerializer.ResourceRef to VariantKind.ResourceRef
        )

        for ((serializer, expectedKind) in descriptors) {
            val descriptor = serializer.descriptor
            val shape = assertIs<SerializerShape.Primitive>(descriptor.shape)
            assertIs<Identifiable>(expectedKind)
            assertEquals(expectedKind, shape.kind)
            assertEquals(expectedKind.id, descriptor.id)
            assertEquals(expectedKind.id, serializer.id)
        }

        assertEquals(VariantKind.List.id, VariantSerializer.List.id)
        assertIs<SerializerShape.List>(VariantSerializer.List.descriptor.shape)
        assertEquals(VariantKind.Map.id, VariantSerializer.Map.id)
        assertIs<SerializerShape.Map>(VariantSerializer.Map.descriptor.shape)
    }

    @Test
    fun `composite serializers derive nested descriptors and value domains`() {
        val enumDescriptor = VariantSerializer.Enum<DescriptorMode>().descriptor
        assertEquals(VariantKind.String, assertIs<SerializerShape.Primitive>(enumDescriptor.shape).kind)
        val enumDomain = assertIs<SerializerValueDomain.Enum>(enumDescriptor.valueDomain)
        assertEquals(listOf("FIRST", "SECOND"), enumDomain.values)

        val nullableSerializer = VariantSerializer.String.nullable()
        val nullable = nullableSerializer.descriptor
        val nullableShape = assertIs<SerializerShape.Nullable>(nullable.shape)
        assertEquals(
            VariantKind.String,
            assertIs<SerializerShape.Primitive>(nullableShape.value.shape).kind
        )
        assertEquals(nullableSerializer.id, nullable.id)

        val taggedListSerializer = VariantSerializer.ListOf(VariantSerializer.Int)
        val taggedList = taggedListSerializer.descriptor
        val taggedListShape = assertIs<SerializerShape.List>(taggedList.shape)
        assertEquals(
            VariantKind.Int,
            assertIs<SerializerShape.Primitive>(taggedListShape.element.shape).kind
        )
        assertEquals(taggedListSerializer.id, taggedList.id)

        val anonymousList = VariantSerializer.ListOf(VariantSerializer.Int as Serializer<Int>).descriptor
        assertEquals(VariantKind.List.id, anonymousList.id)

        val map = VariantSerializer.Map.descriptor
        assertIs<SerializerShape.Map>(map.shape)
        assertEquals(VariantSerializer.Map.id, map.id)

        val mapped = VariantSerializer.mapped(
            VariantSerializer.String,
            Int::toString,
            String::toInt
        ).descriptor
        val mappedShape = assertIs<SerializerShape.Mapped>(mapped.shape)
        assertEquals(
            VariantKind.String,
            assertIs<SerializerShape.Primitive>(mappedShape.encoded.shape).kind
        )
        assertEquals(VariantSerializer.String.id, mapped.id)

        val registryDescriptor = VariantSerializer.Identifiable(DescriptorEntryRegistry).descriptor
        assertIs<SerializerShape.Primitive>(registryDescriptor.shape)
        assertEquals(
            DescriptorEntryRegistry.id,
            assertIs<SerializerValueDomain.Registry>(registryDescriptor.valueDomain).registryId
        )
        assertEquals(VariantSerializer.Id.id, registryDescriptor.id)
    }

    @Test
    fun `tagged mapped serializers retain logical identity and encoded shape`() {
        val descriptor = ComponentSerializer.descriptor
        val mapped = assertIs<SerializerShape.Mapped>(descriptor.shape)
        val encoded = assertIs<SerializerShape.Primitive>(mapped.encoded.shape)

        assertEquals(ComponentSerializer.id, descriptor.id)
        assertEquals(VariantKind.String, encoded.kind)
        assertEquals(VariantKind.String.id, mapped.encoded.id)
    }

    @Test
    fun `mapped JSON uses encoded shape and reports logical identity`() {
        val mappedId = id("test:logical_number")
        val mappedSerializer = VariantSerializer.mapped(
            serializer = VariantSerializer.Int,
            id = mappedId,
            serialize = LogicalNumber::value,
            deserialize = ::LogicalNumber
        )
        val schema = schema<LogicalNumberContainer>(id("test:logical_number_container")) {
            property(LogicalNumberContainer::number, mappedSerializer)
        }

        assertEquals(
            LogicalNumberContainer(LogicalNumber(4)),
            schema.deserializeJson("""{"number":4}""").getOrThrow()
        )
        val failure = assertIs<DeserializeResult.Failure>(
            schema.deserializeJson("""{"number":"four"}""")
        )
        val error = assertIs<SchemaJsonException>(failure.error)
        assertEquals(mappedId.toString(), error.expected)
        assertEquals("\"four\"", error.found)
    }

    @Test
    fun `schema and debug-view descriptors expose their properties`() {
        val schemaDescriptor = DescriptorData.descriptor
        val schemaShape = assertIs<SerializerShape.Object>(schemaDescriptor.shape)
        assertEquals(DescriptorData.id, schemaDescriptor.id)
        assertEquals(listOf("value"), schemaShape.properties.map { it.name })
        val property = assertIs<SchemaPropertyDescriptor>(schemaShape.properties.single())
        assertEquals("value", property.sourceName)
        assertEquals("value", property.constructorParameterName)
        assertEquals("value", property.constructorParameter?.name)
        assertIs<SchemaPropertyPresence.Required>(property.presence)
        assertFalse(property.writable)

        val view = debugView<DescriptorData>(id("test:descriptor_debug_view")) {
            property(DescriptorData::value, VariantSerializer.String)
        }
        val viewDescriptor = view.descriptor
        assertEquals(
            listOf("value"),
            assertIs<SerializerShape.Object>(viewDescriptor.shape).properties.map { it.name }
        )
    }

    @Test
    fun `polymorphic descriptors reflect schemas included after construction`() {
        val parent = polySchema<DescriptorParent>(id("test:descriptor_parent")) {
            property(DescriptorParent::name, VariantSerializer.String)
        }
        val child = schema<DescriptorChild>(id("test:descriptor_child")) {
            extends { parent }
            property(DescriptorChild::count, VariantSerializer.Int)
        }

        assertTrue(assertIs<SerializerShape.Polymorphic>(parent.descriptor.shape).included.isEmpty())
        parent.include(child)

        val included = assertIs<SerializerShape.Polymorphic>(parent.descriptor.shape).included
        assertEquals(listOf(child.id), included.map { it.id })
        assertEquals(
            listOf("name", "count"),
            included.single().shape.properties.map { it.name }
        )
    }

    @Test
    fun `opaque descriptors retain their identifier in JSON diagnostics`() {
        val opaqueId = id("test:opaque_value")
        val opaqueSerializer = serializer(
            descriptor = SerializerDescriptor.opaque(opaqueId),
            serialize = { value: String -> Variant.String(value) },
            deserialize = { variant ->
                (variant as? Variant.String)?.value
                    ?: throw VariantTypeException("String", variant)
            }
        )
        val schema = schema<OpaqueContainer>(id("test:opaque_container")) {
            property(OpaqueContainer::value, opaqueSerializer)
        }

        assertEquals(
            OpaqueContainer("accepted"),
            schema.deserializeJson("""{"value":"accepted"}""").getOrThrow()
        )
        val failure = assertIs<DeserializeResult.Failure>(
            schema.deserializeJson("""{"value":42}""")
        )
        val error = assertIs<SchemaJsonException>(failure.error)
        assertEquals(opaqueId.toString(), error.expected)
        assertEquals("42", error.found)
    }

    @Test
    fun `schema properties reject tagged serializers with mismatched descriptor identities`() {
        val serializer = object : TaggedSerializer<String> {
            override val id: Identifier = id("test:mismatched_serializer")
            override val descriptor: SerializerDescriptor<*> =
                SerializerDescriptor.primitive(VariantKind.String)

            override fun serialize(value: String): SerializeResult =
                SerializeResult.Success(Variant.String(value))

            override fun deserialize(variant: Variant): DeserializeResult<String> =
                VariantSerializer.String.deserialize(variant)
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            schema<DescriptorData>(id("test:mismatched_schema")) {
                property(DescriptorData::value, serializer)
            }
        }
        assertContains(exception.message.orEmpty(), "must use a descriptor with the same id")
    }
}

private enum class DescriptorMode {
    FIRST,
    SECOND
}

private data class DescriptorEntry(
    override val id: Identifier
) : Identifiable

private object DescriptorEntryRegistry :
    IdentifierRegistry<DescriptorEntry>(id("test:descriptor_entry_registry"))

private data class DescriptorData(
    val value: String
) {
    companion object : Schema<DescriptorData> by schema(id("test:descriptor_data"), {
        property(DescriptorData::value, VariantSerializer.String)
    })
}

private interface DescriptorParent {
    val name: String
}

private data class DescriptorChild(
    override val name: String,
    val count: Int
) : DescriptorParent

private data class OpaqueContainer(
    val value: String
)

private data class LogicalNumber(
    val value: Int
)

private data class LogicalNumberContainer(
    val number: LogicalNumber
)
