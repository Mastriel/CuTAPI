@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.item.nativeitem

import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier as MinecraftIdentifier
import net.minecraft.resources.RegistryOps
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.tags.TagNetworkSerialization
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import io.netty.buffer.Unpooled
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.MinecraftServer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.persistence.PersistentDataType
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.item.clearStoredItemAttachments
import xyz.mastriel.cutapi.item.clearStoredItemMaterialization
import xyz.mastriel.cutapi.item.hasStoredItemAttachments
import xyz.mastriel.cutapi.item.hasStoredItemMaterialization
import xyz.mastriel.cutapi.item.setAttachment
import xyz.mastriel.cutapi.item.attachments.BlockPlaceAttachment
import xyz.mastriel.cutapi.item.attachments.DisplayAs
import xyz.mastriel.cutapi.item.attachments.Durability
import xyz.mastriel.cutapi.item.attachments.HideTooltip
import xyz.mastriel.cutapi.item.getAttachmentOrNull
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockClientBridge
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockLifecycle
import java.security.MessageDigest
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/** Converts native custom stacks at the stream-codec boundary. */
public object NativeItemClientBridge {
    private const val EnvelopeKey = "cutapi:network_item_v2"
    private const val Version = 2
    private val RenderedPresentationKey: NamespacedKey =
        requireNotNull(NamespacedKey.fromString("cutapi:rendered_presentation"))
    private val secret = Random.Default.nextBytes(32)
    private val componentProjectionCalls = AtomicLong()
    private val pendingCreativeStacks: MutableMap<ItemStack, PendingCreativeStack> =
        Collections.synchronizedMap(WeakHashMap())

    @JvmStatic
    public fun encode(stack: ItemStack): ItemStack {
        val call = NativeItemNetworkContext.current() ?: return stack
        if (call.direction != NetworkDirection.Clientbound || stack.isEmpty) return stack
        consumeRenderedPresentation(stack)?.let { cleaned ->
            val definition = NativeItemTypes.idOf(cleaned.item)?.let { CustomItem.getOrNull(it) }
            if (definition == null && !hasProtectedItemState(cleaned)) return cleaned

            // A presentation marker on authoritative state is invalid. Continue normal projection
            // with the marker removed so it cannot expose native identity or protected data.
            return encode(cleaned)
        }

        val definition = NativeItemTypes.idOf(stack.item)?.let { CustomItem.getOrNull(it) }
        if (definition != null) NativeItemLifecycle.requireActive()

        // A detached Craft mirror preserves authoritative identity while Bukkit display APIs mutate
        // only this network copy.
        val authoritative = CraftItemStack.asCraftMirror(stack.copy())
        val wrapped = CuTItemStack.wrap(authoritative)
        val hasProtectedState = authoritative.hasStoredItemAttachments() ||
            authoritative.hasStoredItemMaterialization()

        val rendered = wrapped.getRenderedItemStack(call.player)
        // Attachment storage and the materialization ownership ledger are authoritative server
        // state. Rendering has already consumed them, so only the authenticated envelope retains
        // their original bytes on the wire.
        rendered.clearStoredItemAttachments()
        rendered.clearStoredItemMaterialization()
        val renderedNms = CraftItemStack.asNMSCopy(rendered)
        val explicitRenderedModel = renderedNms.componentsPatch.get(DataComponents.ITEM_MODEL)
        val displayedType = definition?.let {
            wrapped.getAttachmentOrNull<DisplayAs>()?.itemType ?: it.backingItem
        }
        val projected = if (definition == null) {
            renderedNms
        } else {
            renderedNms.transmuteCopy(NativeItemTypes.getMinecraft(requireNotNull(displayedType)))
        }
        if (definition != null && explicitRenderedModel == null) {
            wrapped.declaredClientItemModel(call.player)?.let { model ->
                projected.set(
                    DataComponents.ITEM_MODEL,
                    MinecraftIdentifier.fromNamespaceAndPath(model.namespace, model.key),
                )
            }
        }
        if (
            displayedType == org.bukkit.inventory.ItemType.PIG_SPAWN_EGG &&
            wrapped.getAttachmentOrNull<BlockPlaceAttachment>() != null
        ) {
            // Spawn-egg use-on-block succeeds client-side and supplies the desired hand swing. The
            // entity identity is unnecessary for that path and must never escape as usable egg data.
            projected.remove(DataComponents.ENTITY_DATA)
        }
        definition?.descriptor?.forcedItemModelId?.let { modelId ->
            projected.set(
                DataComponents.ITEM_MODEL,
                MinecraftIdentifier.fromNamespaceAndPath(modelId.namespace, modelId.key),
            )
        }
        if (
            definition == null &&
            !hasProtectedState &&
            ItemStack.isSameItemSameComponents(stack, projected)
        ) return stack

        val originalBytes = Bukkit.getUnsafe().serializeItem(authoritative)
        val displayedHash = ItemStack.hashItemAndComponents(projected)
        val signature = sign(originalBytes, displayedHash)
        setEnvelope(projected, originalBytes, signature)
        return projected
    }

    /** Marks a detached, fully rendered stack so clientbound projection only removes this marker. */
    internal fun markRenderedPresentation(stack: org.bukkit.inventory.ItemStack) {
        stack.editMeta { meta ->
            meta.persistentDataContainer.set(RenderedPresentationKey, PersistentDataType.BYTE, 1)
        }
    }

    private fun consumeRenderedPresentation(stack: ItemStack): ItemStack? {
        val bukkit = CraftItemStack.asCraftMirror(stack.copy())
        val container = bukkit.itemMeta.persistentDataContainer
        if (!container.has(RenderedPresentationKey, PersistentDataType.BYTE)) return null
        bukkit.editMeta { meta -> meta.persistentDataContainer.remove(RenderedPresentationKey) }
        return CraftItemStack.asNMSCopy(bukkit)
    }

    internal fun verifyRoundTrips(definitions: Collection<CustomItem<*>>) {
        for (definition in definitions) {
            val authoritative = ItemStack(NativeItemTypes.getMinecraft(definition.id))
            val projected = NativeItemNetworkContext.with(
                NativeItemNetworkCall(NetworkDirection.Clientbound, null),
            ) { encode(authoritative) }
            val expectedWrapper = CuTItemStack.wrap(CraftItemStack.asCraftMirror(authoritative.copy()))
            val expectedDisplayedType = expectedWrapper.getAttachmentOrNull<DisplayAs>()?.itemType
                ?: definition.backingItem
            val expectedDisplayedItem = NativeItemTypes.getMinecraft(expectedDisplayedType)
            check(projected.item === expectedDisplayedItem) {
                "Client projection for ${definition.id} did not use ${expectedDisplayedType.key}."
            }
            if (
                expectedDisplayedType == org.bukkit.inventory.ItemType.PIG_SPAWN_EGG &&
                expectedWrapper.getAttachmentOrNull<BlockPlaceAttachment>() != null
            ) {
                check(!projected.has(DataComponents.ENTITY_DATA)) {
                    "Client placement projection for ${definition.id} retained spawn-egg entity data."
                }
            }

            val expectedPresentation = CraftItemStack.asNMSCopy(expectedWrapper.getRenderedItemStack(null))
            val explicitModel = expectedPresentation.componentsPatch.get(DataComponents.ITEM_MODEL)
            val declaredModel = expectedWrapper.declaredClientItemModel(null)?.let { modelId ->
                MinecraftIdentifier.fromNamespaceAndPath(modelId.namespace, modelId.key)
            }
            val forcedModel = definition.descriptor.forcedItemModelId?.let { modelId ->
                MinecraftIdentifier.fromNamespaceAndPath(modelId.namespace, modelId.key)
            }
            val expectedModel = forcedModel ?: if (explicitModel == null) {
                declaredModel ?: ItemStack(expectedDisplayedItem).get(DataComponents.ITEM_MODEL)
            } else {
                explicitModel.orElse(null)
            }
            val actualModel = projected.get(DataComponents.ITEM_MODEL)
            check(actualModel == expectedModel) {
                "Client projection for ${definition.id} used item model $actualModel; expected $expectedModel."
            }
            val staticPresentation = CraftItemStack.asNMSCopy(expectedWrapper.getStaticItemStack(null))
            check(staticPresentation.item === expectedDisplayedItem) {
                "Static presentation for ${definition.id} did not use ${expectedDisplayedType.key}."
            }
            check(staticPresentation.get(DataComponents.ITEM_MODEL) == expectedModel) {
                "Static presentation for ${definition.id} used item model " +
                    "${staticPresentation.get(DataComponents.ITEM_MODEL)}; expected $expectedModel."
            }
            if (forcedModel != null) {
                check(actualModel == forcedModel) {
                    "Client projection for ${definition.id} omitted forced item model $forcedModel."
                }
            }
            val restored = NativeItemNetworkContext.with(
                NativeItemNetworkCall(NetworkDirection.Serverbound, null),
            ) { decode(projected) }
            check(restored.item === authoritative.item) {
                "Client projection for ${definition.id} did not restore its native identity."
            }
            check(readEnvelope(restored) == null) {
                "Client projection for ${definition.id} retained its network envelope after restoration."
            }
            verifyCodecRoundTrip(authoritative, definition)
            verifyComponentCodecRoundTrip(authoritative, definition)
        }
        verifyVanillaAttachmentRoundTrip()
        verifyVanillaMaterializationRoundTrip()
        verifyCreativeBundleMutationRoundTrip()
        verifyUnattachedVanillaRenderRoundTrip()
        verifyRenderedPresentationBypass()
        verifyTagProjection()
    }

    private fun verifyRenderedPresentationBypass() {
        val expectedName = net.kyori.adventure.text.Component.text("rendered presentation")
        val expectedLore = listOf(net.kyori.adventure.text.Component.text("single origin"))
        val bukkit = org.bukkit.inventory.ItemStack(Material.STICK).apply {
            editMeta { meta ->
                meta.itemName(expectedName)
                meta.lore(expectedLore)
            }
        }
        markRenderedPresentation(bukkit)

        val bytes = Unpooled.buffer()
        try {
            val buffer = RegistryFriendlyByteBuf(bytes, MinecraftServer.getServer().registryAccess())
            val component = Component.literal("rendered presentation")
                .setStyle(Style.EMPTY.withHoverEvent(HoverEvent.ShowItem(CraftItemStack.asNMSCopy(bukkit))))
            val projectedComponent = NativeItemNetworkContext.with(
                NativeItemNetworkCall(NetworkDirection.Clientbound, null),
            ) { encodeComponent(buffer, component) }
            val projected = (projectedComponent.style.hoverEvent as? HoverEvent.ShowItem)?.item()
                ?: error("Rendered presentation projection removed its item hover event.")
            val result = CraftItemStack.asCraftMirror(projected.copy())

            check(result.itemMeta.itemName() == expectedName) {
                "A rendered presentation lost its item name during clientbound projection."
            }
            check(result.itemMeta.lore() == expectedLore) {
                "A rendered presentation was processed more than once."
            }
            check(!result.itemMeta.persistentDataContainer.has(RenderedPresentationKey)) {
                "A rendered presentation exposed its internal projection marker."
            }
            check(readEnvelope(projected) == null) {
                "A rendered presentation was unnecessarily wrapped in a network envelope."
            }
        } finally {
            bytes.release()
        }
    }

    private fun verifyUnattachedVanillaRenderRoundTrip() {
        val explicitModel = MinecraftIdentifier.fromNamespaceAndPath(
            "cutapi",
            "verification/vanilla_item_model",
        )
        val modeled = ItemStack(net.minecraft.world.item.Items.PAPER).apply {
            set(DataComponents.ITEM_MODEL, explicitModel)
        }
        val renderedModeled = CraftItemStack.asNMSCopy(
            CuTItemStack.wrap(CraftItemStack.asCraftMirror(modeled.copy())).getRenderedItemStack(null),
        )
        check(renderedModeled.get(DataComponents.ITEM_MODEL) == explicitModel) {
            "Rendering an unattached vanilla item discarded its explicit item model."
        }
        val projectedModeled = NativeItemNetworkContext.with(
            NativeItemNetworkCall(NetworkDirection.Clientbound, null),
        ) { encode(modeled) }
        check(projectedModeled.get(DataComponents.ITEM_MODEL) == explicitModel) {
            "Client projection of an unattached vanilla item discarded its explicit item model."
        }

        val authoritative = ItemStack(net.minecraft.world.item.Items.STONE)
        val expected = CraftItemStack.asNMSCopy(
            CuTItemStack.wrap(CraftItemStack.asCraftMirror(authoritative.copy())).getRenderedItemStack(null),
        )

        // Origin rendering can be disabled. If no general render system changes the stack, the
        // unchanged-vanilla fast path is the expected result and there is nothing to round-trip.
        if (ItemStack.isSameItemSameComponents(authoritative, expected)) return

        val projected = NativeItemNetworkContext.with(
            NativeItemNetworkCall(NetworkDirection.Clientbound, null),
        ) { encode(authoritative) }
        check(projected.get(DataComponents.LORE) == expected.get(DataComponents.LORE)) {
            "An unattached vanilla item did not retain output from its general render systems."
        }
        check(readEnvelope(projected) != null) {
            "A rendered unattached vanilla item was sent without an authenticated network envelope."
        }

        val restored = NativeItemNetworkContext.with(
            NativeItemNetworkCall(NetworkDirection.Serverbound, null),
        ) { decode(projected) }
        check(ItemStack.isSameItemSameComponents(authoritative, restored)) {
            "A rendered unattached vanilla item did not restore its authoritative components."
        }
    }

    private fun verifyVanillaAttachmentRoundTrip() {
        val authoritativeBukkit = org.bukkit.inventory.ItemStack(Material.STICK).apply {
            setAttachment(DisplayAs(org.bukkit.inventory.ItemType.PAPER))
            setAttachment(HideTooltip)
        }
        val authoritative = CraftItemStack.asNMSCopy(authoritativeBukkit)
        val expectedDisplayedItem = NativeItemTypes.getMinecraft(org.bukkit.inventory.ItemType.PAPER)
        val projected = NativeItemNetworkContext.with(
            NativeItemNetworkCall(NetworkDirection.Clientbound, null),
        ) { encode(authoritative) }
        check(projected.item === expectedDisplayedItem) {
            "Attached vanilla item did not apply DisplayAs during client projection."
        }
        check(CraftItemStack.asCraftMirror(projected.copy()).itemMeta.isHideTooltip) {
            "Attached vanilla item did not retain its rendered tooltip state."
        }
        check(readEnvelope(projected) != null) {
            "Attached vanilla item was sent without an authenticated network envelope."
        }

        val restored = NativeItemNetworkContext.with(
            NativeItemNetworkCall(NetworkDirection.Serverbound, null),
        ) { decode(projected) }
        check(ItemStack.isSameItemSameComponents(authoritative, restored)) {
            "Attached vanilla item did not restore its authoritative components."
        }
        check(readEnvelope(restored) == null) {
            "Attached vanilla item retained its network envelope after restoration."
        }

        val forged = NativeItemNetworkContext.with(
            NativeItemNetworkCall(NetworkDirection.Serverbound, null),
        ) { decode(authoritative) }
        check(!hasStoredItemAttachments(forged)) {
            "Unauthenticated vanilla item attachment data was accepted from the client."
        }

        verifyVanillaAttachmentCodecRoundTrip(authoritative, expectedDisplayedItem)
        verifyVanillaAttachmentComponentProjection(authoritative, expectedDisplayedItem)
    }

    private fun verifyVanillaMaterializationRoundTrip() {
        val authoritativeBukkit = org.bukkit.inventory.ItemStack(Material.DIAMOND_PICKAXE).apply {
            setAttachment(Durability(77))
        }
        check(authoritativeBukkit.hasStoredItemMaterialization()) {
            "A materialized vanilla item did not retain its authoritative ownership ledger."
        }
        val authoritative = CraftItemStack.asNMSCopy(authoritativeBukkit)
        val projected = NativeItemNetworkContext.with(
            NativeItemNetworkCall(NetworkDirection.Clientbound, null),
        ) { encode(authoritative) }
        check(!CraftItemStack.asCraftMirror(projected.copy()).hasStoredItemMaterialization()) {
            "A materialized vanilla item's ownership ledger reached its client projection."
        }
        val restored = NativeItemNetworkContext.with(
            NativeItemNetworkCall(NetworkDirection.Serverbound, null),
        ) { decode(projected) }
        check(ItemStack.isSameItemSameComponents(authoritative, restored)) {
            "A materialized vanilla item did not restore its authoritative ownership ledger."
        }

        val forged = NativeItemNetworkContext.with(
            NativeItemNetworkCall(NetworkDirection.Serverbound, null),
        ) { decode(authoritative) }
        check(!CraftItemStack.asCraftMirror(forged.copy()).hasStoredItemMaterialization()) {
            "Unauthenticated item materialization state was accepted from the client."
        }
    }

    private fun verifyCreativeBundleMutationRoundTrip() {
        val registryAccess = MinecraftServer.getServer().registryAccess()
        val authoritative = ItemStack(net.minecraft.world.item.Items.BUNDLE)
        val clientboundBytes = Unpooled.buffer()
        val serverboundBytes = Unpooled.buffer()
        try {
            val clientboundBuffer = RegistryFriendlyByteBuf(clientboundBytes, registryAccess)
            NativeItemNetworkContext.with(NativeItemNetworkCall(NetworkDirection.Clientbound, null)) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(clientboundBuffer, authoritative)
            }
            clientboundBytes.readerIndex(0)
            val projected = ItemStack.OPTIONAL_STREAM_CODEC.decode(clientboundBuffer)
            projected.set(
                DataComponents.BUNDLE_CONTENTS,
                net.minecraft.world.item.component.BundleContents(
                    listOf(ItemStack(net.minecraft.world.item.Items.STONE)),
                ),
            )

            val serverboundBuffer = RegistryFriendlyByteBuf(serverboundBytes, registryAccess)
            ItemStack.OPTIONAL_STREAM_CODEC.encode(serverboundBuffer, projected)
            serverboundBytes.readerIndex(0)
            val decoded = NativeItemNetworkContext.with(
                NativeItemNetworkCall(NetworkDirection.Serverbound, null),
            ) { ItemStack.OPTIONAL_STREAM_CODEC.decode(serverboundBuffer) }
            val recovered = when (val resolution = resolveCreativeSlotStack(decoded)) {
                is CreativeSlotStackResolution.Accept -> resolution.stack
                CreativeSlotStackResolution.Reject -> error(
                    "A legitimate creative bundle-content edit was rejected.",
                )
            }
            check(recovered.item === authoritative.item)
            check(
                recovered.get(DataComponents.BUNDLE_CONTENTS)
                    ?.items()
                    ?.singleOrNull()
                    ?.item === net.minecraft.world.item.Items.STONE,
            ) {
                "A creative bundle-content edit did not retain its authoritative nested item."
            }
            val withoutContents = recovered.copy().apply {
                set(DataComponents.BUNDLE_CONTENTS, authoritative.getOrDefault(
                    DataComponents.BUNDLE_CONTENTS,
                    net.minecraft.world.item.component.BundleContents.EMPTY,
                ))
            }
            check(ItemStack.isSameItemSameComponents(authoritative, withoutContents)) {
                "A creative bundle-content edit retained client-rendered outer components."
            }
        } finally {
            clientboundBytes.release()
            serverboundBytes.release()
        }
    }

    private fun verifyVanillaAttachmentCodecRoundTrip(authoritative: ItemStack, expectedDisplayedItem: Item) {
        val registryAccess = MinecraftServer.getServer().registryAccess()
        val clientboundBytes = Unpooled.buffer()
        val serverboundBytes = Unpooled.buffer()
        try {
            val clientboundBuffer = RegistryFriendlyByteBuf(clientboundBytes, registryAccess)
            NativeItemNetworkContext.with(NativeItemNetworkCall(NetworkDirection.Clientbound, null)) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(clientboundBuffer, authoritative)
            }
            clientboundBytes.readerIndex(0)
            val projected = ItemStack.OPTIONAL_STREAM_CODEC.decode(clientboundBuffer)
            check(projected.item === expectedDisplayedItem)

            val serverboundBuffer = RegistryFriendlyByteBuf(serverboundBytes, registryAccess)
            ItemStack.OPTIONAL_STREAM_CODEC.encode(serverboundBuffer, projected)
            serverboundBytes.readerIndex(0)
            val restored = NativeItemNetworkContext.with(
                NativeItemNetworkCall(NetworkDirection.Serverbound, null),
            ) { ItemStack.OPTIONAL_STREAM_CODEC.decode(serverboundBuffer) }
            check(ItemStack.isSameItemSameComponents(authoritative, restored)) {
                "Instrumented stack codec failed to restore an attached vanilla item."
            }
        } finally {
            clientboundBytes.release()
            serverboundBytes.release()
        }
    }

    private fun verifyVanillaAttachmentComponentProjection(
        authoritative: ItemStack,
        expectedDisplayedItem: Item,
    ) {
        val registryAccess = MinecraftServer.getServer().registryAccess()
        val bytes = Unpooled.buffer()
        try {
            val buffer = RegistryFriendlyByteBuf(bytes, registryAccess)
            val component = Component.literal("attached vanilla item")
                .setStyle(Style.EMPTY.withHoverEvent(HoverEvent.ShowItem(authoritative)))
            val projected = NativeItemNetworkContext.with(
                NativeItemNetworkCall(NetworkDirection.Clientbound, null),
            ) { encodeComponent(buffer, component) }
            val shown = (projected.style.hoverEvent as? HoverEvent.ShowItem)?.item()
                ?: error("Vanilla attachment projection removed the item hover event.")
            check(shown.item === expectedDisplayedItem) {
                "Vanilla attachment projection did not convert its hover item."
            }
        } finally {
            bytes.release()
        }
    }

    private fun verifyComponentCodecRoundTrip(authoritative: ItemStack, definition: CustomItem<*>) {
        val registryAccess = MinecraftServer.getServer().registryAccess()
        val expectedDisplayedItem = displayedItem(authoritative, definition)
        val bytes = Unpooled.buffer()
        try {
            val buffer = RegistryFriendlyByteBuf(bytes, registryAccess)
            val component = Component.literal("native item")
                .setStyle(Style.EMPTY.withHoverEvent(HoverEvent.ShowItem(authoritative)))
            val directlyProjected = NativeItemNetworkContext.with(
                NativeItemNetworkCall(NetworkDirection.Clientbound, null),
            ) { encodeComponent(buffer, component) }
            val directlyShown = (directlyProjected.style.hoverEvent as? HoverEvent.ShowItem)?.item()
                ?: error("Component projection removed the item hover event for ${definition.id}.")
            check(directlyShown.item === expectedDisplayedItem) {
                val ops = registryAccess.createSerializationContext(NbtOps.INSTANCE)
                val encoded = ComponentSerialization.CODEC.encodeStart(ops, component).getOrThrow()
                "Component projection did not find native item ${definition.id} in $encoded."
            }
            val callsAfterDirectProjection = componentProjectionCalls.get()
            NativeItemNetworkContext.with(NativeItemNetworkCall(NetworkDirection.Clientbound, null)) {
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, component)
            }
            check(componentProjectionCalls.get() > callsAfterDirectProjection) {
                "Registry-aware component codec instrumentation was not invoked."
            }
            bytes.readerIndex(0)
            val decoded = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer)
            val shown = (decoded.style.hoverEvent as? HoverEvent.ShowItem)?.item()
                ?: error("Instrumented component codec removed the item hover event for ${definition.id}.")
            check(shown.item === expectedDisplayedItem) {
                "Instrumented component codec exposed native item ${definition.id}."
            }
        } finally {
            bytes.release()
        }
    }

    /** Rewrites native item stacks embedded in component NBT before it reaches an unmodified client. */
    @JvmStatic
    public fun encodeComponent(buffer: RegistryFriendlyByteBuf, component: Component): Component {
        val call = NativeItemNetworkContext.current() ?: return component
        if (call.direction != NetworkDirection.Clientbound) return component
        componentProjectionCalls.incrementAndGet()

        val ops = buffer.registryAccess().createSerializationContext(NbtOps.INSTANCE)
        val encoded = ComponentSerialization.CODEC.encodeStart(ops, component).getOrThrow()
        val projected = projectEmbeddedItemTags(encoded, ops)
        if (!projected.changed) return component
        return ComponentSerialization.CODEC.parse(ops, projected.tag).getOrThrow()
    }

    private fun projectEmbeddedItemTags(tag: Tag, ops: RegistryOps<Tag>): ProjectedTag {
        if (tag is CompoundTag) {
            projectItemStackTag(tag, ops)?.let { return ProjectedTag(it, changed = true) }

            var copy: CompoundTag? = null
            for ((key, child) in tag.entrySet()) {
                val projected = projectEmbeddedItemTags(child, ops)
                if (projected.changed) {
                    val target = copy ?: tag.copy().also { copy = it }
                    target.put(key, projected.tag)
                }
            }
            return ProjectedTag(copy ?: tag, copy != null)
        }

        if (tag is ListTag) {
            var copy: ListTag? = null
            for (index in tag.indices) {
                val projected = projectEmbeddedItemTags(tag[index], ops)
                if (projected.changed) {
                    val target = copy ?: tag.copy().also { copy = it }
                    target[index] = projected.tag
                }
            }
            return ProjectedTag(copy ?: tag, copy != null)
        }

        return ProjectedTag(tag, changed = false)
    }

    private fun projectItemStackTag(tag: CompoundTag, ops: RegistryOps<Tag>): Tag? {
        if (tag.getStringOr("id", "").isEmpty()) return null
        val authoritative = ItemStack.CODEC.parse(ops, tag).result().orElse(null) ?: return null
        val projected = encode(authoritative)
        if (projected === authoritative) return null
        val encoded = ItemStack.CODEC.encodeStart(ops, projected).getOrThrow() as? CompoundTag
            ?: error("Projected item did not encode as a compound tag.")

        // HoverEvent.ShowItem flattens ItemStack.MAP_CODEC into the hover-event compound alongside
        // its `action` field. Replace only the item fields so enclosing codec data is preserved.
        return tag.copy().apply {
            for (key in ItemStackFields) {
                val value = encoded.get(key)
                if (value == null) remove(key) else put(key, value.copy())
            }
        }
    }

    private data class ProjectedTag(val tag: Tag, val changed: Boolean)

    private val ItemStackFields: Set<String> = setOf("id", "count", "components")

    private fun verifyCodecRoundTrip(authoritative: ItemStack, definition: CustomItem<*>) {
        val registryAccess = MinecraftServer.getServer().registryAccess()
        val expectedWrapper = CuTItemStack.wrap(CraftItemStack.asCraftMirror(authoritative.copy()))
        val expectedDisplayedType = expectedWrapper.getAttachmentOrNull<DisplayAs>()?.itemType
            ?: definition.backingItem
        val expectedDisplayedItem = NativeItemTypes.getMinecraft(expectedDisplayedType)
        val clientboundBytes = Unpooled.buffer()
        val serverboundBytes = Unpooled.buffer()
        try {
            val clientboundBuffer = RegistryFriendlyByteBuf(clientboundBytes, registryAccess)
            NativeItemNetworkContext.with(NativeItemNetworkCall(NetworkDirection.Clientbound, null)) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(clientboundBuffer, authoritative)
            }
            clientboundBytes.readerIndex(0)
            val projected = ItemStack.OPTIONAL_STREAM_CODEC.decode(clientboundBuffer)
            check(projected.item === expectedDisplayedItem)
            if (
                expectedDisplayedType == org.bukkit.inventory.ItemType.PIG_SPAWN_EGG &&
                expectedWrapper.getAttachmentOrNull<BlockPlaceAttachment>() != null
            ) {
                check(!projected.has(DataComponents.ENTITY_DATA)) {
                    "Instrumented stack codec retained spawn-egg entity data for ${definition.id}."
                }
            }
            definition.descriptor.forcedItemModelId?.let { modelId ->
                val forcedModel = MinecraftIdentifier.fromNamespaceAndPath(modelId.namespace, modelId.key)
                check(projected.get(DataComponents.ITEM_MODEL) == forcedModel) {
                    "Instrumented stack codec omitted forced item model $forcedModel for ${definition.id}."
                }
            }

            val serverboundBuffer = RegistryFriendlyByteBuf(serverboundBytes, registryAccess)
            ItemStack.OPTIONAL_STREAM_CODEC.encode(serverboundBuffer, projected)
            serverboundBytes.readerIndex(0)
            val restored = NativeItemNetworkContext.with(
                NativeItemNetworkCall(NetworkDirection.Serverbound, null),
            ) { ItemStack.OPTIONAL_STREAM_CODEC.decode(serverboundBuffer) }
            check(restored.item === authoritative.item) {
                "Instrumented stack codec failed to restore ${definition.id}."
            }
        } finally {
            clientboundBytes.release()
            serverboundBytes.release()
        }
    }

    private fun displayedItem(authoritative: ItemStack, definition: CustomItem<*>): Item {
        val wrapped = CuTItemStack.wrap(CraftItemStack.asCraftMirror(authoritative.copy()))
        val displayedType = wrapped.getAttachmentOrNull<DisplayAs>()?.itemType ?: definition.backingItem
        return NativeItemTypes.getMinecraft(displayedType)
    }

    @Suppress("UNCHECKED_CAST")
    private fun verifyTagProjection() {
        val payloads = TagNetworkSerialization.serializeTagsToNetwork(MinecraftServer.getServer().registries())
        val payload = payloads[Registries.ITEM] ?: return
        val tagsField = TagNetworkSerialization.NetworkPayload::class.java.getDeclaredField("tags")
        tagsField.isAccessible = true
        val tags = tagsField.get(payload) as Map<net.minecraft.resources.Identifier, IntList>
        check(tags.values.none { ids ->
            ids.intStream().anyMatch { BuiltInRegistries.ITEM.byId(it) is NativeBackedItem }
        }) { "Client item tag projection retained a native custom numeric ID." }
    }

    @JvmStatic
    public fun decode(stack: ItemStack): ItemStack {
        val call = NativeItemNetworkContext.current() ?: return stack
        if (call.direction != NetworkDirection.Serverbound || stack.isEmpty) return stack

        val envelope = readEnvelope(stack)
        if (envelope == null) {
            if (!hasProtectedItemState(stack)) return stack
            call.player?.let { player ->
                Bukkit.getScheduler().runTask(xyz.mastriel.cutapi.Plugin, Runnable(player::updateInventory))
            }
            return removeProtectedItemState(stack)
        }
        val cleaned = removeEnvelope(stack)
        fun reject(): ItemStack {
            call.player?.let { player ->
                Bukkit.getScheduler().runTask(xyz.mastriel.cutapi.Plugin, Runnable(player::updateInventory))
            }
            return removeProtectedItemState(cleaned).also { rejected ->
                pendingCreativeStacks[rejected] = PendingCreativeStack.Rejected
            }
        }

        val originalBytes = envelope.getByteArray("original").orElse(null) ?: return reject()
        val signature = envelope.getByteArray("signature").orElse(null) ?: return reject()
        if (envelope.getIntOr("version", -1) != Version) return reject()
        if (!MessageDigest.isEqual(signature, sign(originalBytes, ItemStack.hashItemAndComponents(cleaned)))) {
            val mutation = recoverCreativeBundleMutation(
                originalBytes = originalBytes,
                signature = signature,
                received = cleaned,
                player = call.player,
            ) ?: return reject()
            pendingCreativeStacks[cleaned] = mutation
            return cleaned
        }

        if (NativeItemLifecycle.state != NativeItemState.Active) return reject()

        val restored = runCatching {
            CraftItemStack.asNMSCopy(Bukkit.getUnsafe().deserializeItem(originalBytes))
        }.getOrElse { return reject() }
        restored.count = stack.count
        return restored
    }

    internal fun resolveCreativeSlotStack(stack: ItemStack): CreativeSlotStackResolution {
        val resolved = resolvePendingCreativeStack(stack)
            ?: return CreativeSlotStackResolution.Reject
        return CreativeSlotStackResolution.Accept(resolved)
    }

    private fun resolvePendingCreativeStack(stack: ItemStack): ItemStack? {
        return when (val pending = pendingCreativeStacks.remove(stack)) {
            null -> stack
            PendingCreativeStack.Rejected -> null
            is PendingCreativeStack.BundleMutation -> {
                val contents = resolvePendingCreativeContents(pending.contents) ?: return null
                pending.authoritative.copy().apply {
                    set(DataComponents.BUNDLE_CONTENTS, contents)
                }
            }
        }
    }

    private fun resolvePendingCreativeContents(
        contents: net.minecraft.world.item.component.BundleContents,
    ): net.minecraft.world.item.component.BundleContents? {
        var changed = false
        val resolvedItems = contents.items().map { item ->
            val resolved = resolvePendingCreativeStack(item) ?: return null
            changed = changed || resolved !== item
            resolved
        }
        if (!changed) return contents

        var resolved = net.minecraft.world.item.component.BundleContents(resolvedItems)
        if (contents.hasSelectedItem()) {
            val mutable = net.minecraft.world.item.component.BundleContents.Mutable(resolved)
            mutable.toggleSelectedItem(contents.selectedItem)
            resolved = mutable.toImmutable()
        }
        return resolved
    }

    private fun recoverCreativeBundleMutation(
        originalBytes: ByteArray,
        signature: ByteArray,
        received: ItemStack,
        player: org.bukkit.entity.Player?,
    ): PendingCreativeStack.BundleMutation? {
        if (NativeItemLifecycle.state != NativeItemState.Active) return null
        val authoritative = runCatching {
            CraftItemStack.asNMSCopy(Bukkit.getUnsafe().deserializeItem(originalBytes))
        }.getOrNull() ?: return null
        if (authoritative.get(DataComponents.BUNDLE_CONTENTS) == null) return null
        val expectedProjection = NativeItemNetworkContext.with(
            NativeItemNetworkCall(NetworkDirection.Clientbound, player),
        ) { encode(authoritative.copy()) }
        val expectedDisplayed = removeEnvelope(expectedProjection)
        val expectedSignature = sign(originalBytes, ItemStack.hashItemAndComponents(expectedDisplayed))
        if (!MessageDigest.isEqual(signature, expectedSignature)) return null
        if (!differsOnlyInBundleContents(expectedDisplayed, received)) return null
        val contents = received.get(DataComponents.BUNDLE_CONTENTS) ?: return null
        return PendingCreativeStack.BundleMutation(authoritative, contents)
    }

    private fun differsOnlyInBundleContents(expected: ItemStack, received: ItemStack): Boolean {
        if (expected.item !== received.item || expected.count != received.count) return false
        val normalized = received.copy()
        val expectedContents = expected.get(DataComponents.BUNDLE_CONTENTS)
        if (expectedContents == null) {
            normalized.remove(DataComponents.BUNDLE_CONTENTS)
        } else {
            normalized.set(DataComponents.BUNDLE_CONTENTS, expectedContents)
        }
        return ItemStack.isSameItemSameComponents(expected, normalized)
    }

    @JvmStatic
    public fun encodeHolder(holder: Holder<Item>): Holder<Item> {
        val call = NativeItemNetworkContext.current() ?: return holder
        if (call.direction != NetworkDirection.Clientbound) return holder
        val item = holder.value()
        if (item !is NativeBackedItem) return holder
        NativeItemLifecycle.requireActive()
        return item.backing.builtInRegistryHolder()
    }

    /** Rewrites item and block tag IDs to registry IDs known by an unmodified client. */
    @JvmStatic
    public fun projectTags(registry: Registry<*>, payload: TagNetworkSerialization.NetworkPayload) {
        if (registry.key() !in setOf(Registries.ITEM, Registries.BLOCK)) return
        if (registry.key() == Registries.ITEM) NativeItemLifecycle.requireActive()
        else NativeBlockLifecycle.requireActive()

        val tagsField = TagNetworkSerialization.NetworkPayload::class.java.getDeclaredField("tags")
        tagsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val tags = tagsField.get(payload) as MutableMap<net.minecraft.resources.Identifier, IntList>

        for (entry in tags.entries) {
            val projected = if (registry.key() == Registries.ITEM) {
                entry.value.intStream()
                    .map { numericId ->
                        val item = BuiltInRegistries.ITEM.byId(numericId)
                        if (item is NativeBackedItem) BuiltInRegistries.ITEM.getId(item.backing) else numericId
                    }
                    .distinct()
                    .toArray()
            } else {
                entry.value.toIntArray().flatMap { numericId ->
                    val block = BuiltInRegistries.BLOCK.byId(numericId)
                    NativeBlockClientBridge.projectTagBlock(block).map(BuiltInRegistries.BLOCK::getId)
                }
                    .distinct()
                    .toIntArray()
            }
            entry.setValue(IntArrayList(projected))
        }
    }

    private fun setEnvelope(stack: ItemStack, original: ByteArray, signature: ByteArray) {
        val root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        val envelope = CompoundTag().apply {
            putInt("version", Version)
            putByteArray("original", original)
            putByteArray("signature", signature)
        }
        root.put(EnvelopeKey, envelope)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root))
    }

    private fun readEnvelope(stack: ItemStack): CompoundTag? =
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompound(EnvelopeKey)?.orElse(null)

    private fun removeEnvelope(stack: ItemStack): ItemStack {
        val cleaned = stack.copy()
        val customData = cleaned.get(DataComponents.CUSTOM_DATA) ?: return cleaned
        val root = customData.copyTag()
        root.remove(EnvelopeKey)
        if (root.isEmpty) cleaned.remove(DataComponents.CUSTOM_DATA)
        else cleaned.set(DataComponents.CUSTOM_DATA, CustomData.of(root))
        return cleaned
    }

    private fun hasStoredItemAttachments(stack: ItemStack): Boolean =
        CraftItemStack.asCraftMirror(stack.copy()).hasStoredItemAttachments()

    private fun hasProtectedItemState(stack: ItemStack): Boolean {
        val bukkit = CraftItemStack.asCraftMirror(stack.copy())
        return bukkit.hasStoredItemAttachments() || bukkit.hasStoredItemMaterialization()
    }

    private fun removeProtectedItemState(stack: ItemStack): ItemStack {
        val bukkit = CraftItemStack.asCraftMirror(stack.copy())
        bukkit.clearStoredItemAttachments()
        bukkit.clearStoredItemMaterialization()
        return CraftItemStack.asNMSCopy(bukkit)
    }

    private fun sign(original: ByteArray, displayedHash: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        mac.update(original)
        mac.update(byteArrayOf(
            (displayedHash ushr 24).toByte(),
            (displayedHash ushr 16).toByte(),
            (displayedHash ushr 8).toByte(),
            displayedHash.toByte(),
        ))
        return mac.doFinal()
    }

    private sealed interface PendingCreativeStack {
        data class BundleMutation(
            val authoritative: ItemStack,
            val contents: net.minecraft.world.item.component.BundleContents,
        ) : PendingCreativeStack

        data object Rejected : PendingCreativeStack
    }
}

internal sealed interface CreativeSlotStackResolution {
    data class Accept(val stack: ItemStack) : CreativeSlotStackResolution

    data object Reject : CreativeSlotStackResolution
}
