package xyz.mastriel.cutapi.item.attachments

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import java.util.*

public object Unstackable : Attachment, Schema<Unstackable> by singletonSchema(id(Plugin, "unstackable"))

internal object UnstackableSystem : ItemSystem by attachmentItemSystem(Unstackable) {
    override fun onCreate(context: ItemCreateContext) {
        var uuid by context.data(Unstackable).nullableUuidTag(id(Plugin, "unstackable_uuid"))
        context.item.handle.amount = 1
        uuid = UUID.randomUUID()
    }
}
