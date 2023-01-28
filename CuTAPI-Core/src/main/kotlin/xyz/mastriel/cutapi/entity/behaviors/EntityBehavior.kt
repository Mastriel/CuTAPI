package xyz.mastriel.cutapi.entity.behaviors

import xyz.mastriel.cutapi.behavior.Behavior
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier

class EntityBehavior(override val id: Identifier) : Behavior, Identifiable {
}