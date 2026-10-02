package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import java.awt.*
import java.awt.image.*
import java.io.*
import kotlinx.serialization.json.*
import kotlin.test.*

public class HorizontalProgressTextureGeneratorTest {
    @Test
    public fun `frame index clamps progress and includes both endpoints`() {
        assertEquals(0, horizontalProgressFrameIndex(Float.NaN, 24))
        assertEquals(0, horizontalProgressFrameIndex(-1f, 24))
        assertEquals(0, horizontalProgressFrameIndex(0f, 24))
        assertEquals(1, horizontalProgressFrameIndex(0.001f, 24))
        assertEquals(12, horizontalProgressFrameIndex(0.5f, 24))
        assertEquals(24, horizontalProgressFrameIndex(1f, 24))
        assertEquals(24, horizontalProgressFrameIndex(Float.POSITIVE_INFINITY, 24))
    }

    @Test
    public fun `fill frame contains the full mask only through its filled width`() {
        val full = solidImage(width = 4, height = 2, color = Color.BLUE)

        val start = composeHorizontalProgressFillFrame(full, frame = 0, frameCount = 4)
        val middle = composeHorizontalProgressFillFrame(full, frame = 2, frameCount = 4)
        val end = composeHorizontalProgressFillFrame(full, frame = 4, frameCount = 4)

        assertRow(start, Color(0, true), Color(0, true), Color(0, true), Color(0, true))
        assertRow(middle, Color.BLUE, Color.BLUE, Color(0, true), Color(0, true))
        assertRow(end, Color.BLUE, Color.BLUE, Color.BLUE, Color.BLUE)
    }

    @Test
    public fun `tint mask normalization makes the brightest authored color white`() {
        val authored = solidImage(2, 1, Color(0x8B, 0x8B, 0x8B))
        authored.setRGB(1, 0, Color(0x46, 0x46, 0x46).rgb)

        val normalized = normalizeProgressTintMask(authored)

        assertRow(normalized, Color.WHITE, Color(0x80, 0x80, 0x80))
    }

    @Test
    public fun `resource generation creates an oversized texture and model for every frame`() {
        val empty = Texture2D(
            ref(TestResourceRoot, "empty.png"),
            solidImage(24, 16, Color.RED),
            Texture2D.Metadata(),
        )
        val full = Texture2D(
            ref(TestResourceRoot, "full.png"),
            solidImage(24, 16, Color.BLUE),
            Texture2D.Metadata(),
        )
        val generatedBase = ref<Texture2D>(TestResourceRoot, "empty^frames.png")
        val backgroundModel = MinecraftModel(
            ref = ref(TestResourceRoot, "inventory_bg.model.json"),
            data = MinecraftModelData(
                textures = mapOf("background" to "test:item/inventory_bg"),
                display = mapOf(
                    MinecraftModelDisplayType.Gui to MinecraftModelDisplay(
                        scale = VoxelVector(1.15f, -1.15f, -1.15f),
                    )
                ),
            ),
            metadata = MinecraftModel.Metadata(),
        )

        val resources = createHorizontalProgressResources(
            empty,
            full,
            generatedBase,
            frameCount = 24,
            backgroundModel = backgroundModel,
        )
        val textures = resources.filterIsInstance<Texture2D>()
        val models = resources.filterIsInstance<MinecraftModel>()
        val itemModels = resources.filterIsInstance<ItemModel>()
        val frameModels = models.filter { "_model_" in it.ref.path }
        val leftBackground = models.single { "_background_left" in it.ref.path }
        val rightBackground = models.single { "_background_right" in it.ref.path }

        assertEquals(26, textures.size)
        assertEquals(27, models.size)
        assertEquals(1, itemModels.size)
        assertTrue(itemModels.single().data.oversizedInGui)
        val expectedBackgrounds = listOf(
            leftBackground.ref,
            backgroundModel.ref,
            rightBackground.ref,
        )
        val itemModel = itemModels.single()
        assertEquals("empty^frames.item_model.json", itemModel.ref.path(withExtension = true))
        val root = itemModel.data.model
        assertEquals("minecraft:condition", root.getValue("type").jsonPrimitive.content)
        assertEquals("minecraft:custom_model_data", root.getValue("property").jsonPrimitive.content)
        assertEquals(0, root.getValue("index").jsonPrimitive.int)
        val enabledModels = root.getValue("on_true").jsonObject.getValue("models").jsonArray
        assertEquals(
            expectedBackgrounds.map { it.toMinecraftModelLocator() },
            enabledModels.take(3).map { it.jsonObject.getValue("model").jsonPrimitive.content },
        )
        val progressNode = root.getValue("on_false").jsonObject
        assertEquals("minecraft:range_dispatch", progressNode.getValue("type").jsonPrimitive.content)
        assertEquals("minecraft:custom_model_data", progressNode.getValue("property").jsonPrimitive.content)
        assertEquals(0, progressNode.getValue("index").jsonPrimitive.int)
        val entries = progressNode.getValue("entries").jsonArray
        assertEquals(25, entries.size)
        assertEquals(12f, entries[12].jsonObject.getValue("threshold").jsonPrimitive.float)
        val middleNode = entries[12].jsonObject.getValue("model").jsonObject
        assertEquals(
            horizontalProgressFrameModelRef(generatedBase, 12).toMinecraftModelLocator(),
            middleNode.getValue("model").jsonPrimitive.content,
        )
        val tints = middleNode.getValue("tints").jsonArray
        assertEquals(listOf(0, 1), tints.map { it.jsonObject.getValue("index").jsonPrimitive.int })
        assertEquals(
            listOf(0x8B8B8B, 0xFFFFFF),
            tints.map { it.jsonObject.getValue("default").jsonPrimitive.int },
        )
        assertEquals(
            VoxelVector(-18f, 0f, 0f),
            leftBackground.data.display.getValue(MinecraftModelDisplayType.Gui).translation,
        )
        assertEquals(
            VoxelVector(18f, 0f, 0f),
            rightBackground.data.display.getValue(MinecraftModelDisplayType.Gui).translation,
        )
        assertEquals("minecraft:item/generated", models.single { "_model_12" in it.ref.path }.data.parent)
        val serializedModel = CuTApiJson.encodeToJsonElement(
            MinecraftModelData.serializer(),
            models.single { "_model_12" in it.ref.path }.data,
        ).jsonObject
        assertFalse("elements" in serializedModel)
        assertEquals(
            VoxelVector(1.5f, 1f, 1f),
            models.single { "_model_12" in it.ref.path }
                .data.display.getValue(MinecraftModelDisplayType.Gui).scale,
        )
        assertEquals(
            VoxelVector(0f, 0f, 12f),
            models.single { "_model_12" in it.ref.path }
                .data.display.getValue(MinecraftModelDisplayType.Gui).translation,
        )
        assertEquals(
            mapOf(
                "layer0" to horizontalProgressEmptyTextureRef(generatedBase).toMinecraftLocator(),
                "layer1" to horizontalProgressFillTextureRef(generatedBase, 12).toMinecraftLocator(),
            ),
            models.single { "_model_12" in it.ref.path }.data.textures,
        )
        val middle = textures.single { "_fill_12" in it.ref.path }.data
        assertEquals(Color.BLUE.rgb, middle.getRGB(11, 0))
        assertEquals(0, middle.getRGB(12, 0))

        val atlasAssignments = resolveModelTextureAtlasAssignments(resources + backgroundModel)
        assertEquals(textures.map { it.ref }.toSet(), atlasAssignments.items)
        assertTrue(atlasAssignments.blocks.isEmpty())
    }

    private fun solidImage(width: Int, height: Int, color: Color): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { image ->
            val graphics = image.createGraphics()
            try {
                graphics.color = color
                graphics.fillRect(0, 0, width, height)
            } finally {
                graphics.dispose()
            }
        }

    private fun assertRow(image: BufferedImage, vararg colors: Color) {
        assertEquals(colors.size, image.width)
        colors.forEachIndexed { x, color -> assertEquals(color.rgb, image.getRGB(x, 0)) }
    }
}

private object TestResourceRoot : ResourceRoot {
    override val cutPlugin: CuTPlugin
        get() = error("Progress generator tests do not resolve the resource root plugin.")

    override val namespace: String = "test"

    override fun getResourcesFolder(): File = File(".")
}
