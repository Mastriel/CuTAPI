package xyz.mastriel.cutapi.utils

import net.kyori.adventure.text.format.*
import kotlin.math.*

public open class Color protected constructor(
    public val red: UByte,
    public val green: UByte,
    public val blue: UByte,
) {

    override fun toString(): String {
        return "#" + red.stringMinSize() + green.stringMinSize() + blue.stringMinSize()
    }

    public open fun toInt(): Int =
        (red.stringMinSize() + green.stringMinSize() + blue.stringMinSize()).toInt(16)


    protected fun UByte.stringMinSize(): String {
        var value = this.toString(16)
        if (value.length == 1) value = "0$value"
        return value
    }

    public val textColor: TextColor get() = TextColor.color(rgb.first, rgb.second, rgb.third)

    public val rgb: Triple<Int, Int, Int>
        get() =
            Triple(
                red.toInt(),
                green.toInt(),
                blue.toInt()
            )

    override fun equals(other: Any?): Boolean {
        if (other !is Color) return false
        return other.red == this.red &&
            other.green == this.green &&
            other.blue == this.blue
    }

    public open operator fun times(double: Double): Color {
        val r = max((red.toInt() * double).toInt(), 255).toUByte()
        val g = max((green.toInt() * double).toInt(), 255).toUByte()
        val b = max((blue.toInt() * double).toInt(), 255).toUByte()
        return Color(r, g, b)
    }

    override fun hashCode(): Int {
        var result = red.hashCode()
        result = 31 * result + green.hashCode()
        result = 31 * result + blue.hashCode()
        return result
    }

    public companion object {
        public fun of(hex: Int): Color {
            if (hex !in 0x000000..0xffffff) error("Invalid color outside of range.")
            val str = java.lang.String.format("%06X", 0xFFFFFF and hex)
            return formatHexString(str)
        }

        public fun of(hex: String): Color {
            val regex = Regex("#[a-f0-9]{6}", setOf(RegexOption.IGNORE_CASE))
            if (hex.matches(regex)) {
                return formatHexString(hex.substring(1..6))
            } else {
                error("Invalid color.")
            }
        }

        private fun formatHexString(str: String): Color {
            val r = str.substring(0..1).toUByte(16)
            val g = str.substring(2..3).toUByte(16)
            val b = str.substring(4..5).toUByte(16)
            return Color(r, g, b)
        }

        public fun of(r: UByte, g: UByte, b: UByte): Color {
            return Color(r, g, b)
        }

        // COLOR CONSTANTS

        public val Red: Color = of(0xffa39e)
        public val Orange: Color = of(0xffd09e)
        public val Yellow: Color = of(0xfff99e)
        public val Green: Color = of(0xd0ff9e)
        public val Teal: Color = of(0x9effc2)
        public val Blue: Color = of(0x5555ff)
        public val Purple: Color = of(0xe29eff)
        public val Blurple: Color = of(0x7289da)

        public val Tridium: Color = of(0x5ef2b2)
        public val Solarium: Color = of(0xf0ed54)
        public val Lunarium: Color = of(0x5983c9)
        public val Elethium: Color = of(0xbafffe)

    }

}