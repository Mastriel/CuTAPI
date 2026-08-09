package xyz.mastriel.cutapi.item.nativeitem

public enum class NativeItemState {
    Collecting,
    Installing,
    Active,
    Failed,
}

public object NativeItemLifecycle {
    @Volatile
    public var state: NativeItemState = NativeItemState.Collecting
        internal set

    public fun requireActive() {
        check(state == NativeItemState.Active) {
            "Native custom items are not active (current state: $state)."
        }
    }
}
