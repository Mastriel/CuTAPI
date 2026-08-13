package xyz.mastriel.cutapi.resources.data

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.process.*
import xyz.mastriel.cutapi.testing.*
import java.io.*
import kotlin.io.path.*
import kotlin.test.*

class SchemaBackedResourceMetadataTest : MockBukkitTest() {
    data class Nested(val count: Int = 3) {
        companion object : Schema<Nested> by schema(id("test:nested_metadata"), {
            untagged = true
            property(Nested::count, VariantSerializer.Int) {
                optional(omitDefaults = true) { 3 }
            }
        })
    }

    data class WidgetMetadata(
        val displayName: String,
        val variants: List<String> = emptyList(),
        val isActive: Boolean = true,
        val nested: Nested = Nested()
    ) : CuTMeta() {
        companion object : Schema<WidgetMetadata> by schema(id("test:widget_metadata"), {
            extends { CuTMeta }
            property(WidgetMetadata::displayName, VariantSerializer.String)
            property(WidgetMetadata::variants, VariantSerializer.ListOf(VariantSerializer.String)) {
                optional(omitDefaults = true) { emptyList() }
            }
            property(WidgetMetadata::isActive, VariantSerializer.Boolean, name = "active") {
                optional(omitDefaults = true) { true }
            }
            property(WidgetMetadata::nested, Nested) {
                optional(omitDefaults = true) { Nested() }
            }
        })
    }

    data class StrictMetadata(val name: String) : CuTMeta() {
        companion object : Schema<StrictMetadata> by schema(id("test:strict_metadata"), {
            extends { CuTMeta }
            strict = true
            property(StrictMetadata::name, VariantSerializer.String)
        })
    }

    @Test
    fun `decodes schema metadata and normalizes scalar descriptors`() {
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

        assertEquals("test:widget_metadata", document.requireTypeId().toString())
        val metadata = document.decode(WidgetMetadata)
        assertEquals("Example", metadata.displayName)
        assertEquals(listOf("red", "blue"), metadata.variants)
        assertFalse(metadata.isActive)
        assertEquals(7, metadata.nested.count)
    }

    @Test
    fun `inherits permissive CuTMeta strictness and discards unknown fields`() {
        val document = ResourceYaml.parse(
            """
            !<test:widget_metadata>
            displayName: Example
            extraValue: ignored
            """.trimIndent()
        )

        val metadata = document.decode(WidgetMetadata)
        val encoded = WidgetMetadata.serialize(metadata).getOrThrow().requireMap()
        assertTrue(metadata.isActive)
        assertEquals(3, metadata.nested.count)
        assertNull(encoded["extraValue"])
        assertFalse(WidgetMetadata.strict)
    }

    @Test
    fun `explicit strict child overrides permissive parent`() {
        val document = ResourceYaml.parse(
            """
            !<test:strict_metadata>
            name: Example
            typo: true
            """.trimIndent(),
            "strict.meta"
        )

        val error = assertFailsWith<ResourceDocumentException> { document.decode(StrictMetadata) }
        assertEquals(3, error.sourceSpan?.startLine)
        assertContains(error.message.orEmpty(), "strict.meta:3:")
        assertTrue(StrictMetadata.strict)
    }

    @Test
    fun `decodes typed flattened generator blocks`() {
        ensureTypedRegistries()
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

        val block = document.decode(WidgetMetadata).generateBlocks.single()
        assertEquals("test:make_variant", block.generatorId.toString())
        assertEquals("generated", block.subId)
        val options = assertIs<TestGeneratorOptions>(block.options)
        assertEquals(16, options.outputWidth)
    }

    @Test
    fun `dynamic option errors resolve to the exact YAML span`() {
        ensureTypedRegistries()
        val document = ResourceYaml.parse(
            """
            !<test:widget_metadata>
            displayName: Example
            generate:
              - !<test:make_variant>
                subId: generated
                outputWidth: wide
            """.trimIndent(),
            "widget.png.meta"
        )

        val error = assertFailsWith<ResourceDocumentException> { document.decode(WidgetMetadata) }
        assertEquals(6, error.sourceSpan?.startLine)
        assertContains(error.message.orEmpty(), "widget.png.meta:6:")
    }

    @Test
    fun `decodes typed texture processor options from tags`() {
        ensureTypedRegistries()
        val document = ResourceYaml.parse(
            """
            !<cutapi:texture2d>
            postProcess:
              - !<test:tint>
                amount: 3
            """.trimIndent()
        )

        val table = document.decode(Texture2D.Metadata).postProcess.single()
        assertEquals("test:tint", table.processor.id.toString())
        assertEquals(3, assertIs<TestProcessorOptions>(table.options).amount)
    }

    @Test
    fun `round trips metadata with YAML tags rather than discriminator fields`() {
        val original = WidgetMetadata("Round trip", listOf("one"), isActive = false)
        val yaml = ResourceYaml.encode(WidgetMetadata.serialize(original).getOrThrow())
        val decoded = ResourceYaml.parse(yaml).decode(WidgetMetadata)

        assertEquals(original, decoded)
        assertTrue(yaml.startsWith("!<test:widget_metadata>"))
        assertFalse(SCHEMA_TYPE_DISCRIMINATOR in yaml)
    }

    @Test
    fun `saveWithMetadata discovers the companion schema`() {
        val directory = createTempDirectory("cutapi-schema-metadata").toFile()
        try {
            val file = File(directory, "widget.bin")
            SavedResource(
                ref(SavedResourceRoot, "widget.bin"),
                WidgetMetadata("Saved")
            ).saveWithMetadata(file)

            assertContentEquals(byteArrayOf(1, 2, 3), file.readBytes())
            val yaml = File(file.path + ".meta").readText()
            assertTrue(yaml.startsWith("!<test:widget_metadata>"))
            assertEquals("Saved", ResourceYaml.parse(yaml).decode(WidgetMetadata).displayName)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `resource loader derives its ID and accepts typed programmatic metadata`() {
        val loader = resourceLoader<SavedResource, WidgetMetadata>(
            extensions = listOf("bin"),
            metadataSchema = WidgetMetadata
        ) {
            success(SavedResource(ref, metadata!!))
        }
        val metadata = WidgetMetadata("Programmatic")
        val result = loader.loadResource(
            ref(SavedResourceRoot, "widget.bin"),
            byteArrayOf(1),
            metadata = null,
            options = ResourceLoadOptions(metadata = metadata)
        )

        assertEquals(WidgetMetadata.id, loader.id)
        assertEquals(metadata, assertIs<ResourceLoadResult.Success<SavedResource>>(result).resource.metadata)
    }

    @Test
    fun `rejects duplicate keys and multiple documents`() {
        assertFailsWith<ResourceDocumentException> { ResourceYaml.parse("value: 1\nvalue: 2") }
        assertFailsWith<ResourceDocumentException> { ResourceYaml.parse("value: 1\n---\nvalue: 2") }
    }

    private fun ensureTypedRegistries() {
        if (!Schema.has(TestGeneratorOptions.id)) {
            check(Schema.isOpen)
            Schema.modifyRegistry {
                register(TestGeneratorOptions)
                register(TestProcessorOptions)
            }
            Schema.initialize()
        }
        if (!ResourceGenerator.has(TestGenerator.id)) {
            check(ResourceGenerator.isOpen)
            ResourceGenerator.modifyRegistry { register(TestGenerator) }
            ResourceGenerator.initialize()
        }
        if (!TexturePostProcessor.has(TestProcessor.id)) {
            check(TexturePostProcessor.isOpen)
            TexturePostProcessor.modifyRegistry { register(TestProcessor) }
            TexturePostProcessor.initialize()
        }
    }

    private class SavedResource(
        override val ref: ResourceRef<SavedResource>,
        override val metadata: WidgetMetadata
    ) : Resource(ref, metadata), ByteArraySerializable {
        override fun toBytes(): ByteArray = byteArrayOf(1, 2, 3)
    }
}

private data class TestGeneratorOptions(val outputWidth: Int) {
    companion object : Schema<TestGeneratorOptions> by schema(id("test:make_variant"), {
        property(TestGeneratorOptions::outputWidth, VariantSerializer.Int)
    })
}

private val TestGenerator: ResourceGenerator<TestGeneratorOptions> =
    resourceGenerator<Resource, TestGeneratorOptions>(TestGeneratorOptions) { }

private data class TestProcessorOptions(val amount: Int) {
    companion object : Schema<TestProcessorOptions> by schema(id("test:tint"), {
        property(TestProcessorOptions::amount, VariantSerializer.Int)
    })
}

private object TestProcessor : TexturePostProcessor<TestProcessorOptions>(TestProcessorOptions) {
    override fun process(texture: Texture2D, context: TexturePostProcessContext<TestProcessorOptions>) = Unit
}

private object SavedResourceRoot : ResourceRoot {
    override val cutPlugin: CuTPlugin
        get() = error("Metadata saving does not resolve the resource root plugin")

    override fun getResourcesFolder(): File = File(".")
}
