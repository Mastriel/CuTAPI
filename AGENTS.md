# CuTAPI Development Rules

## Development Servers

- Before starting a development server, verify that its configured port is free and that no applicable server or world lock file exists.
- If the port is already in use or a lock file exists, do not attempt to start the server.
- Do not retry on another port, world, or universe to bypass this guard.

## Enum Entry Names

- Enum entry names must use UpperCamelCase, such as `MainHand` and `ReadOnly`, never SCREAMING_SNAKE_CASE.

## DSL Defaults

- DSL APIs must accept default values through producers such as `() -> T`, never as preconstructed values.
- Invoke each producer independently for each value built by the DSL. This prevents accidental sharing of mutable default instances.
- A caller that intentionally wants shared identity may capture a value and return it from the producer.

```kotlin
// Good: each application creates a new default value.
property(...) {
    optional { MyInstance() }
}

// Bad: the DSL captures and reuses one instance.
property(...) {
    optional(MyInstance())
}
```

## Registry Registration

- Companion objects that implement `IdentifierRegistry` should not add `register` overloads that configure, mutate, or otherwise add behavior to the object being registered.
- Registration should only register an already-configured object. Initialization, validation, side effects, and other object-specific behavior belong in the registered object itself or in the factory that creates it.

```kotlin
// Good: the registered object owns its behavior.
class MyEntry(...) : Identifiable {
    init {
        configureEntry()
    }

    companion object : IdentifierRegistry<MyEntry>(id("example:registry/my_entry"))
}

// Bad: registration has a second responsibility.
companion object : IdentifierRegistry<MyEntry>(id("example:registry/my_entry")) {
    fun register(entry: MyEntry, configure: MyEntry.() -> Unit): MyEntry {
        entry.configure()
        return register(entry)
    }
}
```
