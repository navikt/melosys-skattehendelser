package no.nav.melosysskattehendelser.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.server.ResponseStatusException

@Component
class ApiKeyInterceptor(
    @Value("\${admin.api-key}") private val apiKey: String
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val requestApiKey = request.getHeader(API_KEY_HEADER)
        if (requestApiKey != apiKey) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ugyldig apikey")
        }
        return true
    }

    companion object {
        const val API_KEY_HEADER = "X-SKATTEHENDELSER-ADMIN-APIKEY"
    }
}
