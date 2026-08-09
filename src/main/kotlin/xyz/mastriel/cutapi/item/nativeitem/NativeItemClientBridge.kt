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
import org.bukkit.craftbukkit.inventory.CraftItemStack
import xyz.mastriel.cutapi.item.CuTItemStack
import xyz.mastriel.cutapi.item.CustomItem
import xyz.mastriel.cutapi.item.clearStoredItemAttachments
import xyz.mastriel.cutapi.item.clearStoredItemMaterialization
import xyz.mastriel.cutapi.item.hasStoredItemAttachments
import xyz.mastriel.cutapi.item.hasStoredItemMaterialization
import xyz.mastriel.cutapi.item.setAttachment
import xyz.mastriel.cutapi.item.attachments.DisplayAs
import xyz.mastriel.cutapi.item.attachments.Durability
import xyz.mastriel.cutapi.item.attachments.HideTooltip
import xyz.mastriel.cutapi.item.getAttachmentOrNull
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/** Converts native custom stacks at the stream-codec boundary. */
public object NativeItemClientBridge {
    private const val EnvelopeKey = "cutapi:network_item_v2"
    private const val Version = 2
    private val secret = Random.Default.nextBytes(32)
    private val componentProjectionCalls = AtomicLong()

    @JvmStatic
    public fun encode(stack: ItemStack): ItemStack {
        val call = NativeItemNetworkContext.current() ?: return stack
        if (call.direction != NetworkDirection.Clientbound || stack.isEmpty) return stack

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
        val projected = if (definition == null) {
            renderedNms
        } else {
            val displayedType = wrapped.getAttachmentOrNull<DisplayAs>()?.itemType ?: definition.backingItem
            renderedNms.transmuteCopy(NativeItemTypes.getMinecraft(displayedType))
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

            val expectedPresentation = CraftItemStack.asNMSCopy(expectedWrapper.getRenderedItemStack(null))
            val explicitModel = expectedPresentation.componentsPatch.get(DataComponents.ITEM_MODEL)
            val expectedModel = if (explicitModel == null) {
                ItemStack(expectedDisplayedItem).get(DataComponents.ITEM_MODEL)
            } else {
                explicitModel.orElse(null)
            }
            val actualModel = projected.get(DataComponents.ITEM_MODEL)
            check(actualModel == expectedModel) {
                "Client projection for ${definition.id} used item model $actualModel; expected $expectedModel."
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
        verifyUnattachedVanillaRenderRoundTrip()
        verifyTagProjection()
    }

    private fun verifyUnattachedVanillaRenderRoundTrip() {
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
        val expectedDisplayedItem = displayedItem(authoritative, definition)
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
            ids.intStream().anyMatch { BuiltInRegistries.ITEM.byId(it) is NativeCustomItem }
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
            return removeProtectedItemState(cleaned)
        }

        val originalBytes = envelope.getByteArray("original").orElse(null) ?: return reject()
        val signature = envelope.getByteArray("signature").orElse(null) ?: return reject()
        if (envelope.getIntOr("version", -1) != Version) return reject()
        if (!MessageDigest.isEqual(signature, sign(originalBytes, ItemStack.hashItemAndComponents(cleaned)))) return reject()

        if (NativeItemLifecycle.state != NativeItemState.Active) return reject()

        val restored = runCatching {
            CraftItemStack.asNMSCopy(Bukkit.getUnsafe().deserializeItem(originalBytes))
        }.getOrElse { return reject() }
        restored.count = stack.count
        return restored
    }

    @JvmStatic
    public fun encodeHolder(holder: Holder<Item>): Holder<Item> {
        val call = NativeItemNetworkContext.current() ?: return holder
        if (call.direction != NetworkDirection.Clientbound) return holder
        val item = holder.value()
        if (item !is NativeCustomItem) return holder
        NativeItemLifecycle.requireActive()
        return item.backing.builtInRegistryHolder()
    }

    /** Rewrites item tag IDs to the registry IDs known by an unmodified client. */
    @JvmStatic
    public fun projectTags(registry: Registry<*>, payload: TagNetworkSerialization.NetworkPayload) {
        if (registry.key() != Registries.ITEM) return
        NativeItemLifecycle.requireActive()

        val tagsField = TagNetworkSerialization.NetworkPayload::class.java.getDeclaredField("tags")
        tagsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val tags = tagsField.get(payload) as MutableMap<net.minecraft.resources.Identifier, IntList>

        for (entry in tags.entries) {
            val projected = entry.value.intStream()
                .map { numericId ->
                    val item = BuiltInRegistries.ITEM.byId(numericId)
                    if (item is NativeCustomItem) BuiltInRegistries.ITEM.getId(item.backing) else numericId
                }
                .distinct()
                .toArray()
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
}
