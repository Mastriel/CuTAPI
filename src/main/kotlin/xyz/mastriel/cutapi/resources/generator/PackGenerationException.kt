package xyz.mastriel.cutapi.resources.generator

public class PackGenerationException(reason: String, causedBy: Throwable? = null) : Exception(reason, causedBy) {
}