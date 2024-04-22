package xyz.mastriel.cutapi.utils

import net.kyori.adventure.text.format.TextColor
import kotlin.math.max

open class Color protected constructor(
    val red : UByte,
    val green : UByte,
    val blue : UByte,
) {

    override fun toString(): String {
        return "#" + red.stringMinSize() + green.stringMinSize() + blue.stringMinSize()
    }

    open fun toInt(): Int =
        (red.stringMinSize() + green.stringMinSize() + blue.stringMinSize()).toInt(16)


    protected fun UByte.stringMinSize() : String {
        var value = this.toString(16)
        if (value.length == 1) value = "0$value"
        return value
    }

    val textColor get() = TextColor.color(rgb.first, rgb.second, rgb.third)

    val rgb : Triple<Int, Int, Int> get() =
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

    open operator fun times(double: Double) : Color {
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

    companion object {
        fun of(hex: Int) : Color {
            if (hex !in 0x000000..0xffffff) error("Invalid color outside of range.")
            val str = java.lang.String.format("%06X", 0xFFFFFF and hex)
            return formatHexString(str)
        }

        fun of(hex: String) : Color {
            val regex = Regex("#[a-f0-9]{6}", setOf(RegexOption.IGNORE_CASE))
            if (hex.matches(regex)) {
                return formatHexString(hex.substring(1..6))
            } else {
                error("Invalid color.")
            }
        }
        private fun formatHexString(str: String) : Color {
            val r = str.substring(0..1).toUByte(16)
            val g = str.substring(2..3).toUByte(16)
            val b = str.substring(4..5).toUByte(16)
            return Color(r, g, b)
        }

        fun of(r: UByte, g: UByte, b: UByte) : Color {
            return Color(r, g, b)
        }

        // COLOR CONSTANTS

        val Red     = of(0xffa39e)
        val Orange  = of(0xffd09e)
        val Yellow  = of(0xfff99e)
        val Green   = of(0xd0ff9e)
        val Teal    = of(0x9effc2)
        val Blue    = of(0x5555ff)
        val Purple  = of(0xe29eff)
        val Blurple = of(0x7289da)

        val Tridium  = of(0x5ef2b2)
        val Solarium = of(0xf0ed54)
        val Lunarium = of(0x5983c9)
        val Elethium = of(0xbafffe)

    }

}