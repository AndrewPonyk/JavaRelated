package org.example.project.domain.model

/**
 * Core business domain model representing a news article across Android, iOS, and Desktop.
 */
data class Article(
    val id: String,
    val title: String,
    val description: String?,
    val content: String?,
    val source: NewsSource,
    val author: String?,
    val url: String,
    val imageUrl: String?,
    val publishedAtEpochMillis: Long,
    val isUkraineRelated: Boolean = false,
    val relevanceScore: Int = 0,
    val isBookmarked: Boolean = false
)
