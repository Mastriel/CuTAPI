package xyz.mastriel.cutapi.commands

import com.mojang.brigadier.arguments.*
import net.kyori.adventure.extra.kotlin.*
import net.kyori.adventure.text.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.commands.brigadier.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.minecraft.*
import xyz.mastriel.cutapi.utils.*


internal val CuTAPICommand = command("cutapi") {

    subcommand("printrfs") {
        argument("includeMinecraft", BoolArgumentType.bool()) { includeMinecraft ->
            executes {
                sender.sendMessage(createRfsChatComponent(includeMinecraft()))
                return@executes BrigadierCommandReturn.Other(1)
            }
        }
        executes {
            sender.sendMessage(createRfsChatComponent(false))
            return@executes BrigadierCommandReturn.Other(1)
        }
    }
}

public fun createRfsChatComponent(includeMinecraft: Boolean): Component {
    val components = mutableListOf<Component>()

    fun addFolder(folder: FolderRef) {
        folder.getChildren().forEach { ref ->
            fun createSpaces(count: Int) = "&8- ".repeat(count)
            when (ref) {
                is FolderRef -> {
                    val spaceCount = ref.pathList.size
                    components += "${createSpaces(spaceCount)}&e${ref.pathList.last()}/".colored
                    addFolder(ref)
                }

                is ResourceRef<*> -> {
                    val path = ref.path(withNamespace = true, withExtension = true)
                    val resource = ref.getResource()


                    val spaceCount = ref.pathList.size

                    if (resource == null) {
                        components += "${createSpaces(spaceCount)}&c$path".colored
                        return@forEach
                    }

                    if (resource.isSubresource()) {
                        return@forEach
                    }

                    components += "${createSpaces(spaceCount)}&a${ref.name}".colored
                        .hoverEvent(createInspectionHover(resource))

                    for (subresource in resource.subResources) {
                        val subPath = subresource.ref.toString().split("#", limit = 2).last()
                        components += "${createSpaces(spaceCount + 1)}&2#${subPath}".colored
                            .hoverEvent(createInspectionHover(subresource))
                    }
                }
            }
        }
    }

    CuTAPI.registeredPlugins
        .map { folderRef(it, "/") }
        .forEach {
            if (!includeMinecraft && it.root == MinecraftAssets) return@forEach

            components += "&e${it}".colored
            addFolder(it)
        }

    return components.reduce { acc, component -> acc.appendNewline().append(component) }
}


public fun createInspectionHover(resource: Resource): Component {
    return text {
        appendLine("&${ResourceInspector.InspectorTitle}${resource.ref}".colored)

        resource.inspector.inspections.forEach {
            append(it.getInspectionComponent())
        }
        appendNewline()
        append("&8Left Click to Inspect".colored)
    }
}