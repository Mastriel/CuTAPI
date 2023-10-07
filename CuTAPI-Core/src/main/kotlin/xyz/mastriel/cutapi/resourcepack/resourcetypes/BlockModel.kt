package xyz.mastriel.cutapi.resourcepack.resourcetypes

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.resourcepack.data.minecraft.Animation
import xyz.mastriel.cutapi.resourcepack.management.ResourceFromFile
import xyz.mastriel.cutapi.resourcepack.management.ResourceReference
import xyz.mastriel.cutapi.utils.mkdirsOfParent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO


typealias BlockModelRef = ResourceReference<BlockModel>