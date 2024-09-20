package no.nav.melosysskattehendelser.fakes

import no.nav.melosysskattehendelser.domain.SkatteHendelserSekvens
import no.nav.melosysskattehendelser.domain.SkatteHendelserStatusRepository
import java.util.*

class SkatteHendelserStatusRepositoryFake : SkatteHendelserStatusRepository {
    val skatteHendelserSekvenser = mutableListOf<SkatteHendelserSekvens>()
    val saved = mutableListOf<SkatteHendelserSekvens>()

    fun reset() = apply {
        skatteHendelserSekvenser.clear()
        saved.clear()
    }

    override fun <S : SkatteHendelserSekvens?> save(entity: S & Any): S & Any {
        saved.add(entity)
        return entity
    }

    override fun <S : SkatteHendelserSekvens?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun findById(id: String): Optional<SkatteHendelserSekvens> =
        Optional.ofNullable(skatteHendelserSekvenser.find { it.consumerId == id })

    override fun existsById(id: String): Boolean {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun findAll(): MutableIterable<SkatteHendelserSekvens> {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun findAllById(ids: MutableIterable<String>): MutableIterable<SkatteHendelserSekvens> {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun count(): Long {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun deleteById(id: String) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun delete(entity: SkatteHendelserSekvens) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun deleteAllById(ids: MutableIterable<String>) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun deleteAll(entities: MutableIterable<SkatteHendelserSekvens>) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun deleteAll() {
        throw UnsupportedOperationException("Not yet implemented")
    }
}