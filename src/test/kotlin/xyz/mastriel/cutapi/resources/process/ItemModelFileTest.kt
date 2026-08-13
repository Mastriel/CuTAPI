package xyz.mastriel.cutapi.resources.process

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import xyz.mastriel.cutapi.CuTApiJson
import xyz.mastriel.cutapi.resources.builtin.VanillaItemModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

public class ItemModelFileTest {
    @Test
    public fun `oversized GUI opt-in is encoded at the item definition root`() {
        val encoded = encodeItemModelFile(
            model = VanillaItemModel("cutapi:ui/inventory_bg"),
            handAnimationOnSwap = false,
            oversizedInGui = true,
        )
        val root = CuTApiJson.parseToJsonElement(encoded).jsonObject

        assertEquals(true, root.getValue("oversized_in_gui").jsonPrimitive.boolean)
        assertNull(root.getValue("model").jsonObject["oversized_in_gui"])
    }

    @Test
    public fun `ordinary item models omit the oversized GUI setting`() {
        val encoded = encodeItemModelFile(
            model = VanillaItemModel("cutapi:item/example"),
            handAnimationOnSwap = true,
        )
        val root = CuTApiJson.parseToJsonElement(encoded).jsonObject

        assertNull(root["oversized_in_gui"])
    }
}
