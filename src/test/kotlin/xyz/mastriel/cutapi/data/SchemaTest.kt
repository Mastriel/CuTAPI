package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import java.io.*
import kotlin.test.*

class SchemaTest {
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
    fun `reified empty schemas preserve their target type and register by id`() {
        assertEquals(EmptySchemaData::class, EmptySchemaData.type)
        assertEquals(EmptySchemaData::class, Schema.get(id("cutapi:empty_schema_data")).type)
    }

    @Test
    fun `singleton schemas deserialize to their object instance and register by id`() {
        val variant = SingletonData.serialize(SingletonData).getOrThrow()

        assertEquals(
            Variant.Map(mapOf(Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to Variant.String("cutapi:singleton_data"))),
            variant
        )
        assertSame(SingletonData, SingletonData.deserialize(variant).getOrThrow())

        @Suppress("UNCHECKED_CAST")
        val registered = Schema.get(id("cutapi:singleton_data")) as Schema<SingletonData>
        assertSame(SingletonData, registered.deserialize(variant).getOrThrow())
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
    fun `polymorphic schemas round trip an included subtype`() {
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
