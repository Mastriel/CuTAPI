package xyz.mastriel.cutapi.resources

import kotlinx.serialization.KSerializer
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.resources.data.GenerateBlock

/**
 * This is called after the server has finished initialization, but before CuTAPI has run the ResourceProcessors.
 * This should be used to
 */
abstract class ResourceGenerator(
    override val id: Identifier
) : Identifiable {

    /**
     * [context] and resources generated through [ResourceGeneratorContext.registrar] can NOT have the same [ResourceRef]!
     */
    abstract fun generate(context: ResourceGeneratorContext<Resource>)

    companion object : IdentifierRegistry<ResourceGenerator>("Resource Generators")
}

data class ResourceGeneratorContext<out T : Resource>(val resource: T, val generateBlock: GenerateBlock, val registrar: (Resource) -> Unit) {

    /**
     * Deserialize the options for this generator into a new object.
     *
     * @param S The type you're deserializing into.
     * @param serializer The serializer for [S].
     */
    fun <S> castOptions(serializer: KSerializer<S>) : S {
        return CuTAPI.toml.decodeFromTomlElement(serializer, generateBlock.options)
    }
}

/**
 * Ran for ALL resources that exist.
 */
fun resourceGenerator(id: Identifier, block: ResourceGeneratorContext<Resource>.() -> Resource?): ResourceGenerator {
    return object : ResourceGenerator(id) {
        override fun generate(context: ResourceGeneratorContext<Resource>) {
            block(context)
        }
    }
}

/**
 * Ran only for resources of a certain type that has [id] in its `generate` metadata section.
 */
@JvmName("resourceGeneratorWithType")
inline fun <reified T : Resource> resourceGenerator(
    id: Identifier,
    crossinline block: ResourceGeneratorContext<T>.() -> T?
): ResourceGenerator {
    return object : ResourceGenerator(id) {
        override fun generate(context: ResourceGeneratorContext<Resource>) {
            if (context.resource is T) {
                // very nasty
                @Suppress("UNCHECKED_CAST")
                block(context as ResourceGeneratorContext<T>)
            }
        }
    }
}
