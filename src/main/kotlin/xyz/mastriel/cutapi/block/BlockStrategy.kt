@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.block

import org.bukkit.Material

/** Source bridge for the pre-native visual API. New definitions should use [BlockVisualMethod]. */
@Deprecated("Use BlockVisualMethod.", ReplaceWith("BlockVisualMethod"))
public sealed class BlockStrategy {
    public data object NoteBlock : BlockStrategy()
    public data object Mushroom : BlockStrategy()
    public data class Vanilla(val material: Material) : BlockStrategy()
    public data object FakeEntity : BlockStrategy()

    public fun toVisualMethod(): BlockVisualMethod = when (this) {
        NoteBlock -> BlockVisualMethod.NoteBlock
        Mushroom -> BlockVisualMethod.Mushroom
        is Vanilla -> BlockVisualMethod.Vanilla(blockVisualData(material))
        FakeEntity -> BlockVisualMethod.DisplayEntity(blockVisualData(Material.BARRIER))
    }
}
