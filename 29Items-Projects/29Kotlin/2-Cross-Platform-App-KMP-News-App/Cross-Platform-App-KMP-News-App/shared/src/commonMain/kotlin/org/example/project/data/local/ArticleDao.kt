package org.example.project.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.example.project.domain.model.Article

/**
 * Data Access Object managing local article storage, full-text search, bookmarking, and deletion.
 */
class ArticleDao {
    private val _articles = MutableStateFlow<List<Article>>(emptyList())
    private val bookmarkedIds = mutableSetOf<String>()

    /**
     * Observes all cached articles.
     */
    fun getAllArticles(): Flow<List<Article>> = _articles.asStateFlow().map { list ->
        list.map { it.copy(isBookmarked = bookmarkedIds.contains(it.id)) }
    }

    /**
     * Inserts or updates articles in local storage (deduplicating by unique article ID).
     */
    suspend fun insertArticles(articles: List<Article>) {
        val currentMap = _articles.value.associateBy { it.id }.toMutableMap()
        articles.forEach { newArticle ->
            val existing = currentMap[newArticle.id]
            val isBookmarked = bookmarkedIds.contains(newArticle.id) || (existing?.isBookmarked == true)
            currentMap[newArticle.id] = newArticle.copy(isBookmarked = isBookmarked)
        }
        _articles.value = currentMap.values.toList()
    }

    /**
     * Retrieves article by unique ID.
     */
    suspend fun getArticleById(id: String): Article? {
        val article = _articles.value.firstOrNull { it.id == id }
        return article?.copy(isBookmarked = bookmarkedIds.contains(id))
    }

    /**
     * Searches articles by keyword query across title, description, and author.
     */
    fun searchArticles(query: String): Flow<List<Article>> {
        val q = query.lowercase().trim()
        return getAllArticles().map { list ->
            if (q.isEmpty()) {
                list
            } else {
                list.filter {
                    it.title.lowercase().contains(q) ||
                    (it.description ?: "").lowercase().contains(q) ||
                    (it.author ?: "").lowercase().contains(q) ||
                    it.source.name.lowercase().contains(q)
                }
            }
        }
    }

    /**
     * Toggles bookmark state for an article.
     */
    suspend fun toggleBookmark(articleId: String): Boolean {
        val newState = if (bookmarkedIds.contains(articleId)) {
            bookmarkedIds.remove(articleId)
            false
        } else {
            bookmarkedIds.add(articleId)
            true
        }

        val updated = _articles.value.map {
            if (it.id == articleId) it.copy(isBookmarked = newState) else it
        }
        _articles.value = updated
        return newState
    }

    /**
     * Returns a reactive flow of only bookmarked articles.
     */
    fun getBookmarkedArticles(): Flow<List<Article>> = getAllArticles().map { list ->
        list.filter { it.isBookmarked }
    }

    /**
     * Deletes a single article from local storage.
     */
    suspend fun deleteArticle(articleId: String) {
        bookmarkedIds.remove(articleId)
        _articles.value = _articles.value.filter { it.id != articleId }
    }

    /**
     * Clears all cached articles.
     */
    suspend fun clearAll() {
        bookmarkedIds.clear()
        _articles.value = emptyList()
    }
}
