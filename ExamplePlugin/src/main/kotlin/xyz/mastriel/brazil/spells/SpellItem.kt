package xyz.mastriel.brazil.spells

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import xyz.mastriel.brazil.utils.ticksToSeconds
import xyz.mastriel.cutapi.items.*
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry
import xyz.mastriel.cutapi.utils.Color
import xyz.mastriel.cutapi.utils.colored
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault
import xyz.mastriel.cutapi.utils.personalized.or
import xyz.mastriel.cutapi.utils.personalized.personalized
import xyz.mastriel.cutapi.utils.personalized.withViewer

open class SpellItem(
    material: Material, private val spell: Spell
) : Spell by spell, CustomItem(spell.id, material) {

    override val id: Identifier
        get() = spell.id

    constructor(
        name: PersonalizedWithDefault<Component>,
        material: Material,
        castTime: Long,
        cooldown: Long,
        flags: Collection<SpellFlag>,
        castMethod: CastMethod,
        id: Identifier
    ) : this(material, object : Spell {
        override val name: PersonalizedWithDefault<Component> = name
        override val castTime: Long = castTime
        override val cooldown: Long = cooldown
        override val flags: Collection<SpellFlag> = flags
        override val castMethod: CastMethod = castMethod
        override val id: Identifier = id
    })

    open fun getLore(item: CuTItemStack, viewer: Player?): List<Component>? = null

    /**
     * Use `super.descriptor with {}` to add new behavior, lest you want weird things to happen.
     */
    override val descriptor: ItemDescriptor = itemDescriptor {
        val color = Color.Elethium.textColor


        name = personalized { this@SpellItem.name.withViewer(it).color(color) } or
                spell.name.withViewer(null).color(color)

        behavior(SpellBehavior(this@SpellItem))

        description { setLore(this, Color.Solarium) }
    }


    internal fun setLore(builder: DescriptionBuilder, color: Color) {
        builder.apply {
            emptyLine()
            val lore = getLore(itemStack, viewer) ?: listOf("A basic spell.".colored)
            for (line in lore) {
                textComponent(line.color(color.textColor))
            }
            emptyLine()
            textComponent("&8${ticksToSeconds(castTime)} cast time".colored)
            textComponent("&8${ticksToSeconds(cooldown)} cooldown".colored)
            for (flag in flags.filter { it.displayLore != null }) {
                textComponent(flag.displayLore!!.colored)
            }
        }
    }

    fun getSpellbook(): CustomItem {
        return spellbooks.get(id.appendSubId("book"))
    }

    companion object : IdentifierRegistry<SpellItem>() {

        private val spellbooks = IdentifierRegistry<CustomItem>()

        @Suppress("RemoveRedundantQualifierName")
        override fun register(item: SpellItem): SpellItem {
            CustomItem.register(item)
            val spellbook = createSpellbookMaterial(item)
            spellbooks.register(spellbook)
            CustomItem.register(spellbook)
            return super.register(item)
        }

        private fun createSpellbookMaterial(spell: SpellItem) =
            customItem(spell.id.appendSubId("book"), Material.BOOK) {
                val color = Color.Elethium.textColor

                name = personalized { player -> (spell.name withViewer player).color(color) } or
                        spell.name.withViewer(null).color(color)

                behavior(SpellbookLearnBehavior(spell))

                description { spell.setLore(this, Color.Elethium) }
            }
    }

}

fun spellItem(
    material: Material,
    spell: Spell,
    block: ItemDescriptorBuilder.() -> Unit
): SpellItem {
    return object : SpellItem(material, spell) {
        override val descriptor: ItemDescriptor = super.descriptor with block
    }
}

fun registerSpellItem(
    material: Material,
    spell: Spell,
    block: ItemDescriptorBuilder.() -> Unit
) = spellItem(material, spell, block).also(SpellItem.Companion::register)
