@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.block.nativeblock

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.block.state.properties.Property
import xyz.mastriel.cutapi.block.BlockStateDefinition
import xyz.mastriel.cutapi.block.BlockStateType
import xyz.mastriel.cutapi.block.CustomBlockState

internal class NativeBlockStateSchema(public val definition: BlockStateDefinition) {
    private val properties: Map<BlockStateType<*>, Property<*>> = definition.declarations
        .mapNotNull { type -> createProperty(type)?.let { type to it } }
        .toMap(linkedMapOf())

    fun contribute(builder: StateDefinition.Builder<Block, BlockState>) {
        if (properties.isNotEmpty()) builder.add(*properties.values.toTypedArray())
    }

    fun toCustom(state: BlockState): CustomBlockState {
        val values = linkedMapOf<BlockStateType<*>, Any>()
        definition.declarations.forEach { type ->
            val value: Any = when (type) {
                is BlockStateType.Boolean -> propertyValue<kotlin.Boolean>(state, properties.getValue(type))
                is BlockStateType.Enum<*> -> {
                    val index = if (type.values.size == 1) 0 else propertyValue<Int>(state, properties.getValue(type))
                    type.values[index]
                }
            }
            values[type] = value
        }
        return definition.state(values)
    }

    fun toNative(base: BlockState, custom: CustomBlockState): BlockState {
        var state = base
        definition.declarations.forEach { type ->
            val property = properties[type] ?: return@forEach
            val customValue = custom.asMap().getValue(type)
            state = when (type) {
                is BlockStateType.Boolean -> setProperty(state, property, customValue as kotlin.Boolean)
                is BlockStateType.Enum<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val values = type.values as List<Any>
                    setProperty(state, property, values.indexOf(customValue))
                }
            }
        }
        return state
    }

    private fun createProperty(type: BlockStateType<*>): Property<*>? = when (type) {
        is BlockStateType.Boolean -> BooleanProperty.create(type.name)
        is BlockStateType.Enum<*> -> if (type.values.size == 1) {
            null
        } else {
            IntegerProperty.create(type.name, 0, type.values.lastIndex)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Comparable<T>> propertyValue(state: BlockState, property: Property<*>): T =
        state.getValue(property as Property<T>)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Comparable<T>> setProperty(
        state: BlockState,
        property: Property<*>,
        value: T,
    ): BlockState = state.setValue(property as Property<T>, value)
}

internal object NativeBlockConstruction {
    private val currentSchema: ThreadLocal<NativeBlockStateSchema?> = ThreadLocal()

    fun schema(): NativeBlockStateSchema =
        currentSchema.get() ?: error("A native custom block was constructed outside its registry transaction.")

    fun <T> with(schema: NativeBlockStateSchema, construct: () -> T): T {
        check(currentSchema.get() == null) { "Nested native custom block construction is not supported." }
        currentSchema.set(schema)
        return try {
            construct()
        } finally {
            currentSchema.remove()
        }
    }
}
