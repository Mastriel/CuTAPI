# CuTAPI Development Rules

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
