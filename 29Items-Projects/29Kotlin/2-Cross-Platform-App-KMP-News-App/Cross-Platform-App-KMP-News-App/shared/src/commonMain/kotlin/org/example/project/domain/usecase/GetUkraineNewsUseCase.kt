package org.example.project.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.example.project.domain.model.Article
import org.example.project.domain.repository.NewsRepository

/**
 * Core Business Logic Use Case:
 * Evaluates, scores, and prioritizes news related to Ukraine (or Ukrainians),
 * ensuring they appear at the top of the feed followed by world news sorted chronologically.
 */
class GetUkraineNewsUseCase(
    private val newsRepository: NewsRepository
) {
    // Comprehensive multi-lingual keyword set for identifying Ukraine coverage
    private val ukraineKeywords = listOf(
        "ukraine", "ukrainian", "ukrainians", "kyiv", "kiev", "zelensky", "zelenskyy",
        "kharkiv", "odesa", "lviv", "donbas", "dnipro", "crimea", "zaporizhzhia",
        "chornobyl", "chernobyl", "azov", "sumy", "mykolaiv", "kherson", "ukrainska",
        "україна", "українець", "українка", "українці", "київ", "зеленський", "зсу",
        "львів", "харків", "одеса", "дніпро", "донецьк", "луганськ", "крим"
    )

    /**
     * Executes the prioritization algorithm over the incoming articles stream.
     */
    operator fun invoke(
        forceRefresh: Boolean = false,
        searchQuery: String? = null,
        bookmarksOnly: Boolean = false
    ): Flow<List<Article>> {
        val baseFlow = if (bookmarksOnly) {
            newsRepository.getBookmarkedArticles()
        } else if (!searchQuery.isNullOrBlank()) {
            newsRepository.searchArticles(searchQuery.trim())
        } else {
            newsRepository.getArticlesStream(forceRefresh)
        }

        return baseFlow.map { articles ->
            articles
                .map { article ->
                    val (isRelated, score) = calculateUkraineRelevance(article)
                    article.copy(
                        isUkraineRelated = isRelated,
                        relevanceScore = score
                    )
                }
                .sortedWith(
                    compareByDescending<Article> { it.isUkraineRelated }
                        .thenByDescending { it.relevanceScore }
                        .thenByDescending { it.publishedAtEpochMillis }
                )
        }
    }

    /**
     * Scores an article based on keyword presence across title, description, and content.
     */
    fun calculateUkraineRelevance(article: Article): Pair<Boolean, Int> {
        val titleLower = article.title.lowercase()
        val descLower = (article.description ?: "").lowercase()
        val contentLower = (article.content ?: "").lowercase()

        var score = 0

        for (keyword in ukraineKeywords) {
            if (titleLower.contains(keyword)) {
                score += 15 // Highest weight for title mention
            }
            if (descLower.contains(keyword)) {
                score += 8  // High weight for description mention
            }
            if (contentLower.contains(keyword)) {
                score += 3  // Base weight for content mention
            }
        }

        val isRelated = score > 0
        return Pair(isRelated, score)
    }

    /**
     * Helper to toggle bookmark through repository.
     */
    suspend fun toggleBookmark(articleId: String): Result<Boolean> {
        return newsRepository.toggleBookmark(articleId)
    }

    /**
     * Helper to delete article through repository.
     */
    suspend fun deleteArticle(articleId: String): Result<Unit> {
        return newsRepository.deleteArticle(articleId)
    }
}
