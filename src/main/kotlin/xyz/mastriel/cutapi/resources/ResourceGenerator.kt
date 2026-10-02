package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.data.*

public enum class ResourceGenerationStage {
    BeforeProcessors,
    AfterProcessors,
    BeforePackProcessors,
    AfterPackProcessors
}

/** A registered generator whose registry ID is its typed option-schema ID. */
public abstract class ResourceGenerator<O : Any>(
    public val optionsSchema: Schema<O>,
    public val stage: ResourceGenerationStage = ResourceGenerationStage.BeforeProcessors
) : Identifiable {
    final override val id: Identifier get() = optionsSchema.id

    internal abstract fun generateUntyped(
        resource: Resource,
        generateBlock: GenerateBlock<*>,
        ref: ResourceRef<*>,
        register: (Resource) -> Unit
    )

    internal fun decodeBlock(root: Variant.Map, subId: String?): GenerateBlock<O> {
        val normalized = root.normalize(optionsSchema.descriptor)
        val options = optionsSchema.deserialize(normalized).getOrThrow()
        return GenerateBlock(this, subId, options, root.without(SCHEMA_TYPE_DISCRIMINATOR))
    }

    public companion object :
        IdentifierRegistry<ResourceGenerator<*>>(id("cutapi:registry/resource_generator")) {
        override fun initialize() {
            super.initialize()
            getAllValues().forEach { it.optionsSchema.requireRegistered() }
        }

        public fun getByStage(stage: ResourceGenerationStage): List<ResourceGenerator<*>> =
            getAllValues().filter { it.stage == stage }
    }
}

public data class ResourceGeneratorContext<out T : Resource, out O : Any>(
    public val resource: T,
    public val generateBlock: GenerateBlock<@UnsafeVariance O>,
    public val ref: ResourceRef<T>,
    public val register: (Resource) -> Unit
) {
    public val options: O get() = generateBlock.options
}

@JvmName("resourceGeneratorWithType")
public inline fun <reified T : Resource, O : Any> resourceGenerator(
    optionsSchema: Schema<O>,
    stage: ResourceGenerationStage = ResourceGenerationStage.BeforeProcessors,
    crossinline block: ResourceGeneratorContext<T, O>.() -> Unit
): ResourceGenerator<O> = object : ResourceGenerator<O>(optionsSchema, stage) {
    override fun generateUntyped(
        resource: Resource,
        generateBlock: GenerateBlock<*>,
        ref: ResourceRef<*>,
        register: (Resource) -> Unit
    ) {
        if (resource !is T) return
        require(generateBlock.generator === this) {
            "Generator block ${generateBlock.generatorId} cannot be handled by $id."
        }
        @Suppress("UNCHECKED_CAST")
        block(
            ResourceGeneratorContext(
                resource,
                generateBlock as GenerateBlock<O>,
                ref.cast(),
                register
            )
        )
    }
}
