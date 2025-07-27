package xyz.mastriel.cutapi.resources

import kotlinx.serialization.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.data.*


/**
 * Represents the stages of resource generation.
 * These stages determine when a resource generator is executed during the resource lifecycle.
 */
public enum class ResourceGenerationStage {
    /**
     * Executed before resource processors are run.
     */
    BeforeProcessors,

    /**
     * Executed after resource processors are run.
     */
    AfterProcessors,

    /**
     * Executed before resource pack processors are run.
     */
    BeforePackProcessors,

    /**
     * Executed after resource pack processors are run.
     */
    AfterPackProcessors
}

/**
 * Abstract base class for resource generators.
 * Resource generators are responsible for generating resources during specific stages
 * of the resource lifecycle.
 *
 * @property id The unique identifier for the resource generator.
 * @property stage The stage at which the generator is executed.
 */
public abstract class ResourceGenerator(
    override val id: Identifier,
    public val stage: ResourceGenerationStage = ResourceGenerationStage.BeforeProcessors
) : Identifiable {

    /**
     * Generates or modifies resources during the specified stage.
     *
     * **Note:** [context.resource] and resources generated through [ResourceGeneratorContext.register]
     * must not have the same [ResourceRef]. Use `subId` to differentiate between them using [ResourceRef.subId].
     *
     * @param context The context for the resource generation, containing the resource and helper methods.
     */
    public abstract fun generate(context: ResourceGeneratorContext<Resource>)

    /**
     * Companion object for managing and retrieving registered resource generators.
     */
    public companion object : IdentifierRegistry<ResourceGenerator>("Resource Generators") {

        /**
         * Retrieves all resource generators for a specific stage.
         *
         * @param stage The stage to filter resource generators by.
         * @return A list of resource generators for the specified stage.
         */
        public fun getByStage(stage: ResourceGenerationStage): List<ResourceGenerator> =
            getAllValues().filter { it.stage == stage }
    }
}

/**
 * Context provided to resource generators during execution.
 *
 * @param T The type of resource being generated or modified.
 * @property resource The resource being processed by the generator.
 * @property generateBlock The block of metadata associated with the resource.
 * @property ref The reference to the resource being processed.
 * @property register A function to register newly generated resources.
 */
public data class ResourceGeneratorContext<out T : Resource>(
    val resource: T,
    val generateBlock: GenerateBlock,
    val ref: ResourceRef<T>,
    val register: (Resource) -> Unit
) {

    /**
     * Deserializes the options for this generator into a new object.
     *
     * @param S The type to deserialize into.
     * @param serializer The serializer for the type [S].
     * @return The deserialized options object.
     */
    public fun <S> castOptions(serializer: KSerializer<S>): S {
        return CuTAPI.toml.decodeFromTomlElement(serializer, generateBlock.options)
    }
}

/**
 * Creates a resource generator that is executed for all resources.
 *
 * @param id The unique identifier for the generator.
 * @param priority The stage at which the generator is executed.
 * @param block The logic to execute for each resource.
 * @return A [ResourceGenerator] instance.
 */
public fun resourceGenerator(
    id: Identifier,
    priority: ResourceGenerationStage = ResourceGenerationStage.BeforeProcessors,
    block: ResourceGeneratorContext<Resource>.() -> Resource?
): ResourceGenerator {
    return object : ResourceGenerator(id, priority) {
        override fun generate(context: ResourceGeneratorContext<Resource>) {
            block(context)
        }
    }
}

/**
 * Creates a resource generator that is executed only for resources of a specific type.
 *
 * @param T The type of resource to process.
 * @param id The unique identifier for the generator.
 * @param priority The stage at which the generator is executed.
 * @param block The logic to execute for each resource of type [T].
 * @return A [ResourceGenerator] instance.
 */
@JvmName("resourceGeneratorWithType")
public inline fun <reified T : Resource> resourceGenerator(
    id: Identifier,
    priority: ResourceGenerationStage = ResourceGenerationStage.BeforeProcessors,
    crossinline block: ResourceGeneratorContext<T>.() -> Unit
): ResourceGenerator {
    return object : ResourceGenerator(id, priority) {
        override fun generate(context: ResourceGeneratorContext<Resource>) {
            if (context.resource is T) {
                // Cast the context to the specific type and execute the block.
                @Suppress("UNCHECKED_CAST")
                block(context as ResourceGeneratorContext<T>)
            }
        }
    }
}
