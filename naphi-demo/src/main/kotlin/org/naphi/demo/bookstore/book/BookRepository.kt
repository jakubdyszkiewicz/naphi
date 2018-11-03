package org.naphi.demo.bookstore.book

import java.util.concurrent.ConcurrentHashMap

interface BookRepository {
    fun findAll(): Collection<Book>
    fun find(id: String): Book?
    fun save(book: Book)
}

class InMemoryBookRepository: BookRepository {

    private val books = ConcurrentHashMap<String, Book>()

    override fun findAll(): Collection<Book> = books.values

    override fun find(id: String): Book? = books[id]

    override fun save(book: Book) {
        books[book.id] = book
    }

}
