package xyz.mastriel.brazil.mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.ServerApi
import com.mongodb.ServerApiVersion
import kotlinx.serialization.KSerializer
import org.bson.UuidRepresentation
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.serialization.SerializationClassMappingTypeService
import java.io.Closeable
import kotlin.reflect.KClass

class Database(val connectionString: String, val databaseName: String) : Closeable {

    val database: CoroutineDatabase
    val connection: CoroutineClient
    private val collections = hashSetOf<DatabaseCollection<*>>()
    var isConnected: Boolean
        private set

    init {
        System.setProperty("org.litote.mongo.mapping.service", SerializationClassMappingTypeService::class.qualifiedName!!)
        val boxedConnectionString = ConnectionString(connectionString)
        val settings: MongoClientSettings = MongoClientSettings.builder()
            .applyConnectionString(boxedConnectionString)
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .serverApi(
                ServerApi.builder()
                    .version(ServerApiVersion.V1)
                    .build()
            )
            .build()

        connection = KMongo.createClient(settings).coroutine
        database = connection.getDatabase(databaseName)
        isConnected = true
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> getCollection(name: String, serializer: KSerializer<T>, kClass: KClass<T>) : DatabaseCollection<T> {
        requireConnected()
        val collection = collections.find { it.name == name }
        if (collection != null) {
            return collection as DatabaseCollection<T>
        }
        val newCollection = database.getCollection(kClass, name)
        return databaseCollection(this, name, serializer, newCollection, kClass)
    }

    inline fun <reified T: Any> getCollection(name: String, serializer: KSerializer<T>) : DatabaseCollection<T> {
        return getCollection(name, serializer, T::class)
    }

    override fun close() {
        requireConnected()
        isConnected = false
        connection.close()
    }

    private fun requireConnected() {
        require(isConnected) { "Connection is closed to $connectionString/$databaseName." }
    }
}


fun <T: Any> CoroutineDatabase.getCollection(kClass: KClass<T>, name: String) : CoroutineCollection<T> {
    return database.getCollection(name, kClass.java).coroutine
}