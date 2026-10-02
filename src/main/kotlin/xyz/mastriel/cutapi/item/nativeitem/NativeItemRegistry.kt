@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item.nativeitem

import net.minecraft.core.Holder
import net.minecraft.core.MappedRegistry
import net.minecraft.core.RegistrationInfo
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.Identifier as MinecraftIdentifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.Item
import org.bukkit.Registry
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.inventory.CraftItemType
import org.bukkit.craftbukkit.util.CraftMagicNumbers
import org.bukkit.inventory.ItemType
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.toIdentifier
import java.util.IdentityHashMap

public class NativeItemSpecification internal constructor(public val definition: CustomItem<*>)

/** Owns the single unfreeze/register/tag/refreeze transaction for native custom items. */
internal object NativeItemRegistry {
    private val registry: MappedRegistry<Item>
        get() = BuiltInRegistries.ITEM as MappedRegistry<Item>

    fun installAll(specifications: Collection<NativeItemSpecification>) {
        check(NativeItemLifecycle.state == NativeItemState.Installing)
        validate(specifications)

        val existingTags = snapshotTags()
        setField("frozen", false)
        resetBoundTagView()
        setField("unregisteredIntrusiveHolders", IdentityHashMap<Item, Holder.Reference<Item>>())

        val installed = linkedMapOf<NativeItemSpecification, NativeItemImplementation>()
        try {
            for (specification in specifications) {
                val definition = specification.definition
                val key = definition.id.minecraftKey()
                val backing = NativeItemTypes.getMinecraft(definition.backingItem)
                val implementation = NativeItemImplementationAdapters.create(backing, key)
                registry.register(key, implementation.item, RegistrationInfo.BUILT_IN)
                installed[specification] = implementation
            }

            restoreAndExtendBackingTags(installed, existingTags)
            installBukkitMaterialMappings(installed)
        } finally {
            registry.freeze()
        }

        for ((specification, implementation) in installed) {
            NativeItemTypes.bind(specification.definition.id, implementation.item)
        }
        verify(specifications, existingTags)
    }

    private fun validate(specifications: Collection<NativeItemSpecification>) {
        val ids = mutableSetOf<Identifier>()
        for (specification in specifications) {
            val definition = specification.definition
            check(ids.add(definition.id)) { "Duplicate native item definition ${definition.id}." }
            check(!registry.containsKey(definition.id.minecraftLocation())) {
                "Minecraft item ${definition.id} is already registered."
            }
            check(Registry.ITEM.get(definition.id.toNamespacedKey()) == null) {
                "Bukkit item ${definition.id} is already registered."
            }
            check(Registry.ITEM.get(definition.backingItem.key) === definition.backingItem) {
                "Backing item ${definition.backingItem.key} is not a registered vanilla ItemType."
            }
            check(definition.backingItem.key.namespace == NamespacedKey.MINECRAFT) {
                "Custom item ${definition.id} must use a vanilla backing item, not ${definition.backingItem.key}."
            }
            check(CustomItem.getOrNull(definition.backingItem.key.toIdentifier()) == null) {
                "Custom item ${definition.id} cannot use another custom item as its backing item."
            }
            NativeItemBehaviorAdapter.create(NativeItemTypes.getMinecraft(definition.backingItem))
        }
    }

    private fun snapshotTags(): Map<net.minecraft.tags.TagKey<Item>, List<Holder<Item>>> =
        registry.listTags().toList().associate { named -> named.key() to named.stream().toList() }

    private fun restoreAndExtendBackingTags(
        installed: Map<NativeItemSpecification, NativeItemImplementation>,
        existingTags: Map<net.minecraft.tags.TagKey<Item>, List<Holder<Item>>>,
    ) {
        for ((tag, existingHolders) in existingTags) {
            val rebuilt = existingHolders.toMutableList()
            for ((_, implementation) in installed) {
                if (implementation.backing.builtInRegistryHolder() in existingHolders) {
                    rebuilt += implementation.item.builtInRegistryHolder()
                }
            }
            registry.bindTag(tag, rebuilt)
        }
    }

    private fun resetBoundTagView() {
        val tagSetClass = Class.forName("net.minecraft.core.MappedRegistry\$TagSet")
        val unbound = tagSetClass.getDeclaredMethod("unbound").apply { isAccessible = true }.invoke(null)
        setField("allTags", unbound)
    }

    @Suppress("UNCHECKED_CAST")
    private fun installBukkitMaterialMappings(installed: Map<NativeItemSpecification, NativeItemImplementation>) {
        val field = CraftMagicNumbers::class.java.getDeclaredField("ITEM_MATERIAL")
        field.isAccessible = true
        val itemMaterials = field.get(null) as MutableMap<Item, org.bukkit.Material>
        for ((specification, implementation) in installed) {
            itemMaterials[implementation.item] = specification.definition.backingItem.asMaterial()
                ?: error("Backing item ${specification.definition.backingItem.key} has no Bukkit Material.")
        }
    }

    private fun verify(
        specifications: Collection<NativeItemSpecification>,
        existingTags: Map<net.minecraft.tags.TagKey<Item>, List<Holder<Item>>>,
    ) {
        val restoredTags = registry.listTags().toList().associate { named ->
            named.key() to named.stream().toList()
        }
        check(restoredTags.keys == existingTags.keys) {
            "Native item installation changed the set of bound vanilla item tags."
        }
        for ((tag, originalHolders) in existingTags) {
            check(restoredTags.getValue(tag).containsAll(originalHolders)) {
                "Native item installation removed vanilla holders from item tag ${tag.location}."
            }
        }

        for (specification in specifications) {
            val definition = specification.definition
            val nms = NativeItemTypes.getMinecraft(definition.id)
            check(registry.getValue(definition.id.minecraftLocation()) === nms)
            val numericId = registry.getId(nms)
            check(numericId >= 0)
            check(Item.byId(numericId) === nms)
            val bukkitType = Registry.ITEM.get(definition.id.toNamespacedKey())
                ?: error("Native item ${definition.id} is absent from Bukkit Registry.ITEM.")
            check(CraftItemType.bukkitToMinecraftNew(bukkitType) === nms)
            check(NativeItemTypes.get(definition.id).key == definition.id.toNamespacedKey())
            val expectedMaterial = definition.backingItem.asMaterial()
                ?: error("Backing item ${definition.backingItem.key} has no Bukkit Material.")
            check(CraftItemType.minecraftToBukkit(nms) == expectedMaterial)

            val bukkitStack = bukkitType.createItemStack()
            check(bukkitStack.type == expectedMaterial)
            check(NativeItemTypes.resolve(bukkitStack).key == definition.id.toNamespacedKey())
            check(NativeItemTypes.resolve(bukkitStack.clone()).key == definition.id.toNamespacedKey())
            val serialized = org.bukkit.Bukkit.getUnsafe().serializeItem(bukkitStack)
            val deserialized = org.bukkit.Bukkit.getUnsafe().deserializeItem(serialized)
            check(NativeItemTypes.resolve(deserialized).key == definition.id.toNamespacedKey())

            val registryOps = MinecraftServer.getServer().registryAccess()
                .createSerializationContext(NbtOps.INSTANCE)
            val encoded = net.minecraft.world.item.ItemStack.CODEC
                .encodeStart(registryOps, net.minecraft.world.item.ItemStack(nms))
                .getOrThrow()
            val decoded = net.minecraft.world.item.ItemStack.CODEC.parse(registryOps, encoded).getOrThrow()
            check(decoded.item === nms)

            val customHolder = nms.builtInRegistryHolder()
            val backing = NativeItemTypes.getMinecraft(definition.backingItem)
            NativeItemImplementationAdapters.verify(NativeItemImplementation(nms, backing))
            check(net.minecraft.world.item.ItemStack(nms).`is`(backing)) {
                "Native item ${definition.id} does not satisfy backing identity checks for " +
                    "${definition.backingItem.key}."
            }
            backing.builtInRegistryHolder().tags().forEach { tag ->
                check(customHolder.`is`(tag)) {
                    "Native item ${definition.id} is missing projected backing tag ${tag.location}."
                }
            }
        }
    }

    private fun setField(name: String, value: Any?) {
        val field = MappedRegistry::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(registry, value)
    }
}

private fun Identifier.minecraftLocation(): MinecraftIdentifier =
    MinecraftIdentifier.fromNamespaceAndPath(namespace, key)

private fun Identifier.minecraftKey(): ResourceKey<Item> = ResourceKey.create(Registries.ITEM, minecraftLocation())
