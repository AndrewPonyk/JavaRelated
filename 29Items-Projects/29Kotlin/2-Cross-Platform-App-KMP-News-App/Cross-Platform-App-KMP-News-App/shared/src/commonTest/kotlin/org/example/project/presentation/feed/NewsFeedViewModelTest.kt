package org.example.project.presentation.feed

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.example.project.domain.model.Article
import org.example.project.domain.model.NewsSource
import org.example.project.domain.model.SourceProvider
import org.example.project.domain.repository.NewsRepository
import org.example.project.domain.usecase.GetUkraineNewsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewsFeedViewModelTest {

    private class FakeRepository : NewsRepository {
        private var items = listOf(
            Article(
                id = "art-1",
                title = "Ukraine accelerates digital state governance initiatives",
                description = "Diia platform updates released across public sectors.",
                content = "Full content description of digital services.",
                source = NewsSource("gov", "State News", SourceProvider.CUSTOM),
                author = "Press Service",
                url = "https://example.com/diia",
                imageUrl = null,
                publishedAtEpochMillis = 5000L,
                isBookmarked = false
            ),
            Article(
                id = "art-2",
                title = "Global astronomy team captures new deep field cosmic images",
                description = "Observatory updates from international consortium.",
                content = "Astronomers observe distant galaxy clusters.",
                source = NewsSource("space", "Space News", SourceProvider.CUSTOM),
                author = "Science Desk",
                url = "https://example.com/space",
                imageUrl = null,
                publishedAtEpochMillis = 4000L,
                isBookmarked = false
            )
        )

        override fun getArticlesStream(forceRefresh: Boolean): Flow<List<Article>> = flowOf(items)
        override suspend fun syncAllNewsSources(): Result<Unit> = Result.success(Unit)
        override suspend fun getArticleById(id: String): Article? = items.firstOrNull { it.id == id }
        override fun searchArticles(query: String): Flow<List<Article>> = flowOf(
            items.filter { it.title.contains(query, ignoreCase = true) }
        )
        override suspend fun toggleBookmark(articleId: String): Result<Boolean> {
            items = items.map { if (it.id == articleId) it.copy(isBookmarked = !it.isBookmarked) else it }
            return Result.success(items.firstOrNull { it.id == articleId }?.isBookmarked == true)
        }
        override fun getBookmarkedArticles(): Flow<List<Article>> = flowOf(items.filter { it.isBookmarked })
        override suspend fun deleteArticle(articleId: String): Result<Unit> {
            items = items.filter { it.id != articleId }
            return Result.success(Unit)
        }
    }

    @Test
    fun `initial state processes feed and populates prioritized articles`() = runTest {
        val useCase = GetUkraineNewsUseCase(FakeRepository())
        val viewModel = NewsFeedViewModel(useCase)

        val loadedState = viewModel.state.first { !it.isLoading }
        assertFalse(loadedState.isLoading)
        assertEquals(2, loadedState.articles.size)
        assertEquals(1, loadedState.ukraineArticlesCount)
        assertEquals("art-1", loadedState.articles.first().id)
        assertTrue(loadedState.articles.first().isUkraineRelated)
    }

    @Test
    fun `selecting filter updates visible articles accordingly`() = runTest {
        val useCase = GetUkraineNewsUseCase(FakeRepository())
        val viewModel = NewsFeedViewModel(useCase)

        viewModel.state.first { !it.isLoading }

        viewModel.handleIntent(NewsFeedIntent.SelectFilter(FeedFilter.UKRAINE_ONLY))
        val ukraineOnlyState = viewModel.state.value
        assertEquals(1, ukraineOnlyState.articles.size)
        assertEquals("art-1", ukraineOnlyState.articles.first().id)

        viewModel.handleIntent(NewsFeedIntent.SelectFilter(FeedFilter.WORLD))
        val worldOnlyState = viewModel.state.value
        assertEquals(1, worldOnlyState.articles.size)
        assertEquals("art-2", worldOnlyState.articles.first().id)
    }

    @Test
    fun `selecting article for detail updates state correctly`() = runTest {
        val useCase = GetUkraineNewsUseCase(FakeRepository())
        val viewModel = NewsFeedViewModel(useCase)

        val loadedState = viewModel.state.first { !it.isLoading }
        val targetArticle = loadedState.articles.first()

        viewModel.handleIntent(NewsFeedIntent.SelectArticleForDetail(targetArticle))
        assertEquals(targetArticle.id, viewModel.state.value.selectedArticleForDetail?.id)

        viewModel.handleIntent(NewsFeedIntent.SelectArticleForDetail(null))
        assertEquals(null, viewModel.state.value.selectedArticleForDetail)
    }

    @Test
    fun `clear error intent clears error state`() = runTest {
        val useCase = GetUkraineNewsUseCase(FakeRepository())
        val viewModel = NewsFeedViewModel(useCase)

        viewModel.handleIntent(NewsFeedIntent.ClearError)
        assertEquals(null, viewModel.state.value.error)
    }
}
