package org.example.project.presentation.feed

import org.example.project.domain.model.Article

/**
 * MVI Contract for the News Feed Feature.
 */

// 1. Immutable UI State
data class NewsFeedState(
    val isLoading: Boolean = false,
    val articles: List<Article> = emptyList(),
    val ukraineArticlesCount: Int = 0,
    val bookmarkedCount: Int = 0,
    val error: String? = null,
    val selectedFilter: FeedFilter = FeedFilter.ALL,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedArticleForDetail: Article? = null
) {
    companion object {
        val Initial = NewsFeedState(isLoading = true)
    }
}

enum class FeedFilter {
    ALL,
    UKRAINE_ONLY,
    WORLD,
    BOOKMARKS
}

// 2. User Actions / Events (Intents)
sealed interface NewsFeedIntent {
    data object LoadFeed : NewsFeedIntent
    data object RefreshFeed : NewsFeedIntent
    data class SelectFilter(val filter: FeedFilter) : NewsFeedIntent
    data class UpdateSearchQuery(val query: String) : NewsFeedIntent
    data class ToggleSearch(val active: Boolean) : NewsFeedIntent
    data class ToggleBookmark(val article: Article) : NewsFeedIntent
    data class SelectArticleForDetail(val article: Article?) : NewsFeedIntent
    data class DeleteArticle(val articleId: String) : NewsFeedIntent
    data object ClearError : NewsFeedIntent
}

// 3. Single-event Side Effects
sealed interface NewsFeedEffect {
    data class OpenUrlInBrowser(val url: String) : NewsFeedEffect
    data class ShowSnackbar(val message: String) : NewsFeedEffect
}
