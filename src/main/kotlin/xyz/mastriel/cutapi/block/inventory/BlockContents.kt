package xyz.mastriel.cutapi.block.inventory

import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.attachment.BlockAttachment
import xyz.mastriel.cutapi.attachment.ItemAttachment
import xyz.mastriel.cutapi.data.Schema
import xyz.mastriel.cutapi.data.VariantSerializer
import xyz.mastriel.cutapi.data.schema
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import java.util.Base64

public class BlockContents(
    public val formatVersion: Int,
    public val inventoryId: Identifier,
    public val size: Int,
    entries: Map<Int, ItemStack>,
) : BlockAttachment, ItemAttachment {
    private val storedEntries: Map<Int, ItemStack> = entries.mapValues { (_, stack) -> stack.clone() }

    init {
        require(formatVersion == CurrentFormatVersion) { "Unsupported BlockContents format version $formatVersion." }
        require(size in 1..54) { "BlockContents size must be between 1 and 54, found $size." }
        require(storedEntries.keys.all { it in 0 until size }) { "BlockContents contains an out-of-bounds slot." }
        require(storedEntries.values.all { !it.type.isAir && it.amount > 0 }) {
            "BlockContents sparse entries must all be nonempty."
        }
        require(storedEntries.values.all { it.amount <= it.maxStackSize }) {
            "BlockContents contains a stack above its maximum size."
        }
    }

    public val entries: Map<Int, ItemStack>
        get() = storedEntries.mapValues { (_, stack) -> stack.clone() }

    private val encodedEntries: Map<String, String>
        get() = storedEntries.mapKeys { (slot, _) -> slot.toString() }
            .mapValues { (_, stack) -> Base64.getEncoder().encodeToString(stack.serializeAsBytes()) }

    public companion object : Schema<BlockContents> by schema(id("cutapi:block_contents"), {
        val formatVersion = property(BlockContents::formatVersion, VariantSerializer.Int, name = "format_version")
        val inventoryId = property(BlockContents::inventoryId, VariantSerializer.Id, name = "inventory_id")
        val size = property(BlockContents::size, VariantSerializer.Int)
        val entries = property(
            name = "entries",
            serializer = VariantSerializer.MapOf(VariantSerializer.String),
            getProperty = BlockContents::encodedEntries,
            constructorParameterName = null,
        )
        constructs {
            val decodedEntries = value(entries).map { (rawSlot, encoded) ->
                val slot = rawSlot.toIntOrNull() ?: error("BlockContents entry key '$rawSlot' is not a slot index.")
                val bytes = Base64.getDecoder().decode(encoded)
                slot to ItemStack.deserializeBytes(bytes)
            }
            require(decodedEntries.map { it.first }.distinct().size == decodedEntries.size) {
                "BlockContents contains duplicate normalized slot entries."
            }
            val decoded = decodedEntries.toMap()
            BlockContents(value(formatVersion), value(inventoryId), value(size), decoded)
        }
    }) {
        public const val CurrentFormatVersion: Int = 1

        public fun of(inventoryId: Identifier, size: Int, entries: Map<Int, ItemStack>): BlockContents =
            BlockContents(CurrentFormatVersion, inventoryId, size, entries)
    }
}
