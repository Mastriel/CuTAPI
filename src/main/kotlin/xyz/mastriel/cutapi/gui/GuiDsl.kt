package xyz.mastriel.cutapi.gui

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
public annotation class GuiDslMarker

public enum class GuiCloseReason {
    Player,
    Programmatic,
    Replaced,
    Quit,
    PluginDisabled,
    HandlerFailure,
    ViewInvalidated,
    BlockRemoved,
    BlockUnloaded,
}

public enum class GuiInteractionPolicy {
    CancelAll,
    CancelManagedSlots,
    Allow,
}
