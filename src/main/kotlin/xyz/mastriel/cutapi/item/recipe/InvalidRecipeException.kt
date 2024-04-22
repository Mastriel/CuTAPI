package xyz.mastriel.cutapi.item.recipe

import xyz.mastriel.cutapi.registry.*

class InvalidRecipeException(id: Identifier, message: String) : IllegalStateException("$id - $message")