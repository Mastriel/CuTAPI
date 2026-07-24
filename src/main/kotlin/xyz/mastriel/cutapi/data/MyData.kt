package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*

public data class MyData(
    public val name: String,
    public val age: Int
) : Serializable<MyData> by +MyData {
    public companion object : Schema<MyData> by schema(id("cutapi:my_data"), {
        property(MyData::name, VariantSerializer.String)
        property(MyData::age, VariantSerializer.Int)
    })
}

public abstract class MyAbstractData(
    public val name: String,
    public val age: Int,
) : Serializable<MyAbstractData> by +MyAbstractData {

    public companion object : PolySchema<MyAbstractData> by polySchema(id("cutapi:my_abstract_data"), {
        property(MyAbstractData::name, VariantSerializer.String)
        include(MyChildData)
    })
}


public data class MyChildData(
    public val isAwesome: Boolean,
) : MyAbstractData("John", 67) {

    public companion object : Schema<MyChildData> by schema(id("cutapi:my_child_data"), {
        extends { MyAbstractData }
        property(MyChildData::isAwesome, VariantSerializer.Boolean)
    }) {}
}