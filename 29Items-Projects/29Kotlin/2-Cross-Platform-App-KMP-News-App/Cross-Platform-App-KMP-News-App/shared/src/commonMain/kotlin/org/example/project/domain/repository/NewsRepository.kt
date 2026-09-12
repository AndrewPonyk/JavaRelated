package org.example.project.domain.repository

import kotlinx.coroutines.flow.Flow
import org.example.project.domain.model.Article

/**
 * Domain boundary contract for retrieving, searching, bookmarking, and synchronizing news data.
 */
interface NewsRepository {
    /**
     * Returns a reactive flow of news articles (cached + fresh remote updates).
     * @param forceRefresh when true, forces network synchronization immediately.
     */
    fun getArticlesStream(forceRefresh: Boolean = false): Flow<List<Article>>

    /**
     * Synchronizes news from all configured sources (NewsAPI, Guardian, RSS).
     */
    suspend fun syncAllNewsSources(): Result<Unit>

    /**
     * Retrieves a single article by ID.
     */
    suspend fun getArticleById(id: String): Article?

    /**
     * Searches articles by keyword query across titles and descriptions.
     */
    fun searchArticles(query: String): Flow<List<Article>>

    /**
     * Toggles bookmark status for a specific article.
     */
    suspend fun toggleBookmark(articleId: String): Result<Boolean>

    /**
     * Returns a flow of all bookmarked articles.
     */
    fun getBookmarkedArticles(): Flow<List<Article>>

    /**
     * Deletes an article from local storage.
     */
    suspend fun deleteArticle(articleId: String): Result<Unit>
}
