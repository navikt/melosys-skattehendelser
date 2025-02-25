package no.nav.melosysskattehendelser.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import no.nav.melosysskattehendelser.domain.PersonRepository
import org.springframework.stereotype.Component

@Component
class PersonerMetrikkerBinder(private val personRepository: PersonRepository) : MeterBinder {
    override fun bindTo(meterRegisty: MeterRegistry) {
        meterRegisty.gauge(Metrikker.PERSONER_REGISTRERT, personRepository) { it.count().toDouble() }
    }
}