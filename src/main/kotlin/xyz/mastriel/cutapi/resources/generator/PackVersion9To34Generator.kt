package xyz.mastriel.cutapi.resources.generator

public class PackVersion9To34Generator : ResourcePackGenerator() {
    override val packVersion: Int = 34
    override val generationSteps: Int = 2

    override suspend fun generate() {
        generationStep("Creating Default Pack", 1) { createSkeleton() }
        generationStep("Running Pack Processors", 2) { runResourceProcessorsPack() }
    }


}