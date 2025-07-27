package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.registry.*

/**
 * Represents a processor that is executed after the server has finished loading and all resources
 * have been registered. This processor is used to perform operations on resources without modifying
 * the [ResourceManager].
 *
 * **Important:** Do not modify the [ResourceManager] during this phase. Any resources added will not
 * go through the processor.
 *
 * - To add resources using generators, see [ResourceGenerator].
 * - To prepare resources for a resource pack, use [ResourcePackProcessor].
 *
 * @see [resourceProcessor]
 * @see [ResourcePackProcessor]
 */
public fun interface ResourceProcessor {

    /**
     * Processes the resources managed by the [ResourceManager].
     *
     * @param resources The [ResourceManager] containing all registered resources.
     */
    public fun processResources(resources: ResourceManager)

    /**
     * A registry for managing all registered [ResourceProcessor] instances.
     *
     * @property name The name of the registry, which is "Resource Processors".
     */
    public companion object : ListRegistry<ResourceProcessor>("Resource Processors")
}

/**
 * Represents a processor that is executed while the server is generating a resource pack.
 * This processor is used to process resources into a resource pack without modifying the
 * [ResourceManager] or making unnecessary modifications to the [Resource]s being processed.
 *
 * - To add resources using generators, see [ResourceGenerator].
 *
 * @see [resourceProcessor]
 * @see [ResourceProcessor]
 */
public object ResourcePackProcessor : ListRegistry<ResourceProcessor>("Resource Pack Processors")

/**
 * Provides a context for processing a specific type of resource.
 *
 * @param T The type of resource being processed.
 * @property resources The list of resources of type [T] to be processed.
 */
public data class ResourceProcessorContext<T : Resource>(val resources: List<T>)

/**
 * Creates a [ResourceProcessor] for a specific type of resource.
 *
 * This function filters resources of type [T] from the [ResourceManager] and provides them
 * to the given processing block.
 *
 * @param T The type of resource to process.
 * @param block The processing logic to apply to the filtered resources.
 * @return A [ResourceProcessor] that processes resources of type [T].
 */
public inline fun <reified T : Resource> resourceProcessor(crossinline block: ResourceProcessorContext<T>.() -> Unit): ResourceProcessor {
    return ResourceProcessor { resources ->
        val filteredResources = resources.getAllResources().filterIsInstance<T>()
        val context = ResourceProcessorContext(filteredResources)
        block(context)
    }
}