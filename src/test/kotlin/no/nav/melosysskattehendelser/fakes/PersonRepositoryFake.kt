package no.nav.melosysskattehendelser.fakes

import no.nav.melosysskattehendelser.domain.Person
import no.nav.melosysskattehendelser.domain.PersonRepository
import java.util.*

class PersonRepositoryFake : PersonRepository {
    val items = mutableListOf<Person>()

    fun reset() = apply {
        items.clear()
    }

    override fun findPersonByIdent(ident: String): Person? =
        items.find { it.ident == ident }

    override fun <S : Person?> save(entity: S & Any): S & Any {
        items.add(entity)
        return entity
    }

    override fun <S : Person?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun findAll(): MutableIterable<Person> {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun findAllById(ids: MutableIterable<Long>): MutableIterable<Person> {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun count(): Long {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun delete(entity: Person) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun deleteAllById(ids: MutableIterable<Long>) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun deleteAll(entities: MutableIterable<Person>) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun deleteAll() {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun deleteById(id: Long) {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun existsById(id: Long): Boolean {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun findById(id: Long): Optional<Person> {
        throw UnsupportedOperationException("Not yet implemented")
    }


}