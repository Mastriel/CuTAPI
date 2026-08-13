package xyz.mastriel.cutapi.block

import org.bukkit.block.*
import org.bukkit.entity.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*

/** Built-in state values for blocks that face along the horizontal plane. */
public enum class HorizontalFacingState {
    North,
    East,
    South,
    West;

    public companion object : BlockStateType.EnumType<HorizontalFacingState>(
        HorizontalFacingState::class,
        "facing",
    )
}

/** Built-in state values for blocks that may face any of Minecraft's six directions. */
public enum class FacingState {
    Down,
    Up,
    North,
    South,
    West,
    East;

    public companion object : BlockStateType.EnumType<FacingState>(
        FacingState::class,
        "facing",
    )
}

/** Information available when a block definition chooses its initial facing. */
public data class PlacementFacingContext(
    public val placer: Player,
    public val block: Block,
)

/** Chooses the horizontal face assigned to an oriented block when it is placed. */
public fun interface PlacementFacing {
    public fun resolve(context: PlacementFacingContext): BlockFace

    public companion object {
        /** Makes the block's north-authored front face point back towards its placer. */
        public val TowardsPlacer: PlacementFacing = PlacementFacing { context ->
            context.placer.facing.oppositeFace
        }

        /** Makes the block's north-authored front face point in the direction its placer faces. */
        public val AwayFromPlacer: PlacementFacing = PlacementFacing { context ->
            context.placer.facing
        }
    }
}

/** Describes how one enum block state controls horizontal orientation. */
public class BlockOrientation internal constructor(
    public val facingState: BlockStateType.EnumType<*>,
    public val placementFacing: PlacementFacing,
) {
    private val valuesByFace: Map<BlockFace, Any> = CardinalFaces.associateWith { face ->
        facingState.values.single { value ->
            enumName(value) == face.name.lowercase()
        }
    }

    public fun facing(state: CustomBlockState): BlockFace {
        val value = state.asMap()[facingState]
            ?: error("Block state '${facingState.name}' is not defined.")
        val name = enumName(value)
        return CardinalFaces.singleOrNull { it.name.lowercase() == name }
            ?: error("Block state '${facingState.name}' value '$value' is not a horizontal direction.")
    }

    internal fun stateForPlacement(
        states: BlockStateDefinition,
        state: CustomBlockState,
        context: PlacementFacingContext,
    ): CustomBlockState = withFacing(states, state, placementFacing.resolve(context))

    internal fun withFacing(
        states: BlockStateDefinition,
        state: CustomBlockState,
        face: BlockFace,
    ): CustomBlockState {
        val value = requireNotNull(valuesByFace[face]) {
            "PlacementFacing for '${facingState.name}' returned non-horizontal face $face."
        }
        return states.state(state.asMap() + (facingState to value))
    }

    /** Minecraft blockstate Y rotation for a model authored with its front facing north. */
    internal fun modelYRotation(state: CustomBlockState): Int = when (facing(state)) {
        BlockFace.NORTH -> 0
        BlockFace.EAST -> 90
        BlockFace.SOUTH -> 180
        BlockFace.WEST -> 270
        else -> error("Horizontal orientation resolved to a non-horizontal face.")
    }

    private fun enumName(value: Any): String = (value as Enum<*>).name.lowercase()

    public companion object : DebugView<BlockOrientation> by debugView(id("cutapi:block_orientation"), {
        property("facingState", VariantSerializer.String) {
            it.facingState.name
        }
        property("placementFacing", VariantSerializer.String) {
            when (it.placementFacing) {
                PlacementFacing.TowardsPlacer -> {
                    "TowardsPlacer"
                }

                PlacementFacing.AwayFromPlacer -> {
                    "AwayFromPlacer"
                }

                else -> {
                    "Dynamic"
                }
            }
        }

    }) {
        private val CardinalFaces: List<BlockFace> =
            listOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)
    }
}

/** DSL for declaring a block's semantic orientation and placement behavior. */
public class BlockOrientationBuilder {
    private var facingState: BlockStateType.EnumType<*>? = null
    private var placementFacing: PlacementFacing = PlacementFacing.TowardsPlacer

    public fun <T : Enum<T>> horizontal(state: BlockStateType.EnumType<T>) {
        check(facingState == null) { "A block orientation state is already defined." }
        facingState = state
    }

    /** Uses CuTAPI's built-in `facing` horizontal state. */
    public fun horizontal() {
        horizontal(HorizontalFacingState)
    }

    public fun placementFacing(placementFacing: PlacementFacing) {
        this.placementFacing = placementFacing
    }

    internal fun build(states: BlockStateDefinition): BlockOrientation {
        val state = requireNotNull(facingState) {
            "orientation { } must declare a state with horizontal(...)."
        }
        require(state in states.declarations) {
            "Orientation state '${state.name}' must be defined in states { } before the descriptor is built."
        }
        val names = state.values.map { it.name.lowercase() }.toSet()
        val expected = setOf("north", "east", "south", "west")
        require(names == expected) {
            "Horizontal orientation state '${state.name}' must contain exactly North, East, South, and West; " +
                "found ${state.values.joinToString { it.name }}."
        }
        return BlockOrientation(state, placementFacing)
    }
}
