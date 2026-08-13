package xyz.mastriel.cutapi.resources.process

import com.jhlabs.image.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.process.builtin.*

/** A registered texture processor whose ID is its typed option-schema ID. */
public abstract class TexturePostProcessor<O : Any>(
    public val optionsSchema: Schema<O>
) : Identifiable {
    final override val id: Identifier get() = optionsSchema.id

    public abstract fun process(texture: Texture2D, context: TexturePostProcessContext<O>)

    internal fun decodeTable(root: Variant.Map): TexturePostprocessTable<O> {
        val normalized = root.normalize(optionsSchema.descriptor)
        return TexturePostprocessTable(this, optionsSchema.deserialize(normalized).getOrThrow())
    }

    public companion object :
        IdentifierRegistry<TexturePostProcessor<*>>(id("cutapi:registry/texture_post_processor")) {
        override fun initialize() {
            super.initialize()
            getAllValues().forEach { it.optionsSchema.requireRegistered() }
        }

        public fun registerBuiltins() {
            val processor = builtinPostProcessor(id(Plugin, "brightness_contrast"), ContrastFilter()) {
                property("brightness", VariantSerializer.Float, ContrastFilter::setBrightness)
                property("contrast", VariantSerializer.Float, ContrastFilter::setContrast)
            }
            Schema.modifyRegistry { register(processor.optionsSchema) }
            register(processor)
        }
    }
}

public data class TexturePostProcessContext<out O : Any>(public val options: O)
