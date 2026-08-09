@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.item

import io.papermc.paper.datacomponent.DataComponentType
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.PaperDataComponentType
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers
import io.papermc.paper.datacomponent.item.BundleContents
import io.papermc.paper.datacomponent.item.ChargedProjectiles
import io.papermc.paper.datacomponent.item.ItemContainerContents
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.server.MinecraftServer
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.block.Container
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Item
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import xyz.mastriel.cutapi.attachment.ItemAttachment
import xyz.mastriel.cutapi.attachment.schema
import xyz.mastriel.cutapi.data.Variant
import xyz.mastriel.cutapi.item.attachments.Durability
import xyz.mastriel.cutapi.item.attachments.Equipable
import xyz.mastriel.cutapi.item.attachments.ModifyAttribute
import xyz.mastriel.cutapi.item.attachments.Tool
import xyz.mastriel.cutapi.item.attachments.ToolCategory
import xyz.mastriel.cutapi.item.attachments.ToolSpeed
import xyz.mastriel.cutapi.item.attachments.ToolTier
import xyz.mastriel.cutapi.item.attachments.Unstackable
import xyz.mastriel.cutapi.item.attachments.VanillaTool
import xyz.mastriel.cutapi.data.getOrThrow
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.registry.toIdentifier
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal enum class ItemTraitStrength { Default, Explicit }

/** Reconciles effective item attachments with the persistent traits they declaratively own. */
public object ItemAttachmentReconciler {
    /**
     * Reconciles [stack] in place. The operation is idempotent and must run on the server thread.
     *
     * Callers that need atomic attachment storage changes should invoke this on a detached clone
     * and copy the clone back only after this method succeeds.
     */
    public fun reconcile(stack: ItemStack): Boolean {
        if (stack.type.isAir || stack.amount <= 0) return false
        requirePrimaryServerThread()
        ItemAttachmentMaterializer.requireActive()

        val wrapped = CuTItemStack.wrap(stack)
        val resolved = resolveItemAttachments(wrapped)
        val desired = buildDesiredClaims(wrapped, resolved)
        val previous = ItemMaterializationLedger.read(stack)

        if (canFastPath(stack, desired, previous)) return false

        val nextClaims = linkedMapOf<String, StoredTraitClaim>()
        var changed = false
        val remainingPrevious = previous.claims.toMutableMap()

        for ((claimKey, desiredClaim) in desired.claims) {
            var stored = remainingPrevious.remove(claimKey)
            if (stored != null && !stored.sameOwner(desiredClaim)) {
                changed = restoreRemovedClaim(stack, stored) || changed
                stored = null
            }

            if (
                stored != null &&
                stored.strength == ItemTraitStrength.Explicit &&
                desiredClaim.strength == ItemTraitStrength.Default
            ) {
                changed = restoreSnapshot(stack, stored, force = true) || changed
                stored = null
            }

            val current = desiredClaim.capture(stack)
            if (stored == null) {
                if (
                    desiredClaim.strength == ItemTraitStrength.Default &&
                    !desiredClaim.matchesNativeDefault(stack, current)
                ) continue

                val applied = desiredClaim.desiredSnapshot(stack)
                if (current != applied) {
                    desiredClaim.apply(stack)
                    changed = true
                }
                nextClaims[claimKey] = desiredClaim.store(current, applied)
                continue
            }

            if (current != stored.applied) {
                if (stored.strength == ItemTraitStrength.Default) {
                    // An external writer changed an intrinsically supplied default. Relinquish it.
                    continue
                }
                desiredClaim.apply(stack)
                changed = true
            } else {
                val desiredSnapshot = desiredClaim.desiredSnapshot(stack)
                if (desiredSnapshot != stored.applied || !stored.matchesRevision(desiredClaim)) {
                    desiredClaim.apply(stack)
                    changed = true
                }
            }

            val applied = desiredClaim.capture(stack)
            nextClaims[claimKey] = desiredClaim.store(stored.baseline, applied)
        }

        for (stored in remainingPrevious.values) {
            changed = restoreRemovedClaim(stack, stored) || changed
        }

        val next = ItemMaterializationLedger(
            inputFingerprint = desired.inputFingerprint,
            claims = nextClaims,
        )
        if (nextClaims.isEmpty()) {
            changed = ItemMaterializationLedger.clear(stack) || changed
        } else if (next != previous) {
            ItemMaterializationLedger.write(stack, next)
            changed = true
        }
        return changed
    }

    private fun canFastPath(
        stack: ItemStack,
        desired: DesiredTraitSet,
        ledger: ItemMaterializationLedger,
    ): Boolean {
        if (!ledger.inputFingerprint.contentEquals(desired.inputFingerprint)) return false
        if (ledger.claims.keys != desired.claims.keys) return false
        return ledger.claims.all { (key, stored) ->
            val claim = desired.claims[key] ?: return@all false
            stored.sameOwner(claim) &&
                stored.matchesRevision(claim) &&
                stored.strength == claim.strength &&
                claim.capture(stack) == stored.applied
        }
    }

    private fun restoreRemovedClaim(stack: ItemStack, stored: StoredTraitClaim): Boolean {
        val current = stored.capture(stack)
        val shouldRestore = stored.strength == ItemTraitStrength.Explicit || current == stored.applied
        return if (shouldRestore) restoreSnapshot(stack, stored, force = true) else false
    }

    private fun restoreSnapshot(stack: ItemStack, stored: StoredTraitClaim, force: Boolean): Boolean {
        if (!force) return false
        val before = CraftItemStack.asNMSCopy(stack.clone())
        stored.restore(stack, stored.baseline)
        return !net.minecraft.world.item.ItemStack.isSameItemSameComponents(
            before,
            CraftItemStack.asNMSCopy(stack),
        )
    }

    private fun buildDesiredClaims(
        item: CuTItemStack,
        resolved: List<ResolvedItemAttachment>,
    ): DesiredTraitSet {
        val claims = linkedMapOf<String, DesiredTraitClaim>()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(item.identity.logicalId.toString().toByteArray(StandardCharsets.UTF_8))

        for ((schemaId, values) in resolved.groupBy { it.attachment.schema().id }) {
            val materializer = ItemAttachmentMaterializer.forSchema(schemaId) ?: continue
            contributeMaterializer(item, materializer, values, claims, digest)
        }
        return DesiredTraitSet(digest.digest(), claims)
    }

    @Suppress("UNCHECKED_CAST")
    private fun contributeMaterializer(
        item: CuTItemStack,
        rawMaterializer: ItemAttachmentMaterializer<*>,
        resolved: List<ResolvedItemAttachment>,
        claims: MutableMap<String, DesiredTraitClaim>,
        digest: MessageDigest,
    ) {
        val materializer = rawMaterializer as ItemAttachmentMaterializer<ItemAttachment>
        val attachments = resolved.map(ResolvedItemAttachment::attachment)
        val hasStackValues = resolved.any { it.source == ItemAttachmentSource.Stack }
        val strength = if (hasStackValues) ItemTraitStrength.Explicit else ItemTraitStrength.Default
        val context = ItemMaterializationContext(
            item = CuTItemStack.wrap(item.handle.clone()),
            attachments = attachments,
            hasStackValues = hasStackValues,
        )
        val output = ItemTraitPatchBuilder(materializer.claims)
        materializer.contribute(context, output)

        digest.update(materializer.id.toString().toByteArray(StandardCharsets.UTF_8))
        digest.update(materializer.revision.toString().toByteArray(StandardCharsets.UTF_8))
        digest.update(strength.name.toByteArray(StandardCharsets.UTF_8))
        for ((index, attachment) in resolved.withIndex()) {
            digest.update(index.toString().toByteArray(StandardCharsets.UTF_8))
            digest.update(attachment.source.id.toString().toByteArray(StandardCharsets.UTF_8))
            val variant = materializer.schema.serialize(attachment.attachment).getOrThrow()
            digest.update(canonicalVariant(variant).toByteArray(StandardCharsets.UTF_8))
        }

        for (operation in output.componentOperations.values) {
            val claim = DesiredComponentClaim(materializer, strength, operation)
            addDesiredClaim(claims, claim)
        }
        for (operation in output.attributeOperations.values) {
            val claim = DesiredAttributeClaim(materializer, strength, operation)
            addDesiredClaim(claims, claim)
        }
    }

    private fun addDesiredClaim(
        claims: MutableMap<String, DesiredTraitClaim>,
        claim: DesiredTraitClaim,
    ) {
        val previous = claims.put(claim.key, claim)
        require(previous == null) {
            "Item trait ${claim.key} is contributed by both ${previous?.ownerId} and ${claim.ownerId}."
        }
    }
}

/** Fast authoritative-boundary entry point used by events and periodic scans. */
public object ItemMaterializationManager {
    public fun ensureReconciled(stack: ItemStack?): Boolean {
        return ensureReconciled(stack, TraversalState())
    }

    private fun ensureReconciled(stack: ItemStack?, traversal: TraversalState): Boolean {
        if (stack == null || stack.type.isAir || stack.amount <= 0) return false
        if (!ItemAttachmentMaterializer.isActive()) return false
        if (traversal.depth >= MaxNestedDepth || traversal.visited >= MaxNestedItems) {
            if (!traversal.guardReported) {
                traversal.guardReported = true
                Bukkit.getLogger().warning(
                    "[CuTAPI] Stopped recursive item materialization after reaching the nested-item guard.",
                )
            }
            return false
        }
        traversal.depth++
        traversal.visited++
        var changed = reconcileNestedContents(stack, traversal)
        traversal.depth--

        val hasMaterializableAttachment = resolveItemAttachments(CuTItemStack.wrap(stack)).any {
            ItemAttachmentMaterializer.forSchema(it.attachment.schema().id) != null
        }
        if (!hasMaterializableAttachment && !ItemMaterializationLedger.has(stack)) return changed
        changed = ItemAttachmentReconciler.reconcile(stack) || changed
        return changed
    }

    private fun reconcileNestedContents(stack: ItemStack, traversal: TraversalState): Boolean {
        var changed = false

        stack.getData(DataComponentTypes.BUNDLE_CONTENTS)?.let { contents ->
            val items = contents.contents().map(ItemStack::clone)
            val nestedChanged = items.fold(false) { anyChanged, item ->
                ensureReconciled(item, traversal) || anyChanged
            }
            if (nestedChanged) {
                stack.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(items))
                changed = true
            }
        }

        stack.getData(DataComponentTypes.CONTAINER)?.let { contents ->
            val items = contents.contents().map(ItemStack::clone)
            val nestedChanged = items.fold(false) { anyChanged, item ->
                ensureReconciled(item, traversal) || anyChanged
            }
            if (nestedChanged) {
                stack.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(items))
                changed = true
            }
        }

        stack.getData(DataComponentTypes.CHARGED_PROJECTILES)?.let { contents ->
            val items = contents.projectiles().map(ItemStack::clone)
            val nestedChanged = items.fold(false) { anyChanged, item ->
                ensureReconciled(item, traversal) || anyChanged
            }
            if (nestedChanged) {
                stack.setData(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectiles.chargedProjectiles(items))
                changed = true
            }
        }
        return changed
    }

    internal fun reconcileInventory(inventory: Inventory): Boolean {
        var changed = false
        for (slot in 0 until inventory.size) {
            val stack = inventory.getItem(slot) ?: continue
            if (ensureReconciled(stack)) {
                inventory.setItem(slot, stack)
                changed = true
            }
        }
        return changed
    }

    internal fun reconcileEquipment(entity: LivingEntity): Boolean {
        val equipment = entity.equipment ?: return false
        var changed = false
        for (slot in EquipmentSlot.entries) {
            val stack = runCatching { equipment.getItem(slot) }.getOrNull() ?: continue
            if (ensureReconciled(stack)) {
                runCatching { equipment.setItem(slot, stack, true) }
                changed = true
            }
        }
        return changed
    }

    internal fun reconcileChunk(chunk: Chunk): Boolean {
        var changed = false
        for (entity in chunk.entities) {
            when (entity) {
                is Item -> {
                    val stack = entity.itemStack
                    if (ensureReconciled(stack)) {
                        entity.itemStack = stack
                        changed = true
                    }
                }
                is LivingEntity -> changed = reconcileEquipment(entity) || changed
            }
        }
        for (state in chunk.tileEntities) {
            val container = state as? Container ?: continue
            changed = reconcileInventory(container.inventory) || changed
        }
        return changed
    }

    internal fun reconcileLoadedServerState() {
        for (world in Bukkit.getWorlds()) {
            for (chunk in world.loadedChunks) reconcileChunk(chunk)
            for (player in world.players) {
                reconcileInventory(player.inventory)
                reconcileInventory(player.enderChest)
            }
        }
    }

    @Suppress("DEPRECATION")
    internal fun verifyBuiltInRoundTrips() {
        val durability = ItemStack(Material.DIAMOND_PICKAXE)
        val nativeMaxDamage = requireNotNull(durability.getData(DataComponentTypes.MAX_DAMAGE))
        durability.setAttachment(Durability(77))
        check(durability.getData(DataComponentTypes.MAX_DAMAGE) == 77)
        check(durability.hasStoredItemMaterialization())

        val reloaded = Bukkit.getUnsafe().deserializeItem(Bukkit.getUnsafe().serializeItem(durability))
        check(reloaded.getData(DataComponentTypes.MAX_DAMAGE) == 77)
        check(reloaded.hasStoredItemMaterialization())
        reloaded.removeAttachment(Durability)
        check(reloaded.getData(DataComponentTypes.MAX_DAMAGE) == nativeMaxDamage)
        check(!reloaded.hasStoredItemMaterialization())

        val externalBaseline = ItemStack(Material.DIAMOND_PICKAXE).apply {
            setData(DataComponentTypes.MAX_DAMAGE, 80)
        }
        externalBaseline.setAttachment(Durability(40))
        check(externalBaseline.getData(DataComponentTypes.MAX_DAMAGE) == 40)
        externalBaseline.removeAttachment(Durability)
        check(externalBaseline.getData(DataComponentTypes.MAX_DAMAGE) == 80)

        val unstackable = ItemStack(Material.PAPER)
        unstackable.setAttachment(Unstackable)
        check(unstackable.getData(DataComponentTypes.MAX_STACK_SIZE) == 1)
        unstackable.removeAttachment(Unstackable)
        check(unstackable.getData(DataComponentTypes.MAX_STACK_SIZE) == 64)

        val modifierKey = id("cutapi:materialization_verification")
        val attributed = ItemStack(Material.PAPER)
        attributed.setAttachment(
            ModifyAttribute(
                key = modifierKey,
                slotGroup = EquipmentSlotGroup.MAINHAND,
                attribute = Attribute.ATTACK_DAMAGE,
                amount = 3.0,
            ),
        )
        check(
            attributed.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                ?.modifiers()
                ?.any { it.modifier().key == modifierKey.toNamespacedKey() } == true,
        )
        attributed.removeAttachment(ModifyAttribute)
        check(
            attributed.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                ?.modifiers()
                ?.none { it.modifier().key == modifierKey.toNamespacedKey() } != false,
        )

        val equipable = ItemStack(Material.PAPER)
        equipable.setAttachment(Equipable(org.bukkit.inventory.EquipmentSlot.HEAD))
        check(equipable.hasData(DataComponentTypes.EQUIPPABLE))
        equipable.removeAttachment(Equipable)
        check(!equipable.isDataOverridden(DataComponentTypes.EQUIPPABLE))

        val tool = ItemStack(Material.PAPER)
        tool.setAttachment(
            VanillaTool(
                Tool(ToolCategory.Pickaxe, ToolTier.Diamond, ToolSpeed.Diamond),
            ),
        )
        check(tool.hasData(DataComponentTypes.TOOL))
        tool.removeAttachment(VanillaTool)
        check(!tool.isDataOverridden(DataComponentTypes.TOOL))
    }

    private const val MaxNestedDepth: Int = 16
    private const val MaxNestedItems: Int = 4096

    private class TraversalState(
        var depth: Int = 0,
        var visited: Int = 0,
        var guardReported: Boolean = false,
    )
}

private data class DesiredTraitSet(
    val inputFingerprint: ByteArray,
    val claims: Map<String, DesiredTraitClaim>,
) {
    override fun equals(other: Any?): Boolean =
        other is DesiredTraitSet &&
            inputFingerprint.contentEquals(other.inputFingerprint) &&
            claims == other.claims

    override fun hashCode(): Int = 31 * inputFingerprint.contentHashCode() + claims.hashCode()
}

private sealed interface DesiredTraitClaim {
    val key: String
    val ownerId: Identifier
    val schemaId: Identifier
    val revision: Int
    val strength: ItemTraitStrength
    val kind: StoredTraitKind
    val traitId: String

    fun capture(stack: ItemStack): TraitSnapshot
    fun desiredSnapshot(stack: ItemStack): TraitSnapshot
    fun matchesNativeDefault(stack: ItemStack, current: TraitSnapshot): Boolean
    fun apply(stack: ItemStack)

    fun store(baseline: TraitSnapshot, applied: TraitSnapshot): StoredTraitClaim = StoredTraitClaim(
        key = key,
        ownerId = ownerId,
        schemaId = schemaId,
        revision = revision,
        strength = strength,
        kind = kind,
        traitId = traitId,
        baseline = baseline,
        applied = applied,
    )
}

private class DesiredComponentClaim(
    materializer: ItemAttachmentMaterializer<*>,
    override val strength: ItemTraitStrength,
    private val operation: ComponentTraitOperation,
) : DesiredTraitClaim {
    override val traitId: String = operation.type.key.toString()
    override val key: String = "component:$traitId"
    override val ownerId: Identifier = materializer.id
    override val schemaId: Identifier = materializer.schema.id
    override val revision: Int = materializer.revision
    override val kind: StoredTraitKind = StoredTraitKind.Component

    override fun capture(stack: ItemStack): TraitSnapshot = ComponentSnapshots.capture(stack, operation.type)

    override fun desiredSnapshot(stack: ItemStack): TraitSnapshot {
        val copy = stack.clone()
        apply(copy)
        return capture(copy)
    }

    override fun matchesNativeDefault(stack: ItemStack, current: TraitSnapshot): Boolean =
        !stack.isDataOverridden(operation.type)

    override fun apply(stack: ItemStack) {
        val before = stack.clone()
        val after = stack.clone()
        operation.apply(after)
        require(after.type == before.type && after.amount == before.amount) {
            "Materializer $ownerId changed item identity or quantity while contributing $traitId."
        }
        require(before.matchesWithoutData(after, setOf(operation.type), true)) {
            "Materializer $ownerId changed data outside its declared component $traitId."
        }
        copyDataOverride(stack, after, operation.type)
        if (operation.type == DataComponentTypes.MAX_DAMAGE) clampDamage(stack)
    }
}

private class DesiredAttributeClaim(
    materializer: ItemAttachmentMaterializer<*>,
    override val strength: ItemTraitStrength,
    private val operation: AttributeTraitOperation,
) : DesiredTraitClaim {
    override val traitId: String = operation.modifier.key.toString()
    override val key: String = "attribute:$traitId"
    override val ownerId: Identifier = materializer.id
    override val schemaId: Identifier = materializer.schema.id
    override val revision: Int = materializer.revision
    override val kind: StoredTraitKind = StoredTraitKind.Attribute

    override fun capture(stack: ItemStack): TraitSnapshot = AttributeSnapshots.capture(stack, operation.modifier.key)

    override fun desiredSnapshot(stack: ItemStack): TraitSnapshot =
        AttributeSnapshots.snapshotOf(operation.attribute, operation.modifier)

    override fun matchesNativeDefault(stack: ItemStack, current: TraitSnapshot): Boolean {
        val native = stack.itemIdentity.itemType.createItemStack(1)
        return AttributeSnapshots.capture(native, operation.modifier.key) == current
    }

    override fun apply(stack: ItemStack) {
        AttributeSnapshots.replace(stack, operation.modifier.key, operation.attribute, operation.modifier)
    }
}

private enum class StoredTraitKind { Component, Attribute }

private data class StoredTraitClaim(
    val key: String,
    val ownerId: Identifier,
    val schemaId: Identifier,
    val revision: Int,
    val strength: ItemTraitStrength,
    val kind: StoredTraitKind,
    val traitId: String,
    val baseline: TraitSnapshot,
    val applied: TraitSnapshot,
) {
    fun sameOwner(desired: DesiredTraitClaim): Boolean =
        ownerId == desired.ownerId && schemaId == desired.schemaId && kind == desired.kind && traitId == desired.traitId

    fun matchesRevision(desired: DesiredTraitClaim): Boolean = revision == desired.revision

    fun capture(stack: ItemStack): TraitSnapshot = when (kind) {
        StoredTraitKind.Component -> ComponentSnapshots.capture(stack, componentType(traitId))
        StoredTraitKind.Attribute -> AttributeSnapshots.capture(stack, NamespacedKey.fromString(traitId)!!)
    }

    fun restore(stack: ItemStack, snapshot: TraitSnapshot) {
        when (kind) {
            StoredTraitKind.Component -> ComponentSnapshots.restore(stack, componentType(traitId), snapshot)
            StoredTraitKind.Attribute -> AttributeSnapshots.restore(stack, NamespacedKey.fromString(traitId)!!, snapshot)
        }
    }

    private fun componentType(id: String): DataComponentType =
        io.papermc.paper.registry.RegistryAccess.registryAccess()
            .getRegistry(io.papermc.paper.registry.RegistryKey.DATA_COMPONENT_TYPE)
            .get(NamespacedKey.fromString(id)!!)
            ?: error("Unknown data component type $id in item materialization ledger.")
}

private enum class SnapshotKind { NativeDefault, EncodedPatch }

private data class TraitSnapshot(
    val kind: SnapshotKind,
    val encodedPatch: ByteArray = byteArrayOf(),
) {
    override fun equals(other: Any?): Boolean =
        other is TraitSnapshot && kind == other.kind && encodedPatch.contentEquals(other.encodedPatch)

    override fun hashCode(): Int = 31 * kind.hashCode() + encodedPatch.contentHashCode()
}

private object ComponentSnapshots {
    fun capture(stack: ItemStack, type: DataComponentType): TraitSnapshot {
        val nms = CraftItemStack.asNMSCopy(stack)
        val nmsType = PaperDataComponentType.bukkitToMinecraft<Any>(type)
        @Suppress("UNCHECKED_CAST")
        val entry = nms.componentsPatch.get(nmsType) as java.util.Optional<Any>?
            ?: return TraitSnapshot(SnapshotKind.NativeDefault)
        val builder = DataComponentPatch.builder()
        if (entry.isPresent) builder.set(nmsType, entry.get()) else builder.remove(nmsType)
        return TraitSnapshot(SnapshotKind.EncodedPatch, ComponentPatchCodec.encode(builder.build()))
    }

    fun restore(stack: ItemStack, type: DataComponentType, snapshot: TraitSnapshot) {
        if (snapshot.kind == SnapshotKind.NativeDefault) {
            stack.resetData(type)
            if (type == DataComponentTypes.MAX_DAMAGE) clampDamage(stack)
            return
        }
        val nms = CraftItemStack.asNMSCopy(stack)
        nms.applyComponents(ComponentPatchCodec.decode(snapshot.encodedPatch))
        val restored = CraftItemStack.asBukkitCopy(nms)
        copyDataOverride(stack, restored, type)
        if (type == DataComponentTypes.MAX_DAMAGE) clampDamage(stack)
    }
}

private fun clampDamage(stack: ItemStack) {
    val maxDamage = stack.getData(DataComponentTypes.MAX_DAMAGE) ?: return
    val damage = stack.getData(DataComponentTypes.DAMAGE) ?: return
    val maximumValidDamage = (maxDamage - 1).coerceAtLeast(0)
    if (damage > maximumValidDamage) {
        stack.setData(DataComponentTypes.DAMAGE, maximumValidDamage)
    }
}

private object AttributeSnapshots {
    fun capture(stack: ItemStack, key: NamespacedKey): TraitSnapshot {
        val entry = stack.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
            ?.modifiers()
            ?.firstOrNull { it.modifier().key == key }
            ?: return TraitSnapshot(SnapshotKind.NativeDefault)
        return snapshotOf(entry)
    }

    fun snapshotOf(attribute: org.bukkit.attribute.Attribute, modifier: org.bukkit.attribute.AttributeModifier): TraitSnapshot {
        val component = ItemAttributeModifiers.itemAttributes()
            .addModifier(attribute, modifier)
            .build()
        val capsule = ItemStack(Material.STONE)
        capsule.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, component)
        return ComponentSnapshots.capture(capsule, DataComponentTypes.ATTRIBUTE_MODIFIERS)
    }

    fun replace(
        stack: ItemStack,
        key: NamespacedKey,
        attribute: org.bukkit.attribute.Attribute,
        modifier: org.bukkit.attribute.AttributeModifier,
    ) {
        val entries = stack.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS)?.modifiers().orEmpty()
        val builder = ItemAttributeModifiers.itemAttributes()
        entries.filterNot { it.modifier().key == key }.forEach { entry ->
            builder.addModifier(entry.attribute(), entry.modifier(), entry.modifier().slotGroup, entry.display())
        }
        builder.addModifier(attribute, modifier)
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build())
    }

    fun restore(stack: ItemStack, key: NamespacedKey, snapshot: TraitSnapshot) {
        val entry = entryFrom(snapshot)
        val entries = stack.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS)?.modifiers().orEmpty()
        val next = buildList {
            addAll(entries.filterNot { it.modifier().key == key })
            if (entry != null) add(entry)
        }
        val nativeEntries = stack.itemIdentity.itemType.createItemStack(1)
            .getData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
            ?.modifiers()
            .orEmpty()
        if (next == nativeEntries) {
            stack.resetData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
            return
        }
        val builder = ItemAttributeModifiers.itemAttributes()
        next.forEach { existing ->
            builder.addModifier(
                existing.attribute(),
                existing.modifier(),
                existing.modifier().slotGroup,
                existing.display(),
            )
        }
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build())
    }

    private fun snapshotOf(entry: ItemAttributeModifiers.Entry): TraitSnapshot {
        val component = ItemAttributeModifiers.itemAttributes()
            .addModifier(entry.attribute(), entry.modifier(), entry.modifier().slotGroup, entry.display())
            .build()
        val capsule = ItemStack(Material.STONE)
        capsule.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, component)
        return ComponentSnapshots.capture(capsule, DataComponentTypes.ATTRIBUTE_MODIFIERS)
    }

    private fun entryFrom(snapshot: TraitSnapshot): ItemAttributeModifiers.Entry? {
        if (snapshot.kind == SnapshotKind.NativeDefault) return null
        val capsule = ItemStack(Material.STONE)
        ComponentSnapshots.restore(capsule, DataComponentTypes.ATTRIBUTE_MODIFIERS, snapshot)
        return capsule.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS)?.modifiers()?.singleOrNull()
    }
}

private object ComponentPatchCodec {
    fun encode(patch: DataComponentPatch): ByteArray {
        val ops = MinecraftServer.getServer().registryAccess().createSerializationContext(NbtOps.INSTANCE)
        val tag = DataComponentPatch.CODEC.encodeStart(ops, patch).getOrThrow()
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output -> NbtIo.writeAnyTag(tag, output) }
            bytes.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): DataComponentPatch {
        val tag = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            NbtIo.readAnyTag(input, NbtAccounter.unlimitedHeap())
        }
        val ops = MinecraftServer.getServer().registryAccess().createSerializationContext(NbtOps.INSTANCE)
        return DataComponentPatch.CODEC.parse(ops, tag).getOrThrow()
    }
}

private data class ItemMaterializationLedger(
    val inputFingerprint: ByteArray = byteArrayOf(),
    val claims: Map<String, StoredTraitClaim> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean =
        other is ItemMaterializationLedger &&
            inputFingerprint.contentEquals(other.inputFingerprint) &&
            claims == other.claims

    override fun hashCode(): Int = 31 * inputFingerprint.contentHashCode() + claims.hashCode()

    companion object {
        private const val Version = 1
        private val RootKey = id("cutapi:item_materialization").toNamespacedKey()
        private val VersionKey = id("cutapi:version").toNamespacedKey()
        private val FingerprintKey = id("cutapi:input_fingerprint").toNamespacedKey()
        private val CountKey = id("cutapi:count").toNamespacedKey()
        private val KeyKey = id("cutapi:key").toNamespacedKey()
        private val OwnerKey = id("cutapi:owner").toNamespacedKey()
        private val SchemaKey = id("cutapi:schema").toNamespacedKey()
        private val RevisionKey = id("cutapi:revision").toNamespacedKey()
        private val StrengthKey = id("cutapi:strength").toNamespacedKey()
        private val KindKey = id("cutapi:kind").toNamespacedKey()
        private val TraitKey = id("cutapi:trait").toNamespacedKey()
        private val BaselineKindKey = id("cutapi:baseline_kind").toNamespacedKey()
        private val BaselineDataKey = id("cutapi:baseline_data").toNamespacedKey()
        private val AppliedKindKey = id("cutapi:applied_kind").toNamespacedKey()
        private val AppliedDataKey = id("cutapi:applied_data").toNamespacedKey()

        fun has(stack: ItemStack): Boolean =
            !stack.type.isAir && stack.amount > 0 && stack.itemMeta.persistentDataContainer.has(RootKey)

        fun read(stack: ItemStack): ItemMaterializationLedger {
            if (stack.type.isAir || stack.amount <= 0) return ItemMaterializationLedger()
            val root = stack.itemMeta.persistentDataContainer.get(RootKey, PersistentDataType.TAG_CONTAINER)
                ?: return ItemMaterializationLedger()
            if (root.get(VersionKey, PersistentDataType.INTEGER) != Version) return ItemMaterializationLedger()
            val claims = linkedMapOf<String, StoredTraitClaim>()
            val count = root.get(CountKey, PersistentDataType.INTEGER) ?: 0
            for (index in 0 until count) {
                val entry = root.get(entryKey(index), PersistentDataType.TAG_CONTAINER) ?: continue
                val stored = readClaim(entry) ?: continue
                claims[stored.key] = stored
            }
            return ItemMaterializationLedger(
                inputFingerprint = root.get(FingerprintKey, PersistentDataType.BYTE_ARRAY) ?: byteArrayOf(),
                claims = claims,
            )
        }

        fun write(stack: ItemStack, ledger: ItemMaterializationLedger) {
            val meta = stack.itemMeta
            val container = meta.persistentDataContainer
            val root = container.adapterContext.newPersistentDataContainer()
            root.set(VersionKey, PersistentDataType.INTEGER, Version)
            root.set(FingerprintKey, PersistentDataType.BYTE_ARRAY, ledger.inputFingerprint)
            root.set(CountKey, PersistentDataType.INTEGER, ledger.claims.size)
            ledger.claims.values.forEachIndexed { index, claim ->
                root.set(entryKey(index), PersistentDataType.TAG_CONTAINER, writeClaim(container, claim))
            }
            container.set(RootKey, PersistentDataType.TAG_CONTAINER, root)
            stack.itemMeta = meta
        }

        fun clear(stack: ItemStack): Boolean {
            if (!has(stack)) return false
            val meta = stack.itemMeta
            meta.persistentDataContainer.remove(RootKey)
            stack.itemMeta = meta
            return true
        }

        private fun readClaim(container: PersistentDataContainer): StoredTraitClaim? = runCatching {
            StoredTraitClaim(
                key = container.get(KeyKey, PersistentDataType.STRING)!!,
                ownerId = id(container.get(OwnerKey, PersistentDataType.STRING)!!),
                schemaId = id(container.get(SchemaKey, PersistentDataType.STRING)!!),
                revision = container.get(RevisionKey, PersistentDataType.INTEGER)!!,
                strength = ItemTraitStrength.entries[container.get(StrengthKey, PersistentDataType.BYTE)!!.toInt()],
                kind = StoredTraitKind.entries[container.get(KindKey, PersistentDataType.BYTE)!!.toInt()],
                traitId = container.get(TraitKey, PersistentDataType.STRING)!!,
                baseline = readSnapshot(container, BaselineKindKey, BaselineDataKey),
                applied = readSnapshot(container, AppliedKindKey, AppliedDataKey),
            )
        }.getOrNull()

        private fun writeClaim(
            parent: PersistentDataContainer,
            claim: StoredTraitClaim,
        ): PersistentDataContainer = parent.adapterContext.newPersistentDataContainer().apply {
            set(KeyKey, PersistentDataType.STRING, claim.key)
            set(OwnerKey, PersistentDataType.STRING, claim.ownerId.toString())
            set(SchemaKey, PersistentDataType.STRING, claim.schemaId.toString())
            set(RevisionKey, PersistentDataType.INTEGER, claim.revision)
            set(StrengthKey, PersistentDataType.BYTE, claim.strength.ordinal.toByte())
            set(KindKey, PersistentDataType.BYTE, claim.kind.ordinal.toByte())
            set(TraitKey, PersistentDataType.STRING, claim.traitId)
            writeSnapshot(this, BaselineKindKey, BaselineDataKey, claim.baseline)
            writeSnapshot(this, AppliedKindKey, AppliedDataKey, claim.applied)
        }

        private fun readSnapshot(
            container: PersistentDataContainer,
            kindKey: NamespacedKey,
            dataKey: NamespacedKey,
        ): TraitSnapshot {
            val kind = SnapshotKind.entries[container.get(kindKey, PersistentDataType.BYTE)!!.toInt()]
            val data = container.get(dataKey, PersistentDataType.BYTE_ARRAY) ?: byteArrayOf()
            return TraitSnapshot(kind, data)
        }

        private fun writeSnapshot(
            container: PersistentDataContainer,
            kindKey: NamespacedKey,
            dataKey: NamespacedKey,
            snapshot: TraitSnapshot,
        ) {
            container.set(kindKey, PersistentDataType.BYTE, snapshot.kind.ordinal.toByte())
            if (snapshot.encodedPatch.isNotEmpty()) {
                container.set(dataKey, PersistentDataType.BYTE_ARRAY, snapshot.encodedPatch)
            }
        }

        private fun entryKey(index: Int): NamespacedKey = id("cutapi:claim/$index").toNamespacedKey()
    }
}

internal fun ItemStack.hasStoredItemMaterialization(): Boolean = ItemMaterializationLedger.has(this)

internal fun ItemStack.clearStoredItemMaterialization() {
    ItemMaterializationLedger.clear(this)
}

internal fun copyItemDataExactly(target: ItemStack, source: ItemStack) {
    require(target.type == source.type && target.amount == source.amount) {
        "Exact item-data copies cannot change item identity or quantity."
    }
    val registry = io.papermc.paper.registry.RegistryAccess.registryAccess()
        .getRegistry(io.papermc.paper.registry.RegistryKey.DATA_COMPONENT_TYPE)
    for (type in registry) {
        if (!type.isPersistent) continue
        copyDataOverride(target, source, type)
    }
}

private fun copyDataOverride(target: ItemStack, source: ItemStack, type: DataComponentType) {
    if (!source.isDataOverridden(type)) {
        target.resetData(type)
    } else if (source.hasData(type)) {
        target.copyDataFrom(source) { it == type }
    } else {
        target.unsetData(type)
    }
}

internal fun requirePrimaryServerThread() {
    check(Bukkit.isPrimaryThread()) {
        "Item attachment mutation and materialization must run on the primary server thread."
    }
}

private fun canonicalVariant(value: Variant): String = when (value) {
    Variant.Null -> "null"
    is Variant.String -> "s:${value.value.length}:${value.value}"
    is Variant.Boolean -> "b:${value.value}"
    is Variant.Byte -> "y:${value.value}"
    is Variant.Short -> "h:${value.value}"
    is Variant.Int -> "i:${value.value}"
    is Variant.Long -> "l:${value.value}"
    is Variant.Float -> "f:${value.value.toRawBits()}"
    is Variant.Double -> "d:${value.value.toRawBits()}"
    is Variant.Char -> "c:${value.value.code}"
    is Variant.Identifier -> "id:${value.value}"
    is Variant.ResourceRef -> "ref:${value.value}"
    is Variant.List -> value.joinToString(prefix = "[", postfix = "]") { canonicalVariant(it) }
    is Variant.Map -> value.entries
        .map { it.key to canonicalVariant(it.value) }
        .sortedBy { it.first }
        .joinToString(prefix = "{", postfix = "}") { (key, entry) -> "$key=$entry" }
}
