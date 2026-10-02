package xyz.mastriel.cutapi.resources.process

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.builtin.*
import kotlin.test.*

public class ItemModelFileTest {
    @Test
    public fun `oversized GUI opt-in is encoded at the item definition root`() {
        val encoded = CuTApiJson.encodeToString(
            ItemModelData(
                model = ModelItemModelNode("cutapi:ui/inventory_bg").toJson(),
                handAnimationOnSwap = false,
                oversizedInGui = true,
            )
        )
        val root = CuTApiJson.parseToJsonElement(encoded).jsonObject

        assertEquals(true, root.getValue("oversized_in_gui").jsonPrimitive.boolean)
        assertNull(root.getValue("model").jsonObject["oversized_in_gui"])
    }

    @Test
    public fun `logical item defaults are omitted`() {
        val encoded = CuTApiJson.encodeToString(
            ItemModelData(model = ModelItemModelNode("cutapi:item/example").toJson())
        )
        val root = CuTApiJson.parseToJsonElement(encoded).jsonObject

        assertNull(root["hand_animation_on_swap"])
        assertNull(root["oversized_in_gui"])
        assertNull(root["swap_animation_scale"])
    }

    @Test
    public fun `multiple background models and foreground are emitted as one ordered composite`() {
        val model = CompositeItemModelNode(
            listOf(
                ModelItemModelNode("cutapi:ui/inventory_bg_left"),
                ModelItemModelNode("cutapi:ui/inventory_bg"),
                ModelItemModelNode("cutapi:ui/inventory_bg_right"),
                ModelItemModelNode("cutapi:ui/progress_arrow_frame_12"),
            )
        ).toJson()
        val layers = model.getValue("models").jsonArray

        assertEquals("minecraft:composite", model.getValue("type").jsonPrimitive.content)
        assertEquals(
            listOf(
                "cutapi:ui/inventory_bg_left",
                "cutapi:ui/inventory_bg",
                "cutapi:ui/inventory_bg_right",
                "cutapi:ui/progress_arrow_frame_12",
            ),
            layers.map { it.jsonObject.getValue("model").jsonPrimitive.content },
        )
    }

    @Test
    public fun `typed 1_21_11 properties tints and special models write vanilla shapes`() {
        val tinted = ModelItemModelNode(
            model = "cutapi:item/hammer",
            tints = listOf(ItemModelTint.Dye(MinecraftColor.Packed(0xAABBCC))),
        )
        val special = SpecialItemModelNode(
            base = "minecraft:item/template_skull",
            model = SpecialItemModel.PlayerHead,
        )
        val condition = ConditionItemModelNode(ItemModelBooleanProperty.UsingItem, special, tinted).toJson()

        assertEquals("minecraft:using_item", condition.getValue("property").jsonPrimitive.content)
        val falseNode = condition.getValue("on_false").jsonObject
        assertEquals(
            "minecraft:dye",
            falseNode.getValue("tints").jsonArray.single().jsonObject.getValue("type").jsonPrimitive.content,
        )
        assertEquals(
            "minecraft:player_head",
            condition.getValue("on_true").jsonObject.getValue("model").jsonObject
                .getValue("type").jsonPrimitive.content,
        )
    }
}
