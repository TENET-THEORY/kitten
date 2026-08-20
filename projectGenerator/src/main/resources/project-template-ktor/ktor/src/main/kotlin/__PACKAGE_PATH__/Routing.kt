package __PACKAGE__

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing

fun Application.routing() {
  routing {
    staticResources("/", "static")
  }
}
