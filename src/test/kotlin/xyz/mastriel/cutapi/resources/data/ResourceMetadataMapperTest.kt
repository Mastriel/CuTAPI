package xyz.mastriel.cutapi.resources.data

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*
import kotlin.test.*

class ResourceMetadataMapperTest {
    @ResourceMetadata(id = "test:widget_metadata")
    data class WidgetMetadata(
        val displayName: String,
        val variants: List<String> = emptyList(),
        @param:ResourceKey("active") val isActive: Boolean = true,
        val nested: Nested = Nested()
    ) : CuTMeta()

    data class Nested(val count: Int = 3)

    private data class WrappedValue(val value: String)

    @Test
    fun `decodes annotated metadata without kotlinx serialization`() {
        val document = ResourceYaml.parse(
            """
            !<test:widget_metadata>
            displayName: Example
            variants: [red, blue]
            active: false
            nested:
              count: 7
            """.trimIndent(),
            "widget.png.meta"
        )

        assertEquals("test:widget_metadata", document.requireTag().toString())
        val metadata = ResourceMetadataMapper.decodeMetadata(
            WidgetMetadata::class,
            document.requireMap()
        )

        assertEquals("Example", metadata.displayName)
        assertEquals(listOf("red", "blue"), metadata.variants)
        assertFalse(metadata.isActive)
        assertEquals(7, metadata.nested.count)
    }

    @Test
    fun `uses defaults and warns about unknown keys in non-strict mode`() {
        val warnings = mutableListOf<String>()
        val document = ResourceYaml.parse(
            """
            !<test:widget_metadata>
            displayName: Example
            extraValue: ignored
            """.trimIndent(),
            "widget.png.meta"
        )

        val metadata = ResourceMetadataMapper.decodeMetadata(
            WidgetMetadata::class,
            document.requireMap(),
            ResourceMappingContext(warning = warnings::add)
        )

        assertTrue(metadata.isActive)
        assertEquals(3, metadata.nested.count)
        assertEquals(1, warnings.size)
        assertContains(warnings.single(), "widget.png.meta:3:")
    }

    @Test
    fun `rejects unknown keys in strict mode`() {
        val document = ResourceYaml.parse(
            """
            !<test:widget_metadata>
            displayName: Example
            typo: true
            """.trimIndent(),
            "widget.png.meta"
        )

        assertFailsWith<ResourceConfigException> {
            ResourceMetadataMapper.decodeMetadata(
                WidgetMetadata::class,
                document.requireMap(),
                ResourceMappingContext(strictUnknownKeys = true)
            )
        }
    }

    @Test
    fun `parses tagged generator blocks`() {
        val document = ResourceYaml.parse(
            """
            !<test:widget_metadata>
            displayName: Example
            generate:
              - !<test:make_variant>
                subId: generated
                outputWidth: 16
            """.trimIndent()
        )

        val metadata = ResourceMetadataMapper.decodeMetadata(WidgetMetadata::class, document.requireMap())
        val block = metadata.generateBlocks.single()

        assertEquals("test:make_variant", block.generatorId.toString())
        assertEquals("generated", block.subId)
        assertEquals(16L, block.options["outputWidth"]?.asConfigScalar()?.value)
    }

    @Test
    fun `round trips metadata with its resource tag`() {
        val original = WidgetMetadata("Round trip", listOf("one"), isActive = false)
        val yaml = ResourceYaml.encode(ResourceMetadataMapper.encodeMetadata(original))
        val parsed = ResourceYaml.parse(yaml)
        val decoded = ResourceMetadataMapper.decodeMetadata(WidgetMetadata::class, parsed.requireMap())

        assertEquals(original, decoded)
        assertTrue(yaml.startsWith("!<test:widget_metadata>"))
    }

    @Test
    fun `supports registered value codecs`() {
        val codec = object : ResourceValueCodec {
            override val id: Identifier = id("test:wrapped_value")

            override fun supports(type: KType): Boolean = type.classifier == WrappedValue::class

            override fun decode(
                value: ResourceConfigValue,
                type: KType,
                context: ResourceMappingContext,
                path: String
            ): Any = WrappedValue(value.asConfigScalar().value as String)

            override fun encode(value: Any, type: KType): ResourceConfigValue =
                ResourceConfigScalar((value as WrappedValue).value)
        }

        data class Holder(val wrapped: WrappedValue)

        ResourceValueCodec.modifyRegistry {
            register(codec)
        }
        ResourceValueCodec.initialize()

        val holder = ResourceMetadataMapper.decode<Holder>(
            ResourceConfigMap(mapOf("wrapped" to ResourceConfigScalar("custom")))
        )
        assertEquals("custom", holder.wrapped.value)
    }

    @Test
    fun `rejects duplicate keys and multiple documents`() {
        assertFailsWith<ResourceConfigException> {
            ResourceYaml.parse("value: 1\nvalue: 2")
        }
        assertFailsWith<ResourceConfigException> {
            ResourceYaml.parse("value: 1\n---\nvalue: 2")
        }
    }
}
