package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.test.*

class SerializerDescriptorTest {
    @Test
    fun `built-in serializers describe every primitive variant kind`() {
        val descriptors = mapOf(
            VariantSerializer.AnyVariant to VariantKind.ANY,
            VariantSerializer.Null to VariantKind.NULL,
            VariantSerializer.String to VariantKind.STRING,
            VariantSerializer.Boolean to VariantKind.BOOLEAN,
            VariantSerializer.Byte to VariantKind.BYTE,
            VariantSerializer.Short to VariantKind.SHORT,
            VariantSerializer.Int to VariantKind.INT,
            VariantSerializer.Long to VariantKind.LONG,
            VariantSerializer.Float to VariantKind.FLOAT,
            VariantSerializer.Double to VariantKind.DOUBLE,
            VariantSerializer.Char to VariantKind.CHAR,
            VariantSerializer.Id to VariantKind.IDENTIFIER,
            VariantSerializer.ResourceRef to VariantKind.RESOURCE_REF
        )

        for ((serializer, expectedKind) in descriptors) {
            val descriptor = assertIs<SerializerDescriptor.Primitive>(serializer.descriptor)
            assertEquals(expectedKind, descriptor.kind)
        }
    }

    @Test
    fun `composite serializers derive nested descriptors and value domains`() {
        val enumDescriptor = assertIs<SerializerDescriptor.Primitive>(
            VariantSerializer.Enum<DescriptorMode>().descriptor
        )
        val enumDomain = assertIs<SerializerValueDomain.Enum>(enumDescriptor.valueDomain)
        assertEquals(listOf("FIRST", "SECOND"), enumDomain.values)

        val nullable = assertIs<SerializerDescriptor.Nullable>(
            VariantSerializer.String.nullable().descriptor
        )
        assertEquals(VariantKind.STRING, assertIs<SerializerDescriptor.Primitive>(nullable.value).kind)

        val list = assertIs<SerializerDescriptor.List>(
            VariantSerializer.ListOf(VariantSerializer.Int).descriptor
        )
        assertEquals(VariantKind.INT, assertIs<SerializerDescriptor.Primitive>(list.element).kind)

        val mapped = assertIs<SerializerDescriptor.Mapped>(
            VariantSerializer.mapped(VariantSerializer.String, Int::toString, String::toInt).descriptor
        )
        assertEquals(VariantKind.STRING, assertIs<SerializerDescriptor.Primitive>(mapped.encoded).kind)

        val registryDescriptor = assertIs<SerializerDescriptor.Primitive>(
            VariantSerializer.Identifiable(DescriptorEntryRegistry).descriptor
        )
        assertEquals(
            DescriptorEntryRegistry.id,
            assertIs<SerializerValueDomain.Registry>(registryDescriptor.valueDomain).registryId
        )
    }

    @Test
    fun `schema and debug-view descriptors expose their properties`() {
        val schemaDescriptor = assertIs<SerializerDescriptor.Object>(DescriptorData.descriptor)
        assertEquals(DescriptorData.id, schemaDescriptor.id)
        assertEquals(listOf("value"), schemaDescriptor.properties.map { it.name })
        val property = assertIs<SchemaPropertyDescriptor>(schemaDescriptor.properties.single())
        assertEquals("value", property.sourceName)
        assertEquals("value", property.constructorParameterName)
        assertEquals("value", property.constructorParameter?.name)
        assertIs<SchemaPropertyPresence.Required>(property.presence)
        assertFalse(property.writable)

        val view = debugView<DescriptorData>(id("test:descriptor_debug_view")) {
            property(DescriptorData::value, VariantSerializer.String)
        }
        val viewDescriptor = assertIs<SerializerDescriptor.Object>(view.descriptor)
        assertEquals(listOf("value"), viewDescriptor.properties.map { it.name })
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

        assertTrue(parent.descriptor.included.isEmpty())
        parent.include(child)

        assertEquals(listOf(child.id), parent.descriptor.included.map { it.id })
        assertEquals(listOf("name", "count"), parent.descriptor.included.single().properties.map { it.name })
    }

    @Test
    fun `opaque descriptors retain their identifier in JSON diagnostics`() {
        val opaqueId = id("test:opaque_value")
        val opaqueSerializer = serializer(
            descriptor = SerializerDescriptor.Opaque(opaqueId),
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
