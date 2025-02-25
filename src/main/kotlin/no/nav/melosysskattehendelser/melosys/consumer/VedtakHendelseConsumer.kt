package no.nav.melosysskattehendelser.melosys.consumer

import mu.KotlinLogging
import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import no.nav.melosysskattehendelser.metrics.Metrikker
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener

private val log = KotlinLogging.logger { }

class VedtakHendelseConsumer(
    private val vedtakHendelseRepository: PersonRepository,
    private val metrikker: Metrikker
) {
    @KafkaListener(
        id = "melosysVedtakMottatt",
        clientIdPrefix = "melosys-vedtak-mottatt",
        topics = ["\${melosys.kafka.consumer.topic}"],
        containerFactory = "melosysVedtakListenerContainerFactory",
        groupId = "\${melosys.kafka.consumer.groupId}"
    )

    fun vedtakHendelseConsumer(consumerRecord: ConsumerRecord<String, MelosysHendelse>) {
        val melding = consumerRecord.value().melding
        val vedtakHendelseMelding = melding as? VedtakHendelseMelding
            ?: return log.debug { "Ignorerer melding av type ${melding.javaClass.simpleName} " }

        if (vedtakHendelseMelding.sakstype != Sakstyper.FTRL) {
            return log.debug { "Ignorerer melding med sakstype ${vedtakHendelseMelding.sakstype} " }
        }

        log.info("Mottatt vedtakshendelse sakstype: ${vedtakHendelseMelding.sakstype} sakstema: ${vedtakHendelseMelding.sakstema}")
        if (vedtakHendelseMelding.medlemskapsperioder.isEmpty()) {
            return log.info { "Ingen medlemskapsperioder i melding, så lager ikke bruker i databasen" }
        }
        vedtakHendelseRepository.findPersonByIdent(vedtakHendelseMelding.folkeregisterIdent)?.let { person ->
            log.info("person med ident(${vedtakHendelseMelding.folkeregisterIdent}) finnes allerede")

            for (periode in vedtakHendelseMelding.medlemskapsperioder) {
                if (person.harPeriode(periode)) {
                    log.info("perioden $periode finnes allerede på person med id:${person.id}")
                    continue
                }

                if (periode.erGyldig()) {
                    log.info("legger til periode $periode på person med id: ${person.id}")
                    metrikker.vedtakMottattOgPeriodeLagtTil()
                    person.leggTilPeriode(periode)
                    vedtakHendelseRepository.save(person)
                } else {
                    log.warn { "Forsøkte å legge til periode med fom:${periode.fom} tom:${periode.tom} for person med id: ${person.id}" }
                }
            }
            return
        }

        metrikker.vedtakMottattOgPersonLagtTil()
        log.info("person med ident(${vedtakHendelseMelding.folkeregisterIdent}) er lagt til")
        vedtakHendelseRepository.save(vedtakHendelseMelding.toPerson())
    }

    private fun Person.harPeriode(periode: Periode) = perioder.any {
        it.fom == periode.fom && it.tom == periode.tom
    }

    private fun Person.leggTilPeriode(periode: Periode) {
        perioder.add(periode.toDbPeriode(this))
    }

    private fun Periode.erGyldig(): Boolean {
        return fom != null && tom != null
    }
}
