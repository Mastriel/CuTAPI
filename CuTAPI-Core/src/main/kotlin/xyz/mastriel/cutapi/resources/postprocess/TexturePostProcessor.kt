package xyz.mastriel.cutapi.resources.postprocess

import com.jhlabs.image.ContrastFilter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import net.peanuuutz.tomlkt.TomlTable
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.postprocess.builtin.BufferedImageOpPostProcessor
import xyz.mastriel.cutapi.resources.postprocess.builtin.builtinPostProcessor
import xyz.mastriel.cutapi.resources.builtin.Texture2D

object TexturePostProcessorSerializer :
    IdentifiableSerializer<TexturePostProcessor>("texture_post_processor", TexturePostProcessor)

@Serializable(with = TexturePostProcessorSerializer::class)
abstract class TexturePostProcessor(override val id: Identifier) : Identifiable {

    abstract fun process(texture: Texture2D, context: TexturePostProcessContext)

    companion object : IdentifierRegistry<TexturePostProcessor>("Texture Post Processors") {

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

data class TexturePostProcessContext(private val optionTable: TomlTable) {

    fun optionsMap() = optionTable.toMap()
    fun <S> castOptions(serializer: KSerializer<S>) : S {
        return CuTAPI.toml.decodeFromTomlElement(serializer, optionTable)
    }
}