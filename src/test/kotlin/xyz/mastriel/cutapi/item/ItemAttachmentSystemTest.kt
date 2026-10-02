@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.item

import net.kyori.adventure.text.Component
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent
import io.papermc.paper.event.player.PlayerStopUsingItemEvent
import org.bukkit.*
import org.bukkit.block.*
import org.bukkit.entity.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.item.nativeitem.NativeItemTypes
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.system.*
import xyz.mastriel.cutapi.testing.*
import xyz.mastriel.cutapi.utils.chatTooltipPresentation
import kotlin.test.*

public class ItemAttachmentSystemTest : MockBukkitTest() {
    @BeforeTest
    public fun initializeItemFixtures() {
        ItemTestFixtures.initialize()
        itemSystemCalls.clear()
    }

    @Test
    public fun `descriptor attachments are available on custom items`() {
        assertEquals(TestItemAttachment(10), ItemWithAttachments.getAttachment(TestItemAttachment))
        assertEquals(
            listOf(
                TestRepeatableItemAttachment("descriptor"),
                TestRepeatableItemAttachment("extension-custom"),
            ),
            ItemWithAttachments.getAttachments(TestRepeatableItemAttachment)
        )
    }

    @Test
    public fun `vanilla and custom identities expose extension attachments`() {
        assertEquals(
            TestItemAttachment(12),
            ItemType.PAPER.asIdentity().getAttachment(TestItemAttachment),
        )
        assertEquals(
            TestItemAttachment(12),
            ItemType.BLAZE_ROD.asIdentity().getAttachment(TestItemAttachment),
        )
        assertEquals(
            listOf(
                TestRepeatableItemAttachment("descriptor"),
                TestRepeatableItemAttachment("extension-custom"),
            ),
            ItemWithAttachments.identity.getAttachments(TestRepeatableItemAttachment),
        )
    }

    @Test
    public fun `extension producers create independent attachment snapshots`() {
        intrinsicItemAttachmentCreations = 0

        val first = ItemType.PAPER.asIdentity().getAttachment(TestItemAttachment)
        val second = ItemType.PAPER.asIdentity().getAttachment(TestItemAttachment)

        assertEquals(2, intrinsicItemAttachmentCreations)
        assertEquals(first, second)
        assertNotSame(first, second)
    }

    @Test
    public fun `checking whether an attachment is intrinsic does not invoke its producer`() {
        intrinsicItemAttachmentCreations = 0

        assertTrue(ItemType.PAPER.asIdentity().hasAttachment(TestItemAttachment))

        assertEquals(0, intrinsicItemAttachmentCreations)
    }

    @Test
    public fun `stack overlays replace suppress and restore vanilla intrinsic attachments`() {
        val item = CuTItemStack.wrap(ItemStack(Material.PAPER))
        assertEquals(TestItemAttachment(12), item.getAttachment(TestItemAttachment))

        item.setAttachment(TestItemAttachment(24))
        assertEquals(TestItemAttachment(24), item.handle.wrap().getAttachment(TestItemAttachment))

        item.removeAttachment(TestItemAttachment)
        assertNull(item.handle.wrap().getAttachmentOrNull(TestItemAttachment))
        assertTrue(
            TestItemAttachment.id in AttachmentPdcStorage.read(
                item.handle.itemMeta.persistentDataContainer,
            ).suppressed,
        )
        val root = assertNotNull(
            item.handle.itemMeta.persistentDataContainer.get(
                id("cutapi:attachments").toNamespacedKey(),
                PersistentDataType.TAG_CONTAINER
            )
        )
        val schema = assertNotNull(
            root.get(TestItemAttachment.id.toNamespacedKey(), PersistentDataType.TAG_CONTAINER)
        )
        assertEquals(true, schema.get(id("cutapi:suppressed").toNamespacedKey(), PersistentDataType.BOOLEAN))
        assertTrue(
            assertNotNull(
                schema.get(id("cutapi:values").toNamespacedKey(), PersistentDataType.LIST.dataContainers())
            ).isEmpty()
        )

        item.setAttachment(TestItemAttachment(32))
        assertEquals(TestItemAttachment(32), item.handle.wrap().getAttachment(TestItemAttachment))
    }

    @Test
    public fun `removing a non-intrinsic attachment leaves no suppression marker`() {
        val item = CuTItemStack.wrap(ItemStack(Material.STONE))
        item.setAttachment(TestItemAttachment(24))

        item.removeAttachment(TestItemAttachment)

        assertNull(item.handle.wrap().getAttachmentOrNull(TestItemAttachment))
        assertFalse(item.handle.hasStoredItemAttachments())
        val state = AttachmentPdcStorage.read(item.handle.itemMeta.persistentDataContainer)
        assertTrue(state.attachments.isEmpty())
        assertTrue(state.suppressed.isEmpty())
    }

    @Test
    public fun `removing one non-intrinsic schema erases it while preserving other overlays`() {
        val item = CuTItemStack.wrap(ItemStack(Material.STONE))
        item.setAttachment(TestItemAttachment(24))
        item.addAttachment(TestRepeatableItemAttachment("remaining"))

        item.removeAttachment(TestItemAttachment)

        val state = AttachmentPdcStorage.read(item.handle.itemMeta.persistentDataContainer)
        assertEquals(listOf(TestRepeatableItemAttachment("remaining")), state.attachments)
        assertTrue(state.suppressed.isEmpty())
    }

    @Test
    public fun `setting a repeatable non-intrinsic attachment does not create suppression`() {
        val item = CuTItemStack.wrap(ItemStack(Material.STONE))

        item.setAttachment(TestRepeatableItemAttachment("dynamic"))

        val state = AttachmentPdcStorage.read(item.handle.itemMeta.persistentDataContainer)
        assertEquals(listOf(TestRepeatableItemAttachment("dynamic")), state.attachments)
        assertTrue(state.suppressed.isEmpty())
    }

    @Test
    public fun `repeatable dynamic additions compose with vanilla intrinsic attachments`() {
        val item = CuTItemStack.wrap(ItemStack(Material.PAPER))
        item.addAttachment(TestRepeatableItemAttachment("dynamic"))

        assertEquals(
            listOf(
                TestRepeatableItemAttachment("extension-vanilla"),
                TestRepeatableItemAttachment("dynamic"),
            ),
            item.handle.wrap().getAttachments(TestRepeatableItemAttachment),
        )

        item.setAttachment(TestRepeatableItemAttachment("replacement"))
        assertEquals(
            listOf(TestRepeatableItemAttachment("replacement")),
            item.handle.wrap().getAttachments(TestRepeatableItemAttachment),
        )
    }

    @Test
    public fun `Bukkit item stack attachment APIs delegate through universal wrapper`() {
        val stack = ItemStack(Material.STICK)

        stack.setAttachment(TestItemAttachment(44))
        assertTrue(stack.hasAttachment<TestItemAttachment>())
        assertEquals(TestItemAttachment(44), stack.getAttachment<TestItemAttachment>())

        stack.removeAttachment<TestItemAttachment>()
        assertNull(stack.getAttachmentOrNull<TestItemAttachment>())
    }

    @Test
    public fun `vanilla rendering appends intrinsic attachment lore without replacing vanilla lore`() {
        val stack = ItemStack(Material.PAPER).apply {
            editMeta { it.lore(listOf(Component.text("vanilla lore"))) }
        }

        val rendered = stack.wrap().getRenderedItemStack(null)
        val lore = assertNotNull(rendered.itemMeta.lore())

        assertEquals(3, lore.size)
        assertEquals(Component.text("vanilla lore"), lore.first())
        assertEquals(Component.text("general render"), lore.last())
    }

    @Test
    public fun `general render systems apply to vanilla stacks without attachments`() {
        val stack = ItemStack(Material.STONE)
        assertTrue(stack.getAllAttachments().isEmpty())

        val rendered = stack.wrap().getRenderedItemStack(null)

        assertEquals(listOf("general:render"), itemSystemCalls)
        assertEquals(listOf(Component.text("general render")), rendered.itemMeta.lore())
    }

    @Test
    public fun `chat tooltip presentation uses viewer-specific rendering without mutating the source`() {
        val viewer = server.addPlayer("TooltipViewer")
        val stack = ItemStack(Material.STICK).apply {
            setAttachment(TestItemAttachment(24))
        }

        val presentation = stack.chatTooltipPresentation(viewer)

        assertEquals(Component.text("Rendered for TooltipViewer"), presentation.name)
        assertEquals(Component.text("Rendered for TooltipViewer"), presentation.item.itemMeta.itemName())
        assertEquals(listOf(Component.text("general render")), presentation.item.itemMeta.lore())
        assertFalse(presentation.item.hasStoredItemAttachments())
        assertFalse(presentation.item.hasStoredItemMaterialization())
        assertFalse(stack.itemMeta.hasItemName())
        assertNull(stack.itemMeta.lore())
    }

    @Test
    public fun `late item identity extension commits fail`() {
        val late: DeferredRegistry<ItemIdentityExtension> =
            BasicDeferredRegistry(ItemIdentityExtension, RegistryPriority.Medium)
        late.registerItemIdentityExtension(
            id = id("test:late_identity_extension"),
            target = ItemType.DIRT,
        ) {
            attach { TestItemAttachment(1) }
        }

        assertFailsWith<IllegalStateException> { late.commitToRegistry() }
    }

    @Test
    public fun `stack attachments replace and remove values persistently for vanilla stacks`() {
        val item = vanillaStack()

        item.setAttachment(TestItemAttachment(24))
        assertEquals(TestItemAttachment(24), item.getAttachment(TestItemAttachment))

        val rewrapped = item.handle.wrap()
        assertEquals(TestItemAttachment(24), rewrapped.getAttachment(TestItemAttachment))

        rewrapped.removeAttachment(TestItemAttachment)

        val afterRemoval = rewrapped.handle.wrap()
        assertFalse(afterRemoval.hasAttachment(TestItemAttachment))
        assertNull(afterRemoval.getAttachmentOrNull(TestItemAttachment))

        afterRemoval.setAttachment(TestItemAttachment(32))
        assertEquals(TestItemAttachment(32), afterRemoval.getAttachment(TestItemAttachment))
    }

    @Test
    public fun `item attachments use direct native fields inside the values list`() {
        val item = vanillaStack()

        item.setAttachment(TestItemAttachment(24))

        val root = assertNotNull(
            item.handle.itemMeta.persistentDataContainer.get(
                id("cutapi:attachments").toNamespacedKey(),
                PersistentDataType.TAG_CONTAINER
            )
        )
        val schema = assertNotNull(
            root.get(TestItemAttachment.id.toNamespacedKey(), PersistentDataType.TAG_CONTAINER)
        )
        val values = assertNotNull(
            schema.get(id("cutapi:values").toNamespacedKey(), PersistentDataType.LIST.dataContainers())
        )
        val attachment = values.single()

        assertEquals(24, attachment.get(id("test:amount").toNamespacedKey(), PersistentDataType.INTEGER))
        assertEquals(setOf(id("test:amount").toNamespacedKey()), attachment.keys)
    }

    @Test
    public fun `unversioned and unsupported attachment data is treated as empty`() {
        val item = vanillaStack()
        val holder = item.handle.itemMeta.persistentDataContainer
        val legacy = holder.adapterContext.newPersistentDataContainer().apply {
            set(id("cutapi:count").toNamespacedKey(), PersistentDataType.INTEGER, 1)
        }
        holder.set(id("cutapi:attachments").toNamespacedKey(), PersistentDataType.TAG_CONTAINER, legacy)

        assertFalse(AttachmentPdcStorage.has(holder))
        assertTrue(AttachmentPdcStorage.read(holder).attachments.isEmpty())
        assertTrue(AttachmentPdcStorage.read(holder).suppressed.isEmpty())

        legacy.set(id("cutapi:format_version").toNamespacedKey(), PersistentDataType.INTEGER, 2)
        holder.set(id("cutapi:attachments").toNamespacedKey(), PersistentDataType.TAG_CONTAINER, legacy)

        assertFalse(AttachmentPdcStorage.has(holder))
        assertEquals(PersistentAttachmentState(emptyList()), AttachmentPdcStorage.read(holder))
    }

    @Test
    public fun `malformed current format entries are isolated from valid attachments`() {
        val item = vanillaStack()
        val holder = item.handle.itemMeta.persistentDataContainer
        AttachmentPdcStorage.write(
            holder,
            listOf(TestItemAttachment(1), TestRepeatableItemAttachment("valid"))
        )
        val root = assertNotNull(
            holder.get(id("cutapi:attachments").toNamespacedKey(), PersistentDataType.TAG_CONTAINER)
        )
        val malformedSchema = assertNotNull(
            root.get(TestItemAttachment.id.toNamespacedKey(), PersistentDataType.TAG_CONTAINER)
        )
        val malformedValue = holder.adapterContext.newPersistentDataContainer().apply {
            set(id("test:amount").toNamespacedKey(), PersistentDataType.STRING, "wrong")
        }
        malformedSchema.set(
            id("cutapi:values").toNamespacedKey(),
            PersistentDataType.LIST.dataContainers(),
            listOf(malformedValue)
        )
        root.set(TestItemAttachment.id.toNamespacedKey(), PersistentDataType.TAG_CONTAINER, malformedSchema)
        holder.set(id("cutapi:attachments").toNamespacedKey(), PersistentDataType.TAG_CONTAINER, root)

        val state = AttachmentPdcStorage.read(holder)

        assertEquals(listOf(TestRepeatableItemAttachment("valid")), state.attachments)
        assertTrue(state.suppressed.isEmpty())
    }

    @Test
    public fun `repeatable stack attachments compose and can be removed on vanilla stacks`() {
        val item = vanillaStack()

        item.addAttachment(TestRepeatableItemAttachment("first"))
        item.addAttachment(TestRepeatableItemAttachment("second"))

        assertEquals(
            listOf(
                TestRepeatableItemAttachment("first"),
                TestRepeatableItemAttachment("second")
            ),
            item.handle.wrap().getAttachments(TestRepeatableItemAttachment)
        )

        item.removeAttachment(TestRepeatableItemAttachment)

        assertTrue(item.handle.wrap().getAttachments(TestRepeatableItemAttachment).isEmpty())
    }

    @Test
    public fun `descriptor rejects duplicate non-repeatable attachments`() {
        val builder = ItemDescriptorBuilder()
        builder.attach(TestItemAttachment(1))

        assertFailsWith<IllegalStateException> {
            builder.attach(TestItemAttachment(2))
        }

        builder.attach(
            TestRepeatableItemAttachment("first"),
            TestRepeatableItemAttachment("second")
        )
        assertEquals(3, builder.attachments.size)
    }

    @Test
    public fun `item system context resolves attachments and isolates attachment data`() {
        val item = vanillaStack().also { it.setAttachment(TestItemAttachment(10)) }
        val context = ItemSystemContext(item)
        val key = id("test:item_system/counter")

        assertEquals(TestItemAttachment(10), context.attachment(TestItemAttachment))

        context.data(TestItemAttachment).setInt(key, 4)

        val rewrappedContext = ItemSystemContext(item.handle.wrap())
        assertEquals(4, rewrappedContext.data(TestItemAttachment).getInt(key))
        assertNull(rewrappedContext.data(TestRepeatableItemAttachment).getInt(key))
    }

    @Test
    public fun `item systems filter by attachments in ascending priority order`() {
        val plainItem = vanillaStack()
        assertEquals(listOf(GeneralTestItemSystem), ItemSystem.applicableTo(plainItem))

        val attachedItem = vanillaStack().also { it.setAttachment(TestItemAttachment(10)) }
        assertEquals(
            listOf(GeneralTestItemSystem, AttachedTestItemSystem),
            ItemSystem.applicableTo(attachedItem)
        )
    }

    @Test
    public fun `right click events dispatch only applicable item systems`() {
        val player = server.addPlayer()
        val item = vanillaStack().also { it.setAttachment(TestItemAttachment(10)) }
        player.inventory.setItemInMainHand(item.handle)
        itemSystemCalls.clear()

        ItemSystemEvents().onInteract(
            PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_AIR,
                item.handle,
                null,
                BlockFace.SELF,
                EquipmentSlot.HAND
            )
        )

        assertEquals(listOf("general:right_click", "attached:right_click"), itemSystemCalls)
    }

    @Test
    public fun `consume dispatch selects the consumed item and propagates cancellation`() {
        val player = server.addPlayer()
        val item = vanillaStack().also { it.setAttachment(TestItemAttachment(10)) }
        player.inventory.setItemInMainHand(ItemStack(Material.DIRT))
        val event = PlayerItemConsumeEvent(player, item.handle, EquipmentSlot.HAND)
        itemSystemCalls.clear()

        ItemSystemEvents().onConsume(event)

        assertEquals(listOf("general:consume", "attached:consume"), itemSystemCalls)
        assertTrue(event.isCancelled)
    }

    @Test
    public fun `projectile launch dispatch selects the launch item and propagates cancellation`() {
        val player = server.addPlayer()
        val item = vanillaStack().also { it.setAttachment(TestItemAttachment(10)) }
        val projectile = player.world.spawnEntity(player.location, EntityType.SNOWBALL) as Projectile
        val event = PlayerLaunchProjectileEvent(player, item.handle, projectile)
        itemSystemCalls.clear()

        ItemSystemEvents().onLaunchProjectile(event)

        assertEquals(listOf("general:launch", "attached:launch"), itemSystemCalls)
        assertTrue(event.isCancelled)
    }

    @Test
    public fun `fishing dispatch selects the event hand item and propagates cancellation`() {
        val player = server.addPlayer()
        val item = vanillaStack().also { it.setAttachment(TestItemAttachment(10)) }
        player.inventory.setItemInMainHand(item.handle)
        val hook = player.world.spawnEntity(player.location, EntityType.FISHING_BOBBER) as FishHook
        val event = PlayerFishEvent(player, null, hook, EquipmentSlot.HAND, PlayerFishEvent.State.FISHING)
        itemSystemCalls.clear()

        ItemSystemEvents().onFish(event)

        assertEquals(listOf("general:fish", "attached:fish"), itemSystemCalls)
        assertTrue(event.isCancelled)
    }

    @Test
    public fun `bow dispatch selects the event bow and propagates cancellation`() {
        val player = server.addPlayer()
        val item = vanillaStack().also { it.setAttachment(TestItemAttachment(10)) }
        val projectile = player.world.spawnEntity(player.location, EntityType.ARROW)
        val event = EntityShootBowEvent(player, item.handle, projectile, 1f)
        itemSystemCalls.clear()

        ItemSystemEvents().onShootBow(event)

        assertEquals(listOf("general:shoot_bow", "attached:shoot_bow"), itemSystemCalls)
        assertTrue(event.isCancelled)
    }

    @Test
    public fun `stop using dispatch selects the stopped item and requires its attachment`() {
        val player = server.addPlayer()
        val attached = vanillaStack().also { it.setAttachment(TestItemAttachment(10)) }
        itemSystemCalls.clear()

        ItemSystemEvents().onStopUsing(PlayerStopUsingItemEvent(player, attached.handle, 4))
        ItemSystemEvents().onStopUsing(PlayerStopUsingItemEvent(player, ItemStack(Material.DIRT), 4))

        assertEquals(
            listOf("general:stop_using", "attached:stop_using", "general:stop_using"),
            itemSystemCalls,
        )
    }

    @Test
    public fun `riptide dispatch selects the riptide item and propagates cancellation`() {
        val player = server.addPlayer()
        val item = vanillaStack().also { it.setAttachment(TestItemAttachment(10)) }
        val event = PlayerRiptideEvent(player, item.handle)
        itemSystemCalls.clear()

        ItemSystemEvents().onRiptide(event)

        assertEquals(listOf("general:riptide", "attached:riptide"), itemSystemCalls)
        assertTrue(event.isCancelled)
    }

    @Test
    public fun `empty stacks wrap safely and do not dispatch item systems`() {
        val empty = CuTItemStack.wrap(ItemStack(Material.AIR))

        assertIs<ItemIdentity.Vanilla>(empty.identity)
        assertEquals(ItemType.AIR, empty.itemType)
        assertTrue(empty.getAllAttachments().isEmpty())
        assertFailsWith<IllegalArgumentException> {
            empty.setAttachment(TestItemAttachment(1))
        }

        val player = server.addPlayer()
        player.inventory.setItemInMainHand(empty.handle)
        itemSystemCalls.clear()
        ItemSystemEvents().onInteract(
            PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_AIR,
                empty.handle,
                null,
                BlockFace.SELF,
                EquipmentSlot.HAND
            )
        )
        assertTrue(itemSystemCalls.isEmpty())
    }

    @Test
    public fun `item identity represents vanilla and custom item kinds`() {
        val vanilla = ItemType.STONE.asIdentity()
        assertIs<ItemIdentity.Vanilla>(vanilla)
        assertEquals(ItemType.STONE, vanilla.itemType)
        assertEquals(ItemType.STONE, vanilla.backingItem)
        assertFalse(vanilla.isCustom)

        val custom = ItemWithAttachments.asIdentity()
        assertIs<ItemIdentity.Custom>(custom)
        assertSame(ItemWithAttachments, custom.item)
        assertEquals(ItemType.STICK, custom.backingItem)
        assertTrue(custom.isCustom)
    }

    @Test
    public fun `items sharing a backing item keep distinct identities`() {
        val first = customItem(id("test:first_identity"), ItemType.PAPER)
        val second = customItem(id("test:second_identity"), ItemType.PAPER)

        assertNotEquals(first.asIdentity(), second.asIdentity())
        assertEquals(first.backingItem, second.backingItem)
    }

    @Test
    public fun `descriptor producers run independently for each item`() {
        var invocations = 0
        val producer = {
            invocations++
            defaultItemDescriptor()
        }

        val first = customItemFromDescriptor(id("test:first_descriptor"), ItemType.PAPER, producer)
        val second = customItemFromDescriptor(id("test:second_descriptor"), ItemType.PAPER, producer)

        assertEquals(2, invocations)
        assertNotSame(first.descriptor, second.descriptor)
    }
}

private fun vanillaStack(): CuTItemStack = CuTItemStack.wrap(ItemStack(Material.STICK))

internal data class TestItemAttachment(val amount: Int = 0) : ItemAttachment {
    companion object : Schema<TestItemAttachment> by schema(id("test:item_attachment"), {
        property(TestItemAttachment::amount, VariantSerializer.Int)
    })
}

@RepeatableAttachment
internal data class TestRepeatableItemAttachment(val value: String = "") : ItemAttachment {
    companion object : Schema<TestRepeatableItemAttachment> by schema(id("test:repeatable_item_attachment"), {
        property(TestRepeatableItemAttachment::value, VariantSerializer.String)
    })
}

internal data class TestItemLoreAttachment(val value: String = "") : ItemLoreAttachment {
    companion object : Schema<TestItemLoreAttachment> by schema(id("test:item_lore_attachment"), {
        property(TestItemLoreAttachment::value, VariantSerializer.String)
    })

    override fun getLore(item: CuTItemStack, viewer: org.bukkit.entity.Player?): Component =
        Component.text(value)
}

private val ItemWithAttachments: CustomItem<CuTItemStack> =
    customItem(id("test:item_with_attachments"), ItemType.STICK) {
        noDisplay()
        attach(TestItemAttachment(10))
        attach(TestRepeatableItemAttachment("descriptor"))
    }

private var intrinsicItemAttachmentCreations: Int = 0

private val TestIdentityExtensions: DeferredRegistry<ItemIdentityExtension> = ItemIdentityExtension.defer()

private val VanillaIdentityExtension: ItemIdentityExtension by TestIdentityExtensions.registerItemIdentityExtension(
    id = id("test:vanilla_identity_extension"),
    targets = setOf(
        ItemType.PAPER.asIdentity(),
        ItemType.BLAZE_ROD.asIdentity(),
    ),
) {
    attach {
        intrinsicItemAttachmentCreations++
        TestItemAttachment(12)
    }
    attach { TestRepeatableItemAttachment("extension-vanilla") }
    attach { TestItemLoreAttachment("extension lore") }
}

private val CustomIdentityExtension: ItemIdentityExtension by TestIdentityExtensions.registerItemIdentityExtension(
    id = id("test:custom_identity_extension"),
    target = ItemWithAttachments,
) {
    attach { TestRepeatableItemAttachment("extension-custom") }
}

private val itemSystemCalls: MutableList<String> = mutableListOf()

private object GeneralTestItemSystem : ItemSystem {
    override val id: Identifier = id("test:item_system/general")
    override val priority: RegistryPriority = RegistryPriority.Low

    override fun prerequisite(target: CuTItemStack): Boolean = true

    override fun onRightClick(context: ItemInteractContext) {
        itemSystemCalls += "general:right_click"
    }

    override fun onConsume(context: ItemConsumeContext) { itemSystemCalls += "general:consume" }
    override fun onLaunchProjectile(context: ItemLaunchProjectileContext) { itemSystemCalls += "general:launch" }
    override fun onFish(context: ItemFishContext) { itemSystemCalls += "general:fish" }
    override fun onShootBow(context: ItemShootBowContext) { itemSystemCalls += "general:shoot_bow" }
    override fun onStopUsing(context: ItemStopUsingContext) { itemSystemCalls += "general:stop_using" }
    override fun onRiptide(context: ItemRiptideContext) { itemSystemCalls += "general:riptide" }

    override fun onRender(context: ItemRenderContext) {
        itemSystemCalls += "general:render"
        context.item.handle.editMeta { meta ->
            meta.lore(meta.lore().orEmpty() + Component.text("general render"))
        }
    }
}

private object AttachedTestItemSystem : ItemSystem {
    override val id: Identifier = id("test:item_system/attached")
    override val priority: RegistryPriority = RegistryPriority.High

    override fun prerequisite(target: CuTItemStack): Boolean =
        target.hasAttachment(TestItemAttachment)

    override fun onRender(context: ItemRenderContext) {
        context.item.handle.editMeta { meta ->
            meta.itemName(Component.text("Rendered for ${context.viewer?.name ?: "default"}"))
        }
    }

    override fun onRightClick(context: ItemInteractContext) {
        itemSystemCalls += "attached:right_click"
    }

    override fun onConsume(context: ItemConsumeContext) {
        itemSystemCalls += "attached:consume"
        context.event.isCancelled = true
    }

    override fun onLaunchProjectile(context: ItemLaunchProjectileContext) {
        itemSystemCalls += "attached:launch"
        context.event.isCancelled = true
    }

    override fun onFish(context: ItemFishContext) {
        itemSystemCalls += "attached:fish"
        context.event.isCancelled = true
    }

    override fun onShootBow(context: ItemShootBowContext) {
        itemSystemCalls += "attached:shoot_bow"
        context.event.isCancelled = true
    }

    override fun onStopUsing(context: ItemStopUsingContext) {
        itemSystemCalls += "attached:stop_using"
    }

    override fun onRiptide(context: ItemRiptideContext) {
        itemSystemCalls += "attached:riptide"
        context.event.isCancelled = true
    }
}

private object ItemTestFixtures {
    private var initialized: Boolean = false

    fun initialize() {
        if (initialized) return

        Schema.registerSchema(TestItemAttachment)
        Schema.registerSchema(TestRepeatableItemAttachment)
        Schema.registerSchema(TestItemLoreAttachment)
        CuTItemStack.registerType(
            id("cutapi:builtin"),
            CuTItemStack::class,
            CuTItemStack.CONSTRUCTOR
        )
        NativeItemTypes.stackResolverForTests = { stack ->
            stack.type.asItemType() ?: error("${stack.type} has no ItemType")
        }
        CustomItem.modifyRegistry {
            register(ItemWithAttachments)
        }
        TestIdentityExtensions.commitToRegistry()
        ItemSystem.modifyRegistry {
            register(GeneralTestItemSystem)
            register(AttachedTestItemSystem)
        }
        CustomItem.initialize()
        ItemIdentityExtension.initialize()
        ItemAttachmentMaterializer.initialize()
        ItemSystem.initialize()

        initialized = true
    }
}
