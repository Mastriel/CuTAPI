package xyz.mastriel.cutapi.resources.data

import xyz.mastriel.cutapi.registry.*

/** Base class for strongly typed resource metadata. */
public open class CuTMeta {
    private var configuredGenerateBlocks: List<GenerateBlock> = emptyList()

    public val generateBlocks: List<GenerateBlock>
        get() = configuredGenerateBlocks

    internal fun setGenerateBlocks(blocks: List<GenerateBlock>) {
        configuredGenerateBlocks = blocks
    }
}

public data class GenerateBlock(
    public val generatorId: Identifier,
    public val subId: String?,
    public val options: ResourceConfigMap
)
