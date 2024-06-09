package xyz.mastriel.cutapi.item

import kotlinx.serialization.*
import net.kyori.adventure.text.*
import net.kyori.adventure.text.serializer.gson.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.pdc.tags.converters.*

public object LoreTagConverter : TagConverter<ByteArray, ItemLore>(ByteArray::class, ItemLore::class) {
    public val objectConverter: ObjectTagConverter<ItemLore> =
        ObjectTagConverter(ItemLore::class, ItemLore.serializer())

    override fun fromPrimitive(primitive: ByteArray): ItemLore {
        return objectConverter.fromPrimitive(primitive)
    }

    override fun toPrimitive(complex: ItemLore): ByteArray {
        return objectConverter.toPrimitive(complex)
    }
}

@Serializable
public class ItemLore {

    @SerialName("components")
    private val components: MutableList<String> = mutableListOf()

    public var displayLoreVisible: Boolean = true

    public fun append(vararg components: Component): Unit = append(components.toList())

    public fun append(components: Collection<Component>) {
        this.components += components.serialized()
    }

    public fun set(vararg components: Component): Unit = set(components.toList())

    public fun set(components: Collection<Component>) {
        this.components.clear()
        this.components += components.toList().serialized()
    }

    public fun get(): List<Component> = components.map { GsonComponentSerializer.gson().deserialize(it) }


    private fun Collection<Component>.serialized(): List<String> {
        return map { GsonComponentSerializer.gson().serialize(it) }
    }
}

public fun TagContainer.loreTag(name: String): NotNullTag<ByteArray, ItemLore> =
    NotNullTag(name, this, ItemLore(), LoreTagConverter)

public var CuTItemStack.displayLoreVisible: Boolean
    get() = lore.displayLoreVisible
    set(value) {
        lore.displayLoreVisible = value; lore = lore
    }


public fun CuTItemStack.setLore(vararg components: Component): Unit = lore.set(*components).also { lore = lore }
public fun CuTItemStack.setLore(components: Collection<Component>): Unit = lore.set(components).also { lore = lore }

public fun CuTItemStack.appendLore(vararg components: Component): CuTItemStack =
    lore.append(*components).also { lore = lore }.let { this }

public fun CuTItemStack.appendLore(components: Collection<Component>): CuTItemStack =
    lore.append(components).also { lore = lore }.let { this }
