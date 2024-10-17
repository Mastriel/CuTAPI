package xyz.mastriel.cutapi.resources

import net.kyori.adventure.extra.kotlin.*
import net.kyori.adventure.text.*
import xyz.mastriel.cutapi.utils.*

public class ResourceInspector {

    private val _inspections = mutableListOf<Inspection>()

    public val inspections: List<Inspection> get() = _inspections

    public fun addInspection(inspection: Inspection) {
        _inspections.add(inspection)
    }

    public fun single(name: String, value: () -> Any) {
        addInspection(Inspection.Single(name, value))
    }

    public fun list(name: String, values: () -> Collection<Any>) {
        addInspection(Inspection.List(name, values))
    }

    public fun map(name: String, values: () -> Map<String, Any>) {
        addInspection(Inspection.Map(name, values))
    }

    public fun raw(name: String, component: Component) {
        addInspection(Inspection.Raw(name, component))
    }

    public companion object {
        public val InspectorTitle: Color = Color.of(0x77ffb8)
        public val PropertyKey: Color = Color.of(0xc4ffb2)
        public val PropertyValue: Color = Color.of(0xf5ff97)
    }
}

private fun TextComponent.Builder.appendInspection(text: String, value: Any = "", spaces: Int = 0) {
    appendLine((" ".repeat(spaces) + "&${ResourceInspector.PropertyKey}$text &8→ &${ResourceInspector.PropertyValue}$value").colored)
}


public sealed interface Inspection {
    public abstract val name: String

    public fun getInspectionComponent(): Component

    public data class Single(
        override val name: String,
        val value: () -> Any
    ) : Inspection {

        override fun getInspectionComponent(): Component {
            return text {
                appendInspection(name, value())
            }
        }
    }

    public data class List(
        override val name: String,
        val values: () -> Collection<Any>
    ) : Inspection {

        override fun getInspectionComponent(): Component {
            return text {
                if (values().isEmpty()) {
                    appendInspection(name, "&7None")
                    return@text
                }

                appendInspection(name)
                for (value in values()) {
                    appendLine("  &8- &${ResourceInspector.PropertyValue}$value".colored)
                }
            }
        }
    }

    public data class Map(
        override val name: String,
        val values: () -> kotlin.collections.Map<String, Any>
    ) : Inspection {

        override fun getInspectionComponent(): Component {
            return text {
                if (values().isEmpty()) {
                    appendInspection(name, "&7None")
                    return@text
                }

                appendInspection(name)
                for ((key, value) in values()) {
                    appendLine("  &8- &${ResourceInspector.PropertyKey}$key &8→ &${ResourceInspector.PropertyValue}$value".colored)
                }
            }
        }
    }

    public data class Raw(
        override val name: String,
        val component: Component
    ) : Inspection {
        override fun getInspectionComponent(): Component {
            return component
        }
    }
}