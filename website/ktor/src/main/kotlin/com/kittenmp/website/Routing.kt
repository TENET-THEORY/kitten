package com.kittenmp.website

import com.kittenmp.ai.ComprehensionDebt
import io.ktor.server.application.Application
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

@ComprehensionDebt(agent = "cursor", model = "Cursor Grok 4.5")
fun Application.routing() {
  routing {
    get("/") { call.respondHtml { homePage() } }
    staticResources("/assets", "static")
  }
}
