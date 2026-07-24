package xyz.mastriel.cutapi.item.recipe

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*

public object CraftsAsBaseMaterial : Attachment,
    Schema<CraftsAsBaseMaterial> by singletonSchema(id(Plugin, "crafts_as_base_material"))
