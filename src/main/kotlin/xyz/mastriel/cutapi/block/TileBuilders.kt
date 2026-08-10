@file:Suppress("DEPRECATION")

package xyz.mastriel.cutapi.block

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*

/** Creates a fully configured block before it is handed to the identifier registry. */
public fun customBlock(
    id: Identifier,
    configure: BlockDescriptorBuilder.() -> Unit = {},
): CustomBlock<CuTPlacedBlock> =
    CustomBlock(id, BlockDescriptorBuilder().apply(configure).build(), CuTPlacedBlock::class)

public fun customBlockFromDescriptor(
    id: Identifier,
    descriptor: () -> BlockDescriptor = ::defaultBlockDescriptor,
): CustomBlock<CuTPlacedBlock> = CustomBlock(id, descriptor(), CuTPlacedBlock::class)

@JvmName("customBlockWithPlacedType")
public inline fun <reified T : CuTPlacedBlock> customBlock(
    id: Identifier,
    noinline configure: BlockDescriptorBuilder.() -> Unit = {},
): CustomBlock<T> = CustomBlock(id, BlockDescriptorBuilder().apply(configure).build(), T::class)

@JvmName("customBlockFromDescriptorWithPlacedType")
public inline fun <reified T : CuTPlacedBlock> customBlockFromDescriptor(
    id: Identifier,
    noinline descriptor: () -> BlockDescriptor = ::defaultBlockDescriptor,
): CustomBlock<T> = CustomBlock(id, descriptor(), T::class)

public fun <T : CuTPlacedBlock> typedCustomBlock(
    id: Identifier,
    placedBlockClass: KClass<T>,
    configure: BlockDescriptorBuilder.() -> Unit = {},
): CustomBlock<T> = CustomBlock(id, BlockDescriptorBuilder().apply(configure).build(), placedBlockClass)

public fun <T : CuTPlacedBlock> typedCustomBlockFromDescriptor(
    id: Identifier,
    placedBlockClass: KClass<T>,
    descriptor: () -> BlockDescriptor = ::defaultBlockDescriptor,
): CustomBlock<T> = CustomBlock(id, descriptor(), placedBlockClass)

@JvmName("registerCustomBlockWithPlacedType")
public inline fun <reified T : CuTPlacedBlock> DeferredRegistry<CustomBlock<*>>.registerCustomBlock(
    id: Identifier,
    noinline configure: BlockDescriptorBuilder.() -> Unit = {},
): Deferred<CustomBlock<T>> {
    val producer: () -> CustomBlock<T> = { customBlock<T>(id, configure) }
    return register(producer).also { associateId(producer, id) }
}

@JvmName("registerCustomBlockFromDescriptorWithPlacedType")
public inline fun <reified T : CuTPlacedBlock> DeferredRegistry<CustomBlock<*>>.registerCustomBlockFromDescriptor(
    id: Identifier,
    noinline descriptor: () -> BlockDescriptor = ::defaultBlockDescriptor,
): Deferred<CustomBlock<T>> {
    val producer: () -> CustomBlock<T> = { customBlockFromDescriptor<T>(id, descriptor) }
    return register(producer).also { associateId(producer, id) }
}

public fun DeferredRegistry<CustomBlock<*>>.registerCustomBlock(
    id: Identifier,
    configure: BlockDescriptorBuilder.() -> Unit = {},
): Deferred<CustomBlock<CuTPlacedBlock>> {
    val producer: () -> CustomBlock<CuTPlacedBlock> = { customBlock(id, configure) }
    return register(producer).also { associateId(producer, id) }
}

public fun DeferredRegistry<CustomBlock<*>>.registerCustomBlockFromDescriptor(
    id: Identifier,
    descriptor: () -> BlockDescriptor = ::defaultBlockDescriptor,
): Deferred<CustomBlock<CuTPlacedBlock>> {
    val producer: () -> CustomBlock<CuTPlacedBlock> = { customBlockFromDescriptor(id, descriptor) }
    return register(producer).also { associateId(producer, id) }
}


public fun customTileEntity(
    id: Identifier,
    configure: TileEntityDescriptorBuilder.() -> Unit = {},
): CustomTileEntity<CuTPlacedTileEntity> =
    CustomTileEntity(id, TileEntityDescriptorBuilder().apply(configure).build(), CuTPlacedTileEntity::class)

public fun customTileEntityFromDescriptor(
    id: Identifier,
    descriptor: () -> TileEntityDescriptor = ::defaultTileEntityDescriptor,
): CustomTileEntity<CuTPlacedTileEntity> = CustomTileEntity(id, descriptor(), CuTPlacedTileEntity::class)

@JvmName("customTileEntityWithPlacedType")
public inline fun <reified T : CuTPlacedTileEntity> customTileEntity(
    id: Identifier,
    noinline configure: TileEntityDescriptorBuilder.() -> Unit = {},
): CustomTileEntity<T> = CustomTileEntity(id, TileEntityDescriptorBuilder().apply(configure).build(), T::class)

@JvmName("customTileEntityFromDescriptorWithPlacedType")
public inline fun <reified T : CuTPlacedTileEntity> customTileEntityFromDescriptor(
    id: Identifier,
    noinline descriptor: () -> TileEntityDescriptor = ::defaultTileEntityDescriptor,
): CustomTileEntity<T> = CustomTileEntity(id, descriptor(), T::class)

public fun <T : CuTPlacedTileEntity> typedCustomTileEntity(
    id: Identifier,
    placedBlockClass: KClass<T>,
    configure: TileEntityDescriptorBuilder.() -> Unit = {},
): CustomTileEntity<T> =
    CustomTileEntity(id, TileEntityDescriptorBuilder().apply(configure).build(), placedBlockClass)

public fun <T : CuTPlacedTileEntity> typedCustomTileEntityFromDescriptor(
    id: Identifier,
    placedBlockClass: KClass<T>,
    descriptor: () -> TileEntityDescriptor = ::defaultTileEntityDescriptor,
): CustomTileEntity<T> = CustomTileEntity(id, descriptor(), placedBlockClass)

@JvmName("registerCustomTileEntityWithPlacedType")
public inline fun <reified T : CuTPlacedTileEntity> DeferredRegistry<CustomTileEntity<*>>.registerCustomTileEntity(
    id: Identifier,
    noinline configure: TileEntityDescriptorBuilder.() -> Unit = {},
): Deferred<CustomTileEntity<T>> {
    val producer: () -> CustomTileEntity<T> = { customTileEntity<T>(id, configure) }
    return register(producer).also { associateId(producer, id) }
}

@JvmName("registerCustomTileEntityFromDescriptorWithPlacedType")
public inline fun <reified T : CuTPlacedTileEntity> DeferredRegistry<CustomTileEntity<*>>.registerCustomTileEntityFromDescriptor(
    id: Identifier,
    noinline descriptor: () -> TileEntityDescriptor = ::defaultTileEntityDescriptor,
): Deferred<CustomTileEntity<T>> {
    val producer: () -> CustomTileEntity<T> = { customTileEntityFromDescriptor<T>(id, descriptor) }
    return register(producer).also { associateId(producer, id) }
}

public fun DeferredRegistry<CustomTileEntity<*>>.registerCustomTileEntity(
    id: Identifier,
    configure: TileEntityDescriptorBuilder.() -> Unit = {},
): Deferred<CustomTileEntity<CuTPlacedTileEntity>> {
    val producer: () -> CustomTileEntity<CuTPlacedTileEntity> = { customTileEntity(id, configure) }
    return register(producer).also { associateId(producer, id) }
}

public fun DeferredRegistry<CustomTileEntity<*>>.registerCustomTileEntityFromDescriptor(
    id: Identifier,
    descriptor: () -> TileEntityDescriptor = ::defaultTileEntityDescriptor,
): Deferred<CustomTileEntity<CuTPlacedTileEntity>> {
    val producer: () -> CustomTileEntity<CuTPlacedTileEntity> = { customTileEntityFromDescriptor(id, descriptor) }
    return register(producer).also { associateId(producer, id) }
}
