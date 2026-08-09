package xyz.mastriel.cutapi.resources.generator

import xyz.mastriel.cutapi.block.nativeblock.NativeBlockResourcePackGenerator

/**
 * Resource pack generator for Minecraft pack format version 46.
 *
 * Implements the generation steps for version 46 resource packs.
 */
public class PackVersion46Generator : ResourcePackGenerator() {
    override val packVersion: Int = 46
    override val generationSteps: Int = 3

    override suspend fun generate() {
        generationStep("Creating Default Pack", 1) { createSkeleton() }
        generationStep("Running Pack Processors", 2) { runResourceProcessorsPack() }
        generationStep("Generating Native Block Visuals", 3) { NativeBlockResourcePackGenerator.generate(tempPackFolder) }
    }
}
