package xyz.mastriel.cutapi.utils

public fun interface SimpleEventHandler<TInput> {
    public fun TInput.trigger()
}


public class EventHandlerList<TInput> {

    private val events: MutableList<SimpleEventHandler<TInput>> = mutableListOf()

    public operator fun plusAssign(event: SimpleEventHandler<TInput>) {
        events += event
    }

    public operator fun minusAssign(event: SimpleEventHandler<TInput>) {
        events -= event
    }

    public fun trigger(event: TInput): Unit = events.forEach { with(it) { event.trigger() } }

    public operator fun invoke(event: SimpleEventHandler<TInput>) {
        events += event
    }
}

