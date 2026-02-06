package xyz.mastriel.cutapi.resources.generator

/**
 * Exception thrown when resource pack generation fails.
 *
 * @param reason The reason for the failure.
 * @param causedBy The underlying cause of the failure, if any.
 */
public class PackGenerationException(reason: String, causedBy: Throwable? = null) : Exception(reason, causedBy) {
}