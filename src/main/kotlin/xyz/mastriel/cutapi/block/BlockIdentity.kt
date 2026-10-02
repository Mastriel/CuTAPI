package xyz.mastriel.cutapi.block

import org.bukkit.Material
import org.bukkit.block.Block
import xyz.mastriel.cutapi.attachment.AttachmentHolder
import xyz.mastriel.cutapi.attachment.BlockAttachment
import xyz.mastriel.cutapi.attachment.matching
import xyz.mastriel.cutapi.attachment.schema
import xyz.mastriel.cutapi.data.Schema
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockTypes

/** Identifies either a CuTAPI custom block definition or a vanilla Minecraft block type. */
public sealed interface BlockIdentity : AttachmentHolder<BlockAttachment> {
    public val isCustom: Boolean

    public val customTile: CustomTile<*>?
        get() = (this as? Custom)?.tile

    override fun hasAttachment(schema: Schema<out BlockAttachment>): Boolean =
        getAllAttachments().any { it.schema().id == schema.id }

    override fun <T : BlockAttachment> getAttachment(schema: Schema<T>): T =
        getAttachmentOrNull(schema) ?: error("Attachment ${schema.id} does not exist on block $this.")

    override fun <T : BlockAttachment> getAttachmentOrNull(schema: Schema<T>): T? =
        getAttachments(schema).firstOrNull()

    override fun <T : BlockAttachment> getAttachments(schema: Schema<T>): List<T> =
        getAllAttachments().matching(schema)

    override fun getAllAttachments(): List<BlockAttachment> = customTile?.getAllAttachments().orEmpty()

    @ConsistentCopyVisibility
    public data class Custom internal constructor(public val tile: CustomTile<*>) : BlockIdentity {
        override val isCustom: Boolean = true
    }

    @ConsistentCopyVisibility
    public data class Vanilla internal constructor(public val material: Material) : BlockIdentity {
        init {
            require(material.isBlock) { "$material is not a block material." }
        }

        override val isCustom: Boolean = false
    }

    public companion object {
        public fun of(tile: CustomTile<*>): Custom = Custom(tile)

        public fun of(material: Material): Vanilla = Vanilla(material)

        public fun of(block: Block): BlockIdentity =
            NativeBlockTypes.definition(block)?.let(::Custom) ?: Vanilla(block.type)
    }
}

public fun CustomTile<*>.asIdentity(): BlockIdentity.Custom = BlockIdentity.of(this)

public fun Material.asBlockIdentity(): BlockIdentity.Vanilla = BlockIdentity.of(this)

public val Block.blockIdentity: BlockIdentity
    get() = BlockIdentity.of(this)
