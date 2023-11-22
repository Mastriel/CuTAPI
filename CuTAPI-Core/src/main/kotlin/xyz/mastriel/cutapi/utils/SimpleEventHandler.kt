package xyz.mastriel.cutapi.utils

fun interface SimpleEventHandler<TInput> {
    fun TInput.trigger()
}


class EventHandlerList<TInput> {

    private val events: MutableList<SimpleEventHandler<TInput>> = mutableListOf()

    operator fun plusAssign(event: SimpleEventHandler<TInput>) {
        events += event
    }

    operator fun minusAssign(event: SimpleEventHandler<TInput>) {
        events -= event
    }

    fun trigger(event: TInput) = events.forEach { with(it) { event.trigger() } }

    operator fun invoke(event: TInput) = trigger(event)
}

