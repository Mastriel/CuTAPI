package xyz.mastriel.cutapi.item

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.pdc.tags.NotNullTag
import xyz.mastriel.cutapi.pdc.tags.TagContainer
import xyz.mastriel.cutapi.pdc.tags.converters.ObjectTagConverter
import xyz.mastriel.cutapi.pdc.tags.converters.TagConverter

object LoreTagConverter : TagConverter<ByteArray, ItemLore>(ByteArray::class, ItemLore::class) {
    val objectConverter = ObjectTagConverter(ItemLore::class, ItemLore.serializer())

    override fun fromPrimitive(primitive: ByteArray): ItemLore {
        return objectConverter.fromPrimitive(primitive)
    }

    override fun toPrimitive(complex: ItemLore): ByteArray {
        return objectConverter.toPrimitive(complex)
    }
}

@Serializable
class ItemLore {

    @SerialName("components")
    private val components: MutableList<String> = mutableListOf()

    var displayLoreVisible: Boolean = true

    fun append(vararg components: Component) = append(components.toList())

    fun append(components: Collection<Component>) {
        this.components += components.serialized()
        ItemStack(Material.PAPER).lore()
    }

    fun set(vararg components: Component) = set(components.toList())

    fun set(components: Collection<Component>) {
        this.components.clear()
        this.components += components.toList().serialized()
    }

    fun get() = components.map { GsonComponentSerializer.gson().deserialize(it) }


    private fun Collection<Component>.serialized() : List<String> {
        return map { GsonComponentSerializer.gson().serialize(it) }
    }
}

fun TagContainer.loreTag(name: String) = NotNullTag(name, this, ItemLore(), LoreTagConverter)

var CuTItemStack.displayLoreVisible
    get() = lore.displayLoreVisible
    set(value) { lore.displayLoreVisible = value; lore = lore }


fun CuTItemStack.setLore(vararg components: Component) = lore.set(*components).also { lore = lore }
fun CuTItemStack.setLore(components: Collection<Component>) = lore.set(components).also { lore = lore }

fun CuTItemStack.appendLore(vararg components: Component) = lore.append(*components).also { lore = lore }.let { this }
fun CuTItemStack.appendLore(components: Collection<Component>) = lore.append(components).also { lore = lore }.let { this }
