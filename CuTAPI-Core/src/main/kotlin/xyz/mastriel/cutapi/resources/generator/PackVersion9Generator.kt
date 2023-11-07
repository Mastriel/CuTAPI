package xyz.mastriel.cutapi.resources.generator

class PackVersion9Generator : ResourcePackGenerator() {
    override val packVersion: Int = 9
    override val generationSteps: Int = 3

    override suspend fun generate() {
        generationStep("Creating Default Pack", 1) { createSkeleton() }
        generationStep("Running Pack Processors", 1) { createSkeleton() }
    }


}