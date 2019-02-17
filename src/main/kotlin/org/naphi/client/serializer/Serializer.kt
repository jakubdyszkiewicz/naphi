package org.naphi.client.serializer

interface Serializer {
    fun supports(body: Any?, contentType: String?): Boolean

    fun serialize(body: Any?): String?
    fun contentType(): String

    fun <T> deserialize(body: String?, clazz: Class<T>): T?
}