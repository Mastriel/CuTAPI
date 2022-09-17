package xyz.mastriel.brazil.utils

import java.text.DecimalFormat

fun Double.format(template: String) : String {
    return DecimalFormat(template).format(this)
}

fun Double.format(decimalPlaces: Int) : String {
    return DecimalFormat("#."+("#".repeat(decimalPlaces))).format(this)
}