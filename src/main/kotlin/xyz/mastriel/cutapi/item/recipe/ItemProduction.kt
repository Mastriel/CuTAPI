package xyz.mastriel.cutapi.item.recipe

import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*

/** A produced item identity and the number of items produced. */
public data class ItemProductionOutput(
    public val identity: ItemIdentity,
    public val amount: Int,
) {
    init {
        require(amount > 0) { "Production output amounts must be positive." }
    }
}

/** One concrete way to satisfy an ingredient slot. */
public data class ItemProductionChoice(
    public val identity: ItemIdentity,
    public val amount: Int = 1,
    public val returnedItems: List<ItemProductionOutput> = emptyList(),
) {
    init {
        require(amount > 0) { "Production ingredient amounts must be positive." }
    }
}

/** Alternative choices which may satisfy the same ingredient slot. */
public data class ItemProductionIngredient(
    public val choices: Set<ItemProductionChoice>,
) {
    init {
        require(choices.isNotEmpty()) { "A production ingredient must have at least one choice." }
    }
}

/**
 * A deterministic item production operation.
 *
 * [kind] is an extensible identifier so consumers can apply policy to recipe families without
 * CuTAPI knowing about their domain-specific rules.
 */
public data class ItemProductionRecipe(
    override val id: Identifier,
    public val kind: Identifier,
    public val ingredients: List<ItemProductionIngredient>,
    public val primaryOutput: ItemProductionOutput,
) : Identifiable {
    init {
        require(ingredients.isNotEmpty()) { "Production recipe $id must have at least one ingredient." }
    }
}

/** Supplies production operations to an [ItemProductionCatalogSnapshot]. */
public interface ItemProductionSource : Identifiable {
    /** Higher-priority sources replace lower-priority recipes with the same identifier. */
    public val priority: RegistryPriority
        get() = RegistryPriority.Medium

    public fun productions(): Sequence<ItemProductionRecipe>

    public companion object : IdentifierRegistry<ItemProductionSource>(
        id("cutapi:registry/item_production_source"),
    )
}

/** Well-known production kinds emitted by CuTAPI's built-in sources. */
public object ItemProductionType {
    public val CraftingShaped: Identifier = id("minecraft:crafting_shaped")
    public val CraftingShapeless: Identifier = id("minecraft:crafting_shapeless")
    public val CraftingTransmute: Identifier = id("minecraft:crafting_transmute")
    public val Smelting: Identifier = id("minecraft:smelting")
    public val Blasting: Identifier = id("minecraft:blasting")
    public val Smoking: Identifier = id("minecraft:smoking")
    public val CampfireCooking: Identifier = id("minecraft:campfire_cooking")
    public val Stonecutting: Identifier = id("minecraft:stonecutting")
    public val SmithingTransform: Identifier = id("minecraft:smithing_transform")
}

@ConsistentCopyVisibility
public data class ItemProductionCatalogSnapshot internal constructor(
    public val recipes: List<ItemProductionRecipe>,
) {
    public val recipesByOutput: Map<ItemIdentity, List<ItemProductionRecipe>> =
        recipes.groupBy { it.primaryOutput.identity }
}

/** Creates immutable, deterministic views of all registered production sources. */
public object ItemProductionCatalog {
    public fun snapshot(): ItemProductionCatalogSnapshot {
        check(!ItemProductionSource.isOpen) {
            "Item production sources must be initialized before taking a snapshot."
        }

        return snapshot(ItemProductionSource.getAllValues())
    }

    internal fun snapshot(sources: Collection<ItemProductionSource>): ItemProductionCatalogSnapshot {

        data class Selected(
            val source: ItemProductionSource,
            val recipe: ItemProductionRecipe,
        )

        val selected = linkedMapOf<Identifier, Selected>()
        for (source in sources.sortedBy { it.priority.value }) {
            for (recipe in source.productions()) {
                val previous = selected[recipe.id]
                check(previous == null || previous.source.priority != source.priority) {
                    "Production recipe ${recipe.id} is supplied at the same priority by " +
                        "${previous?.source?.id} and ${source.id}."
                }
                selected[recipe.id] = Selected(source, recipe)
            }
        }

        return ItemProductionCatalogSnapshot(
            selected.values.map(Selected::recipe).sortedBy { it.id.toString() },
        )
    }
}
