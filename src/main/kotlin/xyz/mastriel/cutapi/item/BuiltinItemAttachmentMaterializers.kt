@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.item

import xyz.mastriel.cutapi.item.attachments.DurabilityMaterializer
import xyz.mastriel.cutapi.item.attachments.EquipableMaterializer
import xyz.mastriel.cutapi.item.attachments.MeleeWeaponMaterializer
import xyz.mastriel.cutapi.item.attachments.ModifyAttributeMaterializer
import xyz.mastriel.cutapi.item.attachments.UnstackableMaterializer

internal fun registerBuiltInItemAttachmentMaterializers() {
    ItemAttachmentMaterializer.modifyRegistry {
        register(DurabilityMaterializer)
        register(MeleeWeaponMaterializer)
        register(ModifyAttributeMaterializer)
        register(EquipableMaterializer)
        register(UnstackableMaterializer)
    }
}
