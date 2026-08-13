package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.persistence.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.internal.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.testing.*
import kotlin.test.*

public class SchemaPdcCodecTest : MockBukkitTest() {
    @Test
    public fun `evil machine data uses PDC-safe serialized property names`() {
        val value = EvilMachineData(progressTicks = 12, totalTicks = 100)

        val encoded = SchemaPdcCodec.encode(context(), EvilMachineData, value)

        assertEquals(
            12,
            encoded.get(id("cutapi:progress_ticks").toNamespacedKey(), PersistentDataType.INTEGER)
        )
        assertEquals(
            100,
            encoded.get(id("cutapi:total_ticks").toNamespacedKey(), PersistentDataType.INTEGER)
        )
        assertEquals(value, SchemaPdcCodec.decode(EvilMachineData, encoded))
    }

    @Test
    public fun `schema primitives use native PDC types`() {
        val value = NativePrimitives(
            stringValue = "hello",
            booleanValue = true,
            byteValue = 1,
            shortValue = 2,
            intValue = 3,
            longValue = 4,
            floatValue = 5.5f,
            doubleValue = 6.5,
            charValue = 'x',
            identifierValue = id("test:value"),
            mappedValue = NativeNumber(7)
        )

        val encoded = SchemaPdcCodec.encode(context(), NativePrimitives, value)

        assertEquals("hello", encoded.get(key("string_value"), PersistentDataType.STRING))
        assertEquals(true, encoded.get(key("boolean_value"), PersistentDataType.BOOLEAN))
        assertEquals(1.toByte(), encoded.get(key("byte_value"), PersistentDataType.BYTE))
        assertEquals(2.toShort(), encoded.get(key("short_value"), PersistentDataType.SHORT))
        assertEquals(3, encoded.get(key("int_value"), PersistentDataType.INTEGER))
        assertEquals(4L, encoded.get(key("long_value"), PersistentDataType.LONG))
        assertEquals(5.5f, encoded.get(key("float_value"), PersistentDataType.FLOAT))
        assertEquals(6.5, encoded.get(key("double_value"), PersistentDataType.DOUBLE))
        assertEquals("x", encoded.get(key("char_value"), PersistentDataType.STRING))
        assertEquals("test:value", encoded.get(key("identifier_value"), PersistentDataType.STRING))
        assertEquals(7, encoded.get(key("mapped_value"), PersistentDataType.INTEGER))
        assertNoTaggedMetadata(encoded)
        assertEquals(value, SchemaPdcCodec.decode(NativePrimitives, encoded))
    }

    @Test
    public fun `objects lists nullable values and omitted defaults retain their schema meaning`() {
        val value = NativeProfile(
            stats = NativeStats(level = 12, enabled = true),
            roles = listOf("admin", "builder"),
            aliases = listOf("main", null),
            matrix = listOf(listOf(1, 2), listOf(3)),
            nickname = null
        )

        val encoded = SchemaPdcCodec.encode(context(), NativeProfile, value)
        val stats = assertNotNull(encoded.get(key("stats"), PersistentDataType.TAG_CONTAINER))
        val aliases = assertNotNull(encoded.get(key("aliases"), PersistentDataType.LIST.dataContainers()))
        val matrix = assertNotNull(encoded.get(key("matrix"), PersistentDataType.LIST.dataContainers()))

        assertEquals(12, stats.get(key("level"), PersistentDataType.INTEGER))
        assertEquals(true, stats.get(key("enabled"), PersistentDataType.BOOLEAN))
        assertEquals(listOf("admin", "builder"), encoded.get(key("roles"), PersistentDataType.LIST.strings()))
        assertEquals("main", aliases[0].get(codecKey("value"), PersistentDataType.STRING))
        assertContentEquals(byteArrayOf(), aliases[1].get(codecKey("value"), PersistentDataType.BYTE_ARRAY))
        assertEquals(listOf(1, 2), matrix[0].get(codecKey("value"), PersistentDataType.LIST.integers()))
        assertContentEquals(byteArrayOf(), encoded.get(key("nickname"), PersistentDataType.BYTE_ARRAY))
        assertFalse(encoded.has(key("note")))
        assertNoTaggedMetadata(encoded)
        assertEquals(value, SchemaPdcCodec.decode(NativeProfile, encoded))
    }

    @Test
    public fun `object map dynamic and opaque lists use their descriptor selected layouts`() {
        val value = NativeCollections(
            stats = listOf(NativeStats(3, true)),
            limitSets = listOf(mapOf("soft" to 8)),
            dynamics = listOf(Variant.String("value"), Variant.Int(4)),
            opaques = listOf(OpaqueValue(Variant.Boolean(true)))
        )

        val encoded = SchemaPdcCodec.encode(context(), NativeCollections, value)
        val stats = assertNotNull(encoded.get(key("stats"), PersistentDataType.LIST.dataContainers()))
        val limitSets = assertNotNull(encoded.get(key("limit_sets"), PersistentDataType.LIST.dataContainers()))
        val dynamics = assertNotNull(encoded.get(key("dynamics"), PersistentDataType.LIST.dataContainers()))
        val opaques = assertNotNull(encoded.get(key("opaques"), PersistentDataType.LIST.dataContainers()))

        assertEquals(3, stats.single().get(key("level"), PersistentDataType.INTEGER))
        assertEquals(8, limitSets.single().get(key("soft"), PersistentDataType.INTEGER))
        assertEquals(
            VariantKind.String.id.toString(),
            dynamics.first().get(id("cutapi:variant_type").toNamespacedKey(), PersistentDataType.STRING)
        )
        assertEquals(
            VariantKind.Boolean.id.toString(),
            opaques.single().get(id("cutapi:variant_type").toNamespacedKey(), PersistentDataType.STRING)
        )
        assertEquals(value, SchemaPdcCodec.decode(NativeCollections, encoded))
    }

    @Test
    public fun `typed maps are native while dynamic and opaque values remain tagged`() {
        val value = NativeMaps(
            limits = linkedMapOf("soft" to 10, "other:hard" to 20),
            metadata = linkedMapOf("count" to Variant.Int(3), "other:flag" to Variant.Boolean(true)),
            opaque = OpaqueValue(Variant.Identifier(id("test:opaque")))
        )

        val encoded = SchemaPdcCodec.encode(context(), NativeMaps, value)
        val limits = assertNotNull(encoded.get(key("limits"), PersistentDataType.TAG_CONTAINER))
        val metadata = assertNotNull(encoded.get(key("metadata"), PersistentDataType.TAG_CONTAINER))
        val taggedCount = assertNotNull(metadata.get(key("count"), PersistentDataType.TAG_CONTAINER))
        val opaque = assertNotNull(encoded.get(key("opaque"), PersistentDataType.TAG_CONTAINER))

        assertEquals(10, limits.get(key("soft"), PersistentDataType.INTEGER))
        assertEquals(20, limits.get(id("other:hard").toNamespacedKey(), PersistentDataType.INTEGER))
        assertEquals(
            VariantKind.Int.id.toString(),
            taggedCount.get(id("cutapi:variant_type").toNamespacedKey(), PersistentDataType.STRING)
        )
        assertEquals(3, taggedCount.get(id("cutapi:variant_value").toNamespacedKey(), PersistentDataType.INTEGER))
        assertEquals(
            VariantKind.Identifier.id.toString(),
            opaque.get(id("cutapi:variant_type").toNamespacedKey(), PersistentDataType.STRING)
        )
        assertEquals(value, SchemaPdcCodec.decode(NativeMaps, encoded))
    }

    @Test
    public fun `same namespace map keys normalize to their unqualified form`() {
        val encoded = SchemaPdcCodec.encode(
            context(),
            NativeMaps,
            NativeMaps(
                limits = mapOf("test:soft" to 10),
                metadata = emptyMap(),
                opaque = OpaqueValue(Variant.Null)
            )
        )

        val decoded = SchemaPdcCodec.decode(NativeMaps, encoded)

        assertEquals(mapOf("soft" to 10), decoded.limits)
    }

    @Test
    public fun `map key normalization collisions fail with a schema path`() {
        val error = assertFailsWith<DataSerializationException> {
            SchemaPdcCodec.encode(
                context(),
                NativeMaps,
                NativeMaps(
                    limits = linkedMapOf("soft" to 10, "test:soft" to 20),
                    metadata = emptyMap(),
                    opaque = OpaqueValue(Variant.Null)
                )
            )
        }

        assertContains(error.message.orEmpty(), "normalize to the same PDC key")
        assertContains(error.message.orEmpty(), "limits")
    }

    @Test
    public fun `invalid map names are rejected at the PDC boundary`() {
        for (name in listOf("Bad", "bad key", ":bad", "a:b:c")) {
            val error = assertFailsWith<DataSerializationException> {
                SchemaPdcCodec.encode(
                    context(),
                    NativeMaps,
                    NativeMaps(
                        limits = mapOf(name to 10),
                        metadata = emptyMap(),
                        opaque = OpaqueValue(Variant.Null)
                    )
                )
            }

            assertContains(error.message.orEmpty(), name)
            assertContains(error.message.orEmpty(), "limits")
        }
    }

    @Test
    public fun `polymorphic values store only their concrete schema id`() {
        val value = NativeDrawing(NativeCircle(name = "main", radius = 4.0))

        val encoded = SchemaPdcCodec.encode(context(), NativeDrawing, value)
        val shape = assertNotNull(encoded.get(key("shape"), PersistentDataType.TAG_CONTAINER))

        assertEquals(
            NativeCircleSchema.id.toString(),
            shape.get(codecKey("schema_type"), PersistentDataType.STRING)
        )
        assertEquals("main", shape.get(key("name"), PersistentDataType.STRING))
        assertEquals(4.0, shape.get(key("radius"), PersistentDataType.DOUBLE))
        assertFalse(shape.has(id("cutapi:variant_type").toNamespacedKey()))
        assertEquals(value, SchemaPdcCodec.decode(NativeDrawing, encoded))
    }

    @Test
    public fun `camel case schema property names use snake case PDC keys`() {
        val variant = InvalidNativeName.serialize(InvalidNativeName(3)).getOrThrow()
        assertEquals(Variant.Int(3), assertIs<Variant.Map>(variant).value["badName"])

        val encoded = SchemaPdcCodec.encode(context(), InvalidNativeName, InvalidNativeName(3))

        assertEquals(3, encoded.get(key("bad_name"), PersistentDataType.INTEGER))
        assertFalse(encoded.has(key("badName")))
        assertEquals(InvalidNativeName(3), SchemaPdcCodec.decode(InvalidNativeName, encoded))
    }

    @Test
    public fun `schema property PDC normalization collisions fail with both names`() {
        val error = assertFailsWith<DataSerializationException> {
            SchemaPdcCodec.encode(
                context(),
                CollidingNativeNames,
                CollidingNativeNames(camelValue = 3, snakeValue = 4)
            )
        }

        assertContains(error.message.orEmpty(), "camelValue")
        assertContains(error.message.orEmpty(), "camel_value")
        assertContains(error.message.orEmpty(), "normalize to the same PDC key")
    }

    @Test
    public fun `reserved codec property names are rejected`() {
        val error = assertFailsWith<DataSerializationException> {
            SchemaPdcCodec.encode(context(), ReservedNativeName, ReservedNativeName(3))
        }

        assertContains(error.message.orEmpty(), "cutapi:codec/value")
        assertContains(error.message.orEmpty(), "reserved")
    }

    @Test
    public fun `decode errors identify the property path and expected PDC type`() {
        val encoded = SchemaPdcCodec.encode(context(), NativeStats, NativeStats(2, true))
        encoded.set(key("level"), PersistentDataType.STRING, "wrong")

        val error = assertFailsWith<DataSerializationException> {
            SchemaPdcCodec.decode(NativeStats, encoded)
        }

        assertContains(error.message.orEmpty(), "test:native_stats.level")
        assertContains(error.message.orEmpty(), "PDC type INTEGER")
    }

    private fun context(): PersistentDataAdapterContext =
        server.addPlayer().persistentDataContainer.adapterContext

    private fun key(value: String) = id("test:$value").toNamespacedKey()

    private fun codecKey(value: String) = id("cutapi:codec/$value").toNamespacedKey()

    private fun assertNoTaggedMetadata(container: PersistentDataContainer) {
        for (key in container.keys) {
            assertFalse(
                key.namespace == "cutapi" &&
                    (key.key.startsWith("variant_") || key.key.startsWith("variant_entry/")),
                "Unexpected tagged Variant metadata key $key"
            )
            if (container.has(key, PersistentDataType.TAG_CONTAINER)) {
                container.get(key, PersistentDataType.TAG_CONTAINER)?.let(::assertNoTaggedMetadata)
            }
        }
    }
}

private data class NativePrimitives(
    val stringValue: String,
    val booleanValue: Boolean,
    val byteValue: Byte,
    val shortValue: Short,
    val intValue: Int,
    val longValue: Long,
    val floatValue: Float,
    val doubleValue: Double,
    val charValue: Char,
    val identifierValue: Identifier,
    val mappedValue: NativeNumber
) {
    companion object : Schema<NativePrimitives> by schema(id("test:native_primitives"), {
        property(NativePrimitives::stringValue, VariantSerializer.String, name = "string_value")
        property(NativePrimitives::booleanValue, VariantSerializer.Boolean, name = "boolean_value")
        property(NativePrimitives::byteValue, VariantSerializer.Byte, name = "byte_value")
        property(NativePrimitives::shortValue, VariantSerializer.Short, name = "short_value")
        property(NativePrimitives::intValue, VariantSerializer.Int, name = "int_value")
        property(NativePrimitives::longValue, VariantSerializer.Long, name = "long_value")
        property(NativePrimitives::floatValue, VariantSerializer.Float, name = "float_value")
        property(NativePrimitives::doubleValue, VariantSerializer.Double, name = "double_value")
        property(NativePrimitives::charValue, VariantSerializer.Char, name = "char_value")
        property(NativePrimitives::identifierValue, VariantSerializer.Id, name = "identifier_value")
        property(NativePrimitives::mappedValue, NativeNumberSerializer, name = "mapped_value")
    })
}

private data class NativeNumber(val value: Int)

private val NativeNumberSerializer: Serializer<NativeNumber> = VariantSerializer.mapped(
    VariantSerializer.Int,
    serialize = { value -> value.value },
    deserialize = { value -> NativeNumber(value) }
)

private data class NativeStats(
    val level: Int,
    val enabled: Boolean
) {
    companion object : Schema<NativeStats> by schema(id("test:native_stats"), {
        property(NativeStats::level, VariantSerializer.Int)
        property(NativeStats::enabled, VariantSerializer.Boolean)
    })
}

private data class NativeProfile(
    val stats: NativeStats,
    val roles: List<String>,
    val aliases: List<String?>,
    val matrix: List<List<Int>>,
    val nickname: String?,
    val note: String = ""
) {
    companion object : Schema<NativeProfile> by schema(id("test:native_profile"), {
        property(NativeProfile::stats, NativeStats)
        property(NativeProfile::roles, VariantSerializer.ListOf(VariantSerializer.String))
        property(NativeProfile::aliases, VariantSerializer.ListOf(VariantSerializer.String.nullable()))
        property(
            NativeProfile::matrix,
            VariantSerializer.ListOf(VariantSerializer.ListOf(VariantSerializer.Int))
        )
        property(NativeProfile::nickname, VariantSerializer.String.nullable())
        property(NativeProfile::note, VariantSerializer.String) {
            optional(omitDefaults = true) { "" }
        }
    })
}

private data class NativeCollections(
    val stats: List<NativeStats>,
    val limitSets: List<Map<String, Int>>,
    val dynamics: List<Variant>,
    val opaques: List<OpaqueValue>
) {
    companion object : Schema<NativeCollections> by schema(id("test:native_collections"), {
        property(NativeCollections::stats, VariantSerializer.ListOf(NativeStats))
        property(
            NativeCollections::limitSets,
            VariantSerializer.ListOf(VariantSerializer.MapOf(VariantSerializer.Int)),
            name = "limit_sets"
        )
        property(NativeCollections::dynamics, VariantSerializer.ListOf(VariantSerializer.AnyVariant))
        property(NativeCollections::opaques, VariantSerializer.ListOf(OpaqueValueSerializer))
    })
}

private data class OpaqueValue(val variant: Variant)

private val OpaqueValueSerializer: Serializer<OpaqueValue> = serializer(
    descriptor = SerializerDescriptor.opaque(id("test:opaque_value")),
    serialize = OpaqueValue::variant,
    deserialize = ::OpaqueValue
)

private data class NativeMaps(
    val limits: Map<String, Int>,
    val metadata: Map<String, Variant>,
    val opaque: OpaqueValue
) {
    companion object : Schema<NativeMaps> by schema(id("test:native_maps"), {
        property(NativeMaps::limits, VariantSerializer.MapOf(VariantSerializer.Int))
        property(NativeMaps::metadata, VariantSerializer.Map)
        property(NativeMaps::opaque, OpaqueValueSerializer)
    })
}

private open class NativeShape(open val name: String)

private data class NativeCircle(
    override val name: String,
    val radius: Double
) : NativeShape(name)

private val NativeCircleSchema: Schema<NativeCircle> = schema(id("test:native_circle"), {
    property(NativeCircle::name, VariantSerializer.String)
    property(NativeCircle::radius, VariantSerializer.Double)
})

private val NativeShapeSchema: PolySchema<NativeShape> = polySchema(
    NativeShape::class,
    id("test:native_shape")
) {
    property(NativeShape::name, VariantSerializer.String)
    include(NativeCircleSchema)
}

private data class NativeDrawing(val shape: NativeShape) {
    companion object : Schema<NativeDrawing> by schema(id("test:native_drawing"), {
        property(NativeDrawing::shape, NativeShapeSchema)
    })
}

private data class InvalidNativeName(val badName: Int) {
    companion object : Schema<InvalidNativeName> by schema(id("test:invalid_native_name"), {
        property(InvalidNativeName::badName, VariantSerializer.Int)
    })
}

private data class CollidingNativeNames(
    val camelValue: Int,
    val snakeValue: Int
) {
    companion object : Schema<CollidingNativeNames> by schema(id("test:colliding_native_names"), {
        property(CollidingNativeNames::camelValue, VariantSerializer.Int)
        property(CollidingNativeNames::snakeValue, VariantSerializer.Int, name = "camel_value")
    })
}

private data class ReservedNativeName(val value: Int) {
    companion object : Schema<ReservedNativeName> by schema(id("cutapi:reserved_native_name"), {
        property(ReservedNativeName::value, VariantSerializer.Int, name = "codec/value")
    })
}
