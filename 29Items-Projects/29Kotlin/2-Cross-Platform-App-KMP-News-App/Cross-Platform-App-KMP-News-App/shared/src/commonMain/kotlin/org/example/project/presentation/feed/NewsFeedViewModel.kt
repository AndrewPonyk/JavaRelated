package org.example.project.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.example.project.domain.model.Article
import org.example.project.domain.usecase.GetUkraineNewsUseCase

/**
 * MVI ViewModel managing state transitions, search, bookmarking, business logic, and effects.
 */
class NewsFeedViewModel(
    private val getUkraineNewsUseCase: GetUkraineNewsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(NewsFeedState.Initial)
    val state: StateFlow<NewsFeedState> = _state.asStateFlow()

    private val _effect = Channel<NewsFeedEffect>(Channel.BUFFERED)
    val effect: Flow<NewsFeedEffect> = _effect.receiveAsFlow()

    private var feedCollectionJob: Job? = null
    private var allArticlesCache: List<Article> = emptyList()

    init {
        handleIntent(NewsFeedIntent.LoadFeed)
    }

    fun handleIntent(intent: NewsFeedIntent) {
        when (intent) {
            is NewsFeedIntent.LoadFeed -> observeFeed(forceRefresh = false)
            is NewsFeedIntent.RefreshFeed -> observeFeed(forceRefresh = true)
            is NewsFeedIntent.SelectFilter -> updateFilter(intent.filter)
            is NewsFeedIntent.UpdateSearchQuery -> updateSearch(intent.query)
            is NewsFeedIntent.ToggleSearch -> _state.update { it.copy(isSearchActive = intent.active) }
            is NewsFeedIntent.ToggleBookmark -> toggleBookmark(intent.article)
            is NewsFeedIntent.SelectArticleForDetail -> _state.update { it.copy(selectedArticleForDetail = intent.article) }
            is NewsFeedIntent.DeleteArticle -> deleteArticle(intent.articleId)
            is NewsFeedIntent.ClearError -> _state.update { it.copy(error = null) }
        }
    }

    private fun observeFeed(forceRefresh: Boolean) {
        feedCollectionJob?.cancel()
        feedCollectionJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            getUkraineNewsUseCase(
                forceRefresh = forceRefresh,
                searchQuery = _state.value.searchQuery.takeIf { _state.value.isSearchActive },
                bookmarksOnly = _state.value.selectedFilter == FeedFilter.BOOKMARKS
            )
                .catch { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Failed to synchronize feeds."
                        )
                    }
                }
                .collect { prioritizedArticles ->
                    allArticlesCache = prioritizedArticles
                    val ukraineCount = prioritizedArticles.count { it.isUkraineRelated }
                    val bookmarkedCount = prioritizedArticles.count { it.isBookmarked }
                    val filtered = applyFilterToList(prioritizedArticles, _state.value.selectedFilter)

                    _state.update {
                        it.copy(
                            isLoading = false,
                            articles = filtered,
                            ukraineArticlesCount = ukraineCount,
                            bookmarkedCount = bookmarkedCount,
                            error = null
                        )
                    }
                }
        }
    }

    private fun updateFilter(filter: FeedFilter) {
        _state.update { it.copy(selectedFilter = filter) }
        if (filter == FeedFilter.BOOKMARKS) {
            observeFeed(forceRefresh = false)
        } else {
            val filtered = applyFilterToList(allArticlesCache, filter)
            _state.update { it.copy(articles = filtered) }
        }
    }

    private fun updateSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
        observeFeed(forceRefresh = false)
    }

    private fun toggleBookmark(article: Article) {
        viewModelScope.launch {
            val result = getUkraineNewsUseCase.toggleBookmark(article.id)
            result.onSuccess { isBookmarked ->
                val message = if (isBookmarked) "Article saved to Bookmarks" else "Article removed from Bookmarks"
                _effect.send(NewsFeedEffect.ShowSnackbar(message))
            }
        }
    }

    private fun deleteArticle(articleId: String) {
        viewModelScope.launch {
            getUkraineNewsUseCase.deleteArticle(articleId)
            _effect.send(NewsFeedEffect.ShowSnackbar("Article removed from local feed"))
            if (_state.value.selectedArticleForDetail?.id == articleId) {
                _state.update { it.copy(selectedArticleForDetail = null) }
            }
        }
    }

    private fun applyFilterToList(articles: List<Article>, filter: FeedFilter): List<Article> {
        return when (filter) {
            FeedFilter.ALL -> articles
            FeedFilter.UKRAINE_ONLY -> articles.filter { it.isUkraineRelated }
            FeedFilter.WORLD -> articles.filter { !it.isUkraineRelated }
            FeedFilter.BOOKMARKS -> articles.filter { it.isBookmarked }
        }
    }
}
