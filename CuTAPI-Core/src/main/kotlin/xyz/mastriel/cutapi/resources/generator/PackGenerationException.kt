package xyz.mastriel.cutapi.resources.generator

class PackGenerationException(reason: String, causedBy: Throwable? = null) : Exception(reason, causedBy) {
}