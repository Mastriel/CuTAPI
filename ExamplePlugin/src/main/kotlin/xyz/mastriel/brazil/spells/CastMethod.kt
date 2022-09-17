package xyz.mastriel.brazil.spells

import org.bukkit.event.block.Action

enum class CastMethod {
    LEFT_CLICK,
    RIGHT_CLICK,
    MIDDLE_CLICK,
    DROP;


    companion object {
        fun fromAction(action: Action) : CastMethod? {
            return when (action) {
                Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> LEFT_CLICK
                Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> RIGHT_CLICK
                Action.PHYSICAL -> null
            }
        }
    }
}