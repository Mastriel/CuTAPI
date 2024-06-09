package xyz.mastriel.cutapi.item.recipe

import xyz.mastriel.cutapi.registry.*

public class InvalidRecipeException(id: Identifier, message: String) : IllegalStateException("$id - $message")