package xyz.mastriel.cutapi.gui

import kotlinx.serialization.json.*
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentIteratorType
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import xyz.mastriel.cutapi.CuTPlugin
import xyz.mastriel.cutapi.data.getOrThrow
import xyz.mastriel.cutapi.data.requireMap
import xyz.mastriel.cutapi.gui.resource.GuiBaseContainerResources
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resources.ResourceRoot
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.resources.data.ResourceYaml
import xyz.mastriel.cutapi.resources.ref
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

public class GuiBaseContainerOverlayTest {
    @BeforeTest
    public fun setUp() { GuiOverlayCatalog.resetForTests() }

    @AfterTest
    public fun tearDown() { GuiOverlayCatalog.resetForTests() }

    @Test
    public fun `all six row counts prepend distinct backgrounds and preserve styled titles`() {
        val title = Component.translatable("container.chest").color(NamedTextColor.GOLD)
            .font(Key.key("test", "title"))
        val glyphs = (1..6).map { rows ->
            val composed = GuiBaseContainerOverlay.compose(rows, title)
            val background = assertIs<TextComponent>(composed.children().first())
            assertEquals(GuiBaseContainerOverlay.fontKey, background.font())
            assertEquals(NamedTextColor.WHITE, background.color())
            assertEquals(title, composed.children().last())
            assertSame(composed, GuiBaseContainerOverlay.compose(rows, composed))
            assertTrue(background.content().contains(GuiBaseContainerOverlay.glyph(rows)))
            GuiBaseContainerOverlay.glyph(rows)
        }
        assertEquals(6, glyphs.toSet().size)
    }

    @Test
    public fun `base is drawn before custom layers and opt out survives title composition`() {
        val texture = ref<Texture2D>(BaseTextureRoot, "ui/custom.png")
        val profile = GuiOverlayProfile.chest(3)
        val visibleTitle = Component.text("Machine")
        for (showBase in listOf(true, false)) {
            val spec = GuiOverlayBuilder(profile).apply {
                showBaseTexture = showBase
                layer(texture)
            }.build()
            val guiId = id("test:base_$showBase")
            val artifact = GuiOverlayCatalog.contribute(guiId, spec)
            val customTitle = GuiOverlayComposer.compose(guiId, spec, visibleTitle)
            val finalTitle = GuiBaseContainerOverlay.compose(3, customTitle)
            val text = finalTitle.iterable(ComponentIteratorType.DEPTH_FIRST).filterIsInstance<TextComponent>()
            assertEquals(1, text.count { it.content() == artifact.layerGlyphs.single() })
            assertEquals(1, text.count { it.content() == "Machine" })
            if (showBase) {
                assertTrue(text.first { it.content().isNotEmpty() }.content().contains(GuiBaseContainerOverlay.glyph(3)))
            } else {
                assertSame(customTitle, finalTitle)
                assertFalse(text.any { it.content().contains(GuiBaseContainerOverlay.glyph(3)) &&
                    it.font() == GuiBaseContainerOverlay.fontKey })
            }
        }
    }

    @Test
    public fun `overlay options can hide the base without a custom layer`() {
        val spec = GuiOverlayBuilder(GuiOverlayProfile.chest(1)).apply {
            showBaseTexture = false
        }.build()
        assertTrue(spec.layers.isEmpty())
        val guiId = id("test:transparent")
        GuiOverlayCatalog.contribute(guiId, spec)
        val title = GuiOverlayComposer.compose(guiId, spec, Component.text("Transparent"))
        assertSame(title, GuiBaseContainerOverlay.compose(1, title))
        assertTrue(GuiOverlayBuilder(GuiOverlayProfile.chest(1)).build().showBaseTexture)
    }

    @Test
    public fun `base resources emit transparent vanilla texture and native size backgrounds with zero net advance`() {
        val textures = (1..6).map { rows ->
            val path = "ui/base_container/generic_9x$rows.png"
            val image = ImageIO.read(requireNotNull(javaClass.getResource("/pack/$path")))
            Texture2D(ref(BaseTextureRoot, path), image, Texture2D.Metadata())
        }
        val folder = Files.createTempDirectory("cutapi-base-containers").toFile()
        try {
            val json = Json { encodeDefaults = true }
            GuiBaseContainerResources.generate(folder, textures, json)
            val transparent = ImageIO.read(File(folder, GuiBaseContainerResources.VanillaTexturePath))
            assertEquals(256, transparent.width)
            assertEquals(256, transparent.height)
            assertTrue((0 until 256).all { x -> (0 until 256).all { y -> transparent.getRGB(x, y) ushr 24 == 0 } })
            val font = json.parseToJsonElement(File(folder, "assets/cutapi/font/gui/base_container.json").readText()).jsonObject
            val providers = font.getValue("providers").jsonArray.map { it.jsonObject }
            assertEquals(7, providers.size)
            val spaces = providers.last().getValue("advances").jsonObject
            textures.forEachIndexed { index, texture ->
                val rows = index + 1
                val provider = providers[index]
                assertEquals("cutapi:ui/base_container/generic_9x$rows.png", provider.getValue("file").jsonPrimitive.content)
                assertEquals(256, provider.getValue("height").jsonPrimitive.int)
                assertEquals(13, provider.getValue("ascent").jsonPrimitive.int)
                assertEquals(GuiBaseContainerOverlay.glyph(rows), provider.getValue("chars").jsonArray.single().jsonPrimitive.content)
                val opaqueWidth = (0 until texture.data.width).last { x ->
                    (0 until texture.data.height).any { y -> texture.data.getRGB(x, y) ushr 24 != 0 }
                } + 1
                assertEquals(176, opaqueWidth)
                val before = spaces.getValue(GuiBaseContainerOverlay.OriginGlyph).jsonPrimitive.int
                val after = spaces.getValue(GuiBaseContainerOverlay.ReturnGlyph).jsonPrimitive.int
                assertEquals(0, before + opaqueWidth + 1 + after, "Row $rows must preserve the title cursor.")
            }
            assertEquals(0, spaces.getValue(GuiBaseContainerOverlay.SuppressGlyph).jsonPrimitive.int)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    public fun `bundled base textures disable automatic default font glyphs`() {
        val path = "/pack/ui/base_container/apply.meta.folder"
        val document = ResourceYaml.parse(requireNotNull(javaClass.getResource(path)).readText(), path)
        assertEquals("cutapi:folder_apply", document.requireTypeId().toString())
        val apply = document.requireMap().getValue("apply").requireMap()
        val metadata = Texture2D.Metadata.deserialize(apply).getOrThrow()
        assertFalse(metadata.fontSettings.enabled)
    }
}

private object BaseTextureRoot : ResourceRoot {
    override val namespace: String = "cutapi"
    override val cutPlugin: CuTPlugin get() = error("These tests do not resolve plugins.")
    override fun getResourcesFolder(): File = File(".")
}
