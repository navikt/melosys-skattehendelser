package no.nav.melosysskattehendelser.domain

import java.util.*

class SkatteHendelserStatusRepositoryFake : SkatteHendelserStatusRepository {
    private val items = mutableMapOf<String, SkatteHendelserSekvens>()

    fun reset() = apply {
        items.clear()
    }

    override fun <S : SkatteHendelserSekvens?> save(entity: S & Any): S & Any {
        items[entity.consumerId] = entity
        return entity
    }

    override fun <S : SkatteHendelserSekvens?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun findById(id: String): Optional<SkatteHendelserSekvens> =
        Optional.ofNullable(items[id])

    override fun existsById(id: String): Boolean {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun findAll(): MutableIterable<SkatteHendelserSekvens> = items.values

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
        items.clear()
    }
}