package xyz.mastriel.cutapi.block

import kotlin.reflect.KClass

/** A typed property that contributes to a custom block's native state definition. */
public sealed class BlockStateType<T : Any>(
    public val name: String,
) {
    init {
        require(Name.matches(name)) {
            "Block state name '$name' must contain only lower-case letters, digits, and underscores."
        }
    }

    public abstract val values: List<T>

    public abstract fun canonicalValue(value: T): String

    internal fun requireValue(value: Any): T {
        @Suppress("UNCHECKED_CAST")
        val typed = value as? T
            ?: error("Value $value is not valid for block state '$name'.")
        require(typed in values) { "Value $value is not valid for block state '$name'." }
        return typed
    }

    public class Boolean(name: String) : BlockStateType<kotlin.Boolean>(name) {
        override val values: List<kotlin.Boolean> = listOf(false, true)

        override fun canonicalValue(value: kotlin.Boolean): String = value.toString()
    }

    public class EnumType<T : kotlin.Enum<T>>(
        public val enumClass: KClass<T>,
        name: String,
    ) : BlockStateType<T>(name) {
        override val values: List<T> = enumClass.java.enumConstants.toList()

        init {
            require(values.isNotEmpty()) { "Block state enum ${enumClass.qualifiedName} has no values." }
        }

        override fun canonicalValue(value: T): String = value.name.lowercase()
    }

    public companion object {
        private val Name: Regex = Regex("[a-z0-9_]+")

        /** Preferred enum-state factory; the enum class is inferred from the reified type. */
        public inline fun <reified T : kotlin.Enum<T>> Enum(name: String): EnumType<T> =
            Enum(T::class, name)

        /** Explicit enum-state factory for call sites that already hold a [KClass]. */
        public fun <T : kotlin.Enum<T>> Enum(enumClass: KClass<T>, name: String): EnumType<T> =
            EnumType(enumClass, name)
    }
}

/** One canonical native custom-block state. */
public class CustomBlockState internal constructor(
    values: Map<BlockStateType<*>, Any>,
) {
    private val stateValues: Map<BlockStateType<*>, Any> = values.toMap()

    public operator fun <T : Any> get(type: BlockStateType<T>): T =
        type.requireValue(stateValues[type] ?: error("Block state '${type.name}' is not defined."))

    public fun contains(type: BlockStateType<*>): Boolean = type in stateValues

    public fun asMap(): Map<BlockStateType<*>, Any> = stateValues.toMap()

    public fun canonicalValues(): String = stateValues.entries
        .sortedBy { it.key.name }
        .joinToString(",") { (type, value) ->
            @Suppress("UNCHECKED_CAST")
            val untyped = type as BlockStateType<Any>
            "${type.name}=${untyped.canonicalValue(value)}"
        }

    override fun equals(other: Any?): kotlin.Boolean =
        other is CustomBlockState && stateValues == other.stateValues

    override fun hashCode(): Int = stateValues.hashCode()

    override fun toString(): String = "CustomBlockState(${canonicalValues()})"
}

/** Immutable state schema and its complete native-state permutation set. */
public class BlockStateDefinition internal constructor(
    declarations: List<BlockStateType<*>>,
    defaults: Map<BlockStateType<*>, Any>,
) {
    public val declarations: List<BlockStateType<*>> = declarations.toList()
    private val defaultValues: Map<BlockStateType<*>, Any> = defaults.toMap()

    public val defaultState: CustomBlockState = CustomBlockState(defaultValues)

    public val permutations: List<CustomBlockState> = createPermutations()

    public val permutationCount: Int get() = permutations.size

    public fun <T : Any> default(type: BlockStateType<T>): T = defaultState[type]

    public fun state(values: Map<BlockStateType<*>, Any>): CustomBlockState {
        val unknown = values.keys - declarations.toSet()
        require(unknown.isEmpty()) { "State contains undefined properties: ${unknown.joinToString { it.name }}." }
        val resolved = defaultValues.toMutableMap()
        values.forEach { (type, value) -> resolved[type] = type.requireValue(value) }
        return CustomBlockState(resolved)
    }

    private fun createPermutations(): List<CustomBlockState> {
        if (declarations.isEmpty()) return listOf(CustomBlockState(emptyMap()))
        val permutations = mutableListOf<CustomBlockState>()

        fun visit(index: Int, values: MutableMap<BlockStateType<*>, Any>) {
            if (index == declarations.size) {
                permutations += CustomBlockState(values)
                return
            }
            val declaration = declarations[index]
            declaration.values.forEach { value ->
                values[declaration] = value
                visit(index + 1, values)
            }
            values.remove(declaration)
        }

        visit(0, linkedMapOf())
        return permutations
    }

    public companion object {
        public val Empty: BlockStateDefinition = BlockStateDefinition(emptyList(), emptyMap())
    }
}

/** DSL that atomically defines each state property and its default producer. */
public class BlockStates {
    private val declarations: MutableList<BlockStateType<*>> = mutableListOf()
    private val defaults: MutableMap<BlockStateType<*>, Any> = linkedMapOf()

    public fun <T : Any> define(type: BlockStateType<T>, default: () -> T) {
        require(declarations.none { it.name == type.name }) {
            "Block state '${type.name}' is already defined."
        }
        val value = default()
        type.requireValue(value)
        declarations += type
        defaults[type] = value
    }

    public fun build(): BlockStateDefinition = BlockStateDefinition(declarations, defaults)

    public fun getTotalPermutationsCount(): Int = build().permutationCount

    public fun createPermutations(): List<CustomBlockState> = build().permutations
}

public fun blockStates(configure: BlockStates.() -> Unit = {}): BlockStateDefinition =
    BlockStates().apply(configure).build()
