package org.naphi.demo.bookstore.book

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface BookRepository {
    fun findAll(): Collection<Book>
    fun find(id: String): Book?
    fun create(book: NewBook): Book
    fun update(id: String, book: UpdateBook)
    fun delete(id: String): Boolean

    class BookNotFound(id: String): Exception("Book of id $id not found")
}

class InMemoryBookRepository: BookRepository {

    private val books = ConcurrentHashMap<String, Book>()

    override fun findAll(): Collection<Book> = books.values

    override fun find(id: String): Book? = books[id]

    override fun create(book: NewBook): Book {
        val created = Book(id = UUID.randomUUID().toString(), title = book.title)
        books[created.id] = created
        return created
    }

    override fun update(id: String, book: UpdateBook) {
        val updated = books[id]?.copy(title = book.title)
            ?: throw BookRepository.BookNotFound(id)
        books[id] = updated
    }

    override fun delete(id: String): Boolean = books.remove(id) != null

    fun deleteAll() {
        books.clear()
    }
}
