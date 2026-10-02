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
            "/pack/ui/inventory_bg.model.json.meta" to "cutapi:minecraft_model",
            "/pack/ui/inventory_bg.png.meta" to "cutapi:texture2d",
            "/pack/ui/progress_arrow_empty.png.meta" to "cutapi:texture2d",
            "/pack/ui/progress_arrow_full.png.meta" to "cutapi:texture2d",
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
        assertNotNull(document.requireMap()["generate"])
    }

    @Test
    fun `inventory background opts into oversized GUI rendering`() {
        val path = "/pack/ui/inventory_bg.model.json.meta"
        val text = requireNotNull(javaClass.getResource(path)).readText()
        val metadata = ResourceYaml.parse(text, path).requireMap()

        val generate = metadata.getValue("generate").requireList().single().requireMap()
        assertEquals("cutapi:item_model", (generate[SCHEMA_TYPE_DISCRIMINATOR] as? Variant.String)?.value)
        assertEquals(true, (generate["oversized_in_gui"] as? Variant.Boolean)?.value)
    }
}
