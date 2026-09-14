import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
private data class Message(val message: String)

/**
 * One route, one JSON object. This is the whole subject: the collapse is in
 * `HttpHeadersMap`'s pools, which every parsed request goes through, so nothing
 * in the handler is needed to show it.
 *
 * Content negotiation and serialisation are here only because the numbers in the
 * issue were measured with them. A plain `respondText` route collapses the same
 * way — `:rung2` in the original probe, which has neither, went slow in 9 runs
 * of 12.
 */
fun main(args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8080
    embeddedServer(CIO, port = port) {
        install(ContentNegotiation) { json() }
        routing {
            get("/") { call.respond(Message("Hello, world!")) }
        }
    }.start(wait = true)
}
