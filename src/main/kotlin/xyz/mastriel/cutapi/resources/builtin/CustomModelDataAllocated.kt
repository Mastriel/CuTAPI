package xyz.mastriel.cutapi.resources.builtin

/**
 * Deprecated: Use ItemModel instead of CustomModelData.
 *
 * Provides allocation and interface for custom model data values.
 */
@Deprecated("Up for removal due to changing from CustomModelData to ItemModel")
internal var customModelDataCounter = 32120

/**
 * Deprecated: Use ItemModel instead of CustomModelData.
 *
 * Allocates a new custom model data value.
 * @return The allocated custom model data integer.
 */
@Deprecated("Up for removal due to changing from CustomModelData to ItemModel")
public fun allocateCustomModelData(): Int {
    customModelDataCounter += 1
    return customModelDataCounter - 1
}

/**
 * Deprecated: Use ItemModel instead of CustomModelData.
 *
 * Interface for objects that have custom model data.
 */
@Deprecated("Up for removal due to changing from CustomModelData to ItemModel")
public interface CustomModelDataAllocated {
    public val customModelData: Int?
}
