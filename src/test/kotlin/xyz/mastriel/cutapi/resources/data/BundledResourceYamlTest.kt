package xyz.mastriel.cutapi.resources.data

import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.testing.*
import kotlin.test.*

class BundledResourceYamlTest : MockBukkitTest() {
    @Test
    fun `bundled metadata files are tagged YAML`() {
        val resources = mapOf(
            "/pack/items/pfp.png.meta" to "cutapi:texture2d",
            "/pack/items/unknown_item.png.meta" to "cutapi:texture2d",
            "/pack/ui/inventory_bg.model3d.json.meta" to "cutapi:model3d",
            "/pack/ui/inventory_bg.png.meta" to "cutapi:texture2d"
        )

        for ((path, expectedTag) in resources) {
            val text = requireNotNull(javaClass.getResource(path)).readText()
            val document = ResourceYaml.parse(text, path)
            assertEquals(expectedTag, document.requireTypeId().toString())
            document.requireMap()
            val encoded = ResourceYaml.encode(document)
            assertTrue(encoded.startsWith("!<$expectedTag>"))
            assertFalse(SCHEMA_TYPE_DISCRIMINATOR in encoded)
        }
    }

    @Test
    fun `bundled template is an untagged YAML mapping`() {
        val path = "/pack/ui/inventory_texture.template"
        val text = requireNotNull(javaClass.getResource(path)).readText()
        val document = ResourceYaml.parse(text, path)

        assertNull(document.typeId())
        assertNotNull(document.requireMap()["itemModelData"])
    }

    @Test
    fun `inventory background opts into oversized GUI rendering`() {
        val path = "/pack/ui/inventory_bg.model3d.json.meta"
        val text = requireNotNull(javaClass.getResource(path)).readText()
        val metadata = ResourceYaml.parse(text, path).requireMap()

        assertEquals(true, (metadata["oversizedInGui"] as? Variant.Boolean)?.value)
    }
}
