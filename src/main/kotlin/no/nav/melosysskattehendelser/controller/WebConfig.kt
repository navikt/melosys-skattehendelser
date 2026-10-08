package no.nav.melosysskattehendelser.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Autowired private val adminTilgangInterceptor: AdminTilgangInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(adminTilgangInterceptor).addPathPatterns("/admin/**")
    }
}
