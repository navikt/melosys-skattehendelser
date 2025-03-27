package no.nav.melosysskattehendelser.controller

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val caffeineCacheManager = CaffeineCacheManager()
        caffeineCacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .recordStats()
        )
        return caffeineCacheManager
    }
}