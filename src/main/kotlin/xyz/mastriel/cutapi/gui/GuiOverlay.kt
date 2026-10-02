package xyz.mastriel.cutapi.gui

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.builtin.Texture2D

/**
 * Positioning metrics for an inventory screen overlay.
 *
 * Layer textures are always rendered at their native pixel dimensions. [canvasWidth] and
 * [canvasHeight] describe the underlying GUI coordinate space; they do not resize layers.
 */
public data class GuiOverlayProfile(
    public val name: String,
    public val canvasWidth: Int,
    public val canvasHeight: Int,
    public val bitmapAscent: Int = 13,
    public val horizontalOriginShift: Int = -8,
    public val verticalOffset: Int = 0,
    public val visibleTitleX: Int = 0,
    public val supportsTitleOverlay: Boolean = true,
) {
    public companion object {
        public fun chest(rows: Int): GuiOverlayProfile {
            require(rows in 1..6) { "Chest overlay rows must be between 1 and 6." }
            return GuiOverlayProfile("chest_$rows", 176, 114 + rows * 18)
        }

        public val Anvil: GuiOverlayProfile = GuiOverlayProfile("anvil", 176, 166)
        public val Barrel: GuiOverlayProfile = GuiOverlayProfile("barrel", 176, 168)
        public val Beacon: GuiOverlayProfile = GuiOverlayProfile("beacon", 230, 219)
        public val BlastFurnace: GuiOverlayProfile = GuiOverlayProfile("blast_furnace", 176, 166)
        public val BrewingStand: GuiOverlayProfile = GuiOverlayProfile("brewing_stand", 176, 166)
        public val CartographyTable: GuiOverlayProfile = GuiOverlayProfile("cartography_table", 176, 166)
        public val Crafter: GuiOverlayProfile = GuiOverlayProfile("crafter", 176, 194)
        public val CraftingTable: GuiOverlayProfile = GuiOverlayProfile("crafting_table", 176, 166)
        public val Dispenser: GuiOverlayProfile = GuiOverlayProfile("dispenser", 176, 166)
        public val Dropper: GuiOverlayProfile = GuiOverlayProfile("dropper", 176, 166)
        public val EnchantingTable: GuiOverlayProfile = GuiOverlayProfile("enchanting_table", 176, 166)
        public val EnderChest: GuiOverlayProfile = GuiOverlayProfile("ender_chest", 176, 168)
        public val Furnace: GuiOverlayProfile = GuiOverlayProfile("furnace", 176, 166)
        public val Grindstone: GuiOverlayProfile = GuiOverlayProfile("grindstone", 176, 166)
        public val Hopper: GuiOverlayProfile = GuiOverlayProfile("hopper", 176, 133)
        public val Lectern: GuiOverlayProfile = GuiOverlayProfile("lectern", 192, 192)
        public val Loom: GuiOverlayProfile = GuiOverlayProfile("loom", 176, 166)
        public val Merchant: GuiOverlayProfile = GuiOverlayProfile("merchant", 276, 166)
        public val PlayerInventory: GuiOverlayProfile = GuiOverlayProfile("player_inventory", 176, 166)
        public val ShulkerBox: GuiOverlayProfile = GuiOverlayProfile("shulker_box", 176, 168)
        public val SmithingTable: GuiOverlayProfile = GuiOverlayProfile("smithing_table", 176, 166)
        public val Smoker: GuiOverlayProfile = GuiOverlayProfile("smoker", 176, 166)
        public val Stonecutter: GuiOverlayProfile = GuiOverlayProfile("stonecutter", 176, 166)
    }
}

public data class GuiOverlayLayer(
    public val texture: ResourceRef<Texture2D>,
    public val x: Int = 0,
    public val y: Int = 0,
)

public data class GuiOverlaySpec(
    public val profile: GuiOverlayProfile,
    public val layers: List<GuiOverlayLayer>,
    /** Whether generic 9-column containers retain their vanilla background beneath these layers. */
    public val showBaseTexture: Boolean = true,
) {
    init {
        require(profile.supportsTitleOverlay) { "Overlay profile ${profile.name} does not support title overlays." }
        require(layers.size <= 128) { "A GUI overlay may contain at most 128 layers." }
    }
}

@GuiDslMarker
public class GuiOverlayBuilder internal constructor(
    defaultProfile: GuiOverlayProfile?,
) {
    public var profile: GuiOverlayProfile? = defaultProfile
    public var x: Int = 0
    public var y: Int = 0
    public var showBaseTexture: Boolean = true

    private val layers: MutableList<GuiOverlayLayer> = mutableListOf()

    public fun layer(texture: ResourceRef<Texture2D>, configure: GuiOverlayLayerBuilder.() -> Unit = {}) {
        val builder = GuiOverlayLayerBuilder(texture).apply(configure)
        layers += builder.build()
    }

    internal fun addPrimary(texture: ResourceRef<Texture2D>) {
        layers += GuiOverlayLayer(texture, x, y)
    }

    internal fun build(): GuiOverlaySpec = GuiOverlaySpec(
        profile = requireNotNull(profile) { "A GUI overlay profile is required." },
        layers = layers.toList(),
        showBaseTexture = showBaseTexture,
    )
}

@GuiDslMarker
public class GuiOverlayLayerBuilder internal constructor(public val texture: ResourceRef<Texture2D>) {
    public var x: Int = 0
    public var y: Int = 0

    internal fun build(): GuiOverlayLayer = GuiOverlayLayer(texture, x, y)
}

public object GuiOverlayComposer {
    public fun compose(
        guiId: Identifier,
        overlay: GuiOverlaySpec,
        visibleTitle: Component = Component.empty(),
    ): Component {
        val artifact = GuiOverlayCatalog.requireArtifact(guiId, overlay)
        var result: Component = if (overlay.showBaseTexture) Component.empty() else GuiBaseContainerOverlay.suppress()
        artifact.layerGlyphs.forEachIndexed { index, glyph ->
            val layer = overlay.layers[index]
            val shift = overlay.profile.horizontalOriginShift + layer.x
            result = result.append(spacing(artifact, shift))
                .append(
                    Component.text(glyph)
                        .font(artifact.fontKey)
                        .color(NamedTextColor.WHITE),
                )
                .append(spacing(artifact, -shift - overlay.profile.canvasWidth))
        }
        return result.append(visibleTitle.font(Key.key("minecraft", "default")))
    }

    private fun spacing(artifact: GuiOverlayArtifact, amount: Int): Component {
        if (amount == 0) return Component.empty()
        var remaining = amount
        var component: Component = Component.empty()
        val sign = if (remaining < 0) -1 else 1
        var magnitude = kotlin.math.abs(remaining)
        var bit = 1
        while (magnitude > 0) {
            if (magnitude and 1 == 1) {
                val glyph = artifact.spacingGlyphs.getValue(bit * sign)
                component = component.append(Component.text(glyph).font(artifact.fontKey))
            }
            magnitude = magnitude ushr 1
            bit = bit shl 1
        }
        return component
    }
}

internal data class GuiOverlayArtifact(
    val id: Identifier,
    val spec: GuiOverlaySpec,
    val fontKey: Key,
    val layerGlyphs: List<String>,
    val spacingGlyphs: Map<Int, String>,
)

internal object GuiOverlayCatalog {
    private const val LayerStart: Int = 0xE000
    private const val PositiveSpaceStart: Int = 0xE200
    private const val NegativeSpaceStart: Int = 0xE220
    private val artifacts: MutableMap<Identifier, GuiOverlayArtifact> = linkedMapOf()
    private var acceptingContributions: Boolean = true

    fun contribute(id: Identifier, spec: GuiOverlaySpec): GuiOverlayArtifact = synchronized(this) {
        check(acceptingContributions) {
            "Overlay GUI $id was constructed after resource-pack processing began. " +
                "Construct resource-backed GUI definitions during plugin startup."
        }
        val existing = artifacts[id]
        if (existing != null) {
            require(existing.spec == spec) {
                "GUI overlay id $id was contributed with conflicting declarations."
            }
            return existing
        }
        val sanitizedKey = id.key
            .replace(Regex("[^a-z0-9/._-]"), "_")
            .trim('/')
        require(sanitizedKey.isNotEmpty()) { "GUI overlay id $id produces an empty font path." }
        val layers = spec.layers.indices.map { codepoint -> Character.toString(LayerStart + codepoint) }
        val positive = (0..15).associate { power ->
            (1 shl power) to Character.toString(PositiveSpaceStart + power)
        }
        val negative = (0..15).associate { power ->
            -(1 shl power) to Character.toString(NegativeSpaceStart + power)
        }
        GuiOverlayArtifact(
            id = id,
            spec = spec,
            fontKey = Key.key(id.namespace, "gui/$sanitizedKey"),
            layerGlyphs = layers,
            spacingGlyphs = positive + negative,
        ).also { artifacts[id] = it }
    }

    fun requireArtifact(id: Identifier, spec: GuiOverlaySpec): GuiOverlayArtifact = synchronized(this) {
        val artifact = artifacts[id] ?: error("No overlay artifact exists for GUI $id.")
        require(artifact.spec == spec) { "GUI $id does not match its contributed overlay artifact." }
        artifact
    }

    fun closeContributions(): List<GuiOverlayArtifact> = synchronized(this) {
        acceptingContributions = false
        artifacts.values.toList()
    }

    fun all(): List<GuiOverlayArtifact> = synchronized(this) { artifacts.values.toList() }

    fun isClaimed(texture: ResourceRef<Texture2D>): Boolean = synchronized(this) {
        artifacts.values.any { artifact -> artifact.spec.layers.any { it.texture == texture } }
    }

    fun removeNamespace(namespace: String) = synchronized(this) {
        artifacts.keys.removeIf { it.namespace == namespace }
    }

    internal fun resetForTests() = synchronized(this) {
        artifacts.clear()
        acceptingContributions = true
    }
}
