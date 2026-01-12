package xyz.mastriel.cutapi.block

import net.kyori.adventure.text.*
import net.kyori.adventure.text.format.*
import xyz.mastriel.cutapi.utils.*

public abstract class BlockStateDeclaration {

    public abstract fun getPermutations(): List<BlockStateValue>
    public abstract fun getPermutationsCount(): Int;

    public abstract val stateName: String;

    public class Boolean(override val stateName: String) : BlockStateDeclaration() {
        override fun getPermutations(): List<BlockStateValue.Boolean> {
            return listOf(BlockStateValue.Boolean.True, BlockStateValue.Boolean.False)
        }

        override fun getPermutationsCount(): Int {
            return 2
        }
    }

    public class Enum<T : kotlin.Enum<T>>(
        public val enumClass: kotlin.reflect.KClass<T>,
        override val stateName: String
    ) :
        BlockStateDeclaration() {

        private var _cachedPermutations: List<BlockStateValue.Enum<T>>? = null

        override fun getPermutations(): List<BlockStateValue.Enum<T>> {
            if (_cachedPermutations == null) {
                _cachedPermutations = enumClass.java.enumConstants
                    .map { BlockStateValue.Enum(it) }
            }
            return _cachedPermutations!!
        }

        override fun getPermutationsCount(): Int {
            return enumClass.java.enumConstants.size
        }
    }

    public companion object {
        public inline fun <reified T : kotlin.Enum<T>> Enum(name: String): Enum<T> {
            return Enum(T::class, name)
        }
    }
}

public class BlockStates {
    private val stateDeclarations: MutableList<BlockStateDeclaration> = mutableListOf()

    public fun add(state: BlockStateDeclaration) {
        stateDeclarations += state
    }

    public fun getStates(): List<BlockStateDeclaration> {
        return stateDeclarations.toList()
    }

    public fun getTotalPermutationsCount(): Int {
        return stateDeclarations.fold(1) { acc, state -> acc * state.getPermutationsCount() }
    }

    public fun createPermutations(): List<Map<BlockStateDeclaration, BlockStateValue>> {
        val permutations = mutableListOf<Map<BlockStateDeclaration, BlockStateValue>>()

        fun backtrack(
            index: Int,
            currentPermutation: MutableMap<BlockStateDeclaration, BlockStateValue>
        ) {
            if (index == stateDeclarations.size) {
                permutations.add(currentPermutation.toMap())
                return
            }

            val stateDeclaration = stateDeclarations[index]
            for (value in stateDeclaration.getPermutations()) {
                currentPermutation[stateDeclaration] = value
                backtrack(index + 1, currentPermutation)
                currentPermutation.remove(stateDeclaration)
            }
        }

        backtrack(0, mutableMapOf())
        return permutations
    }
}

public interface BlockStateValue {

    public val displayName: Component

    public class Boolean private constructor(bool: kotlin.Boolean) : BlockStateValue {
        override val displayName: Component =
            bool.toString().colored.color(if (bool) NamedTextColor.GREEN else NamedTextColor.RED)

        public companion object {
            public val True: Boolean = Boolean(true)
            public val False: Boolean = Boolean(false)

            public fun from(value: kotlin.Boolean): Boolean {
                return if (value) True else False
            }
        }
    }

    public class Enum<T : kotlin.Enum<T>> public constructor(public val value: T) : BlockStateValue {
        override val displayName: Component =
            value.name.colored.color(NamedTextColor.AQUA)
    }
}
