package xyz.mastriel.cutapi.resources.builtin

internal var customModelDataCounter = 32120

fun allocateCustomModelData(): Int {
    customModelDataCounter += 1
    return customModelDataCounter - 1
}

interface CustomModelDataAllocated {
    val customModelData: Int
}
