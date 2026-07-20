package xyz.mastriel.cutapi.data

import kotlin.reflect.*

public data class MyData(val name: String, val age: Int) : Serializable<MyData> by +MyData {

    public companion object : Serializer<MyData> by schema({
        property(MyData::name, PrimitiveSerializer.String)
        property(MyData::age, PrimitiveSerializer.Int)
    })
}

public interface Schema<T> : Serializer<T> {
    public val properties: String;
}

public interface SchemaBuilder<R> {
    public fun <T> property(ref: KProperty1<R, T>, serializer: Serializer<T>, name: String = ref.name)
}

public fun <T> schema(block: SchemaBuilder<T>.() -> Unit): Schema<T> {
    TODO()
}