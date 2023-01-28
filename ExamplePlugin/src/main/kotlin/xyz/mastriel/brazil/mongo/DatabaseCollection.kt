package xyz.mastriel.brazil.mongo

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.KSerializer
import org.litote.kmongo.coroutine.CoroutineCollection
import xyz.mastriel.brazil.Plugin
import kotlin.reflect.KClass

open class DatabaseCollection<T: Any> internal constructor(
    private val database: Database,
    val name: String,
    val serializer: KSerializer<T>,
    val handle: CoroutineCollection<T>,
    private val kClass: KClass<T>
) {

    suspend fun getFromID(id: Any) : T? {
        return handle.findOneById(id)
    }

    fun saveAsync(obj: T) = Plugin.launch(Dispatchers.IO) {
        handle.save(obj)
    }

    suspend fun save(obj: T) {
        handle.save(obj)
    }
}

fun <T: Any> databaseCollection(
    database: Database,
    name: String,
    serializer: KSerializer<T>,
    collection: CoroutineCollection<T>,
    kClass: KClass<T>
) : DatabaseCollection<T> {
    return DatabaseCollection(database, name, serializer, collection, kClass)
}

inline fun <reified T: Any> databaseCollection(
    database: Database,
    name: String,
    collection: CoroutineCollection<T>,
    serializer: KSerializer<T>
) : DatabaseCollection<T> {
    return databaseCollection(database, name, serializer, collection, T::class)
}