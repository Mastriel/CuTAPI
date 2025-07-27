package xyz.mastriel.cutapi.resources.pack

/**
 * Data class containing information about a generated resource pack.
 *
 * @property packUrl The URL where the pack can be downloaded.
 * @property packHash The hash of the pack for validation.
 */
public data class PackInfo(val packUrl: String, val packHash: String)