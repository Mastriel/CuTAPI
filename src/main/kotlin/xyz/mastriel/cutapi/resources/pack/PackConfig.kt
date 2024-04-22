package xyz.mastriel.cutapi.resources.pack

import xyz.mastriel.cutapi.utils.cutConfigValue

object PackConfig {
    val PackPng by cutConfigValue("pack-png", "pack.png")
    val PackDescription by cutConfigValue("pack-description", "CuTAPI Generated Resource Pack")
    val PackName by cutConfigValue("generated-pack-name", "pack.zip")
}