package xyz.mastriel.cutapi.utils

import kotlin.math.*

internal fun Float.roundToDecimalPlaces(n: Int): Float {
    val multiplier = 10f.pow(n.toFloat())
    return (round(this * multiplier) / multiplier)
}