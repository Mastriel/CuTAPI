package xyz.mastriel.cutapi.resources.postprocess

import xyz.mastriel.cutapi.resources.builtin.Model3D
import xyz.mastriel.cutapi.resources.resourceProcessor

val Model3DProcessor = resourceProcessor<Model3D> {
    val models = this.resources


}