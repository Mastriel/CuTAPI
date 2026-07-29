package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.testing.*
import java.io.*
import kotlin.test.*

class SchemaTest : MockBukkitTest() {
    @Test
    fun `schema serializes and deserializes a data class`() {
        val original = MyData("Ada", 37)

        val variant = MyData.serialize(original).getOrThrow()

        assertEquals(
            Variant.Map(
                linkedMapOf(
                    Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to Variant.String("cutapi:my_data"),
                    Variant.String("name") to Variant.String("Ada"),
                    Variant.String("age") to Variant.Int(37)
                )
            ),
            variant
        )
        assertEquals(original, MyData.deserialize(variant).getOrThrow())
        assertFalse(MyData.untagged)
        assertEquals(original, original.serializer.deserialize(variant).getOrThrow())

        val nameProperty = MyData.properties.single { it.name == "name" }
        assertEquals("Ada", nameProperty.getProperty(original))
    }

    @Test
    fun `a serializer can be implemented directly`() {
        data class ManualValue(val value: String)

        val serializer = object : Serializer<ManualValue> {
            override val descriptor: SerializerDescriptor =
                SerializerDescriptor.Opaque(id("test:manual_value"))

            override fun serialize(value: ManualValue): SerializeResult =
                SerializeResult.Success(Variant.String(value.value))

            override fun deserialize(variant: Variant): DeserializeResult<ManualValue> =
                when (variant) {
                    is Variant.String -> DeserializeResult.Success(ManualValue(variant.value))
                    else -> DeserializeResult.Failure(VariantTypeException("String", variant))
                }
        }

        val encoded = serializer.serialize(ManualValue("value")).getOrThrow()
        assertEquals(Variant.String("value"), encoded)
        assertEquals(ManualValue("value"), serializer.deserialize(encoded).getOrThrow())
    }

    @Test
    fun `a variant format owns conversion to an external representation`() {
        val stringFormat = object : VariantFormat<String> {
            override val id: Identifier = id("cutapi:test_string_format")

            override fun encode(variant: Variant): String =
                (variant as Variant.String).value

            override fun decode(value: String): Variant = Variant.String(value)
        }

        val encoded = Variant.String("portable").encodeTo(stringFormat)

        assertEquals("portable", encoded)
        assertEquals(Variant.String("portable"), Variant.decodeFrom(encoded, stringFormat))
    }

    @Test
    fun `variant serializers cover every variant type`() {
        val listValue = listOf(Variant.String("nested"), Variant.Int(3))
        val mapValue = linkedMapOf<Variant, Variant>(
            Variant.String("key") to Variant.Boolean(true)
        )
        val idValue = id("cutapi:variant_identifier")
        val refValue = ref<Resource>(VariantTestResourceRoot, "textures/widget.png")

        assertEquals(Variant.Null, VariantSerializer.Null.serialize(null).getOrThrow())
        assertNull(VariantSerializer.Null.deserialize(Variant.Null).getOrThrow())
        assertEquals(Variant.String("value"), VariantSerializer.String.serialize("value").getOrThrow())
        assertEquals(true, VariantSerializer.Boolean.deserialize(Variant.Boolean(true)).getOrThrow())
        assertEquals(4.toByte(), VariantSerializer.Byte.deserialize(Variant.Byte(4)).getOrThrow())
        assertEquals(5.toShort(), VariantSerializer.Short.deserialize(Variant.Short(5)).getOrThrow())
        assertEquals(6, VariantSerializer.Int.deserialize(Variant.Int(6)).getOrThrow())
        assertEquals(7L, VariantSerializer.Long.deserialize(Variant.Long(7)).getOrThrow())
        assertEquals(8f, VariantSerializer.Float.deserialize(Variant.Float(8f)).getOrThrow())
        assertEquals(9.0, VariantSerializer.Double.deserialize(Variant.Double(9.0)).getOrThrow())
        assertEquals('c', VariantSerializer.Char.deserialize(Variant.Char('c')).getOrThrow())
        assertEquals(Variant.Identifier(idValue), VariantSerializer.Id.serialize(idValue).getOrThrow())
        assertEquals(idValue, VariantSerializer.Id.deserialize(Variant.Identifier(idValue)).getOrThrow())
        assertEquals(Variant.ResourceRef(refValue), VariantSerializer.ResourceRef.serialize(refValue).getOrThrow())
        assertEquals(refValue, VariantSerializer.ResourceRef.deserialize(Variant.ResourceRef(refValue)).getOrThrow())
        assertEquals(Variant.List(listValue), VariantSerializer.List.serialize(listValue).getOrThrow())
        assertEquals(listValue, VariantSerializer.List.deserialize(Variant.List(listValue)).getOrThrow())
        assertEquals(Variant.Map(mapValue), VariantSerializer.Map.serialize(mapValue).getOrThrow())
        assertEquals(mapValue, VariantSerializer.Map.deserialize(Variant.Map(mapValue)).getOrThrow())
        assertEquals(Variant.Map(mapValue), VariantSerializer.AnyVariant.serialize(Variant.Map(mapValue)).getOrThrow())
        assertEquals(
            Variant.Map(mapValue),
            VariantSerializer.AnyVariant.deserialize(Variant.Map(mapValue)).getOrThrow()
        )
    }

    @Test
    fun `schema failures identify the property`() {
        val invalid = Variant.Map(
            mapOf(
                Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to Variant.String("cutapi:my_data"),
                Variant.String("name") to Variant.String("Ada"),
                Variant.String("age") to Variant.String("not an int")
            )
        )

        val failure = assertIs<DeserializeResult.Failure>(MyData.deserialize(invalid))
        assertContains(failure.error.message.orEmpty(), "age")
        assertIs<VariantTypeException>(failure.error.cause)
    }

    @Test
    fun `untagged schemas omit their type marker`() {
        val value = UntaggedData("quiet")
        val variant = UntaggedData.serialize(value).getOrThrow()

        assertEquals(
            Variant.Map(mapOf(Variant.String("value") to Variant.String("quiet"))),
            variant
        )
        assertTrue(UntaggedData.untagged)
        assertEquals(value, UntaggedData.deserialize(variant).getOrThrow())
    }

    @Test
    fun `reified empty schemas preserve their target type without registering`() {
        assertEquals(EmptySchemaData::class, EmptySchemaData.type)
        assertNull(Schema.getOrNull(EmptySchemaData.id))
    }

    @Test
    fun `singleton schemas deserialize to their object instance without registering`() {
        val variant = SingletonData.serialize(SingletonData).getOrThrow()

        assertEquals(
            Variant.Map(mapOf(Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to Variant.String("cutapi:singleton_data"))),
            variant
        )
        assertSame(SingletonData, SingletonData.deserialize(variant).getOrThrow())
        assertNull(Schema.getOrNull(SingletonData.id))
    }

    @Test
    fun `schemas can be explicitly registered by identity`() {
        class ExplicitSchemaData

        val explicit = schema<ExplicitSchemaData>(id("test:explicit_schema"), {})
        Schema.registerSchema(explicit)

        assertSame(explicit, Schema.get(explicit.id))
        val duplicate = schema<ExplicitSchemaData>(explicit.id, {})
        val exception = assertFailsWith<IllegalArgumentException> {
            Schema.registerSchema(duplicate)
        }
        assertContains(exception.message.orEmpty(), "different schema")
    }

    @Test
    fun `schemas can use explicit getter and setter properties`() {
        val value = AccessorBackedData(13)
        val variant = AccessorBackedData.serialize(value).getOrThrow()

        assertEquals(
            Variant.Map(
                linkedMapOf(
                    Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to Variant.String("cutapi:accessor_backed_data"),
                    Variant.String("value") to Variant.Int(13)
                )
            ),
            variant
        )
        assertEquals(value, AccessorBackedData.deserialize(variant).getOrThrow())
    }

    @Test
    fun `schemas can deserialize through a private primary constructor`() {
        val original = PrivateConstructorData.create("hidden")
        val variant = PrivateConstructorData.serialize(original).getOrThrow()

        assertEquals("hidden", PrivateConstructorData.deserialize(variant).getOrThrow().value)
    }

    @Test
    fun `tagged serializers can be discovered from private companion objects`() {
        assertSame(
            PrivateConstructorData,
            TaggedSerializer.fromCompanion<PrivateConstructorData>()
        )
    }

    @Test
    fun `polymorphic schemas round trip an included subtype`() {
        assertNull(Schema.getOrNull(PolymorphicData.id))
        val value = ExtendedData().apply {
            data = 42
            extraData = "leaf"
        }

        val variant = PolymorphicData.serialize(value).getOrThrow()
        assertEquals(
            Variant.Map(
                linkedMapOf(
                    Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to Variant.String("cutapi:extended_data"),
                    Variant.String("data") to Variant.Int(42),
                    Variant.String("extraData") to Variant.String("leaf")
                )
            ),
            variant
        )

        val decoded = assertIs<ExtendedData>(PolymorphicData.deserialize(variant).getOrThrow())
        assertEquals(42, decoded.data)
        assertEquals("leaf", decoded.extraData)

        val directDecoded = ExtendedData.deserialize(variant).getOrThrow()
        assertEquals(42, directDecoded.data)
        assertEquals("leaf", directDecoded.extraData)
    }

    @Test
    fun `polymorphic schemas can include subtypes after construction`() {
        val value = LateIncludedData(
            baseData = 5,
            extraData = "late"
        )

        val missingInclude = assertIs<SerializeResult.Failure>(LateParentData.serialize(value))
        assertContains(missingInclude.error.message.orEmpty(), "No schema included")

        LateParentData.include(LateIncludedData)

        assertTrue(LateIncludedData::class in LateParentData.includedTypes)
        val variant = LateParentData.serialize(value).getOrThrow()
        assertEquals(
            Variant.Map(
                linkedMapOf(
                    Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to Variant.String("cutapi:late_included_data"),
                    Variant.String("baseData") to Variant.Int(5),
                    Variant.String("extraData") to Variant.String("late")
                )
            ),
            variant
        )
        assertEquals(value, LateParentData.deserialize(variant).getOrThrow())
    }

    @Test
    fun `schemas can extend multiple polymorphic interface parents`() {
        val value = MultiParentData(
            firstData = 7,
            secondData = "branch",
            ownData = true
        )

        val variant = FirstParentData.serialize(value).getOrThrow()
        assertEquals(
            Variant.Map(
                linkedMapOf(
                    Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to Variant.String("cutapi:multi_parent_data"),
                    Variant.String("firstData") to Variant.Int(7),
                    Variant.String("secondData") to Variant.String("branch"),
                    Variant.String("ownData") to Variant.Boolean(true)
                )
            ),
            variant
        )

        assertEquals(value, FirstParentData.deserialize(variant).getOrThrow())
        assertEquals(value, SecondParentData.deserialize(variant).getOrThrow())
        assertEquals(value, MultiParentData.deserialize(variant).getOrThrow())
    }

    @Test
    fun `schema extensions can exclude parent constructor properties`() {
        val value = ExcludedSchemaChild(
            ownData = "child",
            retainedData = true
        )

        val variant = ExcludedSchemaChild.serialize(value).getOrThrow()
        assertEquals(
            Variant.Map(
                linkedMapOf(
                    Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to
                        Variant.String("cutapi:excluded_schema_child"),
                    Variant.String("retainedData") to Variant.Boolean(true),
                    Variant.String("ownData") to Variant.String("child")
                )
            ),
            variant
        )

        val decoded = ExcludedSchemaChild.deserialize(variant).getOrThrow()
        assertEquals(value, decoded)
        assertEquals("fixed", decoded.name)
        assertEquals(21, decoded.age)
    }

    @Test
    fun `excluded sample parent properties use child constructor constants`() {
        val value = MyChildData(isAwesome = true)
        val variant = MyChildData.serialize(value).getOrThrow()

        assertEquals(
            Variant.Map(
                linkedMapOf(
                    Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to
                        Variant.String("cutapi:my_child_data"),
                    Variant.String("isAwesome") to Variant.Boolean(true)
                )
            ),
            variant
        )

        val decoded = MyChildData.deserialize(variant).getOrThrow()
        assertEquals(value, decoded)
        assertEquals("John", decoded.name)
        assertEquals(67, decoded.age)
    }

    @Test
    fun `schema JSON validates attachment values and supplies the type marker`() {
        val value = JsonAttachment.deserializeJson(
            """
            {
              "count": 4,
              "nested": {"label": "ready"},
              "identifier": "test:json_value"
            }
            """.trimIndent()
        ).getOrThrow()

        assertEquals(
            JsonAttachment(4, JsonNested("ready"), id("test:json_value")),
            value
        )
        val invalidCount = assertIs<DeserializeResult.Failure>(
            JsonAttachment.deserializeJson(
                """{"count":"four","nested":{"label":"ready"},"identifier":"test:json_value"}"""
            )
        )
        val invalidCountError = assertIs<SchemaJsonException>(invalidCount.error)
        assertContains(invalidCountError.errorMessage, "at 'count'")
        assertEquals("integer", invalidCountError.expected)
        assertEquals(""""four"""", invalidCountError.found)

        val unknownProperty = assertIs<DeserializeResult.Failure>(
            JsonAttachment.deserializeJson(
                """{"count":4,"nested":{"label":"ready"},"identifier":"test:json_value","extra":true}"""
            )
        )
        val unknownPropertyError = assertIs<SchemaJsonException>(unknownProperty.error)
        assertContains(unknownPropertyError.errorMessage, "at 'extra'")
        assertContains(unknownPropertyError.errorMessage, "Unknown property")
        assertEquals("registered property name", unknownPropertyError.expected)
        assertEquals(""""extra"""", unknownPropertyError.found)
    }

    @Test
    fun `schema JSON errors include nested property paths and expected types`() {
        val failure = assertIs<DeserializeResult.Failure>(
            JsonAttachment.deserializeJson(
                """{"count":4,"nested":{"label":false},"identifier":"test:json_value"}"""
            )
        )

        val error = assertIs<SchemaJsonException>(failure.error)
        assertContains(error.errorMessage, "at 'nested.label'")
        assertEquals("string", error.expected)
        assertEquals("false", error.found)
    }

    @Test
    fun `schema JSON errors describe missing nested properties`() {
        val failure = assertIs<DeserializeResult.Failure>(
            JsonAttachment.deserializeJson(
                """{"count":4,"nested":{},"identifier":"test:json_value"}"""
            )
        )

        val error = assertIs<SchemaJsonException>(failure.error)
        assertContains(error.errorMessage, "at 'nested.label'")
        assertContains(error.errorMessage, "Missing required property")
        assertEquals("string", error.expected)
        assertEquals("<missing>", error.found)
    }

    @Test
    fun `schema JSON errors list valid enum values`() {
        val failure = assertIs<DeserializeResult.Failure>(
            JsonEnumAttachment.deserializeJson("""{"mode":"THIRD"}""")
        )

        val error = assertIs<SchemaJsonException>(failure.error)
        assertContains(error.errorMessage, "at 'mode'")
        assertEquals("JsonMode", error.expected)
        assertEquals(""""THIRD"""", error.found)
        val entries = assertIs<SerializerValueDomain.Enum>(error.availableEntries)
        assertEquals(listOf("FIRST", "SECOND"), entries.values)
        assertContains(error.message.orEmpty(), "Available Entries: { FIRST, SECOND }")
    }

    @Test
    fun `schema JSON errors collapse enums with more than ten entries`() {
        val failure = assertIs<DeserializeResult.Failure>(
            JsonLongEnumAttachment.deserializeJson("""{"mode":"MISSING"}""")
        )

        val error = assertIs<SchemaJsonException>(failure.error)
        val entries = assertIs<SerializerValueDomain.Enum>(error.availableEntries)
        assertEquals(11, entries.values.size)
        assertContains(error.message.orEmpty(), "Available Entries: { ... }")
        assertFalse(error.message.orEmpty().contains("ELEVEN"))
    }

    @Test
    fun `schema JSON identifier errors retain their source registry`() {
        val failure = assertIs<DeserializeResult.Failure>(
            JsonRegistryAttachment.deserializeJson("""{"entry":"test:missing"}""")
        )

        val error = assertIs<SchemaJsonException>(failure.error)
        assertEquals("identifier string", error.expected)
        assertEquals(""""test:missing"""", error.found)
        val entries = assertIs<SerializerValueDomain.Registry>(error.availableEntries)
        assertEquals(JsonEntryRegistry.id, entries.registryId)
    }

    @Test
    fun `schema JSON syntax errors identify the schema and parser location`() {
        val failure = assertIs<DeserializeResult.Failure>(
            JsonAttachment.deserializeJson("""{"count":4,"nested":""")
        )

        val error = assertIs<SchemaJsonException>(failure.error)
        assertContains(error.errorMessage, "Could not parse JSON for schema ${JsonAttachment.id}")
        assertContains(error.errorMessage, "path: $")
        assertEquals("valid JSON object", error.expected)
        assertEquals("""{"count":4,"nested":""", error.found)
    }

    @Test
    fun `schema JSON updates immutable properties through nested dot paths`() {
        val original = JsonAttachment(4, JsonNested("before"), id("test:json_value"))

        val updated = JsonAttachment
            .updateJsonProperty(original, "nested.label", """"after"""")
            .getOrThrow()

        assertEquals(
            JsonAttachment(4, JsonNested("after"), id("test:json_value")),
            updated
        )
        assertIs<DeserializeResult.Failure>(
            JsonAttachment.updateJsonProperty(original, "nested.missing", "5")
        )
        val invalidValue = assertIs<DeserializeResult.Failure>(
            JsonAttachment.updateJsonProperty(original, "count", """"not a number"""")
        )
        val invalidValueError = assertIs<SchemaJsonException>(invalidValue.error)
        assertContains(invalidValueError.errorMessage, "at 'count'")
        assertEquals("integer", invalidValueError.expected)
        assertEquals(""""not a number"""", invalidValueError.found)

        val malformedValue = assertIs<DeserializeResult.Failure>(
            JsonAttachment.updateJsonProperty(original, "nested.label", """"unfinished""")
        )
        val malformedValueError = assertIs<SchemaJsonException>(malformedValue.error)
        assertContains(malformedValueError.errorMessage, "at 'nested.label'")
        assertContains(malformedValueError.errorMessage, "unexpected", ignoreCase = true)
        assertEquals("valid JSON value", malformedValueError.expected)
        assertEquals(""""unfinished""", malformedValueError.found)
    }
}

private data class UntaggedData(val value: String) {
    companion object : Schema<UntaggedData> by schema(id("cutapi:untagged_data"), {
        untagged = true
        property(UntaggedData::value, VariantSerializer.String)
    })
}

private class EmptySchemaData {
    companion object : Schema<EmptySchemaData> by schema(id("cutapi:empty_schema_data"), {})
}

private object SingletonData : Schema<SingletonData> by singletonSchema(id("cutapi:singleton_data"))

private object VariantTestResourceRoot : ResourceRoot {
    override val cutPlugin: CuTPlugin
        get() = error("Variant serializer tests do not use a registered plugin")

    override fun getResourcesFolder(): File = File(".")
}

private class AccessorBackedData(value: Int = 0) {
    private var storedValue: Int = value

    fun readValue(): Int = storedValue

    fun writeValue(value: Int) {
        storedValue = value
    }

    override fun equals(other: Any?): Boolean =
        other is AccessorBackedData && storedValue == other.storedValue

    override fun hashCode(): Int = storedValue

    companion object :
        Schema<AccessorBackedData> by schema(AccessorBackedData::class, id("cutapi:accessor_backed_data"), {
            property(
                name = "value",
                serializer = VariantSerializer.Int,
                getProperty = AccessorBackedData::readValue,
                setProperty = AccessorBackedData::writeValue
            )
        })
}

private class PrivateConstructorData private constructor(val value: String) {
    companion object : Schema<PrivateConstructorData> by schema(id("cutapi:private_constructor_data"), {
        property(PrivateConstructorData::value, VariantSerializer.String)
    }) {
        fun create(value: String): PrivateConstructorData = PrivateConstructorData(value)
    }
}

private abstract class PolymorphicData {
    var data: Int = 0

    companion object : Schema<PolymorphicData> by polySchema(id("cutapi:polymorphic_data"), {
        include(ExtendedData)
        property(PolymorphicData::data, VariantSerializer.Int)
    })
}

private class ExtendedData : PolymorphicData() {
    var extraData: String = ""

    companion object : Schema<ExtendedData> by schema(id("cutapi:extended_data"), {
        extends { PolymorphicData }
        property(ExtendedData::extraData, VariantSerializer.String)
    })
}

private interface LateParentData {
    val baseData: Int

    companion object : PolySchema<LateParentData> by polySchema(id("cutapi:late_parent_data"), {
        property(LateParentData::baseData, VariantSerializer.Int)
    })
}

private data class LateIncludedData(
    override val baseData: Int,
    val extraData: String
) : LateParentData {
    companion object : Schema<LateIncludedData> by schema(id("cutapi:late_included_data"), {
        extends { LateParentData }
        property(LateIncludedData::extraData, VariantSerializer.String)
    })
}

private interface FirstParentData {
    val firstData: Int

    companion object : PolySchema<FirstParentData> by polySchema(id("cutapi:first_parent_data"), {
        include(MultiParentData)
        property(FirstParentData::firstData, VariantSerializer.Int)
    })
}

private interface SecondParentData {
    val secondData: String

    companion object : PolySchema<SecondParentData> by polySchema(id("cutapi:second_parent_data"), {
        include(MultiParentData)
        property(SecondParentData::secondData, VariantSerializer.String)
    })
}

private data class MultiParentData(
    override val firstData: Int,
    override val secondData: String,
    val ownData: Boolean
) : FirstParentData, SecondParentData {
    companion object : Schema<MultiParentData> by schema(id("cutapi:multi_parent_data"), {
        extends { FirstParentData }
        extends { SecondParentData }
        property(MultiParentData::ownData, VariantSerializer.Boolean)
    })
}

private abstract class ExcludedSchemaParent(
    val name: String,
    val age: Int
) {
    companion object : Schema<ExcludedSchemaParent> by schema(id("cutapi:excluded_schema_parent"), {
        property(ExcludedSchemaParent::name, VariantSerializer.String, name = "display_name")
        property(ExcludedSchemaParent::age, VariantSerializer.Int)
    })
}

private interface RetainedSchemaParent {
    val retainedData: Boolean

    companion object : Schema<RetainedSchemaParent> by polySchema(id("cutapi:retained_schema_parent"), {
        property(RetainedSchemaParent::retainedData, VariantSerializer.Boolean)
    })
}

private data class ExcludedSchemaChild(
    val ownData: String,
    override val retainedData: Boolean
) : ExcludedSchemaParent("fixed", 21), RetainedSchemaParent {
    companion object : Schema<ExcludedSchemaChild> by schema(id("cutapi:excluded_schema_child"), {
        extends { ExcludedSchemaParent }
            .exclude { ExcludedSchemaParent::name }
            .exclude { ExcludedSchemaParent::age }
        extends { RetainedSchemaParent }
        property(ExcludedSchemaChild::ownData, VariantSerializer.String)
    })
}

private data class JsonNested(val label: String) {
    companion object : Schema<JsonNested> by schema(id("test:json_nested"), {
        property(JsonNested::label, VariantSerializer.String)
    })
}

private data class JsonAttachment(
    val count: Int,
    val nested: JsonNested,
    val identifier: Identifier
) : ItemAttachment {
    companion object : Schema<JsonAttachment> by schema(id("test:json_attachment"), {
        property(JsonAttachment::count, VariantSerializer.Int)
        property(JsonAttachment::nested, JsonNested)
        property(JsonAttachment::identifier, VariantSerializer.Id)
    })
}

private enum class JsonMode {
    FIRST,
    SECOND
}

private data class JsonEnumAttachment(val mode: JsonMode) {
    companion object : Schema<JsonEnumAttachment> by schema(id("test:json_enum_attachment"), {
        property(JsonEnumAttachment::mode, VariantSerializer.Enum<JsonMode>())
    })
}

private enum class JsonLongMode {
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

private data class JsonLongEnumAttachment(val mode: JsonLongMode) {
    companion object : Schema<JsonLongEnumAttachment> by schema(id("test:json_long_enum_attachment"), {
        property(JsonLongEnumAttachment::mode, VariantSerializer.Enum<JsonLongMode>())
    })
}

private data class JsonRegistryEntry(
    override val id: Identifier
) : Identifiable

private object JsonEntryRegistry :
    IdentifierRegistry<JsonRegistryEntry>(id("test:json_entry_registry"))

private data class JsonRegistryAttachment(val entry: JsonRegistryEntry) {
    companion object : Schema<JsonRegistryAttachment> by schema(id("test:json_registry_attachment"), {
        property(JsonRegistryAttachment::entry, VariantSerializer.Identifiable(JsonEntryRegistry))
    })
}
