package xyz.mastriel.cutapi.resources.pack

import xyz.mastriel.cutapi.utils.*

public object PackConfig {
    public val PackPng: String by cutConfigValue("pack-png", "pack.png")
    public val PackDescription: String by cutConfigValue("pack-description", "CuTAPI Generated Resource Pack")
    public val PackName: String by cutConfigValue("generated-pack-name", "pack.zip")
}