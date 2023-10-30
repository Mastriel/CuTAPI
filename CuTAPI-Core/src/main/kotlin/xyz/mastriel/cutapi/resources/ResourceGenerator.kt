package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resources.builtin.Texture2D

abstract class ResourceGenerator(
    override val id: Identifier
) : Identifiable {

    /**
     * [context] and the return value can NOT have the same [ResourceRef]!
     */
    abstract fun generate(context: ResourceGeneratorContext<Resource>): Resource?

    companion object : IdentifierRegistry<ResourceGenerator>("Resource Generators")
}

data class ResourceGeneratorContext<out T : Resource>(val resource: T)

/**
 * Ran for ALL resources that exist.
 */
fun resourceGenerator(id: Identifier, block: (ResourceGeneratorContext<Resource>) -> Resource?): ResourceGenerator {
    return object : ResourceGenerator(id) {
        override fun generate(context: ResourceGeneratorContext<Resource>): Resource? {
            return block(context)
        }
    }
}

/**
 * Ran only for resources of a certain type that has [id] in its `generate` metadata section.
 */
inline fun <reified T : Resource> resourceGenerator(
    id: Identifier,
    crossinline block: (ResourceGeneratorContext<T>) -> T?
): ResourceGenerator {
    return object : ResourceGenerator(id) {
        override fun generate(context: ResourceGeneratorContext<Resource>): Resource? {
            if (context.resource is T) {
                // very nasty
                @Suppress("UNCHECKED_CAST")
                return block(context as ResourceGeneratorContext<T>)
            }
            return null
        }
    }
}
