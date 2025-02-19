package no.nav.melosysskattehendelser.metrics

import io.micrometer.core.instrument.Metrics
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class Metrikker {

    @PostConstruct
    fun init() {
        Metrics.counter(HENDELSE_HENTET)
        Metrics.counter(PERSON_FUNNET)
        Metrics.counter(HENDELSE_PUBLISERT)
        Metrics.counter(DUPLIKAT_HENDELSE)
        Metrics.counter(PUBLISERING_FEILET)
    }

    fun hendelseHentet() = Metrics.counter(HENDELSE_HENTET).increment()

    fun personFunnet() = Metrics.counter(PERSON_FUNNET).increment()

    fun hendelsePublisert() = Metrics.counter(HENDELSE_PUBLISERT).increment()

    fun duplikatHendelse() = Metrics.counter(DUPLIKAT_HENDELSE).increment()

    fun publiseringFeilet() = Metrics.counter(PUBLISERING_FEILET).increment()

    companion object {
        private const val METRIKKER_NAMESPACE = "skattehendelser"

        private const val HENDELSE_HENTET = "$METRIKKER_NAMESPACE.hendelse.hentet"
        private const val PERSON_FUNNET = "$METRIKKER_NAMESPACE.person.funnet"
        private const val HENDELSE_PUBLISERT = "$METRIKKER_NAMESPACE.hendelse.publisert"
        private const val DUPLIKAT_HENDELSE = "$METRIKKER_NAMESPACE.duplikat.hendelse"
        private const val PUBLISERING_FEILET = "$METRIKKER_NAMESPACE.publisering.feilet"
    }
}