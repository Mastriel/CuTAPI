package xyz.mastriel.brazil.utils

import java.text.DecimalFormat


fun ticksToSeconds(ticks: Long) : String {
    val decimalFormat = DecimalFormat("#.##")
    return decimalFormat.format(ticks/20)+"s"
}