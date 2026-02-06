package xyz.mastriel.cutapi.pdc.tags.converters

public object BooleanTagConverter :
    TagConverter<Byte, Boolean>(Byte::class, Boolean::class) {

    override fun fromPrimitive(primitive: Byte): Boolean {
        return primitive != 0.toByte()
    }

    override fun toPrimitive(complex: Boolean): Byte {
        return if (complex) 1.toByte() else 0.toByte()
    }
}

