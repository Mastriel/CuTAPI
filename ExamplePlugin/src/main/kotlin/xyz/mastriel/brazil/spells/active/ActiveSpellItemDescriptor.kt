package xyz.mastriel.brazil.spells.active

import xyz.mastriel.cutapi.item.ItemDescriptor
import xyz.mastriel.cutapi.item.ItemDescriptorBuilder
import xyz.mastriel.cutapi.utils.personalized.Personalized
import xyz.mastriel.cutapi.utils.personalized.PersonalizedWithDefault
import xyz.mastriel.cutapi.utils.personalized.personalized


class ActiveSpellItemBuilder {
    var meleeAttackDamage = personalized(5.0)
    var rechargeTime = personalized(10L)
}