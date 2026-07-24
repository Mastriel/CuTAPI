package xyz.mastriel.cutapi.resources

import xyz.mastriel.cutapi.testing.*
import java.io.*
import kotlin.test.*

class ResourceMetadataExtensionTest : MockBukkitTest() {
    @Test
    fun `only dot meta is an attached metadata sidecar`() {
        assertTrue(File("widget.png.meta").isResourceMetadataSidecar())
        assertFalse(File("widget.png.meta.yaml").isResourceMetadataSidecar())
        assertFalse(File("widget.yaml").isResourceMetadataSidecar())
        assertFalse(File("widget.yml").isResourceMetadataSidecar())
        assertFalse(File("apply.meta.folder").isResourceMetadataSidecar())
    }
}
