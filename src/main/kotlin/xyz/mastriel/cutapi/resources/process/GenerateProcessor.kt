package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.*


// processes all resources and generates new resources based on the generate blocks
internal fun generateResources(resources: List<Resource>, stage: ResourceGenerationStage) {
    for (resource in resources) {
        val generators = resource.metadata?.generateBlock ?: continue
        for (generateBlock in generators) {
            try {

                val generator = ResourceGenerator.getOrNull(generateBlock.generatorId)
                    ?: error("'${generateBlock.generatorId}' is not a valid Resource Generator for ${resource::class.simpleName}.")
                if (generator.stage != stage) return
                val subId = generateBlock.subId ?: error("No subId supplied.")

                val newResources = mutableListOf<Resource>()
                fun register(resource: Resource) {
                    CuTAPI.resourceManager.register(resource)
                    newResources.add(resource)
                }

                val ctx = ResourceGeneratorContext(resource, generateBlock, subId, ::register)

                generator.generate(ctx)

                for (newResource in newResources) {
                    generateResources(newResources, stage)
                }

            } catch (ex: Exception) {
                val subid = resource.ref.generatedSubId(generateBlock.subId ?: "<no subid>")
                Plugin.error("Failed to generate resource '$subid' from '${resource.ref}'")
                ex.printStackTrace()
            }
        }
    }
}

public fun <T : Resource> ResourceRef<T>.generatedSubId(string: String): ResourceRef<T> {
    return ref(root, "${this.path()}+$string.${this.extension}")
}