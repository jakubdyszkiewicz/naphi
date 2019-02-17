package org.naphi.client.serializer

object StringSerializer : Serializer {

    override fun supports(body: Any?, contentType: String?): Boolean =
        if (body != null) body::class.java.isAssignableFrom(String::class.java) else true

    override fun serialize(body: Any?): String? = body?.toString()
    override fun contentType(): String = "text/plain"

    override fun <T> deserialize(body: String?, clazz: Class<T>): T? = body as T?
}