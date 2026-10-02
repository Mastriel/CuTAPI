package xyz.mastriel.cutapi.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.inventory.*
import org.bukkit.inventory.*
import org.mockbukkit.mockbukkit.*
import org.mockbukkit.mockbukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import java.io.*
import kotlin.test.*

public class GuiDefinitionStateTest {
    private lateinit var server: ServerMock

    @BeforeTest
    public fun setUp() {
        GuiOverlayCatalog.resetForTests()
        server = MockBukkit.mock()
    }

    @AfterTest
    public fun tearDown() {
        MockBukkit.unmock()
        GuiOverlayCatalog.resetForTests()
    }

    @Test
    public fun `ordinary GUI definitions are immutable unregistered values`() {
        val identifier = id("test:same_gui")
        val first = gui(identifier, TestGuiType) {
            slot(0) { item { null } }
        }
        val second = gui(identifier, TestGuiType) {
            slot(1) { item { null } }
        }

        assertNotSame(first, second)
        assertEquals(identifier, first.id)
        assertEquals(listOf(GuiSlot.Top(0)), first.managedSlots.toList())
        assertEquals(listOf(GuiSlot.Top(1)), second.managedSlots.toList())
    }

    @Test
    public fun `typed open context reaches state rendering lifecycle and session backings`() {
        val port = GuiSlotPort("context-port")
        val observed = mutableListOf<Pair<String, TestOpenContext>>()
        val definition: GuiDefinition<TestOpenContext, InventoryView> = gui(
            id = id("test:typed_context"),
            type = OpeningTestGuiType,
            context = guiContext<TestOpenContext>(),
        ) {
            var initialized by state<Int> {
                observed += "state" to context
                context.initialValue
            }

            boundSlot(port, 0) {
                sessionBacking {
                    observed += "backing" to context
                    null
                }
                placeholder {
                    observed += "render" to context
                    ItemStack(org.bukkit.Material.PAPER, initialized)
                }
                onSlotUpdate {
                    observed += "update" to context
                }
            }

            onOpen {
                observed += "open" to context
                port(port).setItem(ItemStack(org.bukkit.Material.COAL))
            }
        }
        val supplied = TestOpenContext("typed", 3)
        val manager = GuiManager()
        val result = assertIs<GuiOpenResult.Opened>(
            manager.open(server.addPlayer("context-viewer"), definition, supplied),
        )

        assertSame(supplied, result.session.context)
        assertEquals(
            listOf("backing", "state", "render", "open", "update"),
            observed.map { it.first },
        )
        assertTrue(observed.all { (_, context) -> context === supplied })
    }

    @Test
    public fun `typed context reaches open-time backing producers`() {
        val port = GuiSlotPort("contextual-external")
        val definition = gui(
            id = id("test:typed_open_binding"),
            type = OpeningTestGuiType,
            context = guiContext<TestOpenContext>(),
        ) {
            boundSlot(port, 0) {
                sessionBacking { error("The open-time binding must take precedence.") }
            }
        }
        val supplied = TestOpenContext("external", 8)
        val backing = MutableGuiSlotBacking { ItemStack(org.bukkit.Material.DIAMOND) }
        var observed: TestOpenContext? = null

        val result = GuiManager().open(
            server.addPlayer("context-binding-viewer"),
            definition,
            supplied,
        ) {
            bind(port) {
                observed = context
                backing
            }
        }

        assertIs<GuiOpenResult.Opened>(result)
        assertSame(supplied, observed)
        assertSame(backing, result.session.port(port).resolved.backing)
    }

    @Test
    public fun `later declarations overwrite the same physical slot`() {
        val definition = gui(id("test:physical_override"), TestGuiType) {
            slot(GuiSlot.Top(0)) {
                key = "first"
                item { ItemStack(org.bukkit.Material.STONE) }
            }
            slot(GuiSlot.Raw(0)) {
                key = "replacement"
                item { ItemStack(org.bukkit.Material.GOLD_INGOT) }
            }
        }

        val element = definition.elements.single()
        assertEquals(GuiSlot.Raw(0), element.slot)
        assertEquals("replacement", element.key)
        assertEquals(
            org.bukkit.Material.GOLD_INGOT,
            element.item!!.invoke(GuiRenderContext(guiSession(definition)))!!.type,
        )
    }

    @Test
    public fun `bound slots expose opaque named ports and render placeholders standalone`() {
        val inputPort = GuiSlotPort("input")
        val definition = gui(id("test:bound_placeholder"), TestGuiType) {
            boundSlot(inputPort, slot = 0) {
                placeholder { ItemStack(org.bukkit.Material.IRON_NUGGET) }
            }
        }

        val element = definition.element(inputPort)
        assertNotNull(element)
        assertEquals(setOf(inputPort), definition.slotPorts)
        assertEquals(setOf(inputPort), definition.requiredSlotPorts)
        assertEquals(GuiSlot.Top(0), element.slot)
        assertEquals(
            org.bukkit.Material.IRON_NUGGET,
            element.item!!.invoke(GuiRenderContext(guiSession(definition)))!!.type,
        )
        assertEquals(inputPort, GuiSlotPort("input"))
    }

    @Test
    public fun `bound slots declare player access overrides and slot update hooks`() {
        val port = GuiSlotPort("take_only")
        val definition = gui(id("test:bound_access"), TestGuiType) {
            boundSlot(port, 0) {
                playerAccess = GuiBoundSlotAccess.ExtractOnly
                onSlotUpdate {
                    block.location
                    viewer.uniqueId
                    previous?.amount
                    item?.amount
                    source
                }
            }
        }

        val element = assertNotNull(definition.element(port))
        assertEquals(GuiBoundSlotAccess.ExtractOnly, element.boundSlotAccess)
        assertEquals(1, element.slotUpdateHandlers.size)
    }

    @Test
    public fun `session backings are created independently and retain defensive item snapshots`() {
        val port = GuiSlotPort("input")
        var initializations = 0
        val definition = gui(id("test:session_backing"), TestGuiType) {
            boundSlot(port, 0) {
                sessionBacking {
                    initializations += 1
                    ItemStack(org.bukkit.Material.COAL, initializations)
                }
            }
        }
        val manager = GuiManager()
        val firstPlayer = server.addPlayer("first-backing")
        val secondPlayer = server.addPlayer("second-backing")
        val first = manager.resolvePorts(
            firstPlayer,
            definition,
            Unit,
            GuiSubject.Standalone,
            presentation = null,
            openBindings = emptyMap(),
        ).single().backing
        val second = manager.resolvePorts(
            secondPlayer,
            definition,
            Unit,
            GuiSubject.Standalone,
            presentation = null,
            openBindings = emptyMap(),
        ).single().backing

        assertNotNull(first)
        assertNotNull(second)
        assertNotSame(first, second)
        assertEquals(1, first.item?.amount)
        assertEquals(2, second.item?.amount)
        first.item?.amount = 32
        assertEquals(1, first.item?.amount)
    }

    @Test
    public fun `open bindings override a declared session backing`() {
        val port = GuiSlotPort("external")
        var sessionInitializations = 0
        val definition = gui(id("test:open_backing"), TestGuiType) {
            boundSlot(port, 0) {
                sessionBacking {
                    sessionInitializations += 1
                    ItemStack(org.bukkit.Material.COAL)
                }
            }
        }
        val external = MutableGuiSlotBacking { ItemStack(org.bukkit.Material.GOLD_INGOT, 3) }
        val open = GuiOpenBuilder(definition).apply {
            bind(port) { external }
        }
        val resolved = GuiManager().resolvePorts(
            server.addPlayer("externally-backed"),
            definition,
            Unit,
            GuiSubject.Standalone,
            presentation = null,
            openBindings = open.bindings,
        ).single()

        assertSame(external, resolved.backing)
        assertEquals(0, sessionInitializations)
        assertEquals(3, resolved.backing?.item?.amount)
    }

    @Test
    public fun `shared external backings notify every subscribed session and unsubscribe on close`() {
        val port = GuiSlotPort("shared-external")
        val notifiedViewers = mutableListOf<String>()
        val definition = gui(id("test:shared_external_backing"), TestGuiType) {
            boundSlot(port, 0) {
                onSlotUpdate { notifiedViewers += viewer.name }
            }
        }
        val backing = MutableGuiSlotBacking { null }
        val manager = GuiManager()
        val firstPlayer = server.addPlayer("first-subscriber")
        val secondPlayer = server.addPlayer("second-subscriber")
        val open = GuiOpenBuilder(definition).apply { bind(port) { backing } }
        val first = GuiSession(definition, firstPlayer, GuiSubject.Standalone, Unit, manager).also { session ->
            session.installPorts(
                manager.resolvePorts(firstPlayer, definition, Unit, GuiSubject.Standalone, null, open.bindings),
            )
            session.initializeState()
        }
        val second = GuiSession(definition, secondPlayer, GuiSubject.Standalone, Unit, manager).also { session ->
            session.installPorts(
                manager.resolvePorts(secondPlayer, definition, Unit, GuiSubject.Standalone, null, open.bindings),
            )
            session.initializeState()
        }

        backing.setItem(ItemStack(org.bukkit.Material.DIAMOND))
        assertEquals(listOf("first-subscriber", "second-subscriber"), notifiedViewers)

        first.markClosed(GuiCloseReason.Programmatic)
        notifiedViewers.clear()
        backing.setItem(ItemStack(org.bukkit.Material.EMERALD))
        assertEquals(listOf("second-subscriber"), notifiedViewers)
        second.markClosed(GuiCloseReason.Programmatic)
    }

    @Test
    public fun `programmatic port handles enforce predicates and publish generic updates`() {
        val port = GuiSlotPort("programmatic")
        val updates = mutableListOf<Pair<GuiSlotUpdateSource, Int?>>()
        val definition = gui(id("test:programmatic_port"), TestGuiType) {
            boundSlot(port, 0) {
                sessionBacking { null }
                accepts { stack -> stack.type == org.bukkit.Material.COAL }
                onSlotUpdate {
                    assertNull(tileEntityOrNull)
                    assertEquals("port-user", viewer.name)
                    updates += source to item?.amount
                }
            }
        }
        val manager = GuiManager()
        val player = server.addPlayer("port-user")
        val session = GuiSession(definition, player, GuiSubject.Standalone, Unit, manager)
        session.installPorts(
            manager.resolvePorts(
                player,
                definition,
                Unit,
                GuiSubject.Standalone,
                presentation = null,
                openBindings = emptyMap(),
            ),
        )
        session.initializeState()
        val handle = session.port(port)

        val rejected = handle.tryInsert(ItemStack(org.bukkit.Material.GOLD_INGOT, 2))
        assertEquals(2, rejected.amount)
        assertNull(handle.item)

        val remainder = handle.tryInsert(ItemStack(org.bukkit.Material.COAL, 4))
        assertEquals(0, remainder.amount)
        assertEquals(4, handle.item?.amount)
        handle.update { stack -> stack?.also { it.amount = 2 } }
        assertEquals(2, handle.item?.amount)
        assertEquals(
            listOf<Pair<GuiSlotUpdateSource, Int?>>(
                GuiSlotUpdateSource.Programmatic to 4,
                GuiSlotUpdateSource.Programmatic to 2,
            ),
            updates,
        )

        session.markClosed(GuiCloseReason.Programmatic)
        assertFailsWith<IllegalStateException> { handle.item }
    }

    @Test
    public fun `players can place items into and take items from standalone input ports`() {
        val port = GuiSlotPort("interactive-input")
        val definition = gui(id("test:interactive_port"), OpeningTestGuiType) {
            boundSlot(port, 0) {
                sessionBacking { null }
                accepts { stack -> stack.type == org.bukkit.Material.COAL }
                placeholder { ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE) }
            }
        }
        val manager = GuiManager()
        val player = server.addPlayer("port-clicker")
        val session = assertIs<GuiOpenResult.Opened>(manager.open(player, definition)).session
        assertEquals(Unit, session.context)

        session.view.setCursor(ItemStack(org.bukkit.Material.COAL, 3))
        val insert = InventoryClickEvent(
            session.view,
            InventoryType.SlotType.CONTAINER,
            0,
            ClickType.LEFT,
            InventoryAction.SWAP_WITH_CURSOR,
        )
        manager.handleClick(insert)

        assertTrue(insert.isCancelled)
        assertEquals(3, session.port(port).item?.amount)
        assertTrue(session.view.cursor.type.isAir)

        val extract = InventoryClickEvent(
            session.view,
            InventoryType.SlotType.CONTAINER,
            0,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL,
        )
        manager.handleClick(extract)

        assertTrue(extract.isCancelled)
        assertNull(session.port(port).item)
        assertEquals(3, session.view.cursor.amount)
    }

    @Test
    public fun `physical slot declarations overwrite ports in either direction`() {
        val port = GuiSlotPort("storage")
        val portWins = gui(id("test:port_wins"), TestGuiType) {
            slot(0) {
                key = "decoration"
                item { ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE) }
            }
            boundSlot(port, 0) {
                required = false
                placeholder { ItemStack(org.bukkit.Material.BARRIER) }
            }
        }
        assertEquals(setOf(port), portWins.slotPorts)
        assertEquals(emptySet(), portWins.requiredSlotPorts)
        assertEquals(
            org.bukkit.Material.BARRIER, portWins.element(port)?.item?.invoke(
                GuiRenderContext(guiSession(portWins)),
            )?.type
        )

        val decorationWins = gui(id("test:decoration_wins"), TestGuiType) {
            boundSlot(port, 0) { placeholder { ItemStack(org.bukkit.Material.BARRIER) } }
            slot(0) {
                key = "decoration"
                item { ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE) }
            }
        }
        assertTrue(decorationWins.slotPorts.isEmpty())
        assertEquals("decoration", decorationWins.elements.single().key)
    }

    @Test
    public fun `overlay catalog accepts identical declarations and rejects conflicts and late contributions`() {
        val texture = ref<Texture2D>(TestResourceRoot, "gui/profile.png")
        val identifier = id("test:profile")
        val profile = GuiOverlayProfile.chest(3)
        val spec = GuiOverlaySpec(profile, listOf(GuiOverlayLayer(texture)))

        val first = GuiOverlayCatalog.contribute(identifier, spec)
        val duplicate = GuiOverlayCatalog.contribute(identifier, spec)

        assertSame(first, duplicate)
        assertEquals("test:gui/profile", first.fontKey.asString())
        assertTrue(GuiOverlayCatalog.isClaimed(texture))
        val overlayGlyph = GuiOverlayComposer.compose(identifier, spec)
            .allComponents()
            .filterIsInstance<TextComponent>()
            .single { it.content() == first.layerGlyphs.single() }
        assertEquals(NamedTextColor.WHITE, overlayGlyph.color())
        assertFailsWith<IllegalArgumentException> {
            GuiOverlayCatalog.contribute(identifier, GuiOverlaySpec(profile, listOf(GuiOverlayLayer(texture, x = 1))))
        }

        GuiOverlayCatalog.closeContributions()
        assertFailsWith<IllegalStateException> {
            GuiOverlayCatalog.contribute(id("test:late"), spec)
        }
    }

    @Test
    public fun `pagination DSL accepts one explicit entry type parameter`() {
        val definition = gui(id("test:pagination"), TestGuiType) {
            paginate<String>(key = "entries", slots = slots(0, 1, 2)) {
                entries { listOf("one", "two") }
                item { null }
                onClick { entry -> entry.length }
            }
        }

        assertEquals(3, definition.managedSlots.size)
    }

    @Test
    public fun `all group fills every top inventory slot in order`() {
        val definition = gui(id("test:fill_all"), TestGuiType) {
            fill {
                item { ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE) }
            }
        }

        assertEquals(
            (0 until TestGuiType.layout.topSize).map(GuiSlot::Top),
            definition.managedSlots.toList(),
        )
        assertTrue(definition.elements.all { it.item != null })
    }

    @Test
    public fun `every standalone vanilla adapter declares stable geometry and an overlay profile`() {
        val fixed = listOf(
            GuiType.Anvil to 3,
            GuiType.Barrel to 27,
            GuiType.Beacon to 1,
            GuiType.BlastFurnace to 3,
            GuiType.BrewingStand to 5,
            GuiType.CartographyTable to 3,
            GuiType.Crafter to 10,
            GuiType.CraftingTable to 10,
            GuiType.Dispenser to 9,
            GuiType.Dropper to 9,
            GuiType.EnchantingTable to 2,
            GuiType.EnderChest to 27,
            GuiType.Furnace to 3,
            GuiType.Grindstone to 3,
            GuiType.Hopper to 5,
            GuiType.Lectern to 1,
            GuiType.Loom to 4,
            GuiType.Merchant to 3,
            GuiType.PlayerInventory to 36,
            GuiType.ShulkerBox to 27,
            GuiType.SmithingTable to 4,
            GuiType.Smoker to 3,
            GuiType.Stonecutter to 2,
        )
        val all = fixed.map { it.first } + (1..6).map { GuiType.Chest(it) }

        fixed.forEach { (type, expectedSize) ->
            assertEquals(expectedSize, type.layout.topSize, type.id.toString())
            assertNotNull(type.overlayProfile, type.id.toString())
            assertEquals(13, type.overlayProfile?.bitmapAscent, type.id.toString())
            assertNotNull(type.inventoryType, type.id.toString())
        }
        (1..6).forEach { rows ->
            assertEquals(rows * 9, GuiType.Chest(rows).layout.topSize)
            assertEquals(13, GuiType.Chest(rows).overlayProfile?.bitmapAscent)
        }
        assertEquals(all.size, all.map { it.id }.distinct().size)
    }

    @Test
    public fun `session state producers run independently and support mutable values`() {
        var initializations = 0
        val rendered = mutableListOf<MutableList<String>>()
        val definition = gui(id("test:session_state"), TestGuiType) {
            var values by state<MutableList<String>> {
                initializations++
                mutableListOf(viewer.name)
            }
            slot(0) {
                item {
                    rendered += values
                    null
                }
            }
        }
        val manager = GuiManager()
        val first = GuiSession(definition, server.addPlayer("first"), GuiSubject.Standalone, Unit, manager)
        val second = GuiSession(definition, server.addPlayer("second"), GuiSubject.Standalone, Unit, manager)
        first.initializeState()
        second.initializeState()

        renderElement(first)
        renderElement(second)

        assertEquals(2, initializations)
        assertNotSame(rendered[0], rendered[1])
        assertEquals(listOf("first"), rendered[0])
        assertEquals(listOf("second"), rendered[1])
    }

    @Test
    public fun `shared state initializes once and is visible across sessions of the exact definition`() {
        var initializations = 0
        val observed = mutableListOf<Int>()
        val definition = gui(id("test:shared_state"), TestGuiType) {
            var shared by sharedState<Int> {
                initializations++
                6
            }
            slot(0) {
                item {
                    observed += shared
                    null
                }
            }
            onOpen { shared += 1 }
        }
        val manager = GuiManager()
        val first = GuiSession(definition, server.addPlayer("first"), GuiSubject.Standalone, Unit, manager)
        val second = GuiSession(definition, server.addPlayer("second"), GuiSubject.Standalone, Unit, manager)
        first.initializeState()
        second.initializeState()

        renderElement(first)
        invokeOpen(first)
        renderElement(second)

        assertEquals(1, initializations)
        assertEquals(listOf(6, 7), observed)
    }

    @Test
    public fun `whileOpen handler retains typed session context and delegated state`() {
        val observed = mutableListOf<Int>()
        val definition = gui(id("test:on_tick"), TestGuiType) {
            var ticks by state<Int> { 0 }
            whileOpen {
                ticks += 1
                observed += ticks
            }
        }
        val session = GuiSession(definition, server.addPlayer("ticker"), GuiSubject.Standalone, Unit, GuiManager())
        session.initializeState()

        repeat(3) {
            GuiExecution.with(GuiExecutionContext(session, GuiExecutionPhase.Handling)) {
                definition.whileOpenHandlers.single().invoke(GuiLifecycleContext(session))
            }
        }

        assertEquals(listOf(1, 2, 3), observed)
    }

    @Test
    public fun `delegated state fails outside its owning execution context`() {
        lateinit var producer: GuiRenderContext<Unit, InventoryView>.() -> org.bukkit.inventory.ItemStack?
        val definition = gui(id("test:context_guard"), TestGuiType) {
            var count by state<Int> { 1 }
            slot(0) {
                item {
                    count.hashCode()
                    null
                }
            }
        }
        producer = definition.elements.single().item!!
        val session = GuiSession(definition, server.addPlayer(), GuiSubject.Standalone, Unit, GuiManager())
        session.initializeState()

        assertFailsWith<IllegalStateException> {
            producer(GuiRenderContext(session))
        }
    }

    @Test
    public fun `nested GUI execution restores the outer state context`() {
        val reads = mutableListOf<Int>()
        val outer = gui(id("test:outer"), TestGuiType) {
            var value by state<Int> { 1 }
            slot(0) { item { reads += value; null } }
        }
        val inner = gui(id("test:inner"), TestGuiType) {
            var value by state<Int> { 2 }
            slot(0) { item { reads += value; null } }
        }
        val manager = GuiManager()
        val outerSession = GuiSession(outer, server.addPlayer("outer"), GuiSubject.Standalone, Unit, manager)
        val innerSession = GuiSession(inner, server.addPlayer("inner"), GuiSubject.Standalone, Unit, manager)
        outerSession.initializeState()
        innerSession.initializeState()

        GuiExecution.with(GuiExecutionContext(outerSession, GuiExecutionPhase.Rendering)) {
            outer.elements.single().item!!.invoke(GuiRenderContext(outerSession))
            GuiExecution.with(GuiExecutionContext(innerSession, GuiExecutionPhase.Rendering)) {
                inner.elements.single().item!!.invoke(GuiRenderContext(innerSession))
            }
            outer.elements.single().item!!.invoke(GuiRenderContext(outerSession))
        }

        assertEquals(listOf(1, 2, 1), reads)
        assertTrue(GuiExecution.currentOrNull() == null)
    }

    private fun renderElement(session: GuiSession<Unit, InventoryView>) {
        GuiExecution.with(GuiExecutionContext(session, GuiExecutionPhase.Rendering)) {
            session.definition.elements.single().item!!.invoke(GuiRenderContext(session))
        }
    }

    private fun invokeOpen(session: GuiSession<Unit, InventoryView>) {
        GuiExecution.with(GuiExecutionContext(session, GuiExecutionPhase.Handling)) {
            session.definition.openHandlers.single().invoke(GuiLifecycleContext(session))
        }
    }

    private fun guiSession(definition: GuiDefinition<Unit, InventoryView>): GuiSession<Unit, InventoryView> =
        GuiSession(definition, server.addPlayer(), GuiSubject.Standalone, Unit, GuiManager())
            .also { it.initializeState() }
}

private object TestGuiType : GuiType<InventoryView> {
    override val id: Identifier = id("test:gui_type")
    override val layout: GuiLayout = GuiLayout.grid(rows = 1)
    override val inventoryType: org.bukkit.event.inventory.InventoryType =
        org.bukkit.event.inventory.InventoryType.CHEST
    override val overlayProfile: GuiOverlayProfile? = null

    override fun create(context: GuiCreateContext): InventoryView =
        error("This test adapter does not open a view.")
}

private data class TestOpenContext(
    val name: String,
    val initialValue: Int,
)

private object OpeningTestGuiType : GuiType<InventoryView> {
    override val id: Identifier = id("test:opening_gui_type")
    override val layout: GuiLayout = GuiLayout.grid(rows = 1)
    override val inventoryType: InventoryType = InventoryType.CHEST
    override val overlayProfile: GuiOverlayProfile? = null

    override fun create(context: GuiCreateContext): InventoryView = OpeningInventoryViewMock(
        context.player,
        org.bukkit.Bukkit.createInventory(null, layout.topSize),
        context.player.inventory,
        InventoryType.CHEST,
    )
}

private class OpeningInventoryViewMock(
    player: org.bukkit.entity.HumanEntity,
    top: Inventory,
    bottom: Inventory,
    type: InventoryType,
) : SimpleInventoryViewMock(player, top, bottom, type) {
    override fun convertSlot(rawSlot: Int): Int = rawSlot
}

private object TestResourceRoot : ResourceRoot {
    override val cutPlugin: CuTPlugin
        get() = error("Overlay catalog tests do not resolve a resource plugin.")

    override fun getResourcesFolder(): File = File(".")
}

private fun Component.allComponents(): List<Component> =
    listOf(this) + children().flatMap { it.allComponents() }
