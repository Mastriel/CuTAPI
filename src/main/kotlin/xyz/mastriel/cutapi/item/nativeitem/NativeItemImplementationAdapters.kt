package xyz.mastriel.cutapi.item.nativeitem

import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.FishingRodItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ProjectileItem

/** The concrete native item and vanilla item whose behavior it preserves. */
internal data class NativeItemImplementation(
    val item: Item,
    val backing: Item,
) {
    init {
        check(item is NativeBackedItem && item.backing === backing) {
            "Native item implementation ${item.javaClass.name} does not retain its backing item."
        }
    }
}

/**
 * Selects concrete native item implementations for backing types that require their vanilla
 * subtype. To support another subtype, add its wrapper and one [subtypeAdapter] entry below. Keep
 * more-specific types before their parents.
 */
internal object NativeItemImplementationAdapters {
    private val subtypeAdapters: List<NativeItemSubtypeAdapter> = listOf(
        subtypeAdapter<BowItem>(::NativeBowCustomItem),
        subtypeAdapter<CrossbowItem>(::NativeCrossbowCustomItem),
        subtypeAdapter<FishingRodItem>(::NativeFishingRodCustomItem),
    )

    fun hasSubtypeAdapter(backing: Item): Boolean = subtypeAdapterFor(backing) != null

    fun create(backing: Item, key: ResourceKey<Item>): NativeItemImplementation {
        val item = subtypeAdapterFor(backing)?.create(backing, key)
            ?: if (backing is ProjectileItem) {
                NativeProjectileCustomItem(backing, key)
            } else {
                NativeCustomItem(backing, key)
            }
        return NativeItemImplementation(item, backing)
    }

    fun verify(implementation: NativeItemImplementation) {
        subtypeAdapterFor(implementation.backing)?.verify(implementation)
    }

    private fun subtypeAdapterFor(backing: Item): NativeItemSubtypeAdapter? =
        subtypeAdapters.firstOrNull { it.supports(backing) }
}

private interface NativeItemSubtypeAdapter {
    fun supports(backing: Item): Boolean

    fun create(backing: Item, key: ResourceKey<Item>): Item

    fun verify(implementation: NativeItemImplementation)
}

private class TypedNativeItemSubtypeAdapter<T : Item>(
    private val backingType: Class<T>,
    private val factory: (T, ResourceKey<Item>) -> Item,
) : NativeItemSubtypeAdapter {
    override fun supports(backing: Item): Boolean = backingType.isInstance(backing)

    override fun create(backing: Item, key: ResourceKey<Item>): Item =
        factory(backingType.cast(backing), key)

    override fun verify(implementation: NativeItemImplementation) {
        check(backingType.isInstance(implementation.item)) {
            "Native item backed by ${backingType.simpleName} used ${implementation.item.javaClass.name}; " +
                "its implementation must preserve the ${backingType.name} contract."
        }
    }
}

private inline fun <reified T : Item> subtypeAdapter(
    noinline factory: (T, ResourceKey<Item>) -> Item,
): NativeItemSubtypeAdapter = TypedNativeItemSubtypeAdapter(T::class.java, factory)
