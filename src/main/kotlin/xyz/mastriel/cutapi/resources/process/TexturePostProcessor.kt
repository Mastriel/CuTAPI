package xyz.mastriel.cutapi.resources.process

import com.jhlabs.image.*
import kotlinx.serialization.*
import net.peanuuutz.tomlkt.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.process.builtin.*

public object TexturePostProcessorSerializer :
    IdentifiableSerializer<TexturePostProcessor>("texture_post_processor", TexturePostProcessor)

@Serializable(with = TexturePostProcessorSerializer::class)
public abstract class TexturePostProcessor(override val id: Identifier) : Identifiable {

    public abstract fun process(texture: Texture2D, context: TexturePostProcessContext)

    public companion object : IdentifierRegistry<TexturePostProcessor>("Texture Post Processors") {

        public fun registerBuiltins() {
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

public data class TexturePostProcessContext(private val optionTable: TomlTable) {

    public fun optionsMap(): Map<String, TomlElement> = optionTable.toMap()
    public fun <S> castOptions(serializer: KSerializer<S>): S {
        return CuTAPI.toml.decodeFromTomlElement(serializer, optionTable)
    }
}