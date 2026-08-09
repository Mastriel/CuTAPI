@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block.nativeblock

import net.minecraft.core.Holder
import net.minecraft.core.MappedRegistry
import net.minecraft.core.RegistrationInfo
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier as MinecraftIdentifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntityType
import org.bukkit.craftbukkit.util.CraftMagicNumbers
import xyz.mastriel.cutapi.block.CustomTile
import xyz.mastriel.cutapi.block.CustomTileEntity
import xyz.mastriel.cutapi.registry.Identifier
import java.util.IdentityHashMap
import java.lang.reflect.Proxy

internal object NativeBlockRegistry {
    internal var vanillaBlockStateCount: Int = -1
        private set

    private val blockRegistry: MappedRegistry<Block>
        get() = BuiltInRegistries.BLOCK as MappedRegistry<Block>
    private val blockEntityRegistry: MappedRegistry<BlockEntityType<*>>
        get() = BuiltInRegistries.BLOCK_ENTITY_TYPE as MappedRegistry<BlockEntityType<*>>

    fun installAll(definitions: Collection<CustomTile<*>>) {
        check(NativeBlockLifecycle.state == NativeBlockState.Installing)
        validate(definitions)
        val installed = installBlocks(definitions)
        installBlockEntityTypes(installed)
        installed.forEach { (definition, block) -> NativeBlockTypes.bind(definition, block) }
        verify(installed)
    }

    internal fun finalizeVanillaBlockStateCount(definitions: Collection<CustomTile<*>>) {
        if (vanillaBlockStateCount >= 0) return

        rebuildBlockStateIds(emptyList())
        val vanillaStateIds = blockRegistry.asSequence()
            .filter { block -> blockRegistry.getKey(block)?.namespace == "minecraft" }
            .flatMap { block -> block.stateDefinition.possibleStates.asSequence() }
            .map { state ->
                val id = Block.BLOCK_STATE_REGISTRY.getId(state)
                check(id >= 0) { "Vanilla state $state has no global state ID." }
                id
            }
            .toSet()
        vanillaBlockStateCount = vanillaStateIds.size
        val expectedVanillaStateIds = (0 until vanillaBlockStateCount).toSet()
        check(vanillaStateIds == expectedVanillaStateIds) {
            "Minecraft block states do not occupy the expected contiguous client ID prefix " +
                "0 until $vanillaBlockStateCount; missing=" +
                (expectedVanillaStateIds - vanillaStateIds).take(8) +
                ", outside=" + (vanillaStateIds - expectedVanillaStateIds).take(8) + "."
        }

        definitions.forEach { definition ->
            NativeBlockTypes.getNative(definition).stateDefinition.possibleStates.forEach { state ->
                val id = Block.BLOCK_STATE_REGISTRY.getId(state)
                check(id >= vanillaBlockStateCount) {
                    "Native state $state for ${definition.id} was inserted into the vanilla state-ID prefix at $id."
                }
            }
        }
    }

    private fun installBlocks(definitions: Collection<CustomTile<*>>): Map<CustomTile<*>, NativeCustomBlock> {
        check(blockRegistry.getKey(Blocks.AIR)?.namespace == "minecraft") {
            "Vanilla blocks were not initialized before native custom block registration."
        }
        val wasFrozen = isFrozen(blockRegistry)
        val stableStatePrefix = if (wasFrozen) snapshotBlockStateIds() else emptyList()
        val tags = if (wasFrozen && areTagsBound(blockRegistry)) snapshotTags(blockRegistry) else null
        if (wasFrozen) unfreeze(blockRegistry, intrusive = true)
        val installed = linkedMapOf<CustomTile<*>, NativeCustomBlock>()
        try {
            for (definition in definitions.sortedBy { it.id.toString() }) {
                val key = definition.id.blockKey()
                val schema = NativeBlockStateSchema(definition.descriptor.states)
                val block = when (definition) {
                    is CustomTileEntity<*> -> NativeCustomTileBlock.create(definition, key, schema)
                    else -> NativeCustomBlock.create(definition, key, schema)
                }
                blockRegistry.register(key, block, RegistrationInfo.BUILT_IN)
                installed[definition] = block
            }
            if (tags != null) restoreTags(blockRegistry, tags)
        } finally {
            if (wasFrozen) {
                blockRegistry.freeze()
                rebuildBlockStateIds(stableStatePrefix)
            }
        }

        return installed
    }

    /**
     * Paper's block-registry freeze listener rebuilds the global block-state mapper by adding every
     * state again. IdMapper.add advances its numeric sequence even for an existing identity, which
     * would remap all vanilla states beyond the range known by an unmodified client. Rebuild the
     * mapper from canonical block-registry order so the vanilla prefix remains byte-for-byte stable
     * and custom states are appended exactly once.
     */
    @Suppress("UNCHECKED_CAST")
    private fun rebuildBlockStateIds(stablePrefix: List<net.minecraft.world.level.block.state.BlockState>) {
        val registry = Block.BLOCK_STATE_REGISTRY
        val idToState = IdMapperFields.idToState.get(registry) as MutableList<net.minecraft.world.level.block.state.BlockState?>
        val stateToId = IdMapperFields.stateToId.get(registry) as MutableMap<net.minecraft.world.level.block.state.BlockState, Int>
        idToState.clear()
        stateToId.clear()
        IdMapperFields.nextId.setInt(registry, 0)

        blockRegistry.forEach { block ->
            block.stateDefinition.possibleStates.forEach { state ->
                registry.add(state)
                state.initCache()
            }
        }

        stablePrefix.forEachIndexed { id, state ->
            check(registry.byId(id) === state && registry.getId(state) == id) {
                "Native block registration changed vanilla block-state ID $id ($state)."
            }
        }
    }

    private fun snapshotBlockStateIds(): List<net.minecraft.world.level.block.state.BlockState> =
        (0 until Block.BLOCK_STATE_REGISTRY.size()).map { id ->
            checkNotNull(Block.BLOCK_STATE_REGISTRY.byId(id)) {
                "Global block-state registry had a hole at stable ID $id."
            }
        }

    private object IdMapperFields {
        val nextId = net.minecraft.core.IdMapper::class.java.getDeclaredField("nextId").apply { isAccessible = true }
        val stateToId = net.minecraft.core.IdMapper::class.java.getDeclaredField("tToId").apply { isAccessible = true }
        val idToState = net.minecraft.core.IdMapper::class.java.getDeclaredField("idToT").apply { isAccessible = true }
    }

    private fun installBlockEntityTypes(installed: Map<CustomTile<*>, NativeCustomBlock>) {
        val tileBlocks = installed.entries.filter { it.key is CustomTileEntity<*> }
        if (tileBlocks.isEmpty()) return
        check(blockEntityRegistry.getKey(BlockEntityType.FURNACE)?.namespace == "minecraft") {
            "Vanilla block entity types were not initialized before native custom type registration."
        }
        val wasFrozen = isFrozen(blockEntityRegistry)
        val tags = if (wasFrozen && areTagsBound(blockEntityRegistry)) snapshotTags(blockEntityRegistry) else null
        if (wasFrozen) unfreeze(blockEntityRegistry, intrusive = false)
        try {
            for ((definition, block) in tileBlocks) {
                val tile = definition as CustomTileEntity<*>
                val type = createBlockEntityType(tile, block)
                blockEntityRegistry.register(tile.id.blockEntityKey(), type, RegistrationInfo.BUILT_IN)
                NativeBlockTypes.bindBlockEntityType(tile.id, type)
            }
            if (tags != null) restoreTags(blockEntityRegistry, tags)
        } finally {
            if (wasFrozen) blockEntityRegistry.freeze()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createBlockEntityType(
        definition: CustomTileEntity<*>,
        block: Block,
    ): BlockEntityType<NativeCustomBlockEntity> {
        val supplierClass = Class.forName(
            "net.minecraft.world.level.block.entity.BlockEntityType\$BlockEntitySupplier",
        )
        val constructor = BlockEntityType::class.java.declaredConstructors
            .single { it.parameterCount == 2 }
            .apply { isAccessible = true }
        val supplier = Proxy.newProxyInstance(
            supplierClass.classLoader,
            arrayOf(supplierClass),
        ) { _, method, arguments ->
            if (method.name != "create") error("Unexpected BlockEntitySupplier method ${method.name}.")
            NativeCustomBlockEntity(
                definition,
                arguments[0] as net.minecraft.core.BlockPos,
                arguments[1] as net.minecraft.world.level.block.state.BlockState,
            )
        }
        return constructor.newInstance(supplier, setOf(block)) as BlockEntityType<NativeCustomBlockEntity>
    }

    private fun validate(definitions: Collection<CustomTile<*>>) {
        val ids = mutableSetOf<Identifier>()
        definitions.forEach { definition ->
            check(ids.add(definition.id)) { "Duplicate native block definition ${definition.id}." }
            check(!blockRegistry.containsKey(definition.id.minecraftLocation())) {
                "Minecraft block ${definition.id} is already registered."
            }
            if (definition is CustomTileEntity<*>) {
                check(!blockEntityRegistry.containsKey(definition.id.minecraftLocation())) {
                    "Minecraft block entity type ${definition.id} is already registered."
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun installBukkitMappings(definitions: Collection<CustomTile<*>>) {
        val field = CraftMagicNumbers::class.java.getDeclaredField("BLOCK_MATERIAL").apply { isAccessible = true }
        val mappings = field.get(null) as MutableMap<Block, org.bukkit.Material>
        for (definition in definitions) {
            val block = NativeBlockTypes.getNative(definition)
            mappings[block] = definition.descriptor
                .visualMethod(definition.descriptor.states.defaultState)
                .carrierMaterial()
        }
    }

    internal fun verifyStateIds(definitions: Collection<CustomTile<*>>) {
        for (definition in definitions) {
            NativeBlockTypes.getNative(definition).stateDefinition.possibleStates.forEach { state ->
                check(Block.BLOCK_STATE_REGISTRY.getId(state) >= 0) {
                    "Native state $state for ${definition.id} is absent from the global state registry."
                }
            }
        }
    }

    private fun verify(installed: Map<CustomTile<*>, NativeCustomBlock>) {
        for ((definition, block) in installed) {
            check(blockRegistry.getValue(definition.id.minecraftLocation()) === block)
            check(blockRegistry.getId(block) >= 0)
            check(block.stateDefinition.possibleStates.size == definition.descriptor.states.permutationCount) {
                "Native state count for ${definition.id} does not match its typed definition."
            }
        }
    }

    private fun <T : Any> snapshotTags(registry: MappedRegistry<T>): Map<net.minecraft.tags.TagKey<T>, List<Holder<T>>> =
        registry.listTags().toList().associate { named -> named.key() to named.stream().toList() }

    private fun <T : Any> restoreTags(
        registry: MappedRegistry<T>,
        tags: Map<net.minecraft.tags.TagKey<T>, List<Holder<T>>>,
    ) {
        tags.forEach(registry::bindTag)
    }

    private fun <T : Any> unfreeze(registry: MappedRegistry<T>, intrusive: Boolean) {
        setField(registry, "frozen", false)
        val tagSetClass = Class.forName("net.minecraft.core.MappedRegistry\$TagSet")
        val unbound = tagSetClass.getDeclaredMethod("unbound").apply { isAccessible = true }.invoke(null)
        setField(registry, "allTags", unbound)
        setField(
            registry,
            "unregisteredIntrusiveHolders",
            if (intrusive) IdentityHashMap<T, Holder.Reference<T>>() else null,
        )
    }

    private fun isFrozen(registry: MappedRegistry<*>): Boolean =
        getField(registry, "frozen") as Boolean

    private fun areTagsBound(registry: MappedRegistry<*>): Boolean {
        val tagSet = getField(registry, "allTags")
        val method = tagSet.javaClass.interfaces
            .single { it.name == "net.minecraft.core.MappedRegistry\$TagSet" }
            .getDeclaredMethod("isBound")
            .apply { isAccessible = true }
        return method.invoke(tagSet) as Boolean
    }

    private fun setField(registry: MappedRegistry<*>, name: String, value: Any?) {
        val field = MappedRegistry::class.java.getDeclaredField(name).apply { isAccessible = true }
        field.set(registry, value)
    }

    private fun getField(registry: MappedRegistry<*>, name: String): Any {
        val field = MappedRegistry::class.java.getDeclaredField(name).apply { isAccessible = true }
        return checkNotNull(field.get(registry)) { "MappedRegistry.$name was unexpectedly null." }
    }
}

private fun Identifier.minecraftLocation(): MinecraftIdentifier =
    MinecraftIdentifier.fromNamespaceAndPath(namespace, key)

private fun Identifier.blockKey(): ResourceKey<Block> = ResourceKey.create(Registries.BLOCK, minecraftLocation())

private fun Identifier.blockEntityKey(): ResourceKey<BlockEntityType<*>> =
    ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, minecraftLocation())

private fun xyz.mastriel.cutapi.block.BlockVisualMethod.carrierMaterial(): org.bukkit.Material = when (this) {
    is xyz.mastriel.cutapi.block.BlockVisualMethod.Vanilla -> state.material
    xyz.mastriel.cutapi.block.BlockVisualMethod.NoteBlock -> org.bukkit.Material.NOTE_BLOCK
    xyz.mastriel.cutapi.block.BlockVisualMethod.Mushroom -> org.bukkit.Material.RED_MUSHROOM_BLOCK
    is xyz.mastriel.cutapi.block.BlockVisualMethod.DisplayEntity -> carrier.material
}
