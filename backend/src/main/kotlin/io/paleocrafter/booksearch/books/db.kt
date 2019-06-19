package io.paleocrafter.booksearch.books

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jsoup.nodes.Element
import java.util.UUID

object Books : UUIDTable() {
    val content = blob("content")
    val title = varchar("title", 255)
    val author = varchar("author", 255)
    val series = varchar("series", 255).nullable()
    val orderInSeries = integer("order_in_series").default(0)
}

class Book(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Book>(Books)

    var content by Books.content
    var title by Books.title
    var author by Books.author
    var series by Books.series
    var orderInSeries by Books.orderInSeries

    fun toJson() =
        mapOf(
            "id" to id.value,
            "title" to title,
            "author" to author,
            "series" to series,
            "orderInSeries" to orderInSeries
        )
}

object Chapters : UUIDTable() {
    val book = reference("book", Books).index()
    val tocReference = varchar("toc_reference", 255)
    val title = varchar("title", 255)
    val content = text("content")
    val indexedContent = text("indexed_content").nullable()
}

class Chapter(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Chapter>(Chapters)

    var book by Book referencedOn Chapters.book
    var title by Chapters.title
    var tocReference by Chapters.tocReference
    var content by Chapters.content
    var indexedContent by Chapters.indexedContent

    fun toJson() =
        mapOf(
            "id" to id.value,
            "title" to title
        )
}

data class ResolvedChapter(val id: UUID, val bookId: UUID, val title: String, val content: Element)

object Images : Table() {
    val book = reference("book", Books).primaryKey(0)
    val name = varchar("name", 255).primaryKey(1)
    val data = blob("data")
}

object ClassMappings : Table() {
    val book = reference("book", Books).primaryKey(0)
    val className = varchar("class_name", 255).primaryKey(1)
    val mapping = varchar("mapping", 255)
}