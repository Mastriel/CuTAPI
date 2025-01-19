package xyz.mastriel.cutapi.resources.builtin

@Deprecated("Up for removal due to changing from CustomModelData to ItemModel")
internal var customModelDataCounter = 32120

@Deprecated("Up for removal due to changing from CustomModelData to ItemModel")
public fun allocateCustomModelData(): Int {
    customModelDataCounter += 1
    return customModelDataCounter - 1
}

@Deprecated("Up for removal due to changing from CustomModelData to ItemModel")
public interface CustomModelDataAllocated {
    public val customModelData: Int?
}
