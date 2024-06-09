package xyz.mastriel.cutapi.resources.builtin

internal var customModelDataCounter = 32120

public fun allocateCustomModelData(): Int {
    customModelDataCounter += 1
    return customModelDataCounter - 1
}

public interface CustomModelDataAllocated {
    public val customModelData: Int
}
