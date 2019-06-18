package io.paleocrafter.booksearch

import org.apache.http.HttpHost
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.close.CloseIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.open.OpenIndexRequest
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.MultiSearchRequest
import org.elasticsearch.action.search.MultiSearchResponse
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.client.indices.PutMappingRequest
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.Operator
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder
import org.elasticsearch.search.aggregations.metrics.TopHits
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder
import org.elasticsearch.search.sort.SortOrder
import org.intellij.lang.annotations.Language
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine


class BookIndex {
    private val client = RestHighLevelClient(
        RestClient.builder(HttpHost("localhost", 9200, "http"))
    )
    @Language("JSON")
    private val chapterMapping = """
        {
            "properties": {
                "book": {
                    "type": "keyword"
                },
                "chapter": {
                    "type": "keyword"
                },
                "position": {
                    "type": "integer"
                },
                "classes": {
                    "type": "keyword"
                },
                "text": {
                    "type": "keyword",
                    "fields": {
                        "stripped": {
                            "type": "text",
                            "analyzer": "strip_html_analyzer"
                        }
                    }
                }
            }
        }
    """.trimIndent()
    @Language("JSON")
    private val indexSettings = """
        {
            "max_inner_result_window": 1000,
            "analysis": {
                "analyzer": {
                    "strip_html_analyzer": {
                        "tokenizer": "classic",
                        "filter": ["lowercase"],
                        "char_filter": ["html_stripper"]
                    }
                },
                "char_filter": {
                    "html_stripper": {
                        "type": "html_strip"
                    }
                }
            }
        }
    """.trimIndent()

    private val highlighter =
        HighlightBuilder().field(
            HighlightBuilder.Field("text.stripped")
                .numOfFragments(0)
                .preTags("<strong>")
                .postTags("</strong>")
        )

    private suspend fun ensureElasticIndex() {
        if (!suspendCoroutine<Boolean> {
                client.indices().existsAsync(GetIndexRequest("chapters"), RequestOptions.DEFAULT, SuspendingActionListener(it))
            }) {
            suspendCoroutine {
                client.indices().createAsync(
                    CreateIndexRequest("chapters"),
                    RequestOptions.DEFAULT,
                    SuspendingActionListener(it)
                )
            }
            suspendCoroutine {
                client.indices().closeAsync(
                    CloseIndexRequest("chapters"),
                    RequestOptions.DEFAULT,
                    SuspendingActionListener(it)
                )
            }
            suspendCoroutine {
                client.indices().putSettingsAsync(
                    UpdateSettingsRequest("chapters").settings(indexSettings, XContentType.JSON),
                    RequestOptions.DEFAULT,
                    SuspendingActionListener(it)
                )
            }
            suspendCoroutine {
                client.indices().putMappingAsync(
                    PutMappingRequest("chapters").source(chapterMapping, XContentType.JSON),
                    RequestOptions.DEFAULT,
                    SuspendingActionListener(it)
                )
            }
            suspendCoroutine {
                client.indices().openAsync(
                    OpenIndexRequest("chapters"),
                    RequestOptions.DEFAULT,
                    SuspendingActionListener(it)
                )
            }
        }
    }

    suspend fun reset() {
        if (suspendCoroutine {
                client.indices().existsAsync(GetIndexRequest("chapters"), RequestOptions.DEFAULT, SuspendingActionListener(it))
            }) {
            suspendCoroutine {
                client.indices().deleteAsync(DeleteIndexRequest("chapters"), RequestOptions.DEFAULT, SuspendingActionListener(it))
            }
        }
    }

    suspend fun index(id: UUID, chapters: Iterable<ResolvedChapter>) {
        ensureElasticIndex()

        suspendCoroutine {
            client.deleteByQueryAsync(
                DeleteByQueryRequest("chapters").setQuery(QueryBuilders.termQuery("book", id.toString())),
                RequestOptions.DEFAULT,
                SuspendingActionListener(it)
            )
        }

        val entries = chapters.flatMap { this.collectEntries(it) }

        val bulkRequest = BulkRequest("chapters")
        for (entry in entries) {
            bulkRequest.add(IndexRequest().id(UUID.randomUUID().toString()).source(
                mapOf(
                    "book" to id.toString(),
                    "chapter" to entry.chapter,
                    "position" to entry.position,
                    "classes" to entry.classes,
                    "text" to entry.text
                )
            ))
        }
        suspendCoroutine {
            client.bulkAsync(bulkRequest, RequestOptions.DEFAULT, SuspendingActionListener(it))
        }
    }

    private fun collectEntries(chapter: ResolvedChapter): List<IndexEntry> {
        return chapter.content.select("p").mapIndexed { position, p ->
            IndexEntry(chapter.id.toString(), position, p.html(), p.classNames())
        }
    }

    private fun buildBaseQuery(query: String, filter: List<UUID>) =
        SearchSourceBuilder().query(
            QueryBuilders.boolQuery()
                .must(QueryBuilders.queryStringQuery(query).defaultField("text.stripped").defaultOperator(Operator.AND))
                .filter(
                    QueryBuilders.boolQuery()
                        .minimumShouldMatch(1)
                        .also { qry ->
                            filter.forEach {
                                qry.should(QueryBuilders.termQuery("book", it.toString()))
                            }
                        }
                )
        )

    suspend fun search(query: String, page: Int, filter: List<UUID>): SearchResults {
        val baseQuery = buildBaseQuery(query, filter)
        baseQuery.size(10)
        baseQuery.from(page * 10)

        baseQuery.highlighter(highlighter)

        val baseResponse = suspendCoroutine<SearchResponse> {
            client.searchAsync(SearchRequest("chapters").source(baseQuery), RequestOptions.DEFAULT, SuspendingActionListener(it))
        }

        return SearchResults(baseResponse.hits.totalHits.value, gatherContext(baseResponse.hits))
    }

    suspend fun searchGrouped(query: String, filter: List<UUID>): GroupedSearchResults {
        val baseQuery = buildBaseQuery(query, filter)
        baseQuery.aggregation(
            AggregationBuilders.composite(
                "chapters",
                listOf(
                    TermsValuesSourceBuilder("book").field("book"),
                    TermsValuesSourceBuilder("chapter").field("chapter")
                )
            ).size(1000).subAggregation(
                AggregationBuilders.topHits("paragraphs")
                    .size(100)
                    .highlighter(highlighter)
                    .sort("position", SortOrder.ASC)
            )
        )

        val baseResponse = suspendCoroutine<SearchResponse> {
            client.searchAsync(SearchRequest("chapters").source(baseQuery), RequestOptions.DEFAULT, SuspendingActionListener(it))
        }

        val totalHits = baseResponse.hits.totalHits.value
        val chapters = baseResponse.aggregations.get<CompositeAggregation>("chapters")
        val buckets = chapters.buckets

        val chapterCounts = buckets.groupBy { it.key["chapter"] }.mapValues { (_, paragraphs) -> paragraphs.sumBy { it.docCount.toInt() } }

        if (buckets.isEmpty()) {
            return GroupedSearchResults(totalHits, emptySequence())
        }

        val hits = buckets.flatMap { it.aggregations.get<TopHits>("paragraphs").hits }
        if (hits.none()) {
            return GroupedSearchResults(totalHits, emptySequence())
        }

        return GroupedSearchResults(
            totalHits,
            gatherContext(hits)
                .groupBy { it.bookId }
                .asSequence()
                .map { (bookId, bookResults) ->
                    BookSearchResult(
                        bookId,
                        bookResults
                            .groupBy { result -> result.chapterId }
                            .asSequence()
                            .map { (chapterId, chapterResults) ->
                                ChapterSearchResult(chapterId, chapterCounts[chapterId.toString()] ?: 0, chapterResults)
                            }
                    )
                }
        )
    }

    private suspend fun gatherContext(hits: Iterable<SearchHit>): List<SearchResult> {
        val contextRequest = MultiSearchRequest()
        for (hit in hits) {
            val source = hit.sourceAsMap
            val bookId = source["book"] as? String ?: throw IllegalStateException("Indexed paragraph must have book id!")
            val chapter = source["chapter"] as? String ?: throw IllegalStateException("Indexed paragraph must have chapter!")
            val position = source["position"] as? Int ?: throw IllegalStateException("Indexed paragraph must have position in chapter!")
            val contextQuery = SearchSourceBuilder()
            contextQuery.query(
                QueryBuilders.boolQuery()
                    .should(
                        QueryBuilders.boolQuery()
                            .must(QueryBuilders.termQuery("book", bookId))
                            .must(QueryBuilders.termQuery("chapter", chapter))
                            .must(QueryBuilders.rangeQuery("position").from(position - 2).to(position + 2))
                    )
                    .mustNot(
                        QueryBuilders.termQuery("position", position)
                    )
            )
            contextRequest.add(SearchRequest("chapters").source(contextQuery))
        }
        val contextResponses = if (hits.any()) {
            suspendCoroutine<MultiSearchResponse> {
                client.msearchAsync(contextRequest, RequestOptions.DEFAULT, SuspendingActionListener(it))
            }.responses
        } else {
            emptyArray()
        }

        return hits.mapIndexed { index, hit ->
            val context = contextResponses[index].response.hits.map {
                val source = it.sourceAsMap
                val position = source["position"] as? Int
                    ?: throw IllegalStateException("Indexed paragraph must have position in chapter!")
                val text = source["text"] as? String ?: throw IllegalStateException("Indexed paragraph must have text!")
                val classes = source["classes"] as? List<String>
                    ?: throw IllegalStateException("Indexed paragraph must have class array!")

                SearchParagraph(false, position, text, classes)
            }
            val source = hit.sourceAsMap
            val bookId = source["book"] as? String ?: throw IllegalStateException("Indexed paragraph must have book id!")
            val chapter = source["chapter"] as? String ?: throw IllegalStateException("Indexed paragraph must have chapter!")
            val position = source["position"] as? Int ?: throw IllegalStateException("Indexed paragraph must have position in chapter!")
            val text = hit.highlightFields["text.stripped"]?.fragments?.first()?.string() ?: source["text"] as? String
            ?: throw IllegalStateException("Search result must have highlight!")
            val classes = source["classes"] as? List<String> ?: throw IllegalStateException("Indexed paragraph must have class array!")
            val paragraph = SearchParagraph(true, position, text, classes)

            SearchResult(
                UUID.fromString(bookId),
                UUID.fromString(chapter),
                paragraph.position,
                (context + paragraph).sortedBy { it.position }
            )
        }
    }

    private data class IndexEntry(val chapter: String, val position: Int, val text: String, val classes: Set<String>)

    private class SuspendingActionListener<T>(private val continuation: Continuation<T>) : ActionListener<T> {
        override fun onFailure(e: Exception) {
            continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(response: T) {
            continuation.resumeWith(Result.success(response))
        }
    }
}

data class SearchResults(val totalHits: Long, val results: List<SearchResult>)

data class GroupedSearchResults(val totalHits: Long, val results: Sequence<BookSearchResult>)

data class BookSearchResult(val bookId: UUID, val chapters: Sequence<ChapterSearchResult>)

data class ChapterSearchResult(val chapterId: UUID, val totalOccurrences: Int, val results: List<SearchResult>)

data class SearchResult(val bookId: UUID, val chapterId: UUID, val mainPosition: Int, val paragraphs: List<SearchParagraph>)

class SearchParagraph(val main: Boolean, val position: Int, val text: String, val classes: List<String>)
