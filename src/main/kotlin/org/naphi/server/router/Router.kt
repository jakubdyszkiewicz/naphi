package org.naphi.server.router

import org.naphi.contract.Request
import org.naphi.contract.RequestMethod
import org.naphi.contract.Response
import org.naphi.server.Handler
import org.naphi.server.error.NotFoundException
import org.naphi.server.filter.Filter
import org.naphi.server.filter.thenHandler

data class Routes(val routes: Collection<Route>) {

    constructor(vararg routes: Route): this(routes.toList())

    init {
        validateClashes()
    }

    private fun validateClashes() {
        val clashes = routes.flatMap { route -> route.methods.map { method -> method to route.path } }
                .groupBy { it }
                .mapValues { it.value.size }
                .filter { it.value > 1 }
                .keys
        if (clashes.isNotEmpty()) {
            val clashesMsg = clashes.joinToString(", ") { (method, path) -> "at path $path with method $method" }
            throw IllegalArgumentException("Found multiple same routes: $clashesMsg")
        }
    }

    fun withFilter(filter: Filter) = Routes(routes.map { it.withFilter(filter) })

    companion object {
        fun combine(routes: Collection<Routes>) = Routes(routes.flatMap { it.routes })
    }
}

data class Route(val path: String, val methods: Collection<RequestMethod>, val handler: Handler) {
    constructor(path: String, method: RequestMethod, handler: Handler) :this(path, listOf(method), handler)

    fun withFilter(filter: Filter) = this.copy(handler = filter.thenHandler(handler))
}


data class RoutingHandler(val routes: Routes): Handler {

    override fun invoke(request: Request): Response =
            match(request)
                    ?.let { route ->
                        val values = RoutePath(route.path).values(request.path)
                        route.handler(request.withPathParams(values))
                    }
                    ?: throw NotFoundException()


    private fun match(request: Request): Route? {
        return routes.routes
                .firstOrNull { route -> RoutePath(route.path).match(request.path) && route.methods.contains(request.method) }
    }

}

private class RoutePath(val path: String) {

    fun values(reqPath: String): Map<String, String> {
        val reqSegments = reqPath.substringBefore("?").split("/")
        val segments = path.split("/")
        if (reqSegments.size != segments.size) {
            throw IllegalArgumentException("Request path does not match route path")
        }
        val values = mutableMapOf<String, String>()
        segments.forEachIndexed { index, segment ->
            if (isPlaceholder(segment)) {
                val key = segment.substring(startIndex = 1, endIndex = segment.length - 1)
                values[key] = reqSegments[index]
            }
        }
        return values
    }

    private fun isPlaceholder(segment: String) = segment.startsWith("{") && segment.endsWith("}")

    fun match(reqPath: String): Boolean {
        val reqSegments = reqPath.substringBefore("?").split("/")
        val segments = path.split("/")
        if (reqSegments.size != segments.size) {
            return false
        }
        return reqSegments.zip(segments).all { (reqSegment, segment) ->
            (isPlaceholder(segment) && reqSegment.isNotBlank()) || reqSegment == segment
        }
    }

}
