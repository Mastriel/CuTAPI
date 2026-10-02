package xyz.mastriel.cutapi.resources.builtin

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*
import xyz.mastriel.cutapi.resources.data.minecraft.*
import xyz.mastriel.cutapi.resources.process.*
import java.awt.image.*
import java.io.*
import javax.imageio.*


public open class Texture2D(
    override val ref: ResourceRef<Texture2D>,
    data: BufferedImage,
    final override val metadata: Metadata
) : Resource(ref), ByteArraySerializable {

    init {
        inspector.single("Glyph") {
            getGlyphOrNull(GlyphSize.Preview)?.let { "&f${it}" + "\n".repeat(5) } ?: "&7No Glyph Generated"
        }
    }


    public fun toMinecraftLocator(): String = ref.toMinecraftLocator()

    public open class Metadata(
        public val extendPostProcess: List<ResourceRef<PostProcessDefinitionsResource>> = emptyList(),
        public val postProcess: List<TexturePostprocessTable<*>> = emptyList(),
        public val animation: Animation? = null,
        public val fontSettings: FontSettings = FontSettings(),
    ) : CuTMeta() {
        public fun copy(): Metadata {
            return Metadata(
                extendPostProcess = extendPostProcess,
                postProcess = postProcess,
                animation = animation,
                fontSettings = fontSettings,
            ).also {
                it.emit = emit
                it.generateBlocks = generateBlocks
            }
        }

        /**
         * Warning! This is not available until all resources are loaded
         */
        public val postProcessors: List<TexturePostprocessTable<*>>
            get() {
                val list = mutableListOf<TexturePostprocessTable<*>>()
                for (extension in extendPostProcess) {
                    val processors = extension.getResource()?.metadata?.postProcessors
                    if (processors == null) {
                        Plugin.warn("Post process extension [$extension] has no processors, or does not exist.")
                    }
                    list.addAll(processors ?: emptyList())
                }
                list.addAll(postProcess)
                return list
            }

        public companion object : Schema<Metadata> by schema(id("cutapi:texture2d"), {
            extends { CuTMeta }
            property(
                Metadata::extendPostProcess,
                VariantSerializer.ListOf(VariantSerializer.ResourceRef<PostProcessDefinitionsResource>())
            ) {
                optional(omitDefaults = true) { emptyList() }
            }
            property(Metadata::postProcess, VariantSerializer.ListOf(TexturePostprocessTable)) {
                optional(omitDefaults = true) { emptyList() }
            }
            property(Metadata::animation, Animation.nullable()) {
                optional(omitDefaults = true) { null }
            }
            property(Metadata::fontSettings, FontSettings) {
                optional(omitDefaults = true) { FontSettings() }
            }
        })
    }

    internal var glyphChars: MutableMap<GlyphSize, String> = mutableMapOf()

    /**
     * May return a placeholder string if this emoji hasn't been loaded yet.
     */
    public fun getGlyph(size: GlyphSize = GlyphSize.Default): String {
        return glyphChars[size] ?: "<uninitialized emoji>"
    }

    public fun getGlyphOrNull(size: GlyphSize = GlyphSize.Default): String? {
        return glyphChars[size]
    }


    public var data: BufferedImage = data
        private set

    /**
     * Transforms the [data] of this texture to a new BufferedImage.
     */
    public fun process(func: (BufferedImage) -> BufferedImage) {
        data = func(data)
    }

    override fun toBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        ImageIO.write(data, "png", stream)
        return stream.toByteArray()
    }


}

public fun ResourceRef<Texture2D>.toMinecraftLocator(): String {
    val path = sanitizeResourcePath(path(withExtension = false, withNamespaceAsFolder = false))
    return "$minecraftNamespace:$path"
}

/**
 * May return a placeholder string if this emoji hasn't been loaded yet.
 */
public fun ResourceRef<Texture2D>.getGlyph(): String {
    return getResource()?.getGlyphOrNull() ?: "<uninitialized emoji>"
}

public fun ResourceRef<Texture2D>.getGlyphOrNull(): String? {
    return getResource()?.getGlyphOrNull()
}

public val Texture2DResourceLoader: ResourceFileLoader<Texture2D> = resourceLoader(
    extensions = listOf("png"),
    dependencies = listOf(PostProcessDefinitionsResource.Loader),
    metadataSchema = Texture2D.Metadata
) {
    val image = decodeTextureImage(data)
    success(Texture2D(ref, image, metadata ?: Texture2D.Metadata()))
}

public data class TexturePostprocessTable<O : Any>(
    public val processor: TexturePostProcessor<O>,
    public val options: O
) {
    public val postProcessId: Identifier get() = processor.id

    public companion object : Serializer<TexturePostprocessTable<*>> {
        override val descriptor: SerializerDescriptor<*> =
            SerializerDescriptor.opaque(id("cutapi:resource/texture_post_process"))

        override fun serialize(value: TexturePostprocessTable<*>): SerializeResult = try {
            @Suppress("UNCHECKED_CAST")
            val processor = value.processor as TexturePostProcessor<Any>
            SerializeResult.Success(processor.optionsSchema.serialize(value.options).getOrThrow())
        } catch (exception: Exception) {
            SerializeResult.Failure(exception)
        }

        override fun deserialize(variant: Variant): DeserializeResult<TexturePostprocessTable<*>> = try {
            val root = variant.requireMap()
            val type = (root[SCHEMA_TYPE_DISCRIMINATOR] as? Variant.String)?.value
                ?: throw VariantNormalizationException(
                    "Texture post-processor declaration must have a YAML tag.",
                    DataPath().child(SCHEMA_TYPE_DISCRIMINATOR),
                )
            val processor = TexturePostProcessor.getOrNull(id(type))
                ?: throw VariantNormalizationException(
                    "No texture post-processor is registered as $type.",
                    DataPath().child(SCHEMA_TYPE_DISCRIMINATOR),
                )
            DeserializeResult.Success(processor.decodeTable(root))
        } catch (exception: Exception) {
            DeserializeResult.Failure(exception)
        }
    }
}

public open class FontSettings(
    public val enabled: Boolean = true,
    public val ascent: Int? = null,
    public val height: Int? = null,
    public val advance: Int? = null,
) {
    public companion object : Schema<FontSettings> by schema(id("cutapi:resource/font_settings"), {
        untagged = true
        property(FontSettings::enabled, VariantSerializer.Boolean) {
            optional(omitDefaults = true) { true }
        }
        property(FontSettings::ascent, VariantSerializer.Int.nullable()) {
            optional(omitDefaults = true) { null }
        }
        property(FontSettings::height, VariantSerializer.Int.nullable()) {
            optional(omitDefaults = true) { null }
        }
        property(FontSettings::advance, VariantSerializer.Int.nullable()) {
            optional(omitDefaults = true) { null }
        }
    }) {
        public val Disabled: FontSettings = FontSettings(enabled = false)
    }
}
