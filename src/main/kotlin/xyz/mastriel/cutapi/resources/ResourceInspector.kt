package xyz.mastriel.cutapi.resources

import net.kyori.adventure.extra.kotlin.*
import net.kyori.adventure.text.*
import xyz.mastriel.cutapi.utils.*

/**
 * A utility class for inspecting resources by adding and managing inspections.
 * Inspections can be of various types, such as single values, lists, maps, or raw components.
 */
public class ResourceInspector {

    /**
     * A mutable list of inspections added to the inspector.
     */
    private val _inspections = mutableListOf<Inspection>()

    /**
     * A read-only list of all inspections added to the inspector.
     */
    public val inspections: List<Inspection> get() = _inspections

    /**
     * Adds a new inspection to the inspector.
     *
     * @param inspection The inspection to add.
     */
    public fun addInspection(inspection: Inspection) {
        _inspections.add(inspection)
    }

    /**
     * Adds a single-value inspection.
     *
     * @param name The name of the inspection.
     * @param value A lambda that provides the value to inspect.
     */
    public fun single(name: String, value: () -> Any) {
        addInspection(Inspection.Single(name, value))
    }

    /**
     * Adds a list inspection.
     *
     * @param name The name of the inspection.
     * @param values A lambda that provides the collection of values to inspect.
     */
    public fun list(name: String, values: () -> Collection<Any>) {
        addInspection(Inspection.List(name, values))
    }

    /**
     * Adds a map inspection.
     *
     * @param name The name of the inspection.
     * @param values A lambda that provides the map of key-value pairs to inspect.
     */
    public fun map(name: String, values: () -> Map<String, Any>) {
        addInspection(Inspection.Map(name, values))
    }

    /**
     * Adds a raw component inspection.
     *
     * @param name The name of the inspection.
     * @param component The raw component to inspect.
     */
    public fun raw(name: String, component: () -> Component) {
        addInspection(Inspection.Raw(name, component))
    }

    /**
     * Companion object containing default colors for inspection components.
     */
    public companion object {
        /**
         * The color used for the inspector title.
         */
        public val InspectorTitle: Color = Color.of(0x77ffb8)

        /**
         * The color used for property keys in inspections.
         */
        public val PropertyKey: Color = Color.of(0xc4ffb2)

        /**
         * The color used for property values in inspections.
         */
        public val PropertyValue: Color = Color.of(0xf5ff97)
    }
}

/**
 * Appends an inspection to a text component builder.
 *
 * @param text The text of the inspection.
 * @param value The value of the inspection (optional for values that require multiple lines, like maps or lists).
 * @param spaces The number of spaces to prepend to the text (default is 0).
 */
private fun TextComponent.Builder.appendInspection(text: String, value: Any = "", spaces: Int = 0) {
    appendLine((" ".repeat(spaces) + "&${ResourceInspector.PropertyKey}$text &8→ &${ResourceInspector.PropertyValue}$value").colored)
}

/**
 * Represents an inspection that can be added to the [ResourceInspector].
 * Inspections can be of various types, such as single values, lists, maps, or raw components.
 */
public sealed interface Inspection {

    /**
     * The name of the inspection.
     */
    public abstract val name: String

    /**
     * Generates a component representation of the inspection.
     *
     * @return The component representing the inspection.
     */
    public fun getInspectionComponent(): Component

    /**
     * Represents a single-value inspection.
     *
     * @property name The name of the inspection.
     * @property value A lambda that provides the value to inspect.
     */
    public data class Single(
        override val name: String,
        val value: () -> Any
    ) : Inspection {

        /**
         * Generates a component representation of the single-value inspection.
         *
         * @return The component representing the inspection.
         */
        override fun getInspectionComponent(): Component {
            return text {
                appendInspection(name, value())
            }
        }
    }

    /**
     * Represents a list inspection.
     *
     * @property name The name of the inspection.
     * @property values A lambda that provides the collection of values to inspect.
     */
    public data class List(
        override val name: String,
        val values: () -> Collection<Any>
    ) : Inspection {

        /**
         * Generates a component representation of the list inspection.
         *
         * @return The component representing the inspection.
         */
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

    /**
     * Represents a map inspection.
     *
     * @property name The name of the inspection.
     * @property values A lambda that provides the map of key-value pairs to inspect.
     */
    public data class Map(
        override val name: String,
        val values: () -> kotlin.collections.Map<String, Any>
    ) : Inspection {

        /**
         * Generates a component representation of the map inspection.
         *
         * @return The component representing the inspection.
         */
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

    /**
     * Represents a raw component inspection.
     *
     * @property name The name of the inspection.
     * @property component The raw component to inspect.
     */
    public data class Raw(
        override val name: String,
        val component: () -> Component
    ) : Inspection {

        /**
         * Generates a component representation of the raw inspection.
         *
         * @return The component representing the inspection.
         */
        override fun getInspectionComponent(): Component {
            return component()
        }
    }
}