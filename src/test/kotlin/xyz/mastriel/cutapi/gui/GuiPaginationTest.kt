package xyz.mastriel.cutapi.gui

import org.bukkit.Material
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.id
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

public class GuiPaginationTest {
    private lateinit var server: ServerMock

    @BeforeTest
    public fun setUp() {
        server = MockBukkit.mock()
    }

    @AfterTest
    public fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    public fun `unavailable navigation controls render lower precedence slot items`() {
        var entries = listOf("only")
        val definition = gui(id("test:pagination_fallback"), PaginationTestGuiType) {
            slot(7) { item { ItemStack(Material.GRAY_STAINED_GLASS_PANE) } }
            slot(8) { item { ItemStack(Material.BLACK_STAINED_GLASS_PANE) } }

            paginate<String>(key = "entries", slots = slots(0, 1)) {
                entries { entries }
                item { ItemStack(Material.PAPER) }
                previous(7) { ItemStack(Material.ARROW) }
                next(8) { ItemStack(Material.ARROW) }
            }
        }
        val session = GuiSession(
            definition,
            server.addPlayer("pagination-viewer"),
            GuiSubject.Standalone,
            Unit,
            GuiManager(),
        ).also { it.initializeState() }

        assertEquals(Material.GRAY_STAINED_GLASS_PANE, render(definition, session, "entries/previous").type)
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, render(definition, session, "entries/next").type)

        entries = listOf("one", "two", "three")

        assertEquals(Material.GRAY_STAINED_GLASS_PANE, render(definition, session, "entries/previous").type)
        assertEquals(Material.ARROW, render(definition, session, "entries/next").type)

        @Suppress("UNCHECKED_CAST")
        val page = definition.stateDeclarations.single().key as GuiStateKey<Int>
        session.writeState(page, 1)

        assertEquals(Material.ARROW, render(definition, session, "entries/previous").type)
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, render(definition, session, "entries/next").type)
    }

    private fun render(
        definition: GuiDefinition<Unit, InventoryView>,
        session: GuiSession<Unit, InventoryView>,
        key: String,
    ): ItemStack = GuiExecution.with(GuiExecutionContext(session, GuiExecutionPhase.Rendering)) {
        requireNotNull(definition.elements.single { it.key == key }.item?.invoke(GuiRenderContext(session)))
    }
}

private object PaginationTestGuiType : GuiType<InventoryView> {
    override val id: Identifier = id("test:pagination_gui_type")
    override val layout: GuiLayout = GuiLayout.grid(rows = 1)
    override val inventoryType: InventoryType = InventoryType.CHEST
    override val overlayProfile: GuiOverlayProfile? = null

    override fun create(context: GuiCreateContext): InventoryView = error("This test adapter does not open a view.")
}
