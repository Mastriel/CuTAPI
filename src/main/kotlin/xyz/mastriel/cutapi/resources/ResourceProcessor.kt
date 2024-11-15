package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.registry.*


/**
 * A resource processor will be executed when the server has finished loading, and all resources
 * are registered. You should not modify the [ResourceManager], as this period is closed.
 * If you add more resources anyway, they will not go through the processor.
 *
 * If you want to add more resources using generators, see [ResourceGenerator]. Generated resources
 * are also processed here.
 *
 * If you want to prepare your resources to be processed into a resource pack, register to [ResourcePackProcessor]
 * instead. Both registries use the same type of [ResourceProcessor].
 *
 * @see [resourceProcessor]
 * @see [ResourcePackProcessor]
 */
public fun interface ResourceProcessor {
    public fun processResources(resources: ResourceManager)

    public companion object : ListRegistry<ResourceProcessor>("Resource Processors")
}


/**
 * A resource pack processor will be executed while the server is trying to generate a resource pack.
 * You should not modify the [ResourceManager], or make any unnecessary modifiacations to [Resource]s you're
 * processing.
 *
 * If you want to add more resources using generators, see [ResourceGenerator].
 *
 * @see [resourceProcessor]
 * @see [ResourceProcessor]
 */
public object ResourcePackProcessor : ListRegistry<ResourceProcessor>("Resource Pack Processors")


public data class ResourceProcessorContext<T : Resource>(val resources: List<T>)


public inline fun <reified T : Resource> resourceProcessor(crossinline block: ResourceProcessorContext<T>.() -> Unit): ResourceProcessor {
    return ResourceProcessor { resources ->
        val filteredResources = resources.getAllResources().filterIsInstance<T>()
        val context = ResourceProcessorContext(filteredResources)
        block(context)
    }
}