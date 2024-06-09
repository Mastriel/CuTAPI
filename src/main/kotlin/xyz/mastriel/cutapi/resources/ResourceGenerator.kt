package xyz.mastriel.cutapi.resources

import kotlinx.serialization.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.data.*


public enum class ResourceGenerationStage {
    BeforeProcessors,
    AfterProcessors,
    BeforePackProcessors,
    AfterPackProcessors
}

/**
 * This is called after the server has finished initialization, but before CuTAPI has run the ResourceProcessors.
 * This should be used to
 */
public abstract class ResourceGenerator(
    override val id: Identifier,
    public val stage: ResourceGenerationStage = ResourceGenerationStage.BeforeProcessors
) : Identifiable {

    /**
     * [context.resource] and resources generated through [ResourceGeneratorContext.register] can NOT have the same [ResourceRef]!
     * Use subId to differentiate between them using [ResourceRef.subId]
     */
    public abstract fun generate(context: ResourceGeneratorContext<Resource>)

    public companion object : IdentifierRegistry<ResourceGenerator>("Resource Generators") {
        public fun getByStage(stage: ResourceGenerationStage): List<ResourceGenerator> =
            getAllValues().filter { it.stage == stage }
    }
}

public data class ResourceGeneratorContext<out T : Resource>(
    val resource: T,
    val generateBlock: GenerateBlock,
    val suppliedSubId: String,
    val register: (Resource) -> Unit
) {

    /**
     * Deserialize the options for this generator into a new object.
     *
     * @param S The type you're deserializing into.
     * @param serializer The serializer for [S].
     */
    public fun <S> castOptions(serializer: KSerializer<S>): S {
        return CuTAPI.toml.decodeFromTomlElement(serializer, generateBlock.options)
    }
}

/**
 * Ran for ALL resources that exist.
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
 * Ran only for resources of a certain type that has [id] in its `generate` metadata section.
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
                // very nasty
                @Suppress("UNCHECKED_CAST")
                block(context as ResourceGeneratorContext<T>)
            }
        }
    }
}
