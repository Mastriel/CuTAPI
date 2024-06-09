package xyz.mastriel.cutapi.utils

public class TransparentColor private constructor(
    r: UByte,
    g: UByte,
    b: UByte,
    public val alpha: UByte
) : Color(r, g, b) {

    override fun toInt(): Int {
        return alpha.toInt() shl 24 or (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
    }

    public fun toIntARGB(): Int = toInt()
    public fun toIntRGBA(): Int = red.toInt() shl 24 or (green.toInt() shl 16) or (blue.toInt() shl 8) or alpha.toInt()

    override fun times(double: Double): TransparentColor {
        val color = super.times(double)
        return TransparentColor(color.red, color.green, color.blue, alpha)
    }

    override fun hashCode(): Int {
        var result = red.hashCode()
        result = 31 * result + green.hashCode()
        result = 31 * result + blue.hashCode()
        result = 31 * result + alpha.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as TransparentColor

        if (alpha != other.alpha) return false

        return true
    }

    override fun toString(): String {
        return super.toString() + alpha.stringMinSize()
    }


    @Suppress("DuplicatedCode")
    public companion object {

        public fun ofARGB(hex: Int): TransparentColor {
            val alpha = 0xFF and (hex shr 24)
            val red = 0xFF and (hex shr 16)
            val green = 0xFF and (hex shr 8)
            val blue = 0xFF and (hex shr 0)
            return of(red, green, blue, alpha)
        }

        public fun ofRGBA(hex: Int): TransparentColor {
            val red = 0xFF and (hex shr 24)
            val green = 0xFF and (hex shr 16)
            val blue = 0xFF and (hex shr 8)
            val alpha = 0xFF and (hex shr 0)
            return of(red, green, blue, alpha)
        }

        public fun ofRGBA(hex: UInt): TransparentColor {
            val red = 0xFFu and (hex shr 24)
            val green = 0xFFu and (hex shr 16)
            val blue = 0xFFu and (hex shr 8)
            val alpha = 0xFFu and (hex shr 0)
            return of(red.toInt(), green.toInt(), blue.toInt(), alpha.toInt())
        }

        public fun ofRGBA(hex: String): TransparentColor {
            val regex = Regex("#[a-f0-9]{8}", setOf(RegexOption.IGNORE_CASE))
            if (hex.matches(regex)) {
                return formatHexStringRGBA(hex.substring(1..8))
            } else {
                throw IllegalArgumentException("Invalid color.")
            }
        }

        private fun formatHexStringRGBA(str: String): TransparentColor {
            val r = str.substring(0..1).toUByte(16)
            val g = str.substring(2..3).toUByte(16)
            val b = str.substring(4..5).toUByte(16)
            val a = str.substring(6..7).toUByte(16)
            return TransparentColor(r, g, b, a)
        }

        public fun ofARGB(hex: String): TransparentColor {
            val regex = Regex("#[a-f0-9]{8}", setOf(RegexOption.IGNORE_CASE))
            if (hex.matches(regex)) {
                return formatHexStringARGB(hex.substring(1..8))
            } else {
                throw IllegalArgumentException("Invalid color.")
            }
        }

        private fun formatHexStringARGB(str: String): TransparentColor {
            val rgba = formatHexStringRGBA(str)
            return TransparentColor(rgba.alpha, rgba.red, rgba.green, rgba.blue)
        }

        public fun of(r: UByte, g: UByte, b: UByte, a: UByte): TransparentColor {
            return TransparentColor(r, g, b, a)
        }

        public fun of(r: Int, g: Int, b: Int, a: Int): TransparentColor {
            return TransparentColor(r.toUByte(), g.toUByte(), b.toUByte(), a.toUByte())
        }
    }
}