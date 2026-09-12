package org.example.project.data.remote.dto

/**
 * Data Transfer Object for NewsAPI (https://newsapi.org).
 */
data class NewsApiResponseDto(
    val status: String,
    val totalResults: Int? = null,
    val articles: List<NewsApiArticleDto>? = null
)

data class NewsApiArticleDto(
    val source: NewsApiSourceDto? = null,
    val author: String? = null,
    val title: String,
    val description: String? = null,
    val url: String,
    val urlToImage: String? = null,
    val publishedAt: String? = null,
    val content: String? = null
)

data class NewsApiSourceDto(
    val id: String? = null,
    val name: String? = null
)
