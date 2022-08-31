package xyz.mastriel.cutapi.items.components

import de.tr7zw.changeme.nbtapi.NBTCompound
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.registry.idOrNull
import xyz.mastriel.cutapi.utils.nbt
import kotlin.reflect.KClass

private class ItemComponentHandler (private val itemStack: CuTItemStack) : ComponentHandler {
    val compound = itemStack.handle.nbt.getOrCreateCompound("CuTAPIComponents")!!

    private val components by lazy {
        val components = mutableSetOf<ItemComponent>()

        for (id in compound.keys.mapNotNull(::idOrNull)) {

            val componentClass = ItemComponent.get(id)
            val componentInstance = ItemComponent.create(componentClass)
            componentInstance.bind(compound)

            components += componentInstance
        }

        return@lazy components
    }

    override fun addComponent(component: ItemComponent): Boolean {
        if (getComponentOrNull(component::class)?.id == component.id) return false

        val wasAdded = components.add(component)
        if (!wasAdded) return false

        component.bind(getComponentCompound(component))
        component.onApply(itemStack)
        return true
    }

    override fun removeComponent(component: KClass<out ItemComponent>): Boolean {
        val componentInstance = getComponentOrNull(component) ?: return false

        val wasRemoved = components.removeIf { it.id == componentInstance.id }
        if (!wasRemoved) return false

        deleteComponentNbt(componentInstance)
        return true
    }

    override fun hasComponent(component: KClass<out ItemComponent>): Boolean {
        return getComponentOrNull(component) != null
    }

    override fun <T : ItemComponent> getComponent(component: KClass<T>): T {
        return getComponentOrNull(component) ?: error("Component ${component.qualifiedName} not found in component list!")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ItemComponent> getComponentOrNull(component: KClass<T>): T? {
        return components.find { it::class == component } as? T?
    }

    override fun getAllComponents(): Set<ItemComponent> {
        return components.toSet()
    }

    private fun deleteComponentNbt(component: ItemComponent) {
        compound.removeKey("${component.id}")
    }

    private fun getComponentCompound(component: ItemComponent): NBTCompound {
        return compound.getOrCreateCompound("${component.id}")
    }
}

fun itemComponentHandler(itemStack: CuTItemStack) : ComponentHandler = ItemComponentHandler(itemStack)