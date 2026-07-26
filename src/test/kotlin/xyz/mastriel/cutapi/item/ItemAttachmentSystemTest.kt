package xyz.mastriel.cutapi.item

import org.bukkit.*
import org.bukkit.block.*
import org.bukkit.event.block.*
import org.bukkit.event.player.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.ItemStackUtility.wrap
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.system.*
import xyz.mastriel.cutapi.testing.*
import kotlin.test.*

public class ItemAttachmentSystemTest : MockBukkitTest() {
    @BeforeTest
    public fun initializeItemFixtures() {
        ItemTestFixtures.initialize()
        itemSystemCalls.clear()
    }

    @Test
    public fun `descriptor attachments are available on custom items and stacks`() {
        assertEquals(TestItemAttachment(10), ItemWithAttachments.getAttachment(TestItemAttachment))

        val item = ItemWithAttachments.createItemStack()

        assertTrue(item.hasAttachment(TestItemAttachment))
        assertEquals(TestItemAttachment(10), item.getAttachment(TestItemAttachment))
        assertEquals(
            listOf(TestRepeatableItemAttachment("descriptor")),
            item.getAttachments(TestRepeatableItemAttachment)
        )
    }

    @Test
    public fun `stack attachments override and suppress descriptor defaults persistently`() {
        val item = ItemWithAttachments.createItemStack()

        item.setAttachment(TestItemAttachment(24))
        assertEquals(TestItemAttachment(24), item.getAttachment(TestItemAttachment))

        val rewrapped = item.handle.wrap()
        assertNotNull(rewrapped)
        assertEquals(TestItemAttachment(24), rewrapped.getAttachment(TestItemAttachment))

        rewrapped.removeAttachment(TestItemAttachment)

        val afterRemoval = rewrapped.handle.wrap()
        assertNotNull(afterRemoval)
        assertFalse(afterRemoval.hasAttachment(TestItemAttachment))
        assertNull(afterRemoval.getAttachmentOrNull(TestItemAttachment))

        afterRemoval.setAttachment(TestItemAttachment(32))
        assertEquals(TestItemAttachment(32), afterRemoval.getAttachment(TestItemAttachment))
    }

    @Test
    public fun `repeatable stack attachments compose with defaults and can be suppressed`() {
        val item = ItemWithAttachments.createItemStack()

        item.addAttachment(TestRepeatableItemAttachment("first"))
        item.addAttachment(TestRepeatableItemAttachment("second"))

        assertEquals(
            listOf(
                TestRepeatableItemAttachment("descriptor"),
                TestRepeatableItemAttachment("first"),
                TestRepeatableItemAttachment("second")
            ),
            item.handle.wrap()!!.getAttachments(TestRepeatableItemAttachment)
        )

        item.removeAttachment(TestRepeatableItemAttachment)

        assertTrue(item.handle.wrap()!!.getAttachments(TestRepeatableItemAttachment).isEmpty())
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
        val item = ItemWithAttachments.createItemStack()
        val context = ItemSystemContext(item)
        val key = id("test:item_system/counter")

        assertEquals(TestItemAttachment(10), context.attachment(TestItemAttachment))

        context.data(TestItemAttachment).setInt(key, 4)

        val rewrappedContext = ItemSystemContext(item.handle.wrap()!!)
        assertEquals(4, rewrappedContext.data(TestItemAttachment).getInt(key))
        assertNull(rewrappedContext.data(TestRepeatableItemAttachment).getInt(key))
    }

    @Test
    public fun `item systems filter by attachments and dispatch in priority order`() {
        ItemWithoutAttachments.createItemStack()
        assertEquals(listOf("general:create"), itemSystemCalls)

        itemSystemCalls.clear()
        val attachedItem = ItemWithAttachments.createItemStack()

        assertEquals(listOf("attached:create:10", "general:create"), itemSystemCalls)
        assertEquals(
            listOf(AttachedTestItemSystem, GeneralTestItemSystem),
            ItemSystem.applicableTo(attachedItem)
        )
    }

    @Test
    public fun `right click events dispatch only applicable item systems`() {
        val player = server.addPlayer()
        val item = ItemWithAttachments.createItemStack()
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

        assertEquals(listOf("attached:right_click", "general:right_click"), itemSystemCalls)
    }
}

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

private val ItemWithAttachments: CustomItem<CuTItemStack> =
    customItem(id("test:item_with_attachments"), Material.STICK) {
        noDisplay()
        attach(TestItemAttachment(10))
        attach(TestRepeatableItemAttachment("descriptor"))
    }

private val ItemWithoutAttachments: CustomItem<CuTItemStack> =
    customItem(id("test:item_without_attachments"), Material.PAPER) {
        noDisplay()
    }

private val itemSystemCalls: MutableList<String> = mutableListOf()

private object GeneralTestItemSystem : ItemSystem {
    override val id: Identifier = id("test:item_system/general")
    override val priority: RegistryPriority = RegistryPriority.Low

    override fun prerequisite(target: CuTItemStack): Boolean = true

    override fun onCreate(context: ItemCreateContext) {
        itemSystemCalls += "general:create"
    }

    override fun onRightClick(context: ItemInteractContext) {
        itemSystemCalls += "general:right_click"
    }
}

private object AttachedTestItemSystem : ItemSystem {
    override val id: Identifier = id("test:item_system/attached")
    override val priority: RegistryPriority = RegistryPriority.High

    override fun prerequisite(target: CuTItemStack): Boolean =
        target.hasAttachment(TestItemAttachment)

    override fun onCreate(context: ItemCreateContext) {
        itemSystemCalls += "attached:create:${context.attachment(TestItemAttachment).amount}"
    }

    override fun onRightClick(context: ItemInteractContext) {
        itemSystemCalls += "attached:right_click"
    }
}

private object ItemTestFixtures {
    private var initialized: Boolean = false

    fun initialize() {
        if (initialized) return

        Schema.registerSchema(TestItemAttachment)
        Schema.registerSchema(TestRepeatableItemAttachment)
        CuTItemStack.registerType(
            ItemStackUtility.DefaultItemStackTypeId,
            CuTItemStack::class,
            CuTItemStack.CONSTRUCTOR
        )
        CustomItem.modifyRegistry {
            register(ItemWithAttachments)
            register(ItemWithoutAttachments)
        }
        ItemSystem.modifyRegistry {
            register(GeneralTestItemSystem)
            register(AttachedTestItemSystem)
        }
        CustomItem.initialize()
        ItemSystem.initialize()

        initialized = true
    }
}
