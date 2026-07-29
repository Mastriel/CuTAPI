package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.test.*

class SchemaConstructionTest {
    @Test
    fun `required optional nullable and explicit defaults remain distinct`() {
        val decoded = PresenceData.deserialize(
            variantMap(
                PresenceData.id,
                "required" to Variant.String("present"),
                "nullable" to Variant.Null
            )
        ).getOrThrow()

        assertEquals(
            PresenceData(
                required = "present",
                kotlinDefault = 7,
                schemaDefault = 9,
                nullable = null
            ),
            decoded
        )

        val encoded = assertIs<Variant.Map>(PresenceData.serialize(decoded).getOrThrow())
        assertEquals(Variant.Int(7), encoded[Variant.String("kotlinDefault")])
        assertFalse(Variant.String("schemaDefault") in encoded.value)
        assertEquals(Variant.Null, encoded[Variant.String("nullable")])

        val missingNullable = assertIs<DeserializeResult.Failure>(
            PresenceData.deserialize(
                variantMap(
                    PresenceData.id,
                    "required" to Variant.String("present")
                )
            )
        )
        assertContains(missingNullable.error.message.orEmpty(), "Missing required property 'nullable'")
    }

    @Test
    fun `optional mutable properties retain their instance defaults when absent`() {
        val decoded = RetainedInstanceDefault.deserialize(
            variantMap(
                RetainedInstanceDefault.id,
                "required" to Variant.String("present")
            )
        ).getOrThrow()

        assertEquals("retained", decoded.extra)
    }

    @Test
    fun `custom construction supports property handles references and provided checks`() {
        val minimal = FactoryConstructed.deserialize(
            variantMap(
                FactoryConstructed.id,
                "name" to Variant.String("Ada"),
                "count" to Variant.Int(3)
            )
        ).getOrThrow()
        assertEquals(FactoryConstructed.expected("Ada", 3, "not provided"), minimal)

        val complete = FactoryConstructed.deserialize(
            variantMap(
                FactoryConstructed.id,
                "name" to Variant.String("Grace"),
                "count" to Variant.Int(5),
                "note" to Variant.String("provided")
            )
        ).getOrThrow()
        assertEquals(FactoryConstructed.expected("Grace", 5, "provided"), complete)
    }

    @Test
    fun `requesting an absent optional value without a default fails clearly`() {
        val schema = schema<AbsentOptionalFactory>(id("test:absent_optional_factory")) {
            val value = property(AbsentOptionalFactory::value, VariantSerializer.String) {
                optional()
            }
            constructs { AbsentOptionalFactory(value(value)) }
        }

        val failure = assertIs<DeserializeResult.Failure>(
            schema.deserialize(variantMap(schema.id))
        )
        assertContains(failure.error.message.orEmpty(), "was not provided")
        assertContains(failure.error.message.orEmpty(), "has no schema default")
    }

    @Test
    fun `custom construction rejects unconsumed immutable values`() {
        val schema = schema<UnconsumedImmutable>(id("test:unconsumed_immutable")) {
            property(UnconsumedImmutable::value, VariantSerializer.String)
            constructs { UnconsumedImmutable("factory") }
        }

        val failure = assertIs<DeserializeResult.Failure>(
            schema.deserialize(
                variantMap(schema.id, "value" to Variant.String("serialized"))
            )
        )
        assertContains(failure.error.message.orEmpty(), "cannot be assigned after construction")
    }

    @Test
    fun `duplicate and unsupported construction strategies fail at schema creation`() {
        val duplicate = assertFailsWith<IllegalArgumentException> {
            schema<DuplicateFactory>(id("test:duplicate_constructs")) {
                constructs { DuplicateFactory("") }
                constructs { DuplicateFactory("") }
            }
        }
        assertContains(duplicate.message.orEmpty(), "only be configured once")

        val noConstructor = assertFailsWith<IllegalArgumentException> {
            schema<NoConstructor>(id("test:no_constructor")) {
                property(NoConstructor::value, VariantSerializer.String)
            }
        }
        assertContains(noConstructor.message.orEmpty(), "has no primary constructor")

        val singleton = assertFailsWith<IllegalArgumentException> {
            singletonSchema<ConstructedSingleton>(id("test:constructed_singleton")) {
                constructs { ConstructedSingleton }
            }
        }
        assertContains(singleton.message.orEmpty(), "cannot configure constructs")
    }

    @Test
    fun `property handles expose explicit presence and mutability metadata`() {
        var captured: SchemaProperty<PropertyMetadata, String>? = null
        schema<PropertyMetadata>(id("test:property_metadata")) {
            captured = property(PropertyMetadata::value, VariantSerializer.String) {
                optional(default = "default", omitDefaults = true)
            }
        }

        val property = assertNotNull(captured)
        assertEquals("value", property.name)
        assertEquals("value", property.sourceName)
        assertEquals("value", property.constructorParameterName)
        assertEquals("value", property.constructorParameter?.name)
        assertFalse(property.writable)
        val optional = assertIs<SchemaPropertyPresence.Optional<*>>(property.presence)
        assertTrue(optional.hasDefault)
        assertTrue(optional.omitDefaults)
        assertEquals("default", optional.defaultValue())
    }
}

private data class PresenceData(
    val required: String,
    val kotlinDefault: Int = 7,
    val schemaDefault: Int,
    val nullable: String?
) {
    companion object : Schema<PresenceData> by schema(id("test:presence_data"), {
        property(PresenceData::required, VariantSerializer.String)
        property(PresenceData::kotlinDefault, VariantSerializer.Int) {
            optional()
        }
        property(PresenceData::schemaDefault, VariantSerializer.Int) {
            optional(default = 9, omitDefaults = true)
        }
        property(PresenceData::nullable, VariantSerializer.String.nullable())
    })
}

private class RetainedInstanceDefault(
    val required: String
) {
    var extra: String = "retained"

    companion object : Schema<RetainedInstanceDefault> by schema(id("test:retained_instance_default"), {
        property(RetainedInstanceDefault::required, VariantSerializer.String)
        property(
            RetainedInstanceDefault::extra,
            VariantSerializer.String,
            constructorParameterName = null
        ) {
            optional()
        }
    })
}

@ConsistentCopyVisibility
private data class FactoryConstructed private constructor(
    val name: String,
    var count: Int,
    val note: String
) {
    companion object : Schema<FactoryConstructed> by schema(id("test:factory_constructed"), {
        val nameProperty = property(FactoryConstructed::name, VariantSerializer.String)
        property(
            FactoryConstructed::count,
            VariantSerializer.Int,
            constructorParameterName = null
        )
        property(FactoryConstructed::note, VariantSerializer.String) {
            optional()
        }
        constructs {
            FactoryConstructed(
                name = value(nameProperty),
                count = 0,
                note = if (wasProvided(FactoryConstructed::note)) {
                    value(FactoryConstructed::note)
                } else {
                    "not provided"
                }
            )
        }
    }) {
        fun expected(name: String, count: Int, note: String): FactoryConstructed =
            FactoryConstructed(name, count, note)
    }
}

private data class AbsentOptionalFactory(
    val value: String
)

private data class UnconsumedImmutable(
    val value: String
)

private data class DuplicateFactory(
    val value: String
)

private interface NoConstructor {
    val value: String
}

private object ConstructedSingleton

private data class PropertyMetadata(
    val value: String
)

private fun variantMap(
    schemaId: Identifier,
    vararg values: Pair<String, Variant>
): Variant.Map = Variant.Map(
    linkedMapOf<Variant, Variant>(
        Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to Variant.String(schemaId.toString())
    ).apply {
        values.forEach { (name, value) -> put(Variant.String(name), value) }
    }
)
