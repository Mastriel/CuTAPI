package xyz.mastriel.cutapi.resources.generator

public class PackVersion46Generator : ResourcePackGenerator() {
    override val packVersion: Int = 46
    override val generationSteps: Int = 2

    override suspend fun generate() {
        generationStep("Creating Default Pack", 1) { createSkeleton() }
        generationStep("Running Pack Processors", 2) { runResourceProcessorsPack() }
    }
}