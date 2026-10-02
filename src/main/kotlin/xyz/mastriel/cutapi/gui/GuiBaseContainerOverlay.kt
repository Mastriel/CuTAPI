package xyz.mastriel.cutapi.gui

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentIteratorType
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor

/** Shared title decoration for every vanilla generic 9-column screen, including non-CuTAPI inventories. */
internal object GuiBaseContainerOverlay {
    val fontKey: Key = Key.key("cutapi", "gui/base_container")
    const val OriginGlyph: String = "\uE010"
    const val ReturnGlyph: String = "\uE011"
    const val SuppressGlyph: String = "\uE012"
    const val Width: Int = 176
    const val TextureSize: Int = 256

    // Bitmap glyphs advance one pixel beyond the rightmost opaque column.
    val advances: Map<String, Int> = mapOf(
        OriginGlyph to -8,
        ReturnGlyph to -(Width + 1 - 8),
        SuppressGlyph to 0,
    )

    fun glyph(rows: Int): String {
        require(rows in 1..6) { "Base container rows must be between 1 and 6." }
        return Character.toString(0xE000 + rows - 1)
    }

    fun suppress(): Component = Component.text(SuppressGlyph).font(fontKey)

    fun compose(rows: Int, title: Component): Component {
        val glyph = glyph(rows)
        // The marker travels with the title, so packet handling never reads mutable GUI sessions.
        // Recognizing an existing base also keeps title resends from stacking backgrounds.
        if (title.iterable(ComponentIteratorType.DEPTH_FIRST).any { component ->
                component is TextComponent && component.font() == fontKey &&
                    component.content().any { it == SuppressGlyph.single() || it in '\uE000'..'\uE005' }
            }) return title
        return Component.empty()
            .append(Component.text(OriginGlyph + glyph + ReturnGlyph).font(fontKey).color(NamedTextColor.WHITE))
            .append(title)
    }
}
