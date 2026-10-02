package xyz.mastriel.cutapi.resources.process

import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.data.*
import java.awt.image.*
import java.io.*
import kotlin.test.*

public class VanillaItemResourceGeneratorTest {
    @Test
    public fun `compound extensions produce deterministic type-changing refs`() {
        val texture = ref<Texture2D>(GeneratorRoot, "item/hammer.png")

        assertEquals("png", texture.extension)
        assertEquals("item/hammer.model.json", texture.generatedMinecraftModelRef().path(withExtension = true))
        assertEquals(
            "item/hammer^large.model.json",
            texture.generatedMinecraftModelRef("large").path(withExtension = true),
        )
        assertEquals(
            "item/hammer.item_model.json",
            texture.generatedMinecraftModelRef().generatedItemModelRef().path(withExtension = true),
        )
        assertEquals("assets/generator_test/textures/item/hammer.png", texture.texturePackPath())
        assertEquals(
            "assets/generator_test/models/item/hammer.json",
            texture.generatedMinecraftModelRef().minecraftModelPackPath(),
        )
        assertEquals(
            "assets/generator_test/items/item/hammer.json",
            texture.generatedMinecraftModelRef().generatedItemModelRef().itemModelPackPath(),
        )
    }

    @Test
    public fun `texture model and item model generators compose without a subId`() {
        val nestedItemBlock = GenerateBlock(
            generator = ItemModelGenerator,
            subId = null,
            options = ItemModelGeneratorOptions(handAnimationOnSwap = false),
            authoredOptions = Variant.Map(mapOf("hand_animation_on_swap" to Variant.Boolean(false))),
        )
        val textureOptions = TextureModelGeneratorOptions(
            parent = "minecraft:item/handheld",
            textures = mapOf("layer1" to "minecraft:item/glint"),
            generate = listOf(nestedItemBlock),
        )
        val textureBlock = GenerateBlock(
            generator = TextureModelGenerator,
            subId = null,
            options = textureOptions,
            authoredOptions = Variant.Map(
                mapOf(
                    "parent" to Variant.String("minecraft:item/handheld"),
                    "textures" to Variant.Map(mapOf("layer1" to Variant.String("minecraft:item/glint"))),
                )
            ),
        )
        val metadata = Texture2D.Metadata().also { it.generateBlocks = listOf(textureBlock) }
        val texture = Texture2D(
            ref(GeneratorRoot, "item/generator_chain_hammer.png"),
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
            metadata,
        )
        val generatedModels = mutableListOf<Resource>()
        TextureModelGenerator.generateUntyped(texture, textureBlock, texture.ref, generatedModels::add)

        val model = assertIs<MinecraftModel>(generatedModels.single())
        assertEquals("minecraft:item/handheld", model.data.parent)
        assertEquals("generator_test://item/generator_chain_hammer.png", model.data.textures["layer0"])
        assertEquals("minecraft:item/glint", model.data.textures["layer1"])
        val generatedItemModels = mutableListOf<Resource>()
        ItemModelGenerator.generateUntyped(model, nestedItemBlock, model.ref, generatedItemModels::add)
        val itemModel = assertIs<ItemModel>(generatedItemModels.single())
        assertFalse(itemModel.data.handAnimationOnSwap)
        assertEquals("minecraft:model", itemModel.data.model.getValue("type").jsonPrimitive.content)
        assertEquals(model.ref.toString(), itemModel.data.model.getValue("model").jsonPrimitive.content)
    }

    @Test
    public fun `changing a node discriminator replaces the whole node`() {
        val base = Variant.Map(
            mapOf(
                "type" to Variant.String("minecraft:model"),
                "model" to Variant.String("example:item/hammer"),
                "tints" to Variant.List(listOf(Variant.String("old"))),
            )
        )
        val overlay = Variant.Map(mapOf("type" to Variant.String("minecraft:empty")))

        assertEquals(overlay, mergeVanillaResourceData(base, overlay))
    }

    @Test
    public fun `model resources preserve vanilla fields unknown to the typed view`() {
        val json = buildJsonObject {
            put("parent", "minecraft:item/generated")
            put("credit", "kept verbatim")
            put("future_vanilla_field", buildJsonObject { put("enabled", true) })
        }
        val model = MinecraftModel(ref(GeneratorRoot, "item/future.model.json"), json)

        assertEquals("kept verbatim", model.toPackData().getValue("credit").jsonPrimitive.content)
        assertTrue(model.toPackData().getValue("future_vanilla_field").jsonObject.getValue("enabled").jsonPrimitive.boolean)
    }

    @Test
    public fun `item models add folder-independent textures to the items atlas`() {
        val texture = Texture2D(
            ref(GeneratorRoot, "ui/inventory_bg.png"),
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
            Texture2D.Metadata(),
        )
        val model = MinecraftModel(
            ref(GeneratorRoot, "ui/inventory_bg.model.json"),
            MinecraftModelData(textures = mapOf("layer0" to texture.ref.toString())),
        )
        val itemModel = ItemModel(
            ref(GeneratorRoot, "ui/inventory_bg.item_model.json"),
            ItemModelData(model = ModelItemModelNode(model.ref).toJson()),
        )

        val assignments = resolveModelTextureAtlasAssignments(listOf(texture, model, itemModel))

        assertEquals(setOf(texture.ref), assignments.items)
        assertTrue(assignments.blocks.isEmpty())
        assertEquals(
            buildJsonObject {
                put("sources", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "single")
                        put("resource", "generator_test:ui/inventory_bg")
                    })
                })
            },
            textureAtlasJson(assignments.items),
        )
    }

    @Test
    public fun `textures in vanilla item directory do not get duplicate atlas sources`() {
        val texture = Texture2D(
            ref(GeneratorRoot, "item/hammer.png"),
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
            Texture2D.Metadata(),
        )
        val model = MinecraftModel(
            ref(GeneratorRoot, "item/hammer.model.json"),
            MinecraftModelData(textures = mapOf("layer0" to texture.ref.toMinecraftLocator())),
        )
        val itemModel = ItemModel(
            ref(GeneratorRoot, "item/hammer.item_model.json"),
            ItemModelData(model = ModelItemModelNode(model.ref).toJson()),
        )

        val assignments = resolveModelTextureAtlasAssignments(listOf(texture, model, itemModel))

        assertTrue(assignments.items.isEmpty())
        assertTrue(assignments.blocks.isEmpty())
    }

    @Test
    public fun `explicit output and subId cannot be combined`() {
        val texture = Texture2D(
            ref(GeneratorRoot, "item/generator_conflict.png"),
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
            Texture2D.Metadata(),
        )
        val block = GenerateBlock(
            generator = TextureModelGenerator,
            subId = "large",
            options = TextureModelGeneratorOptions(output = ref(GeneratorRoot, "item/other.model.json")),
        )

        assertFailsWith<IllegalArgumentException> {
            TextureModelGenerator.generateUntyped(texture, block, texture.ref) { }
        }
    }
}

private object GeneratorPlugin : CuTPlugin {
    override val namespace: String = "generator_test"
    override fun getResourcesFolder(): File = File(".")
}

private object GeneratorRoot : ResourceRoot {
    override val cutPlugin: CuTPlugin = GeneratorPlugin
    override val namespace: String = GeneratorPlugin.namespace
    override fun getResourcesFolder(): File = File(".")
}
