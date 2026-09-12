package org.example.project.domain.model

/**
 * Metadata for identifying news providers and origins.
 */
enum class SourceProvider {
    NEWS_API,
    GUARDIAN,
    OPEN_RSS,
    CUSTOM
}

data class NewsSource(
    val id: String,
    val name: String,
    val provider: SourceProvider = SourceProvider.NEWS_API,
    val iconUrl: String? = null
)
