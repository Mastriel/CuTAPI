package xyz.mastriel.cutapi.item.recipe

internal class CraftingRecipeDebugGrid(val size: Int) {

    val grid = MutableList(size) { ' ' }
    
    fun set(index: Int, char: Char) {
        grid[index] = char
    }

    fun print(label: String, out: (String) -> Unit) {
        out("$label grid:")
        out(grid.subList(0, 2).joinToString(""))
        out(grid.subList(3, 5).joinToString(""))
        out(grid.subList(6, 8).joinToString(""))
    }
}