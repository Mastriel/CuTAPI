package xyz.mastriel.cutapi.resourcepack.postprocess

import com.jhlabs.image.ContrastFilter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resourcepack.postprocess.builtin.BufferedImageOpPostProcessor
import xyz.mastriel.cutapi.resourcepack.postprocess.builtin.builtinPostProcessor
import xyz.mastriel.cutapi.resourcepack.resourcetypes.Texture

object TexturePostProcessorSerializer :
    IdentifiableSerializer<TexturePostProcessor>("texture_post_processor", TexturePostProcessor)

@Serializable(with = TexturePostProcessorSerializer::class)
abstract class TexturePostProcessor(override val id: Identifier) : Identifiable {

    abstract fun process(texture: Texture)

    protected fun Texture.getProperty(name: String, default: JsonElement): JsonElement {
        return meta.postProcess
            .find { it.processor.id == this@TexturePostProcessor.id }
            ?.properties
            ?.get(name) ?: default
    }

    protected val Texture.postProcessProperties get() = meta.postProcess
        .find { it.processor.id == this@TexturePostProcessor.id }
        ?.properties ?: emptyMap()

    protected fun Texture.getDoubleProperty(name: String, default: Double): Double {
        return meta.postProcess
            .find { it.processor.id == this@TexturePostProcessor.id }
            ?.properties
            ?.get(name)
            ?.jsonPrimitive
            ?.doubleOrNull ?: default
    }


    companion object : IdentifierRegistry<TexturePostProcessor>("Post Processors") {

        fun registerBuiltins() {
            builtinPostProcessor(id(Plugin, "brightness_contrast"), ContrastFilter()) {
                property("brightness", ContrastFilter::setBrightness)
                property("contrast", ContrastFilter::setContrast)
            }.register()
        }

        private fun BufferedImageOpPostProcessor<*>.register() {
            register(this)
        }
    }
}