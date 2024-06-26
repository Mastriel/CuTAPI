package xyz.mastriel.cutapi.resources.generator

public class PackVersion9To18Generator : ResourcePackGenerator() {
    override val packVersion: Int = 18
    override val generationSteps: Int = 2

    override suspend fun generate() {
        generationStep("Creating Default Pack", 1) { createSkeleton() }
        generationStep("Running Pack Processors", 2) { runResourceProcessorsPack() }
    }


}